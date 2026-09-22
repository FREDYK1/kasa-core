package gh.ug.kasacore.ussd

/*
 * K10 — the navigator that DRIVES the mapped menu tree (config/ussd_scripts.json).
 *
 * Design principle: LABEL-DRIVEN navigation. At each USSD dialog we read the on-screen
 * text, decide which step of the flow we're at, and pick the option whose LABEL matches —
 * using the numeric option only as a fallback. This survives promo items and re-ordered
 * menus, which blind "press 1, press 1, press amount" injection does not.
 *
 * The PIN step is special: we NEVER inject it. We detect the PIN prompt and hand off to
 * the user (UssdListener.onPinRequired). See docs/06 + docs/07.
 *
 * This is the engine logic; the AccessibilityService (UssdSpike.kt) provides readDialog()
 * and inject()/choose(). Skeleton — wire to the real service and verify on a device.
 */

data class Step(
    val at: String,
    val chooseLabel: List<String>? = null,
    val fallbackOption: String? = null,
    val input: String? = null,      // may contain {amount},{recipient_number},{reference},{bundle}
    val action: String? = null,     // "handoff_pin"
    val optional: Boolean = false,
    val readReviewAloud: Boolean = false
)
data class Flow(val label: String, val requiresPin: Boolean, val steps: List<Step>, val readResult: Boolean)
data class Detect(
    val pin: List<String>, val success: List<String>,
    val failure: List<String>, val timeout: List<String>
)

class UssdNavigator(
    private val flows: Map<String, Flow>,
    private val detect: Detect,
    private val service: UssdService,     // reads dialogs, injects text, taps options
    private val listener: UssdListener
) {
    /** intent.action selects the flow; slots fill {placeholders}. */
    fun run(action: String, slots: Map<String, String>) {
        val flow = flows[action] ?: return listener.onError("Unsupported action: $action")
        var stepIndex = 0

        service.onDialog { text ->
            val t = text.lowercase()

            // 1) terminal states first — always check these
            when {
                detect.failure.any { t.contains(it) } -> return@onDialog finish { listener.onError(text) }
                detect.timeout.any { t.contains(it) } -> return@onDialog finish { listener.onError("The session timed out. Please try again.") }
                detect.pin.any { t.contains(it) } -> {
                    // PIN prompt: STOP. Never inject. Hand off to the user.
                    //
                    // On a real MTN SIM the review and the PIN prompt are often the SAME
                    // screen ("Transfer to X for GHS 1... Fee is GHS 0.00, Tax amount is
                    // GHS 1.00. Enter MM PIN or 2 to cancel." — captured verifying K10 on
                    // send_money). Speak that text before handing off, or the amount/fee
                    // the K05 safety design promises to read aloud never gets said.
                    listener.onMenuRead(text)
                    listener.onPinRequired()
                    return@onDialog   // engine pauses; user types into the system dialog
                }
                flow.readResult && detect.success.any { t.contains(it) } ->
                    return@onDialog finish { listener.onSuccess(text) }
            }

            // 2) otherwise drive the current step
            if (stepIndex >= flow.steps.size) return@onDialog
            val step = flow.steps[stepIndex]
            listener.onMenuRead(text)                          // app speaks the menu in Twi

            when {
                step.action == "handoff_pin" -> listener.onPinRequired()
                step.input != null -> {
                    val value = fill(step.input, slots)
                    if (value.isBlank() && step.optional) service.skip() else service.inject(value)
                    stepIndex++
                }
                step.chooseLabel != null -> {
                    if (step.readReviewAloud) listener.onMenuRead(text) // read amount+fee before confirm
                    val option = matchOption(text, step.chooseLabel) ?: step.fallbackOption
                    if (option == null) return@onDialog finish {
                        listener.onError("Menu did not match what we expected. Stopping to stay safe.")
                    }
                    service.choose(option)
                    stepIndex++
                }
            }
        }
        service.dial("*170#")
    }

    /** Find the option number whose on-screen label matches one of the expected labels. */
    private fun matchOption(dialogText: String, expected: List<String>): String? {
        // dialog lines look like: "1. Transfer Money", "2. Airtime & Data" ...
        val lineRegex = Regex("""(\d+)[).\s]+(.+)""")
        for (line in dialogText.lines()) {
            val m = lineRegex.find(line.trim()) ?: continue
            val optNum = m.groupValues[1]
            val label = m.groupValues[2].lowercase()
            if (expected.any { label.contains(it) }) return optNum
        }
        return null   // caller uses fallbackOption
    }

    private fun fill(template: String, slots: Map<String, String>): String {
        var s = template
        for ((k, v) in slots) s = s.replace("{$k}", v)
        return if (s.contains("{")) "" else s   // unfilled optional -> blank
    }

    private inline fun finish(block: () -> Unit) { service.end(); block() }
}

/* Provided by the AccessibilityService layer (UssdSpike.kt): */
interface UssdService {
    fun dial(code: String)
    fun onDialog(handler: (String) -> Unit)
    fun inject(text: String)     // type + send
    fun choose(option: String)   // type the option number + send
    fun skip()                   // send empty / skip an optional field
    fun end()                    // tear down listeners
}
