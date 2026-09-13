package clouddev1stack.gui

import clouddev1stack.util.ItemSerialization
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

object SellFlowGUI {

    fun buildInsert(prefill: ItemStack?): Inventory {
        val holder = SellInsertGuiHolder()
        val inv = Bukkit.createInventory(holder, 36, "§8Auction | Insert Item")
        holder.attach(inv)
        if (prefill != null && !prefill.type.isAir) inv.setItem(SellInsertGuiHolder.ITEM_SLOT, prefill)

        inv.setItem(SellInsertGuiHolder.CONFIRM_SLOT, glassPane(Material.LIME_STAINED_GLASS_PANE, "§aNext - Set Price"))
        inv.setItem(SellInsertGuiHolder.CANCEL_SLOT, glassPane(Material.RED_STAINED_GLASS_PANE, "§cCancel"))
        return inv
    }

    fun buildConfirm(itemData: String, quantity: Int, buyNowPrice: Double?, startingBid: Double?): Inventory {
        val holder = SellConfirmGuiHolder(itemData, quantity, buyNowPrice, startingBid)
        val inv = Bukkit.createInventory(holder, 27, "§8Auction | Confirm Listing")
        holder.attach(inv)

        val display = ItemSerialization.deserializeSafe(itemData)
        val meta = display.itemMeta
        val lore = mutableListOf<String>()
        if (buyNowPrice != null) lore.add("§aBuy Now: §f$buyNowPrice")
        if (startingBid != null) lore.add("§6Starting Bid: §f$startingBid")
        lore.add("")
        lore.add("§7Click confirm below to list this item.")
        meta?.lore = lore
        display.itemMeta = meta
        inv.setItem(SellConfirmGuiHolder.ITEM_SLOT, display)

        inv.setItem(SellConfirmGuiHolder.CONFIRM_SLOT, glassPane(Material.LIME_STAINED_GLASS_PANE, "§aConfirm Listing"))
        inv.setItem(SellConfirmGuiHolder.CANCEL_SLOT, glassPane(Material.RED_STAINED_GLASS_PANE, "§cCancel"))
        return inv
    }

    private fun glassPane(material: Material, name: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta?.setDisplayName(name)
        item.itemMeta = meta
        return item
    }
}
