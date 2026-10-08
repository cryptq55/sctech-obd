package com.sctech.obd.expertiz

import com.sctech.obd.core.ConnectionState
import com.sctech.obd.core.EcuState
import com.sctech.obd.core.InfoItem
import com.sctech.obd.core.ObdSession
import com.sctech.obd.core.Pids
import com.sctech.obd.data.DtcRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs an expertiz on the connected car: counters and readiness, trouble codes, VIN,
 * then the analysis. Process-wide so the run survives switching tabs.
 */
object ExpertizController {

    enum class Step { COUNTERS, CODES, IDENTITY, ANALYSIS }

    sealed interface State {
        data object Idle : State
        data class Running(val step: Step) : State
        data class Done(val report: ExpertizReport) : State
        data class Failed(val reason: Reason) : State
    }

    enum class Reason { NOT_READY, CONNECTION_LOST }

    /** PIDs read for the report; unsupported ones simply stay absent. */
    private val EXPERTIZ_PIDS = intArrayOf(
        Pids.MONITOR_STATUS, // MIL, code count, readiness monitors
        0x21, // distance with MIL on
        0x30, // warm-ups since codes cleared
        0x31, // distance since codes cleared
        0x4D, // engine time with MIL on
        0x4E, // engine time since codes cleared
        0xA6, // odometer (newer cars only)
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun start(repository: DtcRepository) {
        if (job?.isActive == true) return
        if (ObdSession.ecuState.value != EcuState.READY) {
            _state.value = State.Failed(Reason.NOT_READY)
            return
        }
        job = scope.launch {
            val watchdog = launch {
                ObdSession.connection.first { it !is ConnectionState.Connected }
                _state.value = State.Failed(Reason.CONNECTION_LOST)
                job?.cancel()
            }
            try {
                _state.value = State.Done(run(repository))
            } finally {
                watchdog.cancel()
            }
        }
    }

    fun reset() {
        job?.cancel()
        _state.value = State.Idle
    }

    private suspend fun run(repository: DtcRepository): ExpertizReport {
        val demo = (ObdSession.connection.value as? ConnectionState.Connected)?.demo == true

        // 1) Monitor status and "since codes cleared" counters
        _state.value = State.Running(Step.COUNTERS)
        ObdSession.startLiveData(EXPERTIZ_PIDS)
        delay(MODE_SWITCH_MS) // the protocol clears and rebuilds its PID list
        withTimeoutOrNull(STEP_TIMEOUT_MS) {
            ObdSession.liveData.first { list -> list.any { it.mnemonic == Pids.MNEMONIC_MIL } }
        }
        // Items exist as soon as the PID is known to be supported; give the poll loop
        // time to fetch each of the few fixed PIDs at least once.
        delay(SAMPLE_MS)
        val live = ObdSession.liveData.value

        // 2) Stored, pending and permanent trouble codes
        _state.value = State.Running(Step.CODES)
        val codesStartedAt = System.currentTimeMillis()
        ObdSession.readTroubleCodes()
        val dtc = withTimeoutOrNull(CODES_TIMEOUT_MS) {
            ObdSession.troubleCodes.first { !it.reading && (it.lastReadAt ?: 0L) >= codesStartedAt }
        } ?: ObdSession.troubleCodes.value

        // 3) VIN
        _state.value = State.Running(Step.IDENTITY)
        ObdSession.readVehicleInfo()
        val info = withTimeoutOrNull(STEP_TIMEOUT_MS) {
            ObdSession.vehicleInfo.first { items ->
                items.any { it.mnemonic == InfoItem.MNEMONIC_VIN && it.value.isNotBlank() }
            }
        } ?: ObdSession.vehicleInfo.value
        val vin = info.firstOrNull { it.mnemonic == InfoItem.MNEMONIC_VIN }?.value

        // 4) Report
        _state.value = State.Running(Step.ANALYSIS)
        val data = ExpertizAnalyzer.collect(live, dtc.codes, vin, System.currentTimeMillis(), demo)
        delay(ANALYSIS_PAUSE_MS) // keeps the last step visible instead of flashing past
        return ExpertizAnalyzer.analyze(data) { repository.find(it) }
    }

    private const val MODE_SWITCH_MS = 1_500L
    private const val SAMPLE_MS = 4_000L
    private const val STEP_TIMEOUT_MS = 12_000L
    private const val CODES_TIMEOUT_MS = 20_000L
    private const val ANALYSIS_PAUSE_MS = 600L
}
