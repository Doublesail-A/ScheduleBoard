package com.doublesail.scheduleboard.tasks

import android.app.Application
import com.doublesail.scheduleboard.tasks.google.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class GoogleTasksApiTest {
    @Test fun paginatesAndIncludesCompletedHiddenAndDeletedTasks() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"items":[{"id":"one","title":"A","status":"completed"}],"nextPageToken":"page2"}"""))
            server.enqueue(MockResponse().setBody("""{"items":[{"id":"two","deleted":true}]}"""))
            val api = GoogleTasksApi(base = server.url("/tasks/v1/"))
            val result = api.tasks("test-token", "list/with spaces")
            assertEquals(listOf("one", "two"), result.map { it.id })
            assertTrue(result.first().completed)
            assertTrue(result.last().deleted)
            val first = server.takeRequest()
            assertEquals("Bearer test-token", first.getHeader("Authorization"))
            assertEquals("true", first.requestUrl!!.queryParameter("showHidden"))
            assertEquals("true", first.requestUrl!!.queryParameter("showCompleted"))
            assertEquals("true", first.requestUrl!!.queryParameter("showDeleted"))
            assertTrue(first.path!!.contains("list%2Fwith%20spaces"))
            assertEquals("page2", server.takeRequest().requestUrl!!.queryParameter("pageToken"))
        }
    }
    @Test fun patchOnlySendsChangedFieldsAndDoesNotClobberRemoteNotesOrDueDate() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"id":"one","title":"renamed","status":"needsAction"}"""))
            GoogleTasksApi(base = server.url("/")).patch("test-token", "list", "one", "renamed", null)
            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("""{"title":"renamed"}""", request.body.readUtf8())
        }
    }
    @Test fun repeatedPaginationTokenFailsInsteadOfOverwritingCacheWithPartialData() = runBlocking {
        MockWebServer().use { server ->
            repeat(2) { server.enqueue(MockResponse().setBody("""{"nextPageToken":"same"}""")) }
            try { GoogleTasksApi(base = server.url("/")).tasks("test-token", "list"); fail() }
            catch (_: IllegalStateException) { }
        }
    }
    @Test fun doesNotFollowRedirectWithBearerToken() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://example.org/"))
            try { GoogleTasksApi(base = server.url("/")).lists("test-token"); fail() }
            catch (error: GoogleTasksHttpException) { assertEquals(302, error.code) }
            assertEquals(1, server.requestCount)
        }
    }
}
