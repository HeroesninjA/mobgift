package com.me.mobgift

import org.bukkit.Bukkit
import org.bukkit.block.Biome
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import java.util.Locale

class MobGiftCommand(
    private val plugin: MobGift,
    private val dropManager: DropManager,
    private val mobGiftGui: MobGiftGui
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when (args.firstOrNull()?.lowercase()) {
            null, "help" -> sendHelp(sender, label)
            "reload" -> reload(sender)
            "list" -> listDrops(sender)
            "info" -> infoDrop(sender, label, args)
            "give" -> giveDrop(sender, label, args)
            "cooldown" -> cooldown(sender, label, args)
            "stats" -> stats(sender, label, args)
            "test" -> testDrop(sender, label, args)
            "preview" -> previewDrops(sender, label, args)
            "validate" -> validateConfig(sender)
            "gui" -> openGui(sender)
            else -> sender.sendMessage("Usage: /$label <help|reload|list|info|give|cooldown|stats|test|preview|validate|gui>")
        }

        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        return when (args.size) {
            1 -> listOf("help", "reload", "list", "info", "give", "cooldown", "stats", "test", "preview", "validate", "gui")
                .filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> when {
                args[0].equals("info", ignoreCase = true) || args[0].equals("test", ignoreCase = true) ->
                    dropManager.dropIds().filter { it.startsWith(args[1], ignoreCase = true) }
                args[0].equals("stats", ignoreCase = true) ->
                    listOf("top", "drop", "player", "export", "reset").filter { it.startsWith(args[1], ignoreCase = true) }
                args[0].equals("give", ignoreCase = true) ->
                    onlinePlayerNames().filter { it.startsWith(args[1], ignoreCase = true) }
                args[0].equals("cooldown", ignoreCase = true) ->
                    listOf("reset").filter { it.startsWith(args[1], ignoreCase = true) }
                args[0].equals("preview", ignoreCase = true) ->
                    EntityType.entries
                        .map { it.name }
                        .filter { it.startsWith(args[1], ignoreCase = true) }
                else -> emptyList()
            }
            3 -> when {
                args[0].equals("give", ignoreCase = true) ->
                    dropManager.dropIds().filter { it.startsWith(args[2], ignoreCase = true) }
                args[0].equals("stats", ignoreCase = true) && args[1].equals("drop", ignoreCase = true) ->
                    dropManager.dropIds().filter { it.startsWith(args[2], ignoreCase = true) }
                args[0].equals("stats", ignoreCase = true) && args[1].equals("player", ignoreCase = true) ->
                    (onlinePlayerNames() + dropManager.statsPlayerNames())
                        .distinct()
                        .filter { it.startsWith(args[2], ignoreCase = true) }
                args[0].equals("stats", ignoreCase = true) && args[1].equals("top", ignoreCase = true) ->
                    listOf("5", "10", "20").filter { it.startsWith(args[2], ignoreCase = true) }
                args[0].equals("stats", ignoreCase = true) && args[1].equals("reset", ignoreCase = true) ->
                    listOf("all", "drop", "player").filter { it.startsWith(args[2], ignoreCase = true) }
                args[0].equals("cooldown", ignoreCase = true) && args[1].equals("reset", ignoreCase = true) ->
                    onlinePlayerNames().filter { it.startsWith(args[2], ignoreCase = true) }
                args[0].equals("test", ignoreCase = true) ->
                    EntityType.entries
                        .map { it.name }
                        .filter { it.startsWith(args[2], ignoreCase = true) }
                args[0].equals("preview", ignoreCase = true) ->
                    Bukkit.getWorlds()
                        .map { it.name }
                        .filter { it.startsWith(args[2], ignoreCase = true) }
                else -> emptyList()
            }
            4 -> when {
                args[0].equals("cooldown", ignoreCase = true) && args[1].equals("reset", ignoreCase = true) ->
                    (listOf("all") + dropManager.dropIds()).filter { it.startsWith(args[3], ignoreCase = true) }
                args[0].equals("stats", ignoreCase = true) &&
                    args[1].equals("reset", ignoreCase = true) &&
                    args[2].equals("drop", ignoreCase = true) ->
                    dropManager.dropIds().filter { it.startsWith(args[3], ignoreCase = true) }
                args[0].equals("stats", ignoreCase = true) &&
                    args[1].equals("reset", ignoreCase = true) &&
                    args[2].equals("player", ignoreCase = true) ->
                    (onlinePlayerNames() + dropManager.statsPlayerNames())
                        .distinct()
                        .filter { it.startsWith(args[3], ignoreCase = true) }
                args[0].equals("test", ignoreCase = true) ->
                    Bukkit.getWorlds()
                        .map { it.name }
                        .filter { it.startsWith(args[3], ignoreCase = true) }
                args[0].equals("preview", ignoreCase = true) ->
                    Biome.values()
                        .map { it.name() }
                        .filter { it.startsWith(args[3], ignoreCase = true) }
                else -> emptyList()
            }
            5 -> if (args[0].equals("test", ignoreCase = true)) {
                Biome.values()
                    .map { it.name() }
                    .filter { it.startsWith(args[4], ignoreCase = true) }
            } else {
                emptyList()
            }
            else -> emptyList()
        }
    }

    private fun sendHelp(sender: CommandSender, label: String) {
        sender.sendMessage("MobGift commands:")
        sender.sendMessage("/$label help - Shows this help message.")
        sender.sendMessage("/$label reload - Reloads the config.")
        sender.sendMessage("/$label list - Lists loaded drop IDs.")
        sender.sendMessage("/$label info <dropId> - Shows detailed drop config.")
        sender.sendMessage("/$label give <player> <dropId> [amount] - Forces a drop reward for a player.")
        sender.sendMessage("/$label cooldown reset <player> [dropId|all] - Clears active cooldowns.")
        sender.sendMessage("/$label stats [top|drop <dropId>|player <player>|export|reset <all|drop|player>] - Manages reward statistics.")
        sender.sendMessage("/$label test <dropId> [mob] [world] [biome] - Tests a drop.")
        sender.sendMessage("/$label preview <mob> [world] [biome] - Shows matching drops.")
        sender.sendMessage("/$label validate - Reloads and reports config warnings.")
        sender.sendMessage("/$label gui - Opens the MobGift admin GUI.")
    }

    private fun reload(sender: CommandSender) {
        if (!sender.hasPermission("mobgift.reload")) {
            sender.sendMessage("You do not have permission to use this command.")
            return
        }

        plugin.reloadConfig()
        val result = dropManager.reload()
        sender.sendMessage("MobGift reloaded. Loaded ${result.loadedDrops} drop(s).")

        if (result.warnings.isNotEmpty()) {
            sender.sendMessage("Found ${result.warnings.size} config warning(s). Check the console.")
        }
    }

    private fun listDrops(sender: CommandSender) {
        if (!sender.hasPermission("mobgift.list")) {
            sender.sendMessage("You do not have permission to use this command.")
            return
        }

        val drops = dropManager.drops.sortedBy { it.id.lowercase() }
        if (drops.isEmpty()) {
            sender.sendMessage("No custom drops are loaded.")
            return
        }

        val dropSummaries = drops.joinToString(", ") { drop ->
            if (drop.enabled) drop.id else "${drop.id} (disabled)"
        }
        sender.sendMessage("Loaded drops (${drops.size}): $dropSummaries")
    }

    private fun infoDrop(sender: CommandSender, label: String, args: Array<out String>) {
        if (!sender.hasPermission("mobgift.info")) {
            sender.sendMessage("You do not have permission to use this command.")
            return
        }

        if (args.size < 2) {
            sender.sendMessage("Usage: /$label info <dropId>")
            return
        }

        val lines = dropManager.dropInfo(args[1])
        if (lines == null) {
            sender.sendMessage("Drop '${args[1]}' is not loaded.")
            return
        }

        lines.forEach(sender::sendMessage)
    }

    private fun giveDrop(sender: CommandSender, label: String, args: Array<out String>) {
        if (!sender.hasPermission("mobgift.give")) {
            sender.sendMessage("You do not have permission to use this command.")
            return
        }

        if (args.size < 3) {
            sender.sendMessage("Usage: /$label give <player> <dropId> [amount]")
            return
        }

        val target = Bukkit.getPlayerExact(args[1])
        if (target == null) {
            sender.sendMessage("Player '${args[1]}' is not online.")
            return
        }

        val amountOverride = args.getOrNull(3)?.toIntOrNull()
        if (args.size >= 4 && (amountOverride == null || amountOverride <= 0)) {
            sender.sendMessage("Amount must be a positive whole number.")
            return
        }

        val lines = dropManager.giveDrop(args[2], target, amountOverride)
        if (lines == null) {
            sender.sendMessage("Drop '${args[2]}' is not loaded.")
            return
        }

        lines.forEach(sender::sendMessage)
    }

    private fun cooldown(sender: CommandSender, label: String, args: Array<out String>) {
        if (!sender.hasPermission("mobgift.cooldown")) {
            sender.sendMessage("You do not have permission to use this command.")
            return
        }

        if (args.size < 3 || !args[1].equals("reset", ignoreCase = true)) {
            sender.sendMessage("Usage: /$label cooldown reset <player> [dropId|all]")
            return
        }

        val target = Bukkit.getPlayerExact(args[2])
        if (target == null) {
            sender.sendMessage("Player '${args[2]}' is not online.")
            return
        }

        val dropId = args.getOrNull(3)?.takeUnless { it.equals("all", ignoreCase = true) }
        if (dropId != null && dropManager.dropIds().none { it.equals(dropId, ignoreCase = true) }) {
            sender.sendMessage("Drop '$dropId' is not loaded.")
            return
        }

        val removed = dropManager.resetCooldown(target, dropId)
        val scope = dropId ?: "all drops"
        sender.sendMessage("Reset $removed cooldown(s) for ${target.name} on $scope.")
    }

    private fun stats(sender: CommandSender, label: String, args: Array<out String>) {
        if (!sender.hasPermission("mobgift.stats")) {
            sender.sendMessage("You do not have permission to use this command.")
            return
        }

        when (args.getOrNull(1)?.lowercase(Locale.ROOT)) {
            null, "top" -> {
                val limit = args.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 20) ?: 8
                dropManager.statsSummary(limit).forEach(sender::sendMessage)
            }
            "export" -> {
                if (!sender.hasPermission("mobgift.stats.export")) {
                    sender.sendMessage("You do not have permission to export MobGift stats.")
                    return
                }

                runCatching { dropManager.exportStats() }
                    .onSuccess { file ->
                        sender.sendMessage("Exported MobGift stats to ${file.name}.")
                    }
                    .onFailure {
                        sender.sendMessage("Could not export MobGift stats: ${it.message}")
                    }
            }
            "reset" -> resetStats(sender, label, args)
            "drop" -> {
                val dropId = args.getOrNull(2)
                if (dropId == null) {
                    sender.sendMessage("Usage: /$label stats drop <dropId>")
                    return
                }

                val lines = dropManager.dropStatsInfo(dropId)
                if (lines == null) {
                    sender.sendMessage("No stats recorded for drop '$dropId'.")
                    return
                }
                lines.forEach(sender::sendMessage)
            }
            "player" -> {
                val playerName = args.getOrNull(2)
                if (playerName == null) {
                    sender.sendMessage("Usage: /$label stats player <player>")
                    return
                }

                val lines = dropManager.playerStatsInfo(playerName)
                if (lines == null) {
                    sender.sendMessage("No MobGift stats recorded for player '$playerName'.")
                    return
                }
                lines.forEach(sender::sendMessage)
            }
            else -> sender.sendMessage("Usage: /$label stats [top [limit]|drop <dropId>|player <player>|export|reset <all|drop <dropId>|player <player>>]")
        }
    }

    private fun resetStats(sender: CommandSender, label: String, args: Array<out String>) {
        if (!sender.hasPermission("mobgift.stats.reset")) {
            sender.sendMessage("You do not have permission to reset MobGift stats.")
            return
        }

        when (args.getOrNull(2)?.lowercase(Locale.ROOT)) {
            "all" -> {
                val removed = dropManager.resetAllStats()
                sender.sendMessage("Reset MobGift stats: ${removed.first} drop record(s), ${removed.second} player record(s).")
            }
            "drop" -> {
                val dropId = args.getOrNull(3)
                if (dropId == null) {
                    sender.sendMessage("Usage: /$label stats reset drop <dropId>")
                    return
                }

                if (dropManager.resetDropStats(dropId)) {
                    sender.sendMessage("Reset aggregate stats for drop '$dropId'. Player aggregate stats were not changed.")
                } else {
                    sender.sendMessage("No aggregate stats recorded for drop '$dropId'.")
                }
            }
            "player" -> {
                val playerName = args.getOrNull(3)
                if (playerName == null) {
                    sender.sendMessage("Usage: /$label stats reset player <player>")
                    return
                }

                val removedPlayer = dropManager.resetPlayerStats(playerName)
                if (removedPlayer == null) {
                    sender.sendMessage("No MobGift stats recorded for player '$playerName'.")
                } else {
                    sender.sendMessage("Reset aggregate MobGift stats for player '$removedPlayer'. Drop aggregate stats were not changed.")
                }
            }
            else -> sender.sendMessage("Usage: /$label stats reset <all|drop <dropId>|player <player>>")
        }
    }

    private fun testDrop(sender: CommandSender, label: String, args: Array<out String>) {
        if (!sender.hasPermission("mobgift.test")) {
            sender.sendMessage("You do not have permission to use this command.")
            return
        }

        if (args.size < 2) {
            sender.sendMessage("Usage: /$label test <dropId> [mob] [world] [biome]")
            return
        }

        val player = sender as? Player
        val entityType = args.getOrNull(2)?.let { parseEntityType(it) }
        if (args.size >= 3 && entityType == null) {
            sender.sendMessage("Unknown mob type: ${args[2]}")
            return
        }

        val worldName = args.getOrNull(3) ?: player?.world?.name
        val biomeName = args.getOrNull(4) ?: player?.location?.block?.biome?.name()
        val result = dropManager.testDrop(args[1], player, entityType, worldName, biomeName)

        result.lines.forEach(sender::sendMessage)
        if (result.found) {
            sender.sendMessage("Result: ${if (result.passed) "passed" else "failed"}")
        }
    }

    private fun previewDrops(sender: CommandSender, label: String, args: Array<out String>) {
        if (!sender.hasPermission("mobgift.preview")) {
            sender.sendMessage("You do not have permission to use this command.")
            return
        }

        if (args.size < 2) {
            sender.sendMessage("Usage: /$label preview <mob> [world] [biome]")
            return
        }

        val entityType = parseEntityType(args[1])
        if (entityType == null) {
            sender.sendMessage("Unknown mob type: ${args[1]}")
            return
        }

        val player = sender as? Player
        val worldName = args.getOrNull(2) ?: player?.world?.name
        val biomeName = args.getOrNull(3) ?: player?.location?.block?.biome?.name()

        dropManager.previewDrops(entityType, player, worldName, biomeName).forEach(sender::sendMessage)
    }

    private fun validateConfig(sender: CommandSender) {
        if (!sender.hasPermission("mobgift.validate")) {
            sender.sendMessage("You do not have permission to use this command.")
            return
        }

        plugin.reloadConfig()
        val result = dropManager.reload()
        if (result.warnings.isEmpty()) {
            sender.sendMessage("MobGift config is valid. Loaded ${result.loadedDrops} drop(s).")
            return
        }

        sender.sendMessage("MobGift config loaded ${result.loadedDrops} drop(s) with ${result.warnings.size} warning(s):")
        result.warnings.take(8).forEach { sender.sendMessage("- $it") }
        if (result.warnings.size > 8) {
            sender.sendMessage("...and ${result.warnings.size - 8} more warning(s). Check the console.")
        }
    }

    private fun openGui(sender: CommandSender) {
        if (!sender.hasPermission("mobgift.gui")) {
            sender.sendMessage("You do not have permission to use this command.")
            return
        }

        val player = sender as? Player
        if (player == null) {
            sender.sendMessage("Only players can open the MobGift GUI.")
            return
        }

        mobGiftGui.openMainMenu(player)
    }

    private fun parseEntityType(value: String): EntityType? {
        val normalized = value.trim()
            .replace("-", "_")
            .replace(" ", "_")
            .uppercase(Locale.ROOT)

        return runCatching { EntityType.valueOf(normalized) }.getOrNull()
    }

    private fun onlinePlayerNames(): List<String> {
        return Bukkit.getOnlinePlayers().map { it.name }
    }
}
