package com.doublesail.scheduleboard.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import com.doublesail.scheduleboard.tasks.BoardTask
import com.doublesail.scheduleboard.data.BoardSettings
import com.doublesail.scheduleboard.data.ThemeMode
import com.doublesail.scheduleboard.pomodoro.PomodoroState
import com.doublesail.scheduleboard.ui.theme.ScheduleBoardTheme
import java.io.File
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class, qualifiers = "w640dp-h360dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PomodoroRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun rendersLightAndPauseResumeControlsWork() {
        render(ThemeMode.LIGHT, "pomodoro-light-1280x720.png")
        compose.onNodeWithText("暂停").performClick()
        compose.onNodeWithText("继续").assertExists().performClick()
        compose.onNodeWithText("暂停").assertExists()
    }
    @Test fun rendersDarkTimer() { render(ThemeMode.DARK, "pomodoro-dark-1280x720.png") }
    @Test fun swipingDownClockOpensPomodoro() {
        val pager = renderPager()
        compose.onRoot().performTouchInput {
            swipe(Offset(width * .2f, height * .1f), Offset(width * .2f, height * .8f))
        }
        compose.runOnIdle { assertEquals(1, pager.settledPage) }
        compose.onRoot().performTouchInput {
            swipe(Offset(width * .2f, height * .8f), Offset(width * .2f, height * .1f))
        }
        compose.runOnIdle { assertEquals("Upward swipe returns to the board", 0, pager.settledPage) }
    }
    @Test fun downwardSwipeOnTaskListDoesNotOpenTimer() {
        val pager = renderPager()
        compose.onRoot().performTouchInput {
            swipe(Offset(width * .85f, height * .2f), Offset(width * .85f, height * .8f))
        }
        compose.runOnIdle { assertEquals("Task list scrolling must remain independent", 0, pager.settledPage) }
        compose.onRoot().performTouchInput {
            swipe(Offset(width * .85f, height * .8f), Offset(width * .85f, height * .2f))
        }
        compose.runOnIdle { assertEquals(0, pager.settledPage) }
    }
    @Test fun partialDragTracksFingerBeforeReleaseThenReturns() {
        val pager = renderPager()
        compose.onRoot().performTouchInput {
            down(Offset(width * .2f, height * .2f))
            repeat(10) { moveBy(Offset(0f, height * .025f), delayMillis = 50) }
        }
        compose.runOnIdle {
            assertTrue("Both pages move while the finger is down: ${pager.currentPageOffsetFraction}",
                kotlin.math.abs(pager.currentPageOffsetFraction) in .1f.. .4f)
        }
        capture("pager-drag-1280x720.png")
        compose.onRoot().performTouchInput { advanceEventTime(500); up() }
        compose.runOnIdle { assertEquals("A short slow drag returns to the same page", 0, pager.settledPage) }
    }
    private fun renderPager(): PagerState {
        lateinit var pager: PagerState
        compose.setContent {
            ScheduleBoardTheme(BoardSettings(dynamicColor = false)) {
                pager = rememberPagerState(pageCount = { 2 })
                BoardPager(pager, dashboard = {
                    BoardDashboard(BoardUiState(settings = BoardSettings(), now = ZonedDateTime.parse("2026-11-10T16:35:00+08:00"),
                        tasks = (1..20).map { BoardTask("$it", "任务 $it") }),
                        onOpenSettings = {}, onAddTask = {}, onTaskCompleted = { _, _ -> }, onEventClick = {})
                }, timer = {
                    PomodoroScreen(PomodoroState(), 25 * 60_000L, onToggle = {}, onReset = {}, onSelect = { _, _ -> }, onNext = {}, onBack = {})
                })
            }
        }
        compose.waitForIdle()
        return pager
    }
    @Test fun restOverlayIsBlackAndCanBeTapped() {
        var peeked = false
        compose.setContent { DisplayRestOverlay { peeked = true } }
        capture("class-rest-1280x720.png")
        compose.onNodeWithContentDescription("上课休屏，点击临时查看两分钟").performClick()
        compose.runOnIdle { assertTrue(peeked) }
    }
    private fun capture(filename: String) {
        val image = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val root = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        compose.runOnUiThread {
            root.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 1280, 720)
        }
        compose.waitForIdle()
        compose.runOnUiThread { root.draw(Canvas(image)) }
        File(requireNotNull(System.getProperty("board.render.output")), filename).apply { parentFile!!.mkdirs() }
            .outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun render(mode: ThemeMode, filename: String) {
        var state by mutableStateOf(PomodoroState(completedFocus = 2).start(0, 0, 1))
        val left = 22 * 60_000L + 38_000
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
        compose.setContent {
            ScheduleBoardTheme(BoardSettings(dynamicColor = false, themeMode = mode)) {
                PomodoroScreen(state, left, onToggle = { state = state.copy(running = !state.running, remainingMs = left) },
                    onReset = {}, onSelect = { _, _ -> }, onNext = {}, onBack = {})
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("22:38").assertExists()
        compose.onNodeWithText("已完成 2 次专注").assertExists()
        val image = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val root = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        compose.runOnUiThread {
            root.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 1280, 720)
        }
        compose.waitForIdle()
        compose.runOnUiThread { root.draw(Canvas(image)) }
        File(requireNotNull(System.getProperty("board.render.output")), filename).apply { parentFile!!.mkdirs() }
            .outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
