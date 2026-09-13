package clouddev1stack.commands

import clouddev1stack.AuctionMaster
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ToggleAlertsCommand(private val plugin: AuctionMaster) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Only players can use this command.")
            return true
        }
        plugin.alertsManager.toggle(sender)
        return true
    }
}
