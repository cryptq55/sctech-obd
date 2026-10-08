package com.sctech.obd.core

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import com.fr3ts0n.ecu.EcuCodeItem
import com.fr3ts0n.ecu.EcuDataPv
import com.fr3ts0n.ecu.prot.obd.ElmProt
import com.fr3ts0n.ecu.prot.obd.ObdProt
import com.fr3ts0n.pvs.PvList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.beans.PropertyChangeEvent
import java.util.Locale
import java.util.logging.Logger

/**
 * Single OBD session for the whole process, wrapping AndrOBD's [ElmProt].
 *
 * The AndrOBD protocol keeps its data in static [PvList]s ([ObdProt.PidPvs],
 * [ObdProt.VidPvs], [ObdProt.tCodes]) that are mutated from the receive thread.
 * Instead of mirroring every change event we take a cheap snapshot a few times
 * per second and publish it as immutable [StateFlow]s for the UI.
 */
object ObdSession {

    private val log = Logger.getLogger("ObdSession")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Protocol commands may block (CLEAR_CODES sleeps), keep them off the UI and in order. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val commandDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val elm = ElmProt()

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _ecuState = MutableStateFlow(EcuState.IDLE)
    val ecuState: StateFlow<EcuState> = _ecuState.asStateFlow()

    private val _mode = MutableStateFlow(ObdMode.NONE)
    val mode: StateFlow<ObdMode> = _mode.asStateFlow()

    private val _liveData = MutableStateFlow<List<LiveValue>>(emptyList())
    val liveData: StateFlow<List<LiveValue>> = _liveData.asStateFlow()

    private val _troubleCodes = MutableStateFlow(DtcReadState())
    val troubleCodes: StateFlow<DtcReadState> = _troubleCodes.asStateFlow()

    private val _vehicleInfo = MutableStateFlow<List<InfoItem>>(emptyList())
    val vehicleInfo: StateFlow<List<InfoItem>> = _vehicleInfo.asStateFlow()

    /** Negative responses (NRC) from the ECU, already formatted by AndrOBD. */
    private val _ecuErrors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val ecuErrors: SharedFlow<String> = _ecuErrors.asSharedFlow()

    private var transport: BluetoothTransport? = null
    private var demoThread: Thread? = null
    private var pollJob: Job? = null

    /** Mode the UI wants; (re)applied whenever the ECU becomes ready. */
    @Volatile private var desiredMode = ObdMode.NONE
    @Volatile private var ecuDetected = false
    @Volatile private var livePids: IntArray = Pids.DASHBOARD
    @Volatile private var fixedPidsForCount = -1

    // DTC read progress tracking
    @Volatile private var dtcReadStartedAt = 0L
    @Volatile private var dtcLastChangeAt = 0L

    init {
        elm.addPropertyChangeListener { evt -> onProtocolEvent(evt) }
    }

    // ---------------------------------------------------------------- connection

    /**
     * Connect to a paired ELM327 over classic Bluetooth.
     * Caller must hold BLUETOOTH_CONNECT (API 31+).
     */
    fun connectBluetooth(
        adapter: BluetoothAdapter?,
        device: BluetoothDevice,
        displayName: String,
        secure: Boolean = false,
    ) {
        disconnect()
        _connection.value = ConnectionState.Connecting(displayName)

        lateinit var created: BluetoothTransport
        created = BluetoothTransport(adapter, device, secure, elm, object : BluetoothTransport.Listener {
            override fun onConnected() {
                if (transport !== created) return
                _connection.value = ConnectionState.Connected(displayName, demo = false)
                startPolling()
                // ATZ + adapter init + ECU detection; READY status triggers desiredMode
                elm.reset()
            }

            override fun onConnectFailed(error: Exception) {
                if (transport !== created) return
                teardown()
                _connection.value = ConnectionState.Failed(FailureReason.CONNECT_FAILED)
            }

            override fun onConnectionLost() {
                if (transport !== created) return
                teardown()
                _connection.value = ConnectionState.Failed(FailureReason.CONNECTION_LOST)
            }
        })
        transport = created
        created.open()
    }

    /** Simulated vehicle from AndrOBD's demo loop, for development without a car. */
    fun startDemo() {
        disconnect()
        _connection.value = ConnectionState.Connected("Demo", demo = true)
        startPolling()
        demoThread = Thread(elm, "obd-demo").also { it.start() }
    }

    fun disconnect() {
        teardown()
        _connection.value = ConnectionState.Disconnected
    }

    private fun teardown() {
        ElmProt.runDemo = false
        // runDemo is a static flag: a restarted demo would revive a still-running old loop
        demoThread?.join(DEMO_STOP_TIMEOUT_MS)
        demoThread = null
        transport?.let {
            transport = null
            it.close()
        }
        pollJob?.cancel()
        pollJob = null
        elm.reset()
        ecuDetected = false
        _ecuState.value = EcuState.IDLE
        _mode.value = ObdMode.NONE
        _liveData.value = emptyList()
        _vehicleInfo.value = emptyList()
        _troubleCodes.update { it.copy(reading = false) }
    }

