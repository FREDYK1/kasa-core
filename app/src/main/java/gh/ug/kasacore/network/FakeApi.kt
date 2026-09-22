package gh.ug.kasacore.network

import gh.ug.kasacore.model.Intent
import gh.ug.kasacore.model.Recipient
import kotlinx.coroutines.delay

/**
 * Build the whole app against this before Selorm's server is reachable
 * (03_FRONTEND_ANDROID_GUIDE.md Step 2). Swap for ApiClient.kasaApi — no UI change.
 */
class FakeApi {
    suspend fun understand(): Intent {
        delay(600) // pretend network + ASR latency, so loading states are honestly exercised
        return cannedIntent()
    }

    fun cannedIntent() = Intent(
        action = Intent.SEND_MONEY,
        amount = 5.0,
        recipient = Recipient(raw = "Kofi", matched_contact = null, number = null),
        extra = emptyMap(),
        confidence = 0.95,
        needs_confirmation = true,
        transcript = "fa cedi enum kɔma Kofi",
        language = "tw",
    )
}
