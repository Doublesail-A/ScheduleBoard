package com.doublesail.scheduleboard.startup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.doublesail.scheduleboard.ScheduleBoardApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                withTimeout(8_000) {
                    val app = context.applicationContext as ScheduleBoardApplication
                    val settings = app.settingsRepository.settings.first()
                    when (bootAction(settings.autoStart, HomeController(context).isDefaultHome(), Build.VERSION.SDK_INT)) {
                        BootAction.NONE, BootAction.SYSTEM_HOME -> Unit
                        BootAction.OPEN_ACTIVITY -> {
                            try {
                                context.startActivity(StartupNotifications.launchIntent(context))
                            } catch (error: RuntimeException) {
                                Log.w("BoardBoot", "Activity launch rejected; posting notification", error)
                                StartupNotifications.show(context)
                            }
                        }
                        BootAction.SHOW_NOTIFICATION -> StartupNotifications.show(context)
                    }
                }
            } catch (error: Exception) {
                Log.e("BoardBoot", "Boot setup did not complete", error)
            } finally {
                pending.finish()
                scope.cancel()
            }
        }
    }
}
