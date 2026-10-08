package com.sctech.obd.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class Severity { LOW, MEDIUM, HIGH }

/** Plain-language Turkish explanation for one trouble code. */
data class DtcInfo(
    val code: String,
    val title: String,
    val meaning: String,
    val causes: List<String>,
    val actions: List<String>,
    val severity: Severity,
    val drivable: Boolean,
)

/**
 * Turkish DTC explanations bundled as assets/dtc_tr.json.
 * Small enough to keep in memory; moves to Room once the Pro features need queries.
 */
class DtcRepository(private val entries: Map<String, DtcInfo>) {

    val size: Int get() = entries.size

    fun find(code: String): DtcInfo? = entries[code.uppercase()]

    companion object {
        private const val ASSET = "dtc_tr.json"

        fun fromAssets(context: Context): DtcRepository =
            context.assets.open(ASSET).bufferedReader().use { parse(it.readText()) }

        fun parse(json: String): DtcRepository {
            val codes = JSONObject(json).getJSONArray("codes")
            val entries = (0 until codes.length()).associate { i ->
                val o = codes.getJSONObject(i)
                val info = DtcInfo(
                    code = o.getString("code").uppercase(),
                    title = o.getString("title"),
                    meaning = o.getString("meaning"),
                    causes = o.getJSONArray("causes").toStringList(),
                    actions = o.getJSONArray("actions").toStringList(),
                    severity = Severity.valueOf(o.getString("severity").uppercase()),
                    drivable = o.getBoolean("drivable"),
                )
                info.code to info
            }
            return DtcRepository(entries)
        }

        private fun JSONArray.toStringList(): List<String> = (0 until length()).map { getString(it) }
    }
}
