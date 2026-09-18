package gh.ug.kasacore.ussd

/*
 * K11 — single-shot USSD (the easy, PIN-free win).
 *
 * IMPORTANT REFRAME (from K10): MoMo WALLET balance sits inside a menu and needs the PIN,
 * so it goes through the interactive navigator (K12), not here. What sendUssdRequest is
 * genuinely good for is PIN-FREE single-shot codes: airtime balance and data balance.
 * That is still a real, demo-able accessibility feature — "check my airtime balance" by
 * voice, read back in Twi, instantly, no PIN. Ship it as a bonus; route MoMo balance
 * through K12.
 *
 * Needs CALL_PHONE (request at runtime). API 26+. Real device only (no emulator USSD).
 */

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

class BalanceChecker(
    private val context: Context,
    private val tts: TwiTts          // Kelvin's Twi text-to-speech
) {
    /**
     * ussdCode: a PIN-free single-shot code. VERIFY the current MTN Ghana codes on the SIM
     * during the spike (e.g. airtime-balance and data-balance short-codes) and put them in config.
     */
    fun check(ussdCode: String, onDone: (String) -> Unit, onError: (String) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) {
            return onError("CALL_PHONE permission not granted")
        }
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        try {
            tm.sendUssdRequest(
                ussdCode,
                object : TelephonyManager.UssdResponseCallback() {
                    override fun onReceiveUssdResponse(t: TelephonyManager, req: String, resp: CharSequence) {
                        val text = resp.toString()
                        tts.speak(text)          // read the balance aloud in Twi
                        onDone(text)
                    }
                    override fun onReceiveUssdResponseFailed(t: TelephonyManager, req: String, failureCode: Int) {
                        onError("USSD failed (code=$failureCode)")
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (e: SecurityException) {
            onError("SecurityException: ${e.message}")
        } catch (e: IllegalArgumentException) {
            onError("Bad USSD code: ${e.message}")
        }
    }
}

/** Implemented by Kelvin's speech module. */
interface TwiTts { fun speak(text: String) }
