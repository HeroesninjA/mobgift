# MobGift Release Checklist

Use this checklist before tagging a public release.

## Automated Checks

- Run `mvn -q clean package`.
- Confirm the GitHub Actions build passes.
- Confirm `dependency-reduced-pom.xml` is not regenerated.
- Confirm the final plugin JAR exists in `target/`.
- Start a clean Paper `1.21.x` server with the plugin installed.
- Confirm the server log contains:
  - `[MobGift] Enabling MobGift`
  - `[MobGift] Loaded ... custom drop(s).`
- Confirm there are no MobGift stack traces in `logs/latest.log`.

## Manual Server Checks

Run these commands as an operator player:

```text
/mobgift validate
/mobgift list
/mobgift preview ZOMBIE
/mobgift info zombie_rotten_flesh
/mobgift stats
/mobgift gui
```

Verify the GUI flows:

- open main menu;
- open drop list;
- open one drop detail page;
- edit chance;
- edit amount;
- edit XP;
- toggle enabled;
- duplicate a drop;
- delete the duplicate;
- validate config again.

Verify reward behavior:

- kill mobs for several configured drops;
- verify vanilla drops are preserved when `drops.replace-default-drops: false`;
- verify configured messages, sounds, particles, XP, and commands;
- verify spawner-mob filtering with `allow-spawner-mobs: false`;
- verify permission-gated drops with and without the permission.

Verify persistence:

- enable `settings.persist-cooldowns`;
- trigger a drop with `cooldown-seconds`;
- restart the server cleanly;
- confirm active cooldowns survive in `plugins/MobGift/cooldowns.yml`;
- trigger rewards and confirm `plugins/MobGift/stats.yml` updates;
- enable `settings.reward-log.enabled` and confirm `plugins/MobGift/rewards.csv` receives rows.

## Release Steps

- Set `pom.xml` version from `1.0-SNAPSHOT` to the release version.
- Rebuild with `mvn -q clean package`.
- Copy the final shaded JAR from `target/`.
- Update release notes with user-facing changes and config migration notes.
- Tag the release in Git.
