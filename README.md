# MobGift

MobGift is a Paper/Spigot plugin that adds custom drops when a player kills a mob.

The default configuration is designed for normal survival servers: it keeps vanilla drops and adds small, realistic bonuses without overly valuable items.

## Requirements

- Java 21
- Maven
- Paper/Spigot compatible with API `1.21`

## Build

Build the plugin with:

```bash
mvn package
```

The final JAR is generated in:

```text
target/
```

## Installation

1. Copy the JAR from `target/` into the server's `plugins/` folder.
2. Start or restart the server.
3. The config will be generated at `plugins/mobgift/config.yml`.

## Quick Config

Example custom drop:

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

Fields:

- `replace-default-drops`: `false` keeps vanilla drops, `true` removes them.
- `material`: the item to drop.
- `amount`: the item amount.
- `chance`: drop chance between `0.0` and `1.0`.
- `mobs`: the mobs this drop applies to, or `ALL` for every mob.

## `chance` Examples

- `0.05` = 5%
- `0.10` = 10%
- `0.25` = 25%
- `0.50` = 50%
- `1.00` = 100%

## Documentation

- [Usage Documentation](docs/USAGE.md)
- [API Documentation](docs/API.md)

## Notes

Bukkit/Paper does not automatically overwrite an existing config. If you install a new version and want the updated default config, rename or delete `plugins/mobgift/config.yml`, then restart the server.
