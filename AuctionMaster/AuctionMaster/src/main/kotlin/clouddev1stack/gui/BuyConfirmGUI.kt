package clouddev1stack.gui

import clouddev1stack.model.Listing
import clouddev1stack.util.ItemSerialization
import java.util.UUID
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

object BuyConfirmGUI {

    const val ITEM_SLOT = 13
    const val CONFIRM_SLOT = 21
    const val CANCEL_SLOT = 23

    fun build(listing: Listing): Inventory {
        val holder = BuyConfirmGuiHolder(listing.id)
        val inv = Bukkit.createInventory(holder, 27, "§8Confirm Purchase")
        holder.attach(inv)

        val display = ItemSerialization.deserializeSafe(listing.itemData)
        inv.setItem(ITEM_SLOT, display)

        val confirm = ItemStack(Material.LIME_STAINED_GLASS_PANE)
        val confirmMeta = confirm.itemMeta
        confirmMeta?.setDisplayName("§aConfirm - Buy for ${listing.buyNowPrice}")
        confirm.itemMeta = confirmMeta
        inv.setItem(CONFIRM_SLOT, confirm)

        val cancel = ItemStack(Material.RED_STAINED_GLASS_PANE)
        val cancelMeta = cancel.itemMeta
        cancelMeta?.setDisplayName("§cCancel")
        cancel.itemMeta = cancelMeta
        inv.setItem(CANCEL_SLOT, cancel)

        return inv
    }
}
