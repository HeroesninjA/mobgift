MobGift
MobGift is a Paper/Spigot plugin that adds custom drops when a player kills a mob.
The default setup is designed for normal survival gameplay: it keeps vanilla drops and adds small, realistic bonuses without overly valuable items.
Requirements


Java 21


Maven


Paper/Spigot compatible with API 1.21


Build
Compile the plugin with:
mvn package
The final JAR is generated in:
target/
Installation


Copy the JAR from target/ into the server’s plugins/ folder.


Start or restart the server.


The config will be generated in plugins/MobGift/config.yml.


Quick config
Example custom drop:
drops:  replace-default-drops: false  items:    creeper_gunpowder:      material: GUNPOWDER      amount: 1      chance: 0.25      mobs:        - CREEPER
Fields:


replace-default-drops: false keeps vanilla drops, true removes them.


enabled: false temporarily disables a drop without deleting it.


material: the item that drops.


amount: the amount.


chance: the chance between 0.0 and 1.0.


cooldown-seconds: cooldown per player after the drop is awarded.


settings.persist-cooldowns: saves active cooldowns in cooldowns.yml, so they remain after clean restarts.


settings.reward-log.enabled: writes a detailed CSV audit log to rewards.csv for every awarded reward.


mobs: the mobs the drop applies to, or ALL for all mobs.


xp: optional extra experience.


commands: optional console commands when the drop is won.


allow-spawner-mobs: allows or blocks the drop for mobs from spawners.


display-name, lore, custom-model-data, enchantments, item-flags, unbreakable: optional metadata for custom items.


Useful commands


/mobgift preview <mob> [world] [biome]: shows the matching drops.


/mobgift info <dropId>: shows details about a loaded drop.


/mobgift give <player> <dropId> [amount]: forcibly gives the reward to an online player.


/mobgift cooldown reset <player> [dropId|all]: resets cooldowns for an online player.


/mobgift stats [top|drop <dropId>|player <player>|export|reset]: shows, exports, or resets persistent statistics from stats.yml.


/mobgift validate: reloads and displays config warnings in chat.


/mobgift gui: opens the admin GUI for drops, stats, settings, creation, duplication, deletion, warnings, enabled toggles, cooldown persistence, reward log, quick edits, and chat input for chance, amount, XP, cooldown, filters, permissions, and metadata.


/mobgift validate also checks for unknown placeholders, permissions with spaces, and very large cooldowns.
For integrations, the plugin exposes MobGiftApi through Bukkit ServicesManager and the cancellable MobGiftDropAwardEvent.
chance examples


0.05 = 5%


0.10 = 10%


0.25 = 25%


0.50 = 50%


1.00 = 100%


Documentation


Usage documentation


API documentation


Changelog


Release checklist


English README


Notes
Bukkit/Paper does not automatically overwrite the existing config. When installing a new version, if you want the updated default config, rename or delete plugins/MobGift/config.yml, then restart the server.
