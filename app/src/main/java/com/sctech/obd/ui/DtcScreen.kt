package com.sctech.obd.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.draw.clip
import com.sctech.obd.data.DtcCategory
import com.sctech.obd.expertiz.ExpertizAnalyzer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sctech.obd.R
import com.sctech.obd.core.DtcKind
import com.sctech.obd.core.EcuState
import com.sctech.obd.core.ObdSession
import com.sctech.obd.core.TroubleCode
import com.sctech.obd.data.DtcInfo
import com.sctech.obd.data.DtcRepository
import com.sctech.obd.data.Severity
import com.sctech.obd.ui.theme.Sct
import com.sctech.obd.ui.theme.color

@Composable
fun DtcScreen(repository: DtcRepository) {
    val ecu by ObdSession.ecuState.collectAsStateWithLifecycle()
    val state by ObdSession.troubleCodes.collectAsStateWithLifecycle()
    val ready = ecu == EcuState.READY
    val finished = state.lastReadAt != null && !state.reading
    var confirmClear by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!ready) item { NotReadyHint() }

        item {
            Panel {
                Eyebrow(stringResource(R.string.eyebrow_trouble_codes))
                when {
                    state.reading -> BigValue("…", Sct.colors.textSecondary)
                    finished && state.codes.isEmpty() -> BigValue(stringResource(R.string.dtc_none_big), Sct.colors.success)
                    finished -> BigValue(state.codes.size.toString(), Sct.colors.warning)
                    else -> BigValue("—", Sct.colors.textTertiary)
                }
                Text(
                    stringResource(
                        when {
                            state.reading -> R.string.dtc_reading
                            finished && state.codes.isEmpty() -> R.string.dtc_none_body
                            finished -> R.string.dtc_found_sub
                            else -> R.string.dtc_not_read
                        }
                    ),
                    color = Sct.colors.textSecondary,
                    fontSize = 12.sp,
                )
                if (finished && state.codes.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    KindBreakdown(state.codes)
                }
                Spacer(Modifier.height(14.dp))
                if (state.reading) {
                    ProgressLine(stringResource(R.string.dtc_reading_progress))
                } else {
                    PrimaryButton(
                        stringResource(if (finished) R.string.dtc_read_again else R.string.dtc_read),
                        R.drawable.ic_search,
                        enabled = ready,
                        onClick = { ObdSession.readTroubleCodes() },
                    )
                }
            }
        }

        val clearedCodes = ExpertizAnalyzer.clearedButUnresolved(state.codes).map { it.code }.toSet()
        items(state.codes, key = { it.code + it.kind }) { code ->
            DtcCard(code, repository.find(code.code), clearedButUnresolved = code.kind == DtcKind.PERMANENT && code.code in clearedCodes)
        }

        if (finished && state.codes.isNotEmpty()) {
            item {
                SecondaryButton(
                    stringResource(R.string.dtc_clear),
                    R.drawable.ic_delete,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth(),
                    enabled = ready,
                    contentColor = Sct.colors.danger,
                    onClick = { confirmClear = true },
                )
            }
        }

        item { Disclaimer(Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)) }
    }

    if (confirmClear) {
        ClearCodesDialog(
            onConfirm = {
                confirmClear = false
                ObdSession.clearTroubleCodes()
            },
            onDismiss = { confirmClear = false },
        )
    }
}

