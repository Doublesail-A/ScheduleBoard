package com.doublesail.scheduleboard.school

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.doublesail.scheduleboard.ScheduleBoardApplication
import com.doublesail.scheduleboard.schedule.IcsScheduleRepository
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class SchoolSyncState(
    val connected: Boolean = false, val syncing: Boolean = false, val requiresLogin: Boolean = false,
    val lastSync: Instant? = null, val message: String? = null, val termLabel: String = "",
)

class SchoolSyncRepository(
    private val context: Context,
    private val schedule: IcsScheduleRepository,
    private val client: SchoolClient = SchoolClient(decoder = SchoolCaptchaRecognizer(context)),
    private val vault: SchoolProfileStore = SchoolSessionVault(context),
    private val calendar: (String, String) -> SchoolCalendar = { year, term -> SchoolCalendarStore(context).resolve(year, term, null) },
    private val schedulePeriodic: () -> Unit = { enqueueSchoolSync(context) },
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized = CompletableDeferred<Unit>()
    private val metadata = context.getSharedPreferences("school_sync_status", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(SchoolSyncState())
    val state = mutableState.asStateFlow()

    init {
        scope.launch {
            try {
                mutex.withLock {
                    val stored = runCatching { vault.read() }
                    val profile = stored.getOrNull()
                    // 0.2.1's needs_login marker could be caused by a temporary session failure.
                    // Only an explicit credential rejection in this version pauses automatic login.
                    val blocked = metadata.getBoolean("auto_login_blocked", false)
                    val needsLogin = stored.isFailure || (profile != null && (profile.password.isNullOrEmpty() || blocked))
                    mutableState.value = SchoolSyncState(connected = profile != null, requiresLogin = needsLogin,
                        lastSync = metadata.getLong("last_sync", 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli),
                        message = if (stored.isFailure) "本机登录信息无法解密，请重新登录"
                            else if (!needsLogin && metadata.getBoolean("needs_login", false)) "正在自动恢复教务连接…"
                            else metadata.getString("message", null), termLabel = profile?.let(::label).orEmpty())
                    if (profile != null) schedulePeriodic()
                }
            } finally { initialized.complete(Unit) }
        }
    }

    suspend fun loginAndSync(account: String, password: String): Int = withContext(Dispatchers.IO) {
        initialized.await()
        mutex.withLock {
            mutableState.value = mutableState.value.copy(syncing = true)
            var savedNewLogin = false
            try {
                val session = client.login(account.trim(), password)
                val previous = runCatching { vault.read() }.getOrNull()
                val profile = SchoolProfile(session, "", "", previous?.campus ?: Campus.MAIN, password = password)
                // Save only after the server accepts the credentials, before fetching/parsing courses.
                vault.write(profile)
                savedNewLogin = true
                metadata.edit().remove("auto_login_blocked").putBoolean("needs_login", false).apply()
                mutableState.value = mutableState.value.copy(connected = true, requiresLogin = false)
                schedulePeriodic()
                val (activeSession, page) = client.synchronize(session, password)
                val updated = profile.copy(session = activeSession, year = page.year, term = page.term)
                val events = expand(page, updated)
                vault.write(updated)
                schedule.replace(events, "教务 · ${label(updated)}")
                markSuccess(updated)
                events.size
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(syncing = false)
                if (error is CancellationException) throw error
                if (savedNewLogin) markFailure("教务已登录，课表暂未同步；将自动重试", false)
                throw error
            }
        }
    }

    /** Returns false for recoverable failures, so WorkManager can retry with backoff. */
    suspend fun syncSaved(force: Boolean = false, onlyIfDue: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        initialized.await()
        mutex.withLock {
            if (!force && metadata.getBoolean("auto_login_blocked", false)) return@withLock true
            val next = metadata.getLong("next_sync_at", metadata.getLong("last_sync", 0) + SchoolSyncPolicy.IntervalMillis)
            if (!force && onlyIfDue && !SchoolSyncPolicy.isDue(nowMillis(), next)) return@withLock true
            val profile = try { vault.read() } catch (_: Exception) {
                markFailure("本机登录信息无法解密，请重新登录", true)
                return@withLock true
            } ?: return@withLock true
            mutableState.value = mutableState.value.copy(syncing = true, message = null)
            try {
                val (session, page) = client.synchronize(profile.session, profile.password)
                val updated = profile.copy(session = session, year = page.year, term = page.term, firstWeekOverride = null)
                val events = expand(page, updated)
                vault.write(updated)
                schedule.replace(events, "教务 · ${label(updated)}")
                markSuccess(updated)
                true
            } catch (error: Exception) {
                if (error is CancellationException) { mutableState.value = mutableState.value.copy(syncing = false); throw error }
                val requiresLogin = error is SchoolAuthenticationException ||
                    (error is SchoolLoginRequired && profile.password.isNullOrEmpty())
                val message = when {
                    requiresLogin -> error.message.orEmpty()
                    error is IllegalArgumentException -> error.message ?: "课表解析暂未成功，将自动重试"
                    else -> "教务暂未连接，将自动重试；继续显示缓存课表"
                }
                markFailure(message, requiresLogin)
                requiresLogin
            }
        }
    }

    /** Called every minute while the display is visible; actual requests respect the persisted deadline. */
    fun syncIfDue() { scope.launch { syncSaved(onlyIfDue = true) } }

    suspend fun logout() = withContext(Dispatchers.IO) {
        initialized.await()
        mutex.withLock {
            vault.clear()
            WorkManager.getInstance(context).cancelUniqueWork(WorkName)
            metadata.edit().clear().apply()
            mutableState.value = SchoolSyncState(message = "已退出教务，自动同步已停止；已缓存课表保留")
        }
    }

    private fun expand(page: TimetablePage, profile: SchoolProfile) =
        expandClasses(SchoolHtmlParser.parse(page.html), calendar(page.year, page.term), profile.campus)

    private fun label(profile: SchoolProfile) = if (profile.year.isEmpty()) "教务已连接 · ${profile.campus.label}"
        else "${profile.year} 第${profile.term}学期 · ${profile.campus.label}"
    private fun markSuccess(profile: SchoolProfile) {
        val now = nowMillis()
        metadata.edit().putLong("last_sync", now).putLong("next_sync_at", now + SchoolSyncPolicy.IntervalMillis)
            .putInt("failures", 0).putBoolean("needs_login", false).remove("auto_login_blocked").remove("message").apply()
        mutableState.value = SchoolSyncState(connected = true, lastSync = Instant.ofEpochMilli(now), termLabel = label(profile))
    }
    private fun markFailure(message: String, needsLogin: Boolean) {
        val failures = (metadata.getInt("failures", 0) + 1).coerceAtMost(30)
        metadata.edit().putString("message", message).putBoolean("needs_login", needsLogin)
            .putBoolean("auto_login_blocked", needsLogin).putInt("failures", failures)
            .putLong("next_sync_at", nowMillis() + SchoolSyncPolicy.retryDelayMillis(failures)).apply()
        mutableState.value = mutableState.value.copy(syncing = false, requiresLogin = needsLogin, message = message)
    }
}

private const val WorkName = "school_timetable_sync"
private fun enqueueSchoolSync(context: Context) {
    val work = PeriodicWorkRequestBuilder<SchoolSyncWorker>(15, TimeUnit.MINUTES)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(WorkName, ExistingPeriodicWorkPolicy.KEEP, work)
}

class SchoolSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as ScheduleBoardApplication).schoolSync
        return if (repository.syncSaved()) Result.success() else Result.retry()
    }
}
