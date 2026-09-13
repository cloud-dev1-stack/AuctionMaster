package clouddev1stack.commands

import clouddev1stack.AuctionMaster
import clouddev1stack.manager.AuctionResult
import clouddev1stack.model.ListingStatus
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class AuctionAdminCommand(private val plugin: AuctionMaster) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("auctionmaster.admin")) {
            sender.sendMessage("§cYou don't have permission."); return true
        }
        if (args.isEmpty()) {
            sender.sendMessage("§cUsage: /ahadmin <reload|remove <id>|cancelall <player>|forceexpire|recovery|status|lock <id>|unlock <id>>")
            return true
        }

        when (args[0].lowercase()) {
            "reload" -> {
                plugin.reloadConfig()
                plugin.messagesConfig.reload()
                plugin.soundsConfig.reload()
                sender.sendMessage("§aConfig, messages, and sounds reloaded. (Storage backend changes require a full restart.)")
            }
            "remove" -> {
                if (args.size < 2) { sender.sendMessage("§cUsage: /ahadmin remove <listingId>"); return true }
                val id = runCatching { java.util.UUID.fromString(args[1]) }.getOrNull()
                if (id == null) { sender.sendMessage("§cInvalid listing id."); return true }
                Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                    val listing = plugin.storage.getListing(id)
                    if (listing == null) {
                        Bukkit.getScheduler().runTask(plugin, Runnable { sender.sendMessage("§cListing not found.") })
                        return@Runnable
                    }
                    plugin.storage.addCollectionItem(listing.sellerUuid, listing.itemData, listing.quantity, "admin-removed")
                    plugin.storage.deleteListing(id)
                    Bukkit.getScheduler().runTask(plugin, Runnable { sender.sendMessage("§aListing removed, item returned to seller's collection box.") })
                })
            }
            "cancelall" -> {
                if (args.size < 2) { sender.sendMessage("§cUsage: /ahadmin cancelall <playerName>"); return true }
                val target = Bukkit.getOfflinePlayer(args[1])
                Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                    val listings = plugin.storage.getListingsBySeller(target.uniqueId, ListingStatus.ACTIVE)
                    listings.forEach {
                        plugin.storage.addCollectionItem(it.sellerUuid, it.itemData, it.quantity, "admin-cancelled")
                        plugin.storage.deleteListing(it.id)
                    }
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        sender.sendMessage("§aCancelled ${listings.size} listing(s) for ${args[1]}.")
                    })
                })
            }
            "forceexpire" -> {
                plugin.auctionManager.processExpirations()
                sender.sendMessage("§aExpiration sweep triggered.")
            }
            "recovery" -> {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                    val playerIds = plugin.storage.getPlayersWithPendingRecovery()
                    val lines = playerIds.map { id ->
                        val name = Bukkit.getOfflinePlayer(id).name ?: id.toString()
                        val balance = plugin.storage.getPendingBalance(id)
                        val items = plugin.storage.getCollectionItems(id).size
                        "§7- §f$name§7: §a$balance §7pending, §e$items §7item(s) waiting"
                    }
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        if (lines.isEmpty()) {
                            sender.sendMessage("§aNobody has anything waiting to be recovered.")
                        } else {
                            sender.sendMessage("§8=== §6Pending Recovery (${lines.size}) §8===")
                            lines.forEach { sender.sendMessage(it) }
                        }
                    })
                })
            }
            "status" -> {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                    val active = plugin.storage.getActiveListings().size
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        sender.sendMessage("§8=== §6AuctionMaster Status §8===")
                        sender.sendMessage("§7Storage backend: §f${plugin.config.getString("storage.type")}")
                        sender.sendMessage("§7Active listings: §f$active")
                        sender.sendMessage("§7Economy (Vault): §f${if (plugin.vaultEconomy.isAvailable) "connected" else "unavailable"}")
                        sender.sendMessage("§7Redis cross-server sync: §f${if (plugin.redisSync?.isConnected == true) "connected" else "disabled/unavailable"}")
                    })
                })
            }
            "lock", "unlock" -> {
                if (args.size < 2) { sender.sendMessage("§cUsage: /ahadmin ${args[0].lowercase()} <listingId>"); return true }
                val id = runCatching { java.util.UUID.fromString(args[1]) }.getOrNull()
                if (id == null) { sender.sendMessage("§cInvalid listing id."); return true }
                plugin.auctionManager.setLocked(id, args[0].equals("lock", ignoreCase = true)) { result ->
                    when (result) {
                        is AuctionResult.Success -> sender.sendMessage("§a${result.message}")
                        is AuctionResult.Failure -> sender.sendMessage("§c${result.reason}")
                    }
                }
            }
            else -> sender.sendMessage("§cUsage: /ahadmin <reload|remove <id>|cancelall <player>|forceexpire|recovery|status|lock <id>|unlock <id>>")
        }
        return true
    }
}
