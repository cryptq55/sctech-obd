package com.sctech.obd.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sctech.obd.R
import com.sctech.obd.bt.BluetoothScanner
import com.sctech.obd.bt.Connector
import com.sctech.obd.core.ConnectionState
import com.sctech.obd.core.EcuState
import com.sctech.obd.core.FailureReason
import com.sctech.obd.core.ObdSession
import com.sctech.obd.data.AppPrefs
import com.sctech.obd.ui.theme.Sct
import kotlinx.coroutines.delay

private enum class WizardStep { PREPARE, BLUETOOTH, SELECT, CONNECT }

/** How long ECU detection may run before we suggest checking the ignition. */
private const val SLOW_SEARCH_MS = 25_000L

/** Classic discovery normally takes ~12 s. */
private const val SCAN_TIMEOUT_MS = 15_000L

@Composable
fun ConnectWizard(prefs: AppPrefs, onClose: () -> Unit) {
    val context = LocalContext.current
    val adapter = remember { Connector.adapter(context) }
    var step by rememberSaveable { mutableStateOf(WizardStep.PREPARE) }
    var targetAddress by rememberSaveable { mutableStateOf<String?>(null) }

    val ready = { Connector.hasPermissions(context) && adapter?.isEnabled == true }
    val connectTo: (BluetoothDevice) -> Unit = { device ->
        targetAddress = device.address
        Connector.connect(context, prefs, device)
        step = WizardStep.CONNECT
    }

    Column(Modifier.fillMaxSize()) {
        WizardHeader(step, onClose)
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "wizardStep",
        ) { current ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (current) {
                    WizardStep.PREPARE -> PrepareStep(
                        onReady = { step = if (ready()) WizardStep.SELECT else WizardStep.BLUETOOTH },
                        onDemo = {
                            ObdSession.startDemo()
                            onClose()
                        },
                    )

                    WizardStep.BLUETOOTH -> BluetoothStep(adapter, onReady = { step = WizardStep.SELECT })

                    WizardStep.SELECT -> if (adapter != null) {
                        SelectStep(adapter, prefs, onSelect = connectTo)
                    }

                    WizardStep.CONNECT -> ConnectStep(
                        onRetry = {
                            val address = targetAddress
                            if (address != null && adapter != null && ready()) {
                                connectTo(adapter.getRemoteDevice(address))
                            }
                        },
                        onOtherAdapter = {
                            ObdSession.disconnect()
                            step = WizardStep.SELECT
                        },
                        onDone = onClose,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun WizardHeader(step: WizardStep, onClose: () -> Unit) {
    val c = Sct.colors
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_close), tint = c.textPrimary)
            }
            Text(stringResource(R.string.wizard_title), color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.wizard_step_of, step.ordinal + 1, WizardStep.entries.size),
                color = c.textTertiary,
                fontSize = 12.sp,
                style = TabularNumbers,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        // Segmented progress, one segment per step
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 12.dp),
        ) {
            WizardStep.entries.forEach { s ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (s.ordinal <= step.ordinal) c.accent else c.surface2)
                )
            }
        }
    }
}

// ───────────────────────────────────────────────────────────── step 1

@Composable
private fun PrepareStep(onReady: () -> Unit, onDemo: () -> Unit) {
    Panel {
        Eyebrow(stringResource(R.string.wizard_prepare_eyebrow))
        Spacer(Modifier.height(4.dp))
        StepTitle(stringResource(R.string.wizard_prepare_title))
        Spacer(Modifier.height(16.dp))
        NumberedItem(1, R.string.wizard_prepare_1, R.string.wizard_prepare_1_sub)
        NumberedItem(2, R.string.wizard_prepare_2, R.string.wizard_prepare_2_sub)
        NumberedItem(3, R.string.wizard_prepare_3, R.string.wizard_prepare_3_sub)
        Spacer(Modifier.height(16.dp))
        PrimaryButton(stringResource(R.string.wizard_prepare_ready), R.drawable.ic_check, onClick = onReady)
    }
    TextButton(onClick = onDemo, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.wizard_try_demo), color = Sct.colors.textSecondary)
    }
}

