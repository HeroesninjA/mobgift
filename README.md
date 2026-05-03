# MobGift

MobGift este un plugin Paper/Spigot care adauga drop-uri custom cand un player omoara un mob.

Default-ul este gandit pentru survival normal: pastreaza drop-urile vanilla si adauga bonusuri mici, realiste, fara iteme exagerat de valoroase.

## Cerinte

- Java 21
- Maven
- Paper/Spigot compatibil cu API `1.21`

## Build

Compileaza pluginul cu:

```bash
mvn package
```

JAR-ul final se genereaza in:

```text
target/
```

## Instalare

1. Copiaza JAR-ul din `target/` in folderul `plugins/` al serverului.
2. Porneste sau reporneste serverul.
3. Configul va fi generat in `plugins/MobGift/config.yml`.

## Config rapid

Exemplu de drop custom:

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

Campuri:

- `replace-default-drops`: `false` pastreaza drop-urile vanilla, `true` le sterge.
- `enabled`: `false` dezactiveaza temporar un drop fara sa-l stergi.
- `material`: itemul care pica.
- `amount`: cantitatea.
- `chance`: sansa intre `0.0` si `1.0`.
- `cooldown-seconds`: cooldown per player dupa ce drop-ul este acordat.
- `settings.persist-cooldowns`: salveaza cooldown-urile active in `cooldowns.yml`, ca sa ramana dupa restarturi curate.
- `settings.reward-log.enabled`: scrie audit CSV detaliat in `rewards.csv` pentru fiecare reward acordat.
- `mobs`: mobii la care se aplica drop-ul sau `ALL` pentru toti.
- `xp`: experienta extra optionala.
- `commands`: comenzi console optionale cand drop-ul este castigat.
- `allow-spawner-mobs`: permite sau blocheaza drop-ul pentru mobi din spawner.
- `display-name`, `lore`, `custom-model-data`, `enchantments`, `item-flags`, `unbreakable`: metadata optionala pentru itemele custom.

## Comenzi utile

- `/mobgift preview <mob> [world] [biome]`: arata drop-urile care se potrivesc.
- `/mobgift info <dropId>`: arata detalii despre un drop incarcat.
- `/mobgift give <player> <dropId> [amount]`: acorda fortat reward-ul unui player online.
- `/mobgift cooldown reset <player> [dropId|all]`: reseteaza cooldown-uri pentru un player online.
- `/mobgift stats [top|drop <dropId>|player <player>|export|reset]`: arata, exporta sau reseteaza statistici persistente din `stats.yml`.
- `/mobgift validate`: reincarca si afiseaza warning-urile de config in chat.
- `/mobgift gui`: deschide GUI admin pentru drop-uri, stats, settings, creare, duplicare, stergere, warning-uri, toggle enabled, persistenta cooldown-uri, reward log, editari rapide si input prin chat pentru chance, amount, XP, cooldown, filtre, permisiuni si metadata.

`/mobgift validate` verifica inclusiv placeholder-e necunoscute, permisiuni cu spatii si cooldown-uri foarte mari.

Pentru integrari, pluginul expune `MobGiftApi` prin Bukkit `ServicesManager` si evenimentul cancellable `MobGiftDropAwardEvent`.

## Exemple `chance`

- `0.05` = 5%
- `0.10` = 10%
- `0.25` = 25%
- `0.50` = 50%
- `1.00` = 100%

## Documentatie

- [Documentatie de utilizare](docs/USAGE.md)
- [Documentatie API](docs/API.md)
- [Changelog](CHANGELOG.md)
- [Checklist release](docs/RELEASE_CHECKLIST.md)
- [English README](README_EN.md)

## Note

Bukkit/Paper nu suprascrie automat configul existent. Daca instalezi o versiune noua si vrei configul default actualizat, redenumeste sau sterge `plugins/MobGift/config.yml`, apoi reporneste serverul.
