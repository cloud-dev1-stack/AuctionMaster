package clouddev1stack.config

import java.io.File
import org.bukkit.Sound
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class SoundsConfig(private val plugin: JavaPlugin) {

    private lateinit var yaml: YamlConfiguration
    private lateinit var file: File

    fun load() {
        file = File(plugin.dataFolder, "sounds.yml")
        if (!file.exists()) plugin.saveResource("sounds.yml", false)
        yaml = YamlConfiguration.loadConfiguration(file)
    }

    fun reload() = load()

        fun play(player: Player, key: String) {
        val section = yaml.getConfigurationSection(key) ?: return
        if (!section.getBoolean("enabled", true)) return

        val soundName = section.getString("sound") ?: return
        val sound = runCatching { Sound.valueOf(soundName) }.getOrElse {
            plugin.logger.warning("sounds.yml: unknown sound '$soundName' under '$key' - skipping.")
            return
        }
        val volume = section.getString("volume")?.toFloatOrNull() ?: 1.0f
        val pitch = section.getString("pitch")?.toFloatOrNull() ?: 1.0f
        player.playSound(player.location, sound, volume, pitch)
    }
}
