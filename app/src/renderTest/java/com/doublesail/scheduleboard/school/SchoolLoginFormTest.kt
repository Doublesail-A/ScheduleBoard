package com.doublesail.scheduleboard.school

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.doublesail.scheduleboard.data.BoardSettings
import com.doublesail.scheduleboard.ui.theme.ScheduleBoardTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class, qualifiers = "w640dp-h360dp-land-xhdpi")
class SchoolLoginFormTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun formNeedsOnlyAccountAndPasswordAndSubmitsThem() {
        var received: Pair<String, String>? = null
        var closed = false
        compose.setContent {
            ScheduleBoardTheme(BoardSettings(dynamicColor = false)) {
                SchoolLoginForm(login = { account, password -> received = account to password; 2 }, onClose = { closed = true })
            }
        }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(2)
        compose.onNodeWithText("验证码", substring = false).assertDoesNotExist()
        compose.onNodeWithText("第一学期").assertDoesNotExist()
        compose.onNodeWithText("登录并同步课表").assertIsNotEnabled()
        compose.onNodeWithText("学号").performTextInput("123456789")
        compose.onNodeWithText("教务密码").performTextInput("fixture-password")
        compose.onNodeWithText("登录并同步课表").performClick()
        compose.runOnIdle {
            assertEquals("123456789" to "fixture-password", received)
            assertTrue(closed)
        }
    }
    @Test fun rejectedPasswordIsShownAndFormCanRetry() {
        compose.setContent {
            ScheduleBoardTheme(BoardSettings(dynamicColor = false)) {
                SchoolLoginForm(login = { _, _ -> throw SchoolAuthenticationException("学号或教务密码不正确，请核对后重试") }, onClose = {})
            }
        }
        compose.onNodeWithText("学号").performTextInput("123456789")
        compose.onNodeWithText("教务密码").performTextInput("fixture-password")
        compose.onNodeWithText("登录并同步课表").performClick()
        compose.onNodeWithText("学号或教务密码不正确，请核对后重试").assertExists()
        compose.onNodeWithText("登录并同步课表").assertIsEnabled()
    }
}
