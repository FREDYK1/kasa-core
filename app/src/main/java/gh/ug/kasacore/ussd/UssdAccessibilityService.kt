package gh.ug.kasacore.ussd

/*
 * K12 — the AccessibilityService that DRIVES interactive USSD menus, and the bridge that
 * lets UssdNavigator (K10) plug into it. It reads each USSD dialog, hands the text to the
 * navigator, and performs the navigator's chosen action (inject / choose option) — EXCEPT
 * the PIN step, which it never touches.
 *
 * Register in AndroidManifest (see AndroidManifest_snippet.xml) with res/xml/ussd_service_config.xml.
 * The user enables it once in Settings (build an accessible onboarding for that — Richmond).
 *
 * This is real, on-device code. It must be TUNED on the two spike phones: the dialer package
 * that renders USSD dialogs (packageNames), the Send/OK button label, and the editable field.
 */

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class UssdAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingDispatch: Runnable? = null
    private var lastDispatchedText: String? = null

    override fun onServiceConnected() { instance = this }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }
    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // SPIKE STEP 1: discover which package renders USSD dialogs on this phone.
        // Log it once, then set it in ussd_service_config.xml packageNames.
        // android.util.Log.d("KASA", "pkg=" + event?.packageName)

        // Android fires several content-changed events per dialog as it renders/settles —
        // acting on the first one alone reads a half-built screen (garbled/truncated text),
        // and acting on every one sends the same input more than once, landing on whatever
        // screen the session has already moved to by the time the second firing runs ("Incorrect
        // choice, try again" on real MTN). Debounce to let the screen settle, then dedupe
        // against the text we just handled, so each real screen gets exactly one dispatch.
        pendingDispatch?.let { handler.removeCallbacks(it) }
        val dispatch = Runnable {
            val root = rootInActiveWindow ?: return@Runnable
            currentRoot = root
            val text = collectText(root)
            if (text.isBlank() || text == lastDispatchedText) return@Runnable
            lastDispatchedText = text
            UssdBridge.dialogListener?.invoke(text)   // navigator decides what to do next
        }
        pendingDispatch = dispatch
        handler.postDelayed(dispatch, DEBOUNCE_MS)
    }

    // ---- actions the navigator asks for (via UssdBridge / AccessibilityUssdService) ----

    fun dialUssd(code: String) {
        lastDispatchedText = null   // a fresh session — don't let a prior run's last screen suppress this one
        // # must be URL-encoded as %23 or the dialer drops it.
        val uri = Uri.parse("tel:" + Uri.encode(code))
        startActivity(Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun inject(value: String) {
        val field = findEditable(currentRoot) ?: return
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        clickSend(currentRoot)
    }

    fun hasInputField(): Boolean = findEditable(currentRoot) != null

    fun choose(optionNumber: String) = inject(optionNumber)   // USSD menus take the number as input
    fun skip() = clickSend(currentRoot)                       // send empty for optional fields

    /**
     * Verified on a real MTN SIM: after a result (success/failure/timeout) the phone
     * leaves a native "Cancel/Send"-style dialog on screen. Tap Cancel to dismiss it so
     * the user isn't stuck looking at a stale system dialog. Only called from
     * UssdNavigator.finish() — never while a PIN prompt is showing, so this can't
     * interfere with "or 2 to cancel" phrasing that appears inside the PIN screen's text.
     */
    fun dismissResultDialog() {
        findByText(currentRoot, listOf("cancel"))?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    // ---- helpers (tune on device) ----

    private fun collectText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = StringBuilder()
        // Skip the reply field's own text: it's what WE just typed, so including it makes the
        // same screen look "new" after every inject and defeats the dedupe in onAccessibilityEvent.
        if (!node.isEditable) {
            node.text?.let { sb.append(it).append('\n') }
            node.contentDescription?.let { sb.append(it).append('\n') }
        }
        for (i in 0 until node.childCount) sb.append(collectText(node.getChild(i)))
        return sb.toString().trim()
    }

    private fun findEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) findEditable(node.getChild(i))?.let { return it }
        return null
    }

    private fun clickSend(node: AccessibilityNodeInfo?) {
        // Find the SEND / OK button by text and click it. Button label varies by OEM — verify.
        val target = findByText(node, listOf("send", "ok", "reply")) ?: return
        target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun findByText(node: AccessibilityNodeInfo?, words: List<String>): AccessibilityNodeInfo? {
        if (node == null) return null
        val t = (node.text?.toString() ?: "").lowercase()
        if (node.isClickable && words.any { t.contains(it) }) return node
        for (i in 0 until node.childCount) findByText(node.getChild(i), words)?.let { return it }
        return null
    }

    companion object {
        @Volatile var instance: UssdAccessibilityService? = null
        private var currentRoot: AccessibilityNodeInfo? = null
        private const val DEBOUNCE_MS = 350L
    }
}

/* Bridge: lets the navigator register a dialog listener and receive a UssdService. */
object UssdBridge { @Volatile var dialogListener: ((String) -> Unit)? = null }

/** The UssdService (from K10) backed by the live AccessibilityService. */
class AccessibilityUssdService : UssdService {
    override fun dial(code: String) { UssdAccessibilityService.instance?.dialUssd(code) }
    override fun onDialog(handler: (String) -> Unit) { UssdBridge.dialogListener = handler }
    override fun inject(text: String) { UssdAccessibilityService.instance?.inject(text) }
    override fun choose(option: String) { UssdAccessibilityService.instance?.choose(option) }
    override fun skip() { UssdAccessibilityService.instance?.skip() }
    override fun hasInput(): Boolean = UssdAccessibilityService.instance?.hasInputField() ?: false
    override fun dismiss() { UssdAccessibilityService.instance?.dismissResultDialog() }
    override fun end() { UssdBridge.dialogListener = null }
}
