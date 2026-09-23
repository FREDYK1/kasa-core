package gh.ug.kasacore.ussd

import android.content.Context
import gh.ug.kasacore.model.Intent
import gh.ug.kasacore.model.toMoneyString

/**
 * Adapts the app-facing UssdEngine contract (00_START_HERE.md §4.3) onto
 * UssdNavigator (K10) + the live AccessibilityService (K12). This is what
 * Richmond swaps FakeUssdEngine for once K12/K13 pass on real devices — see
 * docs/08_K11_K13_SPIKE.md. No UI change needed either side of the swap.
 */
class RealUssdEngine(context: Context) : UssdEngine {

    private val flows: Map<String, Flow>
    private val detect: Detect

    init {
        val (f, d) = UssdScriptLoader.load(context)
        flows = f
        detect = d
    }

    override fun execute(intent: Intent, listener: UssdListener) {
        // Without this, an un-enabled accessibility service means dial()/inject()/choose()
        // all silently no-op on a null UssdAccessibilityService.instance — the Executing
        // screen just spins forever with no feedback. Fail loudly and specifically instead.
        if (UssdAccessibilityService.instance == null) {
            listener.onError(
                "Menu reading isn't turned on yet. Go to the home screen, tap " +
                    "\"Turn on menu reading\", enable KASA in Accessibility settings, then try again."
            )
            return
        }
        val slots = buildMap {
            intent.amount?.let { put("amount", it.toMoneyString()) }
            intent.recipient?.number?.let { put("recipient_number", it) }
            (intent.extra["bundle"] as? String)?.let { put("bundle", it) }
            (intent.extra["reference"] as? String)?.let { put("reference", it) }
        }
        val service = AccessibilityUssdService()
        UssdNavigator(flows, detect, service, listener).run(intent.action, slots)
    }
}
