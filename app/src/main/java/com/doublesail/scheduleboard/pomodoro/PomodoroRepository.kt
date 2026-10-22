package com.doublesail.scheduleboard.pomodoro

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.doublesail.scheduleboard.MainActivity
import com.doublesail.scheduleboard.R
import com.doublesail.scheduleboard.ScheduleBoardApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

class PomodoroRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences("pomodoro", Context.MODE_PRIVATE)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(read())
    val state = mutableState.asStateFlow()
    private val mutableRemaining = MutableStateFlow(mutableState.value.remainingMs)
    val remaining = mutableRemaining.asStateFlow()
    val exactAlarmsAllowed get() = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    init {
        createChannel()
        scope.launch {
            restore()
            while (isActive) {
                val current = mutableState.value
                mutableRemaining.value = current.remaining(SystemClock.elapsedRealtime(), System.currentTimeMillis(), boot())
                if (current.running && mutableRemaining.value == 0L) finishIfDue()
                delay(250)
            }
        }
    }

    suspend fun restore() = update { current -> current.restored(SystemClock.elapsedRealtime(), System.currentTimeMillis(), boot()) }
    suspend fun toggle() = update { current ->
        if (current.running) current.pause(SystemClock.elapsedRealtime(), System.currentTimeMillis(), boot())
        else current.start(SystemClock.elapsedRealtime(), System.currentTimeMillis(), boot())
    }
    suspend fun reset() = update { it.select(it.phase, (it.durationMs / 60_000).toInt()) }
    suspend fun select(phase: PomodoroPhase, minutes: Int = phase.minutes) = update { it.select(phase, minutes) }
    suspend fun next() = update { it.select(it.nextPhase()) }
    suspend fun finishIfDue() = restore()

    private suspend fun update(change: (PomodoroState) -> PomodoroState) = mutex.withLock {
        val old = mutableState.value
        val next = change(old)
        if (next != old) {
            check(preferences.edit().putString("state", encode(next)).commit()) { "无法保存番茄钟，请检查存储空间" }
            mutableState.value = next
        }
        mutableRemaining.value = next.remaining(SystemClock.elapsedRealtime(), System.currentTimeMillis(), boot())
        schedule(next)
        if (!old.finished && next.finished) notifyFinished(next)
        else if (!next.finished) NotificationManagerCompat.from(context).cancel(NotificationId)
    }

    private fun alarmIntent() = PendingIntent.getBroadcast(context, 620, Intent(context, PomodoroAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun schedule(state: PomodoroState) {
        alarms.cancel(alarmIntent())
        if (!state.running) return
        val deadline = SystemClock.elapsedRealtime() + state.remaining(SystemClock.elapsedRealtime(), System.currentTimeMillis(), boot())
        try {
            if (exactAlarmsAllowed) alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, alarmIntent())
            else alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, alarmIntent())
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, alarmIntent())
        }
    }

    private fun boot() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    private fun read(): PomodoroState = try {
        val json = JSONObject(preferences.getString("state", "{}") ?: "{}")
        val phase = PomodoroPhase.valueOf(json.optString("phase", "FOCUS"))
        val duration = json.optLong("duration", phase.minutes * 60_000L).coerceIn(60_000, 180 * 60_000L)
        PomodoroState(phase, duration, json.optLong("remaining", duration).coerceIn(0, duration),
            json.optBoolean("running"), json.optBoolean("finished"), json.optLong("elapsed"),
            json.optLong("wall"), json.optInt("boot", -1), json.optInt("completed").coerceAtLeast(0))
    } catch (_: Exception) { PomodoroState() }

    private fun encode(state: PomodoroState) = JSONObject().apply {
        put("phase", state.phase.name); put("duration", state.durationMs); put("remaining", state.remainingMs)
        put("running", state.running); put("finished", state.finished); put("elapsed", state.deadlineElapsed)
        put("wall", state.deadlineWall); put("boot", state.bootCount); put("completed", state.completedFocus)
    }.toString()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(ChannelId, "番茄钟结束提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            })
    }

    private fun notifyFinished(state: PomodoroState) {
        val open = PendingIntent.getActivity(context, 621, Intent(context, MainActivity::class.java)
            .putExtra("open_pomodoro", true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, ChannelId).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${state.phase.label}时间到")
            .setContentText(if (state.phase == PomodoroPhase.FOCUS) "完成了一次专注，休息一下吧" else "休息结束，准备开始下一次专注")
            .setContentIntent(open).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setDefaults(NotificationCompat.DEFAULT_ALL).build()
        try { NotificationManagerCompat.from(context).notify(NotificationId, notification) } catch (_: SecurityException) { /* UI still shows completion. */ }
    }
    private companion object { const val ChannelId = "pomodoro_finished"; const val NotificationId = 621 }
}

class PomodoroAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { withTimeout(8_000) { (context.applicationContext as ScheduleBoardApplication).pomodoro.restore() } }
            finally { result.finish() }
        }
    }
}
