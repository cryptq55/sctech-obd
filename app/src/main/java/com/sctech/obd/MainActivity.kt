package com.sctech.obd

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sctech.obd.ui.ObdAppRoot
import com.sctech.obd.ui.theme.ObdTheme
import com.sctech.obd.ui.theme.isDark

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = obdApp

        setContent {
            val themeMode by app.prefs.themeMode.collectAsStateWithLifecycle()
            val dark = themeMode.isDark()

            // Keep status/navigation bar icons readable when the in-app theme overrides the system one
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }

            ObdTheme(dark) {
                ObdAppRoot(app)
            }
        }
    }

    private companion object {
        // Same defaults as androidx.activity's enableEdgeToEdge()
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
