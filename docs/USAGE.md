# MobGift - Usage Documentation

MobGift is a Paper/Spigot plugin that adds custom drops when a player kills a mob.

The plugin ignores kills without a player killer and ignores players. For mobs killed by a player, it can either keep vanilla drops or fully replace them with configured drops.

## Installation

1. Build the plugin:

```bash
mvn package
```

2. Copy the JAR from `target/` into the server's `plugins/` folder.
3. Start or restart the server.
4. The config will be generated at `plugins/MobGift/config.yml`.

## Commands

```text
/mobgift help
/mobgift reload
/mobgift list
/mobgift info <dropId>
/mobgift give <player> <dropId> [amount]
/mobgift cooldown reset <player> [dropId|all]
/mobgift stats [top|drop <dropId>|player <player>]
/mobgift stats export
/mobgift stats reset <all|drop <dropId>|player <player>>
/mobgift test <dropId> [mob] [world] [biome]
/mobgift preview <mob> [world] [biome]
/mobgift validate
/mobgift gui
```

Aliases:

```text
/mgift
```

Permissions:

- `mobgift.reload`: allows `/mobgift reload`.
- `mobgift.list`: allows `/mobgift list`.
- `mobgift.info`: allows `/mobgift info`.
- `mobgift.give`: allows `/mobgift give`.
- `mobgift.cooldown`: allows `/mobgift cooldown reset`.
- `mobgift.stats`: allows `/mobgift stats`.
- `mobgift.stats.export`: allows `/mobgift stats export`.
- `mobgift.stats.reset`: allows `/mobgift stats reset`.
- `mobgift.test`: allows `/mobgift test`.
- `mobgift.preview`: allows `/mobgift preview`.
- `mobgift.validate`: allows `/mobgift validate`.
- `mobgift.gui`: allows `/mobgift gui`.
- `mobgift.gui.edit`: allows editing drops from `/mobgift gui`.
- `mobgift.admin`: grants all MobGift admin permissions.

## Default Config

The default config is designed to be realistic and avoid overly valuable items. The plugin keeps vanilla drops and adds small bonuses.

```yaml
config-version: 8

settings:
  debug: false
  allow-spawner-mobs: true
  persist-cooldowns: false
  reward-log:
    enabled: false
    file: rewards.csv

drops:
  replace-default-drops: false
  items:
    zombie_rotten_flesh:
      material: ROTTEN_FLESH
      amount:
        min: 1
        max: 2
      chance: 0.35
      mobs:
        - ZOMBIE
        - HUSK
        - DROWNED
      worlds:
        - ALL
      biomes:
        - ALL
      looting-bonus:
        chance-per-level: 0.02
        amount-per-level: 0
```

## `replace-default-drops`

```yaml
replace-default-drops: false
```

Values:

- `false`: keeps vanilla drops and adds the plugin drops as bonuses.
- `true`: removes vanilla drops and only keeps drops from `drops.items`.

Recommended for normal survival:

```yaml
replace-default-drops: false
```

## Adding A Drop

All drops are placed under:

```yaml
drops:
  items:
```

Example:

```yaml
drops:
  items:
    zombie_extra_flesh:
      material: ROTTEN_FLESH
      amount: 1
      chance: 0.25
      mobs:
        - ZOMBIE
```

`zombie_extra_flesh` is only an internal ID. You can choose any name, but it must be unique.

## Drop Fields

`material`

The item to drop. Use Bukkit/Paper material names. A drop can omit `material` only if it has another reward such as `xp` or `commands`.

```yaml
material: BONE
```

`enabled`

Optional toggle for temporarily disabling a drop without deleting it.

```yaml
enabled: false
```

Disabled drops remain visible in `/mobgift list`, `/mobgift test`, and the GUI, but they are ignored by `/mobgift preview` and are not awarded on mob kills.

`amount`

Fixed amount:

```yaml
amount: 1
```

Random range:

```yaml
amount:
  min: 1
  max: 3
```

If a value is below `1`, the plugin uses `1`.

`chance`

The chance for the drop to occur:

```yaml
chance: 0.25
```

Examples:

- `0.05` = 5%
- `0.10` = 10%
- `0.25` = 25%
- `0.50` = 50%
- `1.00` = 100%

`cooldown-seconds`

Optional per-player cooldown after the drop is awarded.

