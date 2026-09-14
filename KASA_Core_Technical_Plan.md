# KASA Core — Technical Plan & Meeting Output

**Team KASA Core · Tɛkyerɛma Pa Hackathon 2026**
Prototype build window: **now → 22 September**. Second screening (live prototype): **23–24 September**. Top 25: **26 September**.

This is the output of the way-forward meeting. Part A records the decisions. Parts B–H are the engineering plan the decisions imply. The appendix points to starter code already in this repo.

---

## Part A — Agenda, answered as decisions

| Agenda item | Decision |
|---|---|
| Is the team locked and everyone committed? | Yes for the 17 days. Accessibility lead is a full member and joins design + testing, not just review. (Confirm the name and add to all docs.) |
| The riskiest question — USSD automation | We spike it in the first 3 days on a **real Android phone with an MTN SIM**. Balance check via the official `sendUssdRequest` API; interactive send-money via an **AccessibilityService** that drives the USSD dialog and hands off at the PIN step. Go/no-go checkpoint on day 3. Fallback defined (assisted read-aloud mode). |
| Demo scope for 23 Sept | One thing, done well: **Twi voice → intent → confirm → USSD navigation → user enters PIN → result read back in Twi**, for *balance* and *send money*, plus *one* non-transfer intent (buy data) to prove it is not hard-coded, plus the symbol-board input path and captions. Everything else is described, not built. |
| Platform | **Native Android (Kotlin)**, not Flutter — see B3. iOS cannot automate USSD, so it was never in scope. |
| Speech stack | Server-side during the build (Twi ASR + TTS behind FastAPI on a team laptop), with a **constrained command grammar** rather than open-domain Twi NLU. On-device is the stated production path. |
| Roles | Assigned in Part E. |
| Mentor outreach | Owners and deadlines in Part F. Emails go out day 1. |
| PIN entry method | The PIN is **always** entered by the user and never stored or transmitted by us. Exact mechanism (native USSD dialog vs mirrored accessible keypad) closed with the accessibility lead in the first design session — see Part G. |

---

## Part B — Software design

### B1. Architecture at a glance

```
        ┌─────────────────────────── Android app (Kotlin) ───────────────────────────┐
        │                                                                             │
  user  │   Voice in ──▶ [Audio capture]                                              │
  ─────▶│   Symbol tap ─▶ [Symbol board] ──┐                                          │
        │                                  ▼                                          │
        │                          [Intent request] ──(https)──▶  Server (FastAPI)    │
        │                                                          • Twi ASR (Whisper)│
        │                                                          • Intent parser    │
        │                                  ┌──(intent JSON)◀───────• (grammar + LLM)  │
        │                                  ▼                                          │
        │                    [Confirm step]  ── speak + display "Send GH₵50 to Kofi?" │
        │                          │ user approves                                    │
        │                          ▼                                                  │
        │                    [USSD engine] ─▶ AccessibilityService drives *170# menus │
        │                          │  … stops at PIN …                                │
        │                          ▼                                                  │
        │                    [User enters PIN]  ──▶ session completes                 │
        │                          ▼                                                  │
        │                    [Result] ── speak in Twi (TTS) + caption + audio receipt │
        └─────────────────────────────────────────────────────────────────────────┘
```

Two facts shape everything: the money-moving action happens **inside the carrier's own USSD session** (so we never touch the wallet API and never hold the PIN), and the user's instruction is a **small, closed command set** (so we do not need general Twi NLU).

### B2. The riskiest question — USSD automation on Android (the honest analysis)

This is the make-or-break, so here is the real engineering picture.

**Single-shot USSD (balance): solved, official.** `TelephonyManager.sendUssdRequest(...)` (API 26+, needs `CALL_PHONE`) fires a USSD code and returns the response text in a callback. Perfect for a code that returns one result. This is your easy, reliable win — build it day 1.

**Interactive, menu-driven USSD (send money): the hard part.** MoMo transfers are a *session*: `*170#` → pick "Transfer" → enter number → enter amount → reference → **PIN** → confirm. `sendUssdRequest` does **not** cleanly support replying step-by-step to a running USSD menu session — that is a documented limitation, not a skill gap. The established workaround is an **AccessibilityService**: dial the code, let the system USSD dialog appear, read its text from the accessibility node tree, and inject each menu response programmatically. It works for interactive flows because you are driving the real dialog. It is also **fragile across OEMs and Android versions** — dialog rendering differs — which is exactly why we spike it before committing.

**The PIN hand-off — the point that makes the whole security claim true.** When the accessibility service detects the "enter PIN" dialog, it **stops injecting** and surfaces the prompt to the user (accessible keypad / native dialog). The user types the PIN into the carrier session. It never enters our app, never hits our server, never gets stored. That is not a nice-to-have; it is the entire trust argument.

**Hard constraints to plan around now:**
- **Real device + real MTN SIM required.** USSD does not work on an emulator. Whoever owns the spike needs a physical MTN phone.
- Permissions: `CALL_PHONE`, plus the user enabling the AccessibilityService (a settings toggle — build an accessible onboarding flow for that).
- OEM variance: test on at least two different Android phones early.

