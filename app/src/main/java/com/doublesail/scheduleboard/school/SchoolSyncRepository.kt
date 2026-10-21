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
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
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

class SchoolSyncRepository(private val context: Context, private val schedule: IcsScheduleRepository) {
    private val vault = SchoolSessionVault(context)
    private val client = SchoolClient()
    private val calendars = SchoolCalendarStore(context)
    private val mutex = Mutex()
    private val metadata = context.getSharedPreferences("school_sync_status", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(SchoolSyncState())
    val state = mutableState.asStateFlow()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            mutex.withLock {
                val profile = runCatching { vault.read() }.getOrNull()
                mutableState.value = SchoolSyncState(connected = profile != null,
                    requiresLogin = metadata.getBoolean("needs_login", false),
                    lastSync = metadata.getLong("last_sync", 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli),
                    message = metadata.getString("message", null), termLabel = profile?.let(::label).orEmpty())
                if (profile != null) scheduleWorker()
            }
        }
    }

    suspend fun challenge(): SchoolChallenge = withContext(Dispatchers.IO) { client.challenge() }

    suspend fun loginAndSync(challenge: SchoolChallenge, account: String, password: String, captcha: String,
        year: String, term: String, campus: Campus, firstWeek: LocalDate?): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            mutableState.value = mutableState.value.copy(syncing = true)
            try {
                val session = client.login(challenge, account.trim(), password, captcha)
                val profile = SchoolProfile(session, year, term, campus, firstWeek)
                val events = fetch(profile)
                vault.write(profile)
                schedule.replace(events, "教务 · ${label(profile)}")
                markSuccess(profile)
                scheduleWorker()
                events.size
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(syncing = false)
                throw error
            }
        }
    }

    /** Returns false only for a transient network/storage failure, allowing WorkManager backoff. */
    suspend fun syncSaved(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (metadata.getBoolean("needs_login", false)) return@withLock true
            val profile = try { vault.read() } catch (_: Exception) {
                markFailure("本机登录会话无法解密，请重新登录", true)
                return@withLock true
            } ?: return@withLock true
            mutableState.value = mutableState.value.copy(syncing = true)
            try {
                val events = fetch(profile)
                schedule.replace(events, "教务 · ${label(profile)}")
                markSuccess(profile)
                true
            } catch (error: Exception) {
                if (error is CancellationException) { mutableState.value = mutableState.value.copy(syncing = false); throw error }
                val requiresLogin = error is SchoolLoginRequired
                val message = if (requiresLogin || error is IllegalArgumentException) error.message.orEmpty()
                    else "同步暂未成功，继续显示上次课表；网络恢复后会重试"
                markFailure(message, requiresLogin)
                requiresLogin || error is IllegalArgumentException
            }
        }
    }

    fun syncIfDue() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            if (System.currentTimeMillis() - metadata.getLong("last_sync", 0) >= 15 * 60_000L) syncSaved()
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        mutex.withLock {
            vault.clear()
            WorkManager.getInstance(context).cancelUniqueWork(WorkName)
            metadata.edit().clear().apply()
            mutableState.value = SchoolSyncState(message = "已退出教务，自动同步已停止；已缓存课表保留")
        }
    }

    private fun fetch(profile: SchoolProfile) = client.timetable(profile.session, profile.year, profile.term).let { page ->
        val patterns = SchoolHtmlParser.parse(page.html)
        val calendar = calendars.resolve(page.year, page.term, profile.firstWeekOverride)
        expandClasses(patterns, calendar, profile.campus)
    }

    private fun label(profile: SchoolProfile) = "${profile.year} 第${profile.term}学期 · ${profile.campus.label}"
    private fun markSuccess(profile: SchoolProfile) {
        val now = Instant.now()
        metadata.edit().putLong("last_sync", now.toEpochMilli()).putBoolean("needs_login", false).remove("message").apply()
        mutableState.value = SchoolSyncState(connected = true, lastSync = now, termLabel = label(profile))
    }
    private fun markFailure(message: String, needsLogin: Boolean) {
        metadata.edit().putString("message", message).putBoolean("needs_login", needsLogin).apply()
        mutableState.value = mutableState.value.copy(syncing = false, requiresLogin = needsLogin, message = message)
    }
    private fun scheduleWorker() {
        val work = PeriodicWorkRequestBuilder<SchoolSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WorkName, ExistingPeriodicWorkPolicy.KEEP, work)
    }
    private companion object { const val WorkName = "school_timetable_sync" }
}

class SchoolSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as ScheduleBoardApplication).schoolSync
        return if (repository.syncSaved()) Result.success() else Result.retry()
    }
}
