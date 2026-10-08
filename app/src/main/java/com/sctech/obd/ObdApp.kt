package com.sctech.obd

import android.app.Application
import android.content.Context
import com.sctech.obd.data.AppPrefs
import com.sctech.obd.data.DtcRepository

class ObdApp : Application() {
    val prefs by lazy { AppPrefs(this) }
    val dtcRepository by lazy { DtcRepository.fromAssets(this) }
}

val Context.obdApp: ObdApp get() = applicationContext as ObdApp
