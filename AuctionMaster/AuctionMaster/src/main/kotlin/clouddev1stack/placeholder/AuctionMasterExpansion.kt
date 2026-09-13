package clouddev1stack.placeholder

import clouddev1stack.AuctionMaster
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer

class AuctionMasterExpansion(private val plugin: AuctionMaster) : PlaceholderExpansion() {

    private val pendingBalanceCache = ConcurrentHashMap<UUID, Double>()
    private val pendingBalanceLastFetch = ConcurrentHashMap<UUID, Long>()
    private val pendingBalanceTtlMillis = 10_000L

    override fun getIdentifier() = "auctionmaster"
    override fun getAuthor() = "ravirai"
    override fun getVersion() = plugin.pluginMeta.version
    override fun persist() = true

    override fun onPlaceholderRequest(player: org.bukkit.entity.Player?, params: String): String {
        return when (params.lowercase()) {
            "active_listings" -> plugin.listingsCache.snapshot().size.toString()
            "my_listings" -> {
                val p: OfflinePlayer = player ?: return "0"
                plugin.listingsCache.forSeller(p.uniqueId).size.toString()
            }
            "pending_balance" -> {
                val p: OfflinePlayer = player ?: return "0.00"
                String.format("%.2f", pendingBalance(p.uniqueId))
            }
            "alerts_status" -> {
                val p: OfflinePlayer = player ?: return "Disabled"
                if (plugin.preferencesCache.alertsEnabled(p.uniqueId)) "Enabled" else "Disabled"
            }
            else -> ""
        }
    }

    private fun pendingBalance(playerUuid: UUID): Double {
        val lastFetch = pendingBalanceLastFetch[playerUuid] ?: 0L
        val now = System.currentTimeMillis()
        if (now - lastFetch > pendingBalanceTtlMillis) {
            pendingBalanceLastFetch[playerUuid] = now
            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                pendingBalanceCache[playerUuid] = plugin.storage.getPendingBalance(playerUuid)
            })
        }
        return pendingBalanceCache[playerUuid] ?: 0.0
    }
}
