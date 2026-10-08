package com.sctech.obd.license

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class SctLicenseTest {

    // Signed with the production key by lisans-sunucusu/araclar/anahtar_uret.py (no product field)
    private val droneEraToken =
        "eyJrIjoiU0NULVRFU1QtVEVTVC1URVNUIiwiZCI6IjAxMjM0NTY3ODlhYmNkZWYiLCJ0IjoxNzAwMDAwMDAwfQ." +
            "d_OWjPPD0Dk5E4ZjARcj05EuO8tSxcS0b_yoZnWfrCBvpeHM-TXOcPVrVgQqBnjhyWOrMmuVUCBHNArnwLvgAQ"

    /** Signs like the Worker does (raw r||s over the b64url payload) with a throwaway key. */
    private fun signed(payload: String): Pair<String, String> {
        val kp = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val p = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
        val der = Signature.getInstance("SHA256withECDSA").run {
            initSign(kp.private)
            update(p.toByteArray(Charsets.US_ASCII))
            sign()
        }
        val sig = Base64.getUrlEncoder().withoutPadding().encodeToString(derToRaw(der))
        return "$p.$sig" to Base64.getEncoder().encodeToString(kp.public.encoded)
    }

    private fun derToRaw(der: ByteArray): ByteArray {
        var i = 2
        fun int(): ByteArray {
            val len = der[i + 1].toInt()
            val v = der.copyOfRange(i + 2, i + 2 + len)
            i += 2 + len
            val trimmed = v.dropWhile { it == 0.toByte() }.toByteArray()
            return ByteArray(32 - trimmed.size) + trimmed
        }
        return int() + int()
    }

    @Test
    fun productionKeyStillVerifiesDroneTokens() {
        assertNotNull(SctLicense.verifyToken(droneEraToken, SctLicense.PUBLIC_KEY))
    }

    @Test
    fun droneTokenDoesNotUnlockObd() {
        // Valid signature and device, but no "p":"obd"
        assertNull(SctLicense.keyFromToken(droneEraToken, "0123456789abcdef"))
    }

    @Test
    fun obdTokenForThisDeviceUnlocks() {
        val (token, pub) = signed("""{"k":"SCT-AB23-CD45-EF67","d":"aaaa1111bbbb2222","t":1,"p":"obd"}""")
        assertEquals("SCT-AB23-CD45-EF67", SctLicense.keyFromToken(token, "aaaa1111bbbb2222", pub))
    }

    @Test
    fun obdTokenForAnotherDeviceIsRejected() {
        val (token, pub) = signed("""{"k":"SCT-AB23-CD45-EF67","d":"aaaa1111bbbb2222","t":1,"p":"obd"}""")
        assertNull(SctLicense.keyFromToken(token, "ffffffffffffffff", pub))
    }

    @Test
    fun droneProductTokenIsRejected() {
        val (token, pub) = signed("""{"k":"SCT-AB23-CD45-EF67","d":"aaaa1111bbbb2222","t":1,"p":"drone"}""")
        assertNull(SctLicense.keyFromToken(token, "aaaa1111bbbb2222", pub))
    }

    @Test
    fun keyNormalization() {
        assertEquals("SCT-2ER3-M2AD-ZQMM", SctLicense.normalizeKey("sct 2er3-m2ad zqmm"))
        assertEquals("SCT-2ER3-M2AD-ZQMM", SctLicense.normalizeKey("2ER3M2ADZQMM"))
        assertNull(SctLicense.normalizeKey("2ER3-M2AD"))
    }
}
