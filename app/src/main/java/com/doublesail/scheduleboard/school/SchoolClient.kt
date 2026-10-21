package com.doublesail.scheduleboard.school

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpCookie
import java.net.HttpURLConnection
import java.net.URI
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
                    if (code !in 200..299) throw IOException("教务暂时不可用")
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
data class TimetablePage(val html: String, val year: String, val term: String)
class SchoolLoginRequired(message: String = "教务会话已失效，请重新登录；缓存课表仍可离线显示") : IOException(message)

class SchoolClient(private val transport: SchoolTransport = HttpSchoolTransport()) {
    fun challenge(): SchoolChallenge {
        val random = SecureRandom()
        val alphabet = "abcdefghijklmnopqrstuvwxyz12345"
        val token = "(" + (1..24).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("") + ")"
        val base = base(token)
        val page = transport.execute(SchoolRequest(base + "default2.aspx"))
        val fields = hidden(page.html())
        val capture = transport.execute(SchoolRequest(base + "CheckCode.aspx", cookie = page.cookie))
        return SchoolChallenge(token, fields, capture.bytes, capture.cookie)
    }

    fun login(challenge: SchoolChallenge, account: String, password: String, captcha: String): SchoolSession {
        require(account.matches(Regex("[A-Za-z0-9]{5,30}")) && password.isNotEmpty() && captcha.isNotBlank()) { "请填写学号、密码和验证码" }
        val url = base(challenge.token) + "default2.aspx"
        val fields = challenge.fields + mapOf("txtUserName" to account, "Textbox1" to "", "TextBox2" to password,
            "txtSecretCode" to captcha.trim(), "RadioButtonList1" to "学生", "Button1" to "", "lbLanguage" to "", "hidPdrs" to "", "hidsc" to "")
        val response = transport.execute(SchoolRequest(url, fields, url, challenge.cookie))
        val document = Jsoup.parse(response.html())
        val name = document.getElementById("xhxm")?.text()?.removeSuffix("同学")?.trim()
        if (name.isNullOrBlank()) {
            val alert = Regex("alert\\(['\"]([^'\"]+)['\"]\\)").find(response.html())?.groupValues?.get(1)
            throw SchoolLoginRequired(alert?.take(150) ?: "登录未成功，请核对密码和验证码后重试")
        }
        return SchoolSession(account, name, challenge.token, response.cookie)
    }

    fun timetable(session: SchoolSession, requestedYear: String, requestedTerm: String): TimetablePage {
        require(requestedYear.matches(Regex("\\d{4}-\\d{4}")) && requestedTerm in listOf("1", "2")) { "学年或学期不正确" }
        val base = base(session.token)
        val referer = base + "xs_main.aspx?xh=" + session.account
        val url = base + "xskbcx.aspx?xh=" + session.account + "&xm=" + URLEncoder.encode(session.name, "GB2312") + "&gnmkdm=N121602"
        var html = transport.execute(SchoolRequest(url, referer = referer, cookie = session.cookie)).html()
        var document = Jsoup.parse(html)
        if (document.getElementById("Table1") == null) throw SchoolLoginRequired()
        fun selected(name: String) = document.selectFirst("select[name=$name] option[selected]")?.`val`()
            ?: document.selectFirst("select[name=$name] option")?.`val`().orEmpty()
        val years = document.select("select[name=xnd] option").map { it.`val`() }
        require(requestedYear in years) { "教务系统中没有所选学年" }
        if (selected("xnd") != requestedYear || selected("xqd") != requestedTerm) {
            val fields = hidden(html) + mapOf("xnd" to requestedYear, "xqd" to requestedTerm)
            html = transport.execute(SchoolRequest(url, fields, referer, session.cookie)).html()
            document = Jsoup.parse(html)
            if (document.getElementById("Table1") == null) throw SchoolLoginRequired()
        }
        require(selected("xnd") == requestedYear && selected("xqd") == requestedTerm) { "教务返回了其他学期，原课表已保留" }
        return TimetablePage(html, requestedYear, requestedTerm)
    }

    private fun base(token: String): String {
        require(token.matches(Regex("\\([a-z1-5]{24}\\)"))) { "教务会话格式不正确" }
        return "http://jwgl.fafu.edu.cn/$token/"
    }
    private fun hidden(html: String) = Jsoup.parse(html).select("input[type=hidden][name]")
        .associate { it.attr("name") to it.`val`() }
}
