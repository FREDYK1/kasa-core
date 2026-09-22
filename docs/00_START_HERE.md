# 00 · START HERE — Team Build Guide

**Read this first. Everyone. Before you write any code.**

I'm leading this build. My job is to give you clean seams so all four of you can build at the same time without waiting on each other. Your job is to build to the contracts in this document. If you honour the contracts, everything snaps together at integration.

---

## 1. What we are building (in one breath)

A native Android app for blind and low-vision users. The user speaks a command in Twi (or taps a symbol). The app understands it, confirms it out loud, then drives the MTN mobile-money USSD menus on their behalf — **stopping at the PIN, which the user always enters themselves.** The result is read back in Twi. We never store or transmit the PIN.

Full reasoning is in `../KASA_Core_Technical_Plan.md`. This folder is *how we build it*.

---

## 2. The four roles

| You | Role | You own | Guide |
|---|---|---|---|
| **Selorm** | Backend | The inference server: turns audio/text into a validated Intent | `01_BACKEND_GUIDE.md` |
| **Kelvin** | ML | Twi speech-in (ASR) and speech-out (TTS) behind clean functions | `02_ML_GUIDE.md` |
| **Richmond** | Frontend (Android) | The app: screens, accessibility, capture, confirm, symbol board | `03_FRONTEND_ANDROID_GUIDE.md` |
| **Frederick** | Lead + USSD engine | Architecture, and the riskiest module: driving USSD safely | `04_USSD_ENGINE_GUIDE.md` |
| **Accessibility lead** | Co-design + testing | Reviews every screen, tests with real users, owns the PIN-entry decision | see `05_INTEGRATION_AND_TESTING.md` |

Adjust names to real strengths, but **one person owns each layer.** Shared ownership is how hackathon teams die.

---

## 3. The one idea that lets us build in parallel: mock at the seams

Nobody waits for anybody. Each layer talks to the others through a fixed contract (Section 4). Until the real thing on the other side exists, you build against a **fake** that honours the same contract:

- Richmond builds the app against a **fake server** that returns canned Intents. He does not wait for Selorm.
- Selorm builds `/parse` and tests it with typed text. He does not wait for Kelvin's ASR.
- Kelvin improves ASR behind the same function signature. Nobody else changes.
- Frederick tests the USSD engine with **hardcoded Intents**. He does not wait for the app UI.

When the real pieces are ready, you swap the fake for the real one. Because the contract didn't change, it just works.

---

## 4. The contracts (memorise these — they are the law)

### 4.1 The shared data model: `Intent`

This is the single object that flows through the whole system. It is defined once in `../config/intent_schema.json`. Backend **produces** it; Frontend and the USSD engine **consume** it.

```json
{
  "action": "check_balance | send_money | buy_data | buy_airtime | cash_out | pay_merchant | unknown",
  "amount": 50,
  "recipient": { "raw": "Kofi", "matched_contact": "Kofi Mensah", "number": "0551234567" },
  "extra": {},
  "confidence": 0.92,
  "needs_confirmation": true,
  "transcript": "fa cedi aduonum kɔma Kofi",
  "language": "tw"
}
```

Kotlin mirror (Frontend + USSD use this exact class):

```kotlin
data class Recipient(val raw: String, val matched_contact: String?, val number: String?)
data class Intent(
    val action: String,
    val amount: Double?,
    val recipient: Recipient?,
    val extra: Map<String, Any> = emptyMap(),
    val confidence: Double,
    val needs_confirmation: Boolean,
    val transcript: String,
    val language: String
)
```

**Rule for everyone:** the Intent is *proposed*, never *executed*. The USSD engine only runs after the user explicitly approves in the confirm step. If `confidence < 0.6`, the app re-asks; it never guesses.

### 4.2 The HTTP API (App ↔ Server)

