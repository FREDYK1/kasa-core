# 06 · K05 — Design Decision Record (REFERENCE)

**Task:** K05 — first design session; lock the three open decisions before anyone builds on them.
**Owner:** Frederick (Lead). **Phase:** Setup · Day 1. **Deliverable:** the decisions, recorded here.

This is the completed reference output of the design session. Junior developers: run your own session, reach your own conclusions, then compare against this. Where you differ, be able to defend it — the goal is a *reasoned* decision, not the same decision.

---

## 0. The rule that makes every call below consistent

When two goals fight, resolve them in this order. Every decision in this document follows it.

1. **We never hold the user's PIN.** Non-negotiable. It is the whole product.
2. **A blind user can complete the task unaided.**
3. **It works on a low-end phone over a weak network.**
4. **The demo is reliable.**
5. **Breadth** (more intents, more languages).

If a choice makes the app prettier but risks (1), the choice is wrong. Rank first, decide second.

---

## Decision 1 — PIN entry mechanism  *(the one that matters most)*

**Question:** when the USSD session reaches the PIN step, how does the user enter it?

| Option | How it works | For | Against |
|---|---|---|---|
| **A. Native USSD dialog** | The accessibility service stops injecting at the PIN step; the user types into the phone's own system USSD dialog. TalkBack reads that dialog. | The PIN goes into an OS-level input we never touch. Zero PIN code in our app. Least code. | The native dialog's accessibility is the OS's, not ours — we can't add haptic-per-key or guarantee it never echoes the digit. |
| **B. Mirrored accessible keypad** | Our app draws a fully accessible keypad; when the user types, the app injects the PIN into the USSD dialog. | Full control of the keypad accessibility. | **The PIN passes through our app's memory** to be injected. That destroys invariant #1 and our single strongest claim. |

**The call: Option A.** The user enters the PIN directly into the system USSD dialog. Our app never handles the digits.

**Why.** Invariant #1 outranks keypad polish. Option B buys nicer UX at the cost of the exact thing that makes KASA different from every other voice-money tool — so it's off the table. We handle A's weakness with guidance, not by touching the PIN: right before the hand-off the app speaks *"please enter your PIN now"* and fires a haptic cue, then goes silent so it clearly isn't listening. During the Day 1–3 spike, Frederick **verifies TalkBack reads the native USSD PIN dialog on both test phones.** If a specific device's native dialog is genuinely unusable, that's a documented device limitation — we do **not** solve it by mirroring the PIN through our process.

**This is the accessibility lead's call to confirm.**

**Update (PIN audio, resolved):** Android treats the USSD PIN prompt as a secure field. With TalkBack's "Speak passwords" off (the default), digits are announced as "dot", not read aloud — so the native-dialog choice is safe on the acoustic-leak front too, handled by the platform, not by us touching the PIN. We add an onboarding check for that setting, an earpiece cue, and spike-time verification on both phones (see 08_K11_K13_SPIKE.md). The invariant is unchanged. As senior dev I've made the reference decision on security grounds; he owns the lived-experience sign-off. If he finds the native dialog unusable in practice, we discuss — but the answer is never "route the PIN through the app."

**See:** `diagrams/k05_pin_sequence.png` (the hand-off, step by step) and `diagrams/k05_trust_boundary.png` (the red line — the PIN bypassing the app).

---

## Decision 2 — Wake method

**Question:** how does the app start listening?

| Option | For | Against |
|---|---|---|
| **A. One big button** | Reliable, no false triggers, offline, trivial. A full-width target is easy for a blind user to hit. No always-on mic. | Not hands-free. |
| **B. Voice wake word** | Hands-free, feels modern. | Always-listening mic on a **financial** app = a privacy and trust problem. False triggers/misses. On-device wake model = battery + build cost. More failure surface in 17 days. |

**The call: Option A — a large, full-width "Speak" control** that fills most of the home screen, so the user can tap almost anywhere. TalkBack-labelled, haptic on start/stop.

