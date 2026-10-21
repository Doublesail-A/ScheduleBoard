package com.doublesail.scheduleboard.school

import java.io.IOException
import java.nio.charset.Charset
import org.junit.Assert.*
import org.junit.Test

class SchoolClientTest {
    private val token = "(" + "a".repeat(24) + ")"
    private val requests = mutableListOf<SchoolRequest>()
    private fun client(vararg responses: String): SchoolClient {
        val queue = ArrayDeque(responses.toList())
        return SchoolClient(transport = { request ->
            requests += request
            SchoolResponse(queue.removeFirst().toByteArray(Charset.forName("GB2312")), Charset.forName("GB2312"), "fixture=cookie")
        }, decoder = { "abcd" })
    }
    private val loginPage = "<input type=hidden name=__VIEWSTATE value=state>"
    private val success = "<span id=xhxm>测试同学</span>"
    private fun page(term: String, year: String = "2026-2027") = "<select name=xnd><option value=$year selected>$year</option></select>" +
        "<select name=xqd><option value=$term selected>$term</option></select><table id=Table1></table>"
    @Test fun loginAutomaticallyRecognizesCaptchaAndMatchesIfafuForm() {
        val session = client(loginPage, "image", success).login("123456789", "fixture-password")
        assertEquals("测试", session.name)
        val post = requests.last()
        assertEquals("state", post.body?.get("__VIEWSTATE"))
        assertEquals("abcd", post.body?.get("txtSecretCode"))
        assertEquals("", post.body?.get("RadioButtonList1"))
        assertEquals("fixture-password", post.body?.get("TextBox2"))
        assertEquals("fixture=cookie", post.cookie)
        assertEquals(requests.first().url, post.url)
        assertTrue(requests[1].url.startsWith(requests.first().url.removeSuffix("default2.aspx")))
    }
    @Test fun wrongCaptchaAutomaticallyRetriesThenSucceeds() {
        client(loginPage, "image", "<script>alert('验证码不正确')</script>", loginPage, "image", success)
            .login("123456789", "fixture-password")
        assertEquals(6, requests.size)
    }
    @Test fun wrongPasswordIsNotRetried() {
        try {
            client(loginPage, "image", "<script>alert('密码错误')</script>").login("123456789", "fixture-password")
            fail("Must reject password")
        } catch (_: SchoolAuthenticationException) { assertEquals(3, requests.size) }
    }
    @Test fun captchaRetriesAreBounded() {
        val responses = List(5) { listOf(loginPage, "image", "<script>alert('验证码不正确')</script>") }.flatten()
        try { client(*responses.toTypedArray()).login("123456789", "fixture-password"); fail() }
        catch (_: SchoolCaptchaException) { assertEquals(15, requests.size) }
    }
    @Test fun currentSemesterAlwaysComesFromServerWithoutPostingChoices() {
        val c = client(page("1"), page("2", "2027-2028"))
        val session = SchoolSession("123456789", "测试", token, "cookie")
        assertEquals("1", c.timetable(session).term)
        val next = c.timetable(session)
        assertEquals("2", next.term)
        assertEquals("2027-2028", next.year)
        assertTrue(requests.all { it.body == null })
    }
    @Test(expected = IllegalArgumentException::class) fun missingSemesterCannotReplaceCache() {
        client("<table id=Table1></table>").timetable(SchoolSession("123456789", "测试", token, ""))
    }
    @Test fun expiredSessionReauthenticatesAndFetchesOnceMore() {
        val c = client("<input name=txtUserName>", loginPage, "image", success, page("2"))
        val old = SchoolSession("123456789", "测试", token, "")
        val (renewed, page) = c.synchronize(old, "fixture-password")
        assertEquals("2", page.term)
        assertNotEquals(old.token, renewed.token)
        assertEquals(5, requests.size)
    }
    @Test fun legacySessionWithoutSavedPasswordRequestsLogin() {
        try {
            client("<input name=txtUserName>").synchronize(SchoolSession("123456789", "测试", token, ""), null)
            fail()
        } catch (_: SchoolLoginRequired) { assertEquals(1, requests.size) }
    }
    @Test fun secondExpiredResponseDoesNotCauseLoginLoop() {
        try {
            client("<input name=txtUserName>", loginPage, "image", success, "<input name=txtUserName>")
                .synchronize(SchoolSession("123456789", "测试", token, ""), "fixture-password")
            fail()
        } catch (_: SchoolLoginRequired) { assertEquals(5, requests.size) }
    }
    @Test(expected = IOException::class) fun unrecognizedLoginPageIsNotReportedAsWrongPassword() {
        client(loginPage, "image", "<html>maintenance</html>").login("123456789", "fixture-password")
    }
    @Test(expected = IllegalArgumentException::class) fun transportRejectsOtherHostsBeforeConnecting() {
        HttpSchoolTransport().execute(SchoolRequest("https://example.org/"))
    }
}
