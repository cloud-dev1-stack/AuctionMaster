package clouddev1stack.gui

import org.bukkit.Bukkit
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

object CollectionGUI {

    fun build(items: List<ItemStack>, pendingBalance: Double): Inventory {
        val holder = CollectionGuiHolder()
        val size = 54
        val title = "§8Collection Box" + if (pendingBalance > 0) " §a(+$pendingBalance)" else ""
        val inv = Bukkit.createInventory(holder, size, title)
        holder.attach(inv)
        items.take(size).forEach { inv.addItem(it) }
        return inv
    }
}
