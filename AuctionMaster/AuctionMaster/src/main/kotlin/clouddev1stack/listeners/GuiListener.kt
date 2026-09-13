package clouddev1stack.listeners

import clouddev1stack.AuctionMaster
import clouddev1stack.gui.AuctionGUI
import clouddev1stack.gui.AuctionGuiHolder
import clouddev1stack.gui.BuyConfirmGUI
import clouddev1stack.gui.BuyConfirmGuiHolder
import clouddev1stack.gui.CollectionGuiHolder
import clouddev1stack.gui.PreviewGUI
import clouddev1stack.gui.PreviewGuiHolder
import clouddev1stack.gui.SellConfirmGuiHolder
import clouddev1stack.gui.SellFlowGUI
import clouddev1stack.gui.SellInsertGuiHolder
import clouddev1stack.gui.YourListingsGuiHolder
import clouddev1stack.manager.AuctionResult
import clouddev1stack.util.ContainerPreview
import clouddev1stack.util.ItemSerialization
import io.papermc.paper.event.player.AsyncChatEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent

class GuiListener(private val plugin: AuctionMaster) : Listener {

    private val pendingBidInput = ConcurrentHashMap<UUID, UUID>()

    private val pendingSellInput = ConcurrentHashMap<UUID, PendingSell>()

    private val confirmedSales = java.util.Collections.newSetFromMap(ConcurrentHashMap<UUID, Boolean>())

    private data class PendingSell(val itemData: String, val quantity: Int)

    @EventHandler(priority = EventPriority.HIGH)
    fun onClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder
        val player = event.whoClicked as? Player ?: return

