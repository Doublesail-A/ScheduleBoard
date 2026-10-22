package com.doublesail.scheduleboard.school

/** Foreground retry and background scheduling share the same persisted deadline. */
internal object SchoolSyncPolicy {
    const val IntervalMillis = 15 * 60_000L
    fun retryDelayMillis(failures: Int): Long = 60_000L * (1L shl (failures - 1).coerceIn(0, 4))
        .coerceAtMost(15)
    fun isDue(now: Long, next: Long): Boolean = next <= now || next - now > IntervalMillis
}
