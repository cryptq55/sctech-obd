package com.sctech.obd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sctech.obd.R
import com.sctech.obd.core.EcuState
import com.sctech.obd.core.InfoItem
import com.sctech.obd.core.ObdSession
import com.sctech.obd.data.SensorNames
import com.sctech.obd.data.Vin
import com.sctech.obd.ui.theme.Sct
import java.text.Collator
import java.util.Locale

private const val SENSORS_COLLAPSED = 8

@Composable
fun VehicleScreen() {
    val c = Sct.colors
    val ecu by ObdSession.ecuState.collectAsStateWithLifecycle()
    val info by ObdSession.vehicleInfo.collectAsStateWithLifecycle()
    // Last Mode 01 snapshot: every item the ECU reported as supported
    val live by ObdSession.liveData.collectAsStateWithLifecycle()
    val ready = ecu == EcuState.READY
    var showAllSensors by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { ObdSession.readVehicleInfo() }

    val vin = info.firstOrNull { it.mnemonic == InfoItem.MNEMONIC_VIN }?.value?.takeIf { it.isNotBlank() }
    val details = info.filter {
        it.mnemonic != InfoItem.MNEMONIC_VIN && !it.isCounter && it.value.isNotBlank() && it.value != "n/a"
    }
    val collator = remember { Collator.getInstance(Locale.forLanguageTag("tr-TR")) }
    // Curated Turkish names first; AndrOBD's own (often untranslated) labels after them
    val sensors = live
        .map { SensorNames.turkish(it.mnemonic)?.let { name -> name to true } ?: (it.label to false) }
        .filter { it.first.isNotBlank() }
        .distinctBy { it.first }
        .sortedWith(compareByDescending<Pair<String, Boolean>> { it.second }.thenBy(collator) { it.first })
        .map { it.first }
    val shownSensors = if (showAllSensors) sensors else sensors.take(SENSORS_COLLAPSED)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!ready) item { NotReadyHint() }

        item {
            Panel {
                Eyebrow(stringResource(R.string.vehicle_vin))
                Spacer(Modifier.height(4.dp))
                val decoded = vin?.let { Vin(it.uppercase()) }
                SelectionContainer {
                    Text(
                        decoded?.grouped ?: "– – –",
                        color = if (vin != null) c.textPrimary else c.textTertiary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                    )
                }
                if (decoded?.manufacturer != null || decoded?.region != null) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(thickness = 1.dp, color = c.hairline)
                    Spacer(Modifier.height(4.dp))
                    decoded.manufacturer?.let { InfoRow(stringResource(R.string.vehicle_make), it) }
                    decoded.region?.let { InfoRow(stringResource(R.string.vehicle_region), it) }
                }
                if (ready && vin == null) {
                    Spacer(Modifier.height(12.dp))
                    ProgressLine(stringResource(R.string.vehicle_reading))
                }
            }
        }

        if (details.isNotEmpty()) {
            item {
                Panel {
                    Eyebrow(stringResource(R.string.vehicle_details))
                    Spacer(Modifier.height(6.dp))
                    details.forEach { item ->
                        InfoRow(SensorNames.displayName(item.mnemonic, item.label), item.value)
                    }
                }
            }
        }

        item {
            Panel {
                Eyebrow(stringResource(R.string.vehicle_sensors, sensors.size))
                Spacer(Modifier.height(6.dp))
                if (sensors.isEmpty()) {
                    Hint(stringResource(R.string.vehicle_sensors_hint))
                } else {
                    shownSensors.forEachIndexed { index, name ->
                        if (index > 0) HorizontalDivider(thickness = 1.dp, color = c.hairline)
                        Text(
                            name,
                            color = c.textPrimary,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                        )
                    }
                    if (sensors.size > SENSORS_COLLAPSED) {
                        Spacer(Modifier.height(10.dp))
                        SecondaryButton(
                            stringResource(
                                if (showAllSensors) R.string.vehicle_sensors_less else R.string.vehicle_sensors_all
                            ),
                            R.drawable.ic_expand,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { showAllSensors = !showAllSensors },
                        )
                    }
                }
            }
        }
    }
}
