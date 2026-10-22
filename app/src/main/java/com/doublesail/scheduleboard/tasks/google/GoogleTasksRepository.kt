package com.doublesail.scheduleboard.tasks.google

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.*
import com.doublesail.scheduleboard.ScheduleBoardApplication
import com.doublesail.scheduleboard.tasks.TaskRepository
import com.doublesail.scheduleboard.tasks.TaskSnapshot
import com.google.android.gms.common.api.ApiException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.googleTaskStore by preferencesDataStore("google_tasks")
data class GoogleSyncStatus(val syncing: Boolean = false, val message: String? = null, val needsAuthorization: Boolean = false)

class GoogleTasksRepository(context: Context, private val local: TaskRepository,
    private val api: TasksApi = GoogleTasksApi(), private val tokens: TasksTokens = GoogleTasksAuth(context),
    private val scheduleWork: Boolean = true, private val autoSync: Boolean = true,
    dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) : TaskRepository {
    private val context = context.applicationContext
    private val store = dataStore ?: this.context.googleTaskStore
    private val network = Mutex()
    private val source = Mutex()
    private val statusValue = MutableStateFlow(GoogleSyncStatus())
    val status = statusValue.asStateFlow()
    private val cacheFlow = store.data.map { GoogleTaskCache.decode(it[Cache]) }
    val state = cacheFlow.stateIn(scope, SharingStarted.Eagerly, GoogleTaskCache())
    override val snapshot = combine(cacheFlow, local.snapshot) { cloud, device ->
        if (cloud.connected) TaskSnapshot(cloud.tasks) else device
    }
    private var lastAttempt = 0L

    init { if (scheduleWork) scope.launch { if (cacheFlow.first().connected) schedule() } }

    suspend fun connect(account: String) = source.withLock {
        network.withLock {
            val current = cacheFlow.first()
            require(current.edits.isEmpty() || current.account == account) { "请先同步待上传的修改，再更换账号" }
            if (current.account != account) write { GoogleTaskCache(account = account) }
            statusValue.value = GoogleSyncStatus()
            if (scheduleWork) schedule()
        }
        if (autoSync) scope.launch { sync(force = true) }
    }

    suspend fun chooseList(id: String) = source.withLock {
        network.withLock {
            write { old ->
                require(old.edits.isEmpty()) { "请先同步待上传的修改，再切换清单" }
                val list = requireNotNull(old.lists.find { it.id == id }) { "清单不存在，请刷新后重试" }
                old.copy(listId = id, listTitle = list.title, records = emptyList(), lastSync = 0)
            }
        }
        if (autoSync) scope.launch { sync(force = true) }
    }

    /** Local disconnect removes downloaded Google data. Server tasks and system accounts remain intact. */
    suspend fun disconnect(revoke: Boolean = false) = source.withLock {
        network.withLock {
            val current = cacheFlow.first()
            require(current.edits.isEmpty()) { "还有待上传的修改，请同步成功后再断开" }
            if (revoke && current.connected) tokens.revoke(current.account)
            write { old -> require(old.edits.isEmpty()) { "还有待上传的修改，请同步成功后再断开" }; GoogleTaskCache() }
            statusValue.value = GoogleSyncStatus()
            if (scheduleWork) WorkManager.getInstance(context).cancelUniqueWork(WorkName)
        }
    }

    override suspend fun add(title: String) = mutateLocalOrCloud({ local.add(title) }) { cache ->
        require(title.trim().length in 1..200) { "待办名称需要 1–200 个字符" }
        require(cache.records.size < 20_000) { "清单项目过多，请先清理" }
        val id = UUID.randomUUID().toString()
        cache.copy(records = cache.records + CachedGoogleTask(id, title = title.trim()),
            edits = cache.edits + TaskEdit(id, revision(), title.trim(), false))
    }
    override suspend fun rename(id: String, title: String) = mutateLocalOrCloud({ local.rename(id, title) }) { cache ->
        require(title.trim().length in 1..200) { "待办名称需要 1–200 个字符" }
        change(cache, id, title = title.trim())
    }
    override suspend fun setCompleted(id: String, completed: Boolean) = mutateLocalOrCloud({ local.setCompleted(id, completed) }) {
        change(it, id, completed = completed)
    }
    override suspend fun delete(id: String) = mutateLocalOrCloud({ local.delete(id) }) { change(it, id, delete = true) }
    override suspend fun clearCompleted() = mutateLocalOrCloud({ local.clearCompleted() }) { cache ->
        cache.records.filter { it.completed && !it.deleted }.fold(cache) { value, task -> change(value, task.id, delete = true) }
    }

    private suspend fun mutateLocalOrCloud(device: suspend () -> Unit, cloud: (GoogleTaskCache) -> GoogleTaskCache) = source.withLock {
        if (!cacheFlow.first().connected) device() else {
            write { require(it.listId.isNotEmpty()) { "请先在 Google Tasks 设置中选择清单" }; cloud(it) }
            if (autoSync) scope.launch { sync(force = true) }
        }
    }
    private fun change(cache: GoogleTaskCache, id: String, title: String? = null, completed: Boolean? = null, delete: Boolean = false): GoogleTaskCache {
        val task = cache.records.find { it.id == id } ?: return cache
        val previous = cache.edits.find { it.id == id }
        val edit = TaskEdit(id, revision(), title ?: previous?.title, completed ?: previous?.completed, delete)
        // Even an unuploaded task may have reached Google after a lost response. Retain a tombstone until reconciliation.
        return cache.copy(records = cache.records.map { if (it.id == id) task.copy(title = title ?: task.title,
            completed = completed ?: task.completed, deleted = delete) else it }, edits = cache.edits.filterNot { it.id == id } + edit)
    }

    suspend fun sync(force: Boolean = false): Boolean {
        if (!network.tryLock()) return false
        try {
            var cache = cacheFlow.first()
            if (!cache.connected) return true
            val now = System.currentTimeMillis()
            if (!force && now - lastAttempt < 45_000) return true
            lastAttempt = now
            statusValue.value = GoogleSyncStatus(syncing = true)
            var token = tokens.token(cache.account)
            suspend fun <T> authorized(call: suspend (String) -> T): T = try { call(token) }
                catch (error: GoogleTasksHttpException) {
                    if (error.code != 401) throw error
                    tokens.invalidate(token); token = tokens.token(cache.account)
                    call(token)
                }
            val lists = authorized { api.lists(it) }
            if (cache.listId.isNotEmpty() && lists.none { it.id == cache.listId }) {
                write { it.copy(lists = lists) }
                throw IllegalArgumentException("所选 Google 清单已删除，请选择其他清单；缓存和待上传修改已保留")
            }
            val list = lists.find { it.id == cache.listId } ?: lists.firstOrNull()
                ?: throw IllegalArgumentException("此账号没有任务清单，请先在 Google Tasks 中创建清单")
            write { it.copy(lists = lists, listId = list.id, listTitle = list.title) }
            var remote = authorized { api.tasks(it, list.id) }
            cache = cacheFlow.first()
            var remoteDeletedEdits = false
            for (edit in cache.edits) {
                val current = cacheFlow.first()
                val task = current.records.find { it.id == edit.id } ?: continue
                val marker = "[ScheduleBoard:${task.id}]"
                val existing = if (task.remoteId.isNotEmpty()) remote.find { it.id == task.remoteId }
                    else remote.find { marker in it.notes && !it.deleted }
                var result: RemoteTask? = null
                if (edit.delete) {
                    existing?.let { found -> authorized { api.delete(it, list.id, found.id) } }
                } else if (task.remoteId.isNotEmpty() && (existing == null || existing.deleted || existing.hidden)) {
                    remoteDeletedEdits = true
                } else {
                    try {
                        result = if (existing == null) authorized { api.insert(it, list.id, task.title, task.completed, marker) }
                        else authorized { api.patch(it, list.id, existing.id, edit.title, edit.completed) }
                    } catch (error: GoogleTasksHttpException) {
                        if (error.code != 404 && error.code != 410) throw error
                        remoteDeletedEdits = true
                    }
                }
                val acknowledged = result
                write { latest ->
                    val sameRevision = latest.edits.find { it.id == edit.id }?.revision == edit.revision
                    latest.copy(records = latest.records.mapNotNull { row ->
                        if (row.id != edit.id) row
                        else if (acknowledged != null) row.copy(remoteId = acknowledged.id)
                        else if (sameRevision) null else row
                    }, edits = if (sameRevision) latest.edits.filterNot { it.id == edit.id } else latest.edits)
                }
                // A recovered insertion is visible to subsequent operations even before the final full fetch.
                if (result != null) remote = remote.filterNot { it.id == result.id } + result
            }
            remote = authorized { api.tasks(it, list.id) }
            write { it.merge(remote).copy(lastSync = System.currentTimeMillis()) }
            statusValue.value = GoogleSyncStatus(message = if (remoteDeletedEdits) "部分任务已在 Google 中移除，已采用 Google 的删除结果" else null)
            // Changes made during network IO remain queued; drain them without waiting for the periodic worker.
            if (autoSync && cacheFlow.first().edits.isNotEmpty()) scope.launch { delay(500); sync(force = true) }
            return true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            val auth = error is GoogleAuthorizationRequired || error is GoogleTasksHttpException && error.code == 401
            val message = when {
                auth -> "需要重新授权 Google Tasks；待办及未上传修改已保留"
                error is ApiException && error.statusCode == 10 -> "Google 登录尚未配置：请在 Google Cloud 登记此 APK 的包名及签名"
                error is ApiException -> "Google 授权未完成，请检查 Google Play 服务和网络后重试"
                error is GoogleTasksHttpException && error.code == 403 -> "Google Tasks 访问被拒绝，请确认已启用 Tasks API、账号为测试用户并允许访问"
                error is GoogleTasksHttpException && error.code == 429 -> "Google Tasks 请求过多，将稍后重试"
                error is IllegalArgumentException -> error.message ?: "Google Tasks 设置无效"
                else -> "Google Tasks 暂时无法连接，离线修改已保存，将自动重试"
            }
            statusValue.value = GoogleSyncStatus(message = message, needsAuthorization = auth)
            return false
        } finally { network.unlock() }
    }

    private suspend fun write(change: (GoogleTaskCache) -> GoogleTaskCache) {
        store.edit { it[Cache] = change(GoogleTaskCache.decode(it[Cache])).encode() }
    }
    private fun revision() = UUID.randomUUID().toString()
    private fun schedule() {
        val request = PeriodicWorkRequestBuilder<GoogleTasksWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WorkName, ExistingPeriodicWorkPolicy.KEEP, request)
    }
    private companion object { val Cache = stringPreferencesKey("cache"); const val WorkName = "google_tasks_sync" }
}

class GoogleTasksWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val tasks = (applicationContext as ScheduleBoardApplication).taskRepository
        return if (tasks.sync(force = true) || tasks.status.value.needsAuthorization) Result.success() else Result.retry()
    }
}
