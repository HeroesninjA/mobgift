# MobGift - Direct JAR Installation Guide

This guide is for server owners who receive the MobGift plugin as a ready-to-use `.jar` file.

You do not need Maven, Kotlin, or the source code to install the plugin on your server.

## Requirements

- A Paper or Spigot server compatible with Minecraft `1.21`
- Java `21`
- Access to the server's `plugins/` folder

MobGift is built with Kotlin, but the required Kotlin runtime is bundled inside the plugin JAR.

## Files You Receive

You should receive a file similar to:

```text
MobGift-1.0-SNAPSHOT.jar
```

The exact filename may be different, but it must end in `.jar`.

## Installation

1. Stop the Minecraft server.
2. Copy the MobGift `.jar` file into the server's `plugins/` folder.
3. Start the server.
4. Check the console for plugin loading messages.
5. A config file will be generated at:

```text
plugins/MobGift/config.yml
```

## First Run

On the first server start, MobGift creates its default config automatically.

The default setup:

- keeps vanilla mob drops;
- adds small bonus drops;
- avoids high-value items like diamonds by default;
- only applies drops when a mob is killed by a player.

## Basic Configuration

Open:

```text
plugins/MobGift/config.yml
```

Example drop:

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
      looting-bonus:
        chance-per-level: 0.03
        amount-per-level: 0
```

This means:

- Creepers can drop `GUNPOWDER`.
- The amount is `1`.
- The drop chance is `0.25`, which means 25%.
- Vanilla drops are kept because `replace-default-drops` is `false`.

## Replacing Or Keeping Vanilla Drops

Recommended:

```yaml
replace-default-drops: false
```

This keeps normal Minecraft drops and adds MobGift drops as bonuses.

Use this only if you want MobGift to fully control mob drops:

```yaml
replace-default-drops: true
```

This removes vanilla drops and only keeps the configured MobGift drops.

## Adding A New Drop

Add a new entry under:

```yaml
drops:
  items:
```

Example:

```yaml
drops:
  replace-default-drops: false
  items:
    zombie_extra_flesh:
      material: ROTTEN_FLESH
      amount: 1
      chance: 0.30
      mobs:
        - ZOMBIE
        - HUSK
```

The name `zombie_extra_flesh` is only an internal ID. You can choose a different name, but every drop ID must be unique.

## Chance Values

`chance` must be between `0.0` and `1.0`.

Examples:

- `0.05` = 5%
- `0.10` = 10%
- `0.25` = 25%
- `0.50` = 50%
- `1.00` = 100%

## Mob Names

Use Bukkit/Paper mob names:

```yaml
mobs:
  - ZOMBIE
  - SKELETON
  - CREEPER
```

Use `ALL` for every mob:

```yaml
mobs:
  - ALL
```

Single-line format is also supported:

```yaml
mobs: ZOMBIE,SKELETON,CREEPER
```

## Applying Config Changes

After editing `config.yml`, run:

```text
/mobgift reload
```

If the plugin reports config warnings, check the server console for details.

## Updating The Plugin

1. Stop the server.
2. Back up:

```text
plugins/MobGift/config.yml
```

3. Replace the old MobGift `.jar` with the new one.
4. Start the server.

If the new version includes an updated default config, Bukkit/Paper will not overwrite your existing config automatically.

To regenerate the default config:

1. Stop the server.
2. Rename the old config to:

```text
config.yml.old
```

3. Start the server.
4. Copy any settings you still need from the old config.

## Updating From Older Lowercase Builds

Older builds used this config path:

```text
plugins/mobgift/config.yml
```

Current builds use:

```text
plugins/MobGift/config.yml
```

On Linux servers these are different folders. If you already have an old config, move it to the new folder before starting the server.

## Commands And Permissions

MobGift provides these commands:

```text
/mobgift help
/mobgift reload
/mobgift list
/mobgift test <dropId> [mob] [world] [biome]
```

Alias:

```text
/mgift
```

Permissions:

- `mobgift.reload`: allows `/mobgift reload`.
- `mobgift.list`: allows `/mobgift list`.
- `mobgift.test`: allows `/mobgift test`.
- `mobgift.admin`: grants all MobGift admin permissions.

## Troubleshooting

Plugin does not load:

- Make sure the JAR is inside the `plugins/` folder.
- Make sure the server is running Java `21`.
- Make sure the server version is compatible with API `1.21`.
- Check the console for errors during startup.

Drops do not appear:

- Make sure the mob was killed by a player.
- Check that `chance` is not `0.0`.
- Check that the mob name under `mobs` is valid.
- Check that the `material` name is valid.
- Run `/mobgift reload` after editing the config.

Vanilla drops disappeared:

- Set this back to `false`:

```yaml
replace-default-drops: false
```

Config did not update after replacing the JAR:

- Bukkit/Paper keeps the existing config.
- Rename or delete `plugins/MobGift/config.yml`, then restart the server to generate a fresh default config.
