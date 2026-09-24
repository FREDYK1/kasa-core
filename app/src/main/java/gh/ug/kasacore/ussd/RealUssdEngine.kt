package gh.ug.kasacore.ussd

import android.content.Context
import gh.ug.kasacore.model.Intent
import gh.ug.kasacore.model.Network
import gh.ug.kasacore.model.toGhanaLocalNumber
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
        val number = intent.recipient?.number?.toGhanaLocalNumber()
        val network = Network.fromNumber(number)
        if (intent.action == Intent.SEND_MONEY && network == null) {
            // The route through the menus depends on the network, so never guess it.
            listener.onError(
                "I can't tell which network that number is on. Use a 10-digit MTN, Telecel or AT " +
                    "number, like 0244123456."
            )
            return
        }
        val slots = buildMap<String, String> {
            intent.amount?.let { put("amount", it.toMoneyString()) }
            number?.let { put("recipient_number", it) }
            // Flags that switch the network-specific steps in ussd_scripts.json on/off.
            when (network) {
                Network.TELECEL -> { put("other_network", "true"); put("telecel", "true") }
                Network.AT -> { put("other_network", "true"); put("at", "true") }
                Network.MTN, null -> {}
            }
            // Never blank: the USSD reference prompt wants something typed. "1" is the verified default.
            put("reference", (intent.extra["reference"] as? String)?.trim().orEmpty().ifEmpty { "1" })
        }
        val service = AccessibilityUssdService()
        UssdNavigator(flows, detect, service, listener).run(intent.action, slots)
    }
}
