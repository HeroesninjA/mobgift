package com.me.mobgift

import org.bukkit.plugin.java.JavaPlugin

class Mobgift : JavaPlugin() {

    override fun onEnable() {
        saveDefaultConfig()
        server.pluginManager.registerEvents(MobKillListener(this), this)
    }
}
