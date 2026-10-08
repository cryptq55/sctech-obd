package com.sctech.obd.expertiz

import com.sctech.obd.core.TroubleCode

/** One OBD readiness monitor the ECU reports as supported. */
data class MonitorStatus(val key: String, val label: String, val complete: Boolean)

/** Raw facts collected from the car during an expertiz run. Null = not supported / not received. */
data class ExpertizData(
    val milOn: Boolean?,
    val compressionIgnition: Boolean?,
    val monitors: List<MonitorStatus>,
    val distanceSinceClearKm: Double?,
    val timeSinceClearHours: Double?,
    val warmupsSinceClear: Int?,
    val distanceWithMilKm: Double?,
    val odometerKm: Double?,
    val codes: List<TroubleCode>,
    val vin: String?,
    val createdAt: Long,
    val demo: Boolean,
)

enum class CheckStatus {
    PASS, WARN, FAIL,

    /** Informational, never affects the verdict. */
    INFO,

    /** The car did not provide the data for this check. */
    NO_DATA,
}

data class Finding(val label: String, val value: String, val status: CheckStatus? = null)

data class CheckSection(
    val id: SectionId,
    val title: String,
    val status: CheckStatus,
    val summary: String,
    val findings: List<Finding>,
)

enum class SectionId { CLEARED_CODES, TROUBLE_CODES, READINESS, IDENTITY, ODOMETER }

data class ExpertizReport(
    val verdict: CheckStatus,
    val headline: String,
    val summary: String,
    val sections: List<CheckSection>,
    val vin: String?,
    val createdAt: Long,
    val demo: Boolean,
)
