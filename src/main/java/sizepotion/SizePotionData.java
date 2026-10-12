package sizepotion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

public record SizePotionData(double scale, int durationSeconds, double blockInteractionRange,
		double entityInteractionRange, double fallDamageMultiplier, double safeFallDistance,
		int speedEffectLevel, int jumpEffectLevel, int slownessEffectLevel) {

	public static final Codec<SizePotionData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.DOUBLE.fieldOf("scale").forGetter(SizePotionData::scale),
		Codec.INT.fieldOf("duration").forGetter(SizePotionData::durationSeconds),
		Codec.DOUBLE.fieldOf("blockInteractionRange").forGetter(SizePotionData::blockInteractionRange),
		Codec.DOUBLE.fieldOf("entityInteractionRange").forGetter(SizePotionData::entityInteractionRange),
		Codec.DOUBLE.fieldOf("fallDamageMultiplier").forGetter(SizePotionData::fallDamageMultiplier),
		Codec.DOUBLE.fieldOf("safeFallDistance").forGetter(SizePotionData::safeFallDistance),
		Codec.INT.fieldOf("speedEffectLevel").forGetter(SizePotionData::speedEffectLevel),
		Codec.INT.fieldOf("jumpEffectLevel").forGetter(SizePotionData::jumpEffectLevel),
		Codec.INT.fieldOf("slownessEffectLevel").forGetter(SizePotionData::slownessEffectLevel)
	).apply(instance, SizePotionData::new));

	public static SizePotionData create(double scale, int durationSeconds) {
		int enlargementLevel = scale > 1 ? (int) Math.floor(scale) : 0;
		int slownessLevel = scale < 1 ? Math.min(3, (int) Math.floor(4.1 - 4 * scale)) : 0;
		return new SizePotionData(scale, durationSeconds,
			Math.max(3, round1(3 * scale + 1.5)),
			Math.max(2, round1(3 * scale)),
			0.5,
			Math.max(2, round1(2 * scale + 1)),
			enlargementLevel, enlargementLevel, slownessLevel);
	}

	public static Optional<SizePotionData> fromItemStack(ItemStack stack) {
		if (stack.getItem() != Items.POTION) return Optional.empty();
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) return Optional.empty();
		CompoundTag tag = customData.copyTag();
		if (!tag.contains("scale")) return Optional.empty();
		return Optional.of(new SizePotionData(
			tag.getDoubleOr("scale", 0), tag.getIntOr("duration", 0),
			tag.getDoubleOr("blockInteractionRange", 0),
			tag.getDoubleOr("entityInteractionRange", 0),
			tag.getDoubleOr("fallDamageMultiplier", 0),
			tag.getDoubleOr("safeFallDistance", 0),
			tag.getIntOr("speedEffectLevel", 0),
			tag.getIntOr("jumpEffectLevel", 0),
			tag.getIntOr("slownessEffectLevel", 0)));
	}

	public boolean isValid() {
		return Double.isFinite(scale) && scale >= 0.1 && scale <= 16 && scale != 1
			&& durationSeconds >= 1 && durationSeconds <= 65535
			&& Double.isFinite(blockInteractionRange) && blockInteractionRange >= 0
			&& Double.isFinite(entityInteractionRange) && entityInteractionRange >= 0
			&& Double.isFinite(fallDamageMultiplier) && fallDamageMultiplier >= 0
			&& Double.isFinite(safeFallDistance) && safeFallDistance >= 0
			&& speedEffectLevel >= 0 && jumpEffectLevel >= 0 && slownessEffectLevel >= 0;
	}

	public ItemStack toItemStack(String description) {
		boolean enlargement = scale > 1;
		int color = enlargement ? 0x3AE4FF : 0xFFE164;
		String name = enlargement ? "放大药水" : "缩小药水";
		ItemStack stack = new ItemStack(Items.POTION);
		stack.set(DataComponents.POTION_CONTENTS,
			new PotionContents(Optional.empty(), Optional.of(color), List.of(), Optional.empty()));
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(style -> style.withColor(color)));

		List<Component> lore = new ArrayList<>();
		String scaleText = BigDecimal.valueOf(scale).stripTrailingZeros().toPlainString();
		lore.add(Component.literal((enlargement ? "放大" : "缩小") + "至 " + scaleText + " 倍")
			.withStyle(style -> style.withColor(color)));
		lore.add(Component.literal(String.format(Locale.ROOT, "持续时间: %02d:%02d",
			durationSeconds / 60, durationSeconds % 60)).withStyle(ChatFormatting.GRAY));
		if (!description.isBlank()) {
			for (String word : description.split("\\s+")) {
				lore.add(formatLore(word));
			}
		}
		stack.set(DataComponents.LORE, new ItemLore(lore));

		CompoundTag tag = new CompoundTag();
		tag.putDouble("scale", scale);
		tag.putInt("duration", durationSeconds);
		tag.putDouble("blockInteractionRange", blockInteractionRange);
		tag.putDouble("entityInteractionRange", entityInteractionRange);
		tag.putDouble("fallDamageMultiplier", fallDamageMultiplier);
		tag.putDouble("safeFallDistance", safeFallDistance);
		tag.putInt("speedEffectLevel", speedEffectLevel);
		tag.putInt("jumpEffectLevel", jumpEffectLevel);
		tag.putInt("slownessEffectLevel", slownessEffectLevel);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	private static double round1(double value) {
		return Math.round(value * 10) / 10.0;
	}

	private static Component formatLore(String word) {
		MutableComponent result = Component.empty();
		Style style = Style.EMPTY;
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < word.length(); i++) {
			if (word.charAt(i) == '&' && i + 1 < word.length()) {
				char code = word.charAt(i + 1);
				if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')
						|| (code >= 'k' && code <= 'o') || code == 'r') {
					if (!text.isEmpty()) {
						result.append(Component.literal(text.toString()).withStyle(style));
						text.setLength(0);
					}
					style = style.applyFormat(ChatFormatting.getByCode(code));
					i++;
					continue;
				}
			}
			text.append(word.charAt(i));
		}
		if (!text.isEmpty()) result.append(Component.literal(text.toString()).withStyle(style));
		return result;
	}
}
