# 04 · USSD ENGINE GUIDE — Frederick (Lead)

**Your mission:** the module everything else depends on and the thing no other team has — safely driving the mobile-money USSD menus, and stopping dead at the PIN so the user enters it themselves. If this works, we have a winner. If it's fragile, we fall back gracefully. Either way, you decide on **evidence by Day 3**, not hope.

> **K11–K13 built:** `app/BalanceChecker.kt` (single-shot balance), `app/UssdAccessibilityService.kt` (interactive drive + PIN detection), `res/xml/ussd_service_config.xml`, the GO/NO-GO framework and assisted-mode fallback in `08_K11_K13_SPIKE.md`, and `diagrams/k13_go_no_go.png`. Remaining: run the spike on two phones and fill the decision record.



> **K10 done (design + map + engine):** the navigation is now label-driven — see `config/ussd_scripts.json`, `app/UssdNavigator.kt`, `07_K10_USSD_MAPPING_GUIDE.md` and `diagrams/k10_menu_tree.png`. Your remaining K10 job is to dial `*170#` and verify every label/number on two real phones, then flip `_verification.status` to VERIFIED.



> **K05 update (Day 1 design session):** PIN entry = native USSD dialog (Option A). During the spike, verify TalkBack reads the native PIN dialog on both test phones. Never inject at the PIN step. See `06_K05_DESIGN_DECISIONS.md`.



**You own:** the USSD spike, the real menu-tree mapping, the `sendUssdRequest` balance path, the `AccessibilityService` interactive engine, the PIN hand-off, the state machine, and the go/no-go call. Plus architecture and integration across the team.
**The contract you implement:** the `UssdEngine` / `UssdListener` interface in `00_START_HERE.md`. Richmond calls it; you fulfil it. **The PIN never crosses this interface.**

Start file in the repo: `../app/UssdSpike.kt`. Read it — it has both mechanisms sketched.

**Non-negotiable:** you need a **real Android phone with an MTN SIM** in your hands from Day 1. USSD does not run on an emulator. If you don't have one, that's the first blocker to solve, before any code.

---

## Layer by layer

### Step 1 — Map the real menu tree (Day 1)

Before automating anything, dial `*170#` yourself and **write down the exact menu tree**: every option number for balance, transfer, buy data, and where the PIN prompt appears and exactly what text it shows. Put the real digits into `../config/ussd_scripts.json` (the ones there now are placeholders). This half-hour of manual mapping saves days of guessing.

### Step 2 — The easy win: balance via `sendUssdRequest` (Day 1)

Official API, reliable for single-shot codes. From `UssdSpike.kt`:

```kotlin
tm.sendUssdRequest(balanceCode, object : TelephonyManager.UssdResponseCallback() {
    override fun onReceiveUssdResponse(t: TelephonyManager, req: String, resp: CharSequence) {
        listener.onSuccess(resp.toString())   // -> app speaks it in Twi
    }
    override fun onReceiveUssdResponseFailed(t: TelephonyManager, req: String, code: Int) {
        listener.onError("Balance check failed ($code)")
    }
}, Handler(Looper.getMainLooper()))
```

Requires `CALL_PHONE` granted at runtime. **Done when:** the real balance is read aloud in Twi on your phone. That alone is a demo-worthy accessibility win by end of Day 1.

### Step 3 — The hard part: interactive menus via AccessibilityService (Days 2–3)

Multi-step flows (send money) can't be driven by `sendUssdRequest` — you need the `AccessibilityService` that reads the USSD dialog and injects each response. The skeleton is in `UssdSpike.kt` (commented block). To bring it live:

1. Register the service in the manifest and add `res/xml/ussd_service_config.xml`. Set `packageNames` to the phone/dialer package that renders USSD dialogs — **discover this during the spike** (it varies by OEM; log the package of the window that appears).
2. On each USSD dialog: read the text from the node tree, call `listener.onMenuRead(text)` so the app speaks it, then inject the next scripted response (`ACTION_SET_TEXT` + click Send).
3. **Detect the PIN prompt** (match against `pin_prompt_markers` in the config). When you see it: **do not inject.** Call `listener.onPinRequired()` and stop. The app shows the keypad; the user types; the carrier session completes. You never touch the digits.

**Test on at least two different phones** — this is the fragile part and OEM differences are where it breaks.

### Step 4 — Go / No-Go (end of Day 3) — write the criterion down NOW

- **GO** → balance works, AND the accessibility service reliably drives the first 2–3 send-money steps and detects the PIN prompt, on 2 different phones. → Build the full send-money + buy-data flows.
- **NO-GO** → interactive automation is too flaky in the time we have. → **Assisted mode:** the app reads every USSD menu aloud in Twi and the user drives by voice/tap. Still a large, demo-worthy win for a blind user, and far more robust. Switch that day; don't sink a week chasing it.

Decide on the evidence in front of you. Tell the team the moment you know.

### Step 5 — The state machine (Days 4–8, if GO)

Turn a confirmed `Intent` into a scripted USSD run:

```kotlin
class RealUssdEngine(private val scripts: UssdScripts) : UssdEngine {
    override fun execute(intent: Intent, listener: UssdListener) {
        val flow = scripts.flowFor(intent.action) ?: return listener.onError("Unsupported action")
        val steps = flow.render(intent)         // fill {recipient_number},{amount} from the Intent
        AccessibilityBridge.run(
            root = scripts.root,                // *170#
            steps = steps,
            onMenu = listener::onMenuRead,
            onPin  = listener::onPinRequired,   // hand off, do not inject
            onDone = listener::onSuccess,
            onFail = listener::onError
        )
    }
}
```

- **Fail closed:** if a dialog doesn't match what you expect, abort and call `onError` with a clear reason. Never blindly push a response into an unexpected screen when money is involved.
- Nothing is "successful" until the carrier's own success text confirms it (match `success_markers`).

### Step 6 — Integrate and lead (Days 6–15)

- Give Richmond your `RealUssdEngine` to drop in place of `FakeUssdEngine`. Same interface → no UI change.
- Run the daily standup. Watch the integration seam between server, app and engine — that's where time gets lost.
- Own the demo narrative and the backup recording (Day 13–15).

---

## How you integrate

- **With Richmond:** you implement the interface he's already coding against. When yours is ready, he swaps the fake for the real one. Coordinate the PIN-handoff UX with him and the accessibility lead.
- **With Selorm:** you consume his `Intent`. If the fields your engine needs aren't there, that's a schema conversation — change `../config/intent_schema.json` by agreement, tell everyone.
- **With the accessibility lead:** the PIN-entry mechanism is his call (native dialog vs mirrored keypad). Close it in the first design session.

## Your day-by-day

- **Day 1:** real phone+SIM secured; menu tree mapped into config; balance path reading aloud.
- **Day 2–3:** accessibility service driving interactive steps; PIN detection; **Go/No-Go**.
- **Day 4–8:** state machine; send-money + buy-data (or assisted mode); integrate with Richmond.
- **Day 9–12:** harden fail-closed paths; test on multiple phones.
- **Day 13–15:** backup demo recording; rehearse; lead the room.
