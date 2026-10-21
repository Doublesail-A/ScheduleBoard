package com.doublesail.scheduleboard.school

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpCookie
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset
import java.security.SecureRandom
import org.jsoup.Jsoup

data class SchoolRequest(val url: String, val body: Map<String, String>? = null,
    val referer: String = "", val cookie: String = "") {
    override fun toString() = "SchoolRequest(redacted)"
}
data class SchoolResponse(val bytes: ByteArray, val charset: Charset, val cookie: String) {
    fun html(): String = bytes.toString(charset)
    override fun toString() = "SchoolResponse(redacted)"
}
fun interface SchoolTransport { fun execute(request: SchoolRequest): SchoolResponse }

class HttpSchoolTransport : SchoolTransport {
    override fun execute(request: SchoolRequest): SchoolResponse {
        var url = URL(request.url)
        var body = request.body
        val cookies = request.cookie.split(';').mapNotNull {
            val parts = it.trim().split('=', limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap().toMutableMap()
        repeat(5) {
            require(url.host == "jwgl.fafu.edu.cn" && url.protocol in listOf("http", "https")) { "教务跳转地址不受支持" }
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 8_000
                connection.readTimeout = 12_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 Android ScheduleBoard/0.2")
                if (request.referer.isNotEmpty()) connection.setRequestProperty("Referer", request.referer)
                if (cookies.isNotEmpty()) connection.setRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
                if (body != null) {
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=gb2312")
                    val encoded = body!!.entries.joinToString("&") { (key, value) ->
                        URLEncoder.encode(key, "GB2312") + "=" + URLEncoder.encode(value, "GB2312")
                    }.toByteArray(Charsets.US_ASCII)
                    connection.outputStream.use { it.write(encoded) }
                }
                connection.headerFields.filterKeys { it?.equals("Set-Cookie", ignoreCase = true) == true }.values.flatten().forEach { header ->
                    HttpCookie.parse(header).forEach { cookie ->
                        if (cookie.maxAge == 0L) cookies.remove(cookie.name) else cookies[cookie.name] = cookie.value
                    }
                }
                val code = connection.responseCode
                if (code in 300..399) {
                    url = URL(url, connection.getHeaderField("Location") ?: throw IOException("教务返回无效跳转"))
                    if (code != 307 && code != 308) body = null
                } else {
                    if (code !in 200..299) throw IOException("教务服务返回 HTTP $code，请稍后重试")
                    val output = ByteArrayOutputStream()
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > 4 * 1024 * 1024) throw IOException("教务响应过大")
                            output.write(buffer, 0, count)
                        }
                    }
                    val charsetName = Regex("charset=([^;\\s]+)", RegexOption.IGNORE_CASE)
                        .find(connection.contentType.orEmpty())?.groupValues?.get(1)?.trim('"')
                    val charset = runCatching { Charset.forName(charsetName ?: "GB18030") }.getOrDefault(Charset.forName("GB18030"))
                    return SchoolResponse(output.toByteArray(), charset, cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
                }
            } finally { connection.disconnect() }
        }
        throw IOException("教务跳转次数过多")
    }
}

data class SchoolChallenge(val token: String, val fields: Map<String, String>, val image: ByteArray, val cookie: String) {
    override fun toString() = "SchoolChallenge(redacted)"
}
data class SchoolSession(val account: String, val name: String, val token: String, val cookie: String) {
    override fun toString() = "SchoolSession(redacted)"
}
data class TimetablePage(val html: String, val year: String, val term: String) {
    override fun toString() = "TimetablePage(year=$year, term=$term, html=redacted)"
}
class SchoolLoginRequired(message: String = "教务会话已失效，请重新登录；缓存课表仍可离线显示") : IOException(message)
class SchoolAuthenticationException(message: String) : IOException(message)
class SchoolCaptchaException : IOException("验证码自动识别暂未成功，请重试")
fun interface SchoolCaptchaDecoder { fun recognize(image: ByteArray): String }

