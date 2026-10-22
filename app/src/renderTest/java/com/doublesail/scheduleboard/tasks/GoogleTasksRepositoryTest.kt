package com.doublesail.scheduleboard.tasks

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.doublesail.scheduleboard.tasks.google.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class GoogleTasksRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = PreferenceDataStoreFactory.create(scope = scope) {
        File(ApplicationProvider.getApplicationContext<Application>().cacheDir, "tasks-${java.util.UUID.randomUUID()}.preferences_pb")
    }
    private val local = object : TaskRepository {
        override val snapshot = MutableStateFlow(TaskSnapshot(listOf(BoardTask("local", "本机任务"))))
        override suspend fun add(title: String) { snapshot.value = TaskSnapshot(snapshot.value.tasks + BoardTask("new", title)) }
        override suspend fun setCompleted(id: String, completed: Boolean) {}
        override suspend fun clearCompleted() {}
        override suspend fun rename(id: String, title: String) {}
        override suspend fun delete(id: String) {}
    }
    private val api = FakeApi()
    private val tokens = object : TasksTokens {
        var count = 0
        var required = false
        override suspend fun token(account: String): String { if (required) throw GoogleAuthorizationRequired(); return "token${++count}" }
        override suspend fun invalidate(token: String) { }
    }
    private fun repository() = GoogleTasksRepository(ApplicationProvider.getApplicationContext(), local, api, tokens,
        scheduleWork = false, autoSync = false, dataStore = store, scope = scope)
    private val repo by lazy { repository() }
    private suspend fun connected() { repo.connect("fixture@example.test"); assertTrue(repo.sync(true)) }
    @After fun close() { scope.cancel() }

    @Test fun offlineChangesSurviveRecreationAndUploadBothDirections() = runBlocking {
        connected()
        repo.add("离线新增")
        val id = repo.snapshot.first().tasks.single().id
        repo.setCompleted(id, true)
        val reopened = repository()
        assertTrue(reopened.snapshot.first().tasks.single().completed)
        assertEquals(0, api.remote.size)
        assertTrue(reopened.sync(true))
        assertEquals(1, api.remote.size)
        assertTrue(api.remote.values.single().completed)
        api.remote.replaceAll { _, item -> item.copy(title = "Google 中改名", completed = false) }
        assertTrue(reopened.sync(true))
        assertEquals("Google 中改名", reopened.snapshot.first().tasks.single().title)
        assertFalse(reopened.snapshot.first().tasks.single().completed)
        api.remote.clear()
        assertTrue(reopened.sync(true))
        assertTrue(reopened.snapshot.first().tasks.isEmpty())
    }
    @Test fun lostInsertResponseIsReconciledWithoutDuplicateCreation() = runBlocking {
        connected()
        repo.add("只应创建一次")
        api.loseInsertResponse = true
        assertFalse(repo.sync(true))
        assertEquals(1, api.remote.size)
        assertTrue(repo.sync(true))
        assertEquals(1, api.inserts)
        assertEquals(1, repo.snapshot.first().tasks.size)
        assertTrue(repo.state.first { it.lastSync > 0 && it.edits.isEmpty() }.edits.isEmpty())
    }
    @Test fun deleteAfterUncertainInsertionFindsServerCopyInsteadOfLeavingOrphan() = runBlocking {
        connected(); repo.add("丢失应答")
        val id = repo.snapshot.first().tasks.single().id
        api.loseInsertResponse = true
        assertFalse(repo.sync(true))
        repo.delete(id)
        assertTrue(repo.sync(true))
        assertTrue(api.remote.isEmpty())
        assertTrue(repo.snapshot.first().tasks.isEmpty())
    }
    @Test fun networkFailureDoesNotReplaceCacheOrDropPendingChanges() = runBlocking {
        api.remote["r1"] = RemoteTask("r1", "cached")
        connected(); repo.rename("r1", "local change")
        api.offline = true
        assertFalse(repo.sync(true))
        assertEquals("local change", repo.snapshot.first().tasks.single().title)
        assertEquals(1, repo.state.first { it.edits.isNotEmpty() }.edits.size)
        api.offline = false
        assertTrue(repo.sync(true))
        assertEquals("local change", api.remote["r1"]!!.title)
    }
    @Test fun newerEditDuringRequestIsNotRemovedByOldAcknowledgement() = runBlocking {
        api.remote["r1"] = RemoteTask("r1", "original")
        connected(); repo.setCompleted("r1", true)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        api.onPatch = { entered.complete(Unit); release.await() }
        val sync = async(Dispatchers.IO) { repo.sync(true) }
        withTimeout(5_000) { entered.await() }
        repo.rename("r1", "newer title")
        release.complete(Unit)
        assertTrue(sync.await())
        assertEquals("newer title", repo.snapshot.first().tasks.single().title)
        api.onPatch = null
        assertTrue(repo.sync(true))
        assertEquals("newer title", api.remote["r1"]!!.title)
        assertTrue(api.remote["r1"]!!.completed)
    }
    @Test fun expiredTokenRefreshesOnceAndRevokedAuthorizationPreservesOutbox() = runBlocking {
        connected(); repo.add("pending")
        api.unauthorizedOnce = true
        assertTrue(repo.sync(true))
        assertTrue(tokens.count >= 3)
        repo.add("needs auth")
        tokens.required = true
        assertFalse(repo.sync(true))
        assertTrue(repo.status.value.needsAuthorization)
        assertEquals(2, repo.snapshot.first().tasks.size)
        tokens.required = false
        repo.connect("fixture@example.test")
        assertTrue(repo.sync(true))
        assertEquals(2, api.remote.size)
    }
    @Test fun pendingChangesCannotLeakToDifferentAccountOrList() = runBlocking {
        connected(); repo.add("private")
        try { repo.connect("other@example.test"); fail() } catch (_: IllegalArgumentException) { }
        try { repo.chooseList("second"); fail() } catch (_: IllegalArgumentException) { }
        try { repo.disconnect(); fail() } catch (_: IllegalArgumentException) { }
        assertEquals("private", repo.snapshot.first().tasks.single().title)
        assertTrue(repo.sync(true))
        repo.disconnect()
        assertEquals("本机任务", repo.snapshot.first().tasks.single().title)
        assertEquals(1, api.remote.size)
    }
    @Test fun remoteDeletionWinsOverOfflineEditWithoutResurrection() = runBlocking {
        api.remote["r1"] = RemoteTask("r1", "original")
        connected(); repo.rename("r1", "pending rename"); api.remote.clear()
        assertTrue(repo.sync(true))
        assertTrue(repo.snapshot.first().tasks.isEmpty())
        assertEquals(0, api.inserts)
        assertTrue(repo.state.first { it.edits.isEmpty() }.edits.isEmpty())
    }
    @Test fun clearCompletedDeletesOnlyCompletedTasksOnGoogle() = runBlocking {
        api.remote["a"] = RemoteTask("a", "done", completed = true)
        api.remote["b"] = RemoteTask("b", "keep")
        connected(); repo.clearCompleted(); assertTrue(repo.sync(true))
        assertEquals(listOf("b"), api.remote.keys.toList())
    }

    private class FakeApi : TasksApi {
        val remote = linkedMapOf<String, RemoteTask>()
        var inserts = 0
        var offline = false
        var loseInsertResponse = false
        var unauthorizedOnce = false
        var onPatch: (suspend () -> Unit)? = null
        override suspend fun lists(token: String): List<GoogleTaskList> {
            if (offline) throw IOException("fixture offline")
            if (unauthorizedOnce) { unauthorizedOnce = false; throw GoogleTasksHttpException(401) }
            return listOf(GoogleTaskList("default", "默认"), GoogleTaskList("second", "第二清单"))
        }
        override suspend fun tasks(token: String, list: String) = remote.values.toList()
        override suspend fun insert(token: String, list: String, title: String, completed: Boolean, marker: String): RemoteTask {
            val result = RemoteTask("new-${++inserts}", title, completed, notes = marker)
            remote[result.id] = result
            if (loseInsertResponse) { loseInsertResponse = false; throw IOException("response lost") }
            return result
        }
        override suspend fun patch(token: String, list: String, id: String, title: String?, completed: Boolean?): RemoteTask {
            val result = remote.getValue(id).copy(title = title ?: remote.getValue(id).title,
                completed = completed ?: remote.getValue(id).completed)
            remote[id] = result
            onPatch?.invoke()
            return result
        }
        override suspend fun delete(token: String, list: String, id: String) { remote.remove(id) }
    }
}