/** Stored / pending / permanent counts side by side, split by hairlines. */
@Composable
private fun KindBreakdown(codes: List<TroubleCode>) {
    val c = Sct.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, c.hairline, RoundedCornerShape(12.dp))
            .height(IntrinsicSize.Min),
    ) {
        DtcKind.entries.forEachIndexed { index, kind ->
            if (index > 0) VerticalDivider(thickness = 1.dp, color = c.hairline)
            val count = codes.count { it.kind == kind }
            Column(
                Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp, horizontal = 12.dp),
            ) {
                Text(
                    count.toString(),
                    color = if (count > 0) kind.color() else c.textTertiary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    style = TabularNumbers,
                )
                Text(stringResource(kind.label()), color = c.textTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun DtcCard(code: TroubleCode, info: DtcInfo?, clearedButUnresolved: Boolean) {
    val c = Sct.colors
    var expanded by rememberSaveable(code.code, code.kind) { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    Panel(
        modifier = Modifier.animateContentSize(),
        borderColor = if (info?.severity == Severity.HIGH) c.danger.copy(0.4f) else c.hairline,
        onClick = if (info != null) ({ expanded = !expanded }) else null,
    ) {
        Eyebrow(DtcCategory.of(code.code))
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                code.code,
                color = c.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            Chip(stringResource(code.kind.label()), code.kind.color())
        }
        Spacer(Modifier.height(6.dp))
        Text(
            info?.title ?: code.libraryDescription,
            color = c.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 20.sp,
        )
        if (clearedButUnresolved) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.dtc_cleared_unresolved), color = c.danger, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
        }

        if (info == null) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.dtc_no_details), color = c.textTertiary, fontSize = 12.sp)
            return@Panel
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_warning), null, tint = info.severity.color(), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(info.severity.label()) + " · " +
                    stringResource(if (info.drivable) R.string.drivable_yes else R.string.drivable_no),
                color = info.severity.color(),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painterResource(R.drawable.ic_expand),
                contentDescription = null,
                tint = c.textTertiary,
                modifier = Modifier.rotate(chevron),
            )
        }

        if (expanded) {
            Spacer(Modifier.height(12.dp))
            Text(info.meaning, color = c.textPrimary, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(14.dp))
            Eyebrow(stringResource(R.string.dtc_causes))
            Spacer(Modifier.height(6.dp))
            BulletList(info.causes)
            Spacer(Modifier.height(14.dp))
            Eyebrow(stringResource(R.string.dtc_actions))
            Spacer(Modifier.height(6.dp))
            BulletList(info.actions, color = c.textPrimary)
            Spacer(Modifier.height(14.dp))
            Text(stringResource(code.kind.explanation()), color = c.textTertiary, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

private fun Severity.label() = when (this) {
    Severity.LOW -> R.string.severity_low
    Severity.MEDIUM -> R.string.severity_medium
    Severity.HIGH -> R.string.severity_high
}

private fun DtcKind.label() = when (this) {
    DtcKind.STORED -> R.string.dtc_kind_stored
    DtcKind.PENDING -> R.string.dtc_kind_pending
    DtcKind.PERMANENT -> R.string.dtc_kind_permanent
}

@Composable
private fun DtcKind.color(): Color = when (this) {
    DtcKind.STORED -> Sct.colors.warning
    DtcKind.PENDING -> Sct.colors.textSecondary
    DtcKind.PERMANENT -> Sct.colors.danger
}

private fun DtcKind.explanation() = when (this) {
    DtcKind.STORED -> R.string.dtc_kind_stored_info
    DtcKind.PENDING -> R.string.dtc_kind_pending_info
    DtcKind.PERMANENT -> R.string.dtc_kind_permanent_info
}

/** Two-step confirmation: the user must tick "I understand" before clearing. */
@Composable
private fun ClearCodesDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = Sct.colors
    var understood by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface1,
        icon = { Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = c.warning) },
        title = { Text(stringResource(R.string.clear_title), color = c.textPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BulletList(
                    listOf(
                        stringResource(R.string.clear_warn_comes_back),
                        stringResource(R.string.clear_warn_readiness),
                        stringResource(R.string.clear_warn_permanent),
                        stringResource(R.string.clear_warn_note_codes),
                        stringResource(R.string.clear_warn_engine_off),
                    )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = understood,
                        onCheckedChange = { understood = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = c.accent,
                            checkmarkColor = c.onAccent,
                            uncheckedColor = c.textTertiary,
                        ),
                    )
                    Text(stringResource(R.string.clear_understood), color = c.textPrimary, fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = understood,
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.danger,
                    contentColor = Color.White,
                    disabledContainerColor = c.danger.copy(0.18f),
                    disabledContentColor = c.textTertiary,
                ),
            ) {
                Text(stringResource(R.string.clear_confirm), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) }
        },
    )
}
