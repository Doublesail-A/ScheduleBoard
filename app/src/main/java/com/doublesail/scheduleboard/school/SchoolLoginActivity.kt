package com.doublesail.scheduleboard.school

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doublesail.scheduleboard.ScheduleBoardApplication
import com.doublesail.scheduleboard.data.BoardSettings
import com.doublesail.scheduleboard.ui.theme.ScheduleBoardTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class SchoolLoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ScheduleBoardApplication
        setContent {
            val settings by app.settingsRepository.settings.collectAsStateWithLifecycle(BoardSettings())
            ScheduleBoardTheme(settings) { SchoolLoginForm(app.schoolSync::loginAndSync, onClose = ::finish) }
        }
    }
}

@Composable
internal fun SchoolLoginForm(login: suspend (String, String) -> Int, onClose: () -> Unit) {
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun submit() {
        if (busy || account.isBlank() || password.isEmpty()) return
        busy = true; message = null
        scope.launch {
            try {
                login(account, password)
                password = ""
                onClose()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                message = when (error) {
                    is SchoolAuthenticationException, is SchoolLoginRequired, is SchoolCaptchaException,
                    is IllegalArgumentException -> error.message ?: "登录未成功"
                    is java.net.UnknownHostException -> "无法解析学校教务地址，请检查手机网络或 DNS"
                    is java.net.SocketTimeoutException -> "连接学校教务超时，请确认当前网络可访问学校教务后重试"
                    is java.io.IOException -> error.message?.takeIf { it.startsWith("教务") }
                        ?: "无法连接学校教务，请检查手机网络后重试；原课表已保留"
                    else -> "同步未成功，原课表已保留，请重试"
                }
            } finally { busy = false }
        }
    }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("福建农林大学教务", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onClose, enabled = !busy) { Text("返回日程牌") }
            }
            Text("输入学号和教务密码，即可自动获取当前学期课表。", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(account, { account = it }, Modifier.weight(1f), label = { Text("学号") }, singleLine = true,
                    enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next))
                OutlinedTextField(password, { password = it }, Modifier.weight(1f), label = { Text("教务密码") }, singleLine = true,
                    enabled = !busy, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }))
            }
            Text("登录信息通过 Android Keystore 在本机加密保存，用于会话过期后自动登录。验证码在本机自动处理，学期与校历自动获取。退出教务会删除登录信息。",
                style = MaterialTheme.typography.bodySmall)
            Text("学校旧版教务使用 HTTP；账号密码只发送给 jwgl.fafu.edu.cn。", style = MaterialTheme.typography.bodySmall)
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(enabled = !busy && account.isNotBlank() && password.isNotEmpty(), onClick = ::submit) {
                    Text(if (busy) "正在登录并同步…" else "登录并同步课表")
                }
                if (busy) CircularProgressIndicator(Modifier.size(24.dp))
            }
        }
    }
}
