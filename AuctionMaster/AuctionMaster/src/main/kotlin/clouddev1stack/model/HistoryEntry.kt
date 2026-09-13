package clouddev1stack.model

import java.time.Instant
import java.util.UUID

enum class HistoryType { SOLD_BUY_NOW, SOLD_BID_WON, EXPIRED_RETURNED, CANCELLED }

data class HistoryEntry(
    val id: UUID,
    val listingId: UUID,
    val sellerUuid: UUID,
    val sellerName: String,
    val buyerUuid: UUID?,
    val buyerName: String?,
    val itemData: String,
    val quantity: Int,
    val finalPrice: Double?,
    val taxPaid: Double,
    val type: HistoryType,
    val timestamp: Instant
)
