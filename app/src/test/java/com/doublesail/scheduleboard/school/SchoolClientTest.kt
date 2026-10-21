package com.doublesail.scheduleboard.school

import java.nio.charset.Charset
import org.junit.Assert.*
import org.junit.Test

class SchoolClientTest {
    private val token = "(" + "a".repeat(24) + ")"
    private val requests = mutableListOf<SchoolRequest>()
    private fun client(vararg responses: String): SchoolClient {
        val queue = ArrayDeque(responses.toList())
        return SchoolClient { request ->
            requests += request
            SchoolResponse(queue.removeFirst().toByteArray(Charset.forName("GB2312")), Charset.forName("GB2312"), "fixture=cookie")
        }
    }
    private fun page(term: String) = "<select name=xnd><option value=2026-2027 selected>2026-2027</option></select>" +
        "<select name=xqd><option value=$term selected>$term</option></select><input type=hidden name=__VIEWSTATE value=state><table id=Table1></table>"
    @Test fun loginSendsManualCaptchaAndPreservesHiddenFields() {
        val client = client("<span id=xhxm>测试同学</span>")
        val session = client.login(SchoolChallenge(token, mapOf("__VIEWSTATE" to "state"), byteArrayOf(), ""), "123456789", "fixture-password", "abcd")
        assertEquals("测试", session.name)
        assertEquals("state", requests.single().body?.get("__VIEWSTATE"))
        assertEquals("abcd", requests.single().body?.get("txtSecretCode"))
        assertEquals("fixture-password", requests.single().body?.get("TextBox2"))
    }
    @Test(expected = SchoolLoginRequired::class) fun loginFailureIsNotTreatedAsAValidSession() {
        client("<script>alert('验证码不正确')</script>").login(SchoolChallenge(token, emptyMap(), byteArrayOf(), ""),
            "123456789", "fixture-password", "abcd")
    }
    @Test fun changingSemesterPostsViewstateAndRequestedOptions() {
        val session = SchoolSession("123456789", "测试", token, "cookie")
        val result = client(page("1"), page("2")).timetable(session, "2026-2027", "2")
        assertEquals("2", result.term)
        assertEquals("state", requests.last().body?.get("__VIEWSTATE"))
        assertEquals("2", requests.last().body?.get("xqd"))
    }
    @Test(expected = IllegalArgumentException::class) fun wrongSemesterResponseCannotReplaceCache() {
        client(page("1"), page("1")).timetable(SchoolSession("123456789", "测试", token, ""), "2026-2027", "2")
    }
    @Test(expected = SchoolLoginRequired::class) fun expiredSessionRequiresLogin() {
        client("<input name=txtUserName>").timetable(SchoolSession("123456789", "测试", token, ""), "2026-2027", "1")
    }
    @Test(expected = IllegalArgumentException::class) fun transportRejectsOtherHostsBeforeConnecting() {
        HttpSchoolTransport().execute(SchoolRequest("https://example.org/"))
    }
}
