package dev.titan.code.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.webkit.CookieManager
import dev.titan.code.TLog
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * LeetCode session cookies, encrypted at rest (PLAN §6.2). The key lives in the Android Keystore
 * (AES-256-GCM, never leaves the secure hardware); the ciphertext is in a private
 * SharedPreferences file. This replaces the planned `security-crypto` library, which Google has
 * deprecated (*agent decision*, rev 9). Cookie values are never logged.
 */
class AuthStore(context: Context) {
    private val sp = context.getSharedPreferences("titan-auth", Context.MODE_PRIVATE)

    /** The signed-in LeetCode username, if any. */
    val username: String? get() = sp.getString(KEY_USER, null)

    /** The cookie header to send, or null when signed out. Cached after the first decrypt. */
    @Volatile private var cached: String? = null

    fun cookies(): String? {
        cached?.let { return it }
        val blob = sp.getString(KEY_COOKIES, null) ?: return null
        return try {
            decrypt(blob).also { cached = it }
        } catch (e: Exception) {
            TLog.w("auth: cookies unreadable (${e.javaClass.simpleName}); signing out")
            clear()
            null
        }
    }

    /** Copy the WebView's leetcode.com cookies after a sign-in. False if there's no session cookie. */
    fun saveFromWebView(): Boolean {
        val cm = CookieManager.getInstance()
        cm.flush()
        val jar = cm.getCookie(ORIGIN) ?: return false
        if ("LEETCODE_SESSION=" !in jar) return false
        cached = jar
        sp.edit().putString(KEY_COOKIES, encrypt(jar)).apply()
        val names = jar.split(';').map { it.trim().substringBefore('=') }
        TLog.d("auth: saved cookies ${names.joinToString(",")}")
        return true
    }

    fun setUsername(name: String) = sp.edit().putString(KEY_USER, name).apply()

    /** Sign out of LeetCode only: its saved cookies and the WebView's leetcode.com cookies (rev 13). */
    fun clear() {
        cached = null
        sp.edit().remove(KEY_COOKIES).remove(KEY_USER).apply()
        expireCookies(ORIGIN)
    }

    // ---- other platforms (rev 13) -----------------------------------------------------------

    /** An encrypted value (e.g. NeetCode's Firebase refresh token), or null. */
    fun secret(name: String): String? {
        val blob = sp.getString("secret.$name", null) ?: return null
        return try { decrypt(blob) } catch (e: Exception) { TLog.w("auth: secret $name unreadable"); null }
    }

    fun putSecret(name: String, value: String?) {
        if (value == null) sp.edit().remove("secret.$name").apply() else sp.edit().putString("secret.$name", encrypt(value)).apply()
    }

    /** A plain (not secret) per-platform value, such as the signed-in account name. */
    fun account(platform: String): String? = if (platform == "leetcode") username else sp.getString("account.$platform", null)

    fun setAccount(platform: String, name: String?) {
        if (name == null) sp.edit().remove("account.$platform").apply() else sp.edit().putString("account.$platform", name).apply()
    }

    companion object {
        private const val ORIGIN = "https://leetcode.com"
        private const val ALIAS = "titan-code-auth"
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val IV_LEN = 12
        private const val KEY_COOKIES = "cookies"
        private const val KEY_USER = "username"

        /**
         * Expire every cookie the WebView holds for [origin] (and its parent domain), leaving other
         * sites signed in. CookieManager has no per-site delete, so each is overwritten as expired.
         */
        fun expireCookies(origin: String) {
            val cm = CookieManager.getInstance()
            val host = origin.removePrefix("https://").removePrefix("http://").substringBefore('/')
            val jar = cm.getCookie(origin) ?: return
            jar.split(';').map { it.trim().substringBefore('=') }.filter { it.isNotEmpty() }.forEach { name ->
                listOf(host, ".$host", ".${host.removePrefix("www.")}").distinct().forEach { d ->
                    cm.setCookie(origin, "$name=; Max-Age=0; Path=/; Domain=$d")
                }
                cm.setCookie(origin, "$name=; Max-Age=0; Path=/")
            }
            cm.flush()
        }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = c.iv + c.doFinal(plain.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(blob: String): String {
        val raw = Base64.decode(blob, Base64.NO_WRAP)
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, IV_LEN))
        return String(c.doFinal(raw, IV_LEN, raw.size - IV_LEN))
    }
}
