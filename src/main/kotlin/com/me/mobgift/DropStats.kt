package com.me.mobgift

data class DropStatsSnapshot(
    val dropId: String,
    val awards: Long,
    val forcedAwards: Long,
    val itemAmount: Long,
    val xpAmount: Long,
    val commandRuns: Long,
    val lastAwardedAt: Long,
    val lastPlayerName: String?,
    val lastMob: String?
)

data class PlayerStatsSnapshot(
    val playerUuid: String,
    val playerName: String,
    val awards: Long,
    val forcedAwards: Long,
    val itemAmount: Long,
    val xpAmount: Long,
    val commandRuns: Long,
    val lastAwardedAt: Long,
    val lastDropId: String?,
    val lastMob: String?
)
