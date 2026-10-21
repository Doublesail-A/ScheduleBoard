package com.doublesail.scheduleboard.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import com.doublesail.scheduleboard.data.BoardSettings
import com.doublesail.scheduleboard.data.ThemeMode
import com.doublesail.scheduleboard.schedule.ScheduleEvent
import com.doublesail.scheduleboard.tasks.BoardTask
import com.doublesail.scheduleboard.ui.theme.ScheduleBoardTheme
import java.io.File
import java.time.ZonedDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class, qualifiers = "w640dp-h360dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BoardRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun rendersReferenceAndCompletesTask() {
        renderReference(ThemeMode.LIGHT, "board-light-1280x720.png")
        compose.onNodeWithText("高数作业").performClick().assertIsOn()
    }

    @Test fun rendersDarkReference() {
        renderReference(ThemeMode.DARK, "board-dark-1280x720.png")
    }

    private fun renderReference(mode: ThemeMode, filename: String) {
        val now = ZonedDateTime.parse("2026-10-21T16:35:00+08:00[Asia/Shanghai]")
        val courses = listOf(
            ScheduleEvent("one", "植物学（双语课）", now.plusMinutes(13).toInstant(), now.plusMinutes(100).toInstant(),
                location = "创104", teacher = "庄振宏"),
            ScheduleEvent("two", "自然资源与生态保护", now.plusHours(2).toInstant(), now.plusHours(3).toInstant(),
                location = "明北附301", teacher = "Mavic Zhang"),
        )
        var tasks by mutableStateOf(listOf(BoardTask("1", "高数作业"), BoardTask("2", "购买《植物学实验指导》"),
            BoardTask("3", "购买《植物学操作指南》"), BoardTask("4", "背单词")))
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
        compose.setContent {
            ScheduleBoardTheme(BoardSettings(dynamicColor = false, themeMode = mode)) {
                BoardDashboard(BoardUiState(settings = BoardSettings(), now = now, events = courses, tasks = tasks),
                    onOpenSettings = {}, onAddTask = {}, onTaskCompleted = { id, completed ->
                        tasks = tasks.map { if (it.id == id) it.copy(completed = completed) else it }
                    }, onEventClick = {})
            }
        }
        compose.waitForIdle()
        val textLayouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("购买《植物学实验指导》", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(textLayouts) }
        assertEquals("Long task titles must use two lines", 2, textLayouts.first().lineCount)
        val image = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val view = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        compose.runOnUiThread {
            view.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 1280, 720)
        }
        compose.waitForIdle()
        compose.runOnUiThread { view.draw(Canvas(image)) }
        val output = File(requireNotNull(System.getProperty("board.render.output"))).apply { mkdirs() }
        File(output, filename).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithText("13分钟后上课").assertExists()
        compose.onNodeWithText("2小时后上课").assertExists()
    }
}
