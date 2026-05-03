package com.me.mobgift

import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag

data class DropDefinition(
    val id: String,
    val enabled: Boolean,
    val cooldownSeconds: Long,
    val material: Material?,
    val minAmount: Int,
    val maxAmount: Int,
    val chance: Double,
    val mobs: Set<String>,
    val worlds: Set<String>,
    val biomes: Set<String>,
    val requiredTools: Set<Material>,
    val permission: String?,
    val lootingChancePerLevel: Double,
    val lootingAmountPerLevel: Int,
    val message: String?,
    val displayName: String?,
    val lore: List<String>,
    val customModelData: Int?,
    val enchantments: Map<Enchantment, Int>,
    val itemFlags: Set<ItemFlag>,
    val unbreakable: Boolean,
    val xpMinAmount: Int,
    val xpMaxAmount: Int,
    val commands: List<String>,
    val broadcast: String?,
    val sound: DropSound?,
    val particle: DropParticle?,
    val allowSpawnerMobs: Boolean
)

data class DropSound(
    val sound: Sound,
    val volume: Float,
    val pitch: Float
)

data class DropParticle(
    val particle: Particle,
    val count: Int,
    val offsetX: Double,
    val offsetY: Double,
    val offsetZ: Double,
    val extra: Double
)
