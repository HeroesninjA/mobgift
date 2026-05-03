# MobGift - API Documentation

This document describes the technical contract of the MobGift plugin for developers and integrators.

MobGift exposes a read-only public service through Bukkit `ServicesManager` and a cancellable award event for integrations. The available contract is made of:

- `MobGiftApi`;
- `MobGiftDropAwardEvent`;
- the `config.yml` schema;
- the behavior of `MobKillListener`;
- the command surface declared in `plugin.yml`;
- compatibility with the legacy config format.

## Plugin Data

`plugin.yml`:

```yaml
name: MobGift
main: com.me.mobgift.MobGift
api-version: '1.21'
load: POSTWORLD
```

Maven:

```xml
<groupId>com.me</groupId>
<artifactId>MobGift</artifactId>
<version>1.0-SNAPSHOT</version>
```

Runtime:

- Paper API `1.21.11-R0.1-SNAPSHOT`
- Java `21`
- Kotlin `2.4.0-Beta2`

## Lifecycle

Main class:

```kotlin
class MobGift : JavaPlugin()
```

During `onEnable()`, the plugin:

1. runs `saveDefaultConfig()`;
2. creates `DropManager`;
3. loads and validates configured drops;
4. registers `MobKillListener`;
5. registers `MobGiftApi` through `ServicesManager`;
6. registers the admin GUI listener;
7. registers the `/mobgift` command executor and tab completer.

During `onDisable()`, MobGift saves active cooldowns when `settings.persist-cooldowns` is enabled.
It also flushes reward statistics to `plugins/MobGift/stats.yml`.

There are no scheduled tasks in the current version.

## Commands

Command:

```text
/mobgift <help|reload|list|info|give|cooldown|stats|test|preview|validate|gui>
```

Alias:

```text
/mgift
```

Permissions:

- `mobgift.reload`
- `mobgift.list`
- `mobgift.info`
- `mobgift.give`
- `mobgift.cooldown`
- `mobgift.stats`
- `mobgift.stats.export`
- `mobgift.stats.reset`
- `mobgift.test`
- `mobgift.preview`
- `mobgift.validate`
- `mobgift.gui`
- `mobgift.gui.edit`
- `mobgift.admin`

## Internal Classes

- `MobGift`: plugin entrypoint.
- `MobGiftApi`: public service interface for other plugins.
- `MobGiftApiService`: internal implementation registered through `ServicesManager`.
- `MobGiftDropAwardEvent`: cancellable Bukkit event fired before a MobGift reward is applied.
- `MobKillListener`: handles `EntityDeathEvent`.
- `DropManager`: loads, validates, and applies drops.
- `DropDefinition`: immutable loaded drop model.
- `DropLoadResult`: reload result with loaded count and warnings.
- `DropTestResult`: command test result model.
- `DropStatsSnapshot`: immutable drop statistics view.
- `PlayerStatsSnapshot`: immutable player statistics view.
- `MobGiftCommand`: command executor and tab completer.
- `MobGiftGui`: Bukkit inventory GUI listener for the admin menu, stats view, settings toggles, validation warning view, create/duplicate/delete flows, quick edit actions, and chat-based scalar/text/list input.

## Listener Flow

Event:

```kotlin
org.bukkit.event.entity.EntityDeathEvent
```

Flow:

1. If `event.entity.killer == null`, the plugin does nothing.
2. If `event.entityType == EntityType.PLAYER`, the plugin does nothing.
3. If `drops.replace-default-drops` is `true`, it calls `event.drops.clear()`.
4. `DropManager.applyDrops(event)` checks loaded drop definitions.
5. Each enabled matching drop checks mob filter, world filter, biome filter, spawner policy, player filters, cooldown, Looting bonus, and chance roll.
6. Before applying the reward, MobGift fires `MobGiftDropAwardEvent`.
7. If the event is cancelled, that configured drop is skipped and no cooldown/stat/log entry is written.
8. If not cancelled, successful drops can add items to `event.drops`, add dropped XP, run console commands, send messages, play configured effects, and update reward statistics.

## Public API

Service:

```kotlin
com.me.mobgift.api.MobGiftApi
```

Lookup example:

```kotlin
val registration = server.servicesManager.getRegistration(MobGiftApi::class.java)
val mobGiftApi = registration?.provider
```

