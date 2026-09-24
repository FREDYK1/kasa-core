package gh.ug.kasacore

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import gh.ug.kasacore.model.CONFIDENCE_FLOOR
import gh.ug.kasacore.model.Intent
import gh.ug.kasacore.model.Network
import gh.ug.kasacore.model.Payee
import gh.ug.kasacore.model.Recipient
import gh.ug.kasacore.model.toGhanaLocalNumber
import gh.ug.kasacore.model.forSpeech
import gh.ug.kasacore.model.toMoneyString
import gh.ug.kasacore.payees.PayeesRepository
import gh.ug.kasacore.tts.TwiSpeaker
import gh.ug.kasacore.ussd.RealUssdEngine
import gh.ug.kasacore.ussd.UssdEngine
import gh.ug.kasacore.ussd.UssdListener
import gh.ug.kasacore.audio.WavRecorder
import gh.ug.kasacore.network.FakeApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/*
 * Orchestrates the one flow the whole system exists to run — the "chain in
 * one sentence" from the master task tracker:
 *   voice -> /understand -> Intent -> resolve payee on-device -> confirm ->
 *   USSD engine -> user PIN -> result read back.
 *
 * The USSD engine defaults to RealUssdEngine (K12), which drives the live
 * AccessibilityService. Per K13, if that proves flaky on a given demo phone,
 * swap `ussdEngine` for FakeUssdEngine or an assisted-mode implementation —
 * this ViewModel doesn't change either way (that's the point of the
 * UssdEngine seam).
 */
sealed interface KasaScreen {
    data object Home : KasaScreen
    data object Listening : KasaScreen
    data class Confirm(val intent: Intent, val summary: String) : KasaScreen
    data object Executing : KasaScreen
    data class PinHandoff(val caption: String, val review: String) : KasaScreen
    data class Result(val caption: String) : KasaScreen
    data class ErrorScreen(val message: String) : KasaScreen
    data object SymbolBoard : KasaScreen
    data object AddPayee : KasaScreen
    data object Onboarding : KasaScreen
}

class KasaViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx: Context get() = getApplication()

    private val payees = PayeesRepository(ctx)
    private val speaker = TwiSpeaker(ctx)
    private val recorder = WavRecorder(ctx)
    private val api = FakeApi()
    private val ussdEngine: UssdEngine = RealUssdEngine(ctx)

    private val _screen = MutableStateFlow<KasaScreen>(KasaScreen.Home)
    val screen: StateFlow<KasaScreen> = _screen.asStateFlow()

    private val _caption = MutableStateFlow("")
    val caption: StateFlow<String> = _caption.asStateFlow()

    private var currentRecordingFile: File? = null

    private fun say(text: String, append: Boolean = false) {
        _caption.value = text // WCAG 1.3.3 — every spoken string is also a caption
        speaker.speak(text.forSpeech(), append)
    }

    // ---- Home -> Listening -> Understand -------------------------------------

    fun startListening() {
        _screen.value = KasaScreen.Listening
        currentRecordingFile = recorder.start()
        vibrate(40)
    }

    fun stopListeningAndUnderstand() {
        recorder.stop()
        vibrate(40)
        val file = currentRecordingFile
        if (file == null) {
            failToHome("I couldn't hear that. Please try again.")
            return
        }
        viewModelScope.launch {
            try {
                val part = MultipartBody.Part.createFormData(
                    "audio", file.name, file.asRequestBody("audio/wav".toMediaTypeOrNull())
                )
                val intent = api.understand(part)
                onIntentReceived(intent)
            } catch (e: Exception) {
                failToHome("I couldn't reach the server. Check your connection and try again.")
            }
        }
    }

    private fun onIntentReceived(rawIntent: Intent) {
        if (rawIntent.confidence < CONFIDENCE_FLOOR || rawIntent.action == Intent.UNKNOWN) {
            failToHome("I didn't catch that clearly. Please try again.")
            return
        }
        val resolved = rawIntent.copy(recipient = payees.resolve(rawIntent.recipient))
        goToConfirm(resolved)
    }

    private fun goToConfirm(intent: Intent) {
        val summary = buildSummary(intent)
        _screen.value = KasaScreen.Confirm(intent, summary)
        say(summary)
    }

    private fun buildSummary(intent: Intent): String = when (intent.action) {
        Intent.SEND_MONEY -> {
            val who = intent.recipient?.matched_contact
                ?: intent.recipient?.raw?.let { "an unconfirmed contact: $it — please add them first" }
                ?: "an unknown contact"
            // Say the network and reference back: the route through the menus depends on the network,
            // and the user should hear what will actually be typed before approving.
            val network = Network.fromNumber(intent.recipient?.number?.toGhanaLocalNumber())?.let { " on ${it.label}" }.orEmpty()
            val reference = (intent.extra["reference"] as? String)?.let { ", reference $it" }.orEmpty()
            "Send GH₵${fmt(intent.amount)} to $who$network$reference. Confirm?"
        }
        Intent.CHECK_BALANCE -> "Check your MoMo wallet balance. Confirm?"
        Intent.BUY_DATA -> "Buy GH₵${fmt(intent.amount)} of data for yourself. Confirm?"
        Intent.BUY_AIRTIME -> "Buy GH₵${fmt(intent.amount)} of airtime. Confirm?"
        Intent.CASH_OUT -> "Cash out GH₵${fmt(intent.amount)}. Confirm?"
        Intent.PAY_MERCHANT -> "Pay a merchant GH₵${fmt(intent.amount)}. Confirm?"
        else -> "Repeat that action. Confirm?"
    }

    private fun fmt(amount: Double?) = amount?.toMoneyString() ?: "0"

    fun cancel() {
        _screen.value = KasaScreen.Home
        say("Cancelled.")
    }

    // ---- Confirm -> execute ---------------------------------------------------

    fun approve(intent: Intent) {
        _screen.value = KasaScreen.Executing
        val listener = object : UssdListener {
            var lastMenuText = ""
            override fun onMenuRead(text: String) {
                lastMenuText = text
                say(text)
            }
            override fun onAction(description: String) {
                // Queue behind the menu that was just read so the user hears "menu, then what I did".
                say(description, append = true)
            }
            override fun onPinRequired() {
                vibrate(80)
                val message = ctx.getString(
                    if (intent.action == Intent.SEND_MONEY) R.string.pin_handoff_send_money
                    else R.string.pin_handoff_generic
                )
                // The screen just before this is the review (amount, fee, tax) — keep it on screen and
                // let it finish being read, THEN ask for the PIN, instead of cutting it off.
                _screen.value = KasaScreen.PinHandoff(caption = message, review = lastMenuText)
                say(message, append = true)
            }
            override fun onSuccess(resultText: String) {
                _screen.value = KasaScreen.Result(resultText)
                say(resultText)
                viewModelScope.launch { postReceipt(intent, "success") }
            }
            override fun onError(reason: String) {
                _screen.value = KasaScreen.ErrorScreen(reason)
                say(reason)
                viewModelScope.launch { postReceipt(intent, "error") }
            }
        }
        ussdEngine.execute(intent, listener)
    }

    private suspend fun postReceipt(intent: Intent, status: String) {
        runCatching {
            // Fire-and-forget audit trail — 00_START_HERE §4, NEVER the PIN, NEVER raw audio.
            okhttp3.OkHttpClient().newCall(
                okhttp3.Request.Builder()
                    .url(BuildConfig.SERVER_BASE_URL.trimEnd('/') + "/receipt")
                    .post(
                        com.google.gson.Gson().toJson(
                            mapOf(
                                "action" to intent.action,
                                "amount" to intent.amount,
                                "recipient" to mapOf("matched_contact" to intent.recipient?.matched_contact),
                                "result_status" to status,
                            )
                        ).toRequestBody("application/json".toMediaTypeOrNull())
                    ).build()
            ).execute().close()
        }
    }

    fun backToHome() {
        _screen.value = KasaScreen.Home
    }

    // ---- Symbol board — same Intent, same confirm flow -------------------------

    fun openSymbolBoard() { _screen.value = KasaScreen.SymbolBoard }

    fun symbolTapped(action: String, amount: Double?, recipientNumber: String?, reference: String? = null) {
        // The symbol board takes a typed number directly — there's no name to
        // resolve, so matched_contact just carries the number itself (see
        // SymbolBoardScreen's doc comment for why this bypasses PayeesRepository).
        val intent = Intent(
            action = action,
            amount = amount,
            recipient = recipientNumber?.let {
                Recipient(raw = it, matched_contact = it, number = it)
            },
            extra = reference?.let { mapOf("reference" to it) } ?: emptyMap(),
            confidence = 1.0,
            needs_confirmation = true,
            transcript = "[symbol board]",
            language = "tw",
        )
        goToConfirm(intent)
    }

    // ---- Payees ------------------------------------------------------------

    fun openAddPayee() { _screen.value = KasaScreen.AddPayee }

    fun savePayee(name: String, number: String) {
        if (name.isNotBlank() && number.isNotBlank()) payees.add(Payee(name.trim(), number.trim()))
        _screen.value = KasaScreen.Home
    }

    // ---- Onboarding ----------------------------------------------------------

    fun openOnboarding() { _screen.value = KasaScreen.Onboarding }

    private fun failToHome(message: String) {
        _screen.value = KasaScreen.Home
        say(message)
    }

    private fun vibrate(ms: Long) {
        val vibrator = ctx.getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    override fun onCleared() {
        speaker.shutdown()
        super.onCleared()
    }
}
