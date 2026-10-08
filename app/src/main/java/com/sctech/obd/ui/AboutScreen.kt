package com.sctech.obd.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sctech.obd.BuildConfig
import com.sctech.obd.R
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sctech.obd.ObdApp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sctech.obd.ui.theme.Sct

/** Debug builds only: lets us see the app as a user without Pro. */
@Composable
private fun DeveloperPanel(app: ObdApp) {
    val c = Sct.colors
    var locked by remember { mutableStateOf(app.proAccess.debugLocked) }
    Panel(borderColor = c.warning.copy(alpha = 0.4f)) {
        Eyebrow(stringResource(R.string.about_developer), color = c.warning)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.about_dev_lock_pro), color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.about_dev_lock_pro_sub), color = c.textSecondary, fontSize = 12.sp)
            }
            Switch(
                checked = locked,
                onCheckedChange = {
                    locked = it
                    app.proAccess.debugLocked = it
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = c.onAccent,
                    checkedTrackColor = c.accent,
                    uncheckedThumbColor = c.textSecondary,
                    uncheckedTrackColor = c.surface2,
                    uncheckedBorderColor = c.hairline,
                ),
            )
        }
    }
}

private const val ANDROBD_URL = "https://github.com/fr3ts0n/AndrOBD"
private const val GPL_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

@Composable
fun AboutScreen(app: ObdApp, onBack: () -> Unit) {
    val repository = app.dtcRepository
    val c = Sct.colors
    val context = LocalContext.current
    val open: (String) -> Unit = { url ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            // no browser installed: nothing sensible to do
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back), tint = c.textPrimary)
            }
            Text(stringResource(R.string.about_title), color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }

        Panel {
            Image(
                painterResource(R.drawable.sctech_wordmark),
                contentDescription = "SCTech",
                colorFilter = ColorFilter.tint(c.textPrimary),
                modifier = Modifier.height(26.dp),
            )
            Spacer(Modifier.height(6.dp))
            Eyebrow(stringResource(R.string.about_product))
            Spacer(Modifier.height(10.dp))
            InfoRow(stringResource(R.string.about_version), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            InfoRow(stringResource(R.string.about_dtc_db), stringResource(R.string.about_dtc_db_count, repository.size))
            InfoRow(stringResource(R.string.about_adapters), "ELM327 · Bluetooth")
            val licensedKey by app.proAccess.licensedKey.collectAsStateWithLifecycle()
            InfoRow(
                stringResource(R.string.about_license),
                licensedKey ?: stringResource(R.string.about_license_none),
                valueColor = if (licensedKey != null) c.success else c.textSecondary,
            )
            InfoRow(stringResource(R.string.about_device_code), app.proAccess.deviceCode())
        }

        Panel {
            Eyebrow(stringResource(R.string.about_open_source))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.about_open_source_body), color = c.textSecondary, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(14.dp))
            // TODO: add a button to this app's own public source repository once it is published (GPL)
            SecondaryButton(
                stringResource(R.string.about_androbd),
                R.drawable.ic_open,
                modifier = Modifier.fillMaxWidth(),
                onClick = { open(ANDROBD_URL) },
            )
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                stringResource(R.string.about_gpl),
                R.drawable.ic_open,
                modifier = Modifier.fillMaxWidth(),
                onClick = { open(GPL_URL) },
            )
        }

        Panel {
            Eyebrow(stringResource(R.string.about_database))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.about_database_body), color = c.textSecondary, fontSize = 13.sp, lineHeight = 19.sp)
        }

        if (BuildConfig.DEBUG) DeveloperPanel(app)

        Disclaimer(Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.width(1.dp).height(8.dp))
    }
}
