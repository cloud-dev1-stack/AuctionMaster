package clouddev1stack.economy

import net.milkbowl.vault.economy.Economy
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.RegisteredServiceProvider
import org.bukkit.plugin.java.JavaPlugin

class VaultEconomy(private val plugin: JavaPlugin) {

    var economy: Economy? = null
        private set

    val isAvailable: Boolean get() = economy != null

    fun setup(): Boolean {
        if (plugin.server.pluginManager.getPlugin("Vault") == null) {
            plugin.logger.warning("Vault not found - economy features (buying/selling) are disabled until it is installed.")
            return false
        }
        val rsp: RegisteredServiceProvider<Economy> =
            plugin.server.servicesManager.getRegistration(Economy::class.java) ?: run {
                plugin.logger.warning("No economy plugin registered with Vault (e.g. EssentialsX Economy) - economy features are disabled.")
                return false
            }
        economy = rsp.provider
        return true
    }

    fun balance(player: OfflinePlayer): Double = economy?.getBalance(player) ?: 0.0

    fun has(player: OfflinePlayer, amount: Double): Boolean = economy?.has(player, amount) ?: false

    fun withdraw(player: OfflinePlayer, amount: Double): Boolean =
        economy?.withdrawPlayer(player, amount)?.transactionSuccess() ?: false

    fun deposit(player: OfflinePlayer, amount: Double): Boolean =
        economy?.depositPlayer(player, amount)?.transactionSuccess() ?: false
}
