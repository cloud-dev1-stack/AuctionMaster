package clouddev1stack.manager

import clouddev1stack.AuctionMaster
import clouddev1stack.storage.AlertType
import clouddev1stack.storage.PendingAlert
import java.util.UUID
import org.bukkit.Bukkit
import org.bukkit.entity.Player

class AlertsManager(private val plugin: AuctionMaster) {

    private val config get() = plugin.config
    private val messages get() = plugin.messagesConfig
    private val sounds get() = plugin.soundsConfig

    fun toggle(player: Player) {
        plugin.preferencesCache.toggleAlerts(player.uniqueId) { enabled ->
            if (config.getBoolean("auction-alerts.toggle-feedback.chat-enabled", true)) {
                player.sendMessage(messages.get(if (enabled) "alerts.enabled" else "alerts.disabled"))
            }
            if (config.getBoolean("auction-alerts.toggle-feedback.action-bar-enabled", true)) {
                sendActionBar(player, messages.get(if (enabled) "alerts.enabled-action-bar" else "alerts.disabled-action-bar"))
            }
            sounds.play(player, if (enabled) "alert-toggle-enabled" else "alert-toggle-disabled")
        }
    }

    private fun sendActionBar(player: Player, text: String) {
        player.spigot().sendMessage(
            net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
            net.md_5.bungee.api.chat.TextComponent(text)
        )
    }

    fun notifySold(sellerUuid: UUID, itemDescription: String, quantity: Int, price: Double) {
        if (!config.getBoolean("auction-alerts.includes.listing-sold", true)) return
        if (!plugin.preferencesCache.alertsEnabled(sellerUuid)) return

        val onlinePlayer = Bukkit.getPlayer(sellerUuid)
        if (onlinePlayer != null && onlinePlayer.isOnline) {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                onlinePlayer.sendMessage(
                    messages.get(
                        "alerts.sold-single",
                        mapOf("item" to itemDescription, "quantity" to quantity.toString(), "price" to formatMoney(price))
                    )
                )
                sounds.play(onlinePlayer, "seller-sale-alert")
            })
        } else {
            plugin.storage.addPendingAlert(sellerUuid, AlertType.SOLD, itemDescription, quantity, price)
        }
    }

    fun notifyExpired(sellerUuid: UUID, itemDescription: String, quantity: Int) {
        if (!config.getBoolean("auction-alerts.includes.listing-expired", true)) return
        if (!plugin.preferencesCache.alertsEnabled(sellerUuid)) return

        val onlinePlayer = Bukkit.getPlayer(sellerUuid)
        if (onlinePlayer != null && onlinePlayer.isOnline) {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                onlinePlayer.sendMessage(
                    messages.get("alerts.expired-single", mapOf("item" to itemDescription, "quantity" to quantity.toString()))
                )
            })
        } else {
            plugin.storage.addPendingAlert(sellerUuid, AlertType.EXPIRED, itemDescription, quantity, null)
        }
    }

    fun flushOnJoin(player: Player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val alertsEnabled = plugin.storage.getPreferences(player.uniqueId).alertsEnabled
            val pending = plugin.storage.getPendingAlerts(player.uniqueId)
            if (pending.isEmpty()) return@Runnable
            plugin.storage.clearPendingAlerts(player.uniqueId)
            if (!alertsEnabled) return@Runnable

            val sold = pending.filter { it.type == AlertType.SOLD }
            val expired = pending.filter { it.type == AlertType.EXPIRED }

            Bukkit.getScheduler().runTask(plugin, Runnable {
                deliverSoldSummary(player, sold)
                deliverExpiredSummary(player, expired)
            })
        })
    }

    private fun deliverSoldSummary(player: Player, sold: List<PendingAlert>) {
        if (sold.isEmpty()) return
        val total = sold.sumOf { it.price ?: 0.0 }
        val useSummary = config.getBoolean("auction-alerts.includes.offline-sales-summary", true) && sold.size > 1

        if (useSummary) {
            player.sendMessage(messages.get("alerts.sold-summary", mapOf("count" to sold.size.toString(), "total" to formatMoney(total))))
            if (config.getBoolean("auction-alerts.includes.payout-summary", true)) {
                player.sendMessage(messages.get("alerts.payout-summary", mapOf("total" to formatMoney(total))))
            }
        } else {
            sold.forEach {
                player.sendMessage(
                    messages.get(
                        "alerts.sold-single",
                        mapOf("item" to it.itemData, "quantity" to it.quantity.toString(), "price" to formatMoney(it.price ?: 0.0))
                    )
                )
            }
        }
        sounds.play(player, "seller-sale-alert")
    }

    private fun deliverExpiredSummary(player: Player, expired: List<PendingAlert>) {
        if (expired.isEmpty()) return
        val useSummary = config.getBoolean("auction-alerts.includes.offline-expirations-summary", true) && expired.size > 1

        if (useSummary) {
            player.sendMessage(messages.get("alerts.expired-summary", mapOf("count" to expired.size.toString())))
        } else {
            expired.forEach {
                player.sendMessage(messages.get("alerts.expired-single", mapOf("item" to it.itemData, "quantity" to it.quantity.toString())))
            }
        }
    }

    private fun formatMoney(amount: Double): String = String.format("%,.2f", amount)
}
