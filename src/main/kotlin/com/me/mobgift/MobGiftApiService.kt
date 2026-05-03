package com.me.mobgift

import com.me.mobgift.api.MobGiftApi

class MobGiftApiService(
    private val plugin: MobGift,
    private val dropManager: DropManager
) : MobGiftApi {
    override fun reloadDrops(): DropLoadResult {
        plugin.reloadConfig()
        return dropManager.reload()
    }

    override fun getDropIds(): Set<String> {
        return dropManager.dropIds().toSet()
    }

    override fun getDrops(): List<DropDefinition> {
        return dropManager.drops.toList()
    }

    override fun getDrop(dropId: String): DropDefinition? {
        return dropManager.getDrop(dropId)
    }

    override fun getDropStats(dropId: String): DropStatsSnapshot? {
        return dropManager.dropStatsSnapshot(dropId)
    }

    override fun getPlayerStats(playerNameOrUuid: String): PlayerStatsSnapshot? {
        return dropManager.playerStatsSnapshot(playerNameOrUuid)
    }

    override fun getTopDropStats(limit: Int): List<DropStatsSnapshot> {
        return dropManager.topDropStats(limit)
    }

    override fun getTopPlayerStats(limit: Int): List<PlayerStatsSnapshot> {
        return dropManager.topPlayerStats(limit)
    }
}
