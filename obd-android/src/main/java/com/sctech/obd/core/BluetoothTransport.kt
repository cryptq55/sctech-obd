/*
 * Port of AndrOBD's BtCommService (C) 2015 fr3ts0n, GPL.
 * Android Handler/Activity coupling replaced by a plain listener interface.
 */
package com.sctech.obd.core

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.fr3ts0n.ecu.prot.obd.ElmProt
import com.fr3ts0n.prot.StreamHandler
import java.io.IOException
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.concurrent.thread

/**
 * Classic Bluetooth (SPP/RFCOMM) link to an ELM327 adapter.
 * One instance = one connection attempt; create a new one to reconnect.
 */
@SuppressLint("MissingPermission") // caller guarantees BLUETOOTH_CONNECT
internal class BluetoothTransport(
    private val adapter: BluetoothAdapter?,
    private val device: BluetoothDevice,
    private val secure: Boolean,
    private val elm: ElmProt,
    private val listener: Listener,
) {
    interface Listener {
        fun onConnected()
        fun onConnectFailed(error: Exception)
        fun onConnectionLost()
    }

    private val ser = StreamHandler()

    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var closed = false

    fun open() {
        thread(name = "obd-bt") { run() }
    }

    fun close() {
        closed = true
        elm.removeTelegramWriter(ser)
        try {
            socket?.close()
        } catch (e: IOException) {
            log.log(Level.FINE, "close", e)
        }
    }

    private fun run() {
        val connected = try {
            connectSocket()
        } catch (e: Exception) {
            log.log(Level.WARNING, "BT connect failed", e)
            if (!closed) listener.onConnectFailed(e)
            return
        }
        if (closed) {
            runCatching { connected.close() }
            return
        }

        // Delay before first traffic (fix for AndrOBD issue #233)
        Thread.sleep(CONNECT_SETTLE_MS)

        ser.setStreams(connected.inputStream, connected.outputStream)
        elm.addTelegramWriter(ser)
        ser.setMessageHandler(elm)
        listener.onConnected()

        // Blocks until the stream closes or fails
        ser.run()

        elm.removeTelegramWriter(ser)
        if (!closed) listener.onConnectionLost()
    }

    private fun connectSocket(): BluetoothSocket {
        // Discovery slows down connecting considerably
        try {
            adapter?.cancelDiscovery()
        } catch (e: SecurityException) {
            log.log(Level.FINE, "cancelDiscovery not permitted", e)
        }

        val primary = if (secure) {
            device.createRfcommSocketToServiceRecord(SPP_UUID)
        } else {
            device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
        }
        socket = primary
        try {
            primary.connect()
            return primary
        } catch (e: IOException) {
            log.log(Level.INFO, "SPP connect failed, trying RFCOMM channel 1 fallback", e)
            runCatching { primary.close() }
            if (closed) throw e
        }

        // Many cheap ELM327 clones only work via the hidden channel-1 API
        val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
        val fallback = method.invoke(device, 1) as BluetoothSocket
        socket = fallback
        fallback.connect()
        return fallback
    }

    private companion object {
        val log: Logger = Logger.getLogger("BluetoothTransport")
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val CONNECT_SETTLE_MS = 500L
    }
}
