package clouddev1stack.gui

import org.bukkit.Bukkit
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

object PreviewGUI {
    fun build(contents: List<ItemStack>, title: String): Inventory {
        val holder = PreviewGuiHolder()
        val size = ((contents.size / 9) + 1).coerceAtMost(6) * 9
        val inv = Bukkit.createInventory(holder, size.coerceAtLeast(9), "§8Preview: §f$title")
        holder.attach(inv)
        contents.take(size).forEachIndexed { index, item -> inv.setItem(index, item) }
        return inv
    }
}
