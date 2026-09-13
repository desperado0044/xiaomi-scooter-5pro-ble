package com.scooterre.client.cloud

import com.scooterre.client.ble.Protocol
import com.scooterre.client.crypto.MiCrypto
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class CloudException(message: String) : Exception(message)
class PinRequiredException : Exception("This account has a sharing PIN set - enter the device PIN")

/** Returned by [XiaomiCloudClient.startQrLogin]: the QR code image (as delivered by Xiaomi's own
 * server - no local QR generation needed) plus the same URL encoded in it, in case the user wants
 * to open it directly instead of scanning (e.g. if a browser on this phone is already logged in). */
data class QrLoginStart(val qrPng: ByteArray, val loginUrl: String)

/** A device match from [XiaomiCloudClient.findDeviceByMac]: its cloud id, the shard/country it
 * lives on (needed for the follow-up askbluetoothkey call), and its user-given name in the
 * Xiaomi/Mi Home app (e.g. "Mein Roller") - shown in the UI instead of a hardcoded model name. */
data class CloudDeviceMatch(val did: String, val country: String, val name: String?)

/** Countries Xiaomi's cloud API is sharded across; we search all of them for the scooter's `did`. */
private val CLOUD_SERVERS = listOf("cn", "de", "us", "ru", "tw", "sg", "in", "i2")

/**
 * Kotlin port of the Mi Cloud login (both password-based AND QR-based - some accounts, e.g. ones
 * created/linked via "Sign in with Google", have no separate Mi password at all) + RC4-encrypted
 * API calls used by reference/SCOOTER_5_PRO/tools/xct/token_extractor.py, scoped down to exactly
 * what this app needs: log in, find the scooter's `did` by BLE MAC, fetch its `ltmk`.
 *
 * Deliberately NOT ported: captcha solving and email 2FA for the password path (both need extra
 * UI round-trips the reference script handles interactively on a terminal) - if the account hits
 * either, login() throws a CloudException naming which one; the QR path is the fallback for those
 * accounts too, since it never touches a password at all.
 */
class XiaomiCloudClient {

    private val agent = generateAgent()
    private val deviceId = generateDeviceId()

    // Redirects must be followed for step 1/3 and encrypted API calls, but NOT for the password
    // flow's step 2 (its "Location" response header points at a URL we must not fetch here).
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private val noRedirectClient = client.newBuilder().followRedirects(false).build()

    // Xiaomi's login endpoints correlate the multi-step handshake (password steps 1->2->3, or QR
    // steps 1->2->3->4) via ordinary Set-Cookie/Cookie, like a browser session - requests.Session()
    // does this automatically in the reference script; here we track it by hand (see recordCookies/
    // mergeCookies) instead of wiring OkHttp's CookieJar, since a couple of calls also need
    // explicit one-off cookie values the jar's own Cookie header would otherwise clobber.
    private val sessionCookies = mutableMapOf<String, String>()

    private var sign: String? = null
    private var location: String? = null
    private var ssecurity: String? = null
    private var userId: String? = null
    private var serviceToken: String? = null

    private var qrLongPollUrl: String? = null
    private var qrTimeoutSeconds: Int = 180

    /** Full password login: throws CloudException with a human-readable reason on failure. */
    fun login(username: String, password: String) {
        sessionCookies["sdkVersion"] = "accountsdk-18.8.15"
        sessionCookies["deviceId"] = deviceId
        loginStep1(username)
        loginStep2(username, password)
        if (location != null && serviceToken == null) fetchServiceToken()
        requireLoggedIn()
    }