@Composable
private fun NumberedItem(number: Int, title: Int, subtitle: Int) {
    val c = Sct.colors
    Row(Modifier.padding(vertical = 8.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .border(1.dp, c.hairline, CircleShape)
                .background(c.surface2, CircleShape),
        ) {
            Text(number.toString(), color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(stringResource(subtitle), color = c.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

// ───────────────────────────────────────────────────────────── step 2

@Composable
private fun BluetoothStep(adapter: BluetoothAdapter?, onReady: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(Connector.hasPermissions(context)) }
    var btOn by remember { mutableStateOf(adapter?.isEnabled == true) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        askedOnce = true
        hasPermission = Connector.hasPermissions(context)
    }
    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        btOn = adapter?.isEnabled == true
    }

    // Coming back from system settings
    LifecycleResumeEffect(Unit) {
        hasPermission = Connector.hasPermissions(context)
        btOn = adapter?.isEnabled == true
        onPauseOrDispose {}
    }
    LaunchedEffect(hasPermission, btOn) {
        if (hasPermission && btOn) {
            delay(400) // let the user see both rows turn green
            onReady()
        }
    }

    Panel {
        Eyebrow(stringResource(R.string.wizard_bt_eyebrow))
        Spacer(Modifier.height(4.dp))
        StepTitle(stringResource(R.string.wizard_bt_title))
        Spacer(Modifier.height(12.dp))

        if (adapter == null) {
            Hint(stringResource(R.string.picker_no_bluetooth))
            return@Panel
        }

        CheckRow(
            done = hasPermission,
            title = stringResource(R.string.wizard_bt_permission),
            subtitle = stringResource(R.string.wizard_bt_permission_sub),
        ) {
            if (!askedOnce) {
                SecondaryButton(stringResource(R.string.picker_grant_permission), onClick = {
                    permissionLauncher.launch(Connector.requestedPermissions())
                })
            } else {
                // Android stops showing the prompt after repeated denials
                SecondaryButton(stringResource(R.string.wizard_open_settings), onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                })
            }
        }
        HorizontalDivider(thickness = 1.dp, color = Sct.colors.hairline)
        CheckRow(
            done = btOn,
            title = stringResource(R.string.wizard_bt_on),
            subtitle = stringResource(R.string.wizard_bt_on_sub),
        ) {
            SecondaryButton(
                stringResource(R.string.picker_enable_bluetooth),
                enabled = hasPermission,
                onClick = { enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
            )
        }
    }
}

@Composable
private fun CheckRow(done: Boolean, title: String, subtitle: String, action: @Composable () -> Unit) {
    val c = Sct.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
        StateIcon(if (done) RowState.DONE else RowState.PENDING)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = c.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        if (!done) {
            Spacer(Modifier.width(10.dp))
            action()
        }
    }
}

// ───────────────────────────────────────────────────────────── step 3

@SuppressLint("MissingPermission")
@Composable
private fun SelectStep(adapter: BluetoothAdapter, prefs: AppPrefs, onSelect: (BluetoothDevice) -> Unit) {
    val context = LocalContext.current
    val c = Sct.colors
    val scanner = remember { BluetoothScanner(context.applicationContext, adapter) }
    val found by scanner.found.collectAsStateWithLifecycle()
    val scanning by scanner.scanning.collectAsStateWithLifecycle()
    var pairingAddress by remember { mutableStateOf<String?>(null) }
    var pairingFailed by remember { mutableStateOf(false) }
    var bondTick by remember { mutableIntStateOf(0) }

    DisposableEffect(scanner) {
        scanner.start()
        onDispose { scanner.stop() }
    }
    LaunchedEffect(scanning) {
        if (scanning) {
            delay(SCAN_TIMEOUT_MS)
            scanner.cancelScan()
        }
    }
    LaunchedEffect(scanner) {
        scanner.bondEvents.collect { event ->
            bondTick++
            if (event.device.address != pairingAddress) return@collect
            when (event.state) {
                BluetoothDevice.BOND_BONDED -> {
                    pairingAddress = null
                    onSelect(event.device)
                }
                BluetoothDevice.BOND_NONE -> {
                    pairingAddress = null
                    pairingFailed = true
                }
            }
        }
    }

    val paired = remember(bondTick) { Connector.sortForObd(adapter.bondedDevices.orEmpty()) }
    val nearby = Connector.sortForObd(found)

    Panel {
        Eyebrow(stringResource(R.string.wizard_select_eyebrow))
        Spacer(Modifier.height(4.dp))
        StepTitle(stringResource(R.string.wizard_select_title))
        Spacer(Modifier.height(4.dp))
        Hint(stringResource(R.string.wizard_select_hint))
    }

    Panel {
        Eyebrow(stringResource(R.string.wizard_paired))
        Spacer(Modifier.height(4.dp))
        if (paired.isEmpty()) {
            Hint(stringResource(R.string.wizard_paired_empty))
        }
        paired.forEachIndexed { index, device ->
            if (index > 0) HorizontalDivider(thickness = 1.dp, color = c.hairline)
            DeviceRow(
                device = device,
                tag = when {
                    device.address == prefs.lastDeviceAddress -> stringResource(R.string.picker_last_used)
                    Connector.looksLikeObd(device.name) -> stringResource(R.string.picker_likely_obd)
                    else -> null
                },
                busy = false,
                onClick = { onSelect(device) },
            )
        }
    }

    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(stringResource(R.string.wizard_nearby), Modifier.weight(1f))
            if (scanning) {
                CircularProgressIndicator(Modifier.size(14.dp), color = c.textSecondary, strokeWidth = 2.dp)
            } else {
                Text(
                    stringResource(R.string.wizard_scan_again),
                    color = c.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { scanner.scan() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        if (nearby.isEmpty()) {
            Hint(stringResource(if (scanning) R.string.wizard_scanning else R.string.wizard_nearby_empty))
        }
        nearby.forEachIndexed { index, device ->
            if (index > 0) HorizontalDivider(thickness = 1.dp, color = c.hairline)
            DeviceRow(
                device = device,
                tag = if (Connector.looksLikeObd(device.name)) stringResource(R.string.picker_likely_obd) else null,
                busy = device.address == pairingAddress,
                onClick = {
                    if (pairingAddress == null) {
                        pairingFailed = false
                        pairingAddress = device.address
                        if (!scanner.pair(device)) {
                            pairingAddress = null
                            pairingFailed = true
                        }
                    }
                },
            )
        }
        if (pairingAddress != null || pairingFailed) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(if (pairingFailed) R.string.wizard_pair_failed else R.string.wizard_pair_pin),
                color = if (pairingFailed) c.danger else c.warning,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun DeviceRow(device: BluetoothDevice, tag: String?, busy: Boolean, onClick: () -> Unit) {
    val c = Sct.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .background(c.surface2, CircleShape),
        ) {
            Icon(painterResource(R.drawable.ic_bluetooth), null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(Connector.displayName(device), color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                listOfNotNull(tag, device.address).joinToString(" · "),
                color = if (tag != null) c.textSecondary else c.textTertiary,
                fontSize = 12.sp,
                style = TabularNumbers,
            )
        }
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), color = c.accent, strokeWidth = 2.dp)
        } else {
            Icon(painterResource(R.drawable.ic_chevron_right), null, tint = c.textTertiary)
        }
    }
}

// ───────────────────────────────────────────────────────────── step 4

internal enum class RowState { PENDING, ACTIVE, DONE, FAILED }

@Composable
private fun ConnectStep(onRetry: () -> Unit, onOtherAdapter: () -> Unit, onDone: () -> Unit) {
    val connection by ObdSession.connection.collectAsStateWithLifecycle()
    val ecu by ObdSession.ecuState.collectAsStateWithLifecycle()
    val c = Sct.colors

    val linkState = when (connection) {
        is ConnectionState.Connecting -> RowState.ACTIVE
        is ConnectionState.Connected -> RowState.DONE
        is ConnectionState.Failed -> RowState.FAILED
        ConnectionState.Disconnected -> RowState.PENDING
    }
    val connected = connection is ConnectionState.Connected
    val initState = when {
        !connected -> RowState.PENDING
        ecu == EcuState.IDLE || ecu == EcuState.INITIALIZING -> RowState.ACTIVE
        else -> RowState.DONE
    }
    val ecuFailed = ecu == EcuState.NO_RESPONSE || ecu == EcuState.ERROR
    val searchState = when {
        !connected || initState != RowState.DONE -> RowState.PENDING
        ecu == EcuState.READY -> RowState.DONE
        ecuFailed -> RowState.FAILED
        else -> RowState.ACTIVE
    }

    var slowSearch by remember { mutableStateOf(false) }
    LaunchedEffect(searchState) {
        slowSearch = false
        if (searchState == RowState.ACTIVE) {
            delay(SLOW_SEARCH_MS)
            slowSearch = true
        }
    }

    val deviceName = (connection as? ConnectionState.Connected)?.deviceName
        ?: (connection as? ConnectionState.Connecting)?.deviceName

    Panel {
        Eyebrow(stringResource(R.string.wizard_connect_eyebrow))
        Spacer(Modifier.height(4.dp))
        StepTitle(deviceName ?: stringResource(R.string.wizard_connect_title))
        Spacer(Modifier.height(8.dp))
        ProgressRow(linkState, R.string.wizard_row_link, R.string.wizard_row_link_sub)
        ProgressRow(initState, R.string.wizard_row_init, R.string.wizard_row_init_sub)
        ProgressRow(searchState, R.string.wizard_row_search, R.string.wizard_row_search_sub)
        if (slowSearch) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.wizard_slow_search), color = c.warning, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }

    when {
        searchState == RowState.DONE -> Panel(borderColor = c.success.copy(alpha = 0.45f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StateIcon(RowState.DONE)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.wizard_done_title), color = c.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.wizard_done_sub), color = c.textSecondary, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            PrimaryButton(stringResource(R.string.wizard_done_action), R.drawable.ic_chevron_right, onClick = onDone)
        }

        linkState == RowState.FAILED -> TroubleshootPanel(
            title = stringResource(
                if ((connection as? ConnectionState.Failed)?.reason == FailureReason.CONNECTION_LOST) {
                    R.string.status_connection_lost
                } else {
                    R.string.status_connect_failed
                }
            ),
            tips = listOf(
                stringResource(R.string.wizard_tip_light),
                stringResource(R.string.wizard_tip_other_app),
                stringResource(R.string.wizard_tip_replug),
                stringResource(R.string.wizard_tip_pairing),
            ),
            onRetry = onRetry,
            onOtherAdapter = onOtherAdapter,
        )

        searchState == RowState.FAILED -> TroubleshootPanel(
            title = stringResource(R.string.wizard_no_ecu_title),
            tips = listOf(
                stringResource(R.string.wizard_tip_ignition),
                stringResource(R.string.wizard_tip_engine_running),
                stringResource(R.string.wizard_tip_socket),
                stringResource(R.string.wizard_tip_clone),
            ),
            onRetry = onRetry,
            onOtherAdapter = onOtherAdapter,
        )
    }
}

