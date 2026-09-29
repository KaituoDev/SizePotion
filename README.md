# SizePotion

Fabric mod for Minecraft 26.2. Use `/getsizepotion <scale> <duration-seconds> [description words...]` as a player to receive a drinkable size potion. The command requires the `kaituoprops.getsizepotion` permission node (Fabric identifier `minecraft:kaituoprops.getsizepotion`); without a permission provider, moderator level or higher can use it.

The scale must be from 0.1 through 16, excluding 1. Duration must be from 1 through 65,535 seconds. Each description word becomes a lore line, and lowercase `&` color and formatting codes are supported. The potion stores its calculated reach, fall, and effect values as custom item data. See [SIZE_POTION.md](SIZE_POTION.md) for the formulas and examples.

Drinking a size potion changes player scale, block and entity reach, fall damage multiplier, and safe fall distance. It also grants Speed and Jump Boost when enlarging, or Slowness when shrinking. Another size potion cannot be consumed while the transformation is active. The `SizeChangeDuration` scoreboard objective tracks remaining ticks; drinking milk or reaching zero restores the listed default attributes, removes those three effects, and grants Slow Falling I for 60 ticks.

The countdown pauses while a player is offline. Cleanup uses fixed vanilla defaults rather than saving previous attribute and effect values, so it may overwrite changes from other sources.

Build with `./gradlew build` and install the resulting jar from `build/libs` alongside Fabric API.
