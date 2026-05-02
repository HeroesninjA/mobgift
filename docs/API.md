# MobGift - API Documentation

This document describes the technical contract of the MobGift plugin for developers and integrators.

In the current version, MobGift does not expose a public service through `ServicesManager` and does not have a separate stable API class. The available contract is made of:

- the `config.yml` schema;
- the behavior of `MobKillListener`;
- compatibility with the legacy config format.

## Plugin Data

`plugin.yml`:

```yaml
name: mobgift
main: com.me.mobgift.Mobgift
api-version: '1.21'
load: POSTWORLD
```

Maven:

```xml
<groupId>com.me</groupId>
<artifactId>mobgift</artifactId>
<version>1.0-SNAPSHOT</version>
```

Runtime:

- Paper API `1.21.11-R0.1-SNAPSHOT`
- Java `21`
- Kotlin `2.4.0-Beta2`

## Lifecycle

Main class:

```kotlin
class Mobgift : JavaPlugin()
```

During `onEnable()`, the plugin:

1. runs `saveDefaultConfig()`;
2. registers `MobKillListener`.

There are no commands, permissions, or scheduled tasks in the current version.

## Listener

Class:

```kotlin
com.me.mobgift.MobKillListener
```

Event:

```kotlin
org.bukkit.event.entity.EntityDeathEvent
```

Flow:

1. If `event.entity.killer == null`, the plugin does nothing.
2. If `event.entityType == EntityType.PLAYER`, the plugin does nothing.
3. If `drops.replace-default-drops` is `true`, it calls `event.drops.clear()`.
4. If `drops.items` exists and has at least one key, it reads all drops from that section.
5. If `drops.items` is missing or empty, it uses the legacy fallback.

## New Schema

Recommended schema:

```yaml
drops:
  replace-default-drops: boolean
  items:
    <drop-id>:
      material: string
      amount: integer
      chance: double
      mobs: string | list<string>
```

Example:

```yaml
drops:
  replace-default-drops: false
  items:
    creeper_gunpowder:
      material: GUNPOWDER
      amount: 1
      chance: 0.25
      mobs:
        - CREEPER
```

`<drop-id>` is an internal YAML key. It is not used by the logic except when building the config path.

## Fields

### `drops.replace-default-drops`

Type: `boolean`

Code default: `true`

Current config default: `false`

Effect:

- `true`: clears the existing drops in `EntityDeathEvent`.
- `false`: keeps existing drops and adds custom drops.

### `<drop>.material`

Type: `string`

Resolution:

```kotlin
Material.matchMaterial(configuredMaterial)
```

For drops under `drops.items`, if the material is missing or invalid, the drop is ignored.

### `<drop>.amount`

Type: `integer`

Code default: `1`

Normalization:

```kotlin
coerceAtLeast(1)
```

### `<drop>.chance`

Type: `double`

Code default for `drops.items`: `0.0`

Normalization:

```kotlin
coerceIn(0.0, 1.0)
```

The drop is added only if:

```kotlin
Math.random() <= chance
```

### `<drop>.mobs`

Type: `string | list<string>`

The plugin first looks for:

```text
<drop-path>.mobs
```

If it does not exist, it also checks the singular form:

```text
<drop-path>.mob
```

If both are missing, the fallback is:

```yaml
mobs:
  - ALL
```

Accepted formats:

```yaml
mobs:
  - ZOMBIE
  - SKELETON
```

```yaml
mobs: ZOMBIE,SKELETON,CREEPER
```

Normalization:

- `trim()`
- `-` becomes `_`
- space becomes `_`
- uppercase with `Locale.ROOT`

A drop is allowed if the list contains `ALL` or if a normalized name equals `event.entityType`.

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

Legacy fallbacks:

- `drops.guaranteed.material`: `DIAMOND`
- `drops.guaranteed.amount`: `5`
- `drops.guaranteed.chance`: `1.0`
- `drops.bonus.gold.material`: `GOLD_INGOT`
- `drops.bonus.iron.material`: `IRON_INGOT`
- bonus `chance`: `0.0`

## Integration From Another Plugin

MobGift does not expose a public service yet. Another plugin can only check whether it is enabled:

```kotlin
val mobGift = server.pluginManager.getPlugin("mobgift")
if (mobGift != null && mobGift.isEnabled) {
    // MobGift is active.
}
```

Reading the config directly is possible, but it is not recommended as a stable API:

```kotlin
val mobGift = server.pluginManager.getPlugin("mobgift") as? JavaPlugin
val customDrops = mobGift?.config?.getConfigurationSection("drops.items")
```

## Recommended Extension

For a stable public API, split the logic into services:

- `DropDefinition`: model for material, amount, chance, and mobs.
- `DropConfigLoader`: loads `drops.items`.
- `MobGiftApi`: public interface for other plugins.
- `MobGiftService`: internal implementation.

Example interface:

```kotlin
interface MobGiftApi {
    fun reloadDrops()
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