Methods:

```kotlin
fun reloadDrops(): DropLoadResult
fun getDropIds(): Set<String>
fun getDrops(): List<DropDefinition>
fun getDrop(dropId: String): DropDefinition?
fun getDropStats(dropId: String): DropStatsSnapshot?
fun getPlayerStats(playerNameOrUuid: String): PlayerStatsSnapshot?
fun getTopDropStats(limit: Int = 10): List<DropStatsSnapshot>
fun getTopPlayerStats(limit: Int = 10): List<PlayerStatsSnapshot>
```

`reloadDrops()` reloads `config.yml` before loading drops, matching `/mobgift reload`.

## Award Event

Event:

```kotlin
com.me.mobgift.api.event.MobGiftDropAwardEvent
```

This event fires for normal mob-kill rewards and for forced rewards from `/mobgift give`.

Important fields:

- `player`: player receiving the reward.
- `drop`: immutable loaded `DropDefinition`.
- `source`: mob name such as `CREEPER`, or `ADMIN` for forced rewards.
- `entityType`: Bukkit `EntityType` for mob-kill rewards, otherwise `null`.
- `location`: reward source location.
- `forced`: `true` for `/mobgift give`.
- `itemAmount`: mutable non-negative item amount.
- `xpAmount`: mutable non-negative XP amount.

The event is cancellable. Cancelling it prevents the reward, messages, commands, effects, cooldown, stats, and reward log entry for that drop.

## Statistics Storage

Runtime file:

```text
plugins/MobGift/stats.yml
```

The file is created after MobGift records at least one reward. It stores aggregate counters by drop and by player:

- total awards;
- forced awards from `/mobgift give`;
- total item amount;
- total XP amount;
- total command runs;
- last awarded timestamp;
- last player/drop/mob source.

Stats are kept in memory, saved at most once per minute during gameplay, and flushed during clean plugin shutdown.

Stats command operations:

```text
/mobgift stats
/mobgift stats top [limit]
/mobgift stats drop <dropId>
/mobgift stats player <player>
/mobgift stats export
/mobgift stats reset <all|drop <dropId>|player <player>>
```

Exports are written as CSV snapshots named `stats-export-<epochMillis>.csv` in the plugin data folder. Resetting `all` clears both drop and player aggregates. Resetting a single drop or player clears that aggregate only; the opposite aggregate is not recalculated.

## Reward Audit Log

Runtime file when enabled:

```text
plugins/MobGift/rewards.csv
```

The file is controlled by `settings.reward-log`. Unlike `stats.yml`, this is append-only audit history: every awarded drop writes one CSV row with the player, drop ID, mob/source, world coordinates, reward amounts, command count, and whether the reward came from `/mobgift give`.

## Recommended Schema

```yaml
config-version: integer

settings:
  debug: boolean
  allow-spawner-mobs: boolean
  persist-cooldowns: boolean
  reward-log:
    enabled: boolean
    file: string

drops:
  replace-default-drops: boolean
  items:
    <drop-id>:
      enabled: boolean
      material: string
      amount: integer | object
      chance: double
      cooldown-seconds: integer
      mobs: string | list<string>
      worlds: string | list<string>
      biomes: string | list<string>
      permission: string
      required-tools: string | list<string>
      allow-spawner-mobs: boolean
      looting-bonus:
        chance-per-level: double
        amount-per-level: integer
      xp: integer | object
      commands: string | list<string>
      broadcast: string
      sound: string | object
      particle: string | object
      message: string
      display-name: string
      lore: list<string>
      custom-model-data: integer
      enchantments: object | string | list<string>
      item-flags: string | list<string>
      unbreakable: boolean
```

Example:

```yaml
drops:
  replace-default-drops: false
  items:
    creeper_gunpowder:
      material: GUNPOWDER
      amount:
        min: 1
        max: 2
      chance: 0.25
      mobs:
        - CREEPER
      worlds:
        - ALL
      biomes:
        - ALL
      looting-bonus:
        chance-per-level: 0.03
        amount-per-level: 0
      message: "&7You found &f{amount}x {material}&7."
```

`<drop-id>` is an internal YAML key. It is used as the loaded drop ID and in the `{drop}` message placeholder.

## Field Contract

### `config-version`