@Composable
private fun TroubleshootPanel(title: String, tips: List<String>, onRetry: () -> Unit, onOtherAdapter: () -> Unit) {
    val c = Sct.colors
    Panel(borderColor = c.danger.copy(alpha = 0.45f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StateIcon(RowState.FAILED)
            Spacer(Modifier.width(14.dp))
            Text(title, color = c.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Eyebrow(stringResource(R.string.wizard_tips))
        Spacer(Modifier.height(6.dp))
        BulletList(tips)
        Spacer(Modifier.height(16.dp))
        PrimaryButton(stringResource(R.string.action_retry), R.drawable.ic_link, onClick = onRetry)
        Spacer(Modifier.height(10.dp))
        SecondaryButton(
            stringResource(R.string.wizard_other_adapter),
            R.drawable.ic_bluetooth,
            modifier = Modifier.fillMaxWidth(),
            onClick = onOtherAdapter,
        )
    }
}

@Composable
internal fun ProgressRow(state: RowState, title: Int, subtitle: Int) {
    val c = Sct.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
        StateIcon(state)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                stringResource(title),
                color = if (state == RowState.PENDING) c.textTertiary else c.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(stringResource(subtitle), color = c.textTertiary, fontSize = 12.sp)
        }
    }
}

@Composable
internal fun StateIcon(state: RowState) {
    val c = Sct.colors
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) {
        when (state) {
            RowState.PENDING -> Box(
                Modifier
                    .size(10.dp)
                    .background(c.surface2, CircleShape)
                    .border(1.dp, c.hairline, CircleShape)
            )
            RowState.ACTIVE -> CircularProgressIndicator(Modifier.size(22.dp), color = c.accent, strokeWidth = 2.5.dp)
            RowState.DONE -> FilledCircleIcon(R.drawable.ic_check, c.success)
            RowState.FAILED -> FilledCircleIcon(R.drawable.ic_close, c.danger)
        }
    }
}

@Composable
private fun FilledCircleIcon(@DrawableRes icon: Int, color: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(28.dp)
            .background(color.copy(alpha = 0.15f), CircleShape),
    ) {
        Icon(painterResource(icon), null, tint = color, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun StepTitle(text: String) {
    Text(text, color = Sct.colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black, lineHeight = 28.sp)
}
