package sizepotion.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import sizepotion.SizePotion;
import sizepotion.SizePotionData;

@Mixin(ItemStack.class)
public class ItemStackMixin {
	@WrapMethod(method = "finishUsingItem")
	private ItemStack sizepotion$consume(Level level, LivingEntity user, Operation<ItemStack> original) {
		if (!(user instanceof ServerPlayer player)) return original.call(level, user);
		ItemStack stack = (ItemStack) (Object) this;
		// Consumption can empty this stack, making its item appear to be AIR.
		// Capture both the transformation and milk identity before vanilla runs.
		boolean milk = stack.getItem() == Items.MILK_BUCKET;
		SizePotionData data = SizePotionData.fromItemStack(stack).orElse(null);
		if (data != null) {
			if (SizePotion.isActive(player)) {
				SizePotion.rejectActivePotion(player);
				return sizepotion$keepPotion(player, stack);
			} else if (data.scale() == 1) {
				SizePotion.rejectNormalSize(player);
				return sizepotion$keepPotion(player, stack);
			} else if (!data.isValid()) {
				SizePotion.rejectInvalidPotion(player);
				return sizepotion$keepPotion(player, stack);
			}
		}

		ItemStack remainder = original.call(level, user);
		if (data != null) {
			SizePotion.apply(player, data);
		} else if (milk) {
			// Milk clears effects during consumption; add Slow Falling afterward.
			SizePotion.endIfActive(player);
		}
		return remainder;
	}

	@Unique
	private static ItemStack sizepotion$keepPotion(ServerPlayer player, ItemStack stack) {
		// Vanilla clients predict consumption. Force a resend even though the
		// server's inventory has not changed, correcting the predicted bottle.
		player.inventoryMenu.sendAllDataToRemote();
		if (player.containerMenu != player.inventoryMenu) player.containerMenu.sendAllDataToRemote();
		return stack;
	}
}
