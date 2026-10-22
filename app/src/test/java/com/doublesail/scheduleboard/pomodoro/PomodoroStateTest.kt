package com.doublesail.scheduleboard.pomodoro

import org.junit.Assert.*
import org.junit.Test

class PomodoroStateTest {
    @Test fun pauseAndResumeKeepRemainingTime() {
        val start = PomodoroState().start(1_000, 50_000, 1)
        val pause = start.pause(61_000, 110_000, 1)
        assertFalse(pause.running)
        assertEquals(24 * 60_000L, pause.remainingMs)
        val resumed = pause.start(200_000, 400_000, 1)
        assertEquals(23 * 60_000L, resumed.remaining(260_000, 460_000, 1))
    }
    @Test fun wallClockChangesCannotChangeRunningTimerOnSameBoot() {
        val start = PomodoroState().start(1_000, 100_000, 2)
        assertEquals(24 * 60_000L, start.remaining(61_000, 999_999_999, 2))
        assertEquals(24 * 60_000L, start.remaining(61_000, -999_999_999, 2))
    }
    @Test fun restoredTimerUsesWallTimeOnlyAfterReboot() {
        val start = PomodoroState().start(1_000, 100_000, 2)
        assertEquals(23 * 60_000L, start.remaining(10, 220_000, 3))
        assertTrue(start.settle(10, 2_000_000, 3).finished)
    }
    @Test fun completionIsCountedOnceAndNeverAutoStartsBreak() {
        val finished = PomodoroState().start(0, 0, 1).settle(25 * 60_000L, 0, 1)
        assertTrue(finished.finished)
        assertFalse(finished.running)
        assertEquals(1, finished.completedFocus)
        assertEquals(1, finished.settle(30 * 60_000L, 0, 1).completedFocus)
        assertEquals(PomodoroPhase.SHORT_BREAK, finished.nextPhase())
    }
    @Test fun rebootRecoveryRebasesToMonotonicClockBeforeFutureWallChanges() {
        val restored = PomodoroState().start(1_000, 100_000, 2).restored(10, 220_000, 3)
        assertEquals(23 * 60_000L, restored.remaining(10, 999_999_999, 3))
        assertEquals(22 * 60_000L, restored.remaining(60_010, -999_999_999, 3))
    }
    @Test fun fourthFocusUsesLongBreakAndBreakDoesNotIncrementFocusCount() {
        val completed = PomodoroState(completedFocus = 3).start(0, 0, 1).settle(25 * 60_000L, 0, 1)
        assertEquals(PomodoroPhase.LONG_BREAK, completed.nextPhase())
        val rest = completed.select(PomodoroPhase.LONG_BREAK).start(0, 0, 1).settle(15 * 60_000L, 0, 1)
        assertEquals(4, rest.completedFocus)
        assertEquals(PomodoroPhase.FOCUS, rest.nextPhase())
    }
    @Test fun resetAndCustomDurationReplaceDeadline() {
        val reset = PomodoroState().start(0, 0, 1).select(PomodoroPhase.FOCUS, 45)
        assertFalse(reset.running)
        assertEquals(45 * 60_000L, reset.remainingMs)
        assertEquals(180 * 60_000L, reset.select(PomodoroPhase.FOCUS, 999).durationMs)
    }
}
