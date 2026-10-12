# Size potion implementation

SizePotion runs only on the dedicated Fabric server; unmodified Minecraft 26.2 clients can connect. The size potion is a vanilla drinkable `minecraft:potion` carrying its transformation parameters in `minecraft:custom_data`. Vanilla potion contents, custom name, and lore give it its appearance. The mod registers no custom items, potions, effects, or data components.

Its behavior is implemented by [SizePotionData](src/main/java/sizepotion/SizePotionData.java) (item creation and data), [ItemStackMixin](src/main/java/sizepotion/mixin/ItemStackMixin.java) (server consumption), and [SizePotion](src/main/java/sizepotion/SizePotion.java) (command, attributes, effects, and timer).

## Creating a potion

`/getsizepotion <scale> <duration-seconds> [description words...]`

- Requires a player sender with `kaituoprops.getsizepotion` permission.
- Accepts scales from **0.1 to 16**, excluding **1**, and durations from **1 to 65,535 seconds**.
- Gives the potion to the sender, or drops it if their inventory is full. Each optional description word becomes a separate lore line; supported lowercase `&` formatting codes become text component styles.
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

Effect levels in the table are **one-based**: `1` means level I, and `0` means no effect. Consumption subtracts one to obtain Minecraft's zero-based amplifier and only adds effects with a nonnegative amplifier. Each effect lasts `duration * 20` ticks. The mod does not directly set a movement-speed or jump-strength attribute; it uses vanilla Speed, Slowness, and Jump Boost effects. Actual movement and jumping therefore also depend on Minecraft's effect handling and any other active modifiers.

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

1. The client uses the vanilla drinking animation and sounds. On the server, the `ItemStack.finishUsingItem` wrapper checks for `minecraft:potion` and the presence of `scale` in `minecraft:custom_data`. It reads the stored scale, duration, reach, fall parameters, and effect levels; it does not recalculate them on consumption. Releasing use or switching items before completion does not apply the transformation.
2. The player's name indexes the `SizeChangeDuration` dummy objective on the main scoreboard. A missing score is initialized to zero. A positive score cancels consumption, preventing another size potion from stacking or refreshing the transformation.
3. A scale of one or invalid data cancels consumption. A rejected drink forces inventory synchronization to correct a vanilla client's predicted empty bottle, including offhand use.
4. Valid potions complete vanilla consumption, including glass bottle remainders and creative mode item retention. The server then sets the player's scale, block and entity interaction range, fall damage multiplier, and safe fall distance directly through vanilla attributes. Vanilla attribute packets synchronize the size and reach changes to clients.
5. The applicable potion effects are added, and the scoreboard countdown is set to `duration * 20`. A scale greater than one selects enlargement messaging; a scale below one selects shrinking messaging. The player receives the duration and a milk-removal hint.

The mod processes the countdown at the end of every server tick. For online players, scores above one decrease by one; a remaining nonzero score at or below one triggers cleanup and an expiry message. Duration assumes 20 ticks per second, so server lag can extend elapsed real time. Offline players are skipped.

## Ending the effect

Drinking milk while the countdown is positive, or allowing the timer to expire, restores the five attribute base values listed above and sets the countdown to zero. Both paths explicitly remove Speed, Jump Boost, and Slowness, then add **Slow Falling I for 60 ticks** (three seconds at 20 TPS). Milk cleanup runs after vanilla milk consumption, so the new Slow Falling effect is preserved. The wrapper remembers that the item was milk before consumption empties the last bucket.

Cleanup restores hardcoded defaults, not a saved snapshot of the player's original attributes or potion effects. Consequently, it can overwrite custom base values and remove Speed, Jump Boost, or Slowness acquired from other sources. No explicit health, attack damage, armor, mining speed, or step-height changes are made by this implementation.

## Implementation limitations

- The countdown skips offline players and resumes when they return. There is no explicit shutdown cleanup.
- The `scale` tag identifies a size potion. Consumption validates the scale, duration, and finite nonnegative attribute/effect values, but operators can create custom data with different values from the command's formulas.
- Cleanup restores fixed vanilla defaults rather than saving the previous attributes and effects, so changes from other sources may be overwritten.
- The mod is marked for the physical server environment; installing it in a client does not enable it for singleplayer or LAN hosting.

`./gradlew build` runs the unit test and dedicated-server GameTests, including vanilla item serialization, consumption, rejection, and milk cleanup.
