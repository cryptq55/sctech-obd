package com.sctech.obd.expertiz

import com.sctech.obd.core.DtcKind
import com.sctech.obd.core.LiveValue
import com.sctech.obd.core.TroubleCode
import com.sctech.obd.data.DtcInfo
import com.sctech.obd.data.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpertizAnalyzerTest {

    private fun live(mnemonic: String, value: Double) = LiveValue(0x01, mnemonic, mnemonic, value, "", "")

    private fun data(
        distance: Double? = 5_000.0,
        warmups: Int? = null,
        monitors: List<MonitorStatus> = emptyList(),
        codes: List<TroubleCode> = emptyList(),
        milOn: Boolean? = false,
        vin: String? = "VF1RJA00X12345678",
        odometer: Double? = null,
    ) = ExpertizData(
        milOn = milOn, compressionIgnition = false, monitors = monitors,
        distanceSinceClearKm = distance, timeSinceClearHours = null, warmupsSinceClear = warmups,
        distanceWithMilKm = null, odometerKm = odometer, codes = codes, vin = vin,
        createdAt = 0L, demo = false,
    )

    private val noInfo: (String) -> DtcInfo? = { null }

    private fun section(report: ExpertizReport, id: SectionId) = report.sections.first { it.id == id }

    @Test
    fun readinessBitsAreDecoded() {
        val monitors = ExpertizAnalyzer.monitorsFrom(
            listOf(
                live("status_misfires", 0x01.toDouble()),          // supported, complete
                live("status_fuel_system", 0x11.toDouble()),       // supported, incomplete
                live("status_component_test", 0x10.toDouble()),    // not supported
                live("status_catalyst_test", 0x101.toDouble()),    // supported, incomplete
                live("status_evaporative_system_test", 0x100.toDouble()), // supported, complete
                live("status_egr_system_test", 0x001.toDouble()),  // not supported
            ),
            compressionIgnition = false,
        )
        assertEquals(listOf("Tekleme izleme", "Yakıt sistemi", "Katalizör", "Yakıt buharı (EVAP)"), monitors.map { it.label })
        assertEquals(listOf(true, false, false, true), monitors.map { it.complete })
    }

    @Test
    fun dieselUsesDieselMonitorNames() {
        val monitors = ExpertizAnalyzer.monitorsFrom(
            listOf(live("status_oxygen_sensor_heater_test", 0x100.toDouble())),
            compressionIgnition = true,
        )
        assertEquals("Partikül filtresi (DPF)", monitors.single().label)
    }

    @Test
    fun recentlyClearedCodesFail() {
        val report = ExpertizAnalyzer.analyze(data(distance = 12.0), noInfo)
        assertEquals(CheckStatus.FAIL, section(report, SectionId.CLEARED_CODES).status)
        assertEquals(CheckStatus.FAIL, report.verdict)
    }

    @Test
    fun clearedAFewHundredKmAgoWarns() {
        val report = ExpertizAnalyzer.analyze(data(distance = 180.0), noInfo)
        assertEquals(CheckStatus.WARN, section(report, SectionId.CLEARED_CODES).status)
    }

    @Test
    fun manyIncompleteMonitorsEscalateEvenWithOldClear() {
        val incomplete = (1..3).map { MonitorStatus("m$it", "M$it", complete = false) }
        val report = ExpertizAnalyzer.analyze(data(distance = 20_000.0, monitors = incomplete), noInfo)
        assertEquals(CheckStatus.WARN, section(report, SectionId.CLEARED_CODES).status)
    }

    @Test
    fun warmupsUsedWhenDistanceMissing() {
        val report = ExpertizAnalyzer.analyze(data(distance = null, warmups = 2), noInfo)
        assertEquals(CheckStatus.FAIL, section(report, SectionId.CLEARED_CODES).status)
    }

    @Test
    fun permanentCodeFailsCodeCheck() {
        val codes = listOf(TroubleCode("P0420", "Catalyst", DtcKind.PERMANENT))
        val report = ExpertizAnalyzer.analyze(data(codes = codes), noInfo)
        assertEquals(CheckStatus.FAIL, section(report, SectionId.TROUBLE_CODES).status)
    }

    @Test
    fun highSeverityCodeFails() {
        val codes = listOf(TroubleCode("P0300", "Misfire", DtcKind.STORED))
        val high = DtcInfo("P0300", "Tekleme", "", listOf("x"), listOf("y"), Severity.HIGH, false)
        val report = ExpertizAnalyzer.analyze(data(codes = codes)) { high }
        assertEquals(CheckStatus.FAIL, section(report, SectionId.TROUBLE_CODES).status)
    }

    @Test
    fun cleanCarPasses() {
        val monitors = listOf(MonitorStatus("cat", "Katalizör", true))
        val report = ExpertizAnalyzer.analyze(data(distance = 8_000.0, monitors = monitors), noInfo)
        assertEquals(CheckStatus.PASS, report.verdict)
        // Identity and odometer are informational and never change the verdict
        assertEquals(CheckStatus.INFO, section(report, SectionId.IDENTITY).status)
        assertEquals(CheckStatus.NO_DATA, section(report, SectionId.ODOMETER).status)
    }

    @Test
    fun distanceFormatting() {
        assertEquals("1.250 km", ExpertizAnalyzer.km(1_250.0))
        assertTrue(ExpertizAnalyzer.km(65_535.0).contains("fazla"))
        assertEquals("184.320 km", ExpertizAnalyzer.km(184_320.0, saturates = false))
        assertFalse(ExpertizAnalyzer.hours(30.0).contains("gün"))
        assertEquals("3 gün", ExpertizAnalyzer.hours(72.0))
    }
}
