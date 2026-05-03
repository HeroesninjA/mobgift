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

Set `enabled: false` on any drop to disable it temporarily without deleting its config.

Set `cooldown-seconds` to prevent the same player from receiving the same drop repeatedly:

```yaml
cooldown-seconds: 300
```

By default, cooldowns reset on restart. To keep active cooldowns across clean restarts, enable:

```yaml
settings:
  persist-cooldowns: true
```

To keep a detailed CSV audit row for every awarded reward, enable:

```yaml
settings:
  reward-log:
    enabled: true
    file: rewards.csv
```

The file is written to `plugins/MobGift/rewards.csv`.

Optional rewards can also add XP, run console commands, send messages, play sounds, or spawn particles:

```yaml
xp:
  min: 1
  max: 3
commands:
  - "say {player} found {amount}x {material}"
message: "&7You found &f{amount}x {material}&7."
```

Custom item metadata is supported too:

```yaml
display-name: "&aMob Token"
enchantments:
  SHARPNESS: 2
item-flags:
  - HIDE_ENCHANTS
unbreakable: true
```

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

Validation also catches unknown placeholders, permissions with spaces, and unusually long cooldowns.

Reward statistics are available with:

```text
/mobgift stats
/mobgift stats drop <dropId>
/mobgift stats player <player>
/mobgift stats export
/mobgift stats reset all
```

MobGift saves aggregate reward stats to `plugins/MobGift/stats.yml`. Stats exports are written as `plugins/MobGift/stats-export-<timestamp>.csv`.

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

Alias:

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

The GUI supports creating basic drops, duplicating existing drops, deleting drops with confirmation, top reward stats with CSV export, global settings toggles including cooldown persistence and reward logging, validation warnings, quick numeric/toggle edits, enabled toggles, and chat input for chance, amount, XP, cooldown, material, permission, mob/world/biome lists, messages, broadcasts, commands, required tools, item metadata, enchantments, item flags, Looting bonuses, sounds, and particles.

Admin testing commands also include detailed drop info, forced reward give, and cooldown reset for online players.

Developer integrations can use the public `MobGiftApi` service and the cancellable `MobGiftDropAwardEvent`.

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
