package sizepotion;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.inventory.RemoteSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;

public class SizePotionGameTest {
	@GameTest
	public void potionUsesVanillaComponentsAndSurvivesNetworkAndSave(GameTestHelper helper) {
		SizePotionData data = SizePotionData.create(0.8, 95);
		ItemStack potion = data.toItemStack("&aExample lore");
		for (var component : potion.getComponents()) {
			helper.assertValueEqual(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component.type()).getNamespace(),
				"minecraft", "Vanilla component namespace");
		}
		helper.assertTrue(BuiltInRegistries.DATA_COMPONENT_TYPE.keySet().stream()
			.noneMatch(id -> id.getNamespace().equals("sizepotion")), "No custom component is registered");
		helper.assertValueEqual(potion.getItem(), Items.POTION, "Vanilla potion item");
		helper.assertTrue(potion.get(DataComponents.POTION_CONTENTS).customEffects().isEmpty(),
			"Size behavior is handled on the server");

		var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		try {
			ItemStack.STREAM_CODEC.encode(buffer, potion);
			ItemStack received = ItemStack.STREAM_CODEC.decode(buffer);
			helper.assertTrue(ItemStack.isSameItemSameComponents(potion, received), "Packet preserves appearance and data");
			helper.assertValueEqual(SizePotionData.fromItemStack(received).orElseThrow(), data, "Network transformation data");
		} finally {
			buffer.release();
		}
		var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		var saved = ItemStack.CODEC.encodeStart(ops, potion).getOrThrow();
		var loaded = ItemStack.CODEC.parse(ops, saved).getOrThrow();
		helper.assertTrue(ItemStack.isSameItemSameComponents(potion, loaded), "Save preserves appearance and data");
		helper.succeed();
	}

	@GameTest
	public void olderPotionWithRemovedComponentStillLoads(GameTestHelper helper) {
		SizePotionData data = SizePotionData.create(2, 95);
		ItemStack potion = data.toItemStack("");
		var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		CompoundTag saved = (CompoundTag) ItemStack.CODEC.encodeStart(ops, potion).getOrThrow();
		saved.getCompound("components").orElseThrow().put("sizepotion:transformation",
			SizePotionData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow());
		CompoundTag root = new CompoundTag();
		root.put("item", saved);
		// Exercise vanilla's tolerant save reader: the obsolete component is
		// skipped, leaving the mirrored vanilla custom data intact.
		var input = TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), root);
		ItemStack loaded = input.read("item", ItemStack.CODEC).orElseThrow();
		helper.assertTrue(ItemStack.isSameItemSameComponents(potion, loaded), "Older saved potion retains vanilla components");
		helper.assertValueEqual(SizePotionData.fromItemStack(loaded).orElseThrow(), data, "Older potion transformation data");
		helper.succeed();
	}

	@GameTest
	public void survivalDrinkConsumesLastPotionAndReturnsBottle(GameTestHelper helper) {
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		SizePotionData data = SizePotionData.create(2, 95);
		ItemStack potion = data.toItemStack("");
		ItemStack remainder = potion.finishUsingItem(helper.getLevel(), player);
		helper.assertTrue(potion.isEmpty(), "Last potion is consumed");
		helper.assertValueEqual(remainder.getItem(), Items.GLASS_BOTTLE, "Glass bottle remainder");
		helper.assertValueEqual(player.getAttributeValue(Attributes.SCALE), 2.0, "Player scale");
		helper.assertValueEqual(player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE), 7.5, "Block reach");
		helper.assertValueEqual(player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE), 6.0, "Entity reach");
		helper.assertValueEqual(player.getEffect(MobEffects.SPEED).getAmplifier(), 1, "Speed II");
		helper.assertTrue(SizePotion.isActive(player), "Transformation timer is active");
		helper.succeed();
	}

	@GameTest
	public void creativeDrinkPreservesPotion(GameTestHelper helper) {
		ServerPlayer player = player(helper, GameType.CREATIVE);
		ItemStack potion = SizePotionData.create(0.5, 95).toItemStack("");
		ItemStack remainder = potion.finishUsingItem(helper.getLevel(), player);
		helper.assertValueEqual(potion.getCount(), 1, "Creative potion is retained");
		helper.assertTrue(remainder == potion, "Creative remainder is the same potion");
		helper.assertValueEqual(player.getAttributeValue(Attributes.SCALE), 0.5, "Shrinking scale");
		helper.assertValueEqual(player.getEffect(MobEffects.SLOWNESS).getAmplifier(), 1, "Slowness II");
		helper.succeed();
	}

	@GameTest
	public void activeTransformationRejectsAnotherPotion(GameTestHelper helper) {
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		SizePotionData.create(2, 95).toItemStack("").finishUsingItem(helper.getLevel(), player);
		ItemStack second = SizePotionData.create(0.5, 30).toItemStack("");
		player.setItemInHand(InteractionHand.OFF_HAND, second);
		int[] resends = trackInventoryResends(player);
		helper.assertTrue(second.finishUsingItem(helper.getLevel(), player) == second, "Rejected potion is returned");
		helper.assertValueEqual(second.getCount(), 1, "Rejected potion is retained");
		helper.assertValueEqual(player.getAttributeValue(Attributes.SCALE), 2.0, "Existing scale is retained");
		helper.assertValueEqual(resends[0], 1, "Rejected offhand drink resends the inventory to correct client prediction");
		helper.succeed();
	}

	@GameTest
	public void invalidAndNormalScalePotionsAreNotConsumed(GameTestHelper helper) {
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		for (double scale : new double[] {1, 0, Double.NaN, Double.POSITIVE_INFINITY}) {
			ItemStack potion = SizePotionData.create(2, 95).toItemStack("");
			var tag = potion.get(DataComponents.CUSTOM_DATA).copyTag();
			tag.putDouble("scale", scale);
			potion.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
			helper.assertTrue(potion.finishUsingItem(helper.getLevel(), player) == potion, "Invalid potion is returned");
			helper.assertValueEqual(potion.getCount(), 1, "Invalid potion is retained");
			helper.assertFalse(SizePotion.isActive(player), "Invalid data does not transform the player");
		}
		helper.succeed();
	}

	@GameTest
	public void milkRestoresSizeAfterConsumingLastBucket(GameTestHelper helper) {
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		SizePotionData.create(2, 95).toItemStack("").finishUsingItem(helper.getLevel(), player);
		ItemStack milk = new ItemStack(Items.MILK_BUCKET);
		ItemStack remainder = milk.finishUsingItem(helper.getLevel(), player);
		helper.assertTrue(milk.isEmpty(), "Last milk bucket is consumed");
		helper.assertValueEqual(remainder.getItem(), Items.BUCKET, "Bucket remainder");
		helper.assertValueEqual(player.getAttributeValue(Attributes.SCALE), 1.0, "Default scale restored");
		helper.assertFalse(SizePotion.isActive(player), "Timer cleared by milk");
		helper.assertFalse(player.hasEffect(MobEffects.SPEED), "Speed cleared by milk");
		helper.assertTrue(player.hasEffect(MobEffects.SLOW_FALLING), "Slow Falling survives vanilla milk processing");
		helper.succeed();
	}

	@GameTest
	public void ordinaryPotionKeepsVanillaBehavior(GameTestHelper helper) {
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		ItemStack potion = new ItemStack(Items.POTION);
		ItemStack remainder = potion.finishUsingItem(helper.getLevel(), player);
		helper.assertValueEqual(remainder.getItem(), Items.GLASS_BOTTLE, "Ordinary potion returns a bottle");
		helper.assertFalse(SizePotion.isActive(player), "Ordinary potion does not start a transformation");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void interruptedDrinkDoesNotTransformOrConsume(GameTestHelper helper) {
		ServerPlayer player = player(helper, GameType.SURVIVAL);
		ItemStack potion = SizePotionData.create(2, 95).toItemStack("");
		player.setItemInHand(InteractionHand.MAIN_HAND, potion);
		potion.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		player.releaseUsingItem();
		helper.runAfterDelay(35, () -> {
			helper.assertValueEqual(potion.getCount(), 1, "Interrupted potion is retained");
			helper.assertFalse(SizePotion.isActive(player), "Interrupted drink does not transform");
			helper.succeed();
		});
	}

	private static ServerPlayer player(GameTestHelper helper, GameType gameType) {
		UUID id = UUID.randomUUID();
		var profile = new GameProfile(id, "test-" + id.toString().substring(0, 8));
		var server = helper.getLevel().getServer();
		ServerPlayer player = new ServerPlayer(server, helper.getLevel(), profile, ClientInformation.createDefault()) {
			@Override
			public GameType gameMode() {
				return gameType;
			}

			@Override
			public boolean isClientAuthoritative() {
				return false;
			}
		};
		gameType.updatePlayerAbilities(player.getAbilities());
		// Effect and message delivery require a listener even without a real client.
		player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND),
			player, CommonListenerCookie.createInitial(profile, false));
		return player;
	}

	private static int[] trackInventoryResends(ServerPlayer player) {
		int[] resends = {0};
		player.inventoryMenu.setSynchronizer(new ContainerSynchronizer() {
			@Override
			public void sendInitialData(AbstractContainerMenu menu, List<ItemStack> items, ItemStack carried, int[] data) {
				resends[0]++;
			}

			@Override
			public void sendSlotChange(AbstractContainerMenu menu, int slot, ItemStack stack) { }

			@Override
			public void sendCarriedChange(AbstractContainerMenu menu, ItemStack stack) { }

			@Override
			public void sendDataChange(AbstractContainerMenu menu, int index, int value) { }

			@Override
			public RemoteSlot createSlot() {
				return RemoteSlot.PLACEHOLDER;
			}
		});
		resends[0] = 0;
		return resends;
	}
}
