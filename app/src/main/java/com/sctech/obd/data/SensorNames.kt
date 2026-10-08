package com.sctech.obd.data

/**
 * Plain Turkish names for AndrOBD data items, keyed by pids.csv mnemonic.
 * AndrOBD's own messages_tr.properties is incomplete and uneven, so the
 * items users actually see get a curated name; everything else falls back
 * to the library label.
 */
object SensorNames {

    private val names = mapOf(
        // Mode 01
        "number_fault_codes" to "Arıza kodu sayısı",
        "status_mil" to "Arıza lambası",
        "status_fuel_system_1" to "Yakıt sistemi 1 durumu",
        "status_fuel_system_2" to "Yakıt sistemi 2 durumu",
        "engine_load_calculated" to "Motor yükü (hesaplanan)",
        "engine_load" to "Motor yükü (mutlak)",
        "engine_coolant_temperature" to "Motor suyu sıcaklığı",
        "fuel_trim_short_b1" to "Kısa dönem yakıt düzeltme (Bank 1)",
        "fuel_trim_long_b1" to "Uzun dönem yakıt düzeltme (Bank 1)",
        "fuel_trim_short_b2" to "Kısa dönem yakıt düzeltme (Bank 2)",
        "fuel_trim_long_b2" to "Uzun dönem yakıt düzeltme (Bank 2)",
        "fuel_pressure" to "Yakıt basıncı",
        "fuel_pressure_rel" to "Yakıt rampası basıncı (manifolda göre)",
        "fuel_pressure_wr" to "Yakıt rampası basıncı",
        "intake_manifold_pressure" to "Emme manifoldu basıncı",
        "engine_speed" to "Motor devri",
        "vehicle_speed" to "Araç hızı",
        "ignition_timing_advance_cyl1" to "Ateşleme avansı (1. silindir)",
        "intake_air_temperature" to "Emme havası sıcaklığı",
        "mass_airflow" to "Hava debisi (MAF)",
        "throttle_position_abs" to "Gaz kelebeği konumu",
        "throttle_position_rel" to "Gaz kelebeği konumu (bağıl)",
        "throttle_position_abs_b" to "Gaz kelebeği konumu B",
        "throttle_position_c" to "Gaz kelebeği konumu C",
        "throttle_position_d" to "Gaz pedalı konumu D",
        "throttle_position_e" to "Gaz pedalı konumu E",
        "throttle_position_f" to "Gaz pedalı konumu F",
        "commanded_throttle_position" to "İstenen gaz kelebeği konumu",
        "number_oxygen_sensors" to "Oksijen sensörü sayısı",
        "map_oxygen_sensors_present" to "Oksijen sensörü sayısı",
        "obd_type" to "OBD standardı",
        "running_time" to "Motor çalışma süresi",
        "distance_sine_mil_active" to "Arıza lambası yanarken gidilen yol",
        "time_since_mil_on" to "Arıza lambası yanarken çalışma süresi",
        "distance_since_ecu_reset" to "Kodlar silindikten sonra gidilen yol",
        "time_since_ecu_reset" to "Kodlar silindikten sonra geçen süre",
        "counts_warmups_since_ecu_reset" to "Kodlar silindikten sonra ısınma sayısı",
        "egr_ratio_commanded" to "İstenen EGR oranı",
        "egr_error" to "EGR hatası",
        "evaporative_purge_ratio" to "Yakıt buharı (EVAP) tahliye oranı",
        "fuel_level" to "Yakıt seviyesi",
        "pressure_vapor_evaporative_purge" to "Yakıt buharı basıncı",
        "pressure_vapor_evaporative_purge_absolute" to "Yakıt buharı basıncı (mutlak)",
        "pressure_vapor_evaporative_purge_rel" to "Yakıt buharı basıncı",
        "barometric_pressure" to "Atmosfer basıncı",
        "ecu_voltage" to "Sistem voltajı",
        "equiv_ratio_commanded" to "İstenen hava/yakıt oranı (lambda)",
        "ambient_air_temperature" to "Dış hava sıcaklığı",
        "fuel_type" to "Yakıt tipi",
        "ethanol_fuel_percentage" to "Etanol oranı",
        "engine_oil_temperature" to "Motor yağı sıcaklığı",
        "fuel_rate" to "Anlık yakıt tüketimi",
        "status_secondary_air_system" to "İkincil hava sistemi durumu",
        "status_power_take_off" to "PTO durumu",
        // Mode 09
        "calibration_identifier" to "Kalibrasyon kimliği",
        "calibration_identifier2" to "Kalibrasyon kimliği 2",
        "calibration_verification" to "Kalibrasyon doğrulama (CVN)",
        "ecu_name" to "Beyin adı",
        "IGNCNTR" to "Kontak açma sayısı",
        "OBDCOMP" to "OBD izleme koşulu sayısı",
    )

    // Bank/sensor families: o2_sensor_voltage_b1s2, cat_temperature_b2s1, ...
    private val patterns = listOf(
        Regex("o2_sensor_voltage_b(\\d)s(\\d)") to "Oksijen sensörü voltajı (Bank %s, Sensör %s)",
        Regex("o2_sensor_fuel_trim_b(\\d)s(\\d)") to "Oksijen sensörü yakıt düzeltme (Bank %s, Sensör %s)",
        Regex("o2_wr_lambda_b(\\d)s(\\d)") to "Geniş bant O2 lambda (Bank %s, Sensör %s)",
        Regex("o2_wr_voltage_b(\\d)s(\\d)") to "Geniş bant O2 voltajı (Bank %s, Sensör %s)",
        Regex("cat_temperature_b(\\d)s(\\d)") to "Katalizör sıcaklığı (Bank %s, Sensör %s)",
        Regex("o2_wr_lambda_s(\\d)") to "Geniş bant O2 lambda (Sensör %s)",
        Regex("o2_wr_current_s(\\d)") to "Geniş bant O2 akımı (Sensör %s)",
    )

    fun turkish(mnemonic: String): String? {
        names[mnemonic]?.let { return it }
        for ((regex, template) in patterns) {
            val match = regex.matchEntire(mnemonic) ?: continue
            return template.format(*match.groupValues.drop(1).toTypedArray())
        }
        return null
    }

    fun displayName(mnemonic: String, libraryLabel: String): String = turkish(mnemonic) ?: libraryLabel
}
