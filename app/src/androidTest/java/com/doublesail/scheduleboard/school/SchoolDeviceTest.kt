package com.doublesail.scheduleboard.school

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.doublesail.scheduleboard.MainActivity
import com.doublesail.scheduleboard.ScheduleBoardApplication
import java.io.File
import java.security.SecureRandom
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class SchoolDeviceTest {
    @Test fun foregroundRecoversExpiredSessionAndLegacyFlagAtNextMinuteCheck() {
        val app = ApplicationProvider.getApplicationContext<ScheduleBoardApplication>()
        val vault = SchoolSessionVault(app)
        val saved = vault.read()
        assumeNotNull("Log in on the phone before this optional integration test", saved?.password)
        val profile = requireNotNull(saved)
        val keyguard = app.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        val power = app.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        assertTrue("Wake and unlock the phone before testing the visible board", power.isInteractive && !keyguard.isKeyguardLocked)
        val alphabet = "abcdefghijklmnopqrstuvwxyz12345"
        val random = SecureRandom()
        val unused = "(" + (1..24).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("") + ")"
        val prefs = app.getSharedPreferences("school_sync_status", Context.MODE_PRIVATE)
        val started = System.currentTimeMillis()
        vault.write(profile.copy(session = profile.session.copy(token = unused, cookie = "")))
        prefs.edit().putBoolean("needs_login", true).remove("auto_login_blocked")
            .putLong("next_sync_at", started + 5_000).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                val deadline = android.os.SystemClock.elapsedRealtime() + 85_000
                while (android.os.SystemClock.elapsedRealtime() < deadline && prefs.getLong("last_sync", 0) < started) {
                    Thread.sleep(250)
                }
                assertTrue("Visible board must retry automatically without user input", prefs.getLong("last_sync", 0) >= started)
                assertFalse("Legacy failure marker must be cleared", prefs.getBoolean("needs_login", true))
                assertFalse("Recovered login must not remain blocked", app.schoolSync.state.value.requiresLogin)
                assertTrue("Renewed credentials must remain saved", !vault.read()?.password.isNullOrEmpty())
            }
        } finally {
            if (vault.read()?.session?.token == unused) {
                vault.write(profile)
                prefs.edit().putLong("next_sync_at", 0).apply()
            }
        }
    }

    @Test fun savedAccountCanRenewSessionAndSyncWithoutUserInput() {
        val app = ApplicationProvider.getApplicationContext<ScheduleBoardApplication>()
        val vault = SchoolSessionVault(app)
        val saved = vault.read()
        assumeNotNull("Log in on the phone before running this optional integration test", saved?.password)
        val profile = requireNotNull(saved)
        // Exercise renewal using a fresh, unauthenticated session; never export credentials or cookies.
        val alphabet = "abcdefghijklmnopqrstuvwxyz12345"
        val random = SecureRandom()
        val unused = "(" + (1..24).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("") + ")"
        val client = SchoolClient(decoder = SchoolCaptchaRecognizer(app))
        val (renewed, page) = client.synchronize(profile.session.copy(token = unused, cookie = ""), profile.password)
        assertTrue("Must obtain a renewed session", renewed.token != unused)
        assertTrue("Must return current semester", page.term in listOf("1", "2"))
        SchoolHtmlParser.parse(page.html)
        vault.write(profile.copy(session = renewed, year = page.year, term = page.term))
        runBlocking { assertTrue("Saved login must synchronize", app.schoolSync.syncSaved()) }
        assertFalse("Sync must finish without another login", app.schoolSync.state.value.requiresLogin)
        assertNull("Sync must not silently retain an error", app.schoolSync.state.value.message)
    }

    @Test fun schoolNetworkAndLocalCaptchaModelWorkOnPhone() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val alphabet = "abcdefghijklmnopqrstuvwxyz12345"
        val random = SecureRandom()
        val token = "(" + (1..24).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("") + ")"
        val base = "http://jwgl.fafu.edu.cn/$token/"
        val transport = HttpSchoolTransport()
        val page = transport.execute(SchoolRequest(base + "default2.aspx"))
        assertNotNull("Must reach actual school login form", Jsoup.parse(page.html()).selectFirst("input[name=txtUserName]"))
        val image = transport.execute(SchoolRequest(base + "CheckCode.aspx", cookie = page.cookie))
        assertTrue(SchoolCaptchaRecognizer(context).recognize(image.bytes).matches(Regex("[0-9a-z]{4}")))
        // No account or password is submitted by this test.
    }

    @Test fun credentialsRoundTripThroughRealAndroidKeystore() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(app.cacheDir, "vault-device-test").apply { mkdirs() }
        val isolated = object : ContextWrapper(app) { override fun getFilesDir() = directory }
        val vault = SchoolSessionVault(isolated)
        try {
            val profile = SchoolProfile(SchoolSession("123456789", "fixture", "(" + "a".repeat(24) + ")", ""),
                "2026-2027", "1", Campus.MAIN, password = "fixture-password")
            vault.write(profile)
            val encrypted = File(directory, "school-session.enc").readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(encrypted.contains("fixture-password"))
            assertEquals(profile, vault.read())
        } finally { vault.clear(); directory.delete() }
    }
}