```yaml
cooldown-seconds: 300
```

This example prevents the same player from receiving the same drop again for 5 minutes. By default, cooldowns are kept in memory and reset when the server restarts.

`settings.persist-cooldowns`

Optional global setting for keeping active cooldowns across clean server restarts.

```yaml
settings:
  persist-cooldowns: true
```

When enabled, MobGift saves active cooldowns to `plugins/MobGift/cooldowns.yml` when cooldowns change and when the plugin shuts down cleanly.

`settings.reward-log`

Optional detailed audit log for every awarded reward.

```yaml
settings:
  reward-log:
    enabled: true
    file: rewards.csv
```

When enabled, MobGift appends CSV rows to `plugins/MobGift/rewards.csv`. Each row includes timestamp, player UUID/name, drop ID, mob/source, world, block coordinates, material, item amount, XP amount, command count, and whether the reward was forced by `/mobgift give`.

`mobs`

The list of mobs where the drop is active:

```yaml
mobs:
  - ZOMBIE
  - HUSK
```

Use `ALL` for every mob:

```yaml
mobs:
  - ALL
```

A single-line format is also accepted:

```yaml
mobs: ZOMBIE,HUSK,DROWNED
```

`worlds`

Optional world filter. If missing, the drop works in all worlds.

```yaml
worlds:
  - world
  - world_nether
```

Use `ALL` for every world:

```yaml
worlds:
  - ALL
```

`biomes`

Optional biome filter. If missing, the drop works in all biomes.

```yaml
biomes:
  - DESERT
  - BADLANDS
```

Use `ALL` for every biome:

```yaml
biomes:
  - ALL
```

`permission`

Optional permission required from the killer.

```yaml
permission: mobgift.vip
```

`required-tools`

Optional list of tools or weapons the killer must hold.

```yaml
required-tools:
  - IRON_SWORD
  - DIAMOND_SWORD
  - NETHERITE_SWORD
```

`allow-spawner-mobs`

Optional per-drop override for mobs spawned from spawners.

```yaml
allow-spawner-mobs: false
```

The global default is:

```yaml
settings:
  allow-spawner-mobs: true
```

`looting-bonus`

Optional bonus from the killer's Looting enchantment.

```yaml
looting-bonus:
  chance-per-level: 0.02
  amount-per-level: 1
```

With Looting III, this example adds `0.06` chance and `3` extra items.

`xp`

Optional extra experience reward.

```yaml
xp: 3
```

Random range:

```yaml
xp:
  min: 1
  max: 5
```

`commands`

Optional console commands run when the drop is awarded.

```yaml
commands:
  - "say {player} found {amount}x {material} from {mob}"
```

Commands run from the console and should not start with `/`.

`broadcast`

Optional server-wide message when the drop is awarded.

```yaml
broadcast: "&6{player} found a rare drop from {mob}!"
```

`sound`

Optional sound played to the killer.

```yaml
sound:
  name: ENTITY_PLAYER_LEVELUP
  volume: 1.0
  pitch: 1.2
```

`particle`

Optional particle effect at the killed mob location. Use simple particles that do not require extra data.

```yaml
particle:
  name: PORTAL
  count: 18
```

`message`

Optional message sent to the player when the drop is awarded.

```yaml
message: "&7You found &f{amount}x {material}&7."
```

Supported placeholders:

- `{player}`
- `{mob}`
- `{drop}`
- `{material}`
- `{amount}`
- `{xp}`

`display-name`, `lore`, and `custom-model-data`

Optional custom item metadata.

```yaml
display-name: "&aMob Token"
lore:
  - "&7Dropped from {mob}"
  - "&7Drop ID: {drop}"
custom-model-data: 1001
```

Display name and lore support the same placeholders as `message`.

`enchantments`, `item-flags`, and `unbreakable`

Optional item metadata for custom reward items.

```yaml
enchantments:
  SHARPNESS: 2
  UNBREAKING: 1
item-flags:
  - HIDE_ENCHANTS
  - HIDE_ATTRIBUTES
unbreakable: true
```

Enchantments also accept chat/legacy list input such as `SHARPNESS:2;UNBREAKING:1`.

## Testing A Drop

Use:

```text
/mobgift test <dropId> [mob] [world] [biome]
```

Examples:

```text
/mobgift test creeper_gunpowder CREEPER
/mobgift test desert_husk_sand HUSK world DESERT
```

When used by a player, the test also checks permission filters and the item held in the player's main hand.

## Previewing Drops

Use:

```text
/mobgift preview <mob> [world] [biome]
```

Examples:

```text
/mobgift preview CREEPER
/mobgift preview HUSK world DESERT
```

When used by a player, preview checks player permission and required-tool filters.

## Drop Info

Use:

```text
/mobgift info <dropId>
```

This prints the loaded drop state, rewards, filters, cooldown, item metadata, and configured effects.

## Giving A Drop

Use:

```text
/mobgift give <player> <dropId> [amount]
```

This forces the configured reward for an online player. It ignores chance, filters, enabled state, and cooldowns. If the drop has commands, messages, broadcasts, sounds, or particles, those effects are also applied. The optional `amount` overrides the item amount only.

## Resetting Cooldowns

Use:

```text
/mobgift cooldown reset <player> [dropId|all]
```

Examples:

```text
/mobgift cooldown reset Steve
/mobgift cooldown reset Steve enderman_pearl
```

Cooldown reset currently works for online players.

## Reward Statistics

Use:

```text
/mobgift stats [top|drop <dropId>|player <player>]
```

Examples:

```text
/mobgift stats
/mobgift stats top 10
/mobgift stats drop enderman_pearl
/mobgift stats player Steve
/mobgift stats export
/mobgift stats reset all
/mobgift stats reset drop enderman_pearl
/mobgift stats reset player Steve
```

MobGift records awarded drops in memory and saves them to `plugins/MobGift/stats.yml` at most once per minute and on clean plugin shutdown. Stats track total awards, forced awards from `/mobgift give`, item amounts, XP amounts, command runs, last player, and last mob/source.

`/mobgift stats export` writes a CSV snapshot such as `plugins/MobGift/stats-export-1712345678901.csv`. `/mobgift stats reset all` clears both drop and player aggregate stats. Resetting only a drop or only a player clears that aggregate view only; the other aggregate view is not recalculated.

## Validating Config

Use:

```text
/mobgift validate
```

This reloads the config and prints the first warnings directly in chat. Full warning details are still logged to the console.

Validation reports common config mistakes such as invalid materials/mobs/biomes/worlds, bad amount or XP ranges, unknown enchantments or item flags, permission nodes containing spaces, cooldowns longer than 24 hours, and unknown placeholders in messages, broadcasts, commands, display names, or lore.

## Admin GUI

Use:

```text
/mobgift gui
```

The GUI includes:

- main menu with drops, validate, reload, and close buttons;
- paginated loaded drop list;
- drop detail view for rewards, filters, messages, commands, and effects;
- quick edit buttons for `enabled`, `chance`, item `amount`, `xp`, and `allow-spawner-mobs`.
- chat input editing for `chance`, item `amount`, `xp`, `material`, `permission`, `mobs`, `worlds`, `biomes`, `message`, `broadcast`, `commands`, `required-tools`, `display-name`, `lore`, and `custom-model-data`.
- chat input editing for `cooldown-seconds`, `sound`, `sound.volume`, `sound.pitch`, `particle`, `particle.count`, `looting-bonus`, `enchantments`, and `item-flags`.
- item metadata toggle for `unbreakable`.
- create-drop wizard for basic item drops.
- duplicate-drop flow for copying an existing `drops.items` entry to a new ID.
- delete-drop confirmation menu for drops under `drops.items`.
- settings menu for `drops.replace-default-drops`, `settings.debug`, `settings.allow-spawner-mobs`, `settings.persist-cooldowns`, and `settings.reward-log.enabled`.
- stats menu showing top recorded drops, with CSV export.
- validation warnings menu after `/mobgift gui` -> Validate Config.

When the GUI asks for chat input, type the new value in chat or type `cancel`.

Input formats:

- `mobs`, `worlds`, `biomes`, `required-tools`: comma-separated values.
- `commands`: semicolon-separated console commands.
- `lore`: semicolon-separated lore lines.
- `chance`: chance value such as `0.25`, `25%`, or `25`.
- `amount`: fixed amount or range such as `1`, `1-3`, or `1..3`.
- `xp`: fixed XP or range such as `0`, `3`, `1-5`, or `none`.
- `cooldown-seconds`: seconds or duration values like `30`, `30s`, `5m`, `1h`, or `none`.
- `material`: one Bukkit/Paper material name.
- `permission`: one permission node without spaces, or `none`.
- `custom-model-data`: one positive whole number.
- `sound`: Bukkit sound constant or Minecraft sound key.
- `sound.volume`: number `0.0` or higher.
- `sound.pitch`: number from `0.5` to `2.0`.
- `particle`: simple Bukkit particle name; particles requiring extra data are rejected.
- `particle.count`: positive whole number.
- `looting-bonus.chance-per-level`: chance value such as `0.02`, `2%`, or `2`.
- `looting-bonus.amount-per-level`: whole number `0` or higher.
- `enchantments`: semicolon-separated values like `SHARPNESS:2;UNBREAKING:1`.
- `item-flags`: comma-separated Bukkit item flags like `HIDE_ENCHANTS,HIDE_ATTRIBUTES`.
- `message` and `broadcast`: raw message text with optional `&` color codes.
- `none`, `clear`, or `remove`: clears optional fields where that is valid.

The GUI saves to `config.yml`, reloads MobGift, and opens the drop page again after each edit.

Create wizard steps:

1. Drop ID
2. Material
3. Chance
4. Mobs

The wizard creates a basic drop with `amount: 1`. Use the edit buttons after creation for more details.

Duplicate Drop copies all config fields from the selected drop into a new `drops.items.<new-id>` section. The GUI asks for the new ID in chat and rejects IDs that already exist.

## Debug Mode

```yaml
settings:
  debug: true
```

Debug mode writes detailed drop checks to the console. Keep it disabled on production servers unless you are troubleshooting.

## Applying Config Changes

After editing the config, run:

```text
/mobgift reload
```

If the config has invalid materials, mob names, worlds, chances, or amount ranges, MobGift prints warnings in the console.

## Mob Names

Use Bukkit/Paper `EntityType` names:

```yaml
ZOMBIE
HUSK
DROWNED
SKELETON
STRAY
WITHER_SKELETON
SPIDER
CAVE_SPIDER
CREEPER
SLIME
MAGMA_CUBE
ENDERMAN
BLAZE
WITCH
GUARDIAN
ELDER_GUARDIAN
PHANTOM
COW
MOOSHROOM
CHICKEN
```

The plugin normalizes mob names:

- lowercase and uppercase names are accepted;
- spaces become `_`;
- `-` becomes `_`.

Equivalent examples:

```yaml
- wither skeleton
- wither-skeleton
- WITHER_SKELETON
```

## Behavior Rules

- Drops only apply when the killer is a player.
- Players are ignored.
- Drops can be temporarily disabled with `enabled: false`.
- Drops can enforce per-player cooldowns with `cooldown-seconds`; these reset on restart unless `settings.persist-cooldowns` is enabled.
- Awarded drops are counted in `stats.yml` for `/mobgift stats` and the GUI stats page.
- Detailed per-reward CSV audit rows can be enabled with `settings.reward-log.enabled`.
- Other plugins can inspect loaded drops/stats through `MobGiftApi` and can cancel or adjust awards through `MobGiftDropAwardEvent`.
- Drops can block mobs spawned from spawners through `allow-spawner-mobs: false`.
- A configured drop must have at least one real reward: valid `material`, `xp`, or `commands`.
- If `drops.items` exists and contains drops, the plugin uses the dynamic list.
- If `drops.items` is missing, the plugin uses the legacy format: `drops.guaranteed`, `drops.bonus.gold`, and `drops.bonus.iron`.
- Invalid materials are ignored when there is no fallback in code.
- Invalid mob names in `mobs` are ignored and reported during reload.
- Unknown placeholders are reported during reload. Supported placeholders are `{player}`, `{mob}`, `{drop}`, `{material}`, `{amount}`, and `{xp}`.

## Updating The Config

Bukkit/Paper does not automatically overwrite `plugins/MobGift/config.yml`.

If you want the new default config:

1. Stop the server.
2. Rename the old config, for example to `config.yml.old`.
3. Start the server so a new config is generated.
4. Manually copy any old settings you still need.

If you are upgrading from an older build that used `plugins/mobgift/config.yml`, move that file to `plugins/MobGift/config.yml` before starting the server.
