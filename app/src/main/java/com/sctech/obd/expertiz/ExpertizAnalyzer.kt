package com.sctech.obd.expertiz

import com.sctech.obd.core.DtcKind
import com.sctech.obd.core.LiveValue
import com.sctech.obd.core.TroubleCode
import com.sctech.obd.data.DtcInfo
import com.sctech.obd.data.Severity
import com.sctech.obd.data.Vin
import java.util.Locale

/**
 * Turns raw OBD data into the used-car report. Pure Kotlin so the rules can be unit tested.
 *
 * Main idea: sellers clear trouble codes to switch the check-engine light off. Clearing (or
 * disconnecting the battery) resets the "since codes cleared" counters and the readiness
 * monitors, so short distances / few warm-ups / many incomplete monitors are a red flag.
 */
object ExpertizAnalyzer {

    private val TR = Locale.forLanguageTag("tr-TR")

    // Thresholds for "codes were cleared recently"
    const val CLEARED_FAIL_KM = 50.0
    const val CLEARED_WARN_KM = 300.0
    const val CLEARED_FAIL_WARMUPS = 5
    const val CLEARED_WARN_WARMUPS = 20
    const val SUSPICIOUS_INCOMPLETE_MONITORS = 3

    /** PID 0x31 / 0x21 saturate at 65 535 km. */
    private const val DISTANCE_MAX_KM = 65_535.0

    // ─────────────────────────────────────────────── extraction from AndrOBD live items

    /**
     * Readiness monitors from PID 01. AndrOBD exposes each monitor as a masked raw value:
     * byte B monitors (bit k): bit0 = supported, bit4 = incomplete;
     * byte C/D monitors (bit k): bit8 = supported, bit0 = incomplete.
     */
    fun monitorsFrom(live: List<LiveValue>, compressionIgnition: Boolean?): List<MonitorStatus> {
        val byMnemonic = live.associateBy { it.mnemonic }
        val result = mutableListOf<MonitorStatus>()

        CONTINUOUS.forEach { (key, label) ->
            val v = byMnemonic[key]?.value?.toLong() ?: return@forEach
            if (v and 0x01L != 0L) result += MonitorStatus(key, label, complete = v and 0x10L == 0L)
        }
        NON_CONTINUOUS.forEachIndexed { bit, key ->
            val v = byMnemonic[key]?.value?.toLong() ?: return@forEachIndexed
            val label = (if (compressionIgnition == true) DIESEL_LABELS[bit] else SPARK_LABELS[bit]) ?: return@forEachIndexed
            if (v and 0x100L != 0L) result += MonitorStatus(key, label, complete = v and 0x01L == 0L)
        }
        return result
    }

    fun collect(live: List<LiveValue>, codes: List<TroubleCode>, vin: String?, createdAt: Long, demo: Boolean): ExpertizData {
        fun num(mnemonic: String) = live.firstOrNull { it.mnemonic == mnemonic }?.value
        val compression = num("status_ignition_monitoring")?.let { it.toLong() and 0x01L != 0L }
        return ExpertizData(
            milOn = num("status_mil")?.let { it != 0.0 },
            compressionIgnition = compression,
            monitors = monitorsFrom(live, compression),
            distanceSinceClearKm = num("distance_since_ecu_reset"),
            timeSinceClearHours = num("time_since_ecu_reset"),
            warmupsSinceClear = num("counts_warmups_since_ecu_reset")?.toInt(),
            distanceWithMilKm = num("distance_sine_mil_active"),
            odometerKm = num("odometer_reading")?.takeIf { it > 0.0 },
            codes = codes,
            vin = vin?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = createdAt,
            demo = demo,
        )
    }

    // ─────────────────────────────────────────────── analysis

