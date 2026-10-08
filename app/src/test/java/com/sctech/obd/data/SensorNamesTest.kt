package com.sctech.obd.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SensorNamesTest {

    @Test
    fun directNameWins() = assertEquals("Motor devri", SensorNames.turkish("engine_speed"))

    @Test
    fun bankSensorPatternsAreExpanded() = assertEquals(
        "Oksijen sensörü voltajı (Bank 2, Sensör 1)",
        SensorNames.turkish("o2_sensor_voltage_b2s1"),
    )

    @Test
    fun unknownFallsBackToLibraryLabel() {
        assertNull(SensorNames.turkish("something_new"))
        assertEquals("Library label", SensorNames.displayName("something_new", "Library label"))
    }
}
