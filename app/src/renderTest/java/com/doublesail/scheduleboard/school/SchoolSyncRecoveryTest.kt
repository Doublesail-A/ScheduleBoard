package com.doublesail.scheduleboard.school

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.doublesail.scheduleboard.schedule.IcsScheduleRepository
import com.doublesail.scheduleboard.schedule.ScheduleEvent
import java.io.IOException
import java.nio.charset.Charset
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class SchoolSyncRecoveryTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("school_sync_status", Context.MODE_PRIVATE)
    private val requests = mutableListOf<SchoolRequest>()
    private var now = 1_800_000_000_000L
    private val original = SchoolProfile(SchoolSession("123456789", "fixture", "(" + "a".repeat(24) + ")", ""),
        "2026-2027", "1", Campus.MAIN, password = "fixture-password")
    private class MemoryVault(var profile: SchoolProfile?) : SchoolProfileStore {
        override fun read() = profile
        override fun write(profile: SchoolProfile) { this.profile = profile }
        override fun clear() { profile = null }
    }
    private val login = "<input type=hidden name=__VIEWSTATE value=fixture>"
    private val expired = "<input name=txtUserName>"
    private val accepted = "<span id=xhxm>fixture同学</span>"
    private val table = "<select name=xnd><option selected value=2026-2027>2026-2027</option></select>" +
        "<select name=xqd><option selected value=1>1</option></select><table id=Table1></table>"
    private fun repository(vault: MemoryVault, responses: List<Any>): SchoolSyncRepository {
        val queue = ArrayDeque(responses)
        val client = SchoolClient(transport = { request ->
            requests += request
            when (val next = queue.removeFirst()) {
                is IOException -> throw next
                else -> SchoolResponse(next.toString().toByteArray(Charset.forName("GB2312")), Charset.forName("GB2312"), "")
            }
        }, decoder = { "abcd" })
        return SchoolSyncRepository(context, IcsScheduleRepository(context), client, vault,
            calendar = { _, _ -> SchoolCalendar(LocalDate.of(2026, 8, 30)) }, schedulePeriodic = {}, nowMillis = { now })
    }
    @Test fun legacyNeedsLoginFlagDoesNotDisableRenewal() = runBlocking {
        prefs.edit().putBoolean("needs_login", true).putString("message", "旧版会话失效").commit()
        val vault = MemoryVault(original)
        val repo = repository(vault, listOf(expired, login, "image", accepted, table))
        assertTrue(repo.syncSaved())
        assertFalse(repo.state.value.requiresLogin)
        assertNull(repo.state.value.message)
        assertFalse(prefs.getBoolean("needs_login", true))
        assertEquals("fixture-password", vault.profile?.password)
        assertEquals(5, requests.size)
    }
    @Test fun temporarySessionFailureRetriesLaterWithoutLosingCredentials() = runBlocking {
        val vault = MemoryVault(original)
        val repo = repository(vault, listOf(expired, login, "image", accepted, expired, expired, login, "image", accepted, table))
        assertFalse(repo.syncSaved())
        assertFalse(repo.state.value.requiresLogin)
        assertFalse(prefs.getBoolean("auto_login_blocked", false))
        assertEquals("fixture-password", vault.profile?.password)
        assertTrue(repo.syncSaved())
        assertFalse(repo.state.value.requiresLogin)
        assertNull(repo.state.value.message)
        assertEquals(10, requests.size)
    }
    @Test fun wrongPasswordPausesAutomaticRequestsButExplicitRetryWorks() = runBlocking {
        val repo = repository(MemoryVault(original), listOf(expired, login, "image", "<script>alert('密码错误')</script>",
            expired, login, "image", accepted, table))
        assertTrue(repo.syncSaved())
        assertTrue(repo.state.value.requiresLogin)
        assertEquals(4, requests.size)
        assertTrue(repo.syncSaved())
        assertEquals(4, requests.size)
        assertTrue(repo.syncSaved(force = true))
        assertFalse(repo.state.value.requiresLogin)
        assertFalse(prefs.getBoolean("auto_login_blocked", false))
        assertEquals(9, requests.size)
    }
    @Test fun foregroundRetriesAfterBackoffAndKeepsOfflineCourses() = runBlocking {
        val schedule = IcsScheduleRepository(context)
        schedule.replace(listOf(ScheduleEvent("offline", "cached", Instant.ofEpochMilli(now), Instant.ofEpochMilli(now + 60_000))), "fixture")
        val repo = repository(MemoryVault(original), listOf(IOException("fixture offline"), table))
        assertFalse(repo.syncSaved())
        assertEquals("offline", schedule.snapshot.first().events.single().id)
        assertFalse(repo.state.value.requiresLogin)
        assertTrue(repo.syncSaved(onlyIfDue = true))
        assertEquals(1, requests.size)
        now += 60_000
        assertTrue(repo.syncSaved(onlyIfDue = true))
        assertEquals(2, requests.size)
        assertNull(repo.state.value.message)
        assertTrue(repo.syncSaved(onlyIfDue = true))
        assertEquals(2, requests.size)
    }
    @Test fun acceptedCredentialsAreSavedEvenIfFirstTimetableFetchFails() = runBlocking {
        val vault = MemoryVault(null)
        val repo = repository(vault, listOf(login, "image", accepted, IOException("fixture offline"), table))
        try { repo.loginAndSync("123456789", "fixture-password"); fail() } catch (_: IOException) { }
        assertEquals("fixture-password", vault.profile?.password)
        assertTrue(repo.state.value.connected)
        assertFalse(repo.state.value.requiresLogin)
        assertTrue(repo.syncSaved())
        assertNull(repo.state.value.message)
    }
    @Test fun expiredLegacyProfileWithoutPasswordStillNeedsUserCredentials() = runBlocking {
        val repo = repository(MemoryVault(original.copy(password = null)), listOf(expired))
        assertTrue(repo.syncSaved())
        assertTrue(repo.state.value.requiresLogin)
        assertTrue(prefs.getBoolean("auto_login_blocked", false))
    }
}
