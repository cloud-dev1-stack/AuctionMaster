package clouddev1stack.gui

import clouddev1stack.model.Listing
import clouddev1stack.util.ItemSerialization
import clouddev1stack.util.TimeUtil
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

object AuctionGUI {

    const val NEXT_PAGE_SLOT = 53
    const val PREV_PAGE_SLOT = 45
    const val CLOSE_SLOT = 49

    fun build(
        viewer: Player,
        allListings: List<Listing>,
        page: Int,
        itemsPerPage: Int,
        titleTemplate: String,
        rows: Int,
        itemProvider: (Listing) -> ItemStack = { ItemSerialization.deserializeSafe(it.itemData) }
    ): Inventory {
        val holder = AuctionGuiHolder(page)
        val size = rows * 9
        val title = colorize(titleTemplate) + " §7(Page ${page + 1})"
        val inv = Bukkit.createInventory(holder, size, title)
        holder.attach(inv)

        val start = page * itemsPerPage
        val pageListings = allListings.drop(start).take(itemsPerPage)

        for ((index, listing) in pageListings.withIndex()) {
            val display = buildListingIcon(listing, itemProvider(listing))
            inv.setItem(index, display)
            holder.listingIds.add(listing.id)
        }

        if (page > 0) inv.setItem(PREV_PAGE_SLOT, navItem(Material.ARROW, "§ePrevious Page"))
        if (start + itemsPerPage < allListings.size) inv.setItem(NEXT_PAGE_SLOT, navItem(Material.ARROW, "§eNext Page"))
        inv.setItem(CLOSE_SLOT, navItem(Material.BARRIER, "§cClose"))

        return inv
    }

    private fun buildListingIcon(listing: Listing, baseItem: ItemStack): ItemStack {
        val display = baseItem.clone()
        val meta: ItemMeta = display.itemMeta ?: return display
        val lore = mutableListOf<String>()
        lore.add("§7Seller: §f${listing.sellerName}")
        lore.add("")
        if (listing.buyNowPrice != null) lore.add("§aBuy Now: §f${listing.buyNowPrice}")
        if (listing.isBiddable) {
            val currentBid = listing.currentBid ?: listing.startingBid
            lore.add("§6Current Bid: §f$currentBid")
            if (listing.currentBidderName != null) lore.add("§7Highest bidder: §f${listing.currentBidderName}")
        }
        lore.add("")
        lore.add("§7Time left: §f${TimeUtil.humanizeRemaining(listing.expiresAt)}")
        lore.add("")
        if (listing.buyNowPrice != null) lore.add("§eLeft-click to buy now")
        if (listing.isBiddable) lore.add("§eRight-click to place a bid")
        meta.lore = lore
        display.itemMeta = meta
        return display
    }

    private fun navItem(material: Material, name: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta?.setDisplayName(name)
        item.itemMeta = meta
        return item
    }

    private fun colorize(text: String): String = text.replace('&', '\u00A7')
}
