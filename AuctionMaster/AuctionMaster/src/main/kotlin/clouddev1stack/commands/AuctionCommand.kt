package clouddev1stack.commands

import clouddev1stack.AuctionMaster
import clouddev1stack.manager.AuctionResult
import clouddev1stack.util.ItemSerialization
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class AuctionCommand(private val plugin: AuctionMaster) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Only players can use this command.")
            return true
        }
        if (args.isEmpty()) {
            plugin.guiService.openAuctionHouse(sender, 0)
            return true
        }

        when (args[0].lowercase()) {
            "sell" -> handleSell(sender, args)
            "collect" -> plugin.guiService.openCollectionBox(sender)
            "history" -> handleHistory(sender)
            "cancel" -> handleCancel(sender, args)
            "search" -> handleSearch(sender, args)
            "listings" -> plugin.guiService.openYourListings(sender)
            "togglequickbuy" -> plugin.preferencesCache.toggleQuickBuy(sender.uniqueId) { enabled ->
                sender.sendMessage(if (enabled) "§aQuick Buy enabled - buy-now clicks purchase instantly." else "§7Quick Buy disabled - buy-now clicks open a confirmation.")
            }
            "togglequicksell" -> plugin.preferencesCache.toggleQuickSell(sender.uniqueId) { enabled ->
                sender.sendMessage(if (enabled) "§aQuick Sell enabled - /ah sell <price> skips the confirmation GUI." else "§7Quick Sell disabled - /ah sell <price> will still confirm via chat.")
            }
            else -> sender.sendMessage("§cUsage: /ah [sell|search <query>|listings|collect|history|cancel <id>|togglequickbuy|togglequicksell]")
        }
        return true
    }

    private fun handleSell(player: Player, args: Array<out String>) {
        if (!player.hasPermission("auctionmaster.sell")) {
            player.sendMessage("§cYou don't have permission to sell items."); return
        }
        val item = player.inventory.itemInMainHand

        if (args.size < 2) {
            plugin.guiService.openSellInsert(player, if (item.type.isAir) null else item.clone())
            return
        }

        if (item.type.isAir) {
            player.sendMessage("§cHold the item you want to sell in your hand."); return
        }
        val buyNow = args[1].toDoubleOrNull()
        val startingBid = if (args.size >= 3) args[2].toDoubleOrNull() else null
        if (buyNow == null && startingBid == null) {
            player.sendMessage("§cPrices must be numbers."); return
        }

        plugin.auctionManager.createListing(player, item.clone(), buyNow, startingBid) { result ->
            when (result) {
                is AuctionResult.Success -> player.sendMessage("§a${result.message}")
                is AuctionResult.Failure -> player.sendMessage("§c${result.reason}")
            }
        }
    }

    private fun handleSearch(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage("§cUsage: /ah search <item name or seller name>"); return
        }
        val query = args.drop(1).joinToString(" ")
        plugin.guiService.openAuctionHouse(player, 0, query)
    }

    private fun handleCancel(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage("§cUsage: /ah cancel <listingId>"); return
        }
        val id = runCatching { java.util.UUID.fromString(args[1]) }.getOrNull()
        if (id == null) {
            player.sendMessage("§cInvalid listing id."); return
        }
        plugin.auctionManager.cancelListing(player, id) { result ->
            when (result) {
                is AuctionResult.Success -> player.sendMessage("§a${result.message}")
                is AuctionResult.Failure -> player.sendMessage("§c${result.reason}")
            }
        }
    }

    private fun handleHistory(player: Player) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val history = plugin.storage.getHistoryForPlayer(player.uniqueId, 20)
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (history.isEmpty()) {
                    player.sendMessage("§7You have no auction history yet.")
                } else {
                    player.sendMessage("§8=== §6Recent Auction History §8===")
                    history.forEach { entry ->
                        val item = ItemSerialization.deserializeSafe(entry.itemData)
                        val role = if (entry.sellerUuid == player.uniqueId) "Sold" else "Bought"
                        val price = entry.finalPrice?.let { "$it" } ?: "n/a"
                        player.sendMessage("§7[$role] §f${item.type.name} x${entry.quantity} §7- §a$price §7(${entry.type})")
                    }
                }
            })
        })
    }
}
