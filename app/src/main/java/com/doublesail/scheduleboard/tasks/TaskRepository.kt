package com.doublesail.scheduleboard.tasks

import kotlinx.coroutines.flow.Flow

data class BoardTask(val id: String, val title: String, val completed: Boolean = false)
data class TaskSnapshot(val tasks: List<BoardTask> = emptyList(), val error: String? = null)

interface TaskRepository {
    val snapshot: Flow<TaskSnapshot>
    suspend fun add(title: String)
    suspend fun setCompleted(id: String, completed: Boolean)
    suspend fun clearCompleted()
    suspend fun rename(id: String, title: String)
    suspend fun delete(id: String)
}