    fun analyze(data: ExpertizData, explain: (String) -> DtcInfo?): ExpertizReport {
        val sections = listOf(
            clearedCodes(data, explain),
            troubleCodes(data, explain),
            readiness(data),
            identity(data),
            odometer(data),
        )
        val rated = sections.map { it.status }.filter { it == CheckStatus.PASS || it == CheckStatus.WARN || it == CheckStatus.FAIL }
        val verdict = when {
            rated.isEmpty() -> CheckStatus.NO_DATA
            CheckStatus.FAIL in rated -> CheckStatus.FAIL
            CheckStatus.WARN in rated -> CheckStatus.WARN
            else -> CheckStatus.PASS
        }
        val (headline, summary) = when (verdict) {
            CheckStatus.PASS -> "Temiz görünüyor" to "Elektronik kontrollerde dikkat çeken bir bulgu yok."
            CheckStatus.WARN -> "Dikkat gerektiriyor" to
                "${sections.count { it.status == CheckStatus.WARN }} kontrolde dikkat edilmesi gereken bulgu var. Ayrıntıları satıcıyla konuşun."
            CheckStatus.FAIL -> "Sorun tespit edildi" to
                "${sections.count { it.status == CheckStatus.FAIL }} kontrolde ciddi bulgu var. Satın almadan önce servise kontrol ettirin."
            else -> "Değerlendirilemedi" to "Araç bu kontroller için yeterli veri vermedi."
        }
        return ExpertizReport(verdict, headline, summary, sections, data.vin, data.createdAt, data.demo)
    }

    /**
     * Permanent DTCs that are no longer stored: the stored code was cleared with a scan
     * tool, but the ECU has not yet confirmed the fault is fixed. The closest OBD gets to
     * "showing deleted codes" — cleared stored/pending codes themselves are gone for good.
     */
    fun clearedButUnresolved(codes: List<TroubleCode>): List<TroubleCode> {
        val stored = codes.filter { it.kind == DtcKind.STORED }.map { it.code }.toSet()
        return codes.filter { it.kind == DtcKind.PERMANENT && it.code !in stored }.distinctBy { it.code }
    }

    private fun clearedCodes(d: ExpertizData, explain: (String) -> DtcInfo?): CheckSection {
        val incomplete = d.monitors.count { !it.complete }
        val findings = mutableListOf<Finding>()
        val unresolved = clearedButUnresolved(d.codes)
        unresolved.forEach { code ->
            findings += Finding(code.code, explain(code.code)?.title ?: code.libraryDescription, CheckStatus.FAIL)
        }
        d.distanceSinceClearKm?.let { findings += Finding("Silmeden beri gidilen yol", km(it)) }
        d.timeSinceClearHours?.let { findings += Finding("Silmeden beri motor çalışma süresi", hours(it)) }
        d.warmupsSinceClear?.let { findings += Finding("Silmeden beri ısınma sayısı", it.toString()) }
        if (d.monitors.isNotEmpty()) findings += Finding("Tamamlanmamış emisyon testi", "$incomplete / ${d.monitors.size}")

        var status: CheckStatus
        var summary: String
        val dist = d.distanceSinceClearKm
        val warm = d.warmupsSinceClear
        when {
            dist != null && dist < CLEARED_FAIL_KM -> {
                status = CheckStatus.FAIL
                summary = "Arıza kodları yalnızca ${km(dist)} önce silinmiş. Satıcı bir arızayı gizlemiş olabilir."
            }
            dist != null && dist < CLEARED_WARN_KM -> {
                status = CheckStatus.WARN
                summary = "Arıza kodları ${km(dist)} önce silinmiş. Yakın zamanda yapılmış bir silme işlemi olabilir."
            }
            dist != null -> {
                status = CheckStatus.PASS
                summary = "Kodlar en son ${km(dist)} önce silinmiş; yakın zamanda silme belirtisi yok."
            }
            warm != null && warm < CLEARED_FAIL_WARMUPS -> {
                status = CheckStatus.FAIL
                summary = "Kodlar silindikten sonra motor yalnızca $warm kez ısınmış. Yakın zamanda silinmiş."
            }
            warm != null && warm < CLEARED_WARN_WARMUPS -> {
                status = CheckStatus.WARN
                summary = "Kodlar silindikten sonra motor $warm kez ısınmış. Yakın zamanda silinmiş olabilir."
            }
            warm != null -> {
                status = CheckStatus.PASS
                summary = "Kodlar silindikten sonra motor $warm kez ısınmış; yakın zamanda silme belirtisi yok."
            }
            d.monitors.isNotEmpty() -> {
                status = if (incomplete >= SUSPICIOUS_INCOMPLETE_MONITORS) CheckStatus.WARN else CheckStatus.PASS
                summary = "Araç silme sayaçlarını paylaşmıyor; değerlendirme emisyon testlerine göre yapıldı."
            }
            unresolved.isNotEmpty() -> {
                status = CheckStatus.FAIL
                summary = ""
            }
            else -> return CheckSection(
                SectionId.CLEARED_CODES, "Silinmiş kod kontrolü", CheckStatus.NO_DATA,
                "Araç bu kontrol için gerekli bilgiyi paylaşmıyor.", findings,
            )
        }
        // Hard evidence beats counters: a code was cleared but the ECU still holds it
        if (unresolved.isNotEmpty()) {
            status = CheckStatus.FAIL
            summary = "Silinmiş ama giderilmemiş ${unresolved.size} arıza bulundu. Bu kodlar teşhis cihazıyla silinmiş, " +
                "ancak araç beyni arızanın düzeldiğini henüz doğrulamadığı için kalıcı hafızada duruyor."
        } else if (status == CheckStatus.PASS && incomplete >= SUSPICIOUS_INCOMPLETE_MONITORS) {
            // Counters can look fine while several monitors are still incomplete
            status = CheckStatus.WARN
            summary = "Sayaçlar eski bir silmeyi gösteriyor, ancak $incomplete emisyon testi tamamlanmamış. " +
                "Testler yakın zamanda sıfırlanmış olabilir."
        }
        // A battery swap explains reset counters, but never a lingering permanent code
        if (status != CheckStatus.PASS && unresolved.isEmpty()) {
            summary += " Akü sökülmesi veya değişimi de bu sayaçları sıfırlar; satıcıya sorun."
        }
        return CheckSection(SectionId.CLEARED_CODES, "Silinmiş kod kontrolü", status, summary, findings)
    }

