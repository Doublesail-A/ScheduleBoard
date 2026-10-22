package com.doublesail.scheduleboard

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.doublesail.scheduleboard.data.BoardSettings
import com.doublesail.scheduleboard.display.BoardWindowController
import com.doublesail.scheduleboard.startup.HomeController
import com.doublesail.scheduleboard.startup.StartupNotifications
import com.doublesail.scheduleboard.ui.BoardApp
import com.doublesail.scheduleboard.ui.BoardViewModel
import com.doublesail.scheduleboard.ui.StartupEnvironment
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {
    private lateinit var windowController: BoardWindowController
    private val homeController by lazy { HomeController(this) }
    private var environment by mutableStateOf(StartupEnvironment())
    private var systemActionFailed by mutableStateOf(false)
    private var pomodoroRequest by mutableStateOf(0)
    private val boardViewModel: BoardViewModel by viewModels {
        val app = application as ScheduleBoardApplication
        viewModelFactory {
            initializer { BoardViewModel(app.settingsRepository, app.scheduleRepository, app.taskRepository, app.boardClock, app.schoolSync) }
        }
    }
    private val homeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshEnvironment()
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshEnvironment()
    }
    private val timetablePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val resolver = applicationContext.contentResolver
            boardViewModel.importTimetable("iFAFU 课表") { resolver.openInputStream(uri) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("open_pomodoro", false)) pomodoroRequest++
        enableEdgeToEdge()
        windowController = BoardWindowController(this)
        windowController.apply(BoardSettings())
        windowController.setBootWakeup(intent.getBooleanExtra(StartupNotifications.ExtraBootLaunch, false))

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    (application as ScheduleBoardApplication).settingsRepository.settings.collect {
                        windowController.apply(it)
                    }
                }
                launch {
                    while (isActive) {
                        (application as ScheduleBoardApplication).schoolSync.syncIfDue()
                        (application as ScheduleBoardApplication).taskRepository.sync()
                        delay(60_000L)
                    }
                }
            }
        }
        setContent {
            BoardApp(
                viewModel = boardViewModel,
                environment = environment,
                systemActionFailed = systemActionFailed,
                onDismissSystemError = { systemActionFailed = false },
                onChooseHome = { performSystemAction { homeLauncher.launch(homeController.selectionIntent()) } },
                onOpenSystemSettings = { performSystemAction { startActivity(Intent(Settings.ACTION_SETTINGS)) } },
                onEnableNotifications = ::enableNotifications,
                onImportTimetable = { performSystemAction { timetablePicker.launch(arrayOf("*/*")) } },
                onSchoolLogin = { startActivity(Intent(this, com.doublesail.scheduleboard.school.SchoolLoginActivity::class.java)) },
                googleTasks = (application as ScheduleBoardApplication).taskRepository,
                pomodoro = (application as ScheduleBoardApplication).pomodoro,
                onGoogleTasks = { startActivity(Intent(this, com.doublesail.scheduleboard.tasks.google.GoogleTasksActivity::class.java)) },
                onExactAlarmSettings = { performSystemAction {
                    if (Build.VERSION.SDK_INT >= 31) startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        "package:$packageName".toUri()))
                } }, openPomodoroRequest = pomodoroRequest,
                onDarkTheme = { dark ->
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshEnvironment()
        StartupNotifications.cancel(this)
        (application as ScheduleBoardApplication).schoolSync.syncIfDue()
        lifecycleScope.launch { (application as ScheduleBoardApplication).taskRepository.sync() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_pomodoro", false)) pomodoroRequest++
        windowController.setBootWakeup(intent.getBooleanExtra(StartupNotifications.ExtraBootLaunch, false))
    }

    override fun onStop() {
        windowController.setBootWakeup(false)
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::windowController.isInitialized) windowController.restoreSystemBars()
    }

    private fun refreshEnvironment() {
        environment = StartupEnvironment(
            isDefaultHome = homeController.isDefaultHome(),
            notificationsEnabled = StartupNotifications.canNotify(this),
        )
    }

    private fun enableNotifications() = performSystemAction {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        } else {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
        }
    }

    private fun performSystemAction(action: () -> Unit) {
        try {
            action()
        } catch (_: ActivityNotFoundException) {
            systemActionFailed = true
        } catch (_: SecurityException) {
            systemActionFailed = true
        }
    }
}
