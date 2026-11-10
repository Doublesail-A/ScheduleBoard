package com.doublesail.scheduleboard.ui

import android.app.Activity
import android.view.WindowManager
import com.doublesail.scheduleboard.data.BoardSettings
import com.doublesail.scheduleboard.display.BoardWindowController
import com.doublesail.scheduleboard.display.DisplayMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = android.app.Application::class)
class BoardWindowControllerTest {
    @Test fun dimModesOverrideBrightnessAndNormalRestoresSystemWithoutClearingKeepAwake() {
        Robolectric.buildActivity(Activity::class.java).setup().use { activity ->
            val controller = BoardWindowController(activity.get())
            controller.apply(BoardSettings())
            for (mode in listOf(DisplayMode.CLASS_REST, DisplayMode.NIGHT)) {
                controller.setDisplayMode(mode)
                assertEquals(0f, activity.get().window.attributes.screenBrightness, 0f)
                assertTrue(activity.get().window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
            }
            controller.setDisplayMode(DisplayMode.SYSTEM)
            assertEquals(-1f, activity.get().window.attributes.screenBrightness, 0f)
        }
    }
    @Test fun changingDisplayModeRespectsExplicitKeepAwakeOptOut() {
        Robolectric.buildActivity(Activity::class.java).setup().use { activity ->
            val controller = BoardWindowController(activity.get())
            controller.apply(BoardSettings(keepScreenOn = false))
            controller.setDisplayMode(DisplayMode.CLASS_REST)
            controller.setDisplayMode(DisplayMode.SYSTEM)
            assertEquals(0, activity.get().window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
