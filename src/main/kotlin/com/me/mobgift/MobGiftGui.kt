package com.me.mobgift

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Registry
import org.bukkit.Sound
import org.bukkit.block.Biome
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MobGiftGui(
    private val plugin: MobGift,
    private val dropManager: DropManager
) : Listener {
    private enum class ChatInputType(
        val label: String,
        val prompt: String
    ) {
        MATERIAL("material", "Type a Bukkit material name, or 'none' to remove the item reward."),
        CHANCE("chance", "Type the drop chance. Examples: 0.25, 25%, 25."),
        AMOUNT("amount", "Type a fixed amount or range. Examples: 1, 1-3, 1..3."),
        XP("xp", "Type fixed XP or a range. Examples: 0, 3, 1-5. Use 'none' to clear."),
        COOLDOWN("cooldown", "Type cooldown seconds, or use suffixes like 30s, 5m, 1h. Use 'none' to clear."),
        MOBS("mobs", "Type mob names separated by commas. Use ALL for every mob."),
        WORLDS("worlds", "Type world names separated by commas. Use ALL for every world."),
        BIOMES("biomes", "Type biome names separated by commas. Use ALL for every biome."),
        PERMISSION("permission", "Type a permission node, or 'none' to clear it."),
        MESSAGE("message", "Type the player message, or 'none' to clear it."),
        BROADCAST("broadcast", "Type the broadcast message, or 'none' to clear it."),
        COMMANDS("commands", "Type console commands separated by semicolons, or 'none' to clear them."),
        REQUIRED_TOOLS("required tools", "Type material names separated by commas, or 'none' to clear them."),
        DISPLAY_NAME("display name", "Type the item display name, or 'none' to clear it."),
        LORE("lore", "Type lore lines separated by semicolons, or 'none' to clear them."),
        CUSTOM_MODEL_DATA("custom model data", "Type a positive whole number, or 'none' to clear it."),
        SOUND("sound", "Type a Bukkit sound name, Minecraft sound key, or 'none' to clear it."),
        SOUND_VOLUME("sound volume", "Type a number 0.0 or higher."),
        SOUND_PITCH("sound pitch", "Type a number between 0.5 and 2.0."),
        PARTICLE("particle", "Type a simple Bukkit particle name, or 'none' to clear it."),
        PARTICLE_COUNT("particle count", "Type a positive whole number."),
        LOOTING_CHANCE("looting chance bonus", "Type a chance per Looting level. Examples: 0.02, 2%, 2."),
        LOOTING_AMOUNT("looting amount bonus", "Type a whole number per Looting level, or 'none' for 0."),
        ENCHANTMENTS("enchantments", "Type enchantments separated by semicolons. Example: SHARPNESS:3;UNBREAKING:2. Use 'none' to clear."),
        ITEM_FLAGS("item flags", "Type item flags separated by commas. Example: HIDE_ENCHANTS,HIDE_ATTRIBUTES. Use 'none' to clear.")
    }

    private enum class GuiView {
        MAIN,
        DROPS,
        DROP_DETAIL,
        DELETE_CONFIRM,
        SETTINGS,
        WARNINGS,
        STATS
    }

    private enum class CreateStep(
        val prompt: String
    ) {
        ID("Type a new drop ID. Use letters, numbers, '_' or '-'."),
        MATERIAL("Type a Bukkit material name for the item reward."),
        CHANCE("Type the drop chance. Examples: 0.25, 25%, 25."),
        MOBS("Type mob names separated by commas. Use ALL for every mob.")
    }

    private data class PendingInput(
        val dropId: String,
        val page: Int,
        val type: ChatInputType
    )

    private data class CreateDraft(
        val id: String? = null,
        val material: Material? = null,
        val chance: Double? = null
    )

    private data class PendingCreate(
        val step: CreateStep,
        val draft: CreateDraft
    )

    private data class PendingDuplicate(
        val sourceDropId: String,
        val page: Int
    )

    private class MobGiftGuiHolder(
        val view: GuiView,
        val page: Int = 0,
        val dropId: String? = null
    ) : InventoryHolder {
        private lateinit var inventory: Inventory

        fun bind(inventory: Inventory): Inventory {
            this.inventory = inventory
            return inventory
        }

        override fun getInventory(): Inventory {
            return inventory
        }
    }

    private val pendingInputs = ConcurrentHashMap<UUID, PendingInput>()
    private val pendingCreates = ConcurrentHashMap<UUID, PendingCreate>()
    private val pendingDuplicates = ConcurrentHashMap<UUID, PendingDuplicate>()
    private val warningCache = ConcurrentHashMap<UUID, List<String>>()

    fun openMainMenu(player: Player) {
        val inventory = createInventory(MobGiftGuiHolder(GuiView.MAIN), 3, "&2MobGift")

        inventory.setItem(10, button(Material.CHEST, "&aDrops", listOf(
            "&7View loaded MobGift drops.",
            "&f${dropManager.drops.size}&7 drop(s) loaded."
        )))
        inventory.setItem(11, editButton(Material.EMERALD_BLOCK, "&aCreate Drop", listOf(
            "&7Starts a chat wizard for a new drop."
        )))
        inventory.setItem(12, button(Material.WRITABLE_BOOK, "&eValidate Config", listOf(
            "&7Reloads the config and prints warnings.",
            "&7Requires &fmobgift.validate&7."
        )))
        inventory.setItem(13, button(Material.LECTERN, "&bStats", listOf(
            "&7View top MobGift reward stats.",
            "&7Requires &fmobgift.stats&7."
        )))
        inventory.setItem(14, button(Material.CLOCK, "&bReload", listOf(
            "&7Reloads MobGift from config.yml.",
            "&7Requires &fmobgift.reload&7."
        )))
        inventory.setItem(15, button(Material.COMPARATOR, "&bSettings", listOf(
            "&7Edit global MobGift settings."
        )))
        inventory.setItem(16, button(Material.BARRIER, "&cClose"))

        player.openInventory(inventory)
    }

    private fun openDropList(player: Player, page: Int) {
        val drops = sortedDrops()
        val maxPage = maxPage(drops.size)
        val safePage = page.coerceIn(0, maxPage)
        val inventory = createInventory(MobGiftGuiHolder(GuiView.DROPS, safePage), 6, "&2MobGift Drops")
        val startIndex = safePage * DROPS_PER_PAGE

        drops.drop(startIndex)
            .take(DROPS_PER_PAGE)
            .forEachIndexed { index, drop ->
                inventory.setItem(index, dropItem(drop))
            }

        inventory.setItem(45, button(Material.ARROW, "&eBack", listOf("&7Return to the main menu.")))
        inventory.setItem(48, button(Material.SPECTRAL_ARROW, "&ePrevious Page", listOf("&7Page ${safePage + 1}/${maxPage + 1}")))
        inventory.setItem(49, button(Material.BOOK, "&fPage ${safePage + 1}/${maxPage + 1}", listOf(
            "&7Loaded drops: &f${drops.size}"
        )))
        inventory.setItem(50, button(Material.SPECTRAL_ARROW, "&eNext Page", listOf("&7Page ${safePage + 1}/${maxPage + 1}")))
        inventory.setItem(51, editButton(Material.EMERALD_BLOCK, "&aCreate Drop", listOf("&7Starts a chat wizard.")))
        inventory.setItem(53, button(Material.BARRIER, "&cClose"))

        player.openInventory(inventory)
    }

    private fun openDropDetail(player: Player, dropId: String, page: Int) {
        val drop = dropManager.drops.firstOrNull { it.id.equals(dropId, ignoreCase = true) }
        val inventory = createInventory(MobGiftGuiHolder(GuiView.DROP_DETAIL, page, dropId), 6, "&2MobGift Drop")

        if (drop == null) {
            inventory.setItem(22, button(Material.BARRIER, "&cDrop Not Loaded", listOf(
                "&7Drop ID: &f$dropId",
                "&7It may have been removed or failed validation."
            )))
        } else {
            inventory.setItem(0, chatEditButton(Material.NOTE_BLOCK, "&eEdit Sound", if (drop.sound == null) "none" else "configured"))
            inventory.setItem(1, chatEditButton(Material.JUKEBOX, "&eEdit Sound Volume", if (drop.sound == null) "none" else drop.sound.volume.toString()))
            inventory.setItem(2, chatEditButton(Material.GOAT_HORN, "&eEdit Sound Pitch", if (drop.sound == null) "none" else drop.sound.pitch.toString()))
            inventory.setItem(3, chatEditButton(Material.FIREWORK_STAR, "&eEdit Particle", drop.particle?.particle?.name ?: "none"))
            inventory.setItem(4, chatEditButton(Material.GLOWSTONE_DUST, "&eEdit Particle Count", drop.particle?.count?.toString() ?: "none"))
            inventory.setItem(5, chatEditButton(Material.LAPIS_LAZULI, "&eEdit Looting Chance", formatPercent(drop.lootingChancePerLevel)))
            inventory.setItem(6, chatEditButton(Material.ENCHANTED_BOOK, "&eEdit Looting Amount", drop.lootingAmountPerLevel.toString()))
            inventory.setItem(7, chatEditButton(Material.ENCHANTED_BOOK, "&eEdit Enchantments", if (drop.enchantments.isEmpty()) "none" else "${drop.enchantments.size} enchantment(s)"))
            inventory.setItem(8, chatEditButton(Material.WHITE_BANNER, "&eEdit Item Flags", if (drop.itemFlags.isEmpty()) "none" else "${drop.itemFlags.size} flag(s)"))
            inventory.setItem(10, rewardItem(drop))
            inventory.setItem(12, filterItem(drop))
            inventory.setItem(14, playerFilterItem(drop))
            inventory.setItem(16, effectItem(drop))
            inventory.setItem(9, chatEditButton(Material.REDSTONE, "&eEdit Chance", formatChance(drop)))
            inventory.setItem(11, chatEditButton(Material.CHEST, "&eEdit Amount", if (drop.material == null) "no item reward" else formatRange(drop.minAmount, drop.maxAmount)))
            inventory.setItem(13, chatEditButton(Material.EXPERIENCE_BOTTLE, "&eEdit XP", formatXp(drop)))
            inventory.setItem(15, chatEditButton(Material.OAK_SIGN, "&eEdit Permission", drop.permission ?: "none"))
            inventory.setItem(17, editButton(
                if (drop.enabled) Material.LIME_DYE else Material.GRAY_DYE,
                "&eToggle Drop Enabled",
                listOf("&7Current: &f${if (drop.enabled) "enabled" else "disabled"}")
            ))
            inventory.setItem(18, chatEditButton(Material.CLOCK, "&eEdit Cooldown", formatDuration(drop.cooldownSeconds)))
            inventory.setItem(28, messageItem(drop))
            inventory.setItem(30, commandItem(drop))
            inventory.setItem(32, configInfoItem(drop))
            inventory.setItem(27, chatEditButton(
                Material.ANVIL,
                "&eEdit Display Name",
                drop.displayName ?: "none"
            ))
            inventory.setItem(29, chatEditButton(
                Material.WRITABLE_BOOK,
                "&eEdit Lore",
                if (drop.lore.isEmpty()) "none" else "${drop.lore.size} line(s)"
            ))
            inventory.setItem(31, chatEditButton(
                Material.ITEM_FRAME,
                "&eEdit Custom Model Data",
                drop.customModelData?.toString() ?: "none"
            ))
            inventory.setItem(19, chatEditButton(Material.NAME_TAG, "&eEdit Material", drop.material?.name ?: "none"))
            inventory.setItem(20, chatEditButton(Material.ZOMBIE_HEAD, "&eEdit Mobs", shortList(drop.mobs, 3)))
            inventory.setItem(21, chatEditButton(Material.GRASS_BLOCK, "&eEdit Worlds", shortList(drop.worlds, 3)))
            inventory.setItem(22, chatEditButton(Material.OAK_SAPLING, "&eEdit Biomes", shortList(drop.biomes, 3)))
            inventory.setItem(23, chatEditButton(Material.PAPER, "&eEdit Message", if (drop.message == null) "none" else "configured"))
            inventory.setItem(24, chatEditButton(Material.BELL, "&eEdit Broadcast", if (drop.broadcast == null) "none" else "configured"))
            inventory.setItem(25, chatEditButton(Material.COMMAND_BLOCK, "&eEdit Commands", "${drop.commands.size} command(s)"))
            inventory.setItem(26, chatEditButton(
                Material.IRON_SWORD,
                "&eEdit Required Tools",
                if (drop.requiredTools.isEmpty()) "none" else "${drop.requiredTools.size} tool(s)"
            ))
            inventory.setItem(37, editButton(Material.REDSTONE, "&cChance -10%", listOf("&7Current: &f${formatChance(drop)}")))
            inventory.setItem(38, editButton(Material.REDSTONE_TORCH, "&cChance -1%", listOf("&7Current: &f${formatChance(drop)}")))
            inventory.setItem(39, editButton(Material.TORCH, "&aChance +1%", listOf("&7Current: &f${formatChance(drop)}")))
            inventory.setItem(40, editButton(Material.EMERALD, "&aChance +10%", listOf("&7Current: &f${formatChance(drop)}")))
            inventory.setItem(41, editButton(Material.HOPPER, "&cAmount -1", listOf("&7Current: &f${formatRange(drop.minAmount, drop.maxAmount)}")))
            inventory.setItem(42, editButton(Material.CHEST, "&aAmount +1", listOf("&7Current: &f${formatRange(drop.minAmount, drop.maxAmount)}")))
            inventory.setItem(43, editButton(Material.GLASS_BOTTLE, "&cXP -1", listOf("&7Current: &f${formatXp(drop)}")))
            inventory.setItem(44, editButton(Material.EXPERIENCE_BOTTLE, "&aXP +1", listOf("&7Current: &f${formatXp(drop)}")))
            inventory.setItem(34, editButton(
                if (drop.allowSpawnerMobs) Material.SPAWNER else Material.TRIAL_SPAWNER,
                "&eToggle Spawner Mobs",
                listOf("&7Current: &f${if (drop.allowSpawnerMobs) "allowed" else "blocked"}")
            ))
            inventory.setItem(33, editButton(
                if (drop.unbreakable) Material.NETHERITE_INGOT else Material.IRON_INGOT,
                "&eToggle Unbreakable",
                listOf("&7Current: &f${if (drop.unbreakable) "enabled" else "disabled"}")
            ))
            inventory.setItem(35, editButton(Material.TNT, "&cDelete Drop", listOf(
                "&7Opens a confirmation menu.",
                "&7Only drops under &fdrops.items&7 can be deleted."
            )))
            inventory.setItem(36, editButton(Material.WRITABLE_BOOK, "&bDuplicate Drop", listOf(
                "&7Copies this drop under a new ID.",
                "&7Only drops under &fdrops.items&7 can be duplicated."
            )))
        }

        inventory.setItem(45, button(Material.ARROW, "&eBack", listOf("&7Return to the drop list.")))
        inventory.setItem(49, button(Material.CHEST, "&aDrops", listOf("&7Return to the drop list.")))
        inventory.setItem(53, button(Material.BARRIER, "&cClose"))

        player.openInventory(inventory)
    }

    private fun openDeleteConfirm(player: Player, dropId: String, page: Int) {
        val inventory = createInventory(MobGiftGuiHolder(GuiView.DELETE_CONFIRM, page, dropId), 3, "&4Delete MobGift Drop")
        inventory.setItem(11, button(Material.LIME_WOOL, "&aCancel", listOf("&7Return to the drop detail page.")))
        inventory.setItem(13, button(Material.PAPER, "&f${dropId}", listOf(
            "&7This will remove the drop from config.yml.",
            "&7The action cannot be undone from the GUI."
        )))
        inventory.setItem(15, button(Material.RED_WOOL, "&cConfirm Delete", listOf(
            "&7Requires &fmobgift.gui.edit&7.",
            "&7Only works for drops under &fdrops.items&7."
        )))

        player.openInventory(inventory)
    }

    private fun openSettings(player: Player) {
        val inventory = createInventory(MobGiftGuiHolder(GuiView.SETTINGS), 3, "&2MobGift Settings")
        inventory.setItem(10, editButton(
            if (dropManager.replaceDefaultDrops) Material.LAVA_BUCKET else Material.WATER_BUCKET,
            "&eToggle Vanilla Drops",
            listOf(
                "&7Current: &f${if (dropManager.replaceDefaultDrops) "replace vanilla drops" else "keep vanilla drops"}",
                "&7Config: &fdrops.replace-default-drops"
            )
        ))
        inventory.setItem(12, editButton(
            if (dropManager.debug) Material.REDSTONE_TORCH else Material.LEVER,
            "&eToggle Debug",
            listOf(
                "&7Current: &f${if (dropManager.debug) "enabled" else "disabled"}",
                "&7Config: &fsettings.debug"
            )
        ))
        inventory.setItem(14, editButton(
            if (dropManager.allowSpawnerMobs) Material.SPAWNER else Material.TRIAL_SPAWNER,
            "&eToggle Global Spawner Mobs",
            listOf(
                "&7Current: &f${if (dropManager.allowSpawnerMobs) "allowed" else "blocked"}",
                "&7Config: &fsettings.allow-spawner-mobs"
            )
        ))
        inventory.setItem(16, editButton(
            if (dropManager.persistCooldowns) Material.ENDER_CHEST else Material.CHEST,
            "&eToggle Cooldown Persistence",
            listOf(
                "&7Current: &f${if (dropManager.persistCooldowns) "enabled" else "disabled"}",
                "&7Config: &fsettings.persist-cooldowns",
                "&7Stores active cooldowns in &fcooldowns.yml&7."
            )
        ))
        inventory.setItem(20, editButton(
            if (dropManager.rewardLogEnabled) Material.WRITABLE_BOOK else Material.BOOK,
            "&eToggle Reward Log",
            listOf(
                "&7Current: &f${if (dropManager.rewardLogEnabled) "enabled" else "disabled"}",
                "&7Config: &fsettings.reward-log.enabled",
                "&7Writes detailed CSV rows to &frewards.csv&7."
            )
        ))
        inventory.setItem(22, button(Material.ARROW, "&eBack", listOf("&7Return to the main menu.")))

        player.openInventory(inventory)
    }

    private fun openWarnings(player: Player, warnings: List<String>, page: Int) {
        warningCache[player.uniqueId] = warnings
        val maxPage = maxPage(warnings.size)
        val safePage = page.coerceIn(0, maxPage)
        val inventory = createInventory(MobGiftGuiHolder(GuiView.WARNINGS, safePage), 6, "&6MobGift Warnings")

        warnings.drop(safePage * WARNINGS_PER_PAGE)
            .take(WARNINGS_PER_PAGE)
            .forEachIndexed { index, warning ->
                inventory.setItem(index, button(Material.PAPER, "&eWarning ${safePage * WARNINGS_PER_PAGE + index + 1}", splitLore(warning)))
            }

        inventory.setItem(45, button(Material.ARROW, "&eBack", listOf("&7Return to the main menu.")))
        inventory.setItem(48, button(Material.SPECTRAL_ARROW, "&ePrevious Page", listOf("&7Page ${safePage + 1}/${maxPage + 1}")))
        inventory.setItem(49, button(Material.BOOK, "&fPage ${safePage + 1}/${maxPage + 1}", listOf("&7Warnings: &f${warnings.size}")))
        inventory.setItem(50, button(Material.SPECTRAL_ARROW, "&eNext Page", listOf("&7Page ${safePage + 1}/${maxPage + 1}")))
        inventory.setItem(53, button(Material.BARRIER, "&cClose"))

        player.openInventory(inventory)
    }

    private fun openStats(player: Player, page: Int) {
        if (!player.hasPermission("mobgift.stats")) {
            player.sendMessage("You do not have permission to view MobGift stats.")
            return
        }

        val stats = dropManager.topDropStats(STATS_PER_PAGE * 10)
        val maxPage = maxPage(stats.size)
        val safePage = page.coerceIn(0, maxPage)
        val inventory = createInventory(MobGiftGuiHolder(GuiView.STATS, safePage), 6, "&2MobGift Stats")

        if (stats.isEmpty()) {
            inventory.setItem(22, button(Material.PAPER, "&eNo Stats Yet", listOf(
                "&7Stats appear after MobGift awards drops."
            )))
        } else {
            stats.drop(safePage * STATS_PER_PAGE)
                .take(STATS_PER_PAGE)
                .forEachIndexed { index, stat ->
                    inventory.setItem(index, statsItem(stat, (safePage * STATS_PER_PAGE) + index + 1))
                }
        }

        inventory.setItem(45, button(Material.ARROW, "&eBack", listOf("&7Return to the main menu.")))
        inventory.setItem(48, button(Material.SPECTRAL_ARROW, "&ePrevious Page", listOf("&7Page ${safePage + 1}/${maxPage + 1}")))
        inventory.setItem(49, button(Material.BOOK, "&fPage ${safePage + 1}/${maxPage + 1}", listOf(
            "&7Tracked drops: &f${stats.size}",
            "&7Command: &f/mobgift stats"
        )))
        inventory.setItem(50, button(Material.SPECTRAL_ARROW, "&eNext Page", listOf("&7Page ${safePage + 1}/${maxPage + 1}")))
        inventory.setItem(51, button(Material.MAP, "&bExport Stats", listOf(
            "&7Writes a CSV snapshot file.",
            "&7Requires &fmobgift.stats.export&7."
        )))
        inventory.setItem(53, button(Material.BARRIER, "&cClose"))

        player.openInventory(inventory)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? MobGiftGuiHolder ?: return
        event.isCancelled = true

        if (event.clickedInventory != event.view.topInventory) {
            return
        }

        val player = event.whoClicked as? Player ?: return
        when (holder.view) {
            GuiView.MAIN -> handleMainClick(player, event.slot)
            GuiView.DROPS -> handleDropListClick(player, holder.page, event.slot)
            GuiView.DROP_DETAIL -> handleDropDetailClick(player, holder.page, holder.dropId, event.slot)
            GuiView.DELETE_CONFIRM -> handleDeleteConfirmClick(player, holder.page, holder.dropId, event.slot)
            GuiView.SETTINGS -> handleSettingsClick(player, event.slot)
            GuiView.WARNINGS -> handleWarningsClick(player, holder.page, event.slot)
            GuiView.STATS -> handleStatsClick(player, holder.page, event.slot)
        }
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder is MobGiftGuiHolder) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val pendingInput = pendingInputs.remove(event.player.uniqueId)
        val pendingCreate = if (pendingInput == null) pendingCreates.remove(event.player.uniqueId) else null
        val pendingDuplicate = if (pendingInput == null && pendingCreate == null) {
            pendingDuplicates.remove(event.player.uniqueId)
        } else {
            null
        }
        if (pendingInput == null && pendingCreate == null && pendingDuplicate == null) {
            return
        }

        event.isCancelled = true

        val message = event.message.trim()
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (pendingInput != null) {
                handleChatInput(event.player, pendingInput, message)
            } else if (pendingCreate != null) {
                handleCreateInput(event.player, pendingCreate, message)
            } else if (pendingDuplicate != null) {
                handleDuplicateInput(event.player, pendingDuplicate, message)
            }
        })
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        pendingInputs.remove(event.player.uniqueId)
        pendingCreates.remove(event.player.uniqueId)
        pendingDuplicates.remove(event.player.uniqueId)
        warningCache.remove(event.player.uniqueId)
    }

    private fun handleMainClick(player: Player, slot: Int) {
        when (slot) {
            10 -> openDropList(player, 0)
            11 -> startCreateDrop(player)
            12 -> validateConfig(player)
            13 -> openStats(player, 0)
            14 -> reloadConfig(player)
            15 -> openSettings(player)
            16 -> player.closeInventory()
        }
    }

    private fun handleDropListClick(player: Player, page: Int, slot: Int) {
        val drops = sortedDrops()
        val maxPage = maxPage(drops.size)

        when {
            slot in 0 until DROPS_PER_PAGE -> {
                val drop = drops.getOrNull((page * DROPS_PER_PAGE) + slot) ?: return
                openDropDetail(player, drop.id, page)
            }
            slot == 45 -> openMainMenu(player)
            slot == 48 -> openDropList(player, page - 1)
            slot == 50 -> openDropList(player, page + 1)
            slot == 51 -> startCreateDrop(player)
            slot == 53 -> player.closeInventory()
            slot == 49 -> player.sendMessage(colorize("&7MobGift drops: &f${drops.size}&7. Page &f${page + 1}/${maxPage + 1}&7."))
        }
    }

    private fun handleDropDetailClick(player: Player, page: Int, dropId: String?, slot: Int) {
        when (slot) {
            45, 49 -> openDropList(player, page)
            53 -> player.closeInventory()
            37 -> dropId?.let { adjustChance(player, it, page, -0.10) }
            38 -> dropId?.let { adjustChance(player, it, page, -0.01) }
            39 -> dropId?.let { adjustChance(player, it, page, 0.01) }
            40 -> dropId?.let { adjustChance(player, it, page, 0.10) }
            41 -> dropId?.let { adjustAmount(player, it, page, -1) }
            42 -> dropId?.let { adjustAmount(player, it, page, 1) }
            43 -> dropId?.let { adjustXp(player, it, page, -1) }
            44 -> dropId?.let { adjustXp(player, it, page, 1) }
            34 -> dropId?.let { toggleSpawnerMobs(player, it, page) }
            19 -> dropId?.let { startChatInput(player, it, page, ChatInputType.MATERIAL) }
            20 -> dropId?.let { startChatInput(player, it, page, ChatInputType.MOBS) }
            21 -> dropId?.let { startChatInput(player, it, page, ChatInputType.WORLDS) }
            22 -> dropId?.let { startChatInput(player, it, page, ChatInputType.BIOMES) }
            23 -> dropId?.let { startChatInput(player, it, page, ChatInputType.MESSAGE) }
            24 -> dropId?.let { startChatInput(player, it, page, ChatInputType.BROADCAST) }
            25 -> dropId?.let { startChatInput(player, it, page, ChatInputType.COMMANDS) }
            26 -> dropId?.let { startChatInput(player, it, page, ChatInputType.REQUIRED_TOOLS) }
            27 -> dropId?.let { startChatInput(player, it, page, ChatInputType.DISPLAY_NAME) }
            29 -> dropId?.let { startChatInput(player, it, page, ChatInputType.LORE) }
            31 -> dropId?.let { startChatInput(player, it, page, ChatInputType.CUSTOM_MODEL_DATA) }
            0 -> dropId?.let { startChatInput(player, it, page, ChatInputType.SOUND) }
            1 -> dropId?.let { startChatInput(player, it, page, ChatInputType.SOUND_VOLUME) }
            2 -> dropId?.let { startChatInput(player, it, page, ChatInputType.SOUND_PITCH) }
            3 -> dropId?.let { startChatInput(player, it, page, ChatInputType.PARTICLE) }
            4 -> dropId?.let { startChatInput(player, it, page, ChatInputType.PARTICLE_COUNT) }
            5 -> dropId?.let { startChatInput(player, it, page, ChatInputType.LOOTING_CHANCE) }
            6 -> dropId?.let { startChatInput(player, it, page, ChatInputType.LOOTING_AMOUNT) }
            7 -> dropId?.let { startChatInput(player, it, page, ChatInputType.ENCHANTMENTS) }
            8 -> dropId?.let { startChatInput(player, it, page, ChatInputType.ITEM_FLAGS) }
            9 -> dropId?.let { startChatInput(player, it, page, ChatInputType.CHANCE) }
            11 -> dropId?.let { startChatInput(player, it, page, ChatInputType.AMOUNT) }
            13 -> dropId?.let { startChatInput(player, it, page, ChatInputType.XP) }
            15 -> dropId?.let { startChatInput(player, it, page, ChatInputType.PERMISSION) }
            17 -> dropId?.let { toggleDropEnabled(player, it, page) }
            18 -> dropId?.let { startChatInput(player, it, page, ChatInputType.COOLDOWN) }
            33 -> dropId?.let { toggleUnbreakable(player, it, page) }
            35 -> dropId?.let { openDeleteConfirm(player, it, page) }
            36 -> dropId?.let { startDuplicateDrop(player, it, page) }
        }
    }

    private fun handleDeleteConfirmClick(player: Player, page: Int, dropId: String?, slot: Int) {
        when (slot) {
            11 -> dropId?.let { openDropDetail(player, it, page) }
            15 -> dropId?.let { deleteDrop(player, it, page) }
        }
    }

    private fun handleSettingsClick(player: Player, slot: Int) {
        when (slot) {
            10 -> toggleSetting(player, "drops.replace-default-drops", !dropManager.replaceDefaultDrops)
            12 -> toggleSetting(player, "settings.debug", !dropManager.debug)
            14 -> toggleSetting(player, "settings.allow-spawner-mobs", !dropManager.allowSpawnerMobs)
            16 -> toggleSetting(player, "settings.persist-cooldowns", !dropManager.persistCooldowns)
            20 -> toggleSetting(player, "settings.reward-log.enabled", !dropManager.rewardLogEnabled)
            22 -> openMainMenu(player)
        }
    }

    private fun handleWarningsClick(player: Player, page: Int, slot: Int) {
        val warnings = warningCache[player.uniqueId] ?: emptyList()
        when (slot) {
            45 -> openMainMenu(player)
            48 -> openWarnings(player, warnings, page - 1)
            50 -> openWarnings(player, warnings, page + 1)
            53 -> player.closeInventory()
        }
    }

    private fun handleStatsClick(player: Player, page: Int, slot: Int) {
        when (slot) {
            45 -> openMainMenu(player)
            48 -> openStats(player, page - 1)
            50 -> openStats(player, page + 1)
            51 -> exportStats(player, page)
            53 -> player.closeInventory()
        }
    }

    private fun exportStats(player: Player, page: Int) {
        if (!player.hasPermission("mobgift.stats.export")) {
            player.sendMessage("You do not have permission to export MobGift stats.")
            return
        }

        runCatching { dropManager.exportStats() }
            .onSuccess { file ->
                player.sendMessage(colorize("&aExported MobGift stats to &f${file.name}&a."))
            }
            .onFailure {
                player.sendMessage(colorize("&cCould not export MobGift stats: &f${it.message}"))
            }
        openStats(player, page)
    }

    private fun validateConfig(player: Player) {
        if (!player.hasPermission("mobgift.validate")) {
            player.sendMessage("You do not have permission to use this action.")
            return
        }

        plugin.reloadConfig()
        val result = dropManager.reload()
        if (result.warnings.isEmpty()) {
            player.sendMessage(colorize("&aMobGift config is valid. &7Loaded &f${result.loadedDrops}&7 drop(s)."))
            openMainMenu(player)
            return
        }

        player.sendMessage(colorize("&eMobGift config loaded &f${result.loadedDrops}&e drop(s) with &f${result.warnings.size}&e warning(s):"))
        result.warnings.take(8).forEach { player.sendMessage(colorize("&7- &f$it")) }
        if (result.warnings.size > 8) {
            player.sendMessage(colorize("&7...and &f${result.warnings.size - 8}&7 more warning(s). Check the console."))
        }
        openWarnings(player, result.warnings, 0)
    }

    private fun reloadConfig(player: Player) {
        if (!player.hasPermission("mobgift.reload")) {
            player.sendMessage("You do not have permission to use this action.")
            return
        }

        plugin.reloadConfig()
        val result = dropManager.reload()
        player.sendMessage(colorize("&aMobGift reloaded. &7Loaded &f${result.loadedDrops}&7 drop(s)."))
        if (result.warnings.isNotEmpty()) {
            player.sendMessage(colorize("&eFound &f${result.warnings.size}&e config warning(s). Check the console or use validate."))
        }
        openMainMenu(player)
    }

    private fun startCreateDrop(player: Player) {
        if (!player.hasPermission("mobgift.gui.edit")) {
            player.sendMessage("You do not have permission to create drops from the GUI.")
            return
        }

        pendingInputs.remove(player.uniqueId)
        pendingDuplicates.remove(player.uniqueId)
        pendingCreates[player.uniqueId] = PendingCreate(CreateStep.ID, CreateDraft())
        player.closeInventory()
        player.sendMessage(colorize("&aCreating a new MobGift drop."))
        player.sendMessage(colorize("&e${CreateStep.ID.prompt}"))
        player.sendMessage(colorize("&7Type &fcancel&7 to cancel."))
    }

    private fun handleCreateInput(player: Player, pendingCreate: PendingCreate, message: String) {
        if (message.equals("cancel", ignoreCase = true)) {
            player.sendMessage(colorize("&7MobGift drop creation cancelled."))
            openMainMenu(player)
            return
        }

        when (pendingCreate.step) {
            CreateStep.ID -> handleCreateId(player, message)
            CreateStep.MATERIAL -> handleCreateMaterial(player, pendingCreate.draft, message)
            CreateStep.CHANCE -> handleCreateChance(player, pendingCreate.draft, message)
            CreateStep.MOBS -> handleCreateMobs(player, pendingCreate.draft, message)
        }
    }

    private fun handleCreateId(player: Player, input: String) {
        val dropId = input.trim()
        if (!isValidDropId(dropId)) {
            player.sendMessage(colorize("&cInvalid drop ID. Use only letters, numbers, '_' or '-'."))
            queueCreateStep(player, CreateStep.ID, CreateDraft())
            return
        }

        if (dropIdExists(dropId)) {
            player.sendMessage(colorize("&cA drop with that ID already exists."))
            queueCreateStep(player, CreateStep.ID, CreateDraft())
            return
        }

        queueCreateStep(player, CreateStep.MATERIAL, CreateDraft(id = dropId))
    }

    private fun handleCreateMaterial(player: Player, draft: CreateDraft, input: String) {
        val material = Material.matchMaterial(input)
        if (material == null || material == Material.AIR) {
            player.sendMessage(colorize("&cUnknown material: &f$input&c."))
            queueCreateStep(player, CreateStep.MATERIAL, draft)
            return
        }

        queueCreateStep(player, CreateStep.CHANCE, draft.copy(material = material))
    }

    private fun handleCreateChance(player: Player, draft: CreateDraft, input: String) {
        val chance = parseChanceInput(input)
        if (chance == null) {
            player.sendMessage(colorize("&cInvalid chance. Use examples like &f0.25&c, &f25%&c, or &f25&c."))
            queueCreateStep(player, CreateStep.CHANCE, draft)
            return
        }

        queueCreateStep(player, CreateStep.MOBS, draft.copy(chance = chance))
    }

    private fun handleCreateMobs(player: Player, draft: CreateDraft, input: String) {
        val dropId = draft.id
        val material = draft.material
        val chance = draft.chance
        if (dropId == null || material == null || chance == null) {
            player.sendMessage(colorize("&cCreate wizard state was incomplete. Start again."))
            openMainMenu(player)
            return
        }

        val mobs = parseMobListForCreate(player, input) ?: run {
            queueCreateStep(player, CreateStep.MOBS, draft)
            return
        }

        val path = "drops.items.$dropId"
        plugin.config.set("$path.material", material.name)
        plugin.config.set("$path.amount", 1)
        plugin.config.set("$path.chance", chance)
        plugin.config.set("$path.mobs", mobs)
        plugin.saveConfig()
        plugin.reloadConfig()
        val result = dropManager.reload()

        player.sendMessage(colorize("&aCreated drop &f$dropId&a."))
        if (result.warnings.isNotEmpty()) {
            player.sendMessage(colorize("&eConfig reloaded with &f${result.warnings.size}&e warning(s). Use validate for details."))
        }
        openDropDetail(player, dropId, maxPage(dropManager.drops.size))
    }

    private fun queueCreateStep(player: Player, step: CreateStep, draft: CreateDraft) {
        pendingCreates[player.uniqueId] = PendingCreate(step, draft)
        player.sendMessage(colorize("&e${step.prompt}"))
        player.sendMessage(colorize("&7Type &fcancel&7 to cancel."))
    }

    private fun startDuplicateDrop(player: Player, dropId: String, page: Int) {
        if (!player.hasPermission("mobgift.gui.edit")) {
            player.sendMessage("You do not have permission to duplicate drops from the GUI.")
            return
        }

        val drop = dropManager.drops.firstOrNull { it.id.equals(dropId, ignoreCase = true) }
        if (drop == null) {
            player.sendMessage(colorize("&cDrop '$dropId' is not loaded."))
            openDropDetail(player, dropId, page)
            return
        }

        if (editableDropPath(drop.id) == null) {
            player.sendMessage(colorize("&eThis drop is read-only because it is not under drops.items in config.yml."))
            openDropDetail(player, drop.id, page)
            return
        }

        pendingInputs.remove(player.uniqueId)
        pendingCreates.remove(player.uniqueId)
        pendingDuplicates[player.uniqueId] = PendingDuplicate(drop.id, page)
        player.closeInventory()
        player.sendMessage(colorize("&aDuplicating MobGift drop &f${drop.id}&a."))
        player.sendMessage(colorize("&eType the new drop ID. Use letters, numbers, '_' or '-'."))
        player.sendMessage(colorize("&7Type &fcancel&7 to cancel."))
    }

    private fun handleDuplicateInput(player: Player, pendingDuplicate: PendingDuplicate, message: String) {
        if (message.equals("cancel", ignoreCase = true)) {
            player.sendMessage(colorize("&7MobGift drop duplication cancelled."))
            openDropDetail(player, pendingDuplicate.sourceDropId, pendingDuplicate.page)
            return
        }

        val newDropId = message.trim()
        if (!isValidDropId(newDropId)) {
            player.sendMessage(colorize("&cInvalid drop ID. Use only letters, numbers, '_' or '-'."))
            pendingDuplicates[player.uniqueId] = pendingDuplicate
            player.sendMessage(colorize("&eType the new drop ID."))
            player.sendMessage(colorize("&7Type &fcancel&7 to cancel."))
            return
        }

        if (dropIdExists(newDropId)) {
            player.sendMessage(colorize("&cA drop with that ID already exists."))
            pendingDuplicates[player.uniqueId] = pendingDuplicate
            player.sendMessage(colorize("&eType a different drop ID."))
            player.sendMessage(colorize("&7Type &fcancel&7 to cancel."))
            return
        }

        val sourcePath = editableDropPath(pendingDuplicate.sourceDropId)
        val sourceSection = sourcePath?.let { plugin.config.getConfigurationSection(it) }
        if (sourcePath == null || sourceSection == null) {
            player.sendMessage(colorize("&eThe source drop is read-only or no longer exists in drops.items."))
            openDropDetail(player, pendingDuplicate.sourceDropId, pendingDuplicate.page)
            return
        }

        val targetPath = "drops.items.$newDropId"
        plugin.config.set(targetPath, null)
        copySection(sourceSection, plugin.config.createSection(targetPath))
        plugin.saveConfig()
        plugin.reloadConfig()
        val result = dropManager.reload()

        player.sendMessage(colorize("&aDuplicated &f${pendingDuplicate.sourceDropId}&a to &f$newDropId&a."))
        if (result.warnings.isNotEmpty()) {
            player.sendMessage(colorize("&eConfig reloaded with &f${result.warnings.size}&e warning(s). Use validate for details."))
        }
        openDropDetail(player, newDropId, pageForDrop(newDropId))
    }

    private fun startChatInput(player: Player, dropId: String, page: Int, type: ChatInputType) {
        if (!player.hasPermission("mobgift.gui.edit")) {
            player.sendMessage("You do not have permission to edit drops from the GUI.")
            return
        }

        val drop = dropManager.drops.firstOrNull { it.id.equals(dropId, ignoreCase = true) }
        if (drop == null) {
            player.sendMessage(colorize("&cDrop '$dropId' is not loaded."))
            openDropDetail(player, dropId, page)
            return
        }

        if (editableDropPath(drop.id) == null) {
            player.sendMessage(colorize("&eThis drop is read-only because it is not under drops.items in config.yml."))
            openDropDetail(player, drop.id, page)
            return
        }

        pendingCreates.remove(player.uniqueId)
        pendingDuplicates.remove(player.uniqueId)
        pendingInputs[player.uniqueId] = PendingInput(drop.id, page, type)
        player.closeInventory()
        player.sendMessage(colorize("&aEditing &f${drop.id}&a ${type.label}."))
        player.sendMessage(colorize("&7Current: &f${chatInputCurrentValue(drop, type)}"))
        player.sendMessage(colorize("&e${type.prompt}"))
        player.sendMessage(colorize("&7Type &fcancel&7 to cancel."))
    }

    private fun handleChatInput(player: Player, pendingInput: PendingInput, message: String) {
        if (message.equals("cancel", ignoreCase = true)) {
            player.sendMessage(colorize("&7MobGift GUI edit cancelled."))
            openDropDetail(player, pendingInput.dropId, pendingInput.page)
            return
        }

        editDrop(player, pendingInput.dropId, pendingInput.page) { path, drop ->
            when (pendingInput.type) {
                ChatInputType.MATERIAL -> applyMaterialInput(player, path, drop, message)
                ChatInputType.CHANCE -> applyChanceInput(player, path, drop, message)
                ChatInputType.AMOUNT -> applyAmountInput(player, path, drop, message)
                ChatInputType.XP -> applyXpInput(player, path, drop, message)
                ChatInputType.COOLDOWN -> applyCooldownInput(player, path, drop, message)
                ChatInputType.MOBS -> applyMobListInput(player, path, drop, message)
                ChatInputType.WORLDS -> applyWorldListInput(player, path, drop, message)
                ChatInputType.BIOMES -> applyBiomeListInput(player, path, drop, message)
                ChatInputType.PERMISSION -> applyPermissionInput(player, path, drop, message)
                ChatInputType.MESSAGE -> applyMessageInput(path, drop, "message", message)
                ChatInputType.BROADCAST -> applyMessageInput(path, drop, "broadcast", message)
                ChatInputType.COMMANDS -> applyCommandsInput(player, path, drop, message)
                ChatInputType.REQUIRED_TOOLS -> applyRequiredToolsInput(player, path, drop, message)
                ChatInputType.DISPLAY_NAME -> applyMessageInput(path, drop, "display-name", message)
                ChatInputType.LORE -> applyLoreInput(path, drop, message)
                ChatInputType.CUSTOM_MODEL_DATA -> applyCustomModelDataInput(player, path, drop, message)
                ChatInputType.SOUND -> applySoundInput(player, path, drop, message)
                ChatInputType.SOUND_VOLUME -> applySoundVolumeInput(player, path, drop, message)
                ChatInputType.SOUND_PITCH -> applySoundPitchInput(player, path, drop, message)
                ChatInputType.PARTICLE -> applyParticleInput(player, path, drop, message)
                ChatInputType.PARTICLE_COUNT -> applyParticleCountInput(player, path, drop, message)
                ChatInputType.LOOTING_CHANCE -> applyLootingChanceInput(player, path, drop, message)
                ChatInputType.LOOTING_AMOUNT -> applyLootingAmountInput(player, path, drop, message)
                ChatInputType.ENCHANTMENTS -> applyEnchantmentsInput(player, path, drop, message)
                ChatInputType.ITEM_FLAGS -> applyItemFlagsInput(player, path, drop, message)
            }
        }
    }

    private fun applyMaterialInput(player: Player, path: String, drop: DropDefinition, input: String): String? {
        if (isClearInput(input)) {
            if (drop.xpMaxAmount <= 0 && drop.commands.isEmpty()) {
                player.sendMessage(colorize("&eThis drop needs at least one reward. Material cannot be removed."))
                return null
            }

            plugin.config.set("$path.material", null)
            return "Removed item reward from ${drop.id}."
        }

        val material = Material.matchMaterial(input)
        if (material == null || material == Material.AIR) {
            player.sendMessage(colorize("&cUnknown material: &f$input&c."))
            return null
        }

        plugin.config.set("$path.material", material.name)
        if (!plugin.config.contains("$path.amount")) {
            plugin.config.set("$path.amount", 1)
        }
        return "Updated ${drop.id} material to ${material.name}."
    }

    private fun applyChanceInput(player: Player, path: String, drop: DropDefinition, input: String): String? {
        val chance = parseChanceInput(input)
        if (chance == null) {
            player.sendMessage(colorize("&cInvalid chance. Use examples like &f0.25&c, &f25%&c, or &f25&c."))
            return null
        }

        plugin.config.set("$path.chance", chance)
        return "Updated ${drop.id} chance to ${formatPercent(chance)}."
    }

    private fun applyAmountInput(player: Player, path: String, drop: DropDefinition, input: String): String? {
        if (drop.material == null) {
            player.sendMessage(colorize("&eThis drop has no item reward, so amount cannot be edited."))
            return null
        }

        val range = parseRangeInput(input, 1)
        if (range == null) {
            player.sendMessage(colorize("&cInvalid amount. Use examples like &f1&c, &f1-3&c, or &f1..3&c."))
            return null
        }

        writeRequiredRange(path, "amount", range.first, range.second)
        return "Updated ${drop.id} amount to ${formatRange(range.first, range.second)}."
    }

    private fun applyXpInput(player: Player, path: String, drop: DropDefinition, input: String): String? {
        if (isClearInput(input)) {
            if (drop.material == null && drop.commands.isEmpty()) {
                player.sendMessage(colorize("&eThis drop needs at least one reward. XP cannot be removed."))
                return null
            }

            plugin.config.set("$path.xp", null)
            return "Cleared ${drop.id} XP."
        }

        val range = parseRangeInput(input, 0)
        if (range == null) {
            player.sendMessage(colorize("&cInvalid XP. Use examples like &f0&c, &f3&c, or &f1-5&c."))
            return null
        }
        if (range.second <= 0 && drop.material == null && drop.commands.isEmpty()) {
            player.sendMessage(colorize("&eThis drop needs at least one reward. XP cannot be removed."))
            return null
        }

        writeOptionalRange(path, "xp", range.first, range.second)
        return "Updated ${drop.id} XP to ${if (range.second <= 0) "none" else formatRange(range.first, range.second)}."
    }

    private fun applyCooldownInput(player: Player, path: String, drop: DropDefinition, input: String): String? {
        val cooldownSeconds = if (isClearInput(input)) 0L else parseDurationSecondsInput(input)
        if (cooldownSeconds == null) {
            player.sendMessage(colorize("&cInvalid cooldown. Use examples like &f30&c, &f30s&c, &f5m&c, or &f1h&c."))
            return null
        }

        if (cooldownSeconds <= 0) {
            plugin.config.set("$path.cooldown-seconds", null)
            plugin.config.set("$path.cooldown", null)
        } else {
            plugin.config.set("$path.cooldown", null)
            plugin.config.set("$path.cooldown-seconds", cooldownSeconds)
        }
        return "Updated ${drop.id} cooldown to ${formatDuration(cooldownSeconds)}."
    }

    private fun applyMobListInput(player: Player, path: String, drop: DropDefinition, input: String): String? {
        val values = parseCommaList(input)
        if (values.isEmpty() || isClearInput(input)) {
            plugin.config.set("$path.mobs", null)
            return "Updated ${drop.id} mobs to ALL."
        }

        val mobs = mutableListOf<String>()
        values.forEach { value ->
            val normalized = normalizeName(value)
            if (normalized == "ALL") {
                plugin.config.set("$path.mobs", listOf("ALL"))
                return "Updated ${drop.id} mobs to ALL."
            }

            if (runCatching { EntityType.valueOf(normalized) }.isFailure) {
                player.sendMessage(colorize("&cUnknown mob type: &f$value&c."))
                return null
            }
            mobs += normalized
        }

        plugin.config.set("$path.mobs", mobs.distinct())
        return "Updated ${drop.id} mobs."
    }

    private fun applyWorldListInput(player: Player, path: String, drop: DropDefinition, input: String): String? {
        val values = parseCommaList(input)
        if (values.isEmpty() || isClearInput(input)) {
            plugin.config.set("$path.worlds", null)
            return "Updated ${drop.id} worlds to ALL."
        }

        val worlds = if (values.any { it.equals("ALL", ignoreCase = true) }) {
            listOf("ALL")
        } else {
            values.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        }

        worlds
            .filterNot { it.equals("ALL", ignoreCase = true) }
            .filter { plugin.server.getWorld(it) == null }
            .forEach { player.sendMessage(colorize("&eWorld '$it' is not currently loaded; saving it anyway.")) }

        plugin.config.set("$path.worlds", worlds)
        return "Updated ${drop.id} worlds."
    }

    private fun applyBiomeListInput(player: Player, path: String, drop: DropDefinition, input: String): String? {
        val values = parseCommaList(input)
        if (values.isEmpty() || isClearInput(input)) {
            plugin.config.set("$path.biomes", null)
            return "Updated ${drop.id} biomes to ALL."
        }

        val biomes = mutableListOf<String>()
        values.forEach { value ->
            val normalized = normalizeName(value)
            if (normalized == "ALL") {
                plugin.config.set("$path.biomes", listOf("ALL"))
                return "Updated ${drop.id} biomes to ALL."
            }

            if (runCatching { Biome.valueOf(normalized) }.isFailure) {
                player.sendMessage(colorize("&cUnknown biome: &f$value&c."))
                return null
            }
            biomes += normalized
        }

        plugin.config.set("$path.biomes", biomes.distinct())
        return "Updated ${drop.id} biomes."
    }

    private fun applyPermissionInput(player: Player, path: String, drop: DropDefinition, input: String): String? {
        if (isClearInput(input)) {
            plugin.config.set("$path.permission", null)
            return "Cleared ${drop.id} permission."
        }

        val permission = input.trim()
        if (permission.isEmpty() || permission.any(Char::isWhitespace)) {
            player.sendMessage(colorize("&cPermission must be one node without spaces."))
            return null
        }

        plugin.config.set("$path.permission", permission)
        return "Updated ${drop.id} permission to $permission."
    }

    private fun applyMessageInput(path: String, drop: DropDefinition, field: String, input: String): String {
        return if (isClearInput(input)) {
            plugin.config.set("$path.$field", null)
            "Cleared ${drop.id} $field."
        } else {
            plugin.config.set("$path.$field", input)
            "Updated ${drop.id} $field."
        }
    }

    private fun applyLoreInput(path: String, drop: DropDefinition, input: String): String {
        return if (isClearInput(input)) {
            plugin.config.set("$path.lore", null)
            "Cleared ${drop.id} lore."
        } else {
            val lore = input.split(";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            plugin.config.set("$path.lore", lore)
            "Updated ${drop.id} lore."
        }
    }

    private fun applyCustomModelDataInput(player: Player, path: String, drop: DropDefinition, input: String): String? {
        if (isClearInput(input)) {
            plugin.config.set("$path.custom-model-data", null)
            return "Cleared ${drop.id} custom model data."
        }

        val customModelData = input.trim().toIntOrNull()
        if (customModelData == null || customModelData <= 0) {
            player.sendMessage(colorize("&cCustom model data must be a positive whole number."))
            return null
        }

        plugin.config.set("$path.custom-model-data", customModelData)
        return "Updated ${drop.id} custom model data to $customModelData."
    }

    private fun applySoundInput(player: Player, path: String, drop: DropDefinition, input: String): String? {
        if (isClearInput(input)) {
            plugin.config.set("$path.sound", null)
            return "Cleared ${drop.id} sound."
        }

        val sound = parseSound(input)
        if (sound == null) {
            player.sendMessage(colorize("&cUnknown sound: &f$input&c."))
            return null
        }

        plugin.config.set("$path.sound", null)
        plugin.config.set("$path.sound.name", sound.key.key())
        plugin.config.set("$path.sound.volume", drop.sound?.volume ?: 1.0f)
        plugin.config.set("$path.sound.pitch", drop.sound?.pitch ?: 1.0f)
        return "Updated ${drop.id} sound to ${sound.key.key()}."
    }

    private fun applySoundVolumeInput(player: Player, path: String, drop: DropDefinition, input: String): String? {
        if (drop.sound == null && !plugin.config.contains("$path.sound.name")) {
            player.sendMessage(colorize("&eSet a sound before editing volume."))
            return null
        }

        val volume = input.trim().toFloatOrNull()
        if (volume == null || volume < 0.0f) {
            player.sendMessage(colorize("&cSound volume must be 0.0 or higher."))
            return null
        }

        plugin.config.set("$path.sound.volume", volume)
        return "Updated ${drop.id} sound volume to $volume."
    }

    private fun applySoundPitchInput(player: Player, path: String, drop: DropDefinition, input: String): String? {
        if (drop.sound == null && !plugin.config.contains("$path.sound.name")) {
            player.sendMessage(colorize("&eSet a sound before editing pitch."))
            return null
        }

        val pitch = input.trim().toFloatOrNull()
        if (pitch == null || pitch !in 0.5f..2.0f) {
            player.sendMessage(colorize("&cSound pitch must be between 0.5 and 2.0."))
            return null
        }

        plugin.config.set("$path.sound.pitch", pitch)
        return "Updated ${drop.id} sound pitch to $pitch."
    }

    private fun applyParticleInput(player: Player, path: String, drop: DropDefinition, input: String): String? {
        if (isClearInput(input)) {
            plugin.config.set("$path.particle", null)
            return "Cleared ${drop.id} particle."
        }

        val particle = runCatching { Particle.valueOf(normalizeName(input)) }.getOrNull()
        if (particle == null) {
            player.sendMessage(colorize("&cUnknown particle: &f$input&c."))
            return null
        }

        if (particle.dataType != Void::class.java) {
            player.sendMessage(colorize("&cParticle &f${particle.name}&c needs extra data. Use a simple particle."))
            return null
        }

        plugin.config.set("$path.particle", null)
        plugin.config.set("$path.particle.name", particle.name)
        plugin.config.set("$path.particle.count", drop.particle?.count ?: 12)
        return "Updated ${drop.id} particle to ${particle.name}."
    }

    private fun applyParticleCountInput(player: Player, path: String, drop: DropDefinition, input: String): String? {
        if (drop.particle == null && !plugin.config.contains("$path.particle.name")) {
            player.sendMessage(colorize("&eSet a particle before editing count."))
            return null
        }

        val count = input.trim().toIntOrNull()
        if (count == null || count <= 0) {
            player.sendMessage(colorize("&cParticle count must be a positive whole number."))
            return null
        }

        plugin.config.set("$path.particle.count", count)
        return "Updated ${drop.id} particle count to $count."
    }

    private fun applyLootingChanceInput(player: Player, path: String, drop: DropDefinition, input: String): String? {
        val value = if (isClearInput(input)) 0.0 else parseChanceInput(input)
        if (value == null) {
            player.sendMessage(colorize("&cInvalid Looting chance. Use examples like &f0.02&c, &f2%&c, or &f2&c."))
            return null
        }

        plugin.config.set("$path.looting-bonus.chance-per-level", value)
        return "Updated ${drop.id} Looting chance bonus to ${formatPercent(value)}."
    }

    private fun applyLootingAmountInput(player: Player, path: String, drop: DropDefinition, input: String): String? {
        val value = if (isClearInput(input)) 0 else input.trim().toIntOrNull()
        if (value == null || value < 0) {
            player.sendMessage(colorize("&cLooting amount bonus must be a whole number 0 or higher."))
            return null
        }

        plugin.config.set("$path.looting-bonus.amount-per-level", value)
        return "Updated ${drop.id} Looting amount bonus to $value."
    }

    private fun applyEnchantmentsInput(player: Player, path: String, drop: DropDefinition, input: String): String? {
        if (isClearInput(input)) {
            plugin.config.set("$path.enchantments", null)
            return "Cleared ${drop.id} enchantments."
        }

        val entries = input.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (entries.isEmpty()) {
            player.sendMessage(colorize("&cNo enchantments were provided."))
            return null
        }

        val enchantments = linkedMapOf<String, Int>()
        entries.forEach { entry ->
            val parts = entry.split(":", "=", limit = 2).map { it.trim() }
            val enchantmentName = parts.firstOrNull().orEmpty()
            val enchantment = parseEnchantment(enchantmentName)
            if (enchantment == null) {
                player.sendMessage(colorize("&cUnknown enchantment: &f$enchantmentName&c."))
                return null
            }

            val level = parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            enchantments[enchantment.key.value()] = level
        }

        plugin.config.set("$path.enchantments", null)
        enchantments.forEach { (enchantmentName, level) ->
            plugin.config.set("$path.enchantments.$enchantmentName", level)
        }

        return "Updated ${drop.id} enchantments."
    }

    private fun applyItemFlagsInput(player: Player, path: String, drop: DropDefinition, input: String): String? {
        val values = parseCommaList(input)
        if (values.isEmpty() || isClearInput(input)) {
            plugin.config.set("$path.item-flags", null)
            return "Cleared ${drop.id} item flags."
        }

        val flags = mutableListOf<String>()
        values.forEach { value ->
            val itemFlag = runCatching { ItemFlag.valueOf(normalizeName(value)) }.getOrNull()
            if (itemFlag == null) {
                player.sendMessage(colorize("&cUnknown item flag: &f$value&c."))
                return null
            }
            flags += itemFlag.name
        }

        plugin.config.set("$path.item-flags", flags.distinct())
        return "Updated ${drop.id} item flags."
    }

    private fun applyCommandsInput(player: Player, path: String, drop: DropDefinition, input: String): String? {
        if (isClearInput(input)) {
            if (drop.material == null && drop.xpMaxAmount <= 0) {
                player.sendMessage(colorize("&eThis drop needs at least one reward. Commands cannot be removed."))
                return null
            }

            plugin.config.set("$path.commands", null)
            return "Cleared ${drop.id} commands."
        }

        val commands = input.split(";")
            .map { it.trim().removePrefix("/").trim() }
            .filter { it.isNotEmpty() }

        if (commands.isEmpty()) {
            player.sendMessage(colorize("&cNo valid commands were provided."))
            return null
        }

        plugin.config.set("$path.commands", commands)
        return "Updated ${drop.id} commands."
    }

    private fun applyRequiredToolsInput(player: Player, path: String, drop: DropDefinition, input: String): String? {
        val values = parseCommaList(input)
        if (values.isEmpty() || isClearInput(input)) {
            plugin.config.set("$path.required-tools", null)
            return "Cleared ${drop.id} required tools."
        }

        val tools = mutableListOf<String>()
        values.forEach { value ->
            val material = Material.matchMaterial(value)
            if (material == null || material == Material.AIR) {
                player.sendMessage(colorize("&cUnknown tool material: &f$value&c."))
                return null
            }
            tools += material.name
        }

        plugin.config.set("$path.required-tools", tools.distinct())
        return "Updated ${drop.id} required tools."
    }

    private fun adjustChance(player: Player, dropId: String, page: Int, delta: Double) {
        editDrop(player, dropId, page) { path, drop ->
            val newChance = roundChance((drop.chance + delta).coerceIn(0.0, 1.0))
            plugin.config.set("$path.chance", newChance)
            "Updated ${drop.id} chance to ${formatPercent(newChance)}."
        }
    }

    private fun adjustAmount(player: Player, dropId: String, page: Int, delta: Int) {
        editDrop(player, dropId, page) { path, drop ->
            if (drop.material == null) {
                player.sendMessage(colorize("&eThis drop has no item reward, so amount cannot be edited."))
                return@editDrop null
            }

            val newMin = (drop.minAmount + delta).coerceAtLeast(1)
            val newMax = (drop.maxAmount + delta).coerceAtLeast(newMin)
            writeRequiredRange(path, "amount", newMin, newMax)
            "Updated ${drop.id} amount to ${formatRange(newMin, newMax)}."
        }
    }

    private fun adjustXp(player: Player, dropId: String, page: Int, delta: Int) {
        editDrop(player, dropId, page) { path, drop ->
            val newMin = (drop.xpMinAmount + delta).coerceAtLeast(0)
            val newMax = (drop.xpMaxAmount + delta).coerceAtLeast(newMin)
            if (newMax <= 0 && drop.material == null && drop.commands.isEmpty()) {
                player.sendMessage(colorize("&eThis drop needs at least one reward. XP cannot be removed."))
                return@editDrop null
            }

            writeOptionalRange(path, "xp", newMin, newMax)
            "Updated ${drop.id} XP to ${if (newMax <= 0) "none" else formatRange(newMin, newMax)}."
        }
    }

    private fun toggleSpawnerMobs(player: Player, dropId: String, page: Int) {
        editDrop(player, dropId, page) { path, drop ->
            val newValue = !drop.allowSpawnerMobs
            plugin.config.set("$path.allow-spawner-mobs", newValue)
            "Updated ${drop.id} spawner mobs to ${if (newValue) "allowed" else "blocked"}."
        }
    }

    private fun toggleDropEnabled(player: Player, dropId: String, page: Int) {
        editDrop(player, dropId, page) { path, drop ->
            val newValue = !drop.enabled
            plugin.config.set("$path.enabled", newValue)
            "Updated ${drop.id} to ${if (newValue) "enabled" else "disabled"}."
        }
    }

    private fun toggleUnbreakable(player: Player, dropId: String, page: Int) {
        editDrop(player, dropId, page) { path, drop ->
            val newValue = !drop.unbreakable
            plugin.config.set("$path.unbreakable", newValue)
            "Updated ${drop.id} unbreakable to ${if (newValue) "enabled" else "disabled"}."
        }
    }

    private fun deleteDrop(player: Player, dropId: String, page: Int) {
        if (!player.hasPermission("mobgift.gui.edit")) {
            player.sendMessage("You do not have permission to delete drops from the GUI.")
            openDropDetail(player, dropId, page)
            return
        }

        val path = editableDropPath(dropId)
        if (path == null) {
            player.sendMessage(colorize("&eThis drop is read-only because it is not under drops.items in config.yml."))
            openDropDetail(player, dropId, page)
            return
        }

        plugin.config.set(path, null)
        plugin.saveConfig()
        plugin.reloadConfig()
        val result = dropManager.reload()
        player.sendMessage(colorize("&aDeleted drop &f$dropId&a."))
        if (result.warnings.isNotEmpty()) {
            player.sendMessage(colorize("&eConfig reloaded with &f${result.warnings.size}&e warning(s). Use validate for details."))
        }
        openDropList(player, page)
    }

    private fun toggleSetting(player: Player, path: String, value: Boolean) {
        if (!player.hasPermission("mobgift.gui.edit")) {
            player.sendMessage("You do not have permission to edit settings from the GUI.")
            return
        }

        plugin.config.set(path, value)
        plugin.saveConfig()
        plugin.reloadConfig()
        val result = dropManager.reload()
        player.sendMessage(colorize("&aUpdated &f$path&a to &f$value&a."))
        if (result.warnings.isNotEmpty()) {
            player.sendMessage(colorize("&eConfig reloaded with &f${result.warnings.size}&e warning(s). Use validate for details."))
        }
        openSettings(player)
    }

    private fun editDrop(
        player: Player,
        dropId: String,
        page: Int,
        action: (String, DropDefinition) -> String?
    ) {
        if (!player.hasPermission("mobgift.gui.edit")) {
            player.sendMessage("You do not have permission to edit drops from the GUI.")
            return
        }

        val drop = dropManager.drops.firstOrNull { it.id.equals(dropId, ignoreCase = true) }
        if (drop == null) {
            player.sendMessage(colorize("&cDrop '$dropId' is not loaded."))
            openDropDetail(player, dropId, page)
            return
        }

        val path = editableDropPath(drop.id)
        if (path == null) {
            player.sendMessage(colorize("&eThis drop is read-only because it is not under drops.items in config.yml."))
            openDropDetail(player, drop.id, page)
            return
        }

        val message = action(path, drop) ?: run {
            openDropDetail(player, drop.id, page)
            return
        }

        plugin.saveConfig()
        plugin.reloadConfig()
        val result = dropManager.reload()
        player.sendMessage(colorize("&a$message"))
        if (result.warnings.isNotEmpty()) {
            player.sendMessage(colorize("&eConfig reloaded with &f${result.warnings.size}&e warning(s). Use validate for details."))
        }
        openDropDetail(player, drop.id, page)
    }

    private fun editableDropPath(dropId: String): String? {
        val itemsSection = plugin.config.getConfigurationSection("drops.items") ?: return null
        val actualId = itemsSection.getKeys(false).firstOrNull { it.equals(dropId, ignoreCase = true) } ?: return null
        return "drops.items.$actualId"
    }

    private fun copySection(source: ConfigurationSection, target: ConfigurationSection) {
        source.getKeys(false).forEach { key ->
            val value = source.get(key)
            if (value is ConfigurationSection) {
                copySection(value, target.createSection(key))
            } else {
                target.set(key, value)
            }
        }
    }

    private fun isValidDropId(dropId: String): Boolean {
        return dropId.matches(DROP_ID_PATTERN)
    }

    private fun dropIdExists(dropId: String): Boolean {
        val configuredDropExists = plugin.config.getConfigurationSection("drops.items")
            ?.getKeys(false)
            ?.any { it.equals(dropId, ignoreCase = true) } == true
        return configuredDropExists || dropManager.drops.any { it.id.equals(dropId, ignoreCase = true) }
    }

    private fun writeRequiredRange(path: String, field: String, min: Int, max: Int) {
        plugin.config.set("$path.$field", null)
        if (min == max) {
            plugin.config.set("$path.$field", min)
        } else {
            plugin.config.set("$path.$field.min", min)
            plugin.config.set("$path.$field.max", max)
        }
    }

    private fun writeOptionalRange(path: String, field: String, min: Int, max: Int) {
        plugin.config.set("$path.$field", null)
        if (max <= 0) {
            return
        }

        if (min == max) {
            plugin.config.set("$path.$field", min)
        } else {
            plugin.config.set("$path.$field.min", min)
            plugin.config.set("$path.$field.max", max)
        }
    }

    private fun roundChance(value: Double): Double {
        return String.format(Locale.ROOT, "%.4f", value).toDouble()
    }

    private fun createInventory(holder: MobGiftGuiHolder, rows: Int, title: String): Inventory {
        return holder.bind(Bukkit.createInventory(holder, rows * 9, colorize(title)))
    }

    private fun sortedDrops(): List<DropDefinition> {
        return dropManager.drops.sortedBy { it.id.lowercase(Locale.ROOT) }
    }

    private fun maxPage(size: Int): Int {
        return if (size <= 0) 0 else (size - 1) / DROPS_PER_PAGE
    }

    private fun pageForDrop(dropId: String): Int {
        val index = sortedDrops().indexOfFirst { it.id.equals(dropId, ignoreCase = true) }
        return if (index < 0) 0 else index / DROPS_PER_PAGE
    }

    private fun parseRangeInput(input: String, minimum: Int): Pair<Int, Int>? {
        val normalized = input.trim()
            .replace("..", ",")
            .replace(" to ", ",", ignoreCase = true)
            .replace(":", ",")
            .replace("-", ",")
        val parts = normalized.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return when (parts.size) {
            1 -> {
                val value = parts[0].toIntOrNull()?.coerceAtLeast(minimum) ?: return null
                value to value
            }
            2 -> {
                val min = parts[0].toIntOrNull()?.coerceAtLeast(minimum) ?: return null
                val max = parts[1].toIntOrNull()?.coerceAtLeast(min) ?: return null
                min to max
            }
            else -> null
        }
    }

    private fun parseDurationSecondsInput(input: String): Long? {
        val trimmed = input.trim().lowercase(Locale.ROOT)
        if (trimmed.isEmpty()) {
            return null
        }

        val unit = trimmed.last()
        val multiplier = when (unit) {
            's' -> 1L
            'm' -> 60L
            'h' -> 3600L
            'd' -> 86400L
            else -> 1L
        }
        val numericPart = if (unit in setOf('s', 'm', 'h', 'd')) trimmed.dropLast(1) else trimmed
        val value = numericPart.toLongOrNull() ?: return null
        return value.coerceAtLeast(0L).coerceAtMost(Long.MAX_VALUE / multiplier) * multiplier
    }

    private fun statsItem(stats: DropStatsSnapshot, rank: Int): ItemStack {
        val drop = dropManager.drops.firstOrNull { it.id.equals(stats.dropId, ignoreCase = true) }
        val icon = drop?.let(::dropIcon) ?: Material.PAPER
        val forcedLine = if (stats.forcedAwards > 0) {
            listOf("&7Forced awards: &f${stats.forcedAwards}")
        } else {
            emptyList()
        }

        return button(icon, "&a#$rank &f${stats.dropId}", listOf(
            "&7Awards: &f${stats.awards}",
            "&7Items awarded: &f${stats.itemAmount}",
            "&7XP awarded: &f${stats.xpAmount}",
            "&7Commands run: &f${stats.commandRuns}"
        ) + forcedLine + listOf(
            "&7Last player: &f${stats.lastPlayerName ?: "none"}",
            "&7Last mob/source: &f${stats.lastMob ?: "none"}",
            "&7Last awarded: &f${formatTimestamp(stats.lastAwardedAt)}"
        ))
    }

    private fun dropItem(drop: DropDefinition): ItemStack {
        return button(dropIcon(drop), "${if (drop.enabled) "&a" else "&7"}${drop.id}", listOf(
            "&7Status: &f${if (drop.enabled) "enabled" else "disabled"}",
            "&7Rewards: &f${describeRewards(drop)}",
            "&7Chance: &f${formatChance(drop)}",
            "&7Cooldown: &f${formatDuration(drop.cooldownSeconds)}",
            "&7Mobs: &f${shortList(drop.mobs)}",
            "&7Worlds: &f${shortList(drop.worlds)}",
            "&7Biomes: &f${shortList(drop.biomes)}",
            "&7Spawners: &f${if (drop.allowSpawnerMobs) "allowed" else "blocked"}",
            "",
            "&eClick to view details."
        ))
    }

    private fun rewardItem(drop: DropDefinition): ItemStack {
        return button(dropIcon(drop), "&aRewards", listOf(
            "&7Status: &f${if (drop.enabled) "enabled" else "disabled"}",
            "&7Item: &f${drop.material?.name ?: "none"}",
            "&7Amount: &f${formatRange(drop.minAmount, drop.maxAmount)}",
            "&7XP: &f${if (drop.xpMaxAmount > 0) formatRange(drop.xpMinAmount, drop.xpMaxAmount) else "none"}",
            "&7Chance: &f${formatChance(drop)}",
            "&7Cooldown: &f${formatDuration(drop.cooldownSeconds)}",
            "&7Looting chance: &f${formatPercent(drop.lootingChancePerLevel)} per level",
            "&7Looting amount: &f${drop.lootingAmountPerLevel} per level"
        ))
    }

    private fun filterItem(drop: DropDefinition): ItemStack {
        return button(Material.COMPASS, "&bFilters", listOf(
            "&7Mobs: &f${shortList(drop.mobs, 5)}",
            "&7Worlds: &f${shortList(drop.worlds, 5)}",
            "&7Biomes: &f${shortList(drop.biomes, 5)}",
            "&7Spawner mobs: &f${if (drop.allowSpawnerMobs) "allowed" else "blocked"}"
        ))
    }

    private fun playerFilterItem(drop: DropDefinition): ItemStack {
        return button(Material.IRON_SWORD, "&ePlayer Filters", listOf(
            "&7Permission: &f${drop.permission ?: "none"}",
            "&7Required tools: &f${if (drop.requiredTools.isEmpty()) "none" else drop.requiredTools.joinToString(", ") { it.name }}"
        ))
    }

    private fun effectItem(drop: DropDefinition): ItemStack {
        return button(Material.FIREWORK_ROCKET, "&dEffects", listOf(
            "&7Sound: &f${if (drop.sound == null) "none" else "configured"}",
            "&7Particle: &f${drop.particle?.particle?.name ?: "none"}",
            "&7Broadcast: &f${if (drop.broadcast == null) "none" else "configured"}"
        ))
    }

    private fun messageItem(drop: DropDefinition): ItemStack {
        return button(Material.PAPER, "&fMessages", listOf(
            "&7Player message:",
            "&f${drop.message ?: "none"}",
            "",
            "&7Broadcast:",
            "&f${drop.broadcast ?: "none"}"
        ))
    }

    private fun commandItem(drop: DropDefinition): ItemStack {
        val lore = mutableListOf<String>()
        if (drop.commands.isEmpty()) {
            lore += "&7No commands configured."
        } else {
            lore += "&7Console commands:"
            drop.commands.take(5).forEach { lore += "&f$it" }
            if (drop.commands.size > 5) {
                lore += "&7...and ${drop.commands.size - 5} more."
            }
        }

        return button(Material.COMMAND_BLOCK, "&cCommands", lore)
    }

    private fun configInfoItem(drop: DropDefinition): ItemStack {
        return button(Material.KNOWLEDGE_BOOK, "&fConfig Info", listOf(
            "&7Drop ID: &f${drop.id}",
            "&7Enabled: &f${if (drop.enabled) "true" else "false"}",
            "&7Cooldown: &f${formatDuration(drop.cooldownSeconds)}",
            "&7Display name: &f${drop.displayName ?: "none"}",
            "&7Lore lines: &f${drop.lore.size}",
            "&7Custom model data: &f${drop.customModelData ?: "none"}",
            "&7Enchantments: &f${drop.enchantments.size}",
            "&7Item flags: &f${drop.itemFlags.size}",
            "&7Unbreakable: &f${if (drop.unbreakable) "enabled" else "disabled"}",
            "",
            "&7Use the edit buttons to change supported fields."
        ))
    }

    private fun dropIcon(drop: DropDefinition): Material {
        return when {
            drop.material != null -> drop.material
            drop.xpMaxAmount > 0 -> Material.EXPERIENCE_BOTTLE
            drop.commands.isNotEmpty() -> Material.COMMAND_BLOCK
            else -> Material.PAPER
        }
    }

    private fun button(material: Material, name: String, lore: List<String> = emptyList()): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(colorize(name))
        if (lore.isNotEmpty()) {
            meta.setLore(lore.map(::colorize))
        }
        item.itemMeta = meta
        return item
    }

    private fun editButton(material: Material, name: String, lore: List<String> = emptyList()): ItemStack {
        return button(material, name, lore + listOf(
            "",
            "&eClick to edit.",
            "&7Requires &fmobgift.gui.edit&7."
        ))
    }

    private fun chatEditButton(material: Material, name: String, currentValue: String): ItemStack {
        return button(material, name, listOf(
            "&7Current: &f$currentValue",
            "",
            "&eClick to edit in chat.",
            "&7Requires &fmobgift.gui.edit&7."
        ))
    }

    private fun chatInputCurrentValue(drop: DropDefinition, type: ChatInputType): String {
        return when (type) {
            ChatInputType.MATERIAL -> drop.material?.name ?: "none"
            ChatInputType.CHANCE -> formatPercent(drop.chance)
            ChatInputType.AMOUNT -> if (drop.material == null) "no item reward" else formatRange(drop.minAmount, drop.maxAmount)
            ChatInputType.XP -> formatXp(drop)
            ChatInputType.COOLDOWN -> formatDuration(drop.cooldownSeconds)
            ChatInputType.MOBS -> shortList(drop.mobs, 8)
            ChatInputType.WORLDS -> shortList(drop.worlds, 8)
            ChatInputType.BIOMES -> shortList(drop.biomes, 8)
            ChatInputType.PERMISSION -> drop.permission ?: "none"
            ChatInputType.MESSAGE -> drop.message ?: "none"
            ChatInputType.BROADCAST -> drop.broadcast ?: "none"
            ChatInputType.COMMANDS -> if (drop.commands.isEmpty()) "none" else drop.commands.joinToString(" ; ")
            ChatInputType.REQUIRED_TOOLS -> if (drop.requiredTools.isEmpty()) {
                "none"
            } else {
                drop.requiredTools.joinToString(", ") { it.name }
            }
            ChatInputType.DISPLAY_NAME -> drop.displayName ?: "none"
            ChatInputType.LORE -> if (drop.lore.isEmpty()) "none" else drop.lore.joinToString(" ; ")
            ChatInputType.CUSTOM_MODEL_DATA -> drop.customModelData?.toString() ?: "none"
            ChatInputType.SOUND -> if (drop.sound == null) "none" else "configured"
            ChatInputType.SOUND_VOLUME -> drop.sound?.volume?.toString() ?: "none"
            ChatInputType.SOUND_PITCH -> drop.sound?.pitch?.toString() ?: "none"
            ChatInputType.PARTICLE -> drop.particle?.particle?.name ?: "none"
            ChatInputType.PARTICLE_COUNT -> drop.particle?.count?.toString() ?: "none"
            ChatInputType.LOOTING_CHANCE -> formatPercent(drop.lootingChancePerLevel)
            ChatInputType.LOOTING_AMOUNT -> drop.lootingAmountPerLevel.toString()
            ChatInputType.ENCHANTMENTS -> if (drop.enchantments.isEmpty()) {
                "none"
            } else {
                drop.enchantments.entries.joinToString(" ; ") { "${it.key.key.value()}:${it.value}" }
            }
            ChatInputType.ITEM_FLAGS -> if (drop.itemFlags.isEmpty()) {
                "none"
            } else {
                drop.itemFlags.joinToString(", ") { it.name }
            }
        }
    }

    private fun describeRewards(drop: DropDefinition): String {
        val rewards = mutableListOf<String>()
        drop.material?.let { rewards += "${it.name} x${formatRange(drop.minAmount, drop.maxAmount)}" }
        if (drop.xpMaxAmount > 0) {
            rewards += "XP ${formatRange(drop.xpMinAmount, drop.xpMaxAmount)}"
        }
        if (drop.commands.isNotEmpty()) {
            rewards += "${drop.commands.size} command(s)"
        }

        return rewards.joinToString(", ").ifBlank { "none" }
    }

    private fun shortList(values: Set<String>, maxValues: Int = 4): String {
        if (values.isEmpty()) {
            return "none"
        }

        val visible = values.take(maxValues).joinToString(", ")
        val hidden = values.size - maxValues
        return if (hidden > 0) "$visible, +$hidden" else visible
    }

    private fun parseCommaList(input: String): List<String> {
        return input.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun parseMobListForCreate(player: Player, input: String): List<String>? {
        val values = parseCommaList(input)
        if (values.isEmpty()) {
            player.sendMessage(colorize("&cProvide at least one mob name or ALL."))
            return null
        }

        val mobs = mutableListOf<String>()
        values.forEach { value ->
            val normalized = normalizeName(value)
            if (normalized == "ALL") {
                return listOf("ALL")
            }

            if (runCatching { EntityType.valueOf(normalized) }.isFailure) {
                player.sendMessage(colorize("&cUnknown mob type: &f$value&c."))
                return null
            }
            mobs += normalized
        }

        return mobs.distinct()
    }

    private fun parseChanceInput(input: String): Double? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return null
        }

        val numericValue = trimmed.removeSuffix("%").trim().toDoubleOrNull() ?: return null
        val chance = when {
            trimmed.endsWith("%") -> numericValue / 100.0
            numericValue > 1.0 -> numericValue / 100.0
            else -> numericValue
        }

        return chance.takeIf { it in 0.0..1.0 }?.let(::roundChance)
    }

    private fun parseSound(value: String): Sound? {
        val soundFromField = runCatching {
            Sound::class.java.getField(normalizeName(value)).get(null) as? Sound
        }.getOrNull()
        if (soundFromField != null) {
            return soundFromField
        }

        val registryName = value.trim().lowercase(Locale.ROOT).let {
            if (it.contains(".") || it.contains(":")) it else it.replace("_", ".")
        }
        return Registry.SOUNDS.match(registryName)
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

    private fun isClearInput(input: String): Boolean {
        return input.equals("none", ignoreCase = true) ||
            input.equals("clear", ignoreCase = true) ||
            input.equals("remove", ignoreCase = true)
    }

    private fun formatRange(min: Int, max: Int): String {
        return if (min == max) min.toString() else "$min-$max"
    }

    private fun formatXp(drop: DropDefinition): String {
        return if (drop.xpMaxAmount > 0) formatRange(drop.xpMinAmount, drop.xpMaxAmount) else "none"
    }

    private fun formatDuration(seconds: Long): String {
        return when {
            seconds <= 0 -> "none"
            seconds % 3600L == 0L -> "${seconds / 3600L}h"
            seconds % 60L == 0L -> "${seconds / 60L}m"
            else -> "${seconds}s"
        }
    }

    private fun formatTimestamp(millis: Long): String {
        return if (millis <= 0) {
            "never"
        } else {
            DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(millis))
        }
    }

    private fun formatChance(drop: DropDefinition): String {
        val baseChance = formatPercent(drop.chance)
        val bonuses = mutableListOf<String>()
        if (drop.lootingChancePerLevel > 0.0) {
            bonuses += "+${formatPercent(drop.lootingChancePerLevel)}/Looting"
        }
        if (drop.lootingAmountPerLevel > 0) {
            bonuses += "+${drop.lootingAmountPerLevel} item/Looting"
        }

        return if (bonuses.isEmpty()) baseChance else "$baseChance (${bonuses.joinToString(", ")})"
    }

    private fun formatPercent(value: Double): String {
        return String.format(Locale.ROOT, "%.2f%%", value * 100)
    }

    private fun colorize(value: String): String {
        return ChatColor.translateAlternateColorCodes('&', value)
    }

    private fun splitLore(value: String, maxLineLength: Int = 42): List<String> {
        val words = value.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        words.forEach { word ->
            if (currentLine.isNotEmpty() && currentLine.length + word.length + 1 > maxLineLength) {
                lines += "&f$currentLine"
                currentLine = StringBuilder(word)
            } else {
                if (currentLine.isNotEmpty()) {
                    currentLine.append(' ')
                }
                currentLine.append(word)
            }
        }

        if (currentLine.isNotEmpty()) {
            lines += "&f$currentLine"
        }

        return lines.ifEmpty { listOf("&f$value") }
    }

    private fun normalizeName(value: String): String {
        return value.trim()
            .replace("-", "_")
            .replace(" ", "_")
            .uppercase(Locale.ROOT)
    }

    private companion object {
        const val DROPS_PER_PAGE = 45
        const val WARNINGS_PER_PAGE = 45
        const val STATS_PER_PAGE = 45
        val DROP_ID_PATTERN = Regex("[A-Za-z0-9_-]+")
    }
}