Type: `integer`

Current value: `8`

If the value is lower than the current version, MobGift logs a warning during reload.

Reload validation also reports invalid filters, invalid item/effect metadata, unknown placeholders in configured text/commands, permissions containing whitespace, and cooldowns longer than 24 hours.

### `settings.debug`

Type: `boolean`

Default: `false`

When enabled, `DropManager` logs detailed drop checks to the console.

### `settings.allow-spawner-mobs`

Type: `boolean`

Default: `true`

Global default for entities where `Entity#fromMobSpawner()` is true. Individual drops can override this with `<drop>.allow-spawner-mobs`.

### `settings.persist-cooldowns`

Type: `boolean`

Default: `false`

When enabled, active per-player drop cooldowns are loaded from and saved to `plugins/MobGift/cooldowns.yml`. Expired entries are pruned when the file is loaded or saved.

### `settings.reward-log`

Type: `object`

Defaults:

- `enabled`: `false`
- `file`: `rewards.csv`

When enabled, MobGift appends one CSV row per awarded reward to `plugins/MobGift/<file>`. The configured file name must be a simple file name containing only letters, numbers, `.`, `_`, or `-`, and cannot contain `..`; invalid names fall back to `rewards.csv`.

CSV columns:

```text
timestamp,player_uuid,player_name,drop_id,source,world,x,y,z,material,item_amount,xp_amount,commands,forced
```

### `drops.replace-default-drops`

Type: `boolean`

Code default: `true`

Current config default: `false`

Effect:

- `true`: clears existing drops in `EntityDeathEvent`.
- `false`: keeps existing drops and adds custom drops.

### `<drop>.material`

Type: `string`

Resolution:

```kotlin
Material.matchMaterial(configuredMaterial)
```

For drops under `drops.items`, invalid materials disable the item reward and log a warning. A drop without a valid `material` can still load if it has `xp` or `commands`.

### `<drop>.enabled`

Type: `boolean`

Default: `true`

When `false`, the drop remains loaded for admin commands and GUI editing but is skipped during reward application and preview matching.

### `<drop>.amount`

Type: `integer | object`

Fixed amount:

```yaml
amount: 1
```

Range:

```yaml
amount:
  min: 1
  max: 3
```

Values below `1` are clamped to `1`. If `max` is lower than `min`, the loader uses `min` for both values and logs a warning.

### `<drop>.chance`

Type: `double`

Code default for `drops.items`: `0.0`

Normalization:

```kotlin
coerceIn(0.0, 1.0)
```

The final chance is:

```text
drop chance + (Looting level * chance-per-level)
```

The final value is clamped to `0.0..1.0`.

### `<drop>.cooldown-seconds`

Type: `integer`

Default: `0`

Per-player cooldown after this drop is awarded. While active, the same player cannot receive the same drop again. The cooldown is stored in memory by default and resets on server restart unless `settings.persist-cooldowns` is enabled. Legacy `<drop>.cooldown` is also accepted as seconds.

### `<drop>.mobs`

Type: `string | list<string>`

The plugin first looks for:

```text
<drop-path>.mobs
```

If it does not exist, it also checks:

```text
<drop-path>.mob
```

If both are missing, the fallback is:

```yaml
mobs:
  - ALL
```

Mob names are normalized with trim, hyphen-to-underscore, space-to-underscore, and uppercase with `Locale.ROOT`.

### `<drop>.worlds`

Type: `string | list<string>`

The plugin first looks for:

```text
<drop-path>.worlds
```

If it does not exist, it also checks:

```text
<drop-path>.world
```

If both are missing, the fallback is:

```yaml
worlds:
  - ALL
```

Unknown world names are logged as warnings.

### `<drop>.biomes`

Type: `string | list<string>`

The plugin first looks for:

```text
<drop-path>.biomes
```

If it does not exist, it also checks:

```text
<drop-path>.biome
```

If both are missing, the fallback is:

```yaml
biomes:
  - ALL
```

Biome names are normalized like mob names. Invalid biomes are logged as warnings.

### `<drop>.permission`

Type: `string`

Optional. If present, the killer must have this permission for the drop to apply.

### `<drop>.required-tools`

Type: `string | list<string>`

The plugin first looks for:

```text
<drop-path>.required-tools
```

If it does not exist, it also checks:

