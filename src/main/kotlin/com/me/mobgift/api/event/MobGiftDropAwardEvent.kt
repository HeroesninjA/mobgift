package com.me.mobgift.api.event

import com.me.mobgift.DropDefinition
import org.bukkit.Location
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class MobGiftDropAwardEvent(
    val player: Player,
    val drop: DropDefinition,
    val source: String,
    val entityType: EntityType?,
    val location: Location,
    val forced: Boolean,
    initialItemAmount: Int,
    initialXpAmount: Int
) : Event(), Cancellable {
    private var cancelled = false

    var itemAmount: Int = initialItemAmount.coerceAtLeast(0)
        set(value) {
            field = value.coerceAtLeast(0)
        }

    var xpAmount: Int = initialXpAmount.coerceAtLeast(0)
        set(value) {
            field = value.coerceAtLeast(0)
        }

    override fun isCancelled(): Boolean {
        return cancelled
    }

    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    override fun getHandlers(): HandlerList {
        return handlerList
    }

    companion object {
        @JvmStatic
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return handlerList
        }
    }
}
