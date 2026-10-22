package com.doublesail.scheduleboard.tasks.google

import android.accounts.AccountManager
import android.app.Activity
import android.content.ActivityNotFoundException
import android.os.Bundle
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.doublesail.scheduleboard.ScheduleBoardApplication
import com.doublesail.scheduleboard.data.BoardSettings
import com.doublesail.scheduleboard.ui.theme.ScheduleBoardTheme
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.AccountPicker
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class GoogleTasksActivity : ComponentActivity() {
    private val repository get() = (application as ScheduleBoardApplication).taskRepository
    private var chosenAccount: String? = null
    private var message by mutableStateOf<String?>(null)
    private var authorizing by mutableStateOf(false)
    private val chooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { response ->
        if (response.resultCode == Activity.RESULT_OK) {
            val email = response.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (email.isNullOrBlank()) { message = "未能读取所选 Google 账号"; authorizing = false }
            else { chosenAccount = email; authorize(email) }
        } else authorizing = false
    }
    private val authorization = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { response ->
        authorizing = false
        if (response.resultCode == Activity.RESULT_OK && response.data != null) {
            try { authorized(Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(response.data)) }
            catch (error: Exception) { authorizationError(error) }
        } else message = "已取消授权，原待办保持不变"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chosenAccount = savedInstanceState?.getString("chosen_account")
        enableEdgeToEdge()
        setContent {
            val app = application as ScheduleBoardApplication
            val settings by app.settingsRepository.settings.collectAsStateWithLifecycle(BoardSettings())
            val cache by repository.state.collectAsStateWithLifecycle()
            val status by repository.status.collectAsStateWithLifecycle()
            var disconnect by remember { mutableStateOf(false) }
            var oauthConfig by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            fun action(operation: suspend () -> Unit) {
                scope.launch { try { operation(); message = null } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    message = if (error is IllegalArgumentException) error.message else "操作未完成，请检查网络后重试"
                } }
            }
            ScheduleBoardTheme(settings) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Google Tasks", style = MaterialTheme.typography.headlineSmall)
                            TextButton(onClick = ::finish) { Text("返回日程牌") }
                        }
                        Text("在 Google Tasks 与日程牌之间同步新增、重命名、完成和删除。离线修改会在联网后上传。",
                            style = MaterialTheme.typography.bodyMedium)
                        Text(if (cache.connected) cache.account else "当前使用本机待办", style = MaterialTheme.typography.titleMedium)
                        if (cache.connected) {
                            Text("清单：${cache.listTitle.ifEmpty { "正在获取…" }} · ${cache.edits.size} 项待上传")
                            if (cache.lastSync > 0) Text("上次同步：" + java.time.Instant.ofEpochMilli(cache.lastSync)
                                .atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("M月d日 HH:mm")),
                                style = MaterialTheme.typography.bodySmall)
                            cache.lists.forEach { list ->
                                FilterChip(selected = cache.listId == list.id,
                                    enabled = !status.syncing && cache.edits.isEmpty(), onClick = { action { repository.chooseList(list.id) } },
                                    label = { Text(list.title) })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = ::chooseAccount, enabled = !authorizing && !status.syncing) {
                                Text(if (cache.connected) "重新授权 / 更换账号" else "使用 Google 账号登录")
                            }
                            if (cache.connected) FilledTonalButton(onClick = { action { repository.sync(force = true) } }, enabled = !status.syncing) {
                                Text(if (status.syncing) "同步中…" else "立即同步")
                            }
                            if (authorizing || status.syncing) CircularProgressIndicator(Modifier.size(24.dp))
                        }
                        (message ?: status.message)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Text("手机需要安装并启用 Google Play 服务，且网络可访问 Google。首次使用需在 Google Cloud 配置 Tasks API 和 Android OAuth 客户端；方法见项目 README。",
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { oauthConfig = true }) { Text("查看 Google Cloud 注册信息") }
                        Text("本机待办独立保留，连接 Google 后显示所选云端清单。更换清单前需上传完修改；不会自动把本机待办上传给 Google。",
                            style = MaterialTheme.typography.bodySmall)
                        if (cache.connected) TextButton(onClick = { disconnect = true }, enabled = !status.syncing && cache.edits.isEmpty()) {
                            Text("断开 Google 账号")
                        }
                    }
                    if (disconnect) AlertDialog(onDismissRequest = { disconnect = false }, title = { Text("断开 Google Tasks？") },
                        text = { Text("清除本机下载的 Google 待办并恢复显示本机待办。Google 端任务会保留；可同时撤销此应用的 Tasks 授权。") },
                        confirmButton = { TextButton(onClick = { action { repository.disconnect(revoke = true); disconnect = false } }) { Text("撤销授权并断开") } },
                        dismissButton = { TextButton(onClick = { action { repository.disconnect(); disconnect = false } }) { Text("仅切回本机待办") } })
                    if (oauthConfig) AlertDialog(onDismissRequest = { oauthConfig = false }, title = { Text("Android OAuth 客户端") },
                        text = { androidx.compose.foundation.text.selection.SelectionContainer {
                            Text("包名：$packageName\n\nSHA-1：${signingSha1()}\n\n启用 Google Tasks API，并请求 https://www.googleapis.com/auth/tasks 授权。")
                        } }, confirmButton = { TextButton(onClick = { oauthConfig = false }) { Text("关闭") } })
                }
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        chosenAccount?.let { outState.putString("chosen_account", it) }
        super.onSaveInstanceState(outState)
    }
    private fun chooseAccount() {
        message = null
        if (repository.state.value.edits.isNotEmpty()) {
            chosenAccount = repository.state.value.account
            authorize(requireNotNull(chosenAccount))
            return
        }
        val available = GoogleApiAvailability.getInstance()
        val result = available.isGooglePlayServicesAvailable(this)
        if (result != ConnectionResult.SUCCESS) {
            available.getErrorDialog(this, result, 770)?.show()
            message = "请先安装、启用或更新 Google Play 服务，再登录"
            return
        }
        try {
            authorizing = true
            chooser.launch(AccountPicker.newChooseAccountIntent(AccountPicker.AccountChooserOptions.Builder()
                .setAllowableAccountsTypes(listOf("com.google")).setAlwaysShowAccountPicker(true).build()))
        } catch (_: ActivityNotFoundException) { authorizing = false; message = "手机没有可用的 Google 账号服务" }
    }
    private fun authorize(email: String) {
        authorizing = true
        Identity.getAuthorizationClient(this).authorize(GoogleTasksAuth.request(email))
            .addOnSuccessListener { result ->
                try {
                    if (result.hasResolution()) authorization.launch(IntentSenderRequest.Builder(requireNotNull(result.pendingIntent)).build())
                    else authorized(result)
                } catch (error: Exception) { authorizationError(error) }
            }.addOnFailureListener(::authorizationError)
    }
    private fun authorized(result: AuthorizationResult) {
        authorizing = false
        if (!result.grantedScopes.contains(GoogleTasksAuth.TasksScope)) {
            message = "未授予 Google Tasks 权限，请重新授权并允许管理任务"; return
        }
        val account = chosenAccount ?: run { message = "请选择要同步的 Google 账号"; return }
        lifecycleScope.launch {
            try { repository.connect(account); message = null }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                message = error.message?.takeIf { error is IllegalArgumentException } ?: "账号未连接，请重试"
            }
        }
    }
    private fun authorizationError(error: Exception) {
        authorizing = false
        message = if (error is ApiException && error.statusCode == 10)
            "Google OAuth 未配置：请在 Google Cloud 登记 com.doublesail.scheduleboard 和此 APK 的 SHA-1 签名"
        else "Google 授权未完成，请检查网络、Play 服务和 Google Cloud 测试用户设置后重试"
    }
    @Suppress("DEPRECATION")
    private fun signingSha1(): String {
        val certificates = if (Build.VERSION.SDK_INT >= 28)
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
        else packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
        return certificates?.firstOrNull()?.let { signature -> java.security.MessageDigest.getInstance("SHA-1")
            .digest(signature.toByteArray()).joinToString(":") { "%02X".format(java.util.Locale.ROOT, it.toInt() and 255) } } ?: "无法读取"
    }
}
