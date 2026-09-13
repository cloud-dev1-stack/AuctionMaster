package clouddev1stack.gui

import clouddev1stack.model.Listing
import clouddev1stack.util.ItemSerialization
import clouddev1stack.util.TimeUtil
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

object YourListingsGUI {

    fun build(
        player: Player,
        listings: List<Listing>,
        unlockedSlots: Int,
        itemProvider: (Listing) -> ItemStack = { ItemSerialization.deserializeSafe(it.itemData) }
    ): Inventory {
        val holder = YourListingsGuiHolder()
        val size = 54
        val inv = Bukkit.createInventory(holder, size, "§8Auction | Your Listings")
        holder.attach(inv)

        for (slot in 0 until size) {
            val listing = listings.getOrNull(slot)
            if (listing != null) {
                inv.setItem(slot, buildListingIcon(listing, itemProvider(listing)))
                holder.listingIds.add(listing.id)
            } else if (slot >= unlockedSlots) {
                inv.setItem(slot, lockedSlotIcon())
                holder.listingIds.add(null)
            } else {
                holder.listingIds.add(null)
            }
        }
        return inv
    }

    private fun buildListingIcon(listing: Listing, baseItem: ItemStack): ItemStack {
        val display = baseItem.clone()
        val meta = display.itemMeta ?: return display
        val lore = mutableListOf<String>()
        if (listing.buyNowPrice != null) lore.add("§aBuy Now: §f${listing.buyNowPrice}")
        if (listing.isBiddable) {
            lore.add("§6Current Bid: §f${listing.currentBid ?: listing.startingBid}")
            if (listing.currentBidderName != null) lore.add("§7Bidder: §f${listing.currentBidderName}")
        }
        if (listing.locked) lore.add("§c[Locked by admin]")
        lore.add("§7Time left: §f${TimeUtil.humanizeRemaining(listing.expiresAt)}")
        lore.add("")
        lore.add("§eClick to cancel this listing")
        meta.lore = lore
        display.itemMeta = meta
        return display
    }

    private fun lockedSlotIcon(): ItemStack {
        val item = ItemStack(Material.BARRIER)
        val meta = item.itemMeta
        meta?.setDisplayName("§cLocked Slot")
        meta?.lore = listOf(
            "§7This auction slot is locked.",
            "§7Unlock more slots with a",
            "§7higher rank or permission."
        )
        item.itemMeta = meta
        return item
    }
}
