package com.me.mobgift

import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.Locale

class MobKillListener(
    private val plugin: JavaPlugin
) : Listener {

    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        if (event.entity.killer == null) {
            return
        }

        if (event.entityType == EntityType.PLAYER) {
            return
        }

        if (plugin.config.getBoolean("drops.replace-default-drops", true)) {
            event.drops.clear()
        }

        val itemDrops = plugin.config.getConfigurationSection("drops.items")
        if (itemDrops != null && itemDrops.getKeys(false).isNotEmpty()) {
            itemDrops.getKeys(false).forEach { dropKey ->
                addConfiguredDrop(event, "drops.items.$dropKey")
            }
            return
        }

        addLegacyDrops(event)
    }

    private fun addLegacyDrops(event: EntityDeathEvent) {
        addConfiguredDrop(event, "drops.guaranteed", Material.DIAMOND, 1.0, 5)
        addConfiguredDrop(event, "drops.bonus.gold", Material.GOLD_INGOT)
        addConfiguredDrop(event, "drops.bonus.iron", Material.IRON_INGOT)
    }

    private fun addConfiguredDrop(
        event: EntityDeathEvent,
        path: String,
        fallbackMaterial: Material? = null,
        fallbackChance: Double = 0.0,
        fallbackAmount: Int = 1
    ) {
        if (!isDropAllowedForMob(path, event.entityType)) {
            return
        }

        val chance = plugin.config.getDouble("$path.chance", fallbackChance).coerceIn(0.0, 1.0)
        if (chance <= 0.0 || Math.random() > chance) {
            return
        }

        val configuredMaterial = plugin.config.getString("$path.material", fallbackMaterial?.name ?: "") ?: ""
        val material = Material.matchMaterial(configuredMaterial) ?: fallbackMaterial ?: return
        val amount = plugin.config.getInt("$path.amount", fallbackAmount).coerceAtLeast(1)
        event.drops.add(ItemStack(material, amount))
    }

    private fun isDropAllowedForMob(path: String, entityType: EntityType): Boolean {
        val configuredMobs = getConfiguredMobs(path)
        if (configuredMobs.isEmpty()) {
            return true
        }

        return configuredMobs.any { mob ->
            val normalizedMob = normalizeMobName(mob)
            normalizedMob == "ALL" || runCatching { EntityType.valueOf(normalizedMob) }.getOrNull() == entityType
        }
    }

    private fun getConfiguredMobs(path: String): List<String> {
        val mobsPath = "$path.mobs"
        val mobPath = "$path.mob"
        val rawValue = when {
            plugin.config.contains(mobsPath) -> plugin.config.get(mobsPath)
            plugin.config.contains(mobPath) -> plugin.config.get(mobPath)
            else -> return listOf("ALL")
        }

        return when (rawValue) {
            is String -> rawValue.split(",").map { it.trim() }
            is List<*> -> rawValue.mapNotNull { it?.toString()?.trim() }
            else -> emptyList()
        }.filter { it.isNotEmpty() }
    }

    private fun normalizeMobName(mob: String): String {
        return mob.trim()
            .replace("-", "_")
            .replace(" ", "_")
            .uppercase(Locale.ROOT)
    }
}
