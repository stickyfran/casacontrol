package com.kes.casacontrol

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class TuyaApiClient(
    private val clientId: String,
    private val clientSecret: String,
    private val regionUrl: String
) {
    private val client = OkHttpClient()
    private var accessToken: String = ""

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

    fun getToken(): Boolean {
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
                    accessToken = json.getJSONObject("result").getString("access_token")
                    return true
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return false
    }

    fun getHomes(uid: String): List<JSONObject> {
        if (accessToken.isEmpty() && !getToken()) return emptyList()
        val timestamp = System.currentTimeMillis().toString()
        val path = "/v1.0/users/$uid/homes"
        val sign = getSignature(timestamp, "GET", path, "", accessToken)

        val request = Request.Builder().url("$regionUrl$path").get()
            .addHeader("client_id", clientId)
            .addHeader("access_token", accessToken)
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
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return list
    }

    fun getScenes(homeId: String): List<JSONObject> {
        if (accessToken.isEmpty() && !getToken()) return emptyList()
        val timestamp = System.currentTimeMillis().toString()
        val path = "/v1.0/homes/$homeId/scenes"
        val sign = getSignature(timestamp, "GET", path, "", accessToken)

        val request = Request.Builder().url("$regionUrl$path").get()
            .addHeader("client_id", clientId)
            .addHeader("access_token", accessToken)
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
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return list
    }

    fun triggerScene(homeId: String, sceneId: String): Boolean {
        if (accessToken.isEmpty() && !getToken()) return false
        val timestamp = System.currentTimeMillis().toString()
        val path = "/v1.0/homes/$homeId/scenes/$sceneId/trigger"
        val bodyStr = "{}"
        val sign = getSignature(timestamp, "POST", path, bodyStr, accessToken)

        val body = bodyStr.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder().url("$regionUrl$path").post(body)
            .addHeader("client_id", clientId)
            .addHeader("access_token", accessToken)
            .addHeader("sign", sign)
            .addHeader("t", timestamp)
            .addHeader("sign_method", "HMAC-SHA256")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val json = JSONObject(response.body?.string() ?: "")
                return json.optBoolean("success", false)
            }
        } catch (e: Exception) { e.printStackTrace() }
        return false
    }
}\n