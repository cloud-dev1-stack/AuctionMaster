package clouddev1stack.model

import java.time.Instant
import java.util.UUID

data class Listing(
    val id: UUID,
    val sellerUuid: UUID,
    val sellerName: String,
    val itemData: String,
    val quantity: Int,
    val buyNowPrice: Double?,
    val startingBid: Double?,
    var currentBid: Double?,
    var currentBidderUuid: UUID?,
    var currentBidderName: String?,
    val createdAt: Instant,
    var expiresAt: Instant,
    var status: ListingStatus,
    val originServerId: String,
    var locked: Boolean = false
) {
    val isBiddable: Boolean get() = startingBid != null

    val isExpired: Boolean get() = status == ListingStatus.ACTIVE && Instant.now().isAfter(expiresAt)

        fun effectiveBuyNowPrice(): Double? = buyNowPrice

        fun minimumNextBid(incrementPercent: Double): Double {
        val base = currentBid ?: startingBid ?: 0.0
        if (currentBid == null) return base
        return base + (base * (incrementPercent / 100.0))
    }
}
