package clouddev1stack.util

import org.bukkit.Material
import org.bukkit.block.ShulkerBox
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.inventory.meta.BundleMeta

object ContainerPreview {

        fun previewContents(item: ItemStack): List<ItemStack> {
        val meta = item.itemMeta ?: return emptyList()

        if (meta is BundleMeta) {
            return meta.items.toList()
        }

        if (meta is BlockStateMeta && item.type.name.endsWith("SHULKER_BOX")) {
            val state = meta.blockState
            if (state is ShulkerBox) {
                return state.inventory.contents.filterNotNull()
            }
        }

        return emptyList()
    }

    fun isPreviewable(item: ItemStack): Boolean =
        item.type.name.endsWith("SHULKER_BOX") || item.type == Material.BUNDLE
}
