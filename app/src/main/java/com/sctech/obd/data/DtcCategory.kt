package com.sctech.obd.data

/**
 * System group of a trouble code per SAE J2012: the letter gives the domain,
 * the 2nd character standard (0/2/3) vs manufacturer-specific (1), and for
 * powertrain codes the 3rd character the subsystem.
 */
object DtcCategory {

    fun of(code: String): String {
        if (code.length < 3) return "Bilinmiyor"
        return when (code[0]) {
            'P' -> if (code[1] == '1') "Motor · üreticiye özel" else powertrain(code[2])
            'U' -> "Ağ haberleşmesi"
            'B' -> "Gövde elektroniği"
            'C' -> "Şasi · fren ve yürüyen"
            else -> "Bilinmiyor"
        }
    }

    private fun powertrain(subsystem: Char): String = when (subsystem) {
        '0', '1', '2' -> "Yakıt ve hava"
        '3' -> "Ateşleme"
        '4' -> "Emisyon kontrol"
        '5' -> "Hız ve rölanti"
        '6' -> "Motor beyni"
        '7', '8', '9' -> "Şanzıman"
        'A', 'B', 'C' -> "Hibrit sistem"
        else -> "Motor"
    }
}
