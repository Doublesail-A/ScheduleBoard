package com.doublesail.scheduleboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doublesail.scheduleboard.data.BoardSettings
import com.doublesail.scheduleboard.data.SettingsRepository
import com.doublesail.scheduleboard.data.ThemeMode
import com.doublesail.scheduleboard.schedule.ScheduleEvent
import com.doublesail.scheduleboard.schedule.IcsScheduleRepository
import com.doublesail.scheduleboard.schedule.ScheduleSnapshot
import com.doublesail.scheduleboard.schedule.upcomingEvents
import com.doublesail.scheduleboard.tasks.BoardTask
import com.doublesail.scheduleboard.tasks.TaskRepository
import com.doublesail.scheduleboard.time.AndroidBoardClock
import com.doublesail.scheduleboard.school.SchoolSyncRepository
import java.io.InputStream
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BoardUiState(
    val settings: BoardSettings? = null,
    val now: ZonedDateTime = ZonedDateTime.now(),
    val events: List<ScheduleEvent> = emptyList(),
    val schedule: ScheduleSnapshot = ScheduleSnapshot(),
    val tasks: List<BoardTask> = emptyList(),
    val taskError: String? = null,
    val importing: Boolean = false,
    val message: String? = null,
    val syncWarning: String? = null,
)

private data class OperationState(val importing: Boolean = false, val message: String? = null)

class BoardViewModel(
    private val settingsRepository: SettingsRepository,
    private val scheduleRepository: IcsScheduleRepository,
    private val taskRepository: TaskRepository,
    clock: AndroidBoardClock,
    private val school: SchoolSyncRepository,
) : ViewModel() {
    val schoolState = school.state
    fun syncSchool() { viewModelScope.launch { school.syncSaved(force = true) } }
    fun logoutSchool() { viewModelScope.launch { school.logout() } }
    private val operation = MutableStateFlow(OperationState())
    private val now = clock.ticks.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(0), ZonedDateTime.now(),
    )
    val state = combine(
        settingsRepository.settings, now, scheduleRepository.snapshot, taskRepository.snapshot, operation,
    ) { settings, time, schedule, tasks, operation ->
        BoardUiState(settings, time, upcomingEvents(schedule.events, time), schedule,
            tasks.tasks.sortedBy { it.completed }, tasks.error, operation.importing, operation.message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), BoardUiState())

    fun setKeepScreenOn(value: Boolean) = persist { settingsRepository.setKeepScreenOn(value) }
    fun setImmersive(value: Boolean) = persist { settingsRepository.setImmersive(value) }
    fun setDynamicColor(value: Boolean) = persist { settingsRepository.setDynamicColor(value) }
    fun setAutoStart(value: Boolean) = persist { settingsRepository.setAutoStart(value) }
    fun setThemeMode(value: ThemeMode) = persist { settingsRepository.setThemeMode(value) }
    fun setTaskCompleted(id: String, completed: Boolean) = persist { taskRepository.setCompleted(id, completed) }
    fun renameTask(id: String, title: String) = persist { taskRepository.rename(id, title) }
    fun deleteTask(id: String) = persist { taskRepository.delete(id) }
    fun clearCompletedTasks() = persist { taskRepository.clearCompleted() }
    fun dismissError() { operation.value = operation.value.copy(message = null) }

    suspend fun addTask(title: String): Boolean = try {
        taskRepository.add(title)
        true
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        operation.value = operation.value.copy(message = errorMessage(error))
        false
    }

    fun importTimetable(sourceName: String, open: () -> InputStream?) {
        if (operation.value.importing) return
        if (school.state.value.connected) {
            operation.value = operation.value.copy(message = "请先退出教务并停止自动同步，再导入文件")
            return
        }
        operation.value = OperationState(importing = true)
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    val input = requireNotNull(open()) { "无法打开所选文件" }
                    scheduleRepository.import(input, sourceName, ZoneId.systemDefault())
                }
                operation.value = OperationState(message = "已导入 $count 项安排，可离线显示")
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                operation.value = OperationState(message = if (error is IllegalArgumentException)
                    error.message ?: "课表格式不正确，原课表已保留"
                    else "课表导入失败，请选择 iFAFU 导出的 UTF-8 ICS 文件；原课表已保留")
            }
        }
    }

    private fun persist(write: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                write()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                operation.value = operation.value.copy(message = errorMessage(error))
            }
        }
    }

    private fun errorMessage(error: Exception) = if (error is IllegalArgumentException)
        error.message ?: "输入内容不正确" else "保存失败，请检查存储空间后重试"
}
