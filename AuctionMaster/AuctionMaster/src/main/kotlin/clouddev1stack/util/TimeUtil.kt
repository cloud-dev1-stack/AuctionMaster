package clouddev1stack.util

import java.time.Duration
import java.time.Instant

object TimeUtil {
    fun humanizeRemaining(expiresAt: Instant): String {
        val remaining = Duration.between(Instant.now(), expiresAt)
        if (remaining.isNegative || remaining.isZero) return "Expired"
        val days = remaining.toDays()
        val hours = remaining.toHoursPart()
        val minutes = remaining.toMinutesPart()
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }
}
