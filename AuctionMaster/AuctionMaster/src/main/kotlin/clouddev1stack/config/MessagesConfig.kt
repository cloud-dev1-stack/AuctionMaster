package clouddev1stack.config

import clouddev1stack.util.ColorUtil
import java.io.File
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin

class MessagesConfig(private val plugin: JavaPlugin) {

    private lateinit var yaml: YamlConfiguration
    private lateinit var file: File

    fun load() {
        file = File(plugin.dataFolder, "messages.yml")
        if (!file.exists()) plugin.saveResource("messages.yml", false)
        yaml = YamlConfiguration.loadConfiguration(file)
    }

    fun reload() = load()

        fun get(path: String, placeholders: Map<String, String> = emptyMap()): String {
        var raw = yaml.getString(path) ?: return ""
        val prefix = yaml.getString("prefix", "") ?: ""
        raw = raw.replace("{prefix}", prefix)
        placeholders.forEach { (key, value) -> raw = raw.replace("{$key}", value) }
        return ColorUtil.translate(raw)
    }
}
