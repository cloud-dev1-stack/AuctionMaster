package clouddev1stack

import clouddev1stack.commands.AuctionAdminCommand
import clouddev1stack.commands.AuctionCommand
import clouddev1stack.commands.AuctionTabCompleter
import clouddev1stack.commands.ToggleAlertsCommand
import clouddev1stack.config.MessagesConfig
import clouddev1stack.config.SoundsConfig
import clouddev1stack.economy.VaultEconomy
import clouddev1stack.gui.GuiService
import clouddev1stack.listeners.GuiListener
import clouddev1stack.listeners.PlayerConnectionListener
import clouddev1stack.manager.AlertsManager
import clouddev1stack.manager.AuctionManager
import clouddev1stack.manager.ListingsCache
import clouddev1stack.manager.PreferencesCache
import clouddev1stack.placeholder.AuctionMasterExpansion
import clouddev1stack.redis.RedisSync
import clouddev1stack.storage.MySQLStorage
import clouddev1stack.storage.SQLiteStorage
import clouddev1stack.storage.Storage
import org.bukkit.plugin.java.JavaPlugin

class AuctionMaster : JavaPlugin() {

    lateinit var storage: Storage
        private set
    lateinit var vaultEconomy: VaultEconomy
        private set
    lateinit var auctionManager: AuctionManager
        private set
    lateinit var guiService: GuiService
        private set
    lateinit var preferencesCache: PreferencesCache
        private set
    lateinit var listingsCache: ListingsCache
        private set
    lateinit var alertsManager: AlertsManager
        private set
    lateinit var messagesConfig: MessagesConfig
        private set
    lateinit var soundsConfig: SoundsConfig
        private set
    var redisSync: RedisSync? = null
        private set

    override fun onEnable() {
        saveDefaultConfig()
        messagesConfig = MessagesConfig(this).also { it.load() }
        soundsConfig = SoundsConfig(this).also { it.load() }

        storage = buildStorage()
        storage.init()

        vaultEconomy = VaultEconomy(this)
        vaultEconomy.setup()

        auctionManager = AuctionManager(this, storage, vaultEconomy)
        guiService = GuiService(this)
        preferencesCache = PreferencesCache(this)
        alertsManager = AlertsManager(this)
        listingsCache = ListingsCache(this).also { it.start() }

        if (config.getBoolean("redis.enabled")) {
            redisSync = RedisSync(
                this,
                config.getString("redis.host", "localhost") ?: "localhost",
                config.getInt("redis.port", 6379),
                config.getString("redis.password", "") ?: "",
                config.getString("redis.channel", "auctionmaster:events") ?: "auctionmaster:events"
            ).also { it.start() }
        }

        server.pluginManager.registerEvents(GuiListener(this), this)
        server.pluginManager.registerEvents(PlayerConnectionListener(this), this)

        server.onlinePlayers.forEach { preferencesCache.warm(it.uniqueId) }

        getCommand("ah")?.setExecutor(AuctionCommand(this))
        getCommand("ah")?.tabCompleter = AuctionTabCompleter()
        getCommand("ahadmin")?.setExecutor(AuctionAdminCommand(this))
        getCommand("ahadmin")?.tabCompleter = AuctionTabCompleter()
        getCommand("toggleahalerts")?.setExecutor(ToggleAlertsCommand(this))

        if (server.pluginManager.getPlugin("PlaceholderAPI") != null) {
            AuctionMasterExpansion(this).register()
            logger.info("Hooked into PlaceholderAPI.")
        }

        server.scheduler.runTaskTimer(this, Runnable { auctionManager.processExpirations() }, 20L * 60, 20L * 60)

        val keepDays = config.getInt("history.keep-days")
        server.scheduler.runTaskTimerAsynchronously(this, Runnable { storage.pruneHistoryOlderThanDays(keepDays) }, 20L * 60, 20L * 60 * 60 * 24)

        logger.info("AuctionMaster enabled (storage: ${config.getString("storage.type")}).")
    }

    override fun onDisable() {
        listingsCache.stop()
        redisSync?.stop()
        if (::storage.isInitialized) storage.close()
    }

    private fun buildStorage(): Storage {
        return when (config.getString("storage.type", "sqlite")?.lowercase()) {
            "mysql" -> MySQLStorage(
                host = config.getString("storage.mysql.host", "localhost") ?: "localhost",
                port = config.getInt("storage.mysql.port", 3306),
                database = config.getString("storage.mysql.database", "auctionmaster") ?: "auctionmaster",
                username = config.getString("storage.mysql.username", "root") ?: "root",
                password = config.getString("storage.mysql.password", "") ?: "",
                useSSL = config.getBoolean("storage.mysql.useSSL", false),
                poolSize = config.getInt("storage.mysql.pool-size", 8)
            )
            else -> SQLiteStorage(dataFolder)
        }
    }
}