```text
<drop-path>.required-tool
```

Each value is resolved with `Material.matchMaterial(...)`. Invalid tools are logged as warnings.

### `<drop>.allow-spawner-mobs`

Type: `boolean`

Default: `settings.allow-spawner-mobs`

When `false`, this drop is skipped for mobs spawned from spawners.

### `<drop>.looting-bonus`

Type: `object`

Defaults:

- `chance-per-level`: `0.0`
- `amount-per-level`: `0`

Both values are clamped to non-negative values.

### `<drop>.xp`

Type: `integer | object`

Adds extra dropped experience when the drop is awarded.

```yaml
xp:
  min: 1
  max: 5
```

Values below `0` are clamped to `0`. If `max` is lower than `min`, the loader uses `min` for both values and logs a warning.

### `<drop>.commands`

Type: `string | list<string>`

Console commands run when the drop is awarded. Placeholders are resolved before dispatch. A singular `<drop>.command` path is also accepted.

### `<drop>.broadcast`

Type: `string`

Optional server-wide message when the drop is awarded.

### `<drop>.sound`

Type: `string | object`

String form:

```yaml
sound: ENTITY_PLAYER_LEVELUP
```

Object form:

```yaml
sound:
  name: ENTITY_PLAYER_LEVELUP
  volume: 1.0
  pitch: 1.2
```

The loader accepts Bukkit sound constant names and Minecraft sound keys where available.

### `<drop>.particle`

Type: `string | object`

String form:

```yaml
particle: PORTAL
```

Object form:

```yaml
particle:
  name: PORTAL
  count: 18
  offset-x: 0.35
  offset-y: 0.35
  offset-z: 0.35
  extra: 0.0
```

Particles that require extra data are rejected during reload and reported as warnings.

### `<drop>.message`

Type: `string`

Optional. Supports legacy `&` color codes and these placeholders:

- `{player}`
- `{mob}`
- `{drop}`
- `{material}`
- `{amount}`
- `{xp}`

### Item Metadata

Optional fields:

- `display-name`
- `lore`
- `custom-model-data`
- `enchantments`
- `item-flags`
- `unbreakable`

`display-name` and `lore` support legacy `&` color codes and the same placeholders as `message`.

`enchantments` can be a map or a list/string using `NAME:level` entries:

```yaml
enchantments:
  SHARPNESS: 2
  UNBREAKING: 1
```

`item-flags` uses Bukkit `ItemFlag` names:

```yaml
item-flags:
  - HIDE_ENCHANTS
  - HIDE_ATTRIBUTES
unbreakable: true
```

Invalid enchantments and item flags are skipped and logged as reload warnings.

## Legacy Compatibility

If `drops.items` is missing or contains no keys, the plugin uses the old format:

```yaml
drops:
  guaranteed:
    material: DIAMOND
    amount: 5
    mobs:
      - ALL
  bonus:
    gold:
      material: GOLD_INGOT
      amount: 1
      chance: 0.20
      mobs:
        - ALL
    iron:
      material: IRON_INGOT
      amount: 1
      chance: 0.40
      mobs:
        - ALL
```

Legacy paths:

```text
drops.guaranteed
drops.bonus.gold
drops.bonus.iron
```

## Integration From Another Plugin

Another plugin can check whether MobGift is enabled:

```kotlin
val mobGift = server.pluginManager.getPlugin("MobGift")
if (mobGift != null && mobGift.isEnabled) {
    // MobGift is active.
}
```

Prefer `MobGiftApi` for loaded drop and stats data:

```kotlin
val mobGiftApi = server.servicesManager
    .getRegistration(MobGiftApi::class.java)
    ?.provider

val dropIds = mobGiftApi?.getDropIds().orEmpty()
```

Listen to reward awards:

```kotlin
@EventHandler(ignoreCancelled = true)
fun onMobGiftAward(event: MobGiftDropAwardEvent) {
    if (event.drop.id == "enderman_pearl" && event.player.world.name == "event_world") {
        event.itemAmount += 1
    }
}
```

Cancel an award:

```kotlin
@EventHandler
fun onMobGiftAward(event: MobGiftDropAwardEvent) {
    if (!event.player.hasPermission("myserver.loot.enabled")) {
        event.isCancelled = true
    }
}
```
