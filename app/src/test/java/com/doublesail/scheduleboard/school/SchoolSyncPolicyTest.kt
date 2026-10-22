package com.doublesail.scheduleboard.school

import org.junit.Assert.*
import org.junit.Test

class SchoolSyncPolicyTest {
    @Test fun transientFailuresBackOffAndRemainBounded() {
        assertEquals(listOf(1L, 2L, 4L, 8L, 15L, 15L, 15L), (1..7).map { SchoolSyncPolicy.retryDelayMillis(it) / 60_000 })
    }
    @Test fun noRequestBeforeDeadline() {
        assertFalse(SchoolSyncPolicy.isDue(1_000, 61_000))
        assertTrue(SchoolSyncPolicy.isDue(61_000, 61_000))
    }
    @Test fun clockRollbackCannotPauseSynchronizationIndefinitely() {
        assertTrue(SchoolSyncPolicy.isDue(1_000, 1_000 + 60 * 60_000L))
    }
}
