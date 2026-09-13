package clouddev1stack.manager

import clouddev1stack.AuctionMaster
import clouddev1stack.storage.PlayerPreferences
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.Bukkit

class PreferencesCache(private val plugin: AuctionMaster) {

    private val cache = ConcurrentHashMap<UUID, PlayerPreferences>()

    fun warm(playerUuid: UUID) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val prefs = plugin.storage.getPreferences(playerUuid)
            cache[playerUuid] = prefs
        })
    }

    fun drop(playerUuid: UUID) {
        cache.remove(playerUuid)
    }

    fun quickBuy(playerUuid: UUID): Boolean =
        cache[playerUuid]?.quickBuy ?: plugin.config.getBoolean("gui.quick-buy-default", false)

    fun quickSell(playerUuid: UUID): Boolean =
        cache[playerUuid]?.quickSell ?: plugin.config.getBoolean("gui.quick-sell-default", false)

    fun alertsEnabled(playerUuid: UUID): Boolean =
        cache[playerUuid]?.alertsEnabled ?: plugin.config.getBoolean("auction-alerts.default-enabled", false)

    fun toggleQuickBuy(playerUuid: UUID, callback: (Boolean) -> Unit) {
        val newValue = !quickBuy(playerUuid)
        cache[playerUuid] = (cache[playerUuid] ?: PlayerPreferences(playerUuid, false, false)).copy(quickBuy = newValue)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            plugin.storage.setQuickBuy(playerUuid, newValue)
            Bukkit.getScheduler().runTask(plugin, Runnable { callback(newValue) })
        })
    }

    fun toggleQuickSell(playerUuid: UUID, callback: (Boolean) -> Unit) {
        val newValue = !quickSell(playerUuid)
        cache[playerUuid] = (cache[playerUuid] ?: PlayerPreferences(playerUuid, false, false)).copy(quickSell = newValue)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            plugin.storage.setQuickSell(playerUuid, newValue)
            Bukkit.getScheduler().runTask(plugin, Runnable { callback(newValue) })
        })
    }

    fun toggleAlerts(playerUuid: UUID, callback: (Boolean) -> Unit) {
        val newValue = !alertsEnabled(playerUuid)
        cache[playerUuid] = (cache[playerUuid] ?: PlayerPreferences(playerUuid, false, false)).copy(alertsEnabled = newValue)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            plugin.storage.setAlertsEnabled(playerUuid, newValue)
            Bukkit.getScheduler().runTask(plugin, Runnable { callback(newValue) })
        })
    }
}
