package com.doublesail.scheduleboard

import android.app.Application
import com.doublesail.scheduleboard.data.SettingsRepository
import com.doublesail.scheduleboard.schedule.IcsScheduleRepository
import com.doublesail.scheduleboard.tasks.LocalTaskRepository
import com.doublesail.scheduleboard.startup.StartupNotifications
import com.doublesail.scheduleboard.time.AndroidBoardClock
import com.doublesail.scheduleboard.school.SchoolSyncRepository

class ScheduleBoardApplication : Application() {
    val settingsRepository by lazy { SettingsRepository(this) }
    val scheduleRepository by lazy { IcsScheduleRepository(this) }
    val taskRepository by lazy { LocalTaskRepository(this) }
    val schoolSync by lazy { SchoolSyncRepository(this, scheduleRepository) }
    val boardClock by lazy { AndroidBoardClock(this) }

    override fun onCreate() {
        super.onCreate()
        StartupNotifications.createChannel(this)
    }
}
