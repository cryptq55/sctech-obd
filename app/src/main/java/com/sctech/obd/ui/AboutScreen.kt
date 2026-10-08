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
import com.sctech.obd.data.DtcRepository
import com.sctech.obd.ui.theme.Sct

private const val ANDROBD_URL = "https://github.com/fr3ts0n/AndrOBD"
private const val GPL_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

@Composable
fun AboutScreen(repository: DtcRepository, onBack: () -> Unit) {
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

        Disclaimer(Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.width(1.dp).height(8.dp))
    }
}
