package com.sctech.obd.core

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs AndrOBD's built-in demo ECU through [ObdSession] on the plain JVM.
 * Proves the protocol layer is reusable without any of the original UI code.
 */
class ObdSessionDemoTest {

    @After
    fun tearDown() = ObdSession.disconnect()

    @Test
    fun demoSessionDeliversLiveDataCodesAndVehicleInfo() = runBlocking {
        ObdSession.startLiveData(Pids.DASHBOARD)
        ObdSession.startDemo()

        withTimeout(TIMEOUT_MS) { ObdSession.ecuState.first { it == EcuState.READY } }

        val live = withTimeout(TIMEOUT_MS) {
            ObdSession.liveData.first { values -> values.any { it.pid == Pids.ENGINE_RPM && it.value != null } }
        }
        assertTrue("RPM item expected", live.any { it.mnemonic == "engine_speed" })

        ObdSession.readTroubleCodes()
        val dtc = withTimeout(TIMEOUT_MS) {
            ObdSession.troubleCodes.first { !it.reading && it.lastReadAt != null }
        }
        assertTrue("demo ECU reports codes", dtc.codes.isNotEmpty())
        dtc.codes.forEach { assertTrue(it.code, Regex("[PCBU][0-9A-F]{4}").matches(it.code)) }

        ObdSession.readVehicleInfo()
        val info = withTimeout(TIMEOUT_MS) {
            ObdSession.vehicleInfo.first { items ->
                items.any { it.mnemonic == InfoItem.MNEMONIC_VIN && it.value.isNotBlank() }
            }
        }
        // ElmProt's demo loop sends VIN "0123456789ABCDEFG"
        val vin = info.first { it.mnemonic == InfoItem.MNEMONIC_VIN }.value
        assertTrue("unexpected VIN '$vin'", vin.contains("0123456789ABCDEFG"))
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
    }
}
