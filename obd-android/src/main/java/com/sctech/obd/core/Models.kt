package com.sctech.obd.core

import com.fr3ts0n.ecu.prot.obd.ElmProt

/** Adapter link state (transport level). */
sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connecting(val deviceName: String) : ConnectionState
    data class Connected(val deviceName: String, val demo: Boolean) : ConnectionState
    data class Failed(val reason: FailureReason) : ConnectionState
}

enum class FailureReason { CONNECT_FAILED, CONNECTION_LOST }

/** Coarse vehicle/ECU readiness derived from [ElmProt.STAT]. */
enum class EcuState { IDLE, INITIALIZING, SEARCHING, READY, NO_RESPONSE, ERROR }

internal fun ElmProt.STAT.toEcuState(): EcuState = when (this) {
    ElmProt.STAT.UNDEFINED, ElmProt.STAT.STOPPED -> EcuState.IDLE
    ElmProt.STAT.INITIALIZING, ElmProt.STAT.INITIALIZED, ElmProt.STAT.CONNECTING -> EcuState.INITIALIZING
    ElmProt.STAT.ECU_DETECT -> EcuState.SEARCHING
    ElmProt.STAT.ECU_DETECTED, ElmProt.STAT.CONNECTED -> EcuState.READY
    // NO DATA / BUS INIT errors: typically ignition off or unsupported protocol
    ElmProt.STAT.NODATA, ElmProt.STAT.DISCONNECTED -> EcuState.NO_RESPONSE
    ElmProt.STAT.BUSERROR, ElmProt.STAT.DATAERROR, ElmProt.STAT.RXERROR, ElmProt.STAT.ERROR -> EcuState.ERROR
}

/** OBD mode currently being polled by the protocol loop. */
enum class ObdMode { NONE, LIVE_DATA, TROUBLE_CODES, VEHICLE_INFO }

/** One live measurement (Mode 01 data item). */
data class LiveValue(
    val pid: Int,
    val mnemonic: String,
    val label: String,
    val value: Double?,
    val text: String,
    val units: String,
)

enum class DtcKind { STORED, PENDING, PERMANENT }

data class TroubleCode(
    val code: String,
    /** Short description from AndrOBD codes*.properties (localized when available). */
    val libraryDescription: String,
    val kind: DtcKind,
)

data class DtcReadState(
    val reading: Boolean = false,
    val codes: List<TroubleCode> = emptyList(),
    /** null = never read in this session */
    val lastReadAt: Long? = null,
    /** MIL/number-of-codes value reported by PID 01, if received */
    val reportedCount: Int? = null,
)

/** One Mode 09 vehicle information item. */
data class InfoItem(val pid: Int, val mnemonic: String, val label: String, val value: String) {
    /** Protocol bookkeeping (message/item counts), not useful to show. */
    val isCounter: Boolean get() = mnemonic.startsWith("counts_") || mnemonic.endsWith("_numitems")

    companion object {
        const val MNEMONIC_VIN = "vehicle_identification_number"
    }
}

/** Well-known Mode 01 PIDs used by the MVP dashboard. */
object Pids {
    /** Monitor status: MIL on/off + number of stored codes. */
    const val MONITOR_STATUS = 0x01
    const val COOLANT_TEMP = 0x05
    const val ENGINE_RPM = 0x0C
    const val VEHICLE_SPEED = 0x0D
    const val MODULE_VOLTAGE = 0x42

    val DASHBOARD = intArrayOf(MONITOR_STATUS, COOLANT_TEMP, ENGINE_RPM, VEHICLE_SPEED, MODULE_VOLTAGE)

    // Item mnemonics from AndrOBD's pids.csv (one PID can carry several items)
    const val MNEMONIC_MIL = "status_mil"
    const val MNEMONIC_CODE_COUNT = "number_fault_codes"
}
