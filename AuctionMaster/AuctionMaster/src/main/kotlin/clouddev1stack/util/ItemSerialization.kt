package clouddev1stack.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream

object ItemSerialization {

    fun serialize(item: ItemStack): String {
        ByteArrayOutputStream().use { byteStream ->
            BukkitObjectOutputStream(byteStream).use { dataStream ->
                dataStream.writeObject(item)
            }
            return Base64.getEncoder().encodeToString(byteStream.toByteArray())
        }
    }

    fun deserialize(data: String): ItemStack {
        val bytes = Base64.getDecoder().decode(data)
        ByteArrayInputStream(bytes).use { byteStream ->
            BukkitObjectInputStream(byteStream).use { dataStream ->
                return dataStream.readObject() as ItemStack
            }
        }
    }

        fun deserializeSafe(data: String): ItemStack =
        runCatching { deserialize(data) }.getOrElse {
            Bukkit.getLogger().warning("[AuctionMaster] Failed to deserialize an item, it may have used a removed plugin/material: ${it.message}")
            ItemStack(org.bukkit.Material.BARRIER)
        }
}
