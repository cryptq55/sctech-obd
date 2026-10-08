package com.sctech.obd.data

import android.content.Context
import androidx.core.content.edit
import com.sctech.obd.BuildConfig
import com.sctech.obd.license.SctLicense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Whether Pro features (expertiz, later PDF report, dashboards…) are unlocked:
 * an SCTech OBD Pro licence activated on this phone (see [SctLicense]).
 *
 * Debug builds are unlocked by default so features can be developed; the developer switch
 * on the About page re-locks them to test the licence screen.
 */
class ProAccess(private val context: Context) {

    private val prefs = context.getSharedPreferences("pro", Context.MODE_PRIVATE)

    private val _licensedKey = MutableStateFlow(SctLicense.licensedKey(context))
    /** The activated SCT key, if any. */
    val licensedKey: StateFlow<String?> = _licensedKey.asStateFlow()

    private val _isPro = MutableStateFlow(compute())
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    /** Debug builds only: simulate a user without Pro. */
    var debugLocked: Boolean
        get() = prefs.getBoolean(KEY_DEBUG_LOCKED, false)
        set(value) {
            prefs.edit { putBoolean(KEY_DEBUG_LOCKED, value) }
            _isPro.value = compute()
        }

    /** Activates a key online (one time). Safe to call from the main thread. */
    suspend fun activate(key: String): SctLicense.Result {
        val result = withContext(Dispatchers.IO) { SctLicense.activate(context, key) }
        _licensedKey.value = SctLicense.licensedKey(context)
        _isPro.value = compute()
        return result
    }

    fun deviceCode(): String = SctLicense.deviceCode(context)

    private fun compute(): Boolean =
        _licensedKey.value != null || (BuildConfig.DEBUG && !prefs.getBoolean(KEY_DEBUG_LOCKED, false))

    private companion object {
        const val KEY_DEBUG_LOCKED = "debug_locked"
    }
}
