package gh.ug.kasacore

/*
 * KASA Core — USSD spike (Kotlin).
 *
 * PURPOSE: answer the riskiest question in days 1-3 on a REAL Android phone with an MTN SIM.
 * This is a starting skeleton to run on-device, not a tested build. Emulators cannot do USSD.
 *
 * Two mechanisms:
 *   1) sendUssdRequest  -> reliable for SINGLE-SHOT codes (balance). Official, API 26+.
 *   2) AccessibilityService -> drives INTERACTIVE menu sessions (send money) by reading the
 *      USSD dialog and injecting responses, then HANDING OFF to the user at the PIN step.
 *
 * Permissions (AndroidManifest): CALL_PHONE. The AccessibilityService must be enabled by the
 * user in Settings (build an accessible onboarding flow that walks them there).
 */

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager

// ---------- 1. SINGLE-SHOT: balance check (build this first, it will just work) ----------

class BalanceChecker(private val context: Context) {

    /** Requires CALL_PHONE permission granted at runtime. */
    fun checkBalance(ussdCode: String, onResult: (String) -> Unit, onError: (String) -> Unit) {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        try {
            tm.sendUssdRequest(
                ussdCode, // e.g. a direct balance short-code; verify the real one on the SIM
                object : TelephonyManager.UssdResponseCallback() {
                    override fun onReceiveUssdResponse(t: TelephonyManager, req: String, resp: CharSequence) {
                        onResult(resp.toString())      // -> feed to Twi TTS to read aloud
                    }
                    override fun onReceiveUssdResponseFailed(t: TelephonyManager, req: String, failureCode: Int) {
                        onError("USSD failed, code=$failureCode")
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (e: SecurityException) {
            onError("Missing CALL_PHONE permission: ${e.message}")
        }
    }
}

// ---------- 2. INTERACTIVE: drive the send-money menu via AccessibilityService ----------
/*
 * Register in AndroidManifest as an accessibilityService and provide res/xml/ussd_service_config.xml
 * with android:accessibilityEventTypes="typeWindowContentChanged|typeWindowStateChanged"
 * and android:packageNames set to the phone/dialer package(s) that render USSD dialogs
 * (varies by OEM — discover this during the spike).
 *
 * The service holds a queue of responses to inject, popped one per USSD dialog, EXCEPT the PIN
 * step which is deliberately left to the user.
 */

/*
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle

class UssdAccessibilityService : AccessibilityService() {

    companion object {
        // The USSD engine fills this before dialling. Nulls mark "hand off to user" (the PIN).
        @Volatile var pendingResponses: ArrayDeque<String?> = ArrayDeque()
        @Volatile var onDialogText: ((String) -> Unit)? = null   // for reading menus aloud + logging
        val pinMarkers = listOf("PIN", "pin", "hyɛ wo PIN")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        val dialogText = collectText(root)
        if (dialogText.isBlank()) return
        onDialogText?.invoke(dialogText)                 // read this menu to the user in Twi

        if (pinMarkers.any { dialogText.contains(it, ignoreCase = true) }) {
            // PIN prompt: STOP. Do not inject. Surface the accessible keypad and let the user type.
            // The PIN never enters our process. This is the whole trust guarantee.
            return
        }

        val next = pendingResponses.removeFirstOrNull() ?: return
        if (next == null) return                          // explicit hand-off marker
        typeAndSend(root, next)
    }

    private fun collectText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = StringBuilder()
        node.text?.let { sb.append(it).append(' ') }
        for (i in 0 until node.childCount) sb.append(collectText(node.getChild(i)))
        return sb.toString().trim()
    }

    private fun typeAndSend(root: AccessibilityNodeInfo, response: String) {
        val input = findEditable(root) ?: return
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, response)
        }
        input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        // Then click the dialog's SEND/OK button — find by text ("Send"/"OK") and performAction(ACTION_CLICK).
    }

    private fun findEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) findEditable(node.getChild(i))?.let { return it }
        return null
    }

    override fun onInterrupt() {}
}
*/

/*
 * DAY-3 GO/NO-GO:
 *   GO  -> balance works via sendUssdRequest AND the accessibility service reliably drives at
 *          least the first 2-3 send-money steps and detects the PIN prompt, on 2 different phones.
 *   NO  -> fall back to ASSISTED MODE: read every USSD menu aloud in Twi and let the user drive
 *          by voice/tap. Still a real accessibility win; decide on evidence, not hope.
 */
