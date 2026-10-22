package com.doublesail.scheduleboard.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doublesail.scheduleboard.R
import com.doublesail.scheduleboard.data.BoardSettings
import com.doublesail.scheduleboard.data.ThemeMode
import com.doublesail.scheduleboard.schedule.ScheduleEvent
import com.doublesail.scheduleboard.school.SchoolSyncState
import com.doublesail.scheduleboard.ui.theme.ScheduleBoardTheme
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import com.doublesail.scheduleboard.tasks.BoardTask
import com.doublesail.scheduleboard.tasks.google.GoogleTasksRepository
import com.doublesail.scheduleboard.pomodoro.PomodoroRepository

data class StartupEnvironment(
    val isDefaultHome: Boolean = false,
    val notificationsEnabled: Boolean = false,
)

@Composable
fun BoardApp(
    viewModel: BoardViewModel,
    environment: StartupEnvironment,
    systemActionFailed: Boolean,
    onDismissSystemError: () -> Unit,
    onChooseHome: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onEnableNotifications: () -> Unit,
    onImportTimetable: () -> Unit,
    onSchoolLogin: () -> Unit,
    onDarkTheme: (Boolean) -> Unit,
    googleTasks: GoogleTasksRepository,
    pomodoro: PomodoroRepository,
    onGoogleTasks: () -> Unit,
    onExactAlarmSettings: () -> Unit,
    openPomodoroRequest: Int = 0,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val school by viewModel.schoolState.collectAsStateWithLifecycle()
    val google by googleTasks.state.collectAsStateWithLifecycle()
    val googleStatus by googleTasks.status.collectAsStateWithLifecycle()
    val timer by pomodoro.state.collectAsStateWithLifecycle()
    val remaining by pomodoro.remaining.collectAsStateWithLifecycle()
    var timerOpen by rememberSaveable { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<BoardTask?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val settings = state.settings ?: BoardSettings()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var addingTask by rememberSaveable { mutableStateOf(false) }
    var taskTitle by rememberSaveable { mutableStateOf("") }
    var savingTask by remember { mutableStateOf(false) }
    var selectedEvent by remember { mutableStateOf<ScheduleEvent?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(openPomodoroRequest) { if (openPomodoroRequest > 0) timerOpen = true }
    BackHandler(enabled = environment.isDefaultHome && !settingsOpen && !timerOpen) { settingsOpen = true }
    BackHandler(enabled = timerOpen) { timerOpen = false }

    ScheduleBoardTheme(settings, onDarkTheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (state.settings == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (timerOpen) {
                PomodoroScreen(timer, remaining, onToggle = { scope.launch { pomodoro.toggle() } },
                    onReset = { scope.launch { pomodoro.reset() } },
                    onSelect = { phase, minutes -> scope.launch { pomodoro.select(phase, minutes) } },
                    onNext = { scope.launch { pomodoro.next() } }, onBack = { timerOpen = false },
                    precise = pomodoro.exactAlarmsAllowed, onExactAlarmSettings = onExactAlarmSettings,
                    notifications = environment.notificationsEnabled, onNotifications = onEnableNotifications)
            } else {
                BoardDashboard(state.copy(syncWarning = if (school.requiresLogin) school.message ?: "请点击时钟更新教务登录信息"
                    else school.message?.takeIf { school.connected }), onOpenSettings = { settingsOpen = true },
                    onAddTask = { addingTask = true }, onTaskCompleted = viewModel::setTaskCompleted,
                    onEventClick = { selectedEvent = it }, onOpenPomodoro = { timerOpen = true },
                    onEditTask = { editingTask = it; editTitle = it.title })
            }
        }
        if (settingsOpen) {
            SettingsDialog(
                settings = settings,
                environment = environment,
                onDismiss = { settingsOpen = false },
                onKeepScreenOn = viewModel::setKeepScreenOn,
                onImmersive = viewModel::setImmersive,
                onDynamicColor = viewModel::setDynamicColor,
                onAutoStart = viewModel::setAutoStart,
                onTheme = viewModel::setThemeMode,
                onChooseHome = onChooseHome,
                onOpenSystemSettings = onOpenSystemSettings,
                onEnableNotifications = onEnableNotifications,
                state = state,
                onImportTimetable = onImportTimetable,
                onAddTask = { addingTask = true },
                onClearCompleted = { confirmClear = true },
                school = school, onSchoolLogin = onSchoolLogin,
                onSyncSchool = viewModel::syncSchool, onLogoutSchool = viewModel::logoutSchool,
                googleSummary = if (!google.connected) "当前使用本机待办" else
                    "${google.listTitle} · ${google.edits.size} 项待上传" + if (googleStatus.syncing) " · 同步中" else "",
                googleMessage = googleStatus.message, onGoogleTasks = onGoogleTasks,
                onPomodoro = { settingsOpen = false; timerOpen = true },
            )
        }
        editingTask?.let { task ->
            AlertDialog(onDismissRequest = { editingTask = null }, title = { Text("编辑待办") },
                text = { OutlinedTextField(editTitle, { editTitle = it }, label = { Text("待办事项") },
                    isError = editTitle.trim().length !in 1..200, supportingText = { Text("${editTitle.length}/200") }, maxLines = 3) },
                confirmButton = { TextButton(enabled = editTitle.trim().length in 1..200, onClick = {
                    viewModel.renameTask(task.id, editTitle); editingTask = null
                }) { Text("保存") } },
                dismissButton = { Row {
                    TextButton(onClick = { confirmDelete = true }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { editingTask = null }) { Text("取消") }
                } })
            if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("删除此待办？") },
                text = { Text(if (google.connected) "“${task.title}”也会从 Google Tasks 中删除；若它有子任务，子任务也会一起删除。" else task.title) },
                confirmButton = { TextButton(onClick = { viewModel.deleteTask(task.id); confirmDelete = false; editingTask = null }) { Text("删除") } },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } })
        }
        if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("删除已完成待办？") },
            text = { Text(if (google.connected) "将删除已完成项目及其子任务，并同步到 Google Tasks。" else "将删除本机的已完成项目。") },
            confirmButton = { TextButton(onClick = { viewModel.clearCompletedTasks(); confirmClear = false }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } })
        if (state.message != null || systemActionFailed) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissError(); onDismissSystemError() },
                text = { Text(if (systemActionFailed) stringResource(R.string.system_action_error) else state.message.orEmpty()) },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissError(); onDismissSystemError() }) {
                        Text(stringResource(R.string.dismiss))
                    }
                },
            )
        }
        if (addingTask) {
            AlertDialog(
                onDismissRequest = { if (!savingTask) addingTask = false },
                title = { Text("添加待办") },
                text = {
                    OutlinedTextField(value = taskTitle, onValueChange = { taskTitle = it }, label = { Text("待办事项") },
                        isError = taskTitle.length > 200, supportingText = { Text("${taskTitle.length}/200") },
                        minLines = 1, maxLines = 3)
                },
                confirmButton = {
                    TextButton(enabled = taskTitle.trim().length in 1..200 && !savingTask, onClick = {
                        scope.launch {
                            savingTask = true
                            if (viewModel.addTask(taskTitle)) { addingTask = false; taskTitle = "" }
                            savingTask = false
                        }
                    }) { Text(if (savingTask) "保存中" else "添加") }
                },
                dismissButton = { TextButton(onClick = { addingTask = false }, enabled = !savingTask) { Text("取消") } },
            )
        }
        selectedEvent?.let { event ->
            AlertDialog(onDismissRequest = { selectedEvent = null }, title = { Text(event.title) },
                text = {
                    val format = DateTimeFormatter.ofPattern("M月d日 HH:mm")
                    val start = event.start.atZone(state.now.zone).format(format)
                    val end = event.endExclusive.atZone(state.now.zone).format(format)
                    Text(listOf("$start – $end", event.location, event.description).filter(String::isNotBlank).joinToString("\n\n"),
                        modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()))
                }, confirmButton = { TextButton(onClick = { selectedEvent = null }) { Text("关闭") } })
        }
    }
}

