package com.doublesail.scheduleboard.tasks

import kotlinx.coroutines.flow.Flow

data class BoardTask(val id: String, val title: String, val completed: Boolean = false)
data class TaskSnapshot(val tasks: List<BoardTask> = emptyList(), val error: String? = null)

/** A future Google Tasks / CalDAV provider can implement the same UI contract. */
interface TaskRepository {
    val snapshot: Flow<TaskSnapshot>
    suspend fun add(title: String)
    suspend fun setCompleted(id: String, completed: Boolean)
    suspend fun clearCompleted()
}
