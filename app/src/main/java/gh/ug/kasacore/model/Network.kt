package gh.ug.kasacore.model

/**
 * Which mobile network a Ghana number is on, decided purely by its 3-digit prefix.
 * The USSD route differs by network: MTN numbers go Transfer -> MoMo User, while Telecel and
 * AT numbers go Transfer -> Other Networks -> pick the network (see ussd_scripts.json send_money).
 */
enum class Network(val label: String) {
    MTN("MTN"),
    TELECEL("Telecel"),
    AT("AT");

    companion object {
        private val prefixes: Map<String, Network> = mapOf(
            "024" to MTN, "025" to MTN, "053" to MTN, "054" to MTN, "055" to MTN, "059" to MTN,
            "020" to TELECEL, "050" to TELECEL,
            "026" to AT, "027" to AT, "056" to AT, "057" to AT,
        )

        /** null when the number isn't a valid 10-digit local number on a supported network. */
        fun fromNumber(number: String?): Network? {
            val local = number?.toGhanaLocalNumber() ?: return null
            if (local.length != 10) return null
            return prefixes[local.take(3)]
        }
    }
}

/** Digits only, with an international "233…" prefix turned back into local "0…" form. */
fun String.toGhanaLocalNumber(): String {
    val digits = filter { it.isDigit() }
    return if (digits.startsWith("233") && digits.length == 12) "0" + digits.drop(3) else digits
}

/** Text-to-speech reads "AT" as the word "at". Spell it as letters for speech; captions keep "AT". */
fun String.spokenNetworkNames(): String = replace(Regex("\\bAT\\b"), "A T")
