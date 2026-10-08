package com.sctech.obd.data

/**
 * Minimal VIN (ISO 3779) helper: grouping for display and the manufacturer
 * from the WMI (first three characters). Model year is deliberately not
 * decoded: European makers are not required to encode it in position 10.
 */
data class Vin(val raw: String) {

    val isValid: Boolean = raw.length == 17 && raw.all { it.isLetterOrDigit() && it !in "IOQ" }

    /** "VF1 RJA00X 12345678" – WMI, VDS (incl. check digit), VIS. */
    val grouped: String =
        if (isValid) "${raw.substring(0, 3)} ${raw.substring(3, 9)} ${raw.substring(9)}" else raw

    val wmi: String? = if (isValid) raw.substring(0, 3) else null

    val manufacturer: String? = wmi?.let { MANUFACTURERS[it] }

    val region: String? = if (isValid) regionOf(raw) else null

    private companion object {
        // Makes common in Turkey; extend as needed
        val MANUFACTURERS = mapOf(
            "VF1" to "Renault",
            "UU1" to "Dacia",
            "VF3" to "Peugeot",
            "VR3" to "Peugeot",
            "VF7" to "Citroën",
            "VR7" to "Citroën",
            "ZFA" to "Fiat",
            "ZAR" to "Alfa Romeo",
            "WVW" to "Volkswagen",
            "WV1" to "Volkswagen Ticari",
            "WV2" to "Volkswagen Ticari",
            "WAU" to "Audi",
            "TRU" to "Audi",
            "TMB" to "Škoda",
            "VSS" to "SEAT",
            "WBA" to "BMW",
            "WMW" to "MINI",
            "WDD" to "Mercedes-Benz",
            "WDB" to "Mercedes-Benz",
            "W1K" to "Mercedes-Benz",
            "W0L" to "Opel",
            "W0V" to "Opel",
            "WF0" to "Ford",
            "NM0" to "Ford Otosan",
            "NMT" to "Toyota Türkiye",
            "SB1" to "Toyota",
            "VNK" to "Toyota",
            "JTD" to "Toyota",
            "KMH" to "Hyundai",
            "NLH" to "Hyundai Assan",
            "TMA" to "Hyundai",
            "KNA" to "Kia",
            "KNE" to "Kia",
            "U5Y" to "Kia",
            "JHM" to "Honda",
            "NLA" to "Honda Türkiye",
            "SHH" to "Honda",
            "SJN" to "Nissan",
            "VSK" to "Nissan",
            "JN1" to "Nissan",
            "JMZ" to "Mazda",
            "YV1" to "Volvo",
        )

        fun regionOf(vin: String): String? {
            val a = vin[0]
            val b = vin[1]
            return when {
                a == 'N' && b in 'L'..'R' -> "Türkiye"
                a == 'W' -> "Almanya"
                a == 'Z' -> "İtalya"
                a == 'V' && b in 'F'..'R' -> "Fransa"
                a == 'V' && b in 'S'..'W' -> "İspanya"
                a == 'T' && b in 'M'..'P' -> "Çekya"
                a == 'U' && b in 'U'..'Z' -> "Romanya"
                a == 'S' -> "Birleşik Krallık"
                a == 'Y' -> "İsveç / Finlandiya"
                a == 'J' -> "Japonya"
                a == 'K' -> "Güney Kore"
                a == 'L' -> "Çin"
                a in "145" -> "ABD"
                else -> null
            }
        }
    }
}
