package clouddev1stack.redis

import clouddev1stack.AuctionMaster
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.JedisPubSub

class RedisSync(
    private val plugin: AuctionMaster,
    private val host: String,
    private val port: Int,
    private val password: String,
    private val channel: String
) {

    private var pool: JedisPool? = null
    private var subscriberThread: Thread? = null
    private var pubSub: JedisPubSub? = null

    val isConnected: Boolean get() = pool != null && pool?.isClosed == false

    fun start() {
        runCatching {
            val poolConfig = JedisPoolConfig().apply {
                maxTotal = 4
                testOnBorrow = true
            }
            pool = if (password.isBlank()) JedisPool(poolConfig, host, port) else JedisPool(poolConfig, host, port, 2000, password)

            pubSub = object : JedisPubSub() {
                override fun onMessage(subscribedChannel: String, message: String) {
                    handleIncoming(message)
                }
            }

            subscriberThread = Thread({
                runCatching {
                    pool?.resource?.use { jedis -> jedis.subscribe(pubSub, channel) }
                }.onFailure { plugin.logger.warning("Redis subscriber stopped: ${it.message}") }
            }, "AuctionMaster-Redis-Subscriber").apply {
                isDaemon = true
                start()
            }

            plugin.logger.info("Connected to Redis for cross-server auction sync.")
        }.onFailure {
            plugin.logger.warning("Could not connect to Redis (${it.message}) - cross-server sync will rely on MySQL polling only.")
            pool = null
        }
    }

    fun stop() {
        runCatching { pubSub?.unsubscribe() }
        runCatching { subscriberThread?.interrupt() }
        runCatching { pool?.close() }
    }

    fun publishSale(sellerName: String, buyerName: String, itemDescription: String, price: Double) {
        publish("SALE", "seller=${escape(sellerName)}|buyer=${escape(buyerName)}|item=${escape(itemDescription)}|price=$price")
    }

    fun publishNewListing(sellerName: String, itemDescription: String) {
        publish("NEW_LISTING", "seller=${escape(sellerName)}|item=${escape(itemDescription)}")
    }

    private fun publish(type: String, payload: String) {
        val pool = this.pool ?: return
        runCatching {
            pool.resource.use { jedis ->
                jedis.publish(channel, "server=${plugin.config.getString("network.server-id", "server-1")}|type=$type|$payload")
            }
        }.onFailure { plugin.logger.warning("Failed to publish Redis auction event: ${it.message}") }
    }

    private fun handleIncoming(message: String) {
        val fields = message.split("|").mapNotNull { part ->
            val split = part.split("=", limit = 2)
            if (split.size == 2) split[0] to unescape(split[1]) else null
        }.toMap()

        val originServer = fields["server"] ?: return
        if (originServer == plugin.config.getString("network.server-id", "server-1")) return

        if (fields["type"] == "SALE" && plugin.config.getBoolean("redis.broadcast-cross-server-sales", true)) {
            val price = fields["price"]?.toDoubleOrNull() ?: return
            val threshold = plugin.config.getDouble("notifications.broadcast-sales-above")
            if (price >= threshold) {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    plugin.server.broadcastMessage(
                        "§6[Auction - $originServer] §e${fields["buyer"]} §7bought an item from §e${fields["seller"]} §7for §a$price"
                    )
                })
            }
        }
    }

    private fun escape(text: String): String = text.replace("|", "&#124;").replace("=", "&#61;")
    private fun unescape(text: String): String = text.replace("&#124;", "|").replace("&#61;", "=")
}
