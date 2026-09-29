package sizepotion;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

public final class SizePotion implements ModInitializer {
	private static final String DURATION_OBJECTIVE = "SizeChangeDuration";
	private static final Identifier COMMAND_PERMISSION = Identifier.withDefaultNamespace("kaituoprops.getsizepotion");

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			var description = Commands.argument("description", StringArgumentType.greedyString())
				.executes(context -> givePotion(context.getSource().getPlayerOrException(),
					DoubleArgumentType.getDouble(context, "scale"),
					IntegerArgumentType.getInteger(context, "duration-seconds"),
					StringArgumentType.getString(context, "description")));
			var duration = Commands.argument("duration-seconds", IntegerArgumentType.integer(1, 65535))
				.executes(context -> givePotion(context.getSource().getPlayerOrException(),
					DoubleArgumentType.getDouble(context, "scale"),
					IntegerArgumentType.getInteger(context, "duration-seconds"), ""))
				.then(description);
			dispatcher.register(Commands.literal("getsizepotion")
				.requires(PermissionPredicates.require(COMMAND_PERMISSION, PermissionLevel.MODERATORS))
				.then(Commands.argument("scale", DoubleArgumentType.doubleArg()).then(duration)));
		});
		ServerTickEvents.END_SERVER_TICK.register(SizePotion::tick);
	}

	private static int givePotion(ServerPlayer player, double scale, int durationSeconds, String description) {
		if (!Double.isFinite(scale) || scale < 0.1 || scale > 16 || scale == 1) {
			player.sendSystemMessage(Component.literal("倍率必须在 0.1 到 16 之间，且不能为 1。"));
			return 0;
		}

		var potion = SizePotionData.create(scale, durationSeconds).toItemStack(description);
		if (!player.getInventory().add(potion)) {
			player.drop(potion, false);
		}
		player.sendSystemMessage(Component.literal("已获得" + (scale > 1 ? "放大" : "缩小") + "药水。"));
		return 1;
	}

	public static boolean isActive(ServerPlayer player) {
		Scoreboard scoreboard = player.level().getServer().getScoreboard();
		Objective objective = objective(scoreboard);
		ReadOnlyScoreInfo info = scoreboard.getPlayerScoreInfo(player, objective);
		if (info == null) {
			scoreboard.getOrCreatePlayerScore(player, objective).set(0);
			return false;
		}
		return info.value() > 0;
	}

	public static void apply(ServerPlayer player, SizePotionData data) {
		setAttribute(player, Attributes.SCALE, data.scale());
		setAttribute(player, Attributes.BLOCK_INTERACTION_RANGE, data.blockInteractionRange());
		setAttribute(player, Attributes.ENTITY_INTERACTION_RANGE, data.entityInteractionRange());
		setAttribute(player, Attributes.FALL_DAMAGE_MULTIPLIER, data.fallDamageMultiplier());
		setAttribute(player, Attributes.SAFE_FALL_DISTANCE, data.safeFallDistance());

		int ticks = data.durationSeconds() * 20;
		addEffect(player, MobEffects.SPEED, data.speedEffectLevel(), ticks);
		addEffect(player, MobEffects.JUMP_BOOST, data.jumpEffectLevel(), ticks);
		addEffect(player, MobEffects.SLOWNESS, data.slownessEffectLevel(), ticks);
		Scoreboard scoreboard = player.level().getServer().getScoreboard();
		scoreboard.getOrCreatePlayerScore(player, objective(scoreboard)).set(ticks);
		player.sendSystemMessage(Component.literal("你已" + (data.scale() > 1 ? "放大" : "缩小")
			+ "，持续 " + data.durationSeconds() + " 秒。喝牛奶可以提前解除。"));
	}

	public static void rejectActivePotion(ServerPlayer player) {
		player.sendSystemMessage(Component.literal("你已经处于体型变化状态。请等待结束或喝牛奶解除。"));
	}

	public static void rejectNormalSize(ServerPlayer player) {
		player.sendSystemMessage(Component.literal("倍率为 1 的体型药水无效。"));
	}

	public static void endIfActive(ServerPlayer player) {
		Scoreboard scoreboard = player.level().getServer().getScoreboard();
		Objective objective = scoreboard.getObjective(DURATION_OBJECTIVE);
		if (objective == null) return;
		ReadOnlyScoreInfo info = scoreboard.getPlayerScoreInfo(player, objective);
		if (info != null && info.value() > 0) {
			cleanup(player, scoreboard.getOrCreatePlayerScore(player, objective));
			player.sendSystemMessage(Component.literal("体型变化已解除。"));
		}
	}

	private static void tick(MinecraftServer server) {
		Scoreboard scoreboard = server.getScoreboard();
		Objective objective = scoreboard.getObjective(DURATION_OBJECTIVE);
		if (objective == null) return;

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ReadOnlyScoreInfo info = scoreboard.getPlayerScoreInfo(player, objective);
			if (info == null || info.value() <= 0) continue;
			ScoreAccess score = scoreboard.getOrCreatePlayerScore(player, objective);
			if (info.value() > 1) {
				score.set(info.value() - 1);
			} else {
				cleanup(player, score);
				player.sendSystemMessage(Component.literal("体型变化时间已结束。"));
			}
		}
	}

	private static Objective objective(Scoreboard scoreboard) {
		Objective objective = scoreboard.getObjective(DURATION_OBJECTIVE);
		return objective != null ? objective : scoreboard.addObjective(DURATION_OBJECTIVE,
			ObjectiveCriteria.DUMMY, Component.literal(DURATION_OBJECTIVE),
			ObjectiveCriteria.RenderType.INTEGER, false, null);
	}

	private static void cleanup(ServerPlayer player, ScoreAccess score) {
		setAttribute(player, Attributes.SCALE, 1);
		setAttribute(player, Attributes.BLOCK_INTERACTION_RANGE, 4.5);
		setAttribute(player, Attributes.ENTITY_INTERACTION_RANGE, 3);
		setAttribute(player, Attributes.FALL_DAMAGE_MULTIPLIER, 1);
		setAttribute(player, Attributes.SAFE_FALL_DISTANCE, 3);
		score.set(0);
		player.removeEffect(MobEffects.SPEED);
		player.removeEffect(MobEffects.JUMP_BOOST);
		player.removeEffect(MobEffects.SLOWNESS);
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0));
	}

	private static void setAttribute(ServerPlayer player, Holder<Attribute> attribute, double value) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) instance.setBaseValue(value);
	}

	private static void addEffect(ServerPlayer player, Holder<MobEffect> effect, int level, int ticks) {
		if (level > 0) player.addEffect(new MobEffectInstance(effect, ticks, level - 1));
	}
}
