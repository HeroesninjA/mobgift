package com.me.mobgift

import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.block.Biome
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.inventory.ItemStack
import java.util.Locale
import java.util.concurrent.ThreadLocalRandom

class DropManager(
    private val plugin: MobGift
) {
    companion object {
        const val CURRENT_CONFIG_VERSION = 2
    }

    var replaceDefaultDrops: Boolean = true
        private set

    var debug: Boolean = false
        private set

    var drops: List<DropDefinition> = emptyList()
        private set

    fun reload(): DropLoadResult {
        val warnings = mutableListOf<String>()
        val loadedDrops = mutableListOf<DropDefinition>()

        val configVersion = plugin.config.getInt("config-version", 1)
        if (configVersion < CURRENT_CONFIG_VERSION) {
            warnings += "Your config-version is $configVersion. Current config-version is $CURRENT_CONFIG_VERSION. Check the bundled config for new options."
        }

        replaceDefaultDrops = plugin.config.getBoolean("drops.replace-default-drops", true)
        debug = plugin.config.getBoolean("settings.debug", false) || plugin.config.getBoolean("debug", false)

        val itemDrops = plugin.config.getConfigurationSection("drops.items")
        if (itemDrops != null && itemDrops.getKeys(false).isNotEmpty()) {
            itemDrops.getKeys(false).forEach { dropId ->
                loadDrop("drops.items.$dropId", dropId, warnings)?.let(loadedDrops::add)
            }
        } else {
            loadDrop("drops.guaranteed", "guaranteed", warnings, Material.DIAMOND, 1.0, 5)?.let(loadedDrops::add)
            loadDrop("drops.bonus.gold", "bonus.gold", warnings, Material.GOLD_INGOT)?.let(loadedDrops::add)
            loadDrop("drops.bonus.iron", "bonus.iron", warnings, Material.IRON_INGOT)?.let(loadedDrops::add)
        }

        drops = loadedDrops
        warnings.forEach { plugin.logger.warning(it) }
        plugin.logger.info("Loaded ${drops.size} custom drop(s).")

        return DropLoadResult(drops.size, warnings)
    }

    fun applyDrops(event: EntityDeathEvent) {
        val killer = event.entity.killer ?: return
        val lootingLevel = killer.inventory.itemInMainHand.getEnchantmentLevel(Enchantment.LOOTING)

        drops.forEach { drop ->
            if (!isMobAllowed(drop, event.entityType)) {
                debug("Skipped '${drop.id}' for ${event.entityType}: mob filter did not match.")
                return@forEach
            }

            if (!isWorldAllowed(drop, event.entity.world.name)) {
                debug("Skipped '${drop.id}' in ${event.entity.world.name}: world filter did not match.")
                return@forEach
            }

            if (!isBiomeAllowed(drop, event.entity.location.block.biome.name())) {
                debug("Skipped '${drop.id}' in ${event.entity.location.block.biome}: biome filter did not match.")
                return@forEach
            }

            if (!hasRequiredPermission(drop, killer)) {
                debug("Skipped '${drop.id}' for ${killer.name}: permission filter did not match.")
                return@forEach
            }

            if (!hasRequiredTool(drop, killer)) {
                debug("Skipped '${drop.id}' for ${killer.name}: required tool filter did not match.")
                return@forEach
            }

            val effectiveChance = (drop.chance + (lootingLevel * drop.lootingChancePerLevel)).coerceIn(0.0, 1.0)
            if (effectiveChance <= 0.0 || ThreadLocalRandom.current().nextDouble() > effectiveChance) {
                debug("Skipped '${drop.id}': chance roll failed at $effectiveChance.")
                return@forEach
            }

            val baseAmount = ThreadLocalRandom.current().nextInt(drop.minAmount, drop.maxAmount + 1)
            val amount = (baseAmount + (lootingLevel * drop.lootingAmountPerLevel)).coerceAtLeast(1)
            event.drops.add(createItemStack(drop, amount, event.entityType.name))
            sendDropMessage(event, drop, amount)
            debug("Added '${drop.id}' x$amount for ${killer.name}.")
        }
    }

    fun dropIds(): List<String> {
        return drops.map { it.id }.sorted()
    }

    fun testDrop(
        dropId: String,
        player: Player?,
        entityType: EntityType?,
        worldName: String?,
        biomeName: String?
    ): DropTestResult {
        val drop = drops.firstOrNull { it.id.equals(dropId, ignoreCase = true) }
            ?: return DropTestResult(false, false, listOf("Drop '$dropId' is not loaded."))

        val lines = mutableListOf<String>()
        var passed = true

        lines += "Drop: ${drop.id}"
        lines += "Material: ${drop.material.name}"
        lines += "Amount: ${drop.minAmount}" + if (drop.maxAmount != drop.minAmount) "-${drop.maxAmount}" else ""
        lines += "Chance: ${drop.chance}"

        if (entityType == null) {
            lines += "Mob filter: not tested. Provide a mob name to test it."
        } else if (isMobAllowed(drop, entityType)) {
            lines += "Mob filter: passed for ${entityType.name}."
        } else {
            passed = false
            lines += "Mob filter: failed for ${entityType.name}."
        }

        if (worldName == null) {
            lines += "World filter: not tested. Run as a player or provide a world name."
        } else if (isWorldAllowed(drop, worldName)) {
            lines += "World filter: passed for $worldName."
        } else {
            passed = false
            lines += "World filter: failed for $worldName."
        }

        if (biomeName == null) {
            lines += "Biome filter: not tested. Run as a player or provide a biome name."
        } else if (isBiomeAllowed(drop, normalizeName(biomeName))) {
            lines += "Biome filter: passed for ${normalizeName(biomeName)}."
        } else {
            passed = false
            lines += "Biome filter: failed for ${normalizeName(biomeName)}."
        }

        if (player == null) {
            if (drop.permission != null || drop.requiredTools.isNotEmpty()) {
                lines += "Player filters: not tested from console."
            }
        } else {
            if (hasRequiredPermission(drop, player)) {
                lines += "Permission filter: passed."
            } else {
                passed = false
                lines += "Permission filter: failed. Required '${drop.permission}'."
            }

            if (hasRequiredTool(drop, player)) {
                lines += "Required tool filter: passed."
            } else {
                passed = false
                lines += "Required tool filter: failed. Holding ${player.inventory.itemInMainHand.type.name}."
            }
        }

        return DropTestResult(true, passed, lines)
    }

    private fun loadDrop(
        path: String,
        id: String,
        warnings: MutableList<String>,
        fallbackMaterial: Material? = null,
        fallbackChance: Double = 0.0,
        fallbackAmount: Int = 1
    ): DropDefinition? {
        val materialName = plugin.config.getString("$path.material", fallbackMaterial?.name ?: "") ?: ""
        val material = Material.matchMaterial(materialName)
        if (material == null && fallbackMaterial == null) {
            warnings += "Drop '$id' was skipped: invalid or missing material at '$path.material'."
            return null
        }

        val amountRange = readAmountRange(plugin.config, path, id, fallbackAmount, warnings)
        val chance = readChance(plugin.config, path, id, fallbackChance, warnings)

        return DropDefinition(
            id = id,
            material = material ?: fallbackMaterial ?: return null,
            minAmount = amountRange.first,
            maxAmount = amountRange.second,
            chance = chance,
            mobs = readMobs(path, id, warnings),
            worlds = readWorlds(path, id, warnings),
            biomes = readBiomes(path, id, warnings),
            requiredTools = readRequiredTools(path, id, warnings),
            permission = plugin.config.getString("$path.permission")?.takeIf { it.isNotBlank() },
            lootingChancePerLevel = plugin.config
                .getDouble("$path.looting-bonus.chance-per-level", 0.0)
                .coerceAtLeast(0.0),
            lootingAmountPerLevel = plugin.config
                .getInt("$path.looting-bonus.amount-per-level", 0)
                .coerceAtLeast(0),
            message = plugin.config.getString("$path.message")?.takeIf { it.isNotBlank() },
            displayName = plugin.config.getString("$path.display-name")?.takeIf { it.isNotBlank() },
            lore = plugin.config.getStringList("$path.lore").filter { it.isNotBlank() },
            customModelData = plugin.config.getInt("$path.custom-model-data").takeIf {
                plugin.config.contains("$path.custom-model-data") && it > 0
            }
        )
    }

    private fun readAmountRange(
        config: FileConfiguration,
        path: String,
        id: String,
        fallbackAmount: Int,
        warnings: MutableList<String>
    ): Pair<Int, Int> {
        val amountValue = config.get("$path.amount")
        val range = if (amountValue is Number) {
            val amount = amountValue.toInt().coerceAtLeast(1)
            amount to amount
        } else {
            val min = config.getInt("$path.amount.min", fallbackAmount).coerceAtLeast(1)
            val max = config.getInt("$path.amount.max", min).coerceAtLeast(1)
            min to max
        }

        if (range.second < range.first) {
            warnings += "Drop '$id' has amount.max lower than amount.min. Using amount.min for both values."
            return range.first to range.first
        }

        return range
    }

    private fun readChance(
        config: FileConfiguration,
        path: String,
        id: String,
        fallbackChance: Double,
        warnings: MutableList<String>
    ): Double {
        val rawChance = config.getDouble("$path.chance", fallbackChance)
        if (rawChance !in 0.0..1.0) {
            warnings += "Drop '$id' has chance outside 0.0-1.0. It was clamped to the valid range."
        }
        return rawChance.coerceIn(0.0, 1.0)
    }

    private fun readMobs(path: String, id: String, warnings: MutableList<String>): Set<String> {
        val configuredMobs = readStringList("$path.mobs", "$path.mob", listOf("ALL"))
        val validMobs = mutableSetOf<String>()

        configuredMobs.forEach { mob ->
            val normalizedMob = normalizeName(mob)
            if (normalizedMob == "ALL") {
                validMobs += normalizedMob
            } else if (runCatching { EntityType.valueOf(normalizedMob) }.isSuccess) {
                validMobs += normalizedMob
            } else {
                warnings += "Drop '$id' has an invalid mob name: '$mob'."
            }
        }

        if (configuredMobs.isNotEmpty() && validMobs.isEmpty()) {
            warnings += "Drop '$id' has no valid mob filters and will not match any mob."
        }

        return validMobs
    }

    private fun readWorlds(path: String, id: String, warnings: MutableList<String>): Set<String> {
        val configuredWorlds = readStringList("$path.worlds", "$path.world", listOf("ALL"))
        val worlds = configuredWorlds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        worlds
            .filterNot { it.equals("ALL", ignoreCase = true) }
            .filter { plugin.server.getWorld(it) == null }
            .forEach { warnings += "Drop '$id' references unknown world '$it'." }

        return worlds
    }

    private fun readBiomes(path: String, id: String, warnings: MutableList<String>): Set<String> {
        val configuredBiomes = readStringList("$path.biomes", "$path.biome", listOf("ALL"))
        val validBiomes = mutableSetOf<String>()

        configuredBiomes.forEach { biome ->
            val normalizedBiome = normalizeName(biome)
            if (normalizedBiome == "ALL") {
                validBiomes += normalizedBiome
            } else if (runCatching { Biome.valueOf(normalizedBiome) }.isSuccess) {
                validBiomes += normalizedBiome
            } else {
                warnings += "Drop '$id' has an invalid biome name: '$biome'."
            }
        }

        if (configuredBiomes.isNotEmpty() && validBiomes.isEmpty()) {
            warnings += "Drop '$id' has no valid biome filters and will not match any biome."
        }

        return validBiomes
    }

    private fun readRequiredTools(path: String, id: String, warnings: MutableList<String>): Set<Material> {
        val configuredTools = readStringList("$path.required-tools", "$path.required-tool", emptyList())
        val validTools = mutableSetOf<Material>()

        configuredTools.forEach { tool ->
            val material = Material.matchMaterial(tool)
            if (material == null) {
                warnings += "Drop '$id' has an invalid required tool: '$tool'."
            } else {
                validTools += material
            }
        }

        return validTools
    }

    private fun readStringList(primaryPath: String, secondaryPath: String, fallback: List<String>): List<String> {
        val rawValue = when {
            plugin.config.contains(primaryPath) -> plugin.config.get(primaryPath)
            plugin.config.contains(secondaryPath) -> plugin.config.get(secondaryPath)
            else -> return fallback
        }

        return when (rawValue) {
            is String -> rawValue.split(",").map { it.trim() }
            is List<*> -> rawValue.mapNotNull { it?.toString()?.trim() }
            else -> emptyList()
        }.filter { it.isNotEmpty() }
    }

    private fun isMobAllowed(drop: DropDefinition, entityType: EntityType): Boolean {
        return drop.mobs.contains("ALL") || drop.mobs.contains(entityType.name)
    }

    private fun isWorldAllowed(drop: DropDefinition, worldName: String): Boolean {
        return drop.worlds.any { it.equals("ALL", ignoreCase = true) || it.equals(worldName, ignoreCase = true) }
    }

    private fun isBiomeAllowed(drop: DropDefinition, biomeName: String): Boolean {
        return drop.biomes.contains("ALL") || drop.biomes.contains(normalizeName(biomeName))
    }

    private fun hasRequiredPermission(drop: DropDefinition, player: Player): Boolean {
        val permission = drop.permission ?: return true
        return player.hasPermission(permission)
    }

    private fun hasRequiredTool(drop: DropDefinition, player: Player): Boolean {
        return drop.requiredTools.isEmpty() || drop.requiredTools.contains(player.inventory.itemInMainHand.type)
    }

    private fun createItemStack(drop: DropDefinition, amount: Int, mobName: String): ItemStack {
        val itemStack = ItemStack(drop.material, amount)
        if (drop.displayName == null && drop.lore.isEmpty() && drop.customModelData == null) {
            return itemStack
        }

        val meta = itemStack.itemMeta ?: return itemStack
        drop.displayName?.let {
            meta.setDisplayName(colorize(formatPlaceholders(it, null, mobName, drop, amount)))
        }
        if (drop.lore.isNotEmpty()) {
            meta.setLore(drop.lore.map { colorize(formatPlaceholders(it, null, mobName, drop, amount)) })
        }
        drop.customModelData?.let(meta::setCustomModelData)
        itemStack.itemMeta = meta
        return itemStack
    }

    private fun sendDropMessage(event: EntityDeathEvent, drop: DropDefinition, amount: Int) {
        val killer = event.entity.killer ?: return
        val rawMessage = drop.message ?: return
        val message = formatPlaceholders(rawMessage, killer.name, event.entityType.name, drop, amount)

        killer.sendMessage(colorize(message))
    }

    private fun formatPlaceholders(
        value: String,
        playerName: String?,
        mobName: String,
        drop: DropDefinition,
        amount: Int
    ): String {
        return value
            .replace("{player}", playerName ?: "")
            .replace("{mob}", mobName)
            .replace("{drop}", drop.id)
            .replace("{material}", drop.material.name)
            .replace("{amount}", amount.toString())
    }

    private fun colorize(value: String): String {
        return ChatColor.translateAlternateColorCodes('&', value)
    }

    private fun normalizeName(value: String): String {
        return value.trim()
            .replace("-", "_")
            .replace(" ", "_")
            .uppercase(Locale.ROOT)
    }

    private fun debug(message: String) {
        if (debug) {
            plugin.logger.info("[Debug] $message")
        }
    }
}
