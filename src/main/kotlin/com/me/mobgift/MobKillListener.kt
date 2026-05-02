package com.me.mobgift

import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent

class MobKillListener(
    private val dropManager: DropManager
) : Listener {

    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        if (event.entity.killer == null) {
            return
        }

        if (event.entityType == EntityType.PLAYER) {
            return
        }

        if (dropManager.replaceDefaultDrops) {
            event.drops.clear()
        }

        dropManager.applyDrops(event)
    }
}
