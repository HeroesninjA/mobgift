package com.me.mobgift

import org.bukkit.Material

data class DropDefinition(
    val id: String,
    val material: Material,
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
    val customModelData: Int?
)
