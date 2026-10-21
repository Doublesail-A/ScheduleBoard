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
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val school by viewModel.schoolState.collectAsStateWithLifecycle()
    val settings = state.settings ?: BoardSettings()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var addingTask by rememberSaveable { mutableStateOf(false) }
    var taskTitle by rememberSaveable { mutableStateOf("") }
    var savingTask by remember { mutableStateOf(false) }
    var selectedEvent by remember { mutableStateOf<ScheduleEvent?>(null) }
    val scope = rememberCoroutineScope()
    BackHandler(enabled = environment.isDefaultHome && !settingsOpen) { settingsOpen = true }

    ScheduleBoardTheme(settings, onDarkTheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (state.settings == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                BoardDashboard(state.copy(syncWarning = if (school.requiresLogin) "教务登录已失效，点击时钟重新登录"
                    else school.message?.takeIf { school.connected }), onOpenSettings = { settingsOpen = true },
                    onAddTask = { addingTask = true }, onTaskCompleted = viewModel::setTaskCompleted,
                    onEventClick = { selectedEvent = it })
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
                onClearCompleted = viewModel::clearCompletedTasks,
                school = school, onSchoolLogin = onSchoolLogin,
                onSyncSchool = viewModel::syncSchool, onLogoutSchool = viewModel::logoutSchool,
            )
        }
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
                    TextButton(onClick = onSyncSchool, enabled = !school.syncing && !school.requiresLogin) {
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
                TextButton(onClick = onAddTask) { Text("添加本机待办") }
                TextButton(onClick = onClearCompleted, enabled = state.tasks.any { it.completed }) { Text("清理已完成待办") }
                state.taskError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
