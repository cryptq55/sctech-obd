package com.sctech.obd.data

import android.content.Context
import androidx.core.content.edit
import com.sctech.obd.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether Pro features (expertiz, later PDF report, dashboards…) are unlocked.
 *
 * TODO: wire the real one-time purchase (Play Billing or SCTech licence) into [setPurchased].
 * Debug builds are unlocked by default so features can be developed; the developer switch on
 * the About page can re-lock them to test the upsell.
 */
class ProAccess(context: Context) {

    private val prefs = context.getSharedPreferences("pro", Context.MODE_PRIVATE)

    private val _isPro = MutableStateFlow(compute())
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    /** Debug builds only: simulate a user without Pro. */
    var debugLocked: Boolean
        get() = prefs.getBoolean(KEY_DEBUG_LOCKED, false)
        set(value) {
            prefs.edit { putBoolean(KEY_DEBUG_LOCKED, value) }
            _isPro.value = compute()
        }

    fun setPurchased(purchased: Boolean) {
        prefs.edit { putBoolean(KEY_PURCHASED, purchased) }
        _isPro.value = compute()
    }

    private fun compute(): Boolean =
        prefs.getBoolean(KEY_PURCHASED, false) || (BuildConfig.DEBUG && !prefs.getBoolean(KEY_DEBUG_LOCKED, false))

    private companion object {
        const val KEY_PURCHASED = "purchased"
        const val KEY_DEBUG_LOCKED = "debug_locked"
    }
}
