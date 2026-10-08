package com.sctech.obd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sctech.obd.ObdApp
import com.sctech.obd.R
import com.sctech.obd.core.EcuState
import com.sctech.obd.core.ObdSession
import com.sctech.obd.expertiz.CheckSection
import com.sctech.obd.expertiz.CheckStatus
import com.sctech.obd.expertiz.ExpertizController
import com.sctech.obd.expertiz.ExpertizReport
import com.sctech.obd.expertiz.Finding
import com.sctech.obd.expertiz.SectionId
import com.sctech.obd.license.SctLicense
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.sctech.obd.ui.theme.Sct
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExpertizScreen(app: ObdApp) {
    val isPro by app.proAccess.isPro.collectAsStateWithLifecycle()
    val state by ExpertizController.state.collectAsStateWithLifecycle()
    val ecu by ObdSession.ecuState.collectAsStateWithLifecycle()
    val ready = ecu == EcuState.READY
    val start = { ExpertizController.start(app.dtcRepository) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!isPro) {
            item { ProUpsell(app) }
            return@LazyColumn
        }
        when (val s = state) {
            ExpertizController.State.Idle -> {
                if (!ready) item { NotReadyHint() }
                item { ExpertizIntro(ready, start) }
            }
            is ExpertizController.State.Running -> item { ExpertizProgress(s.step) }
            is ExpertizController.State.Failed -> {
                item { ExpertizFailed(s.reason, ready, start) }
            }
            is ExpertizController.State.Done -> {
                val report = s.report
                if (report.demo) item { DemoNotice() }
                item { VerdictPanel(report) }
                items(report.sections, key = { it.id }) { SectionPanel(it, report.vin) }
                item {
                    PrimaryButton(
                        stringResource(R.string.exp_run_again),
                        R.drawable.ic_search,
                        enabled = ready,
                        onClick = start,
                    )
                }
                item {
                    Text(
                        stringResource(R.string.exp_disclaimer),
                        color = Sct.colors.textTertiary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

// ───────────────────────────────────────────────────────── upsell / intro / progress

@Composable
private fun ProUpsell(app: ObdApp) {
    val c = Sct.colors
    Panel(borderColor = c.accent.copy(alpha = 0.35f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(stringResource(R.string.exp_eyebrow), Modifier.weight(1f))
            Chip("PRO", c.accent)
        }
        Spacer(Modifier.height(6.dp))
        StepTitle(stringResource(R.string.exp_upsell_title))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.exp_upsell_body), color = c.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(14.dp))
        CheckList(
            listOf(
                stringResource(R.string.exp_check_cleared),
                stringResource(R.string.exp_check_codes),
                stringResource(R.string.exp_check_readiness),
                stringResource(R.string.exp_check_vin),
                stringResource(R.string.exp_check_odometer),
            )
        )
        Spacer(Modifier.height(18.dp))
        HorizontalDivider(thickness = 1.dp, color = c.hairline)
        Spacer(Modifier.height(16.dp))
        LicenseActivation(app)
    }
}

/** Shows "2er3m2adzqmm" as "2ER3-M2AD-ZQMM" (the "SCT-" part is the field prefix). */
private object LicenseKeyTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        // A pasted full key may still start with SCT; hide it behind the prefix
        val skip = if (raw.length > 12 && raw.startsWith("SCT", ignoreCase = true)) 3 else 0
        val body = raw.drop(skip).uppercase()
        val out = body.chunked(4).joinToString("-")
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val o = (offset - skip).coerceIn(0, body.length)
                return o + (if (o > 4) 1 else 0) + (if (o > 8) 1 else 0)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val t = offset - (if (offset > 4) 1 else 0) - (if (offset > 9) 1 else 0)
                return (t + skip).coerceIn(0, raw.length)
            }
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}

/** SCT-XXXX-XXXX-XXXX entry, same flow as the SCTech FCC app. */
@Composable
private fun LicenseActivation(app: ObdApp) {
    val c = Sct.colors
    val scope = rememberCoroutineScope()
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val key = SctLicense.normalizeKey(field.text)

    Eyebrow(stringResource(R.string.lic_eyebrow))
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = field,
        onValueChange = {
            // Only drop characters that can't be part of a key; rewriting the text while the
            // keyboard is composing loses input. Dashes and capitals are display-only.
            val chars = it.text.filter { ch -> ch.isLetterOrDigit() }
            val clean = chars.take(if (chars.startsWith("SCT", ignoreCase = true)) 15 else 12)
            field = if (clean == it.text) it else TextFieldValue(clean, TextRange(clean.length))
            error = null
        },
        visualTransformation = LicenseKeyTransformation,
        prefix = { Text("SCT-", color = c.textSecondary, fontFamily = FontFamily.Monospace) },
        placeholder = { Text("XXXX-XXXX-XXXX", fontFamily = FontFamily.Monospace, color = c.textTertiary) },
        singleLine = true,
        enabled = !busy,
        isError = error != null,
        // No suggestions/autocorrect: keys are random characters
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
        ),
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 17.sp, letterSpacing = 1.sp, color = c.textPrimary),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accent,
            unfocusedBorderColor = c.hairline,
            errorBorderColor = c.danger,
            cursorColor = c.accent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    error?.let {
        Spacer(Modifier.height(6.dp))
        Text(stringResource(it), color = c.danger, fontSize = 12.sp, lineHeight = 17.sp)
    }
    Spacer(Modifier.height(12.dp))
    if (busy) {
        ProgressLine(stringResource(R.string.lic_activating))
    } else {
        PrimaryButton(
            stringResource(R.string.lic_activate),
            R.drawable.ic_check,
            enabled = key != null,
            onClick = {
                val k = key ?: return@PrimaryButton
                busy = true
                scope.launch {
                    val result = app.proAccess.activate(k)
                    busy = false
                    error = when (result) {
                        SctLicense.Result.Success -> null
                        SctLicense.Result.Invalid -> R.string.lic_err_invalid
                        SctLicense.Result.InUse -> R.string.lic_err_in_use
                        SctLicense.Result.Revoked -> R.string.lic_err_revoked
                        SctLicense.Result.NoInternet -> R.string.lic_err_no_internet
                        SctLicense.Result.ServerError -> R.string.lic_err_server
                    }
                }
            },
        )
    }
    Spacer(Modifier.height(10.dp))
    Text(
        stringResource(R.string.lic_note, app.proAccess.deviceCode()),
        color = c.textTertiary,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    )
}

