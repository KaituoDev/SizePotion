# Size potion implementation

The size potion is a normal drinkable `Material.POTION` carrying custom NBT data. Its behavior is implemented by [GetSizeChangePotionCommand](src/main/java/fun/kaituo/kaituoSurvivalProps/commands/GetSizeChangePotionCommand.java) (creation), [SizeChangePotion](src/main/java/fun/kaituo/kaituoSurvivalProps/props/SizeChangePotion.java) (consumption and cleanup), and [KaituoSurvivalProps](src/main/java/fun/kaituo/kaituoSurvivalProps/KaituoSurvivalProps.java) (registration and timer).

## Creating a potion

`/getsizepotion <scale> <duration-seconds> [description words...]`

- Requires a player sender with `kaituoprops.getsizepotion` permission.
- Accepts scales from **0.1 to 16**, excluding **1**, and durations from **1 to 65,535 seconds**.
- Gives the potion to the sender. Each optional description argument becomes a separate lore line; supported lowercase `&` formatting codes are converted to `§` codes.
- Enlargement potions are cyan (`#3AE4FF`), named `放大药水`; shrinking potions are yellow (`#FFE164`), named `缩小药水`. A colored enlargement/shrinking label and `MM:SS` duration are prepended to the lore.

## Size and accompanying changes

Let `s` be the requested scale, and `round1(x) = Math.round(x * 10) / 10.0`. The following values are calculated when the item is created and stored in NBT. Attribute values are absolute base values, not modifiers added to the player's previous values.

| Property / NBT key | Value while active | Value set on cleanup |
| --- | --- | --- |
| Size (`scale`) | `s` | `1` |
| Block reach (`blockInteractionRange`) | `round1(3*s + 1.5)` if `3*s + 1.5 >= 3`; otherwise `3` | `4.5` |
| Entity reach (`entityInteractionRange`) | `round1(3*s)` if `3*s >= 2`; otherwise `2` | `3` |
| Fall damage multiplier (`fallDamageMultiplier`) | `0.5` for both enlargement and shrinking | `1` |
| Safe fall distance (`safeFallDistance`) | `round1(2*s + 1)` if `2*s + 1 >= 2`; otherwise `2` | `3` |
| Speed (`speedEffectLevel`) | Enlargement: `floor(s)`; shrinking: `0` | Effect removed |
| Jump Boost (`jumpEffectLevel`) | Enlargement: `floor(s)`; shrinking: `0` | Effect removed |
| Slowness (`slownessEffectLevel`) | Enlargement: `0`; shrinking: `min(3, floor(4.1 - 4*s))` | Effect removed |

Reach and safe fall distance are expressed in blocks. The fall damage multiplier halves the damage multiplier while the safe fall distance separately changes the threshold for fall damage.

Effect levels in the table are **one-based**: `1` means level I, and `0` means no effect. Consumption subtracts one to obtain Bukkit's zero-based amplifier and only adds effects with a nonnegative amplifier. Each effect lasts `duration * 20` ticks. The plugin does not directly set a movement-speed or jump-strength attribute; it uses vanilla Speed, Slowness, and Jump Boost effects. Actual movement and jumping therefore also depend on Minecraft's effect handling and any other active modifiers.

Enlargement grants matching Speed and Jump Boost levels from I through XVI. Shrinking grants Slowness up to III, with no Speed or Jump Boost added. For scales above `0.775` and below `1`, the formula produces Slowness level `0`, so no Slowness is added.

### Examples

| Scale | Block reach | Entity reach | Safe fall distance | Speed | Jump Boost | Slowness |
| --- | --- | --- | --- | --- | --- | --- |
| `0.1` | `3` | `2` | `2` | None | None | III |
| `0.5` | `3` | `2` | `2` | None | None | II |
| `0.8` | `3.9` | `2.4` | `2.6` | None | None | None |
| `1.5` | `6` | `4.5` | `4` | I | I | None |
| `2` | `7.5` | `6` | `5` | II | II | None |
| `16` | `49.5` | `48` | `33` | XVI | XVI | None |

Every example also sets the fall damage multiplier to `0.5`.

## Applying and timing the transformation

1. `PlayerItemConsumeEvent` checks for `Material.POTION` and the presence of the `scale` NBT tag. It reads the stored scale, duration, reach, fall parameters, and effect levels; it does not recalculate them on consumption.
2. The player's name indexes the `SizeChangeDuration` dummy objective on the main scoreboard. A missing score is initialized to zero. A positive score cancels consumption, preventing another size potion from stacking or refreshing the transformation.
3. A scale greater than one selects enlargement messaging; a scale below one selects shrinking messaging. A scale of one is rejected and consumption is cancelled. The player receives the duration and a milk-removal hint.
4. Console `attribute <player> <attribute> base set <value>` commands set `minecraft:generic.scale`, `minecraft:player.block_interaction_range`, `minecraft:player.entity_interaction_range`, `minecraft:generic.fall_damage_multiplier`, and `minecraft:generic.safe_fall_distance`.
5. The applicable potion effects are added, and the scoreboard countdown is set to `duration * 20`.

The plugin schedules `Countdown` every server tick. For online players, scores above one decrease by one; a remaining nonzero score at or below one triggers cleanup and an expiry message. Duration assumes 20 ticks per second, so server lag can extend elapsed real time. Offline players are skipped.

## Ending the effect

Drinking milk while the countdown is positive, or allowing the timer to expire, restores the five attribute base values listed above and sets the countdown to zero. Both paths explicitly remove Speed, Jump Boost, and Slowness, then add **Slow Falling I for 60 ticks** (three seconds at 20 TPS). The milk listener runs at `EventPriority.LOW`; normal milk processing may affect the final potion effects seen in-game.

Cleanup restores hardcoded defaults, not a saved snapshot of the player's original attributes or potion effects. Consequently, it can overwrite custom base values and remove Speed, Jump Boost, or Slowness acquired from other sources. No explicit health, attack damage, armor, mining speed, or step-height changes are made by this implementation.

## Implementation limitations

- The already-transformed message suggests reconnecting to clear the effect, but this repository has no join/quit cleanup handler. Its countdown simply skips offline players and resumes processing when they are online again.
- Plugin disable unregisters listeners and cancels scheduled tasks without explicitly restoring transformed players.
- The `scale` tag is the only item-identification check. Consumption trusts the other NBT values and does not repeat the command's range validation. The command also does not explicitly reject non-finite floating-point input such as `NaN`.
- Neither consume handler uses `ignoreCancelled = true` or checks whether another listener has cancelled the event. Attribute-command success and potion-effect application results are not checked.

This document describes the repository's code; it is not an in-game verification of server behavior.
