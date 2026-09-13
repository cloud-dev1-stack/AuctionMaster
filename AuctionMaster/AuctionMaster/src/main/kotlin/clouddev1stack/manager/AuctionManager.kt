package clouddev1stack.manager

import clouddev1stack.AuctionMaster
import clouddev1stack.discord.DiscordNotifier
import clouddev1stack.economy.VaultEconomy
import clouddev1stack.model.HistoryEntry
import clouddev1stack.model.HistoryType
import clouddev1stack.model.Listing
import clouddev1stack.model.ListingStatus
import clouddev1stack.storage.Storage
import clouddev1stack.util.ItemSerialization
import java.time.Instant
import java.util.UUID
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class AuctionManager(
    private val plugin: AuctionMaster,
    private val storage: Storage,
    private val economy: VaultEconomy
) {
    private val config get() = plugin.config
    private val discordNotifier: DiscordNotifier? by lazy {
        if (config.getBoolean("notifications.discord.enabled")) {
            val url = config.getString("notifications.discord.webhook-url", "") ?: ""
            if (url.isNotBlank()) DiscordNotifier(plugin, url) else null
        } else null
    }

        fun createListing(
        seller: Player,
        item: ItemStack,
        buyNowPrice: Double?,
        startingBid: Double?,
        callback: (AuctionResult) -> Unit
    ) {
        createListingInternal(seller, ItemSerialization.serialize(item), item.amount, buyNowPrice, startingBid, removeFromInventory = item, callback)
    }

        fun createListingFromData(
        seller: Player,
        itemData: String,
        quantity: Int,
        buyNowPrice: Double?,
        startingBid: Double?,
        callback: (AuctionResult) -> Unit
    ) {
        createListingInternal(seller, itemData, quantity, buyNowPrice, startingBid, removeFromInventory = null, callback)
    }

    private fun createListingInternal(
        seller: Player,
        itemData: String,
        quantity: Int,
        buyNowPrice: Double?,
        startingBid: Double?,
        removeFromInventory: ItemStack?,
        callback: (AuctionResult) -> Unit
    ) {
        val minPrice = config.getDouble("economy.minimum-price")
        val maxPrice = config.getDouble("economy.maximum-price")

        if (buyNowPrice == null && startingBid == null) {
            callback(AuctionResult.Failure("You must set a buy-now price, a starting bid, or both.")); return
        }
        buyNowPrice?.let {
            if (it < minPrice || it > maxPrice) {
                callback(AuctionResult.Failure("Buy-now price must be between $minPrice and $maxPrice.")); return
            }
        }
        startingBid?.let {
            if (!config.getBoolean("listings.bidding.enabled")) {
                callback(AuctionResult.Failure("Bidding auctions are disabled on this server.")); return
            }
            if (it < config.getDouble("listings.bidding.minimum-starting-bid")) {
                callback(AuctionResult.Failure("Starting bid is below the minimum allowed.")); return
            }
        }

        runAsync {
            val activeCount = storage.getListingsBySeller(seller.uniqueId, ListingStatus.ACTIVE).size
            val limit = plugin.guiService.calculateUnlockedSlots(seller)
            if (activeCount >= limit) {
                runSync { callback(AuctionResult.Failure("You already have $limit active listings (your current max - higher tiers unlock more slots).")) }
                return@runAsync
            }

            val durationHours = config.getInt("listings.default-duration-hours")
            val listing = Listing(
                id = UUID.randomUUID(),
                sellerUuid = seller.uniqueId,
                sellerName = seller.name,
                itemData = itemData,
                quantity = quantity,
                buyNowPrice = buyNowPrice,
                startingBid = startingBid,
                currentBid = null,
                currentBidderUuid = null,
                currentBidderName = null,
                createdAt = Instant.now(),
                expiresAt = Instant.now().plusSeconds(durationHours * 3600L),
                status = ListingStatus.ACTIVE,
                originServerId = config.getString("network.server-id", "server-1") ?: "server-1"
            )
            storage.saveListing(listing)
            plugin.redisSync?.publishNewListing(seller.name, "item x$quantity")
            plugin.listingsCache.invalidate()

            runSync {
                if (removeFromInventory != null) seller.inventory.removeItem(removeFromInventory)
                callback(AuctionResult.Success("Your item was listed on the auction house."))
            }
        }
    }

    fun buyNow(buyer: Player, listingId: UUID, callback: (AuctionResult) -> Unit) {
        runAsync {
            val listing = storage.getListing(listingId)
            if (listing == null || listing.status != ListingStatus.ACTIVE) {
                runSync { callback(AuctionResult.Failure("That listing is no longer available.")) }
                return@runAsync
            }
            if (listing.locked) {
                runSync { callback(AuctionResult.Failure("This listing is locked by an admin and can't be purchased right now.")) }
                return@runAsync
            }
            val price = listing.buyNowPrice
            if (price == null) {
                runSync { callback(AuctionResult.Failure("This listing does not have a buy-now price - place a bid instead.")) }
                return@runAsync
            }
            if (listing.sellerUuid == buyer.uniqueId) {
                runSync { callback(AuctionResult.Failure("You can't buy your own listing.")) }
                return@runAsync
            }
            if (!economy.isAvailable) {
                runSync { callback(AuctionResult.Failure("Economy is unavailable right now, try again later.")) }
                return@runAsync
            }

            runSync {
                if (!economy.has(buyer, price)) {
                    callback(AuctionResult.Failure("You don't have enough money. This costs $price."))
                    return@runSync
                }
                if (!economy.withdraw(buyer, price)) {
                    callback(AuctionResult.Failure("Payment failed, please try again.")); return@runSync
                }

                runAsync {
                    finalizeSale(listing, buyer.uniqueId, buyer.name, price, HistoryType.SOLD_BUY_NOW)
                    runSync {
                        buyer.inventory.addItem(ItemSerialization.deserializeSafe(listing.itemData))
                        callback(AuctionResult.Success("Purchased for $price."))
                    }
                }
            }
        }
    }

    fun placeBid(bidder: Player, listingId: UUID, amount: Double, callback: (AuctionResult) -> Unit) {
        runAsync {
            val listing = storage.getListing(listingId)
            if (listing == null || listing.status != ListingStatus.ACTIVE) {
                runSync { callback(AuctionResult.Failure("That auction is no longer active.")) }
                return@runAsync
            }
            if (!listing.isBiddable) {
                runSync { callback(AuctionResult.Failure("This listing does not accept bids.")) }
                return@runAsync
            }
            if (listing.locked) {
                runSync { callback(AuctionResult.Failure("This listing is locked by an admin and can't accept bids right now.")) }
                return@runAsync
            }
            if (listing.sellerUuid == bidder.uniqueId) {
                runSync { callback(AuctionResult.Failure("You can't bid on your own auction.")) }
                return@runAsync
            }
            val incrementPercent = config.getDouble("listings.bidding.minimum-increment-percent")
            val minimumBid = listing.minimumNextBid(incrementPercent)
            if (amount < minimumBid) {
                runSync { callback(AuctionResult.Failure("Minimum bid is $minimumBid.")) }
                return@runAsync
            }
            if (listing.buyNowPrice != null && amount >= listing.buyNowPrice) {
                runSync { buyNow(bidder, listingId, callback) }
                return@runAsync
            }

            runSync {
                if (!economy.isAvailable) {
                    callback(AuctionResult.Failure("Economy is unavailable right now.")); return@runSync
                }
                if (!economy.has(bidder, amount)) {
                    callback(AuctionResult.Failure("You don't have enough money for that bid.")); return@runSync
                }
                if (!economy.withdraw(bidder, amount)) {
                    callback(AuctionResult.Failure("Payment failed, please try again.")); return@runSync
                }

                val previousBidderUuid = listing.currentBidderUuid
                val previousBidAmount = listing.currentBid

                runAsync {
                    if (previousBidderUuid != null && previousBidAmount != null) {
                        val previousBidder: OfflinePlayer = Bukkit.getOfflinePlayer(previousBidderUuid)
                        runSync {
                            val refunded = if (previousBidder.isOnline) economy.deposit(previousBidder, previousBidAmount) else false
                            if (!refunded) {
                                runAsync { storage.addPendingBalance(previousBidderUuid, previousBidAmount) }
                            }
                        }
                    }

                    listing.currentBid = amount
                    listing.currentBidderUuid = bidder.uniqueId
                    listing.currentBidderName = bidder.name

                    val snipeWindow = config.getLong("listings.bidding.anti-snipe-seconds")
                    val extension = config.getLong("listings.bidding.anti-snipe-extension-seconds")
                    val remaining = java.time.Duration.between(Instant.now(), listing.expiresAt).seconds
                    if (remaining in 0..snipeWindow) {
                        listing.expiresAt = listing.expiresAt.plusSeconds(extension)
                    }

                    storage.updateListing(listing)
                    plugin.listingsCache.invalidate()

                    runSync {
                        callback(AuctionResult.Success("Bid placed: $amount"))
                        val seller = Bukkit.getPlayer(listing.sellerUuid)
                        seller?.sendMessage("§a${bidder.name} bid $amount on your auction!")
                    }
                }
            }
        }
    }

    fun cancelListing(seller: Player, listingId: UUID, callback: (AuctionResult) -> Unit) {
        runAsync {
            val listing = storage.getListing(listingId)
            if (listing == null || listing.status != ListingStatus.ACTIVE) {
                runSync { callback(AuctionResult.Failure("That listing can't be cancelled.")) }
                return@runAsync
            }
            if (listing.sellerUuid != seller.uniqueId && !seller.hasPermission("auctionmaster.admin")) {
                runSync { callback(AuctionResult.Failure("That isn't your listing.")) }
                return@runAsync
            }
            if (listing.currentBidderUuid != null && listing.currentBid != null) {
                val bidder: OfflinePlayer = Bukkit.getOfflinePlayer(listing.currentBidderUuid!!)
                val amount = listing.currentBid!!
                runSync {
                    val refunded = if (bidder.isOnline) economy.deposit(bidder, amount) else false
                    if (!refunded) runAsync { storage.addPendingBalance(listing.currentBidderUuid!!, amount) }
                }
            }
            listing.status = ListingStatus.CANCELLED
            storage.updateListing(listing)
            storage.addCollectionItem(listing.sellerUuid, listing.itemData, listing.quantity, "cancelled")
            storage.deleteListing(listing.id)
            plugin.listingsCache.invalidate()

            runSync { callback(AuctionResult.Success("Listing cancelled, collect your item from the collection box.")) }
        }
    }

    fun setLocked(listingId: UUID, locked: Boolean, callback: (AuctionResult) -> Unit) {
        runAsync {
            val listing = storage.getListing(listingId)
            if (listing == null) {
                runSync { callback(AuctionResult.Failure("Listing not found.")) }
                return@runAsync
            }
            storage.setListingLocked(listingId, locked)
            plugin.listingsCache.invalidate()
            runSync { callback(AuctionResult.Success(if (locked) "Listing locked." else "Listing unlocked.")) }
        }
    }

    fun processExpirations() {
        runAsync {
            val expired = storage.getExpiredActiveListings()
            if (expired.isEmpty()) return@runAsync
            for (listing in expired) {
                if (listing.currentBidderUuid != null && listing.currentBid != null) {
                    finalizeSale(listing, listing.currentBidderUuid!!, listing.currentBidderName ?: "Unknown", listing.currentBid!!, HistoryType.SOLD_BID_WON)
                    storage.addCollectionItem(listing.currentBidderUuid!!, listing.itemData, listing.quantity, "auction-won")
                } else {
                    listing.status = ListingStatus.EXPIRED
                    storage.updateListing(listing)
                    storage.addHistory(
                        HistoryEntry(
                            id = UUID.randomUUID(),
                            listingId = listing.id,
                            sellerUuid = listing.sellerUuid,
                            sellerName = listing.sellerName,
                            buyerUuid = null,
                            buyerName = null,
                            itemData = listing.itemData,
                            quantity = listing.quantity,
                            finalPrice = null,
                            taxPaid = 0.0,
                            type = HistoryType.EXPIRED_RETURNED,
                            timestamp = Instant.now()
                        )
                    )
                    storage.addCollectionItem(listing.sellerUuid, listing.itemData, listing.quantity, "expired")
                    storage.deleteListing(listing.id)
                    plugin.alertsManager.notifyExpired(listing.sellerUuid, itemLabel(listing.itemData), listing.quantity)
                }
            }
            plugin.listingsCache.invalidate()
        }
    }

    private fun finalizeSale(listing: Listing, buyerUuid: UUID, buyerName: String, price: Double, type: HistoryType) {
        val taxPercent = config.getDouble("economy.tax-percent")
        val seller: OfflinePlayer = Bukkit.getOfflinePlayer(listing.sellerUuid)
        val bypassTax = Bukkit.getPlayer(listing.sellerUuid)?.hasPermission("auctionmaster.bypass.tax") ?: false
        val tax = if (bypassTax) 0.0 else price * (taxPercent / 100.0)
        val payout = price - tax

        runSync {
            val paid = if (seller.isOnline) economy.deposit(seller, payout) else false
            if (!paid) runAsync { storage.addPendingBalance(listing.sellerUuid, payout) }
        }

        listing.status = ListingStatus.SOLD
        storage.updateListing(listing)
        storage.addHistory(
            HistoryEntry(
                id = UUID.randomUUID(),
                listingId = listing.id,
                sellerUuid = listing.sellerUuid,
                sellerName = listing.sellerName,
                buyerUuid = buyerUuid,
                buyerName = buyerName,
                itemData = listing.itemData,
                quantity = listing.quantity,
                finalPrice = price,
                taxPaid = tax,
                type = type,
                timestamp = Instant.now()
            )
        )
        storage.deleteListing(listing.id)
        plugin.listingsCache.invalidate()
        plugin.alertsManager.notifySold(listing.sellerUuid, itemLabel(listing.itemData), listing.quantity, payout)

        val broadcastThreshold = config.getDouble("notifications.broadcast-sales-above")
        if (price >= broadcastThreshold) {
            runSync {
                Bukkit.broadcastMessage("§6[Auction] §e$buyerName §7bought an item from §e${listing.sellerName} §7for §a$price")
            }
        }
        val discordMin = config.getDouble("notifications.discord.minimum-price")
        if (price >= discordMin) {
            discordNotifier?.sendSaleAlert(listing.sellerName, buyerName, "item x${listing.quantity}", price)
        }
        plugin.redisSync?.publishSale(listing.sellerName, buyerName, "item x${listing.quantity}", price)
    }

    fun collectAll(player: Player, callback: (List<ItemStack>, Double) -> Unit) {
        runAsync {
            val items = storage.getCollectionItems(player.uniqueId)
            val pendingBalance = storage.getPendingBalance(player.uniqueId)
            val stacks = items.map { ItemSerialization.deserializeSafe(it.itemData) }
            items.forEach { storage.removeCollectionItem(it.id) }
            if (pendingBalance > 0) storage.clearPendingBalance(player.uniqueId)

            runSync {
                if (pendingBalance > 0) economy.deposit(player, pendingBalance)
                callback(stacks, pendingBalance)
            }
        }
    }

    private fun itemLabel(itemData: String): String =
        ItemSerialization.deserializeSafe(itemData).type.name.lowercase().replace('_', ' ')

    private fun runAsync(task: () -> Unit) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable(task))
    }

    private fun runSync(task: () -> Unit) {
        Bukkit.getScheduler().runTask(plugin, Runnable(task))
    }
}