    /** Starts a QR login: fetches the QR image + long-polling URL. Call [awaitQrLogin] next. */
    fun startQrLogin(): QrLoginStart {
        val url = "https://account.xiaomi.com/longPolling/loginUrl"
        val httpUrl = url.toHttpUrl().newBuilder()
            .addQueryParameter("_qrsize", "480")
            .addQueryParameter("qs", "%3Fsid%3Dxiaomiio%26_json%3Dtrue")
            .addQueryParameter("callback", "https://sts.api.io.mi.com/sts")
            .addQueryParameter("_hasLogo", "false")
            .addQueryParameter("sid", "xiaomiio")
            .addQueryParameter("serviceParam", "")
            .addQueryParameter("_locale", "en_GB")
            .addQueryParameter("_dc", System.currentTimeMillis().toString())
            .build()
        val request = Request.Builder().url(httpUrl).header("Cookie", mergeCookies()).get().build()
        val response = client.newCall(request).execute()
        recordCookies(response)
        val body = response.use { it.body?.string() ?: "" }
        val json = parseXiaomiJson(body)
        val qrImageUrl = optStringOrNull(json, "qr") ?: throw CloudException("Cloud did not return a QR code")
        val loginUrl = optStringOrNull(json, "loginUrl") ?: throw CloudException("Cloud did not return a login URL")
        qrLongPollUrl = optStringOrNull(json, "lp") ?: throw CloudException("Cloud did not return a long-polling URL")
        qrTimeoutSeconds = json.optInt("timeout", 180)

        val imgRequest = Request.Builder().url(qrImageUrl).header("Cookie", mergeCookies()).get().build()
        val imgResponse = client.newCall(imgRequest).execute()
        recordCookies(imgResponse)
        val png = imgResponse.use { it.body?.bytes() } ?: throw CloudException("Could not download the QR code image")
        return QrLoginStart(png, loginUrl)
    }

    /** Blocks (on a background thread - call from Dispatchers.IO) until the QR is scanned and
     * confirmed, or [startQrLogin]'s reported timeout elapses. Long-polling like the reference
     * script: each individual HTTP call only waits ~15s, retried until the overall budget is up. */
    fun awaitQrLogin() {
        val lp = qrLongPollUrl ?: throw CloudException("startQrLogin() must be called first")
        val pollClient = client.newBuilder().readTimeout(15, TimeUnit.SECONDS).build()
        val deadline = System.currentTimeMillis() + qrTimeoutSeconds * 1000L

        var response: Response? = null
        while (System.currentTimeMillis() < deadline) {
            val request = Request.Builder().url(lp).header("Cookie", mergeCookies()).get().build()
            response = try {
                pollClient.newCall(request).execute()
            } catch (e: java.io.IOException) {
                continue // timed out waiting for a scan - keep polling until the overall deadline
            }
            if (response.code == 200) break
            response.close()
        }
        if (response == null || response.code != 200) throw CloudException("QR login timed out or was not confirmed in time")

        recordCookies(response)
        val body = response.use { it.body?.string() ?: "" }
        val json = parseXiaomiJson(body)
        userId = optStringOrNull(json, "userId")
        ssecurity = optStringOrNull(json, "ssecurity")
        location = optStringOrNull(json, "location")
        if (ssecurity == null || userId == null) throw CloudException("QR login response was incomplete")

        fetchServiceToken()
        requireLoggedIn()
    }

    private fun requireLoggedIn() {
        if (ssecurity == null || userId == null || serviceToken == null) {
            throw CloudException("Login did not complete (missing session data)")
        }
    }

    private fun loginStep1(username: String) {
        val url = "https://account.xiaomi.com/pass/serviceLogin?sid=xiaomiio&_json=true"
        val request = Request.Builder().url(url)
            .header("User-Agent", agent)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Cookie", mergeCookies("userId" to username))
            .get().build()
        val response = client.newCall(request).execute()
        recordCookies(response)
        val body = response.use { it.body?.string() ?: "" }
        val json = parseXiaomiJson(body)
        when {
            json.has("_sign") -> sign = json.getString("_sign")
            json.has("ssecurity") -> applyLoginJson(json)
            else -> throw CloudException("Invalid username (step 1 rejected)")
        }
    }

