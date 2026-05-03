# Changelog

## 1.0.0-rc.1 - 2026-05-03

### Added

- Configurable mob bonus drops through `drops.items`.
- Optional preservation or replacement of vanilla mob drops.
- Per-drop enable/disable toggle.
- Fixed and ranged item amounts.
- Drop chance configuration with Looting bonuses.
- Mob, world, biome, permission, required-tool, and spawner-mob filters.
- Optional XP, console command, player message, broadcast, sound, and particle rewards.
- Custom item display name, lore, custom model data, enchantments, item flags, and unbreakable metadata.
- Per-player cooldowns with optional persistence in `cooldowns.yml`.
- Persistent aggregate reward statistics in `stats.yml`.
- Optional per-reward CSV audit log in `rewards.csv`.
- Admin commands for reload, list, info, give, cooldown reset, stats, test, preview, validate, and GUI.
- Inventory admin GUI for viewing, editing, creating, duplicating, and deleting drops.
- Public `MobGiftApi` service and cancellable `MobGiftDropAwardEvent`.
- Usage, API, installation, and release checklist documentation.
- GitHub Actions build workflow for Java 21 and Maven.

### Changed

- Default behavior keeps vanilla drops unless `drops.replace-default-drops: true` is configured.
- The shaded JAR no longer generates `dependency-reduced-pom.xml` in the project root.
- English README moved from `REDME.md` to `README_EN.md`.
- Plugin entrypoint source file is named `MobGift.kt` to match the `MobGift` class.

### Fixed

- Persistent cooldown storage now uses encoded YAML-safe keys, so drop IDs containing dots do not become nested YAML paths.
- Config validation warns about unknown placeholders, invalid permission nodes, and very long cooldowns.

### Verification

- `mvn -q clean package` passes.
- The plugin loads on Paper `1.21.11` and reports 18 configured default drops.

### Before Final 1.0.0

- Complete the manual in-game checks from `docs/RELEASE_CHECKLIST.md`.
- If no release blockers are found, set the Maven version to `1.0.0`, rebuild, and tag the release.
