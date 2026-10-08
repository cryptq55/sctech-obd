package com.sctech.obd.bt

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.sctech.obd.ObdConnectionService
import com.sctech.obd.core.ObdSession
import com.sctech.obd.data.AppPrefs

/** Bluetooth plumbing shared by the connection wizard and the quick reconnect on the home screen. */
object Connector {

    fun adapter(context: Context): BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    /** Permissions needed to list, discover and connect to classic Bluetooth adapters. */
    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            // Classic discovery needs location on Android 8–11
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    /** Required plus optional ones, requested together so the user sees one prompt. */
    fun requestedPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions() + Manifest.permission.POST_NOTIFICATIONS
        } else {
            requiredPermissions()
        }

    fun hasPermissions(context: Context): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission") // callers check hasPermissions()
    fun connect(context: Context, prefs: AppPrefs, device: BluetoothDevice) {
        ObdSession.connectBluetooth(adapter(context), device, displayName(device))
        prefs.lastDeviceAddress = device.address
        ObdConnectionService.start(context)
    }

    /** The last used adapter, if it is still paired and we may talk to it. */
    @SuppressLint("MissingPermission")
    fun lastDevice(context: Context, prefs: AppPrefs): BluetoothDevice? {
        val address = prefs.lastDeviceAddress ?: return null
        val adapter = adapter(context) ?: return null
        if (!hasPermissions(context) || !adapter.isEnabled) return null
        return adapter.bondedDevices.orEmpty().firstOrNull { it.address == address }
    }

    @SuppressLint("MissingPermission")
    fun displayName(device: BluetoothDevice): String = device.name?.takeIf { it.isNotBlank() } ?: device.address

    private val OBD_NAME_HINTS = listOf("OBD", "ELM", "V-LINK", "VLINK", "VEEPEAK", "KONNWEI", "VGATE", "ICAR", "CAN")

    fun looksLikeObd(name: String?): Boolean =
        name != null && OBD_NAME_HINTS.any { name.contains(it, ignoreCase = true) }

    @SuppressLint("MissingPermission")
    fun sortForObd(devices: Collection<BluetoothDevice>): List<BluetoothDevice> =
        devices.sortedWith(
            compareByDescending<BluetoothDevice> { looksLikeObd(it.name) }.thenBy { displayName(it).lowercase() }
        )
}