    private fun loginStep2(username: String, password: String) {
        val url = "https://account.xiaomi.com/pass/serviceLoginAuth2"
        val passwordHash = md5Hex(password).uppercase()
        val httpUrl = url.toHttpUrl().newBuilder()
            .addQueryParameter("sid", "xiaomiio")
            .addQueryParameter("hash", passwordHash)
            .addQueryParameter("callback", "https://sts.api.io.mi.com/sts")
            .addQueryParameter("qs", "%3Fsid%3Dxiaomiio%26_json%3Dtrue")
            .addQueryParameter("user", username)
            .addQueryParameter("_sign", sign ?: "")
            .addQueryParameter("_json", "true")
            .build()
        val request = Request.Builder().url(httpUrl)
            .header("User-Agent", agent)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Cookie", mergeCookies())
            .post("".toRequestBody(null)).build()
        val response = noRedirectClient.newCall(request).execute()
        recordCookies(response)
        val body = response.use { it.body?.string() ?: "" }
        val json = parseXiaomiJson(body)

        if (json.has("captchaUrl") && !json.isNull("captchaUrl")) {
            throw CloudException("Account requires solving a captcha - please use QR login instead")
        }
        if (!json.has("ssecurity") || json.optString("ssecurity").length <= 4) {
            if (json.has("notificationUrl")) {
                throw CloudException("Account requires 2FA (email/SMS verification) - please use QR login instead")
            }
            throw CloudException("Invalid login or password (accounts linked via Google/Apple sign-in usually have no separate Mi password - use QR login instead)")
        }
        applyLoginJson(json)
    }

    private fun applyLoginJson(json: JSONObject) {
        ssecurity = json.getString("ssecurity")
        userId = optStringOrNull(json, "userId")
        location = optStringOrNull(json, "location")
    }

    private fun fetchServiceToken() {
        val loc = location ?: return
        val request = Request.Builder().url(loc)
            .header("User-Agent", agent)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Cookie", mergeCookies())
            .get().build()
        val response = client.newCall(request).execute()
        response.use { recordCookies(it) }
        serviceToken = sessionCookies["serviceToken"]
        if (serviceToken == null) throw CloudException("Unable to obtain service token")
    }

    /** Merges accumulated session cookies (from every prior Set-Cookie this connector has seen)
     * with one-off [extra] values for this single request - mirrors requests.Session() behavior,
     * where per-call cookies augment but don't overwrite the persistent jar. */
    private fun mergeCookies(vararg extra: Pair<String, String>): String {
        val merged = LinkedHashMap<String, String>(sessionCookies)
        for ((k, v) in extra) merged[k] = v
        return merged.entries.joinToString("; ") { (k, v) -> "$k=$v" }
    }

    private fun recordCookies(response: Response) {
        var r: Response? = response
        while (r != null) {
            for (header in r.headers("Set-Cookie")) {
                val nameValue = header.substringBefore(';')
                val eq = nameValue.indexOf('=')
                if (eq > 0) sessionCookies[nameValue.substring(0, eq).trim()] = nameValue.substring(eq + 1).trim()
            }
            r = r.priorResponse
        }
    }

    // ---- Encrypted API calls (RC4), matching execute_api_call_encrypted -----------------------

    private fun apiUrl(country: String) = "https://" + (if (country == "cn") "" else "$country.") + "api.io.mi.com/app"

    private fun executeEncrypted(url: String, data: String): JSONObject? {
        val uid = userId ?: throw CloudException("Not logged in")
        val token = serviceToken ?: throw CloudException("Not logged in")
        val ss = ssecurity ?: throw CloudException("Not logged in")

        val millis = System.currentTimeMillis()
        val nonce = generateNonce(millis)
        val signedNonce = signedNonce(nonce, ss)
        val params = generateEncParams(url, "POST", signedNonce, nonce, linkedMapOf("data" to data), ss)

        val httpUrlBuilder = url.toHttpUrl().newBuilder()
        for ((k, v) in params) httpUrlBuilder.addQueryParameter(k, v)

        val request = Request.Builder().url(httpUrlBuilder.build())
            .header("Accept-Encoding", "identity")
            .header("User-Agent", agent)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("x-xiaomi-protocal-flag-cli", "PROTOCAL-HTTP2")
            .header("MIOT-ENCRYPT-ALGORITHM", "ENCRYPT-RC4")
            .header(
                "Cookie",
                "userId=$uid; yetAnotherServiceToken=$token; serviceToken=$token; locale=en_GB; " +
                    "timezone=GMT+02:00; is_daylight=1; dst_offset=3600000; channel=MI_APP_STORE",
            )
            .post("".toRequestBody(null)).build()

        val response = client.newCall(request).execute()
        val body = response.use { it.body?.string() ?: return null }
        if (!response.isSuccessful || body.isBlank()) return null
        val decrypted = decryptRc4(signedNonce(nonce, ss), body)
        return JSONObject(decrypted)
    }

