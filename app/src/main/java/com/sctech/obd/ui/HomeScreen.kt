package com.sctech.obd.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sctech.obd.R
import com.sctech.obd.core.ConnectionState
import com.sctech.obd.core.EcuState
import com.sctech.obd.core.FailureReason
import com.sctech.obd.core.LiveValue
import com.sctech.obd.core.ObdSession
import com.sctech.obd.core.Pids
import com.sctech.obd.data.AppPrefs
import com.sctech.obd.ui.theme.Sct

/** Dashboard dial: label, Turkish unit and scale with warning zones. */
private data class Gauge(val pid: Int, @StringRes val label: Int, @StringRes val unit: Int, val scale: GaugeScale)

private val GAUGES = listOf(
    Gauge(
        Pids.ENGINE_RPM, R.string.gauge_rpm, R.string.unit_rpm,
        GaugeScale(0f, 8000f, 0, listOf(GaugeZone(6500f, 8000f)), minLabel = "0", maxLabel = "8K"),
    ),
    Gauge(Pids.VEHICLE_SPEED, R.string.gauge_speed, R.string.unit_speed, GaugeScale(0f, 240f, 0)),
    Gauge(
        Pids.COOLANT_TEMP, R.string.gauge_coolant, R.string.unit_celsius,
        GaugeScale(40f, 130f, 0, listOf(GaugeZone(105f, 112f, danger = false), GaugeZone(112f, 130f))),
    ),
    // TODO: fall back to ELM "AT RV" when the ECU does not support PID 0x42
    Gauge(
        Pids.MODULE_VOLTAGE, R.string.gauge_voltage, R.string.unit_volt,
        GaugeScale(
            10f, 16f, 1,
            listOf(GaugeZone(10f, 11.8f), GaugeZone(11.8f, 12.4f, danger = false), GaugeZone(15f, 16f)),
        ),
    ),
)

@Composable
fun HomeScreen(prefs: AppPrefs, onOpenTroubleCodes: () -> Unit) {
    val connection by ObdSession.connection.collectAsStateWithLifecycle()
    val ecu by ObdSession.ecuState.collectAsStateWithLifecycle()
    val live by ObdSession.liveData.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }

    // Dashboard is visible -> poll the MVP values (applied once the ECU is ready)
    LaunchedEffect(Unit) { ObdSession.startLiveData(Pids.DASHBOARD) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ConnectionPanel(
            connection = connection,
            ecu = ecu,
            onConnect = { showPicker = true },
            onDemo = { ObdSession.startDemo() },
            onDisconnect = { ObdSession.disconnect() },
        )

        if (connection is ConnectionState.Connected && ecu == EcuState.READY) {
            MilPanel(live, onOpenTroubleCodes)
        }

        Eyebrow(stringResource(R.string.live_data_title), Modifier.padding(top = 8.dp, start = 4.dp))
        GAUGES.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { gauge ->
                    GaugeTile(gauge, live.firstOrNull { it.pid == gauge.pid }, Modifier.weight(1f))
                }
            }
        }
    }

    if (showPicker) {
        DevicePickerDialog(prefs = prefs, onDismiss = { showPicker = false })
    }
}