class SchoolClient(private val transport: SchoolTransport = HttpSchoolTransport(),
    private val decoder: SchoolCaptchaDecoder = SchoolCaptchaDecoder { throw IOException("验证码识别器未初始化") }) {
    private fun challenge(): SchoolChallenge {
        val random = SecureRandom()
        val alphabet = "abcdefghijklmnopqrstuvwxyz12345"
        val token = "(" + (1..24).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("") + ")"
        val base = base(token)
        val page = transport.execute(SchoolRequest(base + "default2.aspx"))
        val fields = hidden(page.html())
        val capture = transport.execute(SchoolRequest(base + "CheckCode.aspx", cookie = page.cookie))
        return SchoolChallenge(token, fields, capture.bytes, capture.cookie)
    }

    fun login(account: String, password: String): SchoolSession {
        require(account.matches(Regex("[A-Za-z0-9]{5,30}")) && password.isNotEmpty()) { "请填写学号和教务密码" }
        repeat(5) {
            val challenge = challenge()
            val code = decoder.recognize(challenge.image)
            require(code.matches(Regex("[0-9a-z]{4}"))) { "教务验证码格式不受支持" }
            try { return submit(challenge, account, password, code) }
            catch (_: SchoolCaptchaException) { /* Only retry an explicitly rejected CAPTCHA, never a wrong password. */ }
        }
        throw SchoolCaptchaException()
    }

    private fun submit(challenge: SchoolChallenge, account: String, password: String, captcha: String): SchoolSession {
        val url = base(challenge.token) + "default2.aspx"
        val fields = challenge.fields + mapOf("txtUserName" to account, "Textbox1" to "", "TextBox2" to password,
            "txtSecretCode" to captcha, "RadioButtonList1" to "", "Button1" to "", "lbLanguage" to "", "hidPdrs" to "", "hidsc" to "")
        val response = transport.execute(SchoolRequest(url, fields, url, challenge.cookie))
        val document = Jsoup.parse(response.html())
        val name = document.getElementById("xhxm")?.text()?.removeSuffix("同学")?.trim()
        if (name.isNullOrBlank()) {
            val alert = Regex("alert\\(['\"]([^'\"]+)['\"]\\)").find(response.html())?.groupValues?.get(1)
            if (alert?.contains("验证码") == true) throw SchoolCaptchaException()
            if (document.text().contains("输入原密码")) throw SchoolAuthenticationException("教务要求先修改初始密码，请在学校教务完成后重试")
            if (alert?.contains("密码") == true || alert?.contains("用户") == true || alert?.contains("学号") == true)
                throw SchoolAuthenticationException("学号或教务密码不正确，请核对后重试")
            throw IOException("教务未返回登录成功信息，请稍后重试")
        }
        return SchoolSession(account, name, challenge.token, response.cookie)
    }

    fun timetable(session: SchoolSession): TimetablePage {
        val base = base(session.token)
        val referer = base + "xs_main.aspx?xh=" + session.account
        val url = base + "xskbcx.aspx?xh=" + session.account + "&xm=" + URLEncoder.encode(session.name, "GB2312") + "&gnmkdm=N121602"
        val html = transport.execute(SchoolRequest(url, referer = referer, cookie = session.cookie)).html()
        val document = Jsoup.parse(html)
        if (document.getElementById("Table1") == null) throw SchoolLoginRequired()
        fun selected(name: String) = document.selectFirst("select[name=$name] option[selected]")?.`val`()
            ?: document.selectFirst("select[name=$name] option")?.`val`().orEmpty()
        val year = selected("xnd")
        val term = selected("xqd")
        require(year.matches(Regex("\\d{4}-\\d{4}")) && term in listOf("1", "2")) { "无法读取教务当前学期，原课表已保留" }
        return TimetablePage(html, year, term)
    }

    /** An expired session is renewed once; a failed password must never loop. */
    fun synchronize(session: SchoolSession, password: String?): Pair<SchoolSession, TimetablePage> =
        try { session to timetable(session) }
        catch (error: SchoolLoginRequired) {
            if (password.isNullOrEmpty()) throw error
            val renewed = login(session.account, password)
            renewed to timetable(renewed)
        }

    private fun base(token: String): String {
        require(token.matches(Regex("\\([a-z1-5]{24}\\)"))) { "教务会话格式不正确" }
        return "http://jwgl.fafu.edu.cn/$token/"
    }
    private fun hidden(html: String) = Jsoup.parse(html).select("input[type=hidden][name]")
        .associate { it.attr("name") to it.`val`() }
}
