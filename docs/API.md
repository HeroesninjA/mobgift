# MobGift - Documentatie API

Aceasta documentatie descrie contractul tehnic al pluginului MobGift pentru dezvoltatori si integratori.

In versiunea curenta, MobGift nu expune un serviciu public prin `ServicesManager` si nu are o clasa API separata stabila. Contractul disponibil este format din:

- schema `config.yml`;
- comportamentul listenerului `MobKillListener`;
- compatibilitatea cu vechiul format de config.

## Date plugin

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

Clasa principala:

```kotlin
class Mobgift : JavaPlugin()
```

La `onEnable()` pluginul:

1. ruleaza `saveDefaultConfig()`;
2. inregistreaza `MobKillListener`.

Nu exista comenzi, permisiuni sau task-uri programate in versiunea curenta.

## Listener

Clasa:

```kotlin
com.me.mobgift.MobKillListener
```

Event:

```kotlin
org.bukkit.event.entity.EntityDeathEvent
```

Flux:

1. Daca `event.entity.killer == null`, pluginul nu face nimic.
2. Daca `event.entityType == EntityType.PLAYER`, pluginul nu face nimic.
3. Daca `drops.replace-default-drops` este `true`, apeleaza `event.drops.clear()`.
4. Daca exista `drops.items` si are cel putin o cheie, citeste toate drop-urile din acea sectiune.
5. Daca `drops.items` lipseste sau este gol, foloseste fallback-ul legacy.

## Schema noua

Schema recomandata:

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

Exemplu:

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

`<drop-id>` este o cheie interna din YAML. Nu este folosita in logica, in afara de construirea path-ului configului.

## Campuri

### `drops.replace-default-drops`

Tip: `boolean`

Default in cod: `true`

Default in configul actual: `false`

Efect:

- `true`: curata drop-urile existente in `EntityDeathEvent`.
- `false`: lasa drop-urile existente si adauga drop-uri custom.

### `<drop>.material`

Tip: `string`

Rezolvare:

```kotlin
Material.matchMaterial(configuredMaterial)
```

Pentru drop-urile din `drops.items`, daca materialul lipseste sau este invalid, drop-ul este ignorat.

### `<drop>.amount`

Tip: `integer`

Default in cod: `1`

Normalizare:

```kotlin
coerceAtLeast(1)
```

### `<drop>.chance`

Tip: `double`

Default in cod pentru `drops.items`: `0.0`

Normalizare:

```kotlin
coerceIn(0.0, 1.0)
```

Drop-ul este adaugat doar daca:

```kotlin
Math.random() <= chance
```

### `<drop>.mobs`

Tip: `string | list<string>`

Pluginul cauta intai:

```text
<drop-path>.mobs
```

Daca nu exista, cauta forma singulara:

```text
<drop-path>.mob
```

Daca lipsesc ambele, fallback-ul este:

```yaml
mobs:
  - ALL
```

Formate acceptate:

```yaml
mobs:
  - ZOMBIE
  - SKELETON
```

```yaml
mobs: ZOMBIE,SKELETON,CREEPER
```

Normalizare:

- `trim()`
- `-` devine `_`
- spatiu devine `_`
- uppercase cu `Locale.ROOT`

Un drop este permis daca lista contine `ALL` sau daca un nume normalizat este egal cu `event.entityType`.

## Compatibilitate legacy

Daca `drops.items` lipseste sau nu contine chei, pluginul foloseste formatul vechi:

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

Path-uri legacy:

```text
drops.guaranteed
drops.bonus.gold
drops.bonus.iron
```

Fallback-uri legacy:

- `drops.guaranteed.material`: `DIAMOND`
- `drops.guaranteed.amount`: `5`
- `drops.guaranteed.chance`: `1.0`
- `drops.bonus.gold.material`: `GOLD_INGOT`
- `drops.bonus.iron.material`: `IRON_INGOT`
- bonus `chance`: `0.0`

## Integrare din alt plugin

MobGift nu expune inca un serviciu public. Alt plugin poate verifica doar daca este activ:

```kotlin
val mobGift = server.pluginManager.getPlugin("mobgift")
if (mobGift != null && mobGift.isEnabled) {
    // MobGift este activ.
}
```

Citirea configului direct este posibila, dar nu este recomandata ca API stabil:

```kotlin
val mobGift = server.pluginManager.getPlugin("mobgift") as? JavaPlugin
val customDrops = mobGift?.config?.getConfigurationSection("drops.items")
```

## Extindere recomandata

Pentru un API public stabil, separa logica in servicii:

- `DropDefinition`: model pentru material, amount, chance si mobs.
- `DropConfigLoader`: incarca `drops.items`.
- `MobGiftApi`: interfata publica pentru alte pluginuri.
- `MobGiftService`: implementare interna.

Exemplu de interfata:

```kotlin
interface MobGiftApi {
    fun reloadDrops()
    fun getDropIds(): Set<String>
    fun isDropAllowed(dropId: String, entityType: EntityType): Boolean
}
```

Inregistrare posibila:

```kotlin
server.servicesManager.register(
    MobGiftApi::class.java,
    implementation,
    this,
    ServicePriority.Normal
)
```
