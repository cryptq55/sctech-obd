package com.sctech.obd.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class VinAndCategoryTest {

    @Test
    fun validVinIsGroupedAndDecoded() {
        val vin = Vin("VF1RJA00X12345678")
        assertEquals("VF1 RJA00X 12345678", vin.grouped)
        assertEquals("Renault", vin.manufacturer)
        assertEquals("Fransa", vin.region)
    }

    @Test
    fun turkishWmiRange() = assertEquals("Türkiye", Vin("NMTKZ3BE30R123456").region)

    @Test
    fun invalidVinIsShownRaw() {
        // 16 characters, and O is never used in a VIN
        val vin = Vin("VF1RJA00O1234567")
        assertFalse(vin.isValid)
        assertEquals("VF1RJA00O1234567", vin.grouped)
        assertNull(vin.manufacturer)
    }

    @Test
    fun unknownMakerStillGrouped() {
        val vin = Vin("0123456789ABCDEFG") // AndrOBD demo VIN
        assertEquals("012 345678 9ABCDEFG", vin.grouped)
        assertNull(vin.manufacturer)
        assertNull(vin.region)
    }

    @Test
    fun categories() {
        assertEquals("Ateşleme", DtcCategory.of("P0301"))
        assertEquals("Emisyon kontrol", DtcCategory.of("P242F"))
        assertEquals("Şanzıman", DtcCategory.of("P0730"))
        assertEquals("Motor · üreticiye özel", DtcCategory.of("P1234"))
        assertEquals("Ağ haberleşmesi", DtcCategory.of("U0100"))
    }
}
