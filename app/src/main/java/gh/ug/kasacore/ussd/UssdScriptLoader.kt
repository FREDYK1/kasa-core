package gh.ug.kasacore.ussd

import android.content.Context
import org.json.JSONObject

/**
 * Parses assets/ussd_scripts.json (a copy of the canonical config/ussd_scripts.json,
 * K10) into the Flow/Detect shapes UssdNavigator drives.
 *
 * Check _verification.status in the JSON before trusting a given flow with
 * real money: as of this comment, check_balance and send_money are VERIFIED
 * on a real MTN SIM; buy_data has been removed for now (docs/07_K10_USSD_MAPPING_GUIDE.md).
 */
object UssdScriptLoader {

    fun load(context: Context): Pair<Map<String, Flow>, Detect> {
        val json = context.assets.open("ussd_scripts.json").bufferedReader().use { it.readText() }
        val root = JSONObject(json)

        val detectObj = root.getJSONObject("detect")
        val detect = Detect(
            pin = detectObj.getJSONArray("pin_markers").toStringList(),
            success = detectObj.getJSONArray("success_markers").toStringList(),
            failure = detectObj.getJSONArray("failure_markers").toStringList(),
            timeout = detectObj.getJSONArray("timeout_markers").toStringList(),
        )

        val flowsObj = root.getJSONObject("flows")
        val flows = flowsObj.keys().asSequence().associateWith { key ->
            val f = flowsObj.getJSONObject(key)
            val stepsArr = f.getJSONArray("steps")
            val steps = (0 until stepsArr.length()).map { i ->
                val s = stepsArr.getJSONObject(i)
                Step(
                    at = s.getString("at"),
                    chooseLabel = if (s.has("choose_label")) s.getJSONArray("choose_label").toStringList() else null,
                    fallbackOption = s.optString("fallback_option", null),
                    input = s.optString("input", null),
                    action = s.optString("action", null),
                    optional = s.optBoolean("optional", false),
                    readReviewAloud = s.optBoolean("read_review_aloud", false),
                    expect = if (s.has("expect")) s.getJSONArray("expect").toStringList() else null,
                )
            }
            Flow(
                label = f.getString("label"),
                requiresPin = f.getBoolean("requires_pin"),
                steps = steps,
                readResult = f.getBoolean("read_result"),
            )
        }

        return flows to detect
    }

    private fun org.json.JSONArray.toStringList(): List<String> =
        (0 until length()).map { getString(it) }
}
