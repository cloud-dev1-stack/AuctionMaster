package clouddev1stack.commands

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class AuctionTabCompleter : TabCompleter {

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (args.size != 1) return emptyList()
        val prefix = args[0].lowercase()
        val options = when (command.name.lowercase()) {
            "ah" -> listOf("sell", "collect", "history", "cancel", "search", "listings", "togglequickbuy", "togglequicksell")
            "ahadmin" -> listOf("reload", "remove", "cancelall", "forceexpire", "recovery", "status", "lock", "unlock")
            else -> emptyList()
        }
        return options.filter { it.startsWith(prefix) }
    }
}
