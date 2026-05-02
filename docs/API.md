# MobGift - API Documentation

This document describes the technical contract of the MobGift plugin for developers and integrators.

In the current version, MobGift does not expose a public service through `ServicesManager` and does not have a separate stable API class. The available contract is made of:

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
5. registers the `/mobgift` command executor and tab completer.

There are no scheduled tasks in the current version.

## Commands

Command:

```text
/mobgift <help|reload|list|test>
```

Alias:

```text
/mgift
```

Permissions:

- `mobgift.reload`
- `mobgift.list`
- `mobgift.test`
- `mobgift.admin`

## Internal Classes

- `MobGift`: plugin entrypoint.
- `MobKillListener`: handles `EntityDeathEvent`.
- `DropManager`: loads, validates, and applies drops.
- `DropDefinition`: immutable loaded drop model.
- `DropLoadResult`: reload result with loaded count and warnings.
- `DropTestResult`: command test result model.
- `MobGiftCommand`: command executor and tab completer.

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
5. Each matching drop checks mob filter, world filter, Looting bonus, and chance roll.
6. Successful drops are added to `event.drops`.

## Recommended Schema

```yaml
config-version: integer

settings:
  debug: boolean

drops:
  replace-default-drops: boolean
  items:
    <drop-id>:
      material: string
      amount: integer | object
      chance: double
      mobs: string | list<string>
      worlds: string | list<string>
      biomes: string | list<string>
      permission: string
      required-tools: string | list<string>
      looting-bonus:
        chance-per-level: double
        amount-per-level: integer
      message: string
      display-name: string
      lore: list<string>
      custom-model-data: integer
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

Current value: `2`

If the value is lower than the current version, MobGift logs a warning during reload.

### `settings.debug`

Type: `boolean`

Default: `false`

When enabled, `DropManager` logs detailed drop checks to the console.

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

For drops under `drops.items`, if the material is missing or invalid, the drop is skipped and a warning is logged.

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

### `<drop>.looting-bonus`

Type: `object`

Defaults:

- `chance-per-level`: `0.0`
- `amount-per-level`: `0`

Both values are clamped to non-negative values.

### `<drop>.message`

Type: `string`

Optional. Supports legacy `&` color codes and these placeholders:

- `{player}`
- `{mob}`
- `{drop}`
- `{material}`
- `{amount}`

### Item Metadata

Optional fields:

- `display-name`
- `lore`
- `custom-model-data`

`display-name` and `lore` support legacy `&` color codes and the same placeholders as `message`.

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

MobGift does not expose a public service yet. Another plugin can check whether it is enabled:

```kotlin
val mobGift = server.pluginManager.getPlugin("MobGift")
if (mobGift != null && mobGift.isEnabled) {
    // MobGift is active.
}
```

Reading the config directly is possible, but it is not recommended as a stable API:

```kotlin
val mobGift = server.pluginManager.getPlugin("MobGift") as? JavaPlugin
val customDrops = mobGift?.config?.getConfigurationSection("drops.items")
```

## Recommended Extension

For a stable public API, register a service through Bukkit `ServicesManager`.

Example interface:

```kotlin
interface MobGiftApi {
    fun reloadDrops(): DropLoadResult
    fun getDropIds(): Set<String>
    fun isDropAllowed(dropId: String, entityType: EntityType): Boolean
}
```

Possible registration:

```kotlin
server.servicesManager.register(
    MobGiftApi::class.java,
    implementation,
    this,
    ServicePriority.Normal
)
```