@Composable
private fun ConnectionPanel(
    connection: ConnectionState,
    ecu: EcuState,
    onConnect: () -> Unit,
    onDemo: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val c = Sct.colors
    val connected = connection as? ConnectionState.Connected
    val ready = connected != null && ecu == EcuState.READY

    val (title, titleColor, subtitle) = when (connection) {
        ConnectionState.Disconnected ->
            Triple(stringResource(R.string.status_disconnected), c.textPrimary, stringResource(R.string.status_disconnected_sub))
        is ConnectionState.Connecting ->
            Triple(stringResource(R.string.status_connecting), c.warning, connection.deviceName)
        is ConnectionState.Failed -> Triple(
            stringResource(
                if (connection.reason == FailureReason.CONNECT_FAILED) R.string.status_connect_failed
                else R.string.status_connection_lost
            ),
            c.danger,
            stringResource(R.string.status_failed_sub),
        )
        is ConnectionState.Connected -> Triple(
            stringResource(if (ready) R.string.status_ready else R.string.status_connected),
            if (ready) c.success else c.textPrimary,
            if (connection.demo) stringResource(R.string.status_demo) else connection.deviceName,
        )
    }

    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            CarVisual(
                ringColor = when {
                    ready -> c.success
                    connection is ConnectionState.Failed -> c.danger
                    connection is ConnectionState.Connecting || connected != null -> c.warning
                    else -> c.hairline
                },
                pulsing = connection is ConnectionState.Connecting || (connected != null && !ready),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Eyebrow(stringResource(R.string.eyebrow_vehicle_link))
                BigValue(title, titleColor)
                Text(subtitle, color = c.textSecondary, fontSize = 12.sp, maxLines = 2)
            }
        }

        Spacer(Modifier.height(14.dp))

        when (connection) {
            ConnectionState.Disconnected, is ConnectionState.Failed -> {
                PrimaryButton(stringResource(R.string.action_connect), R.drawable.ic_link, onClick = onConnect)
                Spacer(Modifier.height(10.dp))
                SecondaryButton(
                    stringResource(R.string.action_demo),
                    R.drawable.ic_play,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDemo,
                )
                Spacer(Modifier.height(12.dp))
                Hint(
                    stringResource(
                        if (connection is ConnectionState.Failed) R.string.status_failed_hint
                        else R.string.status_disconnected_hint
                    )
                )
            }

            is ConnectionState.Connecting -> {
                ProgressLine(stringResource(R.string.progress_connecting))
                Spacer(Modifier.height(12.dp))
                SecondaryButton(
                    stringResource(R.string.action_cancel),
                    R.drawable.ic_close,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDisconnect,
                )
            }

            is ConnectionState.Connected -> {
                if (!ready) {
                    when (ecu) {
                        EcuState.NO_RESPONSE, EcuState.ERROR -> Hint(stringResource(ecu.description()))
                        else -> ProgressLine(stringResource(ecu.description()))
                    }
                    Spacer(Modifier.height(12.dp))
                }
                SecondaryButton(
                    stringResource(R.string.action_disconnect),
                    R.drawable.ic_close,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDisconnect,
                )
            }
        }
    }
}

/** Car glyph in a status ring, standing in for the drone app's aircraft visual. */
@Composable
private fun CarVisual(ringColor: Color, pulsing: Boolean) {
    val pulse = rememberInfiniteTransition(label = "car")
    val alpha by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "carRing")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(84.dp)
            .background(Sct.colors.surface2, CircleShape)
            .border(2.dp, ringColor.copy(alpha = ringColor.alpha * (if (pulsing) alpha else 1f)), CircleShape),
    ) {
        Icon(
            painterResource(R.drawable.ic_car),
            contentDescription = null,
            tint = Sct.colors.textPrimary,
            modifier = Modifier.size(40.dp),
        )
    }
}

@StringRes
private fun EcuState.description(): Int = when (this) {
    EcuState.IDLE -> R.string.ecu_idle
    EcuState.INITIALIZING -> R.string.ecu_initializing
    EcuState.SEARCHING -> R.string.ecu_searching
    EcuState.READY -> R.string.ecu_ready
    EcuState.NO_RESPONSE -> R.string.ecu_no_response
    EcuState.ERROR -> R.string.ecu_error
}

/** Check-engine light + stored code count, from PID 01. */
@Composable
private fun MilPanel(live: List<LiveValue>, onOpen: () -> Unit) {
    val c = Sct.colors
    val milOn = live.firstOrNull { it.mnemonic == Pids.MNEMONIC_MIL }?.value?.let { it != 0.0 }
    val count = live.firstOrNull { it.mnemonic == Pids.MNEMONIC_CODE_COUNT }?.value?.toInt()
    val color = if (milOn == true) c.danger else c.success

    Panel(borderColor = if (milOn == true) c.danger.copy(0.45f) else c.hairline, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(if (milOn == false) R.drawable.ic_check else R.drawable.ic_warning),
                contentDescription = null,
                tint = if (milOn == null) c.textSecondary else color,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        when (milOn) {
                            true -> R.string.mil_on
                            false -> R.string.mil_off
                            null -> R.string.mil_unknown
                        }
                    ),
                    color = c.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (count != null && count > 0) {
                        stringResource(R.string.mil_codes_reported, count)
                    } else {
                        stringResource(R.string.mil_check_codes)
                    },
                    color = c.textSecondary,
                    fontSize = 12.sp,
                )
            }
            Icon(painterResource(R.drawable.ic_chevron_right), null, tint = c.textTertiary)
        }
    }
}

@Composable
private fun GaugeTile(gauge: Gauge, value: LiveValue?, modifier: Modifier = Modifier) {
    Panel(modifier) {
        Eyebrow(stringResource(gauge.label))
        Spacer(Modifier.height(8.dp))
        ArcGauge(value?.value, stringResource(gauge.unit), gauge.scale)
    }
}
