package com.doublesail.scheduleboard.school

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doublesail.scheduleboard.ScheduleBoardApplication
import com.doublesail.scheduleboard.data.BoardSettings
import com.doublesail.scheduleboard.ui.theme.ScheduleBoardTheme
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class SchoolLoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ScheduleBoardApplication
        setContent {
            val settings by app.settingsRepository.settings.collectAsStateWithLifecycle(BoardSettings())
            ScheduleBoardTheme(settings) { SchoolLoginForm(app.schoolSync, onClose = ::finish) }
        }
    }
}

@Composable
private fun SchoolLoginForm(repository: SchoolSyncRepository, onClose: () -> Unit) {
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var captcha by remember { mutableStateOf("") }
    val today = LocalDate.now()
    val startYear = if (today.monthValue >= 8) today.year else today.year - 1
    var year by remember { mutableStateOf("$startYear-${startYear + 1}") }
    var term by remember { mutableStateOf(if (today.monthValue in 2..7) "2" else "1") }
    var campus by remember { mutableStateOf(Campus.MAIN) }
    var firstWeek by remember { mutableStateOf("") }
    var challenge by remember { mutableStateOf<SchoolChallenge?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun refresh() {
        if (busy) return
        busy = true; challenge = null; captcha = ""
        scope.launch {
            try { challenge = repository.challenge(); message = null }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                message = "无法连接学校教务，请确认网络可访问 jwgl.fafu.edu.cn 后重试"
            } finally { busy = false }
        }
    }
    LaunchedEffect(Unit) { refresh() }
    val image = remember(challenge) {
        challenge?.image?.let { bytes ->
            val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, size)
            if (size.outWidth in 1..2000 && size.outHeight in 1..1000 && size.outWidth.toLong() * size.outHeight <= 1_000_000)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() else null
        }
    }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("福建农林大学教务", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onClose) { Text("返回日程牌") }
            }
            Text("直接连接学校教务（HTTP）。密码不保存；登录会话在本机加密保存。",
                style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(account, { account = it }, Modifier.weight(1f), label = { Text("学号") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii))
                OutlinedTextField(password, { password = it }, Modifier.weight(1f), label = { Text("教务密码") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(captcha, { captcha = it }, Modifier.weight(1f), label = { Text("验证码") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii))
                if (image != null) Image(image, "教务验证码", Modifier.width(96.dp).height(40.dp))
                TextButton(onClick = ::refresh, enabled = !busy) { Text("换一张") }
                if (busy) CircularProgressIndicator(Modifier.size(24.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(year, { year = it }, Modifier.weight(1f), label = { Text("学年，如 2026-2027") }, singleLine = true)
                FilterChip(term == "1", onClick = { term = "1" }, label = { Text("第一学期") })
                FilterChip(term == "2", onClick = { term = "2" }, label = { Text("第二学期") })
                Campus.entries.forEach { item -> FilterChip(campus == item, onClick = { campus = item }, label = { Text(item.label) }) }
            }
            OutlinedTextField(firstWeek, { firstWeek = it }, Modifier.fillMaxWidth(),
                label = { Text("第一周日期（可留空，优先使用已核对校历）") },
                supportingText = { Text("仅缺少该学期校历时填写，例如 2026-08-30；手动日期不会推断未公布的假期。") }, singleLine = true)
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("联网时约每 15 分钟同步一次。会话失效后需重新登录，断网时继续显示缓存课表。",
                style = MaterialTheme.typography.bodySmall)
            Button(enabled = !busy && challenge != null && account.isNotBlank() && password.isNotEmpty() && captcha.isNotBlank(),
                onClick = {
                    val current = challenge ?: return@Button
                    busy = true; message = null
                    scope.launch {
                        try {
                            val first = firstWeek.trim().takeIf(String::isNotEmpty)?.let(LocalDate::parse)
                            repository.loginAndSync(current, account, password, captcha, year.trim(), term, campus, first)
                            password = ""
                            onClose()
                        } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            message = if (error is java.time.DateTimeException) "第一周日期格式不正确，请使用 yyyy-MM-dd" else if (error is SchoolLoginRequired || error is IllegalArgumentException)
                                error.message ?: "登录未成功" else "暂时无法同步，请检查网络和教务服务后重试；原课表已保留"
                            challenge = null
                        } finally { busy = false }
                    }
                }) { Text("登录并同步课表") }
        }
    }
}
