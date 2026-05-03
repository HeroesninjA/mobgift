package com.me.mobgift.api

import com.me.mobgift.DropDefinition
import com.me.mobgift.DropLoadResult
import com.me.mobgift.DropStatsSnapshot
import com.me.mobgift.PlayerStatsSnapshot

interface MobGiftApi {
    fun reloadDrops(): DropLoadResult

    fun getDropIds(): Set<String>

    fun getDrops(): List<DropDefinition>

    fun getDrop(dropId: String): DropDefinition?

    fun getDropStats(dropId: String): DropStatsSnapshot?

    fun getPlayerStats(playerNameOrUuid: String): PlayerStatsSnapshot?

    fun getTopDropStats(limit: Int = 10): List<DropStatsSnapshot>

    fun getTopPlayerStats(limit: Int = 10): List<PlayerStatsSnapshot>
}
