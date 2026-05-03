# MobGift

**MobGift** is a lightweight Minecraft plugin that adds configurable bonus drops to mobs.  
It is designed for Paper and Spigot servers and gives server owners an easy way to create custom loot rewards without modifying the source code.

MobGift keeps the gameplay simple, configurable, and server-friendly.

---

## What Is MobGift?

MobGift allows mobs to drop extra items based on a simple configuration file.

You can use it to add:

- bonus mob drops;
- rare reward items;
- custom loot chances;
- different drops for different mobs;
- server-specific reward systems.

By default, MobGift keeps normal Minecraft drops and only adds small bonus rewards.

---

## Main Features

- Easy direct `.jar` installation
- No Maven, Kotlin, or source code required
- Supports Paper and Spigot servers
- Compatible with Minecraft `1.21`
- Requires Java `21`
- Configurable mob drops
- Adjustable drop chances
- Per-drop enable/disable toggle
- Per-player drop cooldowns
- Optional persistent cooldowns across clean restarts
- Persistent reward statistics in `stats.yml`
- Optional per-reward CSV audit log in `rewards.csv`
- Public `MobGiftApi` service and cancellable reward award event
- Option to keep or replace vanilla drops
- Supports specific mobs or all mobs
- Optional XP, command, message, sound, and particle rewards
- Optional custom item metadata, enchantments, item flags, and unbreakable rewards
- Preview and validation admin commands
- Drop info, forced reward give, and cooldown reset admin commands
- Inventory-based admin GUI with settings, warnings, create, duplicate, delete, quick edits, and chat input for rewards, filters, permissions, and metadata
- Optional spawner-mob filtering
- Kotlin runtime bundled inside the plugin JAR

---

## Simple Installation

To install MobGift, copy the plugin file into your server's `plugins/` folder.

Example file:

```text
MobGift-1.0-SNAPSHOT.jar
```

Then restart your Minecraft server.

On first launch, MobGift automatically creates its configuration file:

```text
plugins/MobGift/config.yml
```

---

## Default Behavior

The default configuration is safe for normal survival servers.

By default, MobGift:

- keeps vanilla mob drops;
- adds small bonus drops;
- avoids high-value rewards like diamonds;
- only applies bonus drops when a mob is killed by a player.

This makes the plugin useful without breaking server balance.

---

## Configuration Example

MobGift uses a simple YAML configuration system.

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
```

This means that Creepers have a 25% chance to drop 1 extra Gunpowder.

Custom item metadata can also be configured:

```yaml
display-name: "&aMob Token"
enchantments:
  SHARPNESS: 2
item-flags:
  - HIDE_ENCHANTS
unbreakable: true
```

---

## Keep Or Replace Vanilla Drops

Recommended setting:

```yaml
replace-default-drops: false
```

This keeps normal Minecraft drops and adds MobGift drops as bonuses.

Advanced setting:

```yaml
replace-default-drops: true
```

This removes vanilla drops and allows MobGift to fully control mob loot.

---

## Custom Drop Chances

Drop chances use values between `0.0` and `1.0`.

Examples:

```text
0.05 = 5%
0.10 = 10%
0.25 = 25%
0.50 = 50%
1.00 = 100%
```

This gives server owners full control over how common or rare each reward is.

---

## Mob Support

You can target specific mobs:

```yaml
mobs:
  - ZOMBIE
  - SKELETON
  - CREEPER
```

Or apply a drop to every mob:

```yaml
mobs:
  - ALL
```

Single-line mob lists are also supported:

```yaml
mobs: ZOMBIE,SKELETON,CREEPER
```

---

## Updating MobGift

To update the plugin:

1. Stop the server.
2. Back up your config file:

```text
plugins/MobGift/config.yml
```

3. Replace the old MobGift `.jar` with the new version.
4. Start the server.

Your existing configuration will not be overwritten automatically.

---

## Commands And Permissions

MobGift provides these admin commands:

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

---

## Troubleshooting

If the plugin does not load, check that:

- the `.jar` file is inside the `plugins/` folder;
- the server is running Java `21`;
- the server is compatible with Minecraft API `1.21`;
- the console does not show startup errors.

If drops do not appear, check that:

- the mob was killed by a player;
- the drop chance is not `0.0`;
- the mob name is valid;
- the material name is valid;
- the server was restarted after editing the config.

---

## Documentation

- [Usage documentation](docs/USAGE.md)
- [API documentation](docs/API.md)
- [Changelog](CHANGELOG.md)
- [Release checklist](docs/RELEASE_CHECKLIST.md)

---

## Perfect For

MobGift is useful for:

- survival servers;
- RPG servers;
- event servers;
- economy servers;
- custom reward systems;
- lightweight loot customization.

---

## Summary

MobGift is a simple and configurable mob drop plugin for Minecraft servers.

It gives server owners an easy way to add bonus loot, rare rewards, and custom mob drops while keeping the installation and configuration process beginner-friendly.
