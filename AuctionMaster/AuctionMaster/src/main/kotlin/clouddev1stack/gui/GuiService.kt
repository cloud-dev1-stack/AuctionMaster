package clouddev1stack.gui

import clouddev1stack.AuctionMaster
import clouddev1stack.util.ItemSerialization
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class GuiService(private val plugin: AuctionMaster) {

    fun openAuctionHouse(player: Player, page: Int, searchQuery: String? = null) {
        val safePage = page.coerceAtLeast(0)
        var listings = plugin.listingsCache.snapshot().sortedByDescending { it.createdAt }
        if (!searchQuery.isNullOrBlank()) {
            val query = searchQuery.lowercase()
            listings = listings.filter { listing ->
                listing.sellerName.lowercase().contains(query) ||
                    ItemSerialization.deserializeSafe(listing.itemData).type.name.lowercase().replace('_', ' ').contains(query)
            }
        }
        val itemsPerPage = plugin.config.getInt("gui.items-per-page")
        val rows = plugin.config.getInt("gui.rows")
        val titleTemplate = plugin.config.getString("gui.title", "&8Auction House") ?: "&8Auction House"
        val title = if (!searchQuery.isNullOrBlank()) "$titleTemplate &7- \"$searchQuery\"" else titleTemplate
        val inv = AuctionGUI.build(player, listings, safePage, itemsPerPage, title, rows) { listing ->
            plugin.listingsCache.item(listing.id) ?: ItemSerialization.deserializeSafe(listing.itemData)
        }
        player.openInventory(inv)
    }

    fun openYourListings(player: Player) {
        val listings = plugin.listingsCache.forSeller(player.uniqueId).sortedByDescending { it.createdAt }
        val unlockedSlots = calculateUnlockedSlots(player)
        val inv = YourListingsGUI.build(player, listings, unlockedSlots) { listing ->
            plugin.listingsCache.item(listing.id) ?: ItemSerialization.deserializeSafe(listing.itemData)
        }
        player.openInventory(inv)
    }

    fun openSellInsert(player: Player, prefill: ItemStack?) {
        player.openInventory(SellFlowGUI.buildInsert(prefill))
    }

    fun calculateUnlockedSlots(player: Player): Int {
        val base = plugin.config.getInt("listings.max-active-per-player")
        if (player.hasPermission("auctionmaster.bypass.limit")) return 54
        val section = plugin.config.getConfigurationSection("listings.tier-bonus-permissions")
        val bonus = section?.getKeys(false)?.sumOf { permission ->
            if (player.hasPermission(permission)) section.getInt(permission) else 0
        } ?: 0
        return (base + bonus).coerceAtMost(54)
    }

    fun openCollectionBox(player: Player) {
        plugin.auctionManager.collectAll(player) { items, pendingBalance ->
            if (items.isEmpty() && pendingBalance <= 0.0) {
                player.sendMessage("§7Your collection box is empty.")
                return@collectAll
            }
            if (pendingBalance > 0) {
                player.sendMessage("§aCollected $pendingBalance from pending sales.")
            }
            val leftover = mutableListOf<ItemStack>()
            items.forEach { item ->
                val remaining = player.inventory.addItem(item)
                leftover.addAll(remaining.values)
            }
            if (leftover.isNotEmpty()) {
                leftover.forEach { player.world.dropItemNaturally(player.location, it) }
                player.sendMessage("§eYour inventory was full - some items were dropped at your feet.")
            } else if (items.isNotEmpty()) {
                player.sendMessage("§aCollected ${items.size} item(s) from your collection box.")
            }
        }
    }
}
