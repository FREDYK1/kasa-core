package gh.ug.kasacore.payees

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import gh.ug.kasacore.model.Payee
import gh.ug.kasacore.model.Recipient

/**
 * K05 Decision 3 — the piece that used to live on the server. Contact names
 * never leave this device: this repository stores the trusted-payees list
 * (SharedPreferences is enough for a hackathon prototype; a real build would
 * use EncryptedSharedPreferences or a local DB) and resolves the `raw` text
 * the server heard against it.
 *
 * Same safety property the old server code had, moved to the right side of
 * the trust boundary: if two payees are too close a match, we refuse and
 * force a re-ask rather than risk sending to the wrong person.
 */
class PayeesRepository(context: Context) {

    private val prefs = context.getSharedPreferences("kasa_payees", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<Payee>>() {}.type

    fun list(): List<Payee> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching { gson.fromJson<List<Payee>>(json, listType) }.getOrDefault(emptyList())
    }

    fun add(payee: Payee) {
        val updated = list().filterNot { it.name.equals(payee.name, ignoreCase = true) } + payee
        prefs.edit().putString(KEY, gson.toJson(updated)).apply()
    }

    fun remove(name: String) {
        prefs.edit().putString(KEY, gson.toJson(list().filterNot { it.name == name })).apply()
    }

    /**
     * Fill in `matched_contact`/`number` for a server-returned recipient. If the
     * server already found a raw phone number, that's authoritative — no
     * matching needed. Otherwise fuzzy-match `raw` against the trusted list; if
     * two payees are too close, return the recipient unresolved (matched_contact
     * stays null) so the Confirm screen shows "who did you mean?" instead of
     * guessing.
     */
    fun resolve(recipient: Recipient?): Recipient? {
        if (recipient == null) return null
        if (recipient.number != null) return recipient // a spoken phone number, nothing to match

        val payees = list()
        val scored = payees.map { it to similarity(it.name, recipient.raw) }
            .filter { it.second >= MATCH_THRESHOLD }
            .sortedByDescending { it.second }

        val best = scored.getOrNull(0) ?: return recipient
        val second = scored.getOrNull(1)
        if (second != null && best.second - second.second < AMBIGUITY_MARGIN) {
            return recipient // too close to call — never guess
        }
        return recipient.copy(matched_contact = best.first.name, number = best.first.number)
    }

    companion object {
        private const val KEY = "payees_json"
        private const val MATCH_THRESHOLD = 0.55
        private const val AMBIGUITY_MARGIN = 0.08

        /** Simple, dependency-free similarity: token containment + normalized edit distance. */
        internal fun similarity(name: String, raw: String): Double {
            val a = name.trim().lowercase()
            val b = raw.trim().lowercase()
            if (a.isEmpty() || b.isEmpty()) return 0.0
            if (a == b) return 1.0
            val firstName = a.split(" ").firstOrNull() ?: a
            if (firstName == b || a.contains(b) || b.contains(firstName)) return 0.9
            val dist = levenshtein(firstName, b)
            val maxLen = maxOf(firstName.length, b.length)
            return 1.0 - (dist.toDouble() / maxLen)
        }

        private fun levenshtein(a: String, b: String): Int {
            val dp = Array(a.length + 1) { IntArray(b.length + 1) }
            for (i in 0..a.length) dp[i][0] = i
            for (j in 0..b.length) dp[0][j] = j
            for (i in 1..a.length) {
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                }
            }
            return dp[a.length][b.length]
        }
    }
}
