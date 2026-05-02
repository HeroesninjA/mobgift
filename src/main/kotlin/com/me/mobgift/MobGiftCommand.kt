package com.me.mobgift

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import java.util.Locale

class MobGiftCommand(
    private val plugin: MobGift,
    private val dropManager: DropManager
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when (args.firstOrNull()?.lowercase()) {
            null, "help" -> sendHelp(sender, label)
            "reload" -> reload(sender)
            "list" -> listDrops(sender)
            "test" -> testDrop(sender, label, args)
            else -> sender.sendMessage("Usage: /$label <help|reload|list|test>")
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
            1 -> listOf("help", "reload", "list", "test")
                .filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> if (args[0].equals("test", ignoreCase = true)) {
                dropManager.dropIds().filter { it.startsWith(args[1], ignoreCase = true) }
            } else {
                emptyList()
            }
            3 -> if (args[0].equals("test", ignoreCase = true)) {
                EntityType.entries
                    .map { it.name }
                    .filter { it.startsWith(args[2], ignoreCase = true) }
            } else {
                emptyList()
            }
            4 -> if (args[0].equals("test", ignoreCase = true)) {
                Bukkit.getWorlds()
                    .map { it.name }
                    .filter { it.startsWith(args[3], ignoreCase = true) }
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
        sender.sendMessage("/$label test <dropId> [mob] [world] [biome] - Tests a drop.")
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

        val dropIds = dropManager.dropIds()
        if (dropIds.isEmpty()) {
            sender.sendMessage("No custom drops are loaded.")
            return
        }

        sender.sendMessage("Loaded drops (${dropIds.size}): ${dropIds.joinToString(", ")}")
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

    private fun parseEntityType(value: String): EntityType? {
        val normalized = value.trim()
            .replace("-", "_")
            .replace(" ", "_")
            .uppercase(Locale.ROOT)

        return runCatching { EntityType.valueOf(normalized) }.getOrNull()
    }
}
