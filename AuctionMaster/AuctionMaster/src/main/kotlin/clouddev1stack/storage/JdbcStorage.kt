package clouddev1stack.storage

import com.zaxxer.hikari.HikariDataSource
import clouddev1stack.model.HistoryEntry
import clouddev1stack.model.HistoryType
import clouddev1stack.model.Listing
import clouddev1stack.model.ListingStatus
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

abstract class JdbcStorage : Storage {

    protected abstract val dataSource: HikariDataSource

    override fun init() {
        dataSource.connection.use { conn ->
            conn.createStatement().use { st ->
                st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS zap_listings (
                        id VARCHAR(36) PRIMARY KEY,
                        seller_uuid VARCHAR(36) NOT NULL,
                        seller_name VARCHAR(64) NOT NULL,
                        item_data TEXT NOT NULL,
                        quantity INTEGER NOT NULL,
                        buy_now_price REAL,
                        starting_bid REAL,
                        current_bid REAL,
                        current_bidder_uuid VARCHAR(36),
                        current_bidder_name VARCHAR(64),
                        created_at BIGINT NOT NULL,
                        expires_at BIGINT NOT NULL,
                        status VARCHAR(16) NOT NULL,
                        origin_server_id VARCHAR(64) NOT NULL,
                        locked INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                runCatching { st.executeUpdate("ALTER TABLE zap_listings ADD COLUMN locked INTEGER NOT NULL DEFAULT 0") }
                st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS zap_history (
                        id VARCHAR(36) PRIMARY KEY,
                        listing_id VARCHAR(36) NOT NULL,
                        seller_uuid VARCHAR(36) NOT NULL,
                        seller_name VARCHAR(64) NOT NULL,
                        buyer_uuid VARCHAR(36),
                        buyer_name VARCHAR(64),
                        item_data TEXT NOT NULL,
                        quantity INTEGER NOT NULL,
                        final_price REAL,
                        tax_paid REAL NOT NULL,
                        type VARCHAR(24) NOT NULL,
                        timestamp BIGINT NOT NULL
                    )
                    """.trimIndent()
                )
                st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS zap_pending_balance (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        amount REAL NOT NULL
                    )
                    """.trimIndent()
                )
                st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS zap_collection_items (
                        id VARCHAR(36) PRIMARY KEY,
                        player_uuid VARCHAR(36) NOT NULL,
                        item_data TEXT NOT NULL,
                        quantity INTEGER NOT NULL,
                        reason VARCHAR(32) NOT NULL
                    )
                    """.trimIndent()
                )
                st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS zap_player_prefs (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        quick_buy INTEGER NOT NULL DEFAULT 0,
                        quick_sell INTEGER NOT NULL DEFAULT 0,
                        alerts_enabled INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                runCatching { st.executeUpdate("ALTER TABLE zap_player_prefs ADD COLUMN alerts_enabled INTEGER NOT NULL DEFAULT 0") }
                st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS zap_pending_alerts (
                        id VARCHAR(36) PRIMARY KEY,
                        player_uuid VARCHAR(36) NOT NULL,
                        type VARCHAR(16) NOT NULL,
                        item_data TEXT NOT NULL,
                        quantity INTEGER NOT NULL,
                        price REAL
                    )
                    """.trimIndent()
                )
            }
        }
    }

    override fun close() {
        dataSource.close()
    }

    override fun saveListing(listing: Listing) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO zap_listings
                (id, seller_uuid, seller_name, item_data, quantity, buy_now_price, starting_bid,
                 current_bid, current_bidder_uuid, current_bidder_name, created_at, expires_at, status, origin_server_id, locked)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, listing.id.toString())
                ps.setString(2, listing.sellerUuid.toString())
                ps.setString(3, listing.sellerName)
                ps.setString(4, listing.itemData)
                ps.setInt(5, listing.quantity)
                ps.setNullableDouble(6, listing.buyNowPrice)
                ps.setNullableDouble(7, listing.startingBid)
                ps.setNullableDouble(8, listing.currentBid)
                ps.setString(9, listing.currentBidderUuid?.toString())
                ps.setString(10, listing.currentBidderName)
                ps.setLong(11, listing.createdAt.toEpochMilli())
                ps.setLong(12, listing.expiresAt.toEpochMilli())
                ps.setString(13, listing.status.name)
                ps.setString(14, listing.originServerId)
                ps.setInt(15, if (listing.locked) 1 else 0)
                ps.executeUpdate()
            }
        }
    }

    override fun updateListing(listing: Listing) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                UPDATE zap_listings SET
                    current_bid = ?, current_bidder_uuid = ?, current_bidder_name = ?,
                    expires_at = ?, status = ?
                WHERE id = ?
                """.trimIndent()
            ).use { ps ->
                ps.setNullableDouble(1, listing.currentBid)
                ps.setString(2, listing.currentBidderUuid?.toString())
                ps.setString(3, listing.currentBidderName)
                ps.setLong(4, listing.expiresAt.toEpochMilli())
                ps.setString(5, listing.status.name)
                ps.setString(6, listing.id.toString())
                ps.executeUpdate()
            }
        }
    }

    override fun setListingLocked(id: UUID, locked: Boolean) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("UPDATE zap_listings SET locked = ? WHERE id = ?").use { ps ->
                ps.setInt(1, if (locked) 1 else 0)
                ps.setString(2, id.toString())
                ps.executeUpdate()
            }
        }
    }

    override fun getPreferences(playerUuid: UUID): PlayerPreferences {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT quick_buy, quick_sell, alerts_enabled FROM zap_player_prefs WHERE player_uuid = ?").use { ps ->
                ps.setString(1, playerUuid.toString())
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        return PlayerPreferences(
                            playerUuid,
                            rs.getInt("quick_buy") == 1,
                            rs.getInt("quick_sell") == 1,
                            runCatching { rs.getInt("alerts_enabled") == 1 }.getOrDefault(false)
                        )
                    }
                }
            }
        }
        return PlayerPreferences(playerUuid, quickBuy = false, quickSell = false, alertsEnabled = false)
    }

    override fun setQuickBuy(playerUuid: UUID, enabled: Boolean) = upsertPref(playerUuid) { current ->
        current.copy(quickBuy = enabled)
    }

    override fun setQuickSell(playerUuid: UUID, enabled: Boolean) = upsertPref(playerUuid) { current ->
        current.copy(quickSell = enabled)
    }

    override fun setAlertsEnabled(playerUuid: UUID, enabled: Boolean) = upsertPref(playerUuid) { current ->
        current.copy(alertsEnabled = enabled)
    }

    private fun upsertPref(playerUuid: UUID, mutate: (PlayerPreferences) -> PlayerPreferences) {
        val updated = mutate(getPreferences(playerUuid))
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM zap_player_prefs WHERE player_uuid = ?").use { ps ->
                ps.setString(1, playerUuid.toString())
                ps.executeUpdate()
            }
            conn.prepareStatement(
                "INSERT INTO zap_player_prefs (player_uuid, quick_buy, quick_sell, alerts_enabled) VALUES (?, ?, ?, ?)"
            ).use { ps ->
                ps.setString(1, playerUuid.toString())
                ps.setInt(2, if (updated.quickBuy) 1 else 0)
                ps.setInt(3, if (updated.quickSell) 1 else 0)
                ps.setInt(4, if (updated.alertsEnabled) 1 else 0)
                ps.executeUpdate()
            }
        }
    }

    override fun addPendingAlert(playerUuid: UUID, type: AlertType, itemData: String, quantity: Int, price: Double?) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO zap_pending_alerts (id, player_uuid, type, item_data, quantity, price) VALUES (?, ?, ?, ?, ?, ?)"
            ).use { ps ->
                ps.setString(1, UUID.randomUUID().toString())
                ps.setString(2, playerUuid.toString())
                ps.setString(3, type.name)
                ps.setString(4, itemData)
                ps.setInt(5, quantity)
                if (price == null) ps.setNull(6, java.sql.Types.REAL) else ps.setDouble(6, price)
                ps.executeUpdate()
            }
        }
    }

    override fun getPendingAlerts(playerUuid: UUID): List<PendingAlert> {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT * FROM zap_pending_alerts WHERE player_uuid = ?").use { ps ->
                ps.setString(1, playerUuid.toString())
                ps.executeQuery().use { rs ->
                    val results = mutableListOf<PendingAlert>()
                    while (rs.next()) {
                        val price = rs.getDouble("price").let { if (rs.wasNull()) null else it }
                        results.add(
                            PendingAlert(
                                id = UUID.fromString(rs.getString("id")),
                                playerUuid = UUID.fromString(rs.getString("player_uuid")),
                                type = AlertType.valueOf(rs.getString("type")),
                                itemData = rs.getString("item_data"),
                                quantity = rs.getInt("quantity"),
                                price = price
                            )
                        )
                    }
                    return results
                }
            }
        }
    }

    override fun clearPendingAlerts(playerUuid: UUID) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM zap_pending_alerts WHERE player_uuid = ?").use { ps ->
                ps.setString(1, playerUuid.toString())
                ps.executeUpdate()
            }
        }
    }

    override fun getPlayersWithPendingRecovery(): List<UUID> {
        val ids = LinkedHashSet<UUID>()
        dataSource.connection.use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery("SELECT DISTINCT player_uuid FROM zap_pending_balance WHERE amount > 0").use { rs ->
                    while (rs.next()) ids.add(UUID.fromString(rs.getString("player_uuid")))
                }
                st.executeQuery("SELECT DISTINCT player_uuid FROM zap_collection_items").use { rs ->
                    while (rs.next()) ids.add(UUID.fromString(rs.getString("player_uuid")))
                }
            }
        }
        return ids.toList()
    }

    override fun deleteListing(id: UUID) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM zap_listings WHERE id = ?").use { ps ->
                ps.setString(1, id.toString())
                ps.executeUpdate()
            }
        }
    }

    override fun getListing(id: UUID): Listing? {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT * FROM zap_listings WHERE id = ?").use { ps ->
                ps.setString(1, id.toString())
                ps.executeQuery().use { rs ->
                    return if (rs.next()) rs.toListing() else null
                }
            }
        }
    }

    override fun getActiveListings(): List<Listing> {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT * FROM zap_listings WHERE status = ?").use { ps ->
                ps.setString(1, ListingStatus.ACTIVE.name)
                ps.executeQuery().use { rs ->
                    val results = mutableListOf<Listing>()
                    while (rs.next()) results.add(rs.toListing())
                    return results
                }
            }
        }
    }

    override fun getListingsBySeller(sellerUuid: UUID, status: ListingStatus?): List<Listing> {
        val sql = if (status != null)
            "SELECT * FROM zap_listings WHERE seller_uuid = ? AND status = ?"
        else
            "SELECT * FROM zap_listings WHERE seller_uuid = ?"
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, sellerUuid.toString())
                if (status != null) ps.setString(2, status.name)
                ps.executeQuery().use { rs ->
                    val results = mutableListOf<Listing>()
                    while (rs.next()) results.add(rs.toListing())
                    return results
                }
            }
        }
    }

    override fun getExpiredActiveListings(): List<Listing> {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT * FROM zap_listings WHERE status = ? AND expires_at <= ?").use { ps ->
                ps.setString(1, ListingStatus.ACTIVE.name)
                ps.setLong(2, Instant.now().toEpochMilli())
                ps.executeQuery().use { rs ->
                    val results = mutableListOf<Listing>()
                    while (rs.next()) results.add(rs.toListing())
                    return results
                }
            }
        }
    }

    override fun addHistory(entry: HistoryEntry) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO zap_history
                (id, listing_id, seller_uuid, seller_name, buyer_uuid, buyer_name, item_data,
                 quantity, final_price, tax_paid, type, timestamp)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, entry.id.toString())
                ps.setString(2, entry.listingId.toString())
                ps.setString(3, entry.sellerUuid.toString())
                ps.setString(4, entry.sellerName)
                ps.setString(5, entry.buyerUuid?.toString())
                ps.setString(6, entry.buyerName)
                ps.setString(7, entry.itemData)
                ps.setInt(8, entry.quantity)
                ps.setNullableDouble(9, entry.finalPrice)
                ps.setDouble(10, entry.taxPaid)
                ps.setString(11, entry.type.name)
                ps.setLong(12, entry.timestamp.toEpochMilli())
                ps.executeUpdate()
            }
        }
    }

    override fun getHistoryForPlayer(playerUuid: UUID, limit: Int): List<HistoryEntry> {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT * FROM zap_history WHERE seller_uuid = ? OR buyer_uuid = ? ORDER BY timestamp DESC LIMIT ?"
            ).use { ps ->
                ps.setString(1, playerUuid.toString())
                ps.setString(2, playerUuid.toString())
                ps.setInt(3, limit)
                ps.executeQuery().use { rs ->
                    val results = mutableListOf<HistoryEntry>()
                    while (rs.next()) {
                        results.add(
                            HistoryEntry(
                                id = UUID.fromString(rs.getString("id")),
                                listingId = UUID.fromString(rs.getString("listing_id")),
                                sellerUuid = UUID.fromString(rs.getString("seller_uuid")),
                                sellerName = rs.getString("seller_name"),
                                buyerUuid = rs.getString("buyer_uuid")?.let { UUID.fromString(it) },
                                buyerName = rs.getString("buyer_name"),
                                itemData = rs.getString("item_data"),
                                quantity = rs.getInt("quantity"),
                                finalPrice = rs.getNullableDouble("final_price"),
                                taxPaid = rs.getDouble("tax_paid"),
                                type = HistoryType.valueOf(rs.getString("type")),
                                timestamp = Instant.ofEpochMilli(rs.getLong("timestamp"))
                            )
                        )
                    }
                    return results
                }
            }
        }
    }

    override fun pruneHistoryOlderThanDays(days: Int) {
        val cutoff = Instant.now().minusSeconds(days * 86400L).toEpochMilli()
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM zap_history WHERE timestamp < ?").use { ps ->
                ps.setLong(1, cutoff)
                ps.executeUpdate()
            }
        }
    }

    override fun addPendingBalance(playerUuid: UUID, amount: Double) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT amount FROM zap_pending_balance WHERE player_uuid = ?").use { ps ->
                ps.setString(1, playerUuid.toString())
                ps.executeQuery().use { rs ->
                    if (rs.next()) {
                        val newAmount = rs.getDouble("amount") + amount
                        conn.prepareStatement("UPDATE zap_pending_balance SET amount = ? WHERE player_uuid = ?").use { update ->
                            update.setDouble(1, newAmount)
                            update.setString(2, playerUuid.toString())
                            update.executeUpdate()
                        }
                        return
                    }
                }
            }
            conn.prepareStatement("INSERT INTO zap_pending_balance (player_uuid, amount) VALUES (?, ?)").use { ps ->
                ps.setString(1, playerUuid.toString())
                ps.setDouble(2, amount)
                ps.executeUpdate()
            }
        }
    }

    override fun getPendingBalance(playerUuid: UUID): Double {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT amount FROM zap_pending_balance WHERE player_uuid = ?").use { ps ->
                ps.setString(1, playerUuid.toString())
                ps.executeQuery().use { rs ->
                    return if (rs.next()) rs.getDouble("amount") else 0.0
                }
            }
        }
    }

    override fun clearPendingBalance(playerUuid: UUID) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM zap_pending_balance WHERE player_uuid = ?").use { ps ->
                ps.setString(1, playerUuid.toString())
                ps.executeUpdate()
            }
        }
    }

    override fun addCollectionItem(playerUuid: UUID, itemData: String, quantity: Int, reason: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO zap_collection_items (id, player_uuid, item_data, quantity, reason) VALUES (?, ?, ?, ?, ?)"
            ).use { ps ->
                ps.setString(1, UUID.randomUUID().toString())
                ps.setString(2, playerUuid.toString())
                ps.setString(3, itemData)
                ps.setInt(4, quantity)
                ps.setString(5, reason)
                ps.executeUpdate()
            }
        }
    }

    override fun getCollectionItems(playerUuid: UUID): List<CollectionItem> {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT * FROM zap_collection_items WHERE player_uuid = ?").use { ps ->
                ps.setString(1, playerUuid.toString())
                ps.executeQuery().use { rs ->
                    val results = mutableListOf<CollectionItem>()
                    while (rs.next()) {
                        results.add(
                            CollectionItem(
                                id = UUID.fromString(rs.getString("id")),
                                playerUuid = UUID.fromString(rs.getString("player_uuid")),
                                itemData = rs.getString("item_data"),
                                quantity = rs.getInt("quantity"),
                                reason = rs.getString("reason")
                            )
                        )
                    }
                    return results
                }
            }
        }
    }

    override fun removeCollectionItem(collectionId: UUID) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM zap_collection_items WHERE id = ?").use { ps ->
                ps.setString(1, collectionId.toString())
                ps.executeUpdate()
            }
        }
    }

    private fun ResultSet.toListing(): Listing = Listing(
        id = UUID.fromString(getString("id")),
        sellerUuid = UUID.fromString(getString("seller_uuid")),
        sellerName = getString("seller_name"),
        itemData = getString("item_data"),
        quantity = getInt("quantity"),
        buyNowPrice = getNullableDouble("buy_now_price"),
        startingBid = getNullableDouble("starting_bid"),
        currentBid = getNullableDouble("current_bid"),
        currentBidderUuid = getString("current_bidder_uuid")?.let { UUID.fromString(it) },
        currentBidderName = getString("current_bidder_name"),
        createdAt = Instant.ofEpochMilli(getLong("created_at")),
        expiresAt = Instant.ofEpochMilli(getLong("expires_at")),
        status = ListingStatus.valueOf(getString("status")),
        originServerId = getString("origin_server_id"),
        locked = runCatching { getInt("locked") == 1 }.getOrDefault(false)
    )

    private fun ResultSet.getNullableDouble(column: String): Double? {
        val value = getDouble(column)
        return if (wasNull()) null else value
    }

    private fun java.sql.PreparedStatement.setNullableDouble(index: Int, value: Double?) {
        if (value == null) setNull(index, java.sql.Types.REAL) else setDouble(index, value)
    }
}
