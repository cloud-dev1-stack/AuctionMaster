package clouddev1stack.discord

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import org.bukkit.plugin.java.JavaPlugin

class DiscordNotifier(private val plugin: JavaPlugin, private val webhookUrl: String) {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    fun sendSaleAlert(sellerName: String, buyerName: String, itemDescription: String, price: Double) {
        if (webhookUrl.isBlank()) return
        val content = buildString {
            append("💰 **")
            append(escape(buyerName))
            append("** bought **")
            append(escape(itemDescription))
            append("** from **")
            append(escape(sellerName))
            append("** for **")
            append(String.format("%,.2f", price))
            append("**")
        }
        sendAsync("""{"content":"${jsonEscape(content)}"}""")
    }

    private fun sendAsync(jsonBody: String) {
        runCatching {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build()
            client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .exceptionally { error ->
                    plugin.logger.warning("Failed to send Discord webhook: ${error.message}")
                    null
                }
        }.onFailure {
            plugin.logger.warning("Failed to build Discord webhook request: ${it.message}")
        }
    }

    private fun escape(text: String): String = text.replace("*", "\\*").replace("_", "\\_")

    private fun jsonEscape(text: String): String = text.replace("\\", "\\\\").replace("\"", "\\\"")
}