    fun getHomes(country: String): JSONObject? =
        executeEncrypted(
            apiUrl(country) + "/v2/homeroom/gethome",
            """{"fg":true,"fetch_share":true,"fetch_share_dev":true,"limit":300,"app_ver":7}""",
        )

    fun getDevices(country: String, homeId: String, ownerId: String): JSONObject? =
        executeEncrypted(
            apiUrl(country) + "/v2/home/home_device_list",
            """{"home_owner":$ownerId,"home_id":$homeId,"limit":200,"get_split_device":true,"support_smart_home":true}""",
        )

    fun askBluetoothKey(country: String, did: String): JSONObject? =
        executeEncrypted(
            apiUrl(country) + "/share/askbluetoothkey",
            """{"type":"own","did":"$did","keyid":0}""",
        )

    /** Searches every cloud shard/home for a device whose BLE MAC matches [mac]. */
    fun findDeviceByMac(mac: String): CloudDeviceMatch? {
        val target = mac.uppercase().replace(":", "")
        for (country in CLOUD_SERVERS) {
            val homesResp = runCatching { getHomes(country) }.getOrNull() ?: continue
            if (homesResp.optInt("code", 0) != 0) continue
            val result = homesResp.optJSONObject("result") ?: continue
            val homelist = result.optJSONArray("homelist") ?: result.optJSONArray("homeList") ?: continue
            for (i in 0 until homelist.length()) {
                val home = homelist.getJSONObject(i)
                val homeId = if (home.has("id")) home.get("id").toString() else optStringOrNull(home, "home_id") ?: continue
                val ownerId = home.optString("uid", userId ?: "")

                val devResp = runCatching { getDevices(country, homeId, ownerId) }.getOrNull() ?: continue
                if (devResp.optInt("code", 0) != 0) continue
                val dresult = devResp.optJSONObject("result") ?: continue
                val devList = dresult.optJSONArray("device_info") ?: dresult.optJSONArray("list")
                    ?: dresult.optJSONArray("devices") ?: continue
                for (j in 0 until devList.length()) {
                    val dev = devList.getJSONObject(j)
                    val devMac = (optStringOrNull(dev, "mac") ?: optStringOrNull(dev, "bt_mac")
                        ?: optStringOrNull(dev, "bleMac") ?: optStringOrNull(dev, "bt_mac_str"))
                        ?.uppercase()?.replace(":", "")
                    if (devMac == target) {
                        val name = optStringOrNull(dev, "name")
                        return CloudDeviceMatch(dev.getString("did"), country, name)
                    }
                }
            }
        }
        return null
    }

    /** Fetches and decrypts the `ltmk`. Throws [PinRequiredException] if the account needs one and [pin] is null. */
    fun fetchLtmk(did: String, country: String, pin: String?): ByteArray {
        val resp = askBluetoothKey(country, did) ?: throw CloudException("Cloud did not respond to askbluetoothkey")
        if (resp.optInt("code", 0) != 0) throw CloudException("Cloud rejected askbluetoothkey (code=${resp.optInt("code")})")
        val result = resp.optJSONObject("result") ?: throw CloudException("Cloud response had no result")
        val keyHex = optStringOrNull(result, "key") ?: throw CloudException("Cloud did not return a key")
        val encryptType = result.optInt("encrypt_type", 0)

        val ltmk = when (encryptType) {
            0 -> hexToBytes(keyHex)
            1 -> {
                if (pin.isNullOrEmpty()) throw PinRequiredException()
                val aesKey = MessageDigest.getInstance("MD5").digest(pin.toByteArray(Charsets.UTF_8))
                MiCrypto.aesCbcDecryptNoPadding(aesKey, Protocol.LTMK_ENCRYPT_IV, hexToBytes(keyHex))
            }
            else -> throw CloudException("Unknown encrypt_type=$encryptType")
        }
        if (ltmk.size != 32) throw CloudException("Unexpected ltmk length: ${ltmk.size} bytes (expected 32)")
        return ltmk
    }

    // ---- Signing / RC4 helpers, matching XiaomiCloudConnector's static methods -----------------

