package com.sctech.obd.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Classic Bluetooth discovery and pairing for ELM327 adapters.
 * Bound to a screen: [start] when it appears, [stop] when it goes away.
 */
@SuppressLint("MissingPermission") // only used after Connector.hasPermissions()
class BluetoothScanner(private val context: Context, private val adapter: BluetoothAdapter) {

    data class BondEvent(val device: BluetoothDevice, val state: Int)

    private val _found = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    /** Named, not yet paired, non-LE-only devices seen during discovery. */
    val found: StateFlow<List<BluetoothDevice>> = _found.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _bondEvents = MutableSharedFlow<BondEvent>(extraBufferCapacity = 8)
    val bondEvents: SharedFlow<BondEvent> = _bondEvents.asSharedFlow()

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> device?.let(::onFound)
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> _scanning.value = true
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> _scanning.value = false
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> device?.let {
                    val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                    _bondEvents.tryEmit(BondEvent(it, state))
                    if (state == BluetoothDevice.BOND_BONDED) {
                        _found.update { list -> list.filterNot { d -> d.address == it.address } }
                    }
                }
            }
        }
    }

    private fun onFound(device: BluetoothDevice) {
        // ELM327 clones are classic SPP; LE-only devices can't be used (yet)
        if (device.type == BluetoothDevice.DEVICE_TYPE_LE) return
        if (device.bondState == BluetoothDevice.BOND_BONDED) return
        if (device.name.isNullOrBlank()) return
        _found.update { list -> if (list.any { it.address == device.address }) list else list + device }
    }

    fun start() {
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            }
            // System broadcasts reach non-exported receivers
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
        }
        scan()
    }

    fun scan() {
        try {
            if (adapter.isDiscovering) adapter.cancelDiscovery()
            _scanning.value = adapter.startDiscovery()
        } catch (_: SecurityException) {
            _scanning.value = false
        }
    }

    /** Ends discovery early; some stacks never send ACTION_DISCOVERY_FINISHED. */
    fun cancelScan() {
        runCatching { adapter.cancelDiscovery() }
        _scanning.value = false
    }

    fun pair(device: BluetoothDevice): Boolean {
        // Discovery slows pairing and connecting down considerably
        runCatching { adapter.cancelDiscovery() }
        return try {
            device.createBond()
        } catch (_: SecurityException) {
            false
        }
    }

    fun stop() {
        runCatching { adapter.cancelDiscovery() }
        if (registered) {
            runCatching { context.unregisterReceiver(receiver) }
            registered = false
        }
        _scanning.value = false
    }
}
