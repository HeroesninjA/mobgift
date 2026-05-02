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
/mobgift test <dropId> [mob] [world] [biome]
```

Aliases:

```text
/mgift
```

Permissions:

- `mobgift.reload`: allows `/mobgift reload`.
- `mobgift.list`: allows `/mobgift list`.
- `mobgift.test`: allows `/mobgift test`.
- `mobgift.admin`: grants all MobGift admin permissions.

## Default Config

The default config is designed to be realistic and avoid overly valuable items. The plugin keeps vanilla drops and adds small bonuses.

```yaml
config-version: 2

settings:
  debug: false

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

The item to drop. Use Bukkit/Paper material names:

```yaml
material: BONE
```

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

`looting-bonus`

Optional bonus from the killer's Looting enchantment.

```yaml
looting-bonus:
  chance-per-level: 0.02
  amount-per-level: 1
```

With Looting III, this example adds `0.06` chance and `3` extra items.

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
- If `drops.items` exists and contains drops, the plugin uses the dynamic list.
- If `drops.items` is missing, the plugin uses the legacy format: `drops.guaranteed`, `drops.bonus.gold`, and `drops.bonus.iron`.
- Invalid materials are ignored when there is no fallback in code.
- Invalid mob names in `mobs` are ignored and reported during reload.

## Updating The Config

Bukkit/Paper does not automatically overwrite `plugins/MobGift/config.yml`.

If you want the new default config:

1. Stop the server.
2. Rename the old config, for example to `config.yml.old`.
3. Start the server so a new config is generated.
4. Manually copy any old settings you still need.

If you are upgrading from an older build that used `plugins/mobgift/config.yml`, move that file to `plugins/MobGift/config.yml` before starting the server.