    // ---------------------------------------------------------------- commands

    /** Poll Mode 01 data; [pids] limits the loop for faster refresh (empty = all supported). */
    fun startLiveData(pids: IntArray = Pids.DASHBOARD) {
        livePids = pids.sortedArray()
        fixedPidsForCount = -1
        requestMode(ObdMode.LIVE_DATA)
    }

    fun readTroubleCodes() = requestMode(ObdMode.TROUBLE_CODES)

    fun readVehicleInfo() = requestMode(ObdMode.VEHICLE_INFO)

    /**
     * Clear DTCs (Mode 04) and re-read them.
     * Also resets readiness monitors on the ECU; the UI must warn before calling.
     */
    fun clearTroubleCodes() {
        if (!ecuDetected) return
        desiredMode = ObdMode.TROUBLE_CODES
        markDtcReadStarted()
        scope.launch(commandDispatcher) {
            elm.setService(ObdProt.OBD_SVC_CLEAR_CODES, true)
            elm.setService(ObdProt.OBD_SVC_READ_CODES, true)
            _mode.value = ObdMode.TROUBLE_CODES
        }
    }

    private fun requestMode(mode: ObdMode) {
        desiredMode = mode
        if (ecuDetected) applyMode(mode)
    }

    private fun applyMode(mode: ObdMode) {
        if (mode == ObdMode.TROUBLE_CODES) markDtcReadStarted()
        scope.launch(commandDispatcher) {
            val service = when (mode) {
                ObdMode.NONE -> ObdProt.OBD_SVC_NONE
                ObdMode.LIVE_DATA -> ObdProt.OBD_SVC_DATA
                ObdMode.TROUBLE_CODES -> ObdProt.OBD_SVC_READ_CODES
                ObdMode.VEHICLE_INFO -> ObdProt.OBD_SVC_VEH_INFO
            }
            // ElmProt ignores setService() for the active service, so bounce via NONE to re-run it
            if (elm.service == service) elm.setService(ObdProt.OBD_SVC_NONE, false)
            ObdProt.resetFixedPid()
            fixedPidsForCount = -1
            elm.setService(service, true)
            _mode.value = mode
        }
    }

    private fun markDtcReadStarted() {
        val now = System.currentTimeMillis()
        dtcReadStartedAt = now
        dtcLastChangeAt = now
        _troubleCodes.update { it.copy(reading = true, codes = emptyList(), reportedCount = null) }
    }

    // ---------------------------------------------------------------- protocol events (RX thread)

    private fun onProtocolEvent(evt: PropertyChangeEvent) {
        when (evt.propertyName) {
            ElmProt.PROP_STATUS -> onElmStatus(evt.newValue as ElmProt.STAT)

            ElmProt.PROP_ECU_ADDRESS -> {
                @Suppress("UNCHECKED_CAST")
                val addresses = (evt.newValue as? Set<Int>)?.toSortedSet() ?: return
                if (addresses.size > 1) elm.setEcuAddress(preferredEcu(addresses))
            }

            ObdProt.PROP_NUM_CODES -> {
                // From PID 01 byte A: bit 7 is the MIL flag, bits 0-6 the code count
                val count = (evt.newValue as? Int)?.and(0x7F)
                _troubleCodes.update { it.copy(reportedCount = count) }
            }

            ObdProt.PROP_NRC -> (evt.newValue as? String)?.let { _ecuErrors.tryEmit(it) }
        }
    }

    /**
     * ElmProt reports CONNECTED on every data frame and NODATA whenever a single PID
     * stays unanswered, so those only mean something before detection has finished.
     * ECU_DETECTED is the one reliable "ready" edge (also after an adapter reset).
     */
    private fun onElmStatus(stat: ElmProt.STAT) {
        when (stat) {
            ElmProt.STAT.ECU_DETECTED -> {
                ecuDetected = true
                _ecuState.value = EcuState.READY
                if (desiredMode != ObdMode.NONE) applyMode(desiredMode)
            }

            ElmProt.STAT.INITIALIZING, ElmProt.STAT.INITIALIZED, ElmProt.STAT.ECU_DETECT -> {
                ecuDetected = false
                _ecuState.value = stat.toEcuState()
            }

            ElmProt.STAT.CONNECTED, ElmProt.STAT.NODATA, ElmProt.STAT.DATAERROR ->
                if (!ecuDetected) _ecuState.value = stat.toEcuState()

            else -> _ecuState.value = stat.toEcuState()
        }
    }

    /** Prefer the engine ECU when several modules answer. */
    private fun preferredEcu(addresses: Set<Int>): Int = when {
        0x7E8 in addresses -> 0x7E8          // 11-bit CAN engine
        0x18DAF110 in addresses -> 0x18DAF110 // 29-bit CAN engine
        0x10 in addresses -> 0x10            // ISO/KWP engine
        else -> addresses.first()
    }

