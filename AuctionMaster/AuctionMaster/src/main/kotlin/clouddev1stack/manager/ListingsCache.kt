package clouddev1stack.manager

import clouddev1stack.AuctionMaster
import clouddev1stack.model.Listing
import clouddev1stack.util.ItemSerialization
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack

class ListingsCache(private val plugin: AuctionMaster) {

    private val listings = AtomicReference<List<Listing>>(emptyList())
    private val itemCache = ConcurrentHashMap<UUID, ItemStack>()
    private var refreshTaskId: Int = -1

    fun start() {
        refreshNow()
        val intervalSeconds = plugin.config.getLong("performance.listings-cache-refresh-seconds", 5L).coerceAtLeast(1L)
        val ticks = 20L * intervalSeconds
        refreshTaskId = Bukkit.getScheduler()
            .runTaskTimerAsynchronously(plugin, Runnable { refreshNow() }, ticks, ticks)
            .taskId
    }

    fun stop() {
        if (refreshTaskId != -1) Bukkit.getScheduler().cancelTask(refreshTaskId)
    }

    fun snapshot(): List<Listing> = listings.get()

    fun get(id: UUID): Listing? = listings.get().firstOrNull { it.id == id }

    fun forSeller(sellerUuid: UUID): List<Listing> = listings.get().filter { it.sellerUuid == sellerUuid }

    /** Deserializing an ItemStack is real CPU work - do it once per refresh, not once per GUI open. */
    fun item(id: UUID): ItemStack? = itemCache[id]?.clone()

    fun invalidate() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { refreshNow() })
    }

    private fun refreshNow() {
        runCatching {
            val fresh = plugin.storage.getActiveListings()
            listings.set(fresh)

            val freshIds = fresh.map { it.id }.toHashSet()
            itemCache.keys.retainAll(freshIds)
            fresh.forEach { listing ->
                if (!itemCache.containsKey(listing.id)) {
                    itemCache[listing.id] = ItemSerialization.deserializeSafe(listing.itemData)
                }
            }
        }.onFailure { plugin.logger.warning("Listings cache refresh failed: ${it.message}") }
    }
}
