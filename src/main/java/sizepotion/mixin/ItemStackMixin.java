package sizepotion.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sizepotion.SizePotion;
import sizepotion.SizePotionData;

@Mixin(ItemStack.class)
public class ItemStackMixin {
	@Unique
	private SizePotionData sizepotion$pendingData;

	@Inject(method = "finishUsingItem", at = @At("HEAD"), cancellable = true)
	private void sizepotion$beforeConsumption(Level level, LivingEntity user,
			CallbackInfoReturnable<ItemStack> callback) {
		sizepotion$pendingData = null;
		if (!(user instanceof ServerPlayer player)) return;
		ItemStack stack = (ItemStack) (Object) this;
		if (stack.getItem() != Items.POTION) return;
		SizePotionData.fromItemStack(stack).ifPresent(data -> {
			if (SizePotion.isActive(player)) {
				SizePotion.rejectActivePotion(player);
				callback.setReturnValue(stack);
			} else if (data.scale() == 1) {
				SizePotion.rejectNormalSize(player);
				callback.setReturnValue(stack);
			} else {
				sizepotion$pendingData = data;
			}
		});
	}

	@Inject(method = "finishUsingItem", at = @At("RETURN"))
	private void sizepotion$afterConsumption(Level level, LivingEntity user,
			CallbackInfoReturnable<ItemStack> callback) {
		if (!(user instanceof ServerPlayer player)) return;
		ItemStack stack = (ItemStack) (Object) this;
		if (stack.getItem() == Items.MILK_BUCKET) {
			SizePotion.endIfActive(player);
		} else if (sizepotion$pendingData != null) {
			SizePotionData data = sizepotion$pendingData;
			sizepotion$pendingData = null;
			SizePotion.apply(player, data);
		}
	}
}
