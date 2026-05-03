package com.me.mobgift

import com.me.mobgift.api.MobGiftApi
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.plugin.ServicePriority

class MobGift : JavaPlugin() {
    private lateinit var dropManager: DropManager

    override fun onEnable() {
        saveDefaultConfig()
        dropManager = DropManager(this)
        dropManager.reload()
        server.servicesManager.register(MobGiftApi::class.java, MobGiftApiService(this, dropManager), this, ServicePriority.Normal)
        val mobGiftGui = MobGiftGui(this, dropManager)

        server.pluginManager.registerEvents(MobKillListener(dropManager), this)
        server.pluginManager.registerEvents(mobGiftGui, this)
        val mobGiftCommand = MobGiftCommand(this, dropManager, mobGiftGui)
        getCommand("mobgift")?.setExecutor(mobGiftCommand)
        getCommand("mobgift")?.tabCompleter = mobGiftCommand
    }

    override fun onDisable() {
        server.servicesManager.unregisterAll(this)
        if (::dropManager.isInitialized) {
            dropManager.shutdown()
        }
    }
}
