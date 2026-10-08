package com.sctech.obd.license

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.core.content.edit
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * One-time online licensing for SCTech OBD Pro, shared with the SCTech FCC app
 * (same server, same signing key; see lisans-sunucusu/).
 *
 * A key (SCT-XXXX-XXXX-XXXX) is activated once over the internet. The server binds it to
 * this phone and returns a signed token that names the product. Every launch checks that
 * token offline against [PUBLIC_KEY], so no internet is needed after activation.
 * Only tokens for [PRODUCT] are accepted, so an SCTech FCC key can't unlock this app.
 */
object SctLicense {

    const val SERVER = "https://sctechdrone.com"
    const val PRODUCT = "obd"

    /** ECDSA P-256 public key (SubjectPublicKeyInfo, base64) matching the server's SIGNING_KEY. */
    internal const val PUBLIC_KEY =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEQ2rJmhzhdmwONAVrhkYsD2NuADimzvSVq3dy/j3elKWD5ml5bJHWlv00Lb9ncJr/hTcO2G8IppPqpOwyxHalEA=="

    private const val PREFS = "license"
    private const val PREF_TOKEN = "token"

    private val KEY_PATTERN = Regex("SCT-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}")

    enum class Result { Success, Invalid, InUse, Revoked, NoInternet, ServerError }

    /** Stable per-install id (ANDROID_ID is scoped to this app's signing key). */
    @SuppressLint("HardwareIds")
    fun deviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)?.lowercase().orEmpty()

    /** Short form shown to customers for support, e.g. "3F2A-91BC". */
    fun deviceCode(context: Context): String =
        deviceId(context).take(8).uppercase().chunked(4).joinToString("-")

    /** "sct 2er3m2ad zqmm" -> "SCT-2ER3-M2AD-ZQMM"; null when it can't be a key. */
    fun normalizeKey(raw: String): String? {
        val s = raw.uppercase().filter { it.isLetterOrDigit() }
        val body = if (s.startsWith("SCT")) s.drop(3) else s
        if (body.length != 12) return null
        val key = "SCT-${body.substring(0, 4)}-${body.substring(4, 8)}-${body.substring(8, 12)}"
        return key.takeIf { KEY_PATTERN.matches(it) }
    }

    /** The activated key, or null when this phone holds no valid OBD Pro license. */
    fun licensedKey(context: Context): String? {
        val token = prefs(context).getString(PREF_TOKEN, null) ?: return null
        return keyFromToken(token, deviceId(context))
    }

    /** Validates signature, device and product; returns the key. */
    internal fun keyFromToken(token: String, device: String, publicKey: String = PUBLIC_KEY): String? {
        val payload = verifyToken(token, publicKey) ?: return null
        return try {
            val json = JSONObject(payload)
            if (json.optString("d") == device && json.optString("p") == PRODUCT) json.optString("k").ifEmpty { null } else null
        } catch (_: Exception) {
            null
        }
    }

    /** Activates [key] for this phone. Blocking network call — run off the main thread. */
    fun activate(context: Context, key: String): Result {
        val device = deviceId(context)
        if (device.isEmpty()) return Result.ServerError
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("$SERVER/v1/activate").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "SCTech-OBD")
            }
            val body = JSONObject().put("key", key).put("device", device).put("product", PRODUCT).toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            when (conn.responseCode) {
                200 -> {
                    val token = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).getString("token")
                    // Only keep a token that really verifies for this phone and product
                    if (keyFromToken(token, device) != null) {
                        prefs(context).edit(commit = true) { putString(PREF_TOKEN, token) }
                        Result.Success
                    } else {
                        Result.ServerError
                    }
                }
                400, 404 -> Result.Invalid
                403 -> Result.Revoked
                409 -> Result.InUse
                else -> Result.ServerError
            }
        } catch (_: IOException) {
            Result.NoInternet
        } catch (_: Exception) {
            Result.ServerError
        } finally {
            conn?.disconnect()
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Returns the signed payload JSON when [token] carries a valid signature from the
     * server's key, else null. Token = b64url(payload) + "." + b64url(raw 64-byte r||s
     * ECDSA signature over the b64url payload text).
     */
    internal fun verifyToken(token: String, publicKeyB64: String): String? {
        return try {
            val parts = token.split('.')
            if (parts.size != 2) return null
            val raw = Base64.getUrlDecoder().decode(parts[1])
            if (raw.size != 64) return null
            val key = KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyB64)))
            val ok = Signature.getInstance("SHA256withECDSA").run {
                initVerify(key)
                update(parts[0].toByteArray(Charsets.US_ASCII))
                verify(rawToDer(raw))
            }
            if (ok) String(Base64.getUrlDecoder().decode(parts[0]), Charsets.UTF_8) else null
        } catch (_: Exception) {
            null
        }
    }

    /** WebCrypto signs as raw r||s; Java's verifier wants an ASN.1 DER SEQUENCE of two INTEGERs. */
    private fun rawToDer(raw: ByteArray): ByteArray {
        fun derInt(half: ByteArray): ByteArray {
            var v = half.dropWhile { it == 0.toByte() }.toByteArray()
            if (v.isEmpty()) v = byteArrayOf(0)
            if (v[0] < 0) v = byteArrayOf(0) + v
            return byteArrayOf(0x02, v.size.toByte()) + v
        }
        val body = derInt(raw.copyOfRange(0, 32)) + derInt(raw.copyOfRange(32, 64))
        return byteArrayOf(0x30, body.size.toByte()) + body
    }
}
