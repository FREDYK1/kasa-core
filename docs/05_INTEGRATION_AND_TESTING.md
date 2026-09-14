# 05 · INTEGRATION, TESTING & PRESENTATION

How the four layers become one working prototype, how we test it, and how we present it on 23 September. Owned by the lead, but everyone reads this.

---

## 1. The accessibility lead's role (not an afterthought)

You are a full member, not a reviewer at the end. Concretely:
- **Sit in the first design session** and decide the PIN-entry mechanism (native USSD dialog vs mirrored accessible keypad). It's your call.
- **Test every screen as it's built**, with the developer next to you. Say plainly what you can't do. Each thing you can't do is a bug, not a preference.
- **Recruit 2–3 more blind testers** through the association for the Day 13–15 testing.
- **Be the source of truth** for how Twi commands and numbers should sound (work with Kelvin).
- Keep a running note: *"On [date], [name] couldn't do [X], so we changed [Y]."* That log is our single best piece of evidence for the mentors.

---

## 2. Integration milestones (thin first, then thick)

We do not integrate once at the end. We stand up a thin end-to-end path early and thicken it.

| Milestone | What connects | Proof |
|---|---|---|
| **M1 · Day 1** | App → `/health` → server | phone gets `ok` |
| **M2 · Day 3** | App → record → `/understand` (real ASR) → Intent shown on screen; separately, USSD balance reads aloud | two halves proven |
| **M3 · Day 8** | Full path joined: voice → Intent → confirm → USSD engine → PIN handoff → result read back | one complete send-money on a real phone |
| **M4 · Day 12** | Symbol board + buy-data + full accessibility layer | breadth proven |
| **M5 · Day 15** | Tested with blind users, fixes in, backup recorded, rehearsed | demo-ready |

If M3 slips, everything after compresses — protect it. A narrow path that works by Day 8 is worth more than a broad one that integrates on Day 16.

---

## 3. Test plan (layer by layer, then whole)

- **Unit (each owner, continuously):** Selorm's parser table tests; Kelvin's command-accuracy-by-subgroup; Richmond's TalkBack pass per screen; Frederick's engine on 2+ phones.
- **Integration (from M2):** run the real end-to-end path on a real device after every significant merge. Don't let integration bugs pile up.
- **User testing (Day 13–15):** the accessibility lead + 2–3 blind users complete real tasks, eyes off the screen. Watch where they get stuck. Fix those, not the things you imagined.
- **Adversarial (Day 13–15):** try to break it — silence, gibberish, wrong names, a network drop mid-transaction, ambiguous "send to Kofi" when there are two Kofis. Every failure must degrade to a clear spoken message and a safe state, never a crash and never the wrong action.

---

## 4. The demo — definition of done (repeat from START_HERE, because it's the target)

On a real phone with an MTN SIM:
1. TalkBack reads the home screen; user starts listening.
2. Twi: *"check my balance"* → read back in Twi, captioned.
3. Twi: *"send 5 cedis to [contact]"* → confirm aloud + on screen → approve → USSD runs → **user enters PIN** → result read back.
4. *"buy 1 cedi data"* works → not hard-coded.
5. Symbol board produces the same Intent. Captions on all speech.
6. One sentence of user-testing evidence.

## 5. The backup (both fintech and CV mentors warned: live demos fail)

- Record a **screen capture of a full successful real run** early, once M3 works. This is your insurance.
- Have a **live fallback** that doesn't depend on the mobile network on the day: run voice → Intent → confirm against a USSD mock, and play the recording for the USSD leg.
- Never let the whole demo depend on live USSD succeeding in a conference room in real time.

## 6. The 5-minute presentation (assign a lead speaker + a live driver)

Structure the mentors reward:
1. **Problem** (30s) — a blind user hands their phone and PIN to a stranger to send money. One sentence, ideally your teammate's own words.
2. **What existing tools miss** (20s) — English-only, one network, and they hold your PIN.
3. **Live demo** (2m) — balance, then send-money with the PIN entered by the user. This is the moment.
4. **How it's inclusive + local** (1m) — Twi, symbol board, TalkBack, works on low-end phones over USSD, the WCAG criteria you hit.
5. **Feasibility + honesty** (30s) — what's built vs what's next; subgroup accuracy, not one number.
6. **Impact + trust** (30s) — "we never hold your PIN," and one line of user-testing evidence.

Rehearse it three times. Time it. One person narrates, one drives the phone, so a fumble doesn't stall the story.

## 7. Definition of done for THIS phase (26 Sept, top 25)

We advance if the prototype demonstrably: understands a Twi command, confirms before acting, drives (or assists) a real USSD transaction with the user entering the PIN, is usable with TalkBack by a blind user, and works on a low-end phone. Everything in these five guides serves that sentence. Build to it.
