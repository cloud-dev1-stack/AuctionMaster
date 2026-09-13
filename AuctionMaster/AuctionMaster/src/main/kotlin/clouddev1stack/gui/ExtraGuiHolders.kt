package clouddev1stack.gui

import java.util.UUID
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

class PreviewGuiHolder : InventoryHolder {
    private lateinit var inv: Inventory
    override fun getInventory(): Inventory = inv
    fun attach(inventory: Inventory) { inv = inventory }
}

class BuyConfirmGuiHolder(val listingId: UUID) : InventoryHolder {
    private lateinit var inv: Inventory
    override fun getInventory(): Inventory = inv
    fun attach(inventory: Inventory) { inv = inventory }
}

class SellInsertGuiHolder : InventoryHolder {
    private lateinit var inv: Inventory
    override fun getInventory(): Inventory = inv
    fun attach(inventory: Inventory) { inv = inventory }
    companion object { const val ITEM_SLOT = 13; const val CONFIRM_SLOT = 22; const val CANCEL_SLOT = 31 }
}

class SellConfirmGuiHolder(
    val itemData: String,
    val quantity: Int,
    val buyNowPrice: Double?,
    val startingBid: Double?
) : InventoryHolder {
    private lateinit var inv: Inventory
    override fun getInventory(): Inventory = inv
    fun attach(inventory: Inventory) { inv = inventory }
    companion object { const val ITEM_SLOT = 13; const val CONFIRM_SLOT = 21; const val CANCEL_SLOT = 23 }
}

class YourListingsGuiHolder(val listingIds: MutableList<UUID?> = mutableListOf()) : InventoryHolder {
    private lateinit var inv: Inventory
    override fun getInventory(): Inventory = inv
    fun attach(inventory: Inventory) { inv = inventory }
}
