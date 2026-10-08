package com.sctech.obd.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sctech.obd.R
import com.sctech.obd.core.ConnectionState
import com.sctech.obd.core.ObdSession
import com.sctech.obd.ui.theme.Sct

// Building blocks mirroring the SCTech drone app (FreeFCC-main MainActivity.kt)

private val PanelShape = RoundedCornerShape(18.dp)
private val ButtonShape = RoundedCornerShape(14.dp)

/** Fixed-width digits so live values don't jitter as they change. */
val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    borderColor: Color = Sct.colors.hairline,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(Sct.colors.surface1)
            .border(1.dp, borderColor, PanelShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        content = content,
    )
}

/** Small spaced-out caps label ("SİNYAL MODU" style). */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Sct.colors.textTertiary) {
    Text(
        text.uppercase(java.util.Locale.forLanguageTag("tr")),
        modifier = modifier,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun BigValue(text: String, color: Color = Sct.colors.textPrimary) {
    Text(text, color = color, fontSize = 34.sp, fontWeight = FontWeight.Black, maxLines = 1, style = TabularNumbers)
}

@Composable
fun StatusPill(label: String, color: Color, pulsing: Boolean = false) {
    val pulse = rememberInfiniteTransition(label = "pill")
    val alpha by pulse.animateFloat(0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pillAlpha")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .background(color.copy(if (pulsing) alpha else 1f), CircleShape)
        )
        Spacer(Modifier.width(7.dp))
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun Chip(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.sp,
        modifier = Modifier
            .clip(CircleShape)
            .border(1.dp, color.copy(0.4f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun PrimaryButton(
    text: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Sct.colors.accent,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            onClick()
        },
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = if (color == Sct.colors.accent) Sct.colors.onAccent else Color.White,
            disabledContainerColor = color.copy(0.18f),
            disabledContentColor = Sct.colors.textTertiary,
        ),
        shape = ButtonShape,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Icon(painterResource(icon), null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    @DrawableRes icon: Int? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = Sct.colors.textPrimary,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            onClick()
        },
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Sct.colors.surface2,
            contentColor = contentColor,
            disabledContainerColor = Sct.colors.surface2.copy(0.5f),
            disabledContentColor = Sct.colors.textTertiary,
        ),
        shape = ButtonShape,
        border = BorderStroke(1.dp, Sct.colors.hairline),
        contentPadding = PaddingValues(horizontal = 14.dp),
        modifier = modifier.height(48.dp),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Indeterminate progress with a label, in the drone app's progress style. */
@Composable
fun ProgressLine(label: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = Sct.colors.textPrimary, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = Sct.colors.accent,
            trackColor = Sct.colors.surface2,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
        )
    }
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color = Sct.colors.textPrimary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Sct.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun BulletList(items: List<String>, color: Color = Sct.colors.textSecondary) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Row {
                Text("•", color = color, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text(item, color = color, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
fun Hint(text: String) {
    Text(text, color = Sct.colors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
}

/** Shown on screens that need a ready ECU while there is none. */
@Composable
fun NotReadyHint() {
    val connection by ObdSession.connection.collectAsStateWithLifecycle()
    val text = if (connection is ConnectionState.Connected) R.string.hint_waiting_for_ecu else R.string.hint_connect_first
    Panel {
        Text(stringResource(text), color = Sct.colors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
fun Disclaimer(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.disclaimer),
        modifier = modifier,
        color = Sct.colors.textTertiary,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    )
}
