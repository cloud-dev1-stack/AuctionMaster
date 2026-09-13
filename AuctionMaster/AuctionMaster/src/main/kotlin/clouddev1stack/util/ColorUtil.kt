package clouddev1stack.util

object ColorUtil {

    private val HEX_PATTERN = Regex("&#([A-Fa-f0-9]{6})")

        fun translate(text: String): String {
        val withHex = HEX_PATTERN.replace(text) { match ->
            val hex = match.groupValues[1]
            buildString {
                append("\u00A7x")
                hex.forEach { append('\u00A7').append(it) }
            }
        }
        return withHex.replace('&', '\u00A7')
    }
}
