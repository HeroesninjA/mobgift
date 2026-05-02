# MobGift - Documentatie de utilizare

MobGift este un plugin Paper/Spigot care adauga drop-uri custom cand un player omoara un mob.

Pluginul ignora kill-urile fara player killer si ignora playerii. Pentru mobii omorati de player, poate pastra drop-urile vanilla sau le poate inlocui complet cu drop-urile configurate.

## Instalare

1. Compileaza pluginul:

```bash
mvn package
```

2. Copiaza JAR-ul din `target/` in folderul `plugins/` al serverului.
3. Porneste sau reporneste serverul.
4. Configul va fi generat in `plugins/mobgift/config.yml`.

## Config default

Default-ul este gandit sa fie realist si sa nu ofere iteme exagerat de valoroase. Pluginul pastreaza drop-urile vanilla si adauga bonusuri mici.

```yaml
drops:
  replace-default-drops: false
  items:
    zombie_rotten_flesh:
      material: ROTTEN_FLESH
      amount: 1
      chance: 0.35
      mobs:
        - ZOMBIE
        - HUSK
        - DROWNED

    creeper_gunpowder:
      material: GUNPOWDER
      amount: 1
      chance: 0.25
      mobs:
        - CREEPER
```

Configul complet din plugin include mai multe exemple pentru zombie, skeleton, spider, creeper, slime, witch, guardian, phantom si mobi pasivi.

## `replace-default-drops`

```yaml
replace-default-drops: false
```

Valori:

- `false`: pastreaza drop-urile vanilla si adauga drop-urile pluginului ca bonus.
- `true`: sterge drop-urile vanilla si lasa doar drop-urile din `drops.items`.

Recomandat pentru survival normal:

```yaml
replace-default-drops: false
```

## Adaugarea unui drop

Toate drop-urile se pun sub:

```yaml
drops:
  items:
```

Exemplu:

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

`zombie_extra_flesh` este doar un ID intern. Poti alege orice nume, dar trebuie sa fie unic.

## Campuri drop

`material`

Itemul care pica. Foloseste nume Bukkit/Paper:

```yaml
material: BONE
```

Exemple:

- `ROTTEN_FLESH`
- `BONE`
- `ARROW`
- `STRING`
- `GUNPOWDER`
- `SLIME_BALL`
- `REDSTONE`
- `LEATHER`

`amount`

Cate iteme se adauga cand drop-ul reuseste:

```yaml
amount: 1
```

Daca valoarea este sub `1`, pluginul foloseste `1`.

`chance`

Sansa ca drop-ul sa pice:

```yaml
chance: 0.25
```

Exemple:

- `0.05` = 5%
- `0.10` = 10%
- `0.25` = 25%
- `0.50` = 50%
- `1.00` = 100%

`mobs`

Lista mob-urilor pentru care drop-ul este activ:

```yaml
mobs:
  - ZOMBIE
  - HUSK
```

Pentru orice mob:

```yaml
mobs:
  - ALL
```

Accepta si format pe o singura linie:

```yaml
mobs: ZOMBIE,HUSK,DROWNED
```

## Exemple

Drop mic pentru zombie:

```yaml
zombie_rotten_flesh:
  material: ROTTEN_FLESH
  amount: 1
  chance: 0.35
  mobs:
    - ZOMBIE
    - HUSK
    - DROWNED
```

Drop pentru skeleton si stray:

```yaml
skeleton_bone:
  material: BONE
  amount: 1
  chance: 0.30
  mobs:
    - SKELETON
    - STRAY
```

Drop rar pentru enderman:

```yaml
enderman_pearl:
  material: ENDER_PEARL
  amount: 1
  chance: 0.06
  mobs:
    - ENDERMAN
```

Drop pentru orice mob:

```yaml
common_bonus:
  material: STICK
  amount: 1
  chance: 0.05
  mobs:
    - ALL
```

## Nume de mobi

Foloseste numele `EntityType` din Bukkit/Paper:

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

Pluginul normalizeaza numele:

- litere mici sau mari sunt acceptate;
- spatiile devin `_`;
- `-` devine `_`.

Exemple echivalente:

```yaml
- wither skeleton
- wither-skeleton
- WITHER_SKELETON
```

## Reguli de functionare

- Drop-urile se aplica doar cand killer-ul este un player.
- Playerii sunt ignorati.
- Daca `drops.items` exista si are drop-uri, pluginul foloseste lista dinamica.
- Daca `drops.items` lipseste, pluginul foloseste formatul vechi `drops.guaranteed`, `drops.bonus.gold` si `drops.bonus.iron`.
- Materialele invalide sunt ignorate daca nu exista fallback in cod.
- Mobii invalizi din `mobs` sunt ignorati.

## Update config dupa schimbari

Bukkit/Paper nu suprascrie automat `plugins/mobgift/config.yml`.

Daca vrei noul config default:

1. Opreste serverul.
2. Redenumeste configul vechi, de exemplu `config.yml.old`.
3. Porneste serverul ca sa fie generat un config nou.
4. Copiaza manual setarile vechi de care mai ai nevoie.