@Composable
private fun SettingsDialog(
    settings: BoardSettings,
    environment: StartupEnvironment,
    onDismiss: () -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onImmersive: (Boolean) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onAutoStart: (Boolean) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onChooseHome: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onEnableNotifications: () -> Unit,
    state: BoardUiState,
    onImportTimetable: () -> Unit,
    onAddTask: () -> Unit,
    onClearCompleted: () -> Unit,
    school: SchoolSyncState,
    onSchoolLogin: () -> Unit,
    onSyncSchool: () -> Unit,
    onLogoutSchool: () -> Unit,
    googleSummary: String,
    googleMessage: String?,
    onGoogleTasks: () -> Unit,
    onPomodoro: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            Column(
                Modifier.widthIn(max = 480.dp).heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("课表与待办", style = MaterialTheme.typography.titleSmall)
                FilledTonalButton(onClick = onSchoolLogin) { Text(if (school.connected) "重新登录 / 更换账号" else "登录福建农林大学教务") }
                if (school.connected) {
                    Text(school.termLabel, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onSyncSchool, enabled = !school.syncing) {
                        Text(if (school.syncing) "正在同步…" else "立即同步课表")
                    }
                    TextButton(onClick = onLogoutSchool) { Text("退出教务并停止自动同步") }
                }
                school.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(state.schedule.error ?: if (state.schedule.sourceName.isEmpty()) "尚未导入课表" else
                    "${state.schedule.sourceName} · ${state.schedule.events.size} 项安排", style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick = onImportTimetable, enabled = !state.importing && !school.connected) {
                    Text(if (state.importing) "正在导入…" else "导入 iFAFU ICS 课表")
                }
                Text("在 iFAFU 的“提醒与日历导入”中导出完整 ICS，再传到此手机。重新导入会替换当前课表。",
                    style = MaterialTheme.typography.bodySmall)
                if (school.connected) Text("正在使用教务自动同步；如需导入文件，请先退出教务。", style = MaterialTheme.typography.bodySmall)
                state.schedule.importedAt?.let { stamp ->
                    Text("最近更新：" + stamp.atZone(state.now.zone).format(DateTimeFormatter.ofPattern("M月d日 HH:mm")),
                        style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
                Text(googleSummary, style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick = onGoogleTasks) { Text("Google Tasks 账号与同步") }
                googleMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Text("点击待办勾选，长按待办可重命名或删除。", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onAddTask) { Text("添加待办") }
                TextButton(onClick = onClearCompleted, enabled = state.tasks.any { it.completed }) { Text("清理已完成待办") }
                state.taskError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onPomodoro) { Text("打开番茄钟（主页左侧下滑）") }
                HorizontalDivider()
                ToggleRow(stringResource(R.string.keep_screen_on), settings.keepScreenOn, onKeepScreenOn)
                ToggleRow(stringResource(R.string.immersive), settings.immersive, onImmersive)
                ToggleRow(
                    stringResource(R.string.dynamic_color), settings.dynamicColor, onDynamicColor,
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                )
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    Text(stringResource(R.string.dynamic_color_unavailable), style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.theme_title), style = MaterialTheme.typography.labelLarge)
                ThemeMode.entries.forEach { mode ->
                    val label = when (mode) {
                        ThemeMode.SYSTEM -> R.string.theme_system
                        ThemeMode.LIGHT -> R.string.theme_light
                        ThemeMode.DARK -> R.string.theme_dark
                    }
                    androidx.compose.material3.FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { onTheme(mode) },
                        label = { Text(stringResource(label)) },
                    )
                }
                HorizontalDivider()
                ToggleRow(stringResource(R.string.auto_start), settings.autoStart, onAutoStart)
                Text(stringResource(R.string.auto_start_detail), style = MaterialTheme.typography.bodySmall)
                Text(
                    stringResource(if (environment.isDefaultHome) R.string.home_selected else R.string.home_not_selected),
                    style = MaterialTheme.typography.bodyMedium,
                )
                FilledTonalButton(onClick = onChooseHome, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (environment.isDefaultHome) R.string.home_settings else R.string.choose_home))
                }
                if (!environment.notificationsEnabled) {
                    TextButton(onClick = onEnableNotifications) {
                        Text(stringResource(R.string.notification_permission))
                    }
                }
                TextButton(onClick = onOpenSystemSettings) {
                    Text(stringResource(R.string.system_settings))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
        },
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChecked),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