        when (holder) {
            is AuctionGuiHolder -> handleAuctionGuiClick(event, player, holder)
            is BuyConfirmGuiHolder -> handleBuyConfirmClick(event, player, holder)
            is SellInsertGuiHolder -> handleSellInsertClick(event, player)
            is SellConfirmGuiHolder -> handleSellConfirmClick(event, player, holder)
            is YourListingsGuiHolder -> handleYourListingsClick(event, player, holder)
            is PreviewGuiHolder -> event.isCancelled = true
            is CollectionGuiHolder -> {
                if (event.rawSlot < event.inventory.size && event.action.name.contains("PLACE")) {
                    event.isCancelled = true
                }
            }
            else -> {}
        }
    }

    private fun handleAuctionGuiClick(event: InventoryClickEvent, player: Player, holder: AuctionGuiHolder) {
        event.isCancelled = true
        val slot = event.rawSlot
        when (slot) {
            AuctionGUI.NEXT_PAGE_SLOT -> plugin.guiService.openAuctionHouse(player, holder.page + 1)
            AuctionGUI.PREV_PAGE_SLOT -> plugin.guiService.openAuctionHouse(player, holder.page - 1)
            AuctionGUI.CLOSE_SLOT -> player.closeInventory()
            else -> {
                val listingId = holder.listingIds.getOrNull(slot) ?: return
                val clickedItem = event.currentItem ?: return

                if (event.click == ClickType.SHIFT_LEFT || event.click == ClickType.SHIFT_RIGHT) {
                    if (ContainerPreview.isPreviewable(clickedItem)) {
                        val contents = ContainerPreview.previewContents(clickedItem)
                        player.openInventory(PreviewGUI.build(contents, clickedItem.type.name.lowercase().replace('_', ' ')))
                    }
                    return
                }

                when (event.click) {
                    ClickType.RIGHT -> {
                        pendingBidInput[player.uniqueId] = listingId
                        player.closeInventory()
                        player.sendMessage("§eType your bid amount in chat, or 'cancel' to abort.")
                    }
                    else -> {
                        val listing = plugin.listingsCache.get(listingId)
                        if (listing == null) {
                            player.sendMessage("§cThat listing is no longer available.")
                            plugin.guiService.openAuctionHouse(player, holder.page)
                            return
                        }
                        if (plugin.preferencesCache.quickBuy(player.uniqueId)) {
                            plugin.auctionManager.buyNow(player, listingId) { result ->
                                messageResult(player, result)
                                if (result is AuctionResult.Success) plugin.guiService.openAuctionHouse(player, holder.page)
                            }
                        } else if (listing.buyNowPrice != null) {
                            player.openInventory(BuyConfirmGUI.build(listing))
                        } else {
                            player.sendMessage("§cThis listing has no buy-now price - right-click to bid instead.")
                        }
                    }
                }
            }
        }
    }

    private fun handleBuyConfirmClick(event: InventoryClickEvent, player: Player, holder: BuyConfirmGuiHolder) {
        event.isCancelled = true
        when (event.rawSlot) {
            BuyConfirmGUI.CONFIRM_SLOT -> {
                plugin.auctionManager.buyNow(player, holder.listingId) { result ->
                    messageResult(player, result)
                    player.closeInventory()
                }
            }
            BuyConfirmGUI.CANCEL_SLOT -> player.closeInventory()
        }
    }

    private fun handleSellInsertClick(event: InventoryClickEvent, player: Player) {
        val slot = event.rawSlot
        if (slot >= event.inventory.size) return

        if (slot != SellInsertGuiHolder.ITEM_SLOT) event.isCancelled = true

        when (slot) {
            SellInsertGuiHolder.CONFIRM_SLOT -> {
                val item = event.inventory.getItem(SellInsertGuiHolder.ITEM_SLOT)
                if (item == null || item.type.isAir) {
                    player.sendMessage("§cPlace an item in the slot first."); return
                }
                pendingSellInput[player.uniqueId] = PendingSell(ItemSerialization.serialize(item), item.amount)
                event.inventory.setItem(SellInsertGuiHolder.ITEM_SLOT, null)
                player.closeInventory()
                player.sendMessage("§eType a price in chat: §f<buyNowPrice> [startingBid]")
                player.sendMessage("§7Examples: §f1000 §7(buy-now only)  §f0 500 §7(bid only)  §f1000 500 §7(both)")
            }
            SellInsertGuiHolder.CANCEL_SLOT -> {
                val item = event.inventory.getItem(SellInsertGuiHolder.ITEM_SLOT)
                if (item != null && !item.type.isAir) {
                    returnOrDrop(player, item)
                    event.inventory.setItem(SellInsertGuiHolder.ITEM_SLOT, null)
                }
                player.closeInventory()
            }
        }
    }

    private fun handleSellConfirmClick(event: InventoryClickEvent, player: Player, holder: SellConfirmGuiHolder) {
        event.isCancelled = true
        when (event.rawSlot) {
            SellConfirmGuiHolder.CONFIRM_SLOT -> {
                plugin.auctionManager.createListingFromData(
                    player, holder.itemData, holder.quantity, holder.buyNowPrice, holder.startingBid
                ) { result ->
                    messageResult(player, result)
                    confirmedSales.add(player.uniqueId)
                    player.closeInventory()
                }
            }
            SellConfirmGuiHolder.CANCEL_SLOT -> {
                returnOrDrop(player, ItemSerialization.deserializeSafe(holder.itemData).also { it.amount = holder.quantity })
                confirmedSales.add(player.uniqueId)
                player.closeInventory()
            }
        }
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder
        val player = event.player as? Player ?: return
        if (holder is SellConfirmGuiHolder) {
            if (confirmedSales.remove(player.uniqueId)) return
            returnOrDrop(player, ItemSerialization.deserializeSafe(holder.itemData).also { it.amount = holder.quantity })
        }
        if (holder is SellInsertGuiHolder) {
            val leftover = event.inventory.getItem(SellInsertGuiHolder.ITEM_SLOT)
            if (leftover != null && !leftover.type.isAir) returnOrDrop(player, leftover)
        }
    }

    private fun returnOrDrop(player: Player, item: org.bukkit.inventory.ItemStack) {
        val remaining = player.inventory.addItem(item)
        remaining.values.forEach { player.world.dropItemNaturally(player.location, it) }
    }

    private fun handleYourListingsClick(event: InventoryClickEvent, player: Player, holder: YourListingsGuiHolder) {
        event.isCancelled = true
        val listingId = holder.listingIds.getOrNull(event.rawSlot) ?: return
        plugin.auctionManager.cancelListing(player, listingId) { result ->
            messageResult(player, result)
            if (result is AuctionResult.Success) plugin.guiService.openYourListings(player)
        }
    }

    @EventHandler
    fun onChat(event: AsyncChatEvent) {
        val uuid = event.player.uniqueId
        if (!pendingBidInput.containsKey(uuid) && !pendingSellInput.containsKey(uuid)) return

        event.isCancelled = true
        val message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim()

        if (pendingBidInput.containsKey(uuid)) {
            handleBidChat(event.player, message)
        } else {
            handleSellPriceChat(event.player, message)
        }
    }

    private fun handleBidChat(player: Player, message: String) {
        val listingId = pendingBidInput.remove(player.uniqueId) ?: return

        if (message.equals("cancel", ignoreCase = true)) {
            player.sendMessage("§7Bid cancelled.")
            return
        }
        val amount = message.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            player.sendMessage("§cThat isn't a valid bid amount.")
            return
        }
        plugin.server.scheduler.runTask(plugin, Runnable {
            plugin.auctionManager.placeBid(player, listingId, amount) { result -> messageResult(player, result) }
        })
    }

    private fun handleSellPriceChat(player: Player, message: String) {
        val pending = pendingSellInput.remove(player.uniqueId) ?: return

        if (message.equals("cancel", ignoreCase = true)) {
            player.sendMessage("§7Listing cancelled.")
            plugin.server.scheduler.runTask(plugin, Runnable {
                returnOrDrop(player, ItemSerialization.deserializeSafe(pending.itemData).also { it.amount = pending.quantity })
            })
            return
        }

        val parts = message.split(" ").filter { it.isNotBlank() }
        val buyNow = parts.getOrNull(0)?.toDoubleOrNull()?.takeIf { it > 0 }
        val startBid = parts.getOrNull(1)?.toDoubleOrNull()?.takeIf { it > 0 }

        if (buyNow == null && startBid == null) {
            player.sendMessage("§cInvalid input. Type a price, e.g. '1000' or '1000 500'. Type 'cancel' to abort.")
            pendingSellInput[player.uniqueId] = pending
            return
        }

        plugin.server.scheduler.runTask(plugin, Runnable {
            val inv = SellFlowGUI.buildConfirm(pending.itemData, pending.quantity, buyNow, startBid)
            player.openInventory(inv)
        })
    }

    private fun messageResult(player: Player, result: AuctionResult) {
        when (result) {
            is AuctionResult.Success -> player.sendMessage("§a${result.message}")
            is AuctionResult.Failure -> player.sendMessage("§c${result.reason}")
        }
    }
}
