package com.me.mobgift

import org.bukkit.plugin.java.JavaPlugin

class MobGift : JavaPlugin() {
    private lateinit var dropManager: DropManager

    override fun onEnable() {
        saveDefaultConfig()
        dropManager = DropManager(this)
        dropManager.reload()

        server.pluginManager.registerEvents(MobKillListener(dropManager), this)
        val mobGiftCommand = MobGiftCommand(this, dropManager)
        getCommand("mobgift")?.setExecutor(mobGiftCommand)
        getCommand("mobgift")?.tabCompleter = mobGiftCommand
    }
}
