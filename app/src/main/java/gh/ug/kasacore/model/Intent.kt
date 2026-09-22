package gh.ug.kasacore.model

/*
 * The shared data model — mirrors config/intent_schema.json and
 * docs/00_START_HERE.md §4.1 EXACTLY. Backend produces it; this app and the
 * USSD engine consume it. Do not add fields here without changing the schema
 * "by agreement" first (see 00_START_HERE.md §6).
 *
 * Per K05 Decision 3, `recipient.matched_contact` is ALWAYS null as it comes
 * back from the server — the server only ever returns `raw` (what it heard)
 * and, if a phone number was spoken, `number`. Resolving `raw` against the
 * user's on-device trusted-payees list is this app's job; see
 * payees/PayeesRepository.kt. Intent is *proposed*, never *executed* — the
 * USSD engine only runs after explicit approval on the Confirm screen.
 */

data class Recipient(
    val raw: String,
    val matched_contact: String?,
    val number: String?,
)

data class Intent(
    val action: String,
    val amount: Double?,
    val recipient: Recipient?,
    val extra: Map<String, Any> = emptyMap(),
    val confidence: Double,
    val needs_confirmation: Boolean,
    val transcript: String,
    val language: String,
) {
    companion object {
        const val SEND_MONEY = "send_money"
        const val CHECK_BALANCE = "check_balance"
        const val BUY_DATA = "buy_data"
        const val BUY_AIRTIME = "buy_airtime"
        const val CASH_OUT = "cash_out"
        const val PAY_MERCHANT = "pay_merchant"
        const val UNKNOWN = "unknown"
    }
}

/** Below this, the app re-asks; it never guesses. Matches CONFIDENCE_FLOOR server-side. */
const val CONFIDENCE_FLOOR = 0.6
