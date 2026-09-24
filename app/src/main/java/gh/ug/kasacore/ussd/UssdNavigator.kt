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
    val readReviewAloud: Boolean = false,
    val expect: List<String>? = null, // lowercase phrases; this step only fires if one is on screen
    val whenSlot: String? = null,     // step is skipped unless this slot is set (e.g. other-network transfers)
    val unlessSlot: String? = null    // step is skipped if this slot is set (the MTN-only variant)
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

        // Same flow, different route: e.g. a Telecel/AT number goes Transfer -> Other Networks -> Telecel
        // where an MTN number goes Transfer -> MoMo User. The JSON marks which steps belong to which
        // route with when_slot / unless_slot; the engine only sets flags describing the recipient.
        val steps = flow.steps.filter { step ->
            (step.whenSlot == null || slots.containsKey(step.whenSlot)) &&
                (step.unlessSlot == null || !slots.containsKey(step.unlessSlot))
        }

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
            if (stepIndex >= steps.size) return@onDialog
            val step = steps[stepIndex]

            // Only drive a screen that can take a reply. Samsung shows a "USSD code running..."
            // progress dialog between every step; it has no input field, so inject()/choose()
            // silently do nothing — but stepIndex++ would still burn a step on it, shifting every
            // later step one screen early (e.g. typing the recipient number into the Transfer menu).
            if (!service.hasInput()) return@onDialog

            // Defense in depth for money flows: an input step only fires when its own prompt is
            // on screen, so a desync can never type a number/amount into the wrong menu.
            val expect = step.expect
            if (expect != null && expect.none { t.contains(it) }) {
                // Don't stall silently: speak/caption what's on screen so the user (and whoever is
                // debugging) can see exactly which wording the step didn't recognise.
                listener.onMenuRead(text)
                return@onDialog
            }

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
        val options = dialogText.lines().mapNotNull { line ->
            lineRegex.find(line.trim())?.let { it.groupValues[1] to it.groupValues[2].trim().lowercase() }
        }
        // Exact label first: a short label like "at" must pick "1) AT", not any option that merely
        // contains those letters. Then fall back to "contains" for labels with extra wording.
        options.firstOrNull { (_, label) -> label in expected }?.let { return it.first }
        options.firstOrNull { (_, label) -> expected.any { label.contains(it) } }?.let { return it.first }
        return null   // caller uses fallbackOption
    }

    private fun fill(template: String, slots: Map<String, String>): String {
        var s = template
        for ((k, v) in slots) s = s.replace("{$k}", v)
        return if (s.contains("{")) "" else s   // unfilled optional -> blank
    }

    private inline fun finish(block: () -> Unit) {
        // Verified on a real MTN SIM: after a result (success, failure, or timeout) the
        // phone leaves a native "Cancel/Send"-style dialog on screen. Dismiss it (tap
        // Cancel) so the user isn't stuck looking at a stale system dialog afterwards.
        service.dismiss()
        service.end()
        block()
    }
}

/* Provided by the AccessibilityService layer (UssdSpike.kt): */
interface UssdService {
    fun dial(code: String)
    fun onDialog(handler: (String) -> Unit)
    fun inject(text: String)     // type + send
    fun choose(option: String)   // type the option number + send
    fun hasInput(): Boolean      // does the screen currently showing have a reply field?
    fun dismiss()                // tap "Cancel" on the native post-result dialog, if present
    fun skip()                   // send empty / skip an optional field
    fun end()                    // tear down listeners
}
