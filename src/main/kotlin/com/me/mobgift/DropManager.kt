package com.me.mobgift

import com.me.mobgift.api.event.MobGiftDropAwardEvent
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Registry
import org.bukkit.Sound
import org.bukkit.block.Biome
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

class DropManager(
    private val plugin: MobGift
) {
    companion object {
        const val CURRENT_CONFIG_VERSION = 8
        const val HIGH_COOLDOWN_WARNING_SECONDS = 86_400L
        private const val STATS_SAVE_INTERVAL_MILLIS = 60_000L

        private val PLACEHOLDER_PATTERN = Regex("\\{([A-Za-z0-9_-]+)}")
        private val SUPPORTED_PLACEHOLDERS = setOf("player", "mob", "drop", "material", "amount", "xp")
        private val REWARD_LOG_FILE_PATTERN = Regex("[A-Za-z0-9._-]+")
    }

    var replaceDefaultDrops: Boolean = false
        private set

    var allowSpawnerMobs: Boolean = true
        private set

    var debug: Boolean = false
        private set

    var drops: List<DropDefinition> = emptyList()
        private set

    private val cooldowns = ConcurrentHashMap<String, Long>()

    var persistCooldowns: Boolean = false
        private set

    var rewardLogEnabled: Boolean = false
        private set

    private var rewardLogFileName: String = "rewards.csv"
    private var rewardLogFailureNotified: Boolean = false

    private val dropStats = ConcurrentHashMap<String, MutableDropStats>()
    private val playerStats = ConcurrentHashMap<String, MutablePlayerStats>()
    private var statsLoaded = false
    private var statsDirty = false
    private var lastStatsSaveAt = 0L

    private data class MutableDropStats(
        val dropId: String,
        var awards: Long = 0,
        var forcedAwards: Long = 0,
        var itemAmount: Long = 0,
        var xpAmount: Long = 0,
        var commandRuns: Long = 0,
        var lastAwardedAt: Long = 0,
        var lastPlayerName: String? = null,
        var lastMob: String? = null
    )

    private data class MutablePlayerStats(
        val playerUuid: String,
        var playerName: String,
        var awards: Long = 0,
        var forcedAwards: Long = 0,
        var itemAmount: Long = 0,
        var xpAmount: Long = 0,
        var commandRuns: Long = 0,
        var lastAwardedAt: Long = 0,
        var lastDropId: String? = null,
        var lastMob: String? = null
    )

    fun reload(): DropLoadResult {
        val warnings = mutableListOf<String>()
        val loadedDrops = mutableListOf<DropDefinition>()

        val configVersion = plugin.config.getInt("config-version", 1)
        if (configVersion < CURRENT_CONFIG_VERSION) {
            warnings += "Your config-version is $configVersion. Current config-version is $CURRENT_CONFIG_VERSION. Check the bundled config for new options."
        }

        replaceDefaultDrops = plugin.config.getBoolean("drops.replace-default-drops", false)
        allowSpawnerMobs = plugin.config.getBoolean("settings.allow-spawner-mobs", true)
        debug = plugin.config.getBoolean("settings.debug", false) || plugin.config.getBoolean("debug", false)
        persistCooldowns = plugin.config.getBoolean("settings.persist-cooldowns", false)
        rewardLogEnabled = plugin.config.getBoolean("settings.reward-log.enabled", false)
        rewardLogFileName = readRewardLogFileName(warnings)
        if (persistCooldowns) {
            loadPersistentCooldowns(warnings)
        }
        if (!statsLoaded) {
            loadStats(warnings)
            statsLoaded = true
        }

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

    fun shutdown() {
        if (persistCooldowns) {
            savePersistentCooldownsSafely()
        }
        saveStatsSafely(force = true)
    }

    fun applyDrops(event: EntityDeathEvent) {
        val killer = event.entity.killer ?: return
        val lootingLevel = killer.inventory.itemInMainHand.getEnchantmentLevel(Enchantment.LOOTING)

        drops.forEach { drop ->
            if (!drop.enabled) {
                debug("Skipped '${drop.id}': drop is disabled.")
                return@forEach
            }

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

            if (!drop.allowSpawnerMobs && event.entity.fromMobSpawner()) {
                debug("Skipped '${drop.id}' for ${event.entityType}: spawner mobs are disabled for this drop.")
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

            val now = System.currentTimeMillis()
            val remainingCooldown = remainingCooldownSeconds(drop, killer, now)
            if (remainingCooldown > 0) {
                debug("Skipped '${drop.id}' for ${killer.name}: cooldown active for ${remainingCooldown}s.")
                return@forEach
            }

            val effectiveChance = (drop.chance + (lootingLevel * drop.lootingChancePerLevel)).coerceIn(0.0, 1.0)
            if (effectiveChance <= 0.0 || ThreadLocalRandom.current().nextDouble() > effectiveChance) {
                debug("Skipped '${drop.id}': chance roll failed at $effectiveChance.")
                return@forEach
            }

            val itemAmount = if (drop.material != null) {
                val baseAmount = ThreadLocalRandom.current().nextInt(drop.minAmount, drop.maxAmount + 1)
                (baseAmount + (lootingLevel * drop.lootingAmountPerLevel)).coerceAtLeast(1)
            } else {
                0
            }
            val xpAmount = if (drop.xpMaxAmount > 0) {
                ThreadLocalRandom.current().nextInt(drop.xpMinAmount, drop.xpMaxAmount + 1)
            } else {
                0
            }
            val rewardLocation = event.entity.location.clone()
            val awardEvent = MobGiftDropAwardEvent(
                player = killer,
                drop = drop,
                source = event.entityType.name,
                entityType = event.entityType,
                location = rewardLocation,
                forced = false,
                initialItemAmount = itemAmount,
                initialXpAmount = xpAmount
            )
            plugin.server.pluginManager.callEvent(awardEvent)
            if (awardEvent.isCancelled) {
                debug("Skipped '${drop.id}' for ${killer.name}: award was cancelled by MobGiftDropAwardEvent.")
                return@forEach
            }

            val finalItemAmount = awardEvent.itemAmount
            val finalXpAmount = awardEvent.xpAmount
            if (drop.material != null && finalItemAmount > 0) {
                event.drops.add(createItemStack(drop, finalItemAmount, event.entityType.name, killer.name))
            }
            if (finalXpAmount > 0) {
                event.droppedExp = (event.droppedExp + finalXpAmount).coerceAtLeast(0)
            }

            sendDropMessage(event, drop, finalItemAmount, finalXpAmount)
            sendBroadcast(event, drop, finalItemAmount, finalXpAmount)
            runCommands(event, drop, finalItemAmount, finalXpAmount)
            playEffects(event, drop)
            recordStats(
                drop = drop,
                player = killer,
                sourceName = event.entityType.name,
                worldName = event.entity.world.name,
                blockX = rewardLocation.blockX,
                blockY = rewardLocation.blockY,
                blockZ = rewardLocation.blockZ,
                itemAmount = finalItemAmount,
                xpAmount = finalXpAmount,
                forced = false
            )
            markCooldown(drop, killer, now)
            debug("Applied '${drop.id}' for ${killer.name}: ${describeRewards(drop, finalItemAmount, finalXpAmount)}.")
        }
    }

    fun dropIds(): List<String> {
        return drops.map { it.id }.sorted()
    }

    fun getDrop(dropId: String): DropDefinition? {
        return findDrop(dropId)
    }

    fun dropInfo(dropId: String): List<String>? {
        val drop = findDrop(dropId) ?: return null
        return buildList {
            add("Drop: ${drop.id}")
            add("Status: ${if (drop.enabled) "enabled" else "disabled"}")
            add("Rewards: ${describeConfiguredRewards(drop)}")
            add("Chance: ${formatChance(drop)}")
            add("Cooldown: ${formatDuration(drop.cooldownSeconds)}")
            add("Mobs: ${drop.mobs.joinToString(", ")}")
            add("Worlds: ${drop.worlds.joinToString(", ")}")
            add("Biomes: ${drop.biomes.joinToString(", ")}")
            add("Permission: ${drop.permission ?: "none"}")
            add("Required tools: ${drop.requiredTools.joinToString(", ") { it.name }.ifBlank { "none" }}")
            add("Spawner mobs: ${if (drop.allowSpawnerMobs) "allowed" else "blocked"}")
            add("Display name: ${drop.displayName ?: "none"}")
            add("Lore lines: ${drop.lore.size}")
            add("Enchantments: ${drop.enchantments.size}")
            add("Item flags: ${drop.itemFlags.size}")
            add("Unbreakable: ${if (drop.unbreakable) "true" else "false"}")
            add("Commands: ${drop.commands.size}")
            add("Broadcast: ${if (drop.broadcast == null) "none" else "configured"}")
            add("Sound: ${if (drop.sound == null) "none" else "configured"}")
            add("Particle: ${drop.particle?.particle?.name ?: "none"}")
        }
    }

    fun giveDrop(dropId: String, target: Player, amountOverride: Int? = null): List<String>? {
        val drop = findDrop(dropId) ?: return null
        val itemAmount = if (drop.material != null) {
            amountOverride ?: ThreadLocalRandom.current().nextInt(drop.minAmount, drop.maxAmount + 1)
        } else {
            0
        }.coerceAtLeast(0)
        val xpAmount = if (drop.xpMaxAmount > 0) {
            ThreadLocalRandom.current().nextInt(drop.xpMinAmount, drop.xpMaxAmount + 1)
        } else {
            0
        }
        val rewardLocation = target.location.clone()
        val awardEvent = MobGiftDropAwardEvent(
            player = target,
            drop = drop,
            source = "ADMIN",
            entityType = null,
            location = rewardLocation,
            forced = true,
            initialItemAmount = itemAmount,
            initialXpAmount = xpAmount
        )
        plugin.server.pluginManager.callEvent(awardEvent)
        if (awardEvent.isCancelled) {
            return listOf("Forced drop '${drop.id}' for ${target.name} was cancelled by MobGiftDropAwardEvent.")
        }

        val finalItemAmount = awardEvent.itemAmount
        val finalXpAmount = awardEvent.xpAmount
        if (drop.material != null && finalItemAmount > 0) {
            val leftovers = target.inventory.addItem(createItemStack(drop, finalItemAmount, "ADMIN", target.name))
            leftovers.values.forEach { target.world.dropItemNaturally(target.location, it) }
        }
        if (finalXpAmount > 0) {
            target.giveExp(finalXpAmount)
        }

        sendConfiguredMessage(target, drop, "ADMIN", finalItemAmount, finalXpAmount)
        sendConfiguredBroadcast(target, drop, "ADMIN", finalItemAmount, finalXpAmount)
        runConfiguredCommands(target, drop, "ADMIN", finalItemAmount, finalXpAmount)
        playConfiguredEffects(target, drop)
        recordStats(
            drop = drop,
            player = target,
            sourceName = "ADMIN",
            worldName = target.world.name,
            blockX = rewardLocation.blockX,
            blockY = rewardLocation.blockY,
            blockZ = rewardLocation.blockZ,
            itemAmount = finalItemAmount,
            xpAmount = finalXpAmount,
            forced = true
        )

        return buildList {
            add("Forced drop '${drop.id}' for ${target.name}.")
            add("Rewards: ${describeRewards(drop, finalItemAmount, finalXpAmount)}.")
            if (!drop.enabled) {
                add("Note: drop is disabled in normal mob kills.")
            }
            if (drop.cooldownSeconds > 0) {
                add("Note: /mobgift give does not start cooldowns.")
            }
        }
    }

    fun resetCooldown(player: Player, dropId: String? = null): Int {
        val prefix = "${player.uniqueId}:"
        val targetDropId = dropId?.lowercase(Locale.ROOT)
        val keysToRemove = cooldowns.keys.filter { key ->
            key.startsWith(prefix) && (targetDropId == null || key == "$prefix$targetDropId")
        }
        keysToRemove.forEach(cooldowns::remove)
        if (keysToRemove.isNotEmpty() && persistCooldowns) {
            savePersistentCooldownsSafely()
        }
        return keysToRemove.size
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
        lines += "Status: ${if (drop.enabled) "enabled" else "disabled"}."
        lines += "Cooldown: ${formatDuration(drop.cooldownSeconds)}."
        lines += "Rewards: ${describeConfiguredRewards(drop)}"
        lines += "Chance: ${formatChance(drop)}"
        lines += "Spawner mobs: ${if (drop.allowSpawnerMobs) "allowed" else "blocked"}."
        if (!drop.enabled) {
            passed = false
            lines += "Drop is disabled and will not be awarded."
        }

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

            val remainingCooldown = remainingCooldownSeconds(drop, player)
            if (remainingCooldown > 0) {
                passed = false
                lines += "Cooldown: active for ${remainingCooldown}s."
            } else if (drop.cooldownSeconds > 0) {
                lines += "Cooldown: ready."
            }
        }

        return DropTestResult(true, passed, lines)
    }

    fun previewDrops(
        entityType: EntityType,
        player: Player?,
        worldName: String?,
        biomeName: String?
    ): List<String> {
        val matchingDrops = drops.filter { drop ->
            drop.enabled &&
                isMobAllowed(drop, entityType) &&
                (worldName == null || isWorldAllowed(drop, worldName)) &&
                (biomeName == null || isBiomeAllowed(drop, biomeName)) &&
                (player == null || hasRequiredPermission(drop, player)) &&
                (player == null || hasRequiredTool(drop, player)) &&
                (player == null || remainingCooldownSeconds(drop, player) <= 0)
        }.sortedBy { it.id }

        val context = buildList {
            add(entityType.name)
            worldName?.let { add("world=$it") }
            biomeName?.let { add("biome=${normalizeName(it)}") }
        }.joinToString(", ")

        if (matchingDrops.isEmpty()) {
            return listOf("No MobGift drops match $context.")
        }

        return buildList {
            add("MobGift preview for $context:")
            matchingDrops.forEach { drop ->
                val spawnerNote = if (drop.allowSpawnerMobs) "" else " | spawners blocked"
                val cooldownNote = if (drop.cooldownSeconds > 0) " | cooldown ${formatDuration(drop.cooldownSeconds)}" else ""
                add("- ${drop.id}: ${describeConfiguredRewards(drop)} | chance ${formatChance(drop)}$spawnerNote$cooldownNote")
            }
        }
    }

    @Synchronized
    fun dropStatsSnapshot(dropId: String): DropStatsSnapshot? {
        return dropStats[statsKey(dropId)]?.toSnapshot()
    }

    @Synchronized
    fun playerStatsSnapshot(playerNameOrUuid: String): PlayerStatsSnapshot? {
        return findPlayerStats(playerNameOrUuid)?.toSnapshot()
    }

    @Synchronized
    fun statsSummary(limit: Int = 8): List<String> {
        val topDrops = topDropStats(limit)
        val totalAwards = dropStats.values.sumOf { it.awards }
        val totalItems = dropStats.values.sumOf { it.itemAmount }
        val totalXp = dropStats.values.sumOf { it.xpAmount }
        val totalCommands = dropStats.values.sumOf { it.commandRuns }

        return buildList {
            add("MobGift stats:")
            add("Total awards: $totalAwards")
            add("Items awarded: $totalItems")
            add("XP awarded: $totalXp")
            add("Commands run: $totalCommands")
            if (topDrops.isEmpty()) {
                add("No drop stats recorded yet.")
            } else {
                add("Top drops:")
                topDrops.forEachIndexed { index, stats ->
                    val forcedText = if (stats.forcedAwards > 0) ", ${stats.forcedAwards} forced" else ""
                    add("${index + 1}. ${stats.dropId}: ${stats.awards} award(s)$forcedText, ${stats.itemAmount} item(s), ${stats.xpAmount} XP")
                }
            }
        }
    }

    @Synchronized
    fun dropStatsInfo(dropId: String): List<String>? {
        val stats = dropStats[statsKey(dropId)]?.toSnapshot() ?: return null
        return buildList {
            add("Stats for drop: ${stats.dropId}")
            add("Awards: ${stats.awards}")
            add("Forced awards: ${stats.forcedAwards}")
            add("Items awarded: ${stats.itemAmount}")
            add("XP awarded: ${stats.xpAmount}")
            add("Commands run: ${stats.commandRuns}")
            add("Last awarded: ${formatTimestamp(stats.lastAwardedAt)}")
            add("Last player: ${stats.lastPlayerName ?: "none"}")
            add("Last mob/source: ${stats.lastMob ?: "none"}")
        }
    }

    @Synchronized
    fun playerStatsInfo(playerNameOrUuid: String): List<String>? {
        val stats = findPlayerStats(playerNameOrUuid)?.toSnapshot() ?: return null
        return buildList {
            add("MobGift stats for player: ${stats.playerName}")
            add("UUID: ${stats.playerUuid}")
            add("Awards: ${stats.awards}")
            add("Forced awards: ${stats.forcedAwards}")
            add("Items awarded: ${stats.itemAmount}")
            add("XP awarded: ${stats.xpAmount}")
            add("Commands run: ${stats.commandRuns}")
            add("Last awarded: ${formatTimestamp(stats.lastAwardedAt)}")
            add("Last drop: ${stats.lastDropId ?: "none"}")
            add("Last mob/source: ${stats.lastMob ?: "none"}")
        }
    }

    @Synchronized
    fun topDropStats(limit: Int = 10): List<DropStatsSnapshot> {
        return dropStats.values
            .map { it.toSnapshot() }
            .sortedWith(compareByDescending<DropStatsSnapshot> { it.awards }.thenBy { it.dropId.lowercase(Locale.ROOT) })
            .take(limit.coerceAtLeast(0))
    }

    @Synchronized
    fun topPlayerStats(limit: Int = 10): List<PlayerStatsSnapshot> {
        return playerStats.values
            .map { it.toSnapshot() }
            .sortedWith(compareByDescending<PlayerStatsSnapshot> { it.awards }.thenBy { it.playerName.lowercase(Locale.ROOT) })
            .take(limit.coerceAtLeast(0))
    }

    @Synchronized
    fun statsPlayerNames(): List<String> {
        return playerStats.values
            .map { it.playerName }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    @Synchronized
    fun exportStats(): File {
        val file = File(plugin.dataFolder, "stats-export-${System.currentTimeMillis()}.csv")
        file.parentFile?.mkdirs()
        val lines = mutableListOf<String>()
        lines += listOf(
            "type",
            "id",
            "name",
            "awards",
            "forced_awards",
            "item_amount",
            "xp_amount",
            "command_runs",
            "last_awarded_at",
            "last_player_or_drop",
            "last_source"
        ).joinToString(",") { csvValue(it) }

        dropStats.values
            .map { it.toSnapshot() }
            .sortedWith(compareByDescending<DropStatsSnapshot> { it.awards }.thenBy { it.dropId.lowercase(Locale.ROOT) })
            .forEach { stats ->
                lines += listOf(
                    "drop",
                    stats.dropId,
                    stats.dropId,
                    stats.awards.toString(),
                    stats.forcedAwards.toString(),
                    stats.itemAmount.toString(),
                    stats.xpAmount.toString(),
                    stats.commandRuns.toString(),
                    formatTimestamp(stats.lastAwardedAt),
                    stats.lastPlayerName ?: "",
                    stats.lastMob ?: ""
                ).joinToString(",") { csvValue(it) }
            }

        playerStats.values
            .map { it.toSnapshot() }
            .sortedWith(compareByDescending<PlayerStatsSnapshot> { it.awards }.thenBy { it.playerName.lowercase(Locale.ROOT) })
            .forEach { stats ->
                lines += listOf(
                    "player",
                    stats.playerUuid,
                    stats.playerName,
                    stats.awards.toString(),
                    stats.forcedAwards.toString(),
                    stats.itemAmount.toString(),
                    stats.xpAmount.toString(),
                    stats.commandRuns.toString(),
                    formatTimestamp(stats.lastAwardedAt),
                    stats.lastDropId ?: "",
                    stats.lastMob ?: ""
                ).joinToString(",") { csvValue(it) }
            }

        file.writeText(lines.joinToString("\n") + "\n", Charsets.UTF_8)
        return file
    }

    @Synchronized
    fun resetAllStats(): Pair<Int, Int> {
        val removedDrops = dropStats.size
        val removedPlayers = playerStats.size
        dropStats.clear()
        playerStats.clear()
        statsDirty = true
        saveStatsSafely(force = true)
        return removedDrops to removedPlayers
    }

    @Synchronized
    fun resetDropStats(dropId: String): Boolean {
        val removed = dropStats.remove(statsKey(dropId)) != null
        if (removed) {
            statsDirty = true
            saveStatsSafely(force = true)
        }
        return removed
    }

    @Synchronized
    fun resetPlayerStats(playerNameOrUuid: String): String? {
        val stats = findPlayerStats(playerNameOrUuid) ?: return null
        playerStats.remove(stats.playerUuid)
        statsDirty = true
        saveStatsSafely(force = true)
        return stats.playerName
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
        val parsedMaterial = materialName.takeIf { it.isNotBlank() }?.let(Material::matchMaterial)
        val material = (parsedMaterial ?: fallbackMaterial)?.takeUnless { it == Material.AIR }
        val commands = readCommandList("$path.commands")
        val xpRange = readXpRange(plugin.config, path, id, warnings)
        val cooldownSeconds = readCooldownSeconds(path)
        val permission = plugin.config.getString("$path.permission")?.takeIf { it.isNotBlank() }
        val message = plugin.config.getString("$path.message")?.takeIf { it.isNotBlank() }
        val displayName = plugin.config.getString("$path.display-name")?.takeIf { it.isNotBlank() }
        val lore = plugin.config.getStringList("$path.lore").filter { it.isNotBlank() }
        val broadcast = plugin.config.getString("$path.broadcast")?.takeIf { it.isNotBlank() }

        if (materialName.isNotBlank() && parsedMaterial == null) {
            warnings += "Drop '$id' has an invalid material at '$path.material'. Item reward disabled."
        } else if (parsedMaterial == Material.AIR) {
            warnings += "Drop '$id' uses AIR as material. Item reward disabled."
        }

        if (material == null && xpRange.second <= 0 && commands.isEmpty()) {
            warnings += "Drop '$id' was skipped: configure a valid material, xp, or commands reward."
            return null
        }

        val amountRange = readAmountRange(plugin.config, path, id, fallbackAmount, warnings)
        val chance = readChance(plugin.config, path, id, fallbackChance, warnings)
        validateDropConfig(id, commands, permission, message, broadcast, displayName, lore, cooldownSeconds, warnings)

        return DropDefinition(
            id = id,
            enabled = plugin.config.getBoolean("$path.enabled", true),
            cooldownSeconds = cooldownSeconds,
            material = material,
            minAmount = amountRange.first,
            maxAmount = amountRange.second,
            chance = chance,
            mobs = readMobs(path, id, warnings),
            worlds = readWorlds(path, id, warnings),
            biomes = readBiomes(path, id, warnings),
            requiredTools = readRequiredTools(path, id, warnings),
            permission = permission,
            lootingChancePerLevel = plugin.config
                .getDouble("$path.looting-bonus.chance-per-level", 0.0)
                .coerceAtLeast(0.0),
            lootingAmountPerLevel = plugin.config
                .getInt("$path.looting-bonus.amount-per-level", 0)
                .coerceAtLeast(0),
            message = message,
            displayName = displayName,
            lore = lore,
            customModelData = plugin.config.getInt("$path.custom-model-data").takeIf {
                plugin.config.contains("$path.custom-model-data") && it > 0
            },
            enchantments = readEnchantments(path, id, warnings),
            itemFlags = readItemFlags(path, id, warnings),
            unbreakable = plugin.config.getBoolean("$path.unbreakable", false),
            xpMinAmount = xpRange.first,
            xpMaxAmount = xpRange.second,
            commands = commands,
            broadcast = broadcast,
            sound = readSound(path, id, warnings),
            particle = readParticle(path, id, warnings),
            allowSpawnerMobs = plugin.config.getBoolean("$path.allow-spawner-mobs", allowSpawnerMobs)
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

    private fun readXpRange(
        config: FileConfiguration,
        path: String,
        id: String,
        warnings: MutableList<String>
    ): Pair<Int, Int> {
        val xpValue = config.get("$path.xp")
        val range = if (xpValue is Number) {
            val xp = xpValue.toInt().coerceAtLeast(0)
            xp to xp
        } else {
            val min = config.getInt("$path.xp.min", 0).coerceAtLeast(0)
            val max = config.getInt("$path.xp.max", min).coerceAtLeast(0)
            min to max
        }

        if (range.second < range.first) {
            warnings += "Drop '$id' has xp.max lower than xp.min. Using xp.min for both values."
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

    private fun readCooldownSeconds(path: String): Long {
        val seconds = plugin.config.getLong("$path.cooldown-seconds", plugin.config.getLong("$path.cooldown", 0L))
        return seconds.coerceAtLeast(0L)
    }

    private fun validateDropConfig(
        id: String,
        commands: List<String>,
        permission: String?,
        message: String?,
        broadcast: String?,
        displayName: String?,
        lore: List<String>,
        cooldownSeconds: Long,
        warnings: MutableList<String>
    ) {
        if (permission != null && permission.any(Char::isWhitespace)) {
            warnings += "Drop '$id' has a permission containing whitespace: '$permission'. Permission checks will likely fail."
        }

        if (cooldownSeconds > HIGH_COOLDOWN_WARNING_SECONDS) {
            warnings += "Drop '$id' has cooldown-seconds $cooldownSeconds, which is longer than 24 hours. Confirm this is intentional."
        }

        validatePlaceholders(id, "message", message, warnings)
        validatePlaceholders(id, "broadcast", broadcast, warnings)
        validatePlaceholders(id, "display-name", displayName, warnings)
        lore.forEachIndexed { index, line ->
            validatePlaceholders(id, "lore[${index + 1}]", line, warnings)
        }
        commands.forEachIndexed { index, command ->
            validatePlaceholders(id, "commands[${index + 1}]", command, warnings)
        }
    }

    private fun validatePlaceholders(
        id: String,
        field: String,
        value: String?,
        warnings: MutableList<String>
    ) {
        if (value.isNullOrBlank()) {
            return
        }

        PLACEHOLDER_PATTERN.findAll(value)
            .map { it.groupValues[1] }
            .filterNot { it in SUPPORTED_PLACEHOLDERS }
            .distinct()
            .forEach { placeholder ->
                warnings += "Drop '$id' has unknown placeholder '{$placeholder}' in $field. Supported placeholders: ${SUPPORTED_PLACEHOLDERS.joinToString(", ") { "{$it}" }}."
            }
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

    private fun readEnchantments(path: String, id: String, warnings: MutableList<String>): Map<Enchantment, Int> {
        val enchantments = linkedMapOf<Enchantment, Int>()
        val section = plugin.config.getConfigurationSection("$path.enchantments")
        if (section != null) {
            section.getKeys(false).forEach { enchantmentName ->
                val enchantment = parseEnchantment(enchantmentName)
                if (enchantment == null) {
                    warnings += "Drop '$id' has an invalid enchantment: '$enchantmentName'."
                    return@forEach
                }

                val level = section.getInt(enchantmentName, 1).coerceAtLeast(1)
                enchantments[enchantment] = level
            }
            return enchantments
        }

        readStringList("$path.enchantments", "$path.enchantment", emptyList()).forEach { entry ->
            val parts = entry.split(":", "=", limit = 2).map { it.trim() }
            val enchantmentName = parts.firstOrNull().orEmpty()
            val enchantment = parseEnchantment(enchantmentName)
            if (enchantment == null) {
                warnings += "Drop '$id' has an invalid enchantment: '$enchantmentName'."
                return@forEach
            }

            val level = parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            enchantments[enchantment] = level
        }

        return enchantments
    }

    private fun parseEnchantment(value: String): Enchantment? {
        val enchantmentFromField = runCatching {
            Enchantment::class.java.getField(normalizeName(value)).get(null) as? Enchantment
        }.getOrNull()
        if (enchantmentFromField != null) {
            return enchantmentFromField
        }

        val registryName = value.trim()
            .lowercase(Locale.ROOT)
            .replace(" ", "_")
            .replace("-", "_")
        return Registry.ENCHANTMENT.match(registryName)
    }

    private fun readItemFlags(path: String, id: String, warnings: MutableList<String>): Set<ItemFlag> {
        val itemFlags = mutableSetOf<ItemFlag>()
        val configuredFlags = readStringList("$path.item-flags", "$path.item-flag", emptyList())

        configuredFlags.forEach { flag ->
            val itemFlag = runCatching { ItemFlag.valueOf(normalizeName(flag)) }.getOrNull()
            if (itemFlag == null) {
                warnings += "Drop '$id' has an invalid item flag: '$flag'."
            } else {
                itemFlags += itemFlag
            }
        }

        return itemFlags
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

    private fun readCommandList(path: String): List<String> {
        val rawValue = plugin.config.get(path) ?: plugin.config.get(path.removeSuffix("s"))

        return when (rawValue) {
            is String -> listOf(rawValue.trim())
            is List<*> -> rawValue.mapNotNull { it?.toString()?.trim() }
            else -> emptyList()
        }.filter { it.isNotEmpty() }
    }

    private fun readRewardLogFileName(warnings: MutableList<String>): String {
        val configuredName = plugin.config.getString("settings.reward-log.file", "rewards.csv")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "rewards.csv"

        if (!configuredName.matches(REWARD_LOG_FILE_PATTERN) || configuredName == "." || configuredName.contains("..")) {
            warnings += "settings.reward-log.file must be a simple file name using letters, numbers, '.', '_' or '-' and cannot contain '..'. Using rewards.csv."
            return "rewards.csv"
        }

        return configuredName
    }

    private fun readSound(path: String, id: String, warnings: MutableList<String>): DropSound? {
        if (!plugin.config.contains("$path.sound")) {
            return null
        }

        val rawValue = plugin.config.get("$path.sound")
        val soundName = if (rawValue is String) rawValue else plugin.config.getString("$path.sound.name")
        if (soundName.isNullOrBlank()) {
            warnings += "Drop '$id' has a sound section without a sound name."
            return null
        }

        val sound = parseSound(soundName)
        if (sound == null) {
            warnings += "Drop '$id' has an invalid sound: '$soundName'."
            return null
        }

        val volume = plugin.config.getDouble("$path.sound.volume", 1.0).toFloat().coerceAtLeast(0.0f)
        val pitch = plugin.config.getDouble("$path.sound.pitch", 1.0).toFloat().coerceIn(0.5f, 2.0f)
        return DropSound(sound, volume, pitch)
    }

    private fun parseSound(value: String): Sound? {
        val constantName = normalizeName(value)
        val soundFromField = runCatching {
            Sound::class.java.getField(constantName).get(null) as? Sound
        }.getOrNull()
        if (soundFromField != null) {
            return soundFromField
        }

        val registryName = value.trim().lowercase(Locale.ROOT).let {
            if (it.contains(".") || it.contains(":")) it else it.replace("_", ".")
        }
        return Registry.SOUNDS.match(registryName)
    }

    private fun readParticle(path: String, id: String, warnings: MutableList<String>): DropParticle? {
        if (!plugin.config.contains("$path.particle")) {
            return null
        }

        val rawValue = plugin.config.get("$path.particle")
        val particleName = if (rawValue is String) rawValue else plugin.config.getString("$path.particle.name")
        if (particleName.isNullOrBlank()) {
            warnings += "Drop '$id' has a particle section without a particle name."
            return null
        }

        val particle = runCatching { Particle.valueOf(normalizeName(particleName)) }.getOrNull()
        if (particle == null) {
            warnings += "Drop '$id' has an invalid particle: '$particleName'."
            return null
        }

        if (particle.dataType != Void::class.java) {
            warnings += "Drop '$id' uses particle '${particle.name}' which requires extra data. Use a simple particle."
            return null
        }

        return DropParticle(
            particle = particle,
            count = plugin.config.getInt("$path.particle.count", 12).coerceAtLeast(1),
            offsetX = plugin.config.getDouble("$path.particle.offset-x", 0.35).coerceAtLeast(0.0),
            offsetY = plugin.config.getDouble("$path.particle.offset-y", 0.35).coerceAtLeast(0.0),
            offsetZ = plugin.config.getDouble("$path.particle.offset-z", 0.35).coerceAtLeast(0.0),
            extra = plugin.config.getDouble("$path.particle.extra", 0.0).coerceAtLeast(0.0)
        )
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

    private fun remainingCooldownSeconds(drop: DropDefinition, player: Player, now: Long = System.currentTimeMillis()): Long {
        if (drop.cooldownSeconds <= 0) {
            return 0
        }

        val key = cooldownKey(drop, player)
        val expiresAt = cooldowns[key] ?: return 0
        val remainingMillis = expiresAt - now
        if (remainingMillis <= 0) {
            cooldowns.remove(key)
            return 0
        }

        return (remainingMillis + 999) / 1000
    }

    private fun markCooldown(drop: DropDefinition, player: Player, now: Long = System.currentTimeMillis()) {
        if (drop.cooldownSeconds <= 0) {
            return
        }

        cooldowns[cooldownKey(drop, player)] = now + (drop.cooldownSeconds * 1000L)
        if (persistCooldowns) {
            savePersistentCooldownsSafely()
        }
    }

    private fun cooldownKey(drop: DropDefinition, player: Player): String {
        return "${player.uniqueId}:${drop.id.lowercase(Locale.ROOT)}"
    }

    private fun loadPersistentCooldowns(warnings: MutableList<String>) {
        val file = cooldownFile()
        if (!file.exists()) {
            return
        }

        val now = System.currentTimeMillis()
        val config = YamlConfiguration.loadConfiguration(file)
        val section = config.getConfigurationSection("cooldowns") ?: return
        var loaded = 0
        section.getKeys(false).forEach { storageKey ->
            val entrySection = section.getConfigurationSection(storageKey)
            val key = if (entrySection == null) {
                storageKey
            } else {
                entrySection.getString("key") ?: decodeStorageKey(storageKey) ?: storageKey
            }
            val expiresAt = entrySection?.getLong("expires-at", 0L) ?: section.getLong(storageKey, 0L)
            if (expiresAt > now) {
                cooldowns.merge(key, expiresAt, ::maxOf)
                loaded++
            } else {
                cooldowns.remove(key)
            }
        }

        if (loaded > 0) {
            plugin.logger.info("Loaded $loaded persisted MobGift cooldown(s).")
        }
        savePersistentCooldownsSafely(warnings)
    }

    private fun savePersistentCooldownsSafely(warnings: MutableList<String>? = null) {
        runCatching { savePersistentCooldowns() }
            .onFailure {
                val message = "Could not save MobGift cooldowns to cooldowns.yml: ${it.message}"
                if (warnings == null) {
                    plugin.logger.warning(message)
                } else {
                    warnings += message
                }
            }
    }

    private fun savePersistentCooldowns() {
        val file = cooldownFile()
        file.parentFile?.mkdirs()
        val now = System.currentTimeMillis()
        val config = YamlConfiguration()

        cooldowns.entries
            .filter { it.value > now }
            .sortedBy { it.key }
            .forEach { (key, expiresAt) ->
                val path = "cooldowns.${encodeStorageKey(key)}"
                config.set("$path.key", key)
                config.set("$path.expires-at", expiresAt)
            }

        cooldowns.entries
            .filter { it.value <= now }
            .map { it.key }
            .forEach(cooldowns::remove)

        config.save(file)
    }

    private fun cooldownFile(): File {
        return File(plugin.dataFolder, "cooldowns.yml")
    }

    @Synchronized
    private fun recordStats(
        drop: DropDefinition,
        player: Player,
        sourceName: String,
        worldName: String,
        blockX: Int,
        blockY: Int,
        blockZ: Int,
        itemAmount: Int,
        xpAmount: Int,
        forced: Boolean
    ) {
        val now = System.currentTimeMillis()
        val commandRuns = drop.commands.size.toLong()
        val dropStat = dropStats.computeIfAbsent(statsKey(drop.id)) {
            MutableDropStats(drop.id)
        }
        dropStat.awards++
        if (forced) {
            dropStat.forcedAwards++
        }
        dropStat.itemAmount += itemAmount.toLong().coerceAtLeast(0L)
        dropStat.xpAmount += xpAmount.toLong().coerceAtLeast(0L)
        dropStat.commandRuns += commandRuns
        dropStat.lastAwardedAt = now
        dropStat.lastPlayerName = player.name
        dropStat.lastMob = sourceName

        val playerStat = playerStats.computeIfAbsent(player.uniqueId.toString()) {
            MutablePlayerStats(player.uniqueId.toString(), player.name)
        }
        playerStat.playerName = player.name
        playerStat.awards++
        if (forced) {
            playerStat.forcedAwards++
        }
        playerStat.itemAmount += itemAmount.toLong().coerceAtLeast(0L)
        playerStat.xpAmount += xpAmount.toLong().coerceAtLeast(0L)
        playerStat.commandRuns += commandRuns
        playerStat.lastAwardedAt = now
        playerStat.lastDropId = drop.id
        playerStat.lastMob = sourceName

        statsDirty = true
        saveStatsSafely()
        writeRewardLogSafely(
            timestamp = now,
            drop = drop,
            player = player,
            sourceName = sourceName,
            worldName = worldName,
            blockX = blockX,
            blockY = blockY,
            blockZ = blockZ,
            itemAmount = itemAmount,
            xpAmount = xpAmount,
            commandRuns = commandRuns,
            forced = forced
        )
    }

    @Synchronized
    private fun loadStats(warnings: MutableList<String>) {
        val file = statsFile()
        if (!file.exists()) {
            return
        }

        val config = YamlConfiguration.loadConfiguration(file)
        dropStats.clear()
        playerStats.clear()

        config.getConfigurationSection("drops")?.getKeys(false)?.forEach { key ->
            val path = "drops.$key"
            val dropId = config.getString("$path.id") ?: decodeStorageKey(key) ?: return@forEach
            dropStats[statsKey(dropId)] = MutableDropStats(
                dropId = dropId,
                awards = config.getLong("$path.awards", 0L).coerceAtLeast(0L),
                forcedAwards = config.getLong("$path.forced-awards", 0L).coerceAtLeast(0L),
                itemAmount = config.getLong("$path.item-amount", 0L).coerceAtLeast(0L),
                xpAmount = config.getLong("$path.xp-amount", 0L).coerceAtLeast(0L),
                commandRuns = config.getLong("$path.command-runs", 0L).coerceAtLeast(0L),
                lastAwardedAt = config.getLong("$path.last-awarded-at", 0L).coerceAtLeast(0L),
                lastPlayerName = config.getString("$path.last-player-name"),
                lastMob = config.getString("$path.last-mob")
            )
        }

        config.getConfigurationSection("players")?.getKeys(false)?.forEach { uuid ->
            val path = "players.$uuid"
            val playerName = config.getString("$path.name") ?: uuid
            playerStats[uuid] = MutablePlayerStats(
                playerUuid = uuid,
                playerName = playerName,
                awards = config.getLong("$path.awards", 0L).coerceAtLeast(0L),
                forcedAwards = config.getLong("$path.forced-awards", 0L).coerceAtLeast(0L),
                itemAmount = config.getLong("$path.item-amount", 0L).coerceAtLeast(0L),
                xpAmount = config.getLong("$path.xp-amount", 0L).coerceAtLeast(0L),
                commandRuns = config.getLong("$path.command-runs", 0L).coerceAtLeast(0L),
                lastAwardedAt = config.getLong("$path.last-awarded-at", 0L).coerceAtLeast(0L),
                lastDropId = config.getString("$path.last-drop-id"),
                lastMob = config.getString("$path.last-mob")
            )
        }

        lastStatsSaveAt = System.currentTimeMillis()
        plugin.logger.info("Loaded MobGift stats for ${dropStats.size} drop(s) and ${playerStats.size} player(s).")
        runCatching { saveStats(force = true) }
            .onFailure { warnings += "Could not normalize stats.yml: ${it.message}" }
    }

    @Synchronized
    private fun saveStatsSafely(force: Boolean = false) {
        if (!force && (!statsDirty || System.currentTimeMillis() - lastStatsSaveAt < STATS_SAVE_INTERVAL_MILLIS)) {
            return
        }

        runCatching { saveStats(force) }
            .onFailure { plugin.logger.warning("Could not save MobGift stats to stats.yml: ${it.message}") }
    }

    private fun saveStats(force: Boolean = false) {
        if (!force && !statsDirty) {
            return
        }
        if (dropStats.isEmpty() && playerStats.isEmpty()) {
            val emptyFile = statsFile()
            if (emptyFile.exists()) {
                emptyFile.delete()
            }
            statsDirty = false
            lastStatsSaveAt = System.currentTimeMillis()
            return
        }

        val file = statsFile()
        file.parentFile?.mkdirs()
        val config = YamlConfiguration()
        config.set("generated-at", System.currentTimeMillis())

        dropStats.values
            .sortedBy { it.dropId.lowercase(Locale.ROOT) }
            .forEach { stats ->
                val path = "drops.${encodeStorageKey(stats.dropId)}"
                config.set("$path.id", stats.dropId)
                config.set("$path.awards", stats.awards)
                config.set("$path.forced-awards", stats.forcedAwards)
                config.set("$path.item-amount", stats.itemAmount)
                config.set("$path.xp-amount", stats.xpAmount)
                config.set("$path.command-runs", stats.commandRuns)
                config.set("$path.last-awarded-at", stats.lastAwardedAt)
                config.set("$path.last-player-name", stats.lastPlayerName)
                config.set("$path.last-mob", stats.lastMob)
            }

        playerStats.values
            .sortedBy { it.playerName.lowercase(Locale.ROOT) }
            .forEach { stats ->
                val path = "players.${stats.playerUuid}"
                config.set("$path.name", stats.playerName)
                config.set("$path.awards", stats.awards)
                config.set("$path.forced-awards", stats.forcedAwards)
                config.set("$path.item-amount", stats.itemAmount)
                config.set("$path.xp-amount", stats.xpAmount)
                config.set("$path.command-runs", stats.commandRuns)
                config.set("$path.last-awarded-at", stats.lastAwardedAt)
                config.set("$path.last-drop-id", stats.lastDropId)
                config.set("$path.last-mob", stats.lastMob)
            }

        config.save(file)
        statsDirty = false
        lastStatsSaveAt = System.currentTimeMillis()
    }

    private fun statsFile(): File {
        return File(plugin.dataFolder, "stats.yml")
    }

    private fun writeRewardLogSafely(
        timestamp: Long,
        drop: DropDefinition,
        player: Player,
        sourceName: String,
        worldName: String,
        blockX: Int,
        blockY: Int,
        blockZ: Int,
        itemAmount: Int,
        xpAmount: Int,
        commandRuns: Long,
        forced: Boolean
    ) {
        if (!rewardLogEnabled) {
            return
        }

        runCatching {
            writeRewardLog(
                timestamp = timestamp,
                drop = drop,
                player = player,
                sourceName = sourceName,
                worldName = worldName,
                blockX = blockX,
                blockY = blockY,
                blockZ = blockZ,
                itemAmount = itemAmount,
                xpAmount = xpAmount,
                commandRuns = commandRuns,
                forced = forced
            )
        }.onSuccess {
            rewardLogFailureNotified = false
        }.onFailure {
            if (!rewardLogFailureNotified) {
                plugin.logger.warning("Could not append MobGift reward log to $rewardLogFileName: ${it.message}")
                rewardLogFailureNotified = true
            }
        }
    }

    private fun writeRewardLog(
        timestamp: Long,
        drop: DropDefinition,
        player: Player,
        sourceName: String,
        worldName: String,
        blockX: Int,
        blockY: Int,
        blockZ: Int,
        itemAmount: Int,
        xpAmount: Int,
        commandRuns: Long,
        forced: Boolean
    ) {
        val file = rewardLogFile()
        file.parentFile?.mkdirs()
        if (!file.exists() || file.length() == 0L) {
            file.appendText(
                "timestamp,player_uuid,player_name,drop_id,source,world,x,y,z,material,item_amount,xp_amount,commands,forced\n",
                Charsets.UTF_8
            )
        }

        val line = listOf(
            formatTimestamp(timestamp),
            player.uniqueId.toString(),
            player.name,
            drop.id,
            sourceName,
            worldName,
            blockX.toString(),
            blockY.toString(),
            blockZ.toString(),
            drop.material?.name ?: "",
            itemAmount.coerceAtLeast(0).toString(),
            xpAmount.coerceAtLeast(0).toString(),
            commandRuns.coerceAtLeast(0).toString(),
            forced.toString()
        ).joinToString(",") { csvValue(it) }
        file.appendText("$line\n", Charsets.UTF_8)
    }

    private fun rewardLogFile(): File {
        return File(plugin.dataFolder, rewardLogFileName)
    }

    private fun csvValue(value: String): String {
        return "\"${value.replace("\"", "\"\"")}\""
    }

    private fun statsKey(dropId: String): String {
        return dropId.lowercase(Locale.ROOT)
    }

    private fun encodeStorageKey(value: String): String {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    }

    private fun decodeStorageKey(value: String): String? {
        return runCatching {
            String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    private fun findPlayerStats(playerNameOrUuid: String): MutablePlayerStats? {
        playerStats[playerNameOrUuid]?.let { return it }
        return playerStats.values.firstOrNull { stats ->
            stats.playerName.equals(playerNameOrUuid, ignoreCase = true) ||
                stats.playerUuid.equals(playerNameOrUuid, ignoreCase = true)
        }
    }

    private fun MutableDropStats.toSnapshot(): DropStatsSnapshot {
        return DropStatsSnapshot(
            dropId = dropId,
            awards = awards,
            forcedAwards = forcedAwards,
            itemAmount = itemAmount,
            xpAmount = xpAmount,
            commandRuns = commandRuns,
            lastAwardedAt = lastAwardedAt,
            lastPlayerName = lastPlayerName,
            lastMob = lastMob
        )
    }

    private fun MutablePlayerStats.toSnapshot(): PlayerStatsSnapshot {
        return PlayerStatsSnapshot(
            playerUuid = playerUuid,
            playerName = playerName,
            awards = awards,
            forcedAwards = forcedAwards,
            itemAmount = itemAmount,
            xpAmount = xpAmount,
            commandRuns = commandRuns,
            lastAwardedAt = lastAwardedAt,
            lastDropId = lastDropId,
            lastMob = lastMob
        )
    }

    private fun formatTimestamp(millis: Long): String {
        return if (millis <= 0) {
            "never"
        } else {
            DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(millis))
        }
    }

    private fun findDrop(dropId: String): DropDefinition? {
        return drops.firstOrNull { it.id.equals(dropId, ignoreCase = true) }
    }

    private fun createItemStack(drop: DropDefinition, amount: Int, mobName: String, playerName: String?): ItemStack {
        val material = drop.material ?: return ItemStack(Material.AIR)
        val itemStack = ItemStack(material, amount)
        if (
            drop.displayName == null &&
            drop.lore.isEmpty() &&
            drop.customModelData == null &&
            drop.enchantments.isEmpty() &&
            drop.itemFlags.isEmpty() &&
            !drop.unbreakable
        ) {
            return itemStack
        }

        val meta = itemStack.itemMeta ?: return itemStack
        drop.displayName?.let {
            meta.setDisplayName(colorize(formatPlaceholders(it, playerName, mobName, drop, amount, 0)))
        }
        if (drop.lore.isNotEmpty()) {
            meta.setLore(drop.lore.map { colorize(formatPlaceholders(it, playerName, mobName, drop, amount, 0)) })
        }
        drop.customModelData?.let(meta::setCustomModelData)
        drop.enchantments.forEach { (enchantment, level) ->
            meta.addEnchant(enchantment, level, true)
        }
        if (drop.itemFlags.isNotEmpty()) {
            meta.addItemFlags(*drop.itemFlags.toTypedArray())
        }
        meta.isUnbreakable = drop.unbreakable
        itemStack.itemMeta = meta
        return itemStack
    }

    private fun sendDropMessage(event: EntityDeathEvent, drop: DropDefinition, amount: Int, xpAmount: Int) {
        val killer = event.entity.killer ?: return
        sendConfiguredMessage(killer, drop, event.entityType.name, amount, xpAmount)
    }

    private fun sendBroadcast(event: EntityDeathEvent, drop: DropDefinition, amount: Int, xpAmount: Int) {
        val killer = event.entity.killer ?: return
        sendConfiguredBroadcast(killer, drop, event.entityType.name, amount, xpAmount)
    }

    private fun runCommands(event: EntityDeathEvent, drop: DropDefinition, amount: Int, xpAmount: Int) {
        val killer = event.entity.killer ?: return
        runConfiguredCommands(killer, drop, event.entityType.name, amount, xpAmount)
    }

    private fun playEffects(event: EntityDeathEvent, drop: DropDefinition) {
        val killer = event.entity.killer ?: return
        playConfiguredEffects(killer, drop)
    }

    private fun sendConfiguredMessage(player: Player, drop: DropDefinition, mobName: String, amount: Int, xpAmount: Int) {
        val rawMessage = drop.message ?: return
        val message = formatPlaceholders(rawMessage, player.name, mobName, drop, amount, xpAmount)

        player.sendMessage(colorize(message))
    }

    private fun sendConfiguredBroadcast(player: Player, drop: DropDefinition, mobName: String, amount: Int, xpAmount: Int) {
        val rawMessage = drop.broadcast ?: return
        val message = formatPlaceholders(rawMessage, player.name, mobName, drop, amount, xpAmount)

        plugin.server.broadcastMessage(colorize(message))
    }

    private fun runConfiguredCommands(player: Player, drop: DropDefinition, mobName: String, amount: Int, xpAmount: Int) {
        drop.commands.forEach { rawCommand ->
            val command = formatPlaceholders(rawCommand, player.name, mobName, drop, amount, xpAmount)
                .removePrefix("/")
                .trim()
            if (command.isNotEmpty()) {
                plugin.server.dispatchCommand(plugin.server.consoleSender, command)
            }
        }
    }

    private fun playConfiguredEffects(player: Player, drop: DropDefinition) {
        drop.sound?.let {
            player.playSound(player.location, it.sound, it.volume, it.pitch)
        }

        drop.particle?.let {
            val location = player.location.add(0.0, 0.5, 0.0)
            player.world.spawnParticle(
                it.particle,
                location,
                it.count,
                it.offsetX,
                it.offsetY,
                it.offsetZ,
                it.extra
            )
        }
    }

    private fun formatPlaceholders(
        value: String,
        playerName: String?,
        mobName: String,
        drop: DropDefinition,
        amount: Int,
        xpAmount: Int
    ): String {
        return value
            .replace("{player}", playerName ?: "")
            .replace("{mob}", mobName)
            .replace("{drop}", drop.id)
            .replace("{material}", drop.material?.name ?: "NONE")
            .replace("{amount}", amount.toString())
            .replace("{xp}", xpAmount.toString())
    }

    private fun describeConfiguredRewards(drop: DropDefinition): String {
        val rewards = mutableListOf<String>()
        drop.material?.let {
            rewards += "${it.name} x${formatRange(drop.minAmount, drop.maxAmount)}"
        }
        if (drop.xpMaxAmount > 0) {
            rewards += "XP ${formatRange(drop.xpMinAmount, drop.xpMaxAmount)}"
        }
        if (drop.commands.isNotEmpty()) {
            rewards += "${drop.commands.size} command(s)"
        }

        return rewards.joinToString(", ").ifBlank { "none" }
    }

    private fun describeRewards(drop: DropDefinition, amount: Int, xpAmount: Int): String {
        val rewards = mutableListOf<String>()
        drop.material?.let {
            rewards += "${it.name} x$amount"
        }
        if (xpAmount > 0) {
            rewards += "XP $xpAmount"
        }
        if (drop.commands.isNotEmpty()) {
            rewards += "${drop.commands.size} command(s)"
        }

        return rewards.joinToString(", ").ifBlank { "no rewards" }
    }

    private fun formatRange(min: Int, max: Int): String {
        return if (min == max) min.toString() else "$min-$max"
    }

    private fun formatDuration(seconds: Long): String {
        return when {
            seconds <= 0 -> "none"
            seconds % 3600L == 0L -> "${seconds / 3600L}h"
            seconds % 60L == 0L -> "${seconds / 60L}m"
            else -> "${seconds}s"
        }
    }

    private fun formatChance(drop: DropDefinition): String {
        val baseChance = "%.2f%%".format(Locale.ROOT, drop.chance * 100)
        if (drop.lootingChancePerLevel <= 0.0 && drop.lootingAmountPerLevel <= 0) {
            return baseChance
        }

        val bonuses = mutableListOf<String>()
        if (drop.lootingChancePerLevel > 0.0) {
            bonuses += "+${"%.2f%%".format(Locale.ROOT, drop.lootingChancePerLevel * 100)}/Looting"
        }
        if (drop.lootingAmountPerLevel > 0) {
            bonuses += "+${drop.lootingAmountPerLevel} item/Looting"
        }
        return "$baseChance (${bonuses.joinToString(", ")})"
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
