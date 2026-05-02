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
3. Configul va fi generat in `plugins/mobgift/config.yml`.

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
- `material`: itemul care pica.
- `amount`: cantitatea.
- `chance`: sansa intre `0.0` si `1.0`.
- `mobs`: mobii la care se aplica drop-ul sau `ALL` pentru toti.

## Exemple `chance`

- `0.05` = 5%
- `0.10` = 10%
- `0.25` = 25%
- `0.50` = 50%
- `1.00` = 100%

## Documentatie

- [Documentatie de utilizare](docs/UTILIZARE.md)
- [Documentatie API](docs/API.md)

## Note

Bukkit/Paper nu suprascrie automat configul existent. Daca instalezi o versiune noua si vrei configul default actualizat, redenumeste sau sterge `plugins/mobgift/config.yml`, apoi reporneste serverul.