    private fun troubleCodes(d: ExpertizData, explain: (String) -> DtcInfo?): CheckSection {
        val stored = d.codes.count { it.kind == DtcKind.STORED }
        val pending = d.codes.count { it.kind == DtcKind.PENDING }
        val permanent = d.codes.count { it.kind == DtcKind.PERMANENT }
        val severe = d.codes.filter { explain(it.code)?.severity == Severity.HIGH }

        val findings = mutableListOf<Finding>()
        d.milOn?.let {
            findings += Finding("Arıza lambası", if (it) "Yanıyor" else "Sönük", if (it) CheckStatus.FAIL else CheckStatus.PASS)
        }
        d.distanceWithMilKm?.takeIf { it > 0 }?.let { findings += Finding("Lamba yanarken gidilen yol", km(it), CheckStatus.WARN) }
        d.codes.distinctBy { it.code }.forEach { code ->
            val info = explain(code.code)
            val status = when (info?.severity) {
                Severity.HIGH -> CheckStatus.FAIL
                else -> CheckStatus.WARN
            }
            findings += Finding(code.code, info?.title ?: code.libraryDescription, status)
        }

        val status = when {
            d.milOn == true || permanent > 0 || severe.isNotEmpty() -> CheckStatus.FAIL
            d.codes.isNotEmpty() -> CheckStatus.WARN
            else -> CheckStatus.PASS
        }
        val summary = when {
            d.codes.isEmpty() && d.milOn != true -> "Kayıtlı, bekleyen veya kalıcı arıza kodu yok."
            else -> buildString {
                append("$stored kayıtlı, $pending bekleyen, $permanent kalıcı kod.")
                val cleared = clearedButUnresolved(d.codes).size
                if (cleared > 0) append(" $cleared kod silinmiş ama kalıcı hafızada duruyor (silinmiş kod kontrolüne bakın).")
                else if (permanent > 0) append(" Kalıcı kodlar silinemez; arızanın gerçekten giderildiğini kanıtlamak için sürüş testi gerekir.")
                if (severe.isNotEmpty()) append(" ${severe.size} kod yüksek önemde.")
            }
        }
        return CheckSection(SectionId.TROUBLE_CODES, "Arıza kodları", status, summary, findings)
    }

