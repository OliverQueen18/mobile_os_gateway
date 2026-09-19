package com.osgateway.distributor.util

data class PhoneCountry(
    val code: String,
    val name: String,
    val dial: String,
    val flag: String,
) {
    val label: String get() = "$flag $name (+$dial)"
    val shortLabel: String get() = "$flag +$dial"
}

/** Indicatifs (Afrique prioritaire + pays courants), alignés sur le frontend. */
val PHONE_COUNTRIES: List<PhoneCountry> = listOf(
    PhoneCountry("ML", "Mali", "223", "🇲🇱"),
    PhoneCountry("CI", "Côte d’Ivoire", "225", "🇨🇮"),
    PhoneCountry("SN", "Sénégal", "221", "🇸🇳"),
    PhoneCountry("BF", "Burkina Faso", "226", "🇧🇫"),
    PhoneCountry("NE", "Niger", "227", "🇳🇪"),
    PhoneCountry("GN", "Guinée", "224", "🇬🇳"),
    PhoneCountry("GW", "Guinée-Bissau", "245", "🇬🇼"),
    PhoneCountry("GM", "Gambie", "220", "🇬🇲"),
    PhoneCountry("MR", "Mauritanie", "222", "🇲🇷"),
    PhoneCountry("TG", "Togo", "228", "🇹🇬"),
    PhoneCountry("BJ", "Bénin", "229", "🇧🇯"),
    PhoneCountry("GH", "Ghana", "233", "🇬🇭"),
    PhoneCountry("NG", "Nigeria", "234", "🇳🇬"),
    PhoneCountry("CM", "Cameroun", "237", "🇨🇲"),
    PhoneCountry("GA", "Gabon", "241", "🇬🇦"),
    PhoneCountry("CG", "Congo", "242", "🇨🇬"),
    PhoneCountry("CD", "RD Congo", "243", "🇨🇩"),
    PhoneCountry("TD", "Tchad", "235", "🇹🇩"),
    PhoneCountry("CF", "Centrafrique", "236", "🇨🇫"),
    PhoneCountry("GQ", "Guinée équatoriale", "240", "🇬🇶"),
    PhoneCountry("ST", "Sao Tomé", "239", "🇸🇹"),
    PhoneCountry("AO", "Angola", "244", "🇦🇴"),
    PhoneCountry("CV", "Cap-Vert", "238", "🇨🇻"),
    PhoneCountry("LR", "Libéria", "231", "🇱🇷"),
    PhoneCountry("SL", "Sierra Leone", "232", "🇸🇱"),
    PhoneCountry("MA", "Maroc", "212", "🇲🇦"),
    PhoneCountry("DZ", "Algérie", "213", "🇩🇿"),
    PhoneCountry("TN", "Tunisie", "216", "🇹🇳"),
    PhoneCountry("LY", "Libye", "218", "🇱🇾"),
    PhoneCountry("EG", "Égypte", "20", "🇪🇬"),
    PhoneCountry("SD", "Soudan", "249", "🇸🇩"),
    PhoneCountry("SS", "Soudan du Sud", "211", "🇸🇸"),
    PhoneCountry("ET", "Éthiopie", "251", "🇪🇹"),
    PhoneCountry("KE", "Kenya", "254", "🇰🇪"),
    PhoneCountry("UG", "Ouganda", "256", "🇺🇬"),
    PhoneCountry("TZ", "Tanzanie", "255", "🇹🇿"),
    PhoneCountry("RW", "Rwanda", "250", "🇷🇼"),
    PhoneCountry("BI", "Burundi", "257", "🇧🇮"),
    PhoneCountry("DJ", "Djibouti", "253", "🇩🇯"),
    PhoneCountry("SO", "Somalie", "252", "🇸🇴"),
    PhoneCountry("ER", "Érythrée", "291", "🇪🇷"),
    PhoneCountry("MG", "Madagascar", "261", "🇲🇬"),
    PhoneCountry("MU", "Maurice", "230", "🇲🇺"),
    PhoneCountry("SC", "Seychelles", "248", "🇸🇨"),
    PhoneCountry("KM", "Comores", "269", "🇰🇲"),
    PhoneCountry("ZA", "Afrique du Sud", "27", "🇿🇦"),
    PhoneCountry("NA", "Namibie", "264", "🇳🇦"),
    PhoneCountry("BW", "Botswana", "267", "🇧🇼"),
    PhoneCountry("ZW", "Zimbabwe", "263", "🇿🇼"),
    PhoneCountry("ZM", "Zambie", "260", "🇿🇲"),
    PhoneCountry("MW", "Malawi", "265", "🇲🇼"),
    PhoneCountry("MZ", "Mozambique", "258", "🇲🇿"),
    PhoneCountry("FR", "France", "33", "🇫🇷"),
    PhoneCountry("BE", "Belgique", "32", "🇧🇪"),
    PhoneCountry("CH", "Suisse", "41", "🇨🇭"),
    PhoneCountry("CA", "Canada", "1", "🇨🇦"),
    PhoneCountry("US", "États-Unis", "1", "🇺🇸"),
    PhoneCountry("GB", "Royaume-Uni", "44", "🇬🇧"),
    PhoneCountry("DE", "Allemagne", "49", "🇩🇪"),
    PhoneCountry("ES", "Espagne", "34", "🇪🇸"),
    PhoneCountry("PT", "Portugal", "351", "🇵🇹"),
    PhoneCountry("IT", "Italie", "39", "🇮🇹"),
    PhoneCountry("AE", "Émirats arabes unis", "971", "🇦🇪"),
    PhoneCountry("SA", "Arabie saoudite", "966", "🇸🇦"),
    PhoneCountry("IN", "Inde", "91", "🇮🇳"),
    PhoneCountry("CN", "Chine", "86", "🇨🇳"),
)

const val DEFAULT_PHONE_COUNTRY = "ML"

fun findCountryByCode(code: String): PhoneCountry? =
    PHONE_COUNTRIES.firstOrNull { it.code.equals(code, ignoreCase = true) }

fun joinPhone(countryCode: String, national: String): String {
    val country = findCountryByCode(countryCode) ?: findCountryByCode(DEFAULT_PHONE_COUNTRY)!!
    val local = national.filter { it.isDigit() }.trimStart('0')
    if (local.isBlank()) return ""
    return "+${country.dial}$local"
}