**Go / no-go, day 3.** If interactive automation is solid on our test devices → full send-money flow. If it is too fragile in the time we have → **fallback: assisted mode**, where KASA reads the USSD menus aloud and the user drives with voice/tap guidance. That is *still* a large accessibility win for a blind user, and it de-risks the demo. Decide on evidence, not hope.

### B3. Platform decision — native Android (Kotlin)

The abstract said Flutter; the engineering says **native Kotlin**, and it is worth the change:
1. The core innovation — `TelephonyManager` USSD + `AccessibilityService` — is **native-only**. Wrapping the riskiest, fiddliest part in Flutter platform channels adds failure surface exactly where we can least afford it.
2. Android's screen reader (TalkBack) and accessibility node handling are most predictable in native.
3. iOS cannot do USSD automation at all, so cross-platform buys us nothing here. MoMo-over-USSD is Android territory in Ghana anyway.

This *strengthens* feasibility rather than weakening it, and it is defensible to a technical mentor. Note it in the next abstract revision if asked.

### B4. Speech layer

**The unlock: this is a closed command set, not open-domain Twi.** The vocabulary is small — a handful of actions (send, balance, cash out, buy data/airtime), numbers, and contact names. That means we do not need to "solve Twi ASR." We need high accuracy on a constrained grammar, which is far more achievable and far more defensible.

- **ASR (Twi → text):** start from the HCI Lab's `dcshcilab_Whisper` (Akan-fine-tuned) or Whisper-small; fine-tune on ~25–30 min of *our* command-domain audio if accuracy needs it (Evans confirmed fine-tune-not-pretrain). Bias decoding toward our command lexicon and digits.
- **TTS (text → Twi speech):** `dcshcilab_Vits` / GhanaNLP Khaya. **Pre-record the fixed prompts** ("What would you like to do?", "Send how much, to whom?", "Please enter your PIN", "Done") — highest quality, trivial, offline. Use TTS only for variable slots (amounts, names, balance), with a number-to-Twi templating helper.
- **Where it runs:** server-side (laptop + FastAPI + ngrok) during the build for fast iteration and low latency. On-device via quantised models (whisper.cpp / ONNX / TFLite) is the stated production path — say so, don't build it now.

### B5. Intent layer

Transcript → strict intent JSON. Design:
- **Primary: deterministic Twi command grammar.** Keyword matching for the action, number parsing for amounts, fuzzy contact matching for recipients — operating directly on the Twi transcript with a Twi lexicon. Deterministic, auditable, no dependency on an LLM understanding a low-resource language.
- **Fallback: LLM on the transcript, constrained to JSON.** Only for phrasings the rules miss. It fills slots; it **never executes** and cannot invent an amount or recipient. Output validated against the schema; low confidence → re-ask, never guess.
- **Confirm-before-execute, always.** No USSD navigation that moves money starts until the user has heard and approved the summary. This is WCAG 3.3.4 (Error Prevention, Financial) made literal.

Schema: see `config/intent_schema.json`.

### B6. USSD execution engine

A state machine that maps `intent → a scripted USSD navigation path` for that action, executes it step-by-step via the accessibility service, detects the PIN prompt and hands off, then reads the final result.

- Per-action menu paths live in **config** (`config/ussd_scripts.json`), not code, so a menu change is a config edit.
- **The exact menu digits in that file are placeholders and MUST be mapped by hand against the live `*170#` tree on a real MTN SIM.** Mapping the real menu tree is a day-1 task, not an assumption.
- Every step: read dialog text → match expected prompt → inject response (or hand off at PIN) → on mismatch, abort safely and tell the user. Fail closed: nothing is treated as done until the carrier confirms.

### B7. Accessibility specification (mapped to the criteria we cited)

| Requirement | Build task | WCAG / source |
|---|---|---|
| Full voice path, nothing display-only | Every prompt/result spoken in Twi + captioned | 1.3.3 Sensory Characteristics |
| Confirm before any financial action | Spoken + visual summary, explicit approve | 3.3.4 Error Prevention (Financial) |
| No timeout traps | Confirmation windows extendable / no hard timeout | 2.2.1 Timing Adjustable |
| Screen-reader usable | TalkBack labels on every control; test with TalkBack on | 4.1.2 Name, Role, Value |
| Motor access | Large targets, symbol board, full keyboard/switch operability | 2.1.1 Keyboard |
| Low vision | High contrast ≥ 4.5:1, resizable text/symbols | 1.4.3 / 1.4.4 |
| Non-verbal users | Symbol board reaches the same intent parser | inclusion criterion |
| Deaf / HoH | Captions on all spoken output | 1.2 |

PIN entry: never spoken (a spoken PIN is overheard), never stored; entered on a private accessible keypad with haptic + headphone feedback.

### B8. Privacy & security model

- **No PIN, ever** — entered by the user into the carrier session only.
- **No standing wallet access** — we automate USSD, we do not hold a wallet token.
- **Audio** processed for the command then discarded; no raw voice retained server-side beyond the request.
- **Contacts** matched on-device.
- **Audit / receipt:** we log the *action* (not credentials) and offer an **accessible audio receipt** a blind user can replay — this is our "prove" step and doubles as a WCAG-friendly confirmation.
- **Guardrails:** rate-limit and refuse when the assistant is repeatedly pushed past its permitted intents.

