package com.doublesail.scheduleboard.tasks.google

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class GoogleTaskList(val id: String, val title: String)
data class RemoteTask(val id: String, val title: String, val completed: Boolean = false,
    val deleted: Boolean = false, val hidden: Boolean = false, val notes: String = "")
class GoogleTasksHttpException(val code: Int) : IOException("Google Tasks HTTP $code")

interface TasksApi {
    suspend fun lists(token: String): List<GoogleTaskList>
    suspend fun tasks(token: String, list: String): List<RemoteTask>
    suspend fun insert(token: String, list: String, title: String, completed: Boolean, marker: String): RemoteTask
    suspend fun patch(token: String, list: String, id: String, title: String?, completed: Boolean?): RemoteTask
    suspend fun delete(token: String, list: String, id: String)
}

/** OAuth bearer tokens stay in memory. Only this HTTPS origin receives them. */
class GoogleTasksApi(
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build(),
    private val base: HttpUrl = "https://tasks.googleapis.com/tasks/v1/".toHttpUrl(),
) : TasksApi {
    override suspend fun lists(token: String) = pages(token, listOf("users", "@me", "lists"), emptyMap()).map {
        GoogleTaskList(it.getString("id"), it.getString("title"))
    }
    override suspend fun tasks(token: String, list: String) = pages(token, listOf("lists", list, "tasks"),
        mapOf("showCompleted" to "true", "showHidden" to "true", "showDeleted" to "true")).map(::task)

    override suspend fun insert(token: String, list: String, title: String, completed: Boolean, marker: String) = task(
        request(token, listOf("lists", list, "tasks"), "POST", JSONObject().put("title", title)
            .put("status", if (completed) "completed" else "needsAction").put("notes", marker)))

    override suspend fun patch(token: String, list: String, id: String, title: String?, completed: Boolean?): RemoteTask {
        val body = JSONObject()
        title?.let { body.put("title", it) }
        completed?.let { body.put("status", if (it) "completed" else "needsAction"); if (!it) body.put("completed", JSONObject.NULL) }
        return task(request(token, listOf("lists", list, "tasks", id), "PATCH", body))
    }
    override suspend fun delete(token: String, list: String, id: String) {
        try { request(token, listOf("lists", list, "tasks", id), "DELETE") }
        catch (error: GoogleTasksHttpException) { if (error.code != 404 && error.code != 410) throw error }
    }
    private suspend fun pages(token: String, path: List<String>, parameters: Map<String, String>): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        val seen = mutableSetOf<String>()
        var page = ""
        do {
            val response = request(token, path, parameters = parameters + mapOf("maxResults" to "100", "pageToken" to page))
            val items = response.optJSONArray("items")
            if (items != null) for (i in 0 until items.length()) result.add(items.getJSONObject(i))
            page = response.optString("nextPageToken")
            check(page.isEmpty() || seen.add(page)) { "Google Tasks 返回重复分页，已保留原待办" }
            check(result.size <= 100_000 && seen.size <= 2_000) { "Google Tasks 数据过多，已保留原待办" }
        } while (page.isNotEmpty())
        return result
    }
    private suspend fun request(token: String, path: List<String>, method: String = "GET", body: JSONObject? = null,
        parameters: Map<String, String> = emptyMap()): JSONObject = withContext(Dispatchers.IO) {
        val url = base.newBuilder().apply {
            path.forEach(::addPathSegment)
            parameters.filterValues { it.isNotEmpty() }.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").header("Accept", "application/json")
            .method(method, body?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw GoogleTasksHttpException(response.code)
            if (response.code == 204) JSONObject() else JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
        }
    }
    private fun task(item: JSONObject) = RemoteTask(item.getString("id"), item.optString("title"),
        item.optString("status") == "completed", item.optBoolean("deleted"), item.optBoolean("hidden"), item.optString("notes"))
}