    private fun readiness(d: ExpertizData): CheckSection {
        if (d.monitors.isEmpty()) {
            return CheckSection(
                SectionId.READINESS, "Emisyon testleri", CheckStatus.NO_DATA,
                "Araç emisyon test durumunu paylaşmıyor.", emptyList(),
            )
        }
        val incomplete = d.monitors.count { !it.complete }
        val findings = mutableListOf<Finding>()
        d.compressionIgnition?.let { findings += Finding("Motor tipi", if (it) "Dizel" else "Benzinli / LPG") }
        d.monitors.forEach {
            findings += Finding(it.label, if (it.complete) "Tamamlandı" else "Tamamlanmadı", if (it.complete) CheckStatus.PASS else CheckStatus.WARN)
        }
        val status = if (incomplete == 0) CheckStatus.PASS else CheckStatus.WARN
        val summary = if (incomplete == 0) {
            "Desteklenen ${d.monitors.size} emisyon testinin hepsi tamamlanmış."
        } else {
            "${d.monitors.size} testten $incomplete tanesi tamamlanmamış. Yakın zamanda kod silme veya akü işlemi yapılmış olabilir; " +
                "muayenedeki egzoz ölçümünden önce aracın birkaç gün normal kullanılması gerekebilir."
        }
        return CheckSection(SectionId.READINESS, "Emisyon testleri", status, summary, findings)
    }

    private fun identity(d: ExpertizData): CheckSection {
        val vin = d.vin ?: return CheckSection(
            SectionId.IDENTITY, "Araç kimliği", CheckStatus.NO_DATA,
            "Araç beyni şasi numarasını paylaşmıyor (bazı eski araçlarda normaldir).", emptyList(),
        )
        val decoded = Vin(vin.uppercase())
        val findings = mutableListOf(Finding("Beyindeki şasi no", decoded.grouped))
        decoded.manufacturer?.let { findings += Finding("Üretici", it) }
        decoded.region?.let { findings += Finding("Üretim bölgesi", it) }
        return CheckSection(
            SectionId.IDENTITY, "Araç kimliği", CheckStatus.INFO,
            "Beyinden okunan şasi numarasını ruhsattaki ve kaporta üzerindeki numarayla karşılaştırın. Farklıysa beyin değiştirilmiş olabilir.",
            findings,
        )
    }

    private fun odometer(d: ExpertizData): CheckSection {
        val odo = d.odometerKm ?: return CheckSection(
            SectionId.ODOMETER, "Kilometre", CheckStatus.NO_DATA,
            "Bu araç kilometre bilgisini OBD üzerinden paylaşmıyor (2019 öncesi araçların çoğunda yoktur).", emptyList(),
        )
        return CheckSection(
            SectionId.ODOMETER, "Kilometre", CheckStatus.INFO,
            "Göstergedeki kilometreyle karşılaştırın. Belirgin fark kilometre düşürme belirtisi olabilir.",
            listOf(Finding("Beyindeki kilometre", km(odo, saturates = false))), // PID 0xA6 is 32-bit
        )
    }

    // ─────────────────────────────────────────────── formatting

    /** [saturates]: the value comes from a 16-bit counter (PID 0x21/0x31) that stops at 65 535 km. */
    fun km(value: Double, saturates: Boolean = true): String =
        if (saturates && value >= DISTANCE_MAX_KM) "65.535 km'den fazla" else String.format(TR, "%,.0f km", value)

    fun hours(value: Double): String = when {
        value >= 48 -> String.format(TR, "%,.0f gün", value / 24)
        value >= 1 -> String.format(TR, "%,.0f saat", value)
        else -> String.format(TR, "%,.0f dakika", value * 60)
    }

    private val CONTINUOUS = listOf(
        "status_misfires" to "Tekleme izleme",
        "status_fuel_system" to "Yakıt sistemi",
        "status_component_test" to "Bileşen izleme",
    )

    /** PID 01 bytes C/D, bit 0..7. Meaning differs for spark vs compression ignition. */
    private val NON_CONTINUOUS = listOf(
        "status_catalyst_test",
        "status_catalyst_test_nox_monitor",
        "status_evaporative_system_test",
        "status_secondary_air_system_test",
        "status_ac_refrigerant_test",
        "status_oxygen_sensor_test",
        "status_oxygen_sensor_heater_test",
        "status_egr_system_test",
    )
    private val SPARK_LABELS = listOf(
        "Katalizör", "Isıtmalı katalizör", "Yakıt buharı (EVAP)", "İkincil hava",
        "Klima gazı", "Oksijen sensörü", "Oksijen sensörü ısıtıcı", "EGR / VVT",
    )
    private val DIESEL_LABELS = listOf(
        "NMHC katalizörü", "NOx / SCR (AdBlue)", null, "Turbo basıncı",
        null, "Egzoz gaz sensörü", "Partikül filtresi (DPF)", "EGR / VVT",
    )
}
