package com.doublesail.scheduleboard.pomodoro

enum class PomodoroPhase(val label: String, val minutes: Int) {
    FOCUS("专注", 25), SHORT_BREAK("短休息", 5), LONG_BREAK("长休息", 15),
}

data class PomodoroState(
    val phase: PomodoroPhase = PomodoroPhase.FOCUS,
    val durationMs: Long = 25 * 60_000L,
    val remainingMs: Long = 25 * 60_000L,
    val running: Boolean = false,
    val finished: Boolean = false,
    val deadlineElapsed: Long = 0,
    val deadlineWall: Long = 0,
    val bootCount: Int = -1,
    val completedFocus: Int = 0,
) {
    fun remaining(elapsed: Long, wall: Long, boot: Int): Long = if (!running) remainingMs else
        (if (boot == bootCount) deadlineElapsed - elapsed else deadlineWall - wall).coerceIn(0, durationMs)

    fun start(elapsed: Long, wall: Long, boot: Int): PomodoroState {
        if (running) return this
        val left = if (finished) durationMs else remainingMs
        return copy(running = true, finished = false, remainingMs = left,
            deadlineElapsed = elapsed + left, deadlineWall = wall + left, bootCount = boot)
    }

    fun pause(elapsed: Long, wall: Long, boot: Int): PomodoroState =
        settle(elapsed, wall, boot).let { it.copy(running = false, remainingMs = it.remaining(elapsed, wall, boot)) }

    fun settle(elapsed: Long, wall: Long, boot: Int): PomodoroState =
        if (running && remaining(elapsed, wall, boot) == 0L) copy(running = false, finished = true, remainingMs = 0,
            completedFocus = completedFocus + if (phase == PomodoroPhase.FOCUS) 1 else 0) else this

    fun restored(elapsed: Long, wall: Long, boot: Int): PomodoroState {
        val settled = settle(elapsed, wall, boot)
        if (!settled.running || settled.bootCount == boot) return settled
        val left = settled.remaining(elapsed, wall, boot)
        return settled.copy(remainingMs = left, bootCount = boot, deadlineElapsed = elapsed + left, deadlineWall = wall + left)
    }

    fun select(next: PomodoroPhase, minutes: Int = next.minutes) = copy(phase = next,
        durationMs = minutes.coerceIn(1, 180) * 60_000L, remainingMs = minutes.coerceIn(1, 180) * 60_000L,
        running = false, finished = false, deadlineElapsed = 0, deadlineWall = 0)

    fun nextPhase() = if (phase != PomodoroPhase.FOCUS) PomodoroPhase.FOCUS else
        if (completedFocus > 0 && completedFocus % 4 == 0) PomodoroPhase.LONG_BREAK else PomodoroPhase.SHORT_BREAK
}
