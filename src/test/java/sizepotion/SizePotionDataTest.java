package sizepotion;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SizePotionDataTest {
	@Test
	void transformationDataSurvivesSerialization() {
		SizePotionData original = SizePotionData.create(0.8, 95);
		var encoded = SizePotionData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
		var decoded = SizePotionData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
		assertEquals(original, decoded);
		assertEquals(3.9, decoded.blockInteractionRange());
		assertEquals(2.4, decoded.entityInteractionRange());
		assertEquals(2.6, decoded.safeFallDistance());
	}

}
