package dev.titan.code.data.neetcode

import dev.titan.code.TLog
import dev.titan.code.data.AuthStore
import dev.titan.code.data.SignedOutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Calls NeetCode's Firebase functions (rev 13). Signed-in calls carry a Firebase ID token, which
 * lasts an hour; it's minted from the refresh token saved at sign-in (encrypted in [AuthStore]).
 * Firebase may hand back a new refresh token, which replaces the old one. Tokens are never logged.
 */
class NeetCodeHttp(private val auth: AuthStore, private val userAgent: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // Submit runs every hidden test before replying.
        .callTimeout(120, TimeUnit.SECONDS)
        .build()
    private val lock = Mutex()
    private var idToken: String? = null
    private var expiresAt = 0L

    val signedIn get() = auth.secret(REFRESH) != null

    /** Call function [name] with a `{"data": …}` [body]; [signedIn] adds the ID token. */
    suspend fun call(name: String, body: String, signedIn: Boolean): String {
        val token = if (signedIn) token() else null
        return withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("${NeetCodeApi.FUNCTIONS}/$name")
                .post(body.toRequestBody("application/json".toMediaType()))
                .header("User-Agent", userAgent)
                .header("Origin", NeetCodeApi.ORIGIN)
                .header("Referer", "${NeetCodeApi.ORIGIN}/")
                .apply { if (token != null) header("Authorization", "Bearer $token") }
                .build()
            client.newCall(req).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (r.code == 401 || text.contains("\"UNAUTHENTICATED\"")) {
                    idToken = null
                    throw SignedOutException()
                }
                if (r.code !in 200..299 && !text.trimStart().startsWith("{")) throw IOException("NeetCode: HTTP ${r.code}")
                text
            }
        }
    }

    /** Save the refresh token read from the sign-in page; the next call mints an ID token from it. */
    fun signIn(refreshToken: String) {
        auth.putSecret(REFRESH, refreshToken)
        idToken = null
        expiresAt = 0
    }

    fun signOut() {
        auth.putSecret(REFRESH, null)
        idToken = null
        expiresAt = 0
    }

    private suspend fun token(): String = lock.withLock {
        idToken?.takeIf { System.currentTimeMillis() < expiresAt - 60_000 }?.let { return it }
        val refresh = auth.secret(REFRESH) ?: throw SignedOutException()
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(NeetCodeApi.TOKEN_URL)
                .post(FormBody.Builder().add("grant_type", "refresh_token").add("refresh_token", refresh).build())
                .header("User-Agent", userAgent)
                .header("Referer", "${NeetCodeApi.ORIGIN}/")
                .header("Origin", NeetCodeApi.ORIGIN)
                .build()
            client.newCall(req).execute().use { r ->
                val text = r.body?.string().orEmpty()
                val o = runCatching { JSONObject(text) }.getOrNull()
                if (r.code !in 200..299 || o == null || !o.has("id_token")) {
                    TLog.w("neetcode: token refresh failed (HTTP ${r.code})")
                    if (r.code in 400..499) {
                        auth.putSecret(REFRESH, null)
                        throw SignedOutException()
                    }
                    throw IOException("NeetCode sign-in check failed (HTTP ${r.code})")
                }
                o.optString("refresh_token").takeIf { it.isNotEmpty() && it != refresh }?.let { auth.putSecret(REFRESH, it) }
                expiresAt = System.currentTimeMillis() + o.optString("expires_in", "3600").toLong() * 1000
                o.getString("id_token").also { idToken = it }
            }
        }
    }

    private companion object {
        const val REFRESH = "neetcode.refresh"
    }
}