Base URL during the build: whatever ngrok prints (e.g. `https://xxxx.ngrok.io`).

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/health` | — | `{"status":"ok"}` |
| POST | `/transcribe` | multipart: `audio` (wav/m4a) | `{"transcript":"...", "language":"tw"}` |
| POST | `/parse` | json: `{"transcript":"...", "language":"tw"}` | an `Intent` |
| POST | `/understand` | multipart: `audio` | an `Intent` (this is `/transcribe` then `/parse`, one call) |

The app will normally call `/understand`. `/transcribe` and `/parse` stay separate so ML and Backend can test their halves independently.

**No contacts cross this API.** Per the K05 design session, recipient resolution is on-device: the server returns `recipient.raw` (the name or number it heard) with `matched_contact` always `null`; the app matches `raw` against its local trusted-payees list and refuses to guess if two are too close. See `06_K05_DESIGN_DECISIONS.md`.

### 4.3 The USSD engine interface (inside the Android app)

This is the seam between the app UI and the risky USSD module. Richmond calls it; Frederick implements it. Until the real one exists, Richmond uses `FakeUssdEngine`.

```kotlin
interface UssdEngine {
    fun execute(intent: Intent, listener: UssdListener)
}

interface UssdListener {
    fun onMenuRead(text: String)       // a USSD menu appeared -> app speaks it in Twi + captions it
    fun onPinRequired()                // app shows the accessible keypad; the engine is paused
    fun onSuccess(resultText: String)  // final result -> app speaks it in Twi
    fun onError(reason: String)        // fail closed -> app speaks a clear, actionable error
}
```

**The PIN never crosses this interface.** `onPinRequired()` is the only mention of it, and it means "the app takes over so the user can type." The engine never sees the digits.

---

## 5. Environment: what everyone installs

- **Git** + a GitHub repo (Frederick creates it, adds everyone).
- **Backend/ML:** Python 3.10+, then `pip install fastapi uvicorn` (ML adds more in its guide).
- **Frontend/USSD:** Android Studio (latest), Kotlin. **minSdk = 26** (required for the USSD API). Test on a **real Android phone with an MTN SIM** — the emulator cannot do USSD.
- **ngrok** (free) so the phone can reach the laptop server.

---

## 6. Git workflow (keep it boring)

- `main` — always demoable. Nothing broken lands here.
- `dev` — integration branch. Everyone merges here daily.
- `feature/<yourname>-<thing>` — your working branch. Small commits, clear messages.
- Open a Pull Request into `dev`. One teammate skims it. Merge.
- **Merge `dev` into your branch every morning** so you never drift far.

Folders you own:
```
/server    -> Selorm (+ Kelvin's model code lives here as importable modules)
/app       -> Richmond (UI) + Frederick (USSD engine, telephony)
/config    -> shared; change only by agreement (it's the contract)
/docs      -> this folder
```

---

## 7. The schedule, as integration milestones

| By end of | The system can… | Depends on |
|---|---|---|
| **Day 3** | Read a real MoMo **balance** aloud in Twi on a real phone. Go/no-go on interactive USSD. | Frederick (spike), Kelvin (TTS prompt), Selorm (server up) |
| **Day 8** | Full **send-money** path: Twi voice → Intent → confirm → USSD → user PIN → result read back | all four, integrated once |
| **Day 12** | Symbol-board input + **buy-data** intent + the accessibility layer complete | Richmond + Selorm + Kelvin |
| **Day 15** | Tested with blind users, fixes done, backup demo recorded, presentation rehearsed | all + accessibility lead |
| **Sept 23** | Present the working prototype | everyone |

Integration is not a day at the end. We integrate a thin end-to-end path by **Day 8** and thicken it. A demo that works narrow beats one that's broad and broken.

---

## 8. Definition of done for the demo (this is what we're all building toward)

On a real phone with an MTN SIM:
1. TalkBack reads the home screen; user starts listening.
2. Twi: *"check my balance"* → balance read back in Twi, captioned.
3. Twi: *"send 5 cedis to [contact]"* → app confirms aloud + on screen → user approves → USSD runs → **user enters PIN** → result read back.
4. One non-transfer intent (*buy data*) works → proves it's not hard-coded.
5. Symbol board produces the same Intent. Captions on all speech.
6. One sentence of evidence: *"we changed X after our blind teammate couldn't do Y."*

If a layer isn't in that list, it's a nice-to-have. Ship the list first.

---

## 9. How we talk

- 15-minute standup daily, same time. Three questions: done since yesterday, doing today, blocked by what.
- One shared checklist (GitHub Projects, Trello, or even a pinned message) with the tasks from your role guide.
- **Shout the moment you're blocked.** A blocker sat on for a day is the most expensive thing in a 17-day build.

Now go to your own guide. Build to the contracts. See you at integration.