**Why.** Ranking says reliability (4) and — more importantly — trust beat "modern." An always-on microphone on an app that moves money is the wrong trade for a hackathon and arguably for production. A big button is genuinely accessible, not a compromise. Voice-wake is a **post-hackathon enhancement**, noted and parked.

---

## Decision 3 — Contacts / recipient source

**Question:** when the user says "send to Kofi," where does Kofi come from?

| Option | For | Against |
|---|---|---|
| **A. Device contacts** | Real experience. | `READ_CONTACTS` permission; messy data; ambiguous matches; the recipient's data would need to travel to the parser; harder to control on stage. |
| **B. In-app trusted payees** | Controlled and predictable; **better money-UX** (a short list of known payees cuts mis-sends and fraud); no permission friction. | User must add payees first. |

**The call: Option B — an in-app trusted-payees list, resolved on-device.** Device-contact import is an optional later step, not in the prototype.

**Why.** A curated payee list is not a demo shortcut — it's the right design for money. It matches how susu and mobile-money users already think (you send to a known set of people), and it reduces the worst failure mode: sending to the wrong person. For the demo we pre-seed two or three payees.

**And a security refinement that comes out of this decision:** resolve the recipient **on the device**, not on the server. The parser returns `action`, `amount`, and the raw recipient text; the **app** matches that raw text against the local payees list and fills in the number. Contact data never leaves the phone. This tightens privacy and simplifies the server.

**Consequence — this changes a contract, so all artifacts update:**
- `/parse` and `/understand` no longer require a `contacts` array. The server stops receiving contact data. *(Selorm)*
- The app owns recipient resolution against the local payees list, and builds the small "add a payee" accessible flow. *(Richmond)*
- `config/intent_schema.json`: `recipient.matched_contact` / `recipient.number` are filled **on-device by the app**, not by the server. *(shared)*

---

## The diagrams (the required design artifacts)

1. **`diagrams/k05_pin_sequence.png` — the PIN hand-off.** The proof of Decision 1. The app drives the menus through the accessibility service; at the PIN prompt the service *stops*; the user types the PIN straight into the OS dialog (the red arrow), which never touches the app or the service; the carrier confirms and the result is read back in Twi.

2. **`diagrams/k05_trust_boundary.png` — trust boundary & data handling.** What crosses the app boundary and what doesn't. Audio is transient and discarded. Payees stay on the device. Intent comes back from the server. The PIN (red) deliberately bypasses the app entirely.

3. **`diagrams/k05_flow.png` — end-to-end interaction flow.** All three decisions in one picture: wake by button, confirm before any money moves, PIN hand-off, result read back, back to home. Also shows the confidence re-ask loop.

> Editable source (Mermaid) for the flow and sequence is in `k05_diagrams.mmd` so juniors can extend them.

---

## What each teammate does with this (so the decisions actually land)

- **Richmond (app):** big-button wake; confirm screen; at `onPinRequired()` speak the prompt + haptic and show nothing that captures digits; own the payees list + on-device recipient resolution + "add a payee" flow.
- **Selorm (server):** drop `contacts` from `/parse` and `/understand`; return raw recipient text and let the app resolve it; keep the audit log free of PIN and audio.
- **Kelvin (speech):** a clear, calm pre-PIN prompt in Twi ("please enter your PIN now") in the fixed-prompt pack.
- **Frederick (USSD):** during the spike, confirm TalkBack reads the native PIN dialog on both phones; implement the PIN-marker detection that triggers the hand-off; never inject at the PIN step.
- **Accessibility lead:** confirm Decision 1 in practice; if the native PIN dialog fails on a device, raise it — the fix is guidance or a device note, never routing the PIN through the app.

---

## The invariant, one more time

**KASA never holds the PIN.** Not in memory, not over the network, not in a log, not for a millisecond to "inject" it. Every decision above serves that sentence. When you make a design call I haven't covered, rank against Section 0 and this line, and you'll land where the rest of the system already is.
