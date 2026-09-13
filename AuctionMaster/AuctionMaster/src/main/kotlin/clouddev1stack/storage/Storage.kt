package clouddev1stack.storage

import clouddev1stack.model.HistoryEntry
import clouddev1stack.model.Listing
import clouddev1stack.model.ListingStatus
import java.util.UUID

interface Storage {

    fun init()

    fun close()

    fun saveListing(listing: Listing)

    fun updateListing(listing: Listing)

    fun deleteListing(id: UUID)

    fun getListing(id: UUID): Listing?

    fun getActiveListings(): List<Listing>

    fun getListingsBySeller(sellerUuid: UUID, status: ListingStatus? = null): List<Listing>

    fun getExpiredActiveListings(): List<Listing>

    fun addHistory(entry: HistoryEntry)

    fun getHistoryForPlayer(playerUuid: UUID, limit: Int = 50): List<HistoryEntry>

    fun pruneHistoryOlderThanDays(days: Int)

        fun addPendingBalance(playerUuid: UUID, amount: Double)

    fun getPendingBalance(playerUuid: UUID): Double

    fun clearPendingBalance(playerUuid: UUID)

        fun addCollectionItem(playerUuid: UUID, itemData: String, quantity: Int, reason: String)

    fun getCollectionItems(playerUuid: UUID): List<CollectionItem>

    fun removeCollectionItem(collectionId: UUID)

        fun setListingLocked(id: UUID, locked: Boolean)

    fun getPreferences(playerUuid: UUID): PlayerPreferences

    fun setQuickBuy(playerUuid: UUID, enabled: Boolean)

    fun setQuickSell(playerUuid: UUID, enabled: Boolean)

        fun getPlayersWithPendingRecovery(): List<UUID>

    fun setAlertsEnabled(playerUuid: UUID, enabled: Boolean)

    fun addPendingAlert(playerUuid: UUID, type: AlertType, itemData: String, quantity: Int, price: Double?)

    fun getPendingAlerts(playerUuid: UUID): List<PendingAlert>

    fun clearPendingAlerts(playerUuid: UUID)
}

enum class AlertType { SOLD, EXPIRED }

data class PendingAlert(
    val id: UUID,
    val playerUuid: UUID,
    val type: AlertType,
    val itemData: String,
    val quantity: Int,
    val price: Double?
)

data class CollectionItem(
    val id: UUID,
    val playerUuid: UUID,
    val itemData: String,
    val quantity: Int,
    val reason: String
)

data class PlayerPreferences(
    val playerUuid: UUID,
    val quickBuy: Boolean,
    val quickSell: Boolean,
    val alertsEnabled: Boolean = false
)
