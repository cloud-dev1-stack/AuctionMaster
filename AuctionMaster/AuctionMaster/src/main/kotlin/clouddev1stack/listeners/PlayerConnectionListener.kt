package clouddev1stack.listeners

import clouddev1stack.AuctionMaster
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class PlayerConnectionListener(private val plugin: AuctionMaster) : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        plugin.preferencesCache.warm(event.player.uniqueId)
        plugin.alertsManager.flushOnJoin(event.player)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        plugin.preferencesCache.drop(event.player.uniqueId)
    }
}
