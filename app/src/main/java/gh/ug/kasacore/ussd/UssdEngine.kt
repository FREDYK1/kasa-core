package gh.ug.kasacore.ussd

import gh.ug.kasacore.model.Intent

/*
 * The seam between the app UI and the USSD module — 00_START_HERE.md §4.3,
 * verbatim. Richmond calls it; Frederick implements it (RealUssdEngine).
 * The PIN never crosses this interface: onPinRequired() is the only mention
 * of it, meaning "the app takes over so the user can type." The engine never
 * sees the digits.
 */
interface UssdEngine {
    fun execute(intent: Intent, listener: UssdListener)
}

interface UssdListener {
    fun onMenuRead(text: String)       // a USSD menu appeared -> app speaks it in Twi + captions it
    fun onPinRequired()                // app shows the hand-off screen; the engine is paused
    fun onSuccess(resultText: String)  // final result -> app speaks it in Twi
    fun onError(reason: String)        // fail closed -> app speaks a clear, actionable error
}