    // ---------------------------------------------------------------- snapshots

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                try {
                    when (_mode.value) {
                        ObdMode.LIVE_DATA -> pollLiveData()
                        ObdMode.TROUBLE_CODES -> pollTroubleCodes()
                        ObdMode.VEHICLE_INFO -> _vehicleInfo.value = snapshotVehicleInfo()
                        ObdMode.NONE -> Unit
                    }
                } catch (e: Exception) {
                    log.warning("snapshot failed: $e")
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun pollLiveData() {
        val items = snapshot(ObdProt.PidPvs).filterIsInstance<EcuDataPv>()
        // Supported-PID list (re)built -> limit polling loop to the PIDs we display
        if (items.size != fixedPidsForCount && livePids.isNotEmpty() && items.isNotEmpty()) {
            ObdProt.resetFixedPid()
            ObdProt.setFixedPid(livePids)
            fixedPidsForCount = items.size
        }
        _liveData.value = items.mapNotNull { it.toLiveValue() }
    }

    private fun pollTroubleCodes() {
        val codes = snapshot(ObdProt.tCodes)
            .filterIsInstance<EcuCodeItem>()
            .mapNotNull { it.toTroubleCode() }
            .distinctBy { it.code to it.kind }
            .sortedWith(compareBy({ it.kind }, { it.code }))

        val now = System.currentTimeMillis()
        _troubleCodes.update { state ->
            if (codes != state.codes) dtcLastChangeAt = now
            val settled = now - dtcReadStartedAt > DTC_MIN_READ_MS && now - dtcLastChangeAt > DTC_SETTLE_MS
            val timedOut = now - dtcReadStartedAt > DTC_MAX_READ_MS
            val done = state.reading && (settled || timedOut)
            state.copy(
                codes = codes,
                reading = state.reading && !done,
                lastReadAt = if (done) now else state.lastReadAt,
            )
        }
    }

    private fun snapshotVehicleInfo(): List<InfoItem> =
        snapshot(ObdProt.VidPvs).filterIsInstance<EcuDataPv>().mapNotNull { pv ->
            val pid = pv.get(EcuDataPv.FID_PID) as? Int ?: return@mapNotNull null
            val mnemonic = pv.get(EcuDataPv.FID_MNEMONIC) as? String ?: return@mapNotNull null
            val label = pv.get(EcuDataPv.FID_DESCRIPT)?.toString() ?: return@mapNotNull null
            InfoItem(pid, mnemonic, label, pv.formattedValue().trim())
        }.sortedWith(compareBy({ it.pid }, { it.mnemonic }))

    /**
     * Copy the list's children while holding only the list lock. Child PVs are read
     * afterwards: the RX thread locks child -> list, so reading children inside the
     * list lock could deadlock.
     */
    private fun snapshot(list: PvList): List<Any?> = synchronized(list) { ArrayList(list.values) }

    private fun EcuDataPv.toLiveValue(): LiveValue? {
        val pid = get(EcuDataPv.FID_PID) as? Int ?: return null
        val mnemonic = get(EcuDataPv.FID_MNEMONIC) as? String ?: return null
        return LiveValue(
            pid = pid,
            mnemonic = mnemonic,
            label = get(EcuDataPv.FID_DESCRIPT)?.toString().orEmpty(),
            value = (get(EcuDataPv.FID_VALUE) as? Number)?.toDouble(),
            text = formattedValue(),
            units = units,
        )
    }

    /** Value rendered with the item's format from pids.csv (e.g. "%.0f", "0x%08x"). */
    private fun EcuDataPv.formattedValue(): String {
        val raw = get(EcuDataPv.FID_VALUE) ?: return ""
        val format = get(EcuDataPv.FID_FORMAT) as? String ?: return raw.toString()
        // Integer formats (%d, %x) reject the Float/Double values conversions produce
        val arg: Any = if (raw is Number && format.contains(Regex("%[0-9#]*[dxX]"))) raw.toLong() else raw
        return runCatching { String.format(Locale.getDefault(), format, arg) }.getOrElse { raw.toString() }
    }

    private fun EcuCodeItem.toTroubleCode(): TroubleCode? {
        val code = get(EcuCodeItem.FID_CODE)?.toString() ?: return null
        // key 0 / P0000 is AndrOBD's "no trouble codes set" placeholder
        if (!DTC_PATTERN.matches(code) || code == "P0000") return null
        val kind = when (get(EcuCodeItem.FID_STATUS) as? Int) {
            ObdProt.OBD_SVC_PENDINGCODES -> DtcKind.PENDING
            ObdProt.OBD_SVC_PERMACODES -> DtcKind.PERMANENT
            else -> DtcKind.STORED
        }
        return TroubleCode(code, get(EcuCodeItem.FID_DESCRIPT)?.toString().orEmpty(), kind)
    }

    private val DTC_PATTERN = Regex("[PCBU][0-9A-F]{4}")
    private const val POLL_INTERVAL_MS = 250L
    private const val DTC_MIN_READ_MS = 2_500L
    private const val DTC_SETTLE_MS = 1_500L
    private const val DTC_MAX_READ_MS = 10_000L
    private const val DEMO_STOP_TIMEOUT_MS = 1_500L
}
