# 03 · FRONTEND (ANDROID) GUIDE — Richmond

**Your mission:** build the app a blind person can actually use. The interface is the product here — if it isn't usable without sight, nothing else matters.

> **PIN audio (K11–K13):** in the accessibility-service onboarding, add a step that confirms TalkBack "Speak passwords" is OFF (so the native PIN dialog does not read digits aloud) and suggests an earpiece. Never build a keypad that captures PIN digits. See `08_K11_K13_SPIKE.md`.



> **K05 update (Day 1 design session):** wake = one big full-width button (no voice-wake). Recipient is resolved **on-device** against a local trusted-payees list (build a small accessible "add a payee" flow). At the PIN step, speak a prompt + haptic and show nothing that captures digits — the user types into the native USSD dialog. See `06_K05_DESIGN_DECISIONS.md`.



**You own:** the native Android app (Kotlin) — screens, accessibility, audio capture, the network layer to the server, the confirm flow, the symbol board, and the accessible PIN keypad.
**You do NOT own:** the USSD engine internals (Frederick — you call his interface), or the server internals (Selorm — you call his API).

**Contracts you honour:** the HTTP API (4.2) and the `UssdEngine` interface (4.3) in `00_START_HERE.md`.

**Golden rule from Prof. Naami:** accessibility is built in from the first screen, never added at the end. Turn TalkBack ON on your test phone and leave it on while you develop. If *you* can't complete a task with the screen off, neither can your user.

---

## Layer by layer

### Step 1 — Project skeleton (Day 1)

- Android Studio → new **Kotlin** project, Empty Activity.
- `minSdk = 26` (required for USSD), `targetSdk` latest.
- Add permissions to `AndroidManifest.xml`: `RECORD_AUDIO`, `CALL_PHONE`, `INTERNET`, `READ_CONTACTS`.
- Add libraries: Retrofit + OkHttp (networking), Coroutines. Media recorder is built in.

**Done when:** the app installs and runs on your real phone.

### Step 2 — The network layer, against a FAKE server first (Day 1–2)

Define the API and a fake, so you never wait for Selorm:

```kotlin
interface KasaApi {
    @Multipart @POST("understand")
    suspend fun understand(@Part audio: MultipartBody.Part): Intent

    @GET("health") suspend fun health(): Map<String, String>
}

// Fake for building UI before the server is ready:
class FakeApi {
    fun cannedIntent() = Intent("send_money", 5.0,
        Recipient("Kofi", null, null),   // server never resolves this — you do, from PayeesRepository
        emptyMap(), 0.95, true, "fa cedi enum kɔma Kofi", "tw")
}
```

No contacts payload — the server never sees your payees. Resolve `recipient.raw` against `PayeesRepository` yourself after the call returns; see the K05 note above.

Swap `FakeApi` for the real Retrofit client (pointed at Selorm's ngrok URL) when he's ready. No UI change needed.

**Done when:** you can build the whole flow using canned Intents, with no server.

### Step 3 — The screens, each accessible from birth (Days 2–7)

Build these, and for **every** control set a `contentDescription`, large touch targets (min 48dp, bigger for us), contrast ≥ 4.5:1, and text that scales:

1. **Home** — one big "Speak" button + the symbol-board entry. TalkBack announces both clearly.
2. **Listening** — records audio (MediaRecorder, 16kHz mono wav — the format Kelvin expects). Haptic buzz on start/stop so a blind user knows it's listening.
3. **Confirm** — the heart of safety. Speak *and* display the summary: "Send 5 cedis to Kofi Mensah?" Two clear actions: approve / cancel. **Nothing proceeds without an explicit approve.** (This is WCAG 3.3.4.)
4. **PIN entry** — an accessible keypad (see Step 6). Appears only when the USSD engine calls `onPinRequired()`.
5. **Result** — speaks the outcome in Twi + shows a caption + offers the audio receipt.

For each spoken prompt, also render it as an on-screen caption — no information is audio-only (WCAG 1.3.3), which also serves deaf users.

### Step 4 — Wire the core flow (Days 6–8)

```
Home → tap/say Speak
  → record audio
  → POST /understand (audio only)   [Selorm]
  → get Intent (recipient.raw only — no contact resolved yet)
  → if confidence < 0.6 → speak "I didn't catch that, please try again" → back to record
  → else → resolve Intent.recipient against PayeesRepository (on-device)
  → Confirm screen (speak + show summary)
      → user approves
      → ussdEngine.execute(intent, listener)      [Frederick]
          onMenuRead  → speak menu in Twi + caption
          onPinRequired → show PIN keypad
          onSuccess → Result screen, speak result
          onError → speak clear error
```

Build this against `FakeUssdEngine` (returns scripted callbacks) until Frederick's real engine is ready.

```kotlin
class FakeUssdEngine : UssdEngine {
    override fun execute(intent: Intent, l: UssdListener) {
        l.onMenuRead("Enter amount")
        l.onPinRequired()
        // (test harness resumes) then:
        l.onSuccess("You have sent GH₵5 to Kofi Mensah. Balance 42.10.")
    }
}
```

### Step 5 — The symbol board (Days 9–11)

A grid of large, high-contrast symbols (send, balance, data, amounts, contacts) for users who cannot speak. Tapping symbols builds the **same `Intent` object** and enters the **same confirm flow**. Do not fork the logic — both inputs converge. This is what earns the "adaptable beyond one disability group" score.

### Step 6 — Accessibility polish (Days 9–12)

- **TalkBack:** navigate the entire app with it on, eyes closed. Fix anything that announces "button, button".
- **PIN keypad:** large keys, spoken/haptic feedback per key press, **never announces the digit aloud** (privacy). The PIN goes to the USSD session only — you never store or send it.
- **No timeout traps** (WCAG 2.2.1): confirmation and PIN screens do not expire under the user; give a generous, extendable window.
- **High contrast + scalable text** everywhere.
- **Onboarding to enable the AccessibilityService** (Frederick's engine needs it): a guided, TalkBack-friendly screen that walks the user to the Settings toggle.

---

## How you integrate

- **With Selorm:** you call `/understand` and `/health`. Agree the audio format (16kHz mono wav) — no contacts payload, his server never receives one. Build against `FakeApi` until his URL is live.
- **With Kelvin:** he defines the audio in/out format; match it. He may hand you pre-recorded prompt audio to bundle in the app for zero-latency playback.
- **With Frederick:** you call `UssdEngine.execute(...)` and react to the `UssdListener` callbacks. Build against `FakeUssdEngine` until his real one lands, then swap — no UI change.
- **With the accessibility lead:** he tests every screen with you. Change what he can't use. Keep a note of each change — it's your evidence for the mentors.

## Your day-by-day

- **Day 1:** project runs on device; permissions; `FakeApi` + `FakeUssdEngine` in place.
- **Day 2–5:** Home, Listening, Confirm, Result screens — each TalkBack-clean.
- **Day 6–8:** wire the real flow; integrate with Selorm's server; end-to-end with Frederick's engine.
- **Day 9–11:** symbol board feeding the same Intent.
- **Day 12:** PIN keypad, no-timeout, onboarding, full TalkBack pass.
- **Day 13–15:** test with blind users, fix, rehearse.
