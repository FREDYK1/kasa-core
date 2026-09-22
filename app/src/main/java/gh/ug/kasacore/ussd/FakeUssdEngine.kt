package gh.ug.kasacore.ussd

import gh.ug.kasacore.model.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Build and demo the whole app flow before RealUssdEngine is verified on a
 * device — 03_FRONTEND_ANDROID_GUIDE.md Step 4. Also the ASSISTED MODE demo
 * safety net's building block if the accessibility service proves flaky on
 * stage (docs/08_K11_K13_SPIKE.md, K13).
 */
class FakeUssdEngine(private val scope: CoroutineScope) : UssdEngine {
    override fun execute(intent: Intent, listener: UssdListener) {
        scope.launch(Dispatchers.Default) {
            delay(500)
            listener.onMenuRead("MTN MoMo\n1. Transfer Money\n2. Airtime & Data\n5. My Wallet")
            delay(800)
            when (intent.action) {
                Intent.CHECK_BALANCE -> {
                    listener.onPinRequired()
                    // resumed externally once the user has typed the PIN into the system dialog
                }
                else -> {
                    val name = intent.recipient?.matched_contact ?: intent.recipient?.raw ?: "your contact"
                    listener.onMenuRead("Send GH₵${intent.amount ?: 0} to $name. Fee GH₵0.00. 1 Confirm")
                    delay(600)
                    listener.onPinRequired()
                }
            }
        }
    }

    /** Call once the fake PIN entry has "completed", to unpause the scripted flow. */
    fun resumeWithSuccess(listener: UssdListener, resultText: String) {
        scope.launch(Dispatchers.Default) {
            delay(700)
            listener.onSuccess(resultText)
        }
    }
}