---

## Part C — Definition of done for the 23 September demo

**Primary demo (on a real phone, MTN SIM):**
1. Open KASA; TalkBack reads the home screen; user triggers listening.
2. Twi: *"check my balance."* → KASA runs the balance USSD, reads the balance back in Twi, captioned.
3. Twi: *"send 5 cedis to [contact]."* → KASA slot-fills, speaks + shows *"Send GH₵5 to [name]?"*, user approves, KASA drives the send-money menus, **stops at PIN**, user enters PIN, transaction completes, KASA reads the result.
4. One non-transfer intent: *"buy 1 cedi data."* → proves the intent set is not hard-coded.
5. Show the **symbol-board** path producing the same intent, and captions throughout.
6. One line of evidence: *"we changed X after [accessibility lead] could not do Y."*

**Backup (demos fail live — both fintech and CV mentors warned):** a screen-recording of a successful real run, **plus** a live run of voice→intent→confirm against a USSD mock if the network misbehaves on the day. Always have the recording.

---

## Part D — 17-day sprint plan

| Days | Goal | Key outputs |
|---|---|---|
| 1–3 · Spike & prove | Answer the riskiest question | Balance via `sendUssdRequest` working on device; interactive send-money spike via AccessibilityService; **go/no-go**. In parallel: repo up, mentor emails sent, real `*170#` menu tree mapped, ~25–30 min command audio collected |
| 4–8 · Core pipeline | One full path, end to end | Twi ASR → intent parser → confirm → USSD engine → Twi readback, for balance + send money, on device |
| 9–12 · Breadth & access | Prove it generalises + is usable | Symbol-board input; buy-data intent; accessibility layer (TalkBack, contrast, captions, no-timeout, PIN keypad); audio receipt |
| 13–15 · Test & rehearse | Make it real and presentable | Testing with blind users; fix what they break; record backup demo; rehearse the 5-min presentation |
| 23–24 Sept | Present | Live prototype to mentors |

Checkpoint after day 3 is mandatory: if go/no-go says the interactive automation is too fragile, switch to assisted mode that day and re-plan around it.

---

## Part E — Roles (adjust to real strengths)

| Member | Owns |
|---|---|
| **Frederick** | Lead; architecture; USSD state-machine + integration; presentation narrative |
| **Kelvin** | ML — Twi ASR/TTS integration and any fine-tuning; number-to-Twi readback |
| **Selorm** | Backend — intent parser, JSON schema, FastAPI server, audit log |
| **Richmond** | Android UI + accessibility — symbol board, TalkBack, contrast, captions, PIN keypad, onboarding for the accessibility-service toggle |
| **Accessibility lead** | Lived-experience co-design; continuous testing; recruiting 2–3 more blind testers; owns the PIN-entry decision |

## Part F — Mentor outreach (day 1, with owners)

| Ask | Who to email | Owner | By |
|---|---|---|---|
| Akan ASR/TTS checkpoints (`dcshcilab_Whisper`, `dcshcilab_Vits`) | Evans | Kelvin | Day 1 |
| Datasets (Akan) + any model access | dcshcilab@ug.edu.gh | Selorm | Day 1 |
| Accessibility review of screens | Prof. Naami (via Dr. Ekpezu) | accessibility lead | Day 6 |
| UI/UX feedback | Dr. Sarah Dsane | Richmond | Day 8 |

Most teams read "no scheduled sessions" as "we're on our own." We are not — this is free advantage.

## Part G — Open decisions to close in the first design session

1. **PIN entry mechanism** — native USSD dialog vs mirrored accessible keypad. Decide with the accessibility lead; it is his call to lead.
2. **Wake mechanism** — a large single button vs voice wake. Button is simpler and more reliable for the demo; lean button.
3. **Contacts source** — device contacts vs an in-app list for the demo. In-app list is safer for a controlled demo; device contacts is the real experience. Pick per demo risk.
4. **Fallback trigger** — the exact day-3 criterion that flips us to assisted mode. Write it down so the decision is not emotional on the day.

---

## Appendix — starter artifacts in this repo

- `config/intent_schema.json` — the strict intent contract the parser must emit.
- `config/ussd_scripts.json` — the USSD navigation config (menu digits are **placeholders — map against a real SIM**).
- `app/UssdSpike.kt` — Kotlin skeleton: balance via `sendUssdRequest`, plus the AccessibilityService approach for interactive flows and the PIN hand-off. Run this on a real device in days 1–3.
- `server/main.py` — FastAPI stubs for `/transcribe` and `/parse`, with the deterministic Twi command grammar and the LLM-JSON fallback shape.
- `README.md` — how the pieces fit and how to run the spike.

> These are engineer's starting skeletons, not tested binaries — the Kotlin must be run on a real Android phone with an MTN SIM, and the Twi lexicon must be expanded with native speakers. They exist to get you building on day 1 instead of from a blank screen.
