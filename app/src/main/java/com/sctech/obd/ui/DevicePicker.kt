package com.sctech.obd.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sctech.obd.ObdConnectionService
import com.sctech.obd.R
import com.sctech.obd.core.ObdSession
import com.sctech.obd.data.AppPrefs

/**
 * Minimal device selection: permission -> Bluetooth on -> paired devices.
 * Will grow into the step-by-step connection wizard (Faz 2).
 */
@SuppressLint("MissingPermission") // guarded by hasConnectPermission()
@Composable
fun DevicePickerDialog(prefs: AppPrefs, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val adapter = remember { context.getSystemService(BluetoothManager::class.java)?.adapter }

    var hasPermission by remember { mutableStateOf(hasConnectPermission(context)) }
    var btEnabled by remember { mutableStateOf(hasPermission && adapter?.isEnabled == true) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = hasConnectPermission(context)
        btEnabled = hasPermission && adapter?.isEnabled == true
    }
    val enableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        btEnabled = adapter?.isEnabled == true
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(runtimePermissions())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.picker_title)) },
        text = {
            when {
                adapter == null -> Text(stringResource(R.string.picker_no_bluetooth))

                !hasPermission -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.picker_permission_rationale))
                    TextButton(onClick = { permissionLauncher.launch(runtimePermissions()) }) {
                        Text(stringResource(R.string.picker_grant_permission))
                    }
                }

                !btEnabled -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.picker_bluetooth_off))
                    TextButton(onClick = {
                        enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    }) {
                        Text(stringResource(R.string.picker_enable_bluetooth))
                    }
                }

                else -> PairedDeviceList(
                    devices = remember { adapter.bondedDevices.orEmpty().sortedForObd() },
                    lastAddress = prefs.lastDeviceAddress,
                    onSelect = { device ->
                        ObdSession.connectBluetooth(adapter, device, device.name ?: device.address)
                        prefs.lastDeviceAddress = device.address
                        ObdConnectionService.start(context)
                        onDismiss()
                    },
                    onOpenSettings = {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@SuppressLint("MissingPermission")
@Composable
private fun PairedDeviceList(
    devices: List<BluetoothDevice>,
    lastAddress: String?,
    onSelect: (BluetoothDevice) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (devices.isEmpty()) {
            Text(stringResource(R.string.picker_no_paired))
        } else {
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(devices, key = { it.address }) { device ->
                    val tag = when {
                        device.address == lastAddress -> stringResource(R.string.picker_last_used)
                        device.name.looksLikeObd() -> stringResource(R.string.picker_likely_obd)
                        else -> null
                    }
                    ListItem(
                        headlineContent = { Text(device.name ?: device.address) },
                        supportingContent = {
                            Text(listOfNotNull(tag, device.address).joinToString(" · "))
                        },
                        modifier = Modifier.clickable { onSelect(device) },
                    )
                    HorizontalDivider()
                }
            }
        }
        Text(
            stringResource(R.string.picker_pairing_hint),
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = onOpenSettings) {
            Text(stringResource(R.string.picker_open_settings))
        }
    }
}

private fun runtimePermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
    // Optional: lets the "connected" notification show; denial is fine
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

private fun hasConnectPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED

private val OBD_NAME_HINTS = listOf("OBD", "ELM", "V-LINK", "VLINK", "VEEPEAK", "KONNWEI", "VGATE", "ICAR")

private fun String?.looksLikeObd(): Boolean =
    this != null && OBD_NAME_HINTS.any { contains(it, ignoreCase = true) }

@SuppressLint("MissingPermission")
private fun Collection<BluetoothDevice>.sortedForObd(): List<BluetoothDevice> =
    sortedWith(compareByDescending<BluetoothDevice> { it.name.looksLikeObd() }.thenBy { it.name ?: it.address })