    private fun signedNonce(nonce: String, ss: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(Base64.getDecoder().decode(ss))
        digest.update(Base64.getDecoder().decode(nonce))
        return Base64.getEncoder().encodeToString(digest.digest())
    }

    private fun generateNonce(millis: Long): String {
        val random = ByteArray(8).also { Random.nextBytes(it) }
        val minute = (millis / 60000).toInt()
        val minuteBytes = byteArrayOf(
            (minute ushr 24).toByte(), (minute ushr 16).toByte(), (minute ushr 8).toByte(), minute.toByte(),
        )
        return Base64.getEncoder().encodeToString(random + minuteBytes)
    }

    private fun generateEncSignature(url: String, method: String, signedNonce: String, params: Map<String, String>): String {
        val parts = mutableListOf(method.uppercase(), url.substringAfter("com").replace("/app/", "/"))
        for ((k, v) in params) parts.add("$k=$v")
        parts.add(signedNonce)
        val digest = MessageDigest.getInstance("SHA-1").digest(parts.joinToString("&").toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(digest)
    }

    private fun generateEncParams(
        url: String,
        method: String,
        signedNonce: String,
        nonce: String,
        params: LinkedHashMap<String, String>,
        ss: String,
    ): LinkedHashMap<String, String> {
        params["rc4_hash__"] = generateEncSignature(url, method, signedNonce, params)
        for (key in params.keys.toList()) {
            params[key] = encryptRc4(signedNonce, params.getValue(key))
        }
        params["signature"] = generateEncSignature(url, method, signedNonce, params)
        params["ssecurity"] = ss
        params["_nonce"] = nonce
        return params
    }

    private fun parseXiaomiJson(text: String): JSONObject = JSONObject(text.replace("&&&START&&&", ""))

    private fun md5Hex(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { ((Character.digit(hex[it * 2], 16) shl 4) + Character.digit(hex[it * 2 + 1], 16)).toByte() }

    companion object {
        private fun generateAgent(): String {
            val agentId = (1..13).map { ('A'..'E').random() }.joinToString("")
            val randomText = (1..18).map { ('a'..'z').random() }.joinToString("")
            return "$randomText-$agentId APP/com.xiaomi.mihome APPV/10.5.201"
        }

        private fun generateDeviceId(): String = (1..6).map { ('a'..'z').random() }.joinToString("")
    }
}

/** RC4/ARC4 stream cipher matching PyCryptodome's ARC4, including the 1024-byte discard the
 * reference script performs before the real payload (`r.encrypt(bytes(1024))`) - both ends of
 * this project must apply the exact same discard or the keystreams desync. */
private class Rc4Cipher(key: ByteArray) {
    private val s = IntArray(256) { it }
    private var i = 0
    private var j = 0

    init {
        var j2 = 0
        for (k in 0 until 256) {
            j2 = (j2 + s[k] + (key[k % key.size].toInt() and 0xFF)) and 0xFF
            val tmp = s[k]; s[k] = s[j2]; s[j2] = tmp
        }
    }

    fun process(data: ByteArray): ByteArray {
        val out = ByteArray(data.size)
        for (n in data.indices) {
            i = (i + 1) and 0xFF
            j = (j + s[i]) and 0xFF
            val tmp = s[i]; s[i] = s[j]; s[j] = tmp
            out[n] = (data[n].toInt() xor s[(s[i] + s[j]) and 0xFF]).toByte()
        }
        return out
    }
}

private fun rc4Transform(base64Key: String, data: ByteArray): ByteArray {
    val cipher = Rc4Cipher(Base64.getDecoder().decode(base64Key))
    cipher.process(ByteArray(1024))
    return cipher.process(data)
}

private fun encryptRc4(base64Key: String, payload: String): String =
    Base64.getEncoder().encodeToString(rc4Transform(base64Key, payload.toByteArray(Charsets.UTF_8)))

private fun decryptRc4(base64Key: String, payloadB64: String): String =
    rc4Transform(base64Key, Base64.getDecoder().decode(payloadB64)).toString(Charsets.UTF_8)

private fun optStringOrNull(json: JSONObject, key: String): String? = if (json.has(key) && !json.isNull(key)) json.getString(key) else null
