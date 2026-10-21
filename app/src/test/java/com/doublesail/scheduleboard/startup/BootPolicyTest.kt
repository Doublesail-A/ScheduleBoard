package com.doublesail.scheduleboard.startup

import org.junit.Assert.assertEquals
import org.junit.Test

class BootPolicyTest {
    @Test fun disabledDoesNotLaunchOrNotify() {
        for (api in listOf(23, 28, 29, 31, 36)) {
            assertEquals(BootAction.NONE, bootAction(false, false, api))
        }
    }

    @Test fun defaultHomeLeavesLaunchToSystem() {
        assertEquals(BootAction.SYSTEM_HOME, bootAction(true, true, 31))
    }

    @Test fun legacyDevicesCanOpenDirectly() {
        assertEquals(BootAction.OPEN_ACTIVITY, bootAction(true, false, 28))
    }

    @Test fun android10AndLaterUseNotificationFallback() {
        for (api in listOf(29, 31, 33, 36)) {
            assertEquals(BootAction.SHOW_NOTIFICATION, bootAction(true, false, api))
        }
    }
}
