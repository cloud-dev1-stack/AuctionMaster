package clouddev1stack.gui

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

class AuctionGuiHolder(var page: Int, val listingIds: MutableList<java.util.UUID> = mutableListOf()) : InventoryHolder {
    private lateinit var inv: Inventory
    override fun getInventory(): Inventory = inv
    fun attach(inventory: Inventory) { inv = inventory }
}

class CollectionGuiHolder : InventoryHolder {
    private lateinit var inv: Inventory
    override fun getInventory(): Inventory = inv
    fun attach(inventory: Inventory) { inv = inventory }
}