@Composable
private fun ExpertizIntro(ready: Boolean, onStart: () -> Unit) {
    val c = Sct.colors
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(stringResource(R.string.exp_eyebrow), Modifier.weight(1f))
            Chip("PRO", c.accent)
        }
        Spacer(Modifier.height(6.dp))
        StepTitle(stringResource(R.string.exp_intro_title))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.exp_intro_body), color = c.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(14.dp))
        CheckList(
            listOf(
                stringResource(R.string.exp_check_cleared),
                stringResource(R.string.exp_check_codes),
                stringResource(R.string.exp_check_readiness),
                stringResource(R.string.exp_check_vin),
                stringResource(R.string.exp_check_odometer),
            )
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton(stringResource(R.string.exp_start), R.drawable.ic_search, enabled = ready, onClick = onStart)
        Spacer(Modifier.height(10.dp))
        Hint(stringResource(R.string.exp_intro_hint))
    }
}

@Composable
private fun CheckList(items: List<String>) {
    val c = Sct.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { text ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(painterResource(R.drawable.ic_check), null, tint = c.success, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(text, color = c.textPrimary, fontSize = 14.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun ExpertizProgress(step: ExpertizController.Step) {
    fun rowState(s: ExpertizController.Step) = when {
        s.ordinal < step.ordinal -> RowState.DONE
        s == step -> RowState.ACTIVE
        else -> RowState.PENDING
    }
    Panel {
        Eyebrow(stringResource(R.string.exp_eyebrow))
        Spacer(Modifier.height(4.dp))
        StepTitle(stringResource(R.string.exp_running_title))
        Spacer(Modifier.height(8.dp))
        ProgressRow(rowState(ExpertizController.Step.COUNTERS), R.string.exp_step_counters, R.string.exp_step_counters_sub)
        ProgressRow(rowState(ExpertizController.Step.CODES), R.string.exp_step_codes, R.string.exp_step_codes_sub)
        ProgressRow(rowState(ExpertizController.Step.IDENTITY), R.string.exp_step_identity, R.string.exp_step_identity_sub)
        ProgressRow(rowState(ExpertizController.Step.ANALYSIS), R.string.exp_step_analysis, R.string.exp_step_analysis_sub)
        Spacer(Modifier.height(8.dp))
        Hint(stringResource(R.string.exp_running_hint))
    }
}

@Composable
private fun ExpertizFailed(reason: ExpertizController.Reason, ready: Boolean, onRetry: () -> Unit) {
    val c = Sct.colors
    Panel(borderColor = c.danger.copy(alpha = 0.45f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StateIcon(RowState.FAILED)
            Spacer(Modifier.width(14.dp))
            Text(
                stringResource(
                    if (reason == ExpertizController.Reason.CONNECTION_LOST) R.string.status_connection_lost
                    else R.string.exp_not_ready
                ),
                color = c.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton(stringResource(R.string.action_retry), R.drawable.ic_search, enabled = ready, onClick = onRetry)
    }
}

@Composable
private fun DemoNotice() {
    Panel(borderColor = Sct.colors.warning.copy(alpha = 0.45f)) {
        Text(stringResource(R.string.exp_demo_notice), color = Sct.colors.warning, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

// ───────────────────────────────────────────────────────── report

private val DATE_FORMAT = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.forLanguageTag("tr-TR"))

@Composable
private fun VerdictPanel(report: ExpertizReport) {
    val c = Sct.colors
    val color = report.verdict.color()
    Panel(borderColor = color.copy(alpha = 0.5f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .background(color.copy(alpha = 0.14f), CircleShape),
            ) {
                Icon(
                    painterResource(if (report.verdict == CheckStatus.PASS) R.drawable.ic_check else R.drawable.ic_warning),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Eyebrow(stringResource(R.string.exp_result))
                Text(report.headline, color = color, fontSize = 24.sp, fontWeight = FontWeight.Black, lineHeight = 28.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(report.summary, color = c.textPrimary, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(14.dp))
        StatusBreakdown(report.sections)
        Spacer(Modifier.height(12.dp))
        Text(DATE_FORMAT.format(Date(report.createdAt)), color = c.textTertiary, fontSize = 12.sp, style = TabularNumbers)
    }
}

@Composable
private fun StatusBreakdown(sections: List<CheckSection>) {
    val c = Sct.colors
    val columns = listOf(
        CheckStatus.PASS to R.string.exp_status_pass,
        CheckStatus.WARN to R.string.exp_status_warn,
        CheckStatus.FAIL to R.string.exp_status_fail,
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, c.hairline, RoundedCornerShape(12.dp))
            .height(IntrinsicSize.Min),
    ) {
        columns.forEachIndexed { index, (status, label) ->
            if (index > 0) VerticalDivider(thickness = 1.dp, color = c.hairline)
            val count = sections.count { it.status == status }
            Column(Modifier.weight(1f).padding(vertical = 10.dp, horizontal = 12.dp)) {
                Text(
                    count.toString(),
                    color = if (count > 0) status.color() else c.textTertiary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    style = TabularNumbers,
                )
                Text(stringResource(label), color = c.textTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun SectionPanel(section: CheckSection, vin: String?) {
    val c = Sct.colors
    Panel(borderColor = if (section.status == CheckStatus.FAIL) c.danger.copy(alpha = 0.4f) else c.hairline) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(section.title, color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Chip(stringResource(section.status.label()), section.status.color())
        }
        Spacer(Modifier.height(8.dp))
        Text(section.summary, color = c.textSecondary, fontSize = 13.sp, lineHeight = 19.sp)
        if (section.findings.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            section.findings.forEachIndexed { index, finding ->
                if (index > 0) HorizontalDivider(thickness = 1.dp, color = c.hairline)
                FindingRow(finding)
            }
        }
        if (section.id == SectionId.IDENTITY && vin != null) {
            Spacer(Modifier.height(12.dp))
            VinMatcher(vin)
        }
    }
}

@Composable
private fun FindingRow(finding: Finding) {
    val c = Sct.colors
    val isCode = Regex("[PCBU][0-9A-F]{4}").matches(finding.label)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 9.dp)) {
        if (finding.status != null) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(finding.status.color(), CircleShape)
            )
            Spacer(Modifier.width(10.dp))
        }
        if (isCode) {
            // Trouble code: code on the left, its explanation as the value
            Text(
                finding.label,
                color = c.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(64.dp),
            )
            Text(finding.value, color = c.textSecondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
        } else {
            Text(finding.label, color = c.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(finding.value, color = c.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, style = TabularNumbers)
        }
    }
}

/** Compares the ECU VIN with the one on the registration document typed by the user. */
@Composable
private fun VinMatcher(ecuVin: String) {
    val c = Sct.colors
    var input by rememberSaveable { mutableStateOf("") }
    val normalized = input.uppercase().filter { it.isLetterOrDigit() }
    OutlinedTextField(
        value = input,
        onValueChange = { input = it.take(24) },
        label = { Text(stringResource(R.string.exp_vin_input)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = c.textPrimary),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accent,
            unfocusedBorderColor = c.hairline,
            focusedLabelColor = c.textSecondary,
            unfocusedLabelColor = c.textTertiary,
            cursorColor = c.accent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    if (normalized.length >= 17) {
        val match = normalized == ecuVin.uppercase().filter { it.isLetterOrDigit() }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(if (match) R.drawable.ic_check else R.drawable.ic_warning),
                contentDescription = null,
                tint = if (match) c.success else c.danger,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(if (match) R.string.exp_vin_match else R.string.exp_vin_mismatch),
                color = if (match) c.success else c.danger,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun CheckStatus.label() = when (this) {
    CheckStatus.PASS -> R.string.exp_status_pass
    CheckStatus.WARN -> R.string.exp_status_warn
    CheckStatus.FAIL -> R.string.exp_status_fail
    CheckStatus.INFO -> R.string.exp_status_info
    CheckStatus.NO_DATA -> R.string.exp_status_no_data
}

@Composable
private fun CheckStatus.color(): Color = when (this) {
    CheckStatus.PASS -> Sct.colors.success
    CheckStatus.WARN -> Sct.colors.warning
    CheckStatus.FAIL -> Sct.colors.danger
    CheckStatus.INFO -> Sct.colors.textSecondary
    CheckStatus.NO_DATA -> Sct.colors.textTertiary
}
