package com.doublesail.scheduleboard.school

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

data class SchoolProfile(val session: SchoolSession, val year: String, val term: String, val campus: Campus,
    val firstWeekOverride: LocalDate? = null, val password: String? = null) {
    override fun toString() = "SchoolProfile(redacted)"
}

/** Credentials and session are encrypted together with an app-owned Android Keystore key. */
class SchoolSessionVault(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "school-session.enc"))
    fun read(): SchoolProfile? {
        if (!file.baseFile.exists()) return null
        val bytes = file.openRead().use { it.readBytes() }
        require(bytes.size in 29..65_536 && bytes[0].toInt() == 1) { "登录缓存格式不正确" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        val json = JSONObject(cipher.doFinal(bytes.copyOfRange(13, bytes.size)).toString(Charsets.UTF_8))
        return SchoolProfile(SchoolSession(json.getString("account"), json.getString("name"), json.getString("token"), json.optString("cookie")),
            json.getString("year"), json.getString("term"), Campus.valueOf(json.getString("campus")),
            json.optString("firstWeek").takeIf(String::isNotEmpty)?.let(LocalDate::parse),
            json.optString("password").takeIf(String::isNotEmpty))
    }

    fun write(profile: SchoolProfile) {
        val json = JSONObject().apply {
            put("account", profile.session.account); put("name", profile.session.name)
            put("token", profile.session.token); put("cookie", profile.session.cookie)
            put("year", profile.year); put("term", profile.term); put("campus", profile.campus.name)
            put("firstWeek", profile.firstWeekOverride?.toString().orEmpty())
            put("password", profile.password.orEmpty())
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = byteArrayOf(1) + cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) } catch (error: Exception) { file.failWrite(output); throw error }
    }

    fun clear() { file.delete() }
    private fun key(): SecretKey {
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keys.getKey(Alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(Alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private companion object { const val Alias = "scheduleboard_school_session_v1" }
}
