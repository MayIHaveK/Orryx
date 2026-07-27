package org.gitee.orryx.core.script.javascript

import org.bukkit.Bukkit
import org.bukkit.entity.Player

class JavaScriptServerBridge {

    fun getPlayer(name: String): Player? = Bukkit.getPlayerExact(name)

    fun getOnlinePlayers(): Collection<Player> = Bukkit.getOnlinePlayers()

    fun broadcast(message: String) {
        Bukkit.broadcastMessage(message)
    }

    fun dispatch(command: String): Boolean {
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.removePrefix("/"))
    }
}
