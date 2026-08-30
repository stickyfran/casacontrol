
package com.kes.casacontrol

import android.content.Context
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.concurrent.TimeUnit

class TuyaApiClient(
    private val context: Context,
    private val clientId: String,
    private val clientSecret: String,
    private val regionUrl: String
) {
    companion object {
        private val connectionPool = ConnectionPool(10, 5, TimeUnit.MINUTES)
        private val client = OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .retryOnConnectionFailure(true)
            .connectTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        fun evictConnectionPool() {
            try {
                connectionPool.evictAll()
            } catch (e: Exception) {}
        }
    }

    private val prefs = context.getSharedPreferences("tuya_prefs", Context.MODE_PRIVATE)
    
    var lastError: String = ""
    var lastErrorCode: Int = 0

    private fun sha256(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun hmacSha256(message: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(secretKey)
        val hash = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }.uppercase()
    }

    private fun getSignature(timestamp: String, method: String, path: String, body: String = "", token: String = ""): String {
        val contentHash = sha256(body)
        val stringToSign = "$method\n$contentHash\n\n$path"
        val strToHash = clientId + token + timestamp + stringToSign
        return hmacSha256(strToHash, clientSecret)
    }

    fun getValidToken(forceRefresh: Boolean = false): String {
        val cachedToken = prefs.getString("access_token", "") ?: ""
        val expireTime = prefs.getLong("access_token_expire", 0L)
        
        // 15-minute safety margin (900,000 ms) before expiration to prevent double-calls on user taps
        if (!forceRefresh && cachedToken.isNotEmpty() && System.currentTimeMillis() < expireTime - 900000) {
            return cachedToken
        }

        val timestamp = System.currentTimeMillis().toString()
        val path = "/v1.0/token?grant_type=1"
        val sign = getSignature(timestamp, "GET", path)

        val request = Request.Builder()
            .url("$regionUrl$path")
            .addHeader("client_id", clientId)
            .addHeader("sign", sign)
            .addHeader("t", timestamp)
            .addHeader("sign_method", "HMAC-SHA256")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val json = JSONObject(response.body?.string() ?: "")
                if (json.optBoolean("success", false)) {
                    val result = json.getJSONObject("result")
                    val newToken = result.getString("access_token")
                    val expireSecs = result.getLong("expire_time")
                    
                    prefs.edit()
                        .putString("access_token", newToken)
                        .putLong("access_token_expire", System.currentTimeMillis() + (expireSecs * 1000))
                        .apply()
                        
                    return newToken
                } else {
                    lastErrorCode = json.optInt("code", 0)
                    lastError = json.optString("msg", "Error de token")
                }
            }
        } catch (e: Exception) { 
            lastError = e.message ?: "Network error"
            e.printStackTrace()
            // Evict pool and retry once on network error (likely stale connection after screen off)
            if (!forceRefresh) {
                evictConnectionPool()
                return getValidToken(forceRefresh = true)
            }
        }
        return ""
    }

    fun getHomes(uid: String): List<JSONObject> {
        val token = getValidToken()
        if (token.isEmpty()) return emptyList()
        val timestamp = System.currentTimeMillis().toString()
        val path = "/v1.0/users/$uid/homes"
        val sign = getSignature(timestamp, "GET", path, "", token)

        val request = Request.Builder().url("$regionUrl$path").get()
            .addHeader("client_id", clientId)
            .addHeader("access_token", token)
            .addHeader("sign", sign)
            .addHeader("t", timestamp)
            .addHeader("sign_method", "HMAC-SHA256")
            .build()
        val list = mutableListOf<JSONObject>()
        try {
            client.newCall(request).execute().use { response ->
                val json = JSONObject(response.body?.string() ?: "")
                if (json.optBoolean("success", false)) {
                    val arr = json.getJSONArray("result")
                    for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                } else {
                    lastErrorCode = json.optInt("code", 0)
                    lastError = json.optString("msg", "Error al obtener casas")
                }
            }
        } catch (e: Exception) { 
            lastError = e.message ?: "Network error"
            e.printStackTrace() 
        }
        return list
    }

    fun getScenes(homeId: String): List<JSONObject> {
        val token = getValidToken()
        if (token.isEmpty()) return emptyList()
        val timestamp = System.currentTimeMillis().toString()
        val path = "/v1.0/homes/$homeId/scenes"
        val sign = getSignature(timestamp, "GET", path, "", token)

        val request = Request.Builder().url("$regionUrl$path").get()
            .addHeader("client_id", clientId)
            .addHeader("access_token", token)
            .addHeader("sign", sign)
            .addHeader("t", timestamp)
            .addHeader("sign_method", "HMAC-SHA256")
            .build()
        val list = mutableListOf<JSONObject>()
        try {
            client.newCall(request).execute().use { response ->
                val json = JSONObject(response.body?.string() ?: "")
                if (json.optBoolean("success", false)) {
                    val arr = json.getJSONArray("result")
                    for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                } else {
                    lastErrorCode = json.optInt("code", 0)
                    lastError = json.optString("msg", "Error al obtener escenas")
                }
            }
        } catch (e: Exception) { 
            lastError = e.message ?: "Network error"
            e.printStackTrace() 
        }
        return list
    }

    fun triggerScene(homeId: String, sceneId: String, retryOnAuthFail: Boolean = true): Boolean {
        val token = getValidToken()
        if (token.isEmpty()) return false
        val timestamp = System.currentTimeMillis().toString()
        val path = "/v1.0/homes/$homeId/scenes/$sceneId/trigger"
        val bodyStr = "{}"
        val sign = getSignature(timestamp, "POST", path, bodyStr, token)

        val body = bodyStr.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder().url("$regionUrl$path").post(body)
            .addHeader("client_id", clientId)
            .addHeader("access_token", token)
            .addHeader("sign", sign)
            .addHeader("t", timestamp)
            .addHeader("sign_method", "HMAC-SHA256")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val json = JSONObject(response.body?.string() ?: "")
                if (json.optBoolean("success", false)) {
                    return true
                } else {
                    val code = json.optInt("code", 0)
                    lastErrorCode = code
                    lastError = json.optString("msg", "Error al ejecutar")
                    if (retryOnAuthFail && (code == 1010 || code == 1107 || lastError.contains("token", true))) {
                        prefs.edit().putString("access_token", "").apply()
                        getValidToken(forceRefresh = true)
                        return triggerScene(homeId, sceneId, retryOnAuthFail = false)
                    }
                    return false
                }
            }
        } catch (e: Exception) { 
            lastError = e.message ?: "Network error"
            e.printStackTrace() 
            if (retryOnAuthFail) {
                evictConnectionPool()
                return triggerScene(homeId, sceneId, retryOnAuthFail = false)
            }
        }
        return false
    }
}
