# 08 · K11–K13 — The USSD Spike (prove it, or fall back cleanly)

**Tasks:** K11 (single-shot balance), K12 (interactive drive + PIN detection on 2 phones), K13 (GO/NO-GO).
**Owner:** Frederick. **Window:** Days 1–3. **All three are Critical** — the whole engine choice depends on them.

I've built the code and the framework. What's left is the part only a real MTN phone can answer: does it actually behave on device. This guide is the exact procedure and the decision it feeds.

---

## 0. Settled first: the PIN is not spoken aloud (design closed)

Android treats the USSD PIN prompt as a **secure/password field**. With TalkBack's **"Speak passwords" off — the default** — the screen reader announces a masked field as "dot" per keypress, not the digit. So the native-dialog decision from K05 holds and the acoustic-leak worry is handled by the platform, not by us touching the PIN. We make it solid three ways:

1. **Onboarding check** (Richmond): a short, accessible step that confirms "Speak passwords" is off and explains why.
2. **Earpiece cue**: before the PIN hand-off, the app suggests an earpiece for any private audio.
3. **Spike verification** (below, K12 step 5): confirm on both test phones that entering a PIN does not read digits aloud.

This does not change the invariant. We still never inject or store the PIN. If the teammate recommends something different once he replies, it slots in on top of this.

---

## K11 — single-shot balance (the easy, PIN-free win)

**Reframe from K10:** MoMo *wallet* balance needs the PIN (it's inside a menu), so it goes through the interactive path (K12), not here. `sendUssdRequest` is genuinely good for **PIN-free single-shot codes** — airtime balance and data balance. That is still a real feature: "check my airtime balance" by voice, read back in Twi, instantly, no PIN. Ship it as a bonus; route MoMo balance through K12.

**Code:** `app/BalanceChecker.kt` (done). **Run it:**
1. Real device, MTN SIM, `CALL_PHONE` granted at runtime.
2. Find the current MTN Ghana airtime/data balance short-codes (verify on the SIM — don't trust a code from memory). Put them in config.
3. Call `BalanceChecker.check(code, ...)`; confirm the real response comes back and is read aloud in Twi.

**K11 results template (fill on 2 phones):**

| Phone (model / Android) | Code used | Response returned? | Read aloud in Twi? | Notes |
|---|---|---|---|---|
| | | Y / N | Y / N | |
| | | Y / N | Y / N | |

**K11 done when:** a PIN-free balance reads aloud on ≥2 phones.

---

## K12 — interactive drive + PIN detection (the real spike)

**Code:** `app/UssdAccessibilityService.kt` + `app/UssdNavigator.kt` (K10) + `res/xml/ussd_service_config.xml` + manifest snippet (all done). This is the part that's fragile across OEMs, so we test it deliberately.

**Procedure, per phone:**

1. **Discover the dialer package.** Enable the service, dial `*170#`, and log `event.packageName` (the commented line in the service). Put that package into `ussd_service_config.xml` `packageNames`. *(Common: `com.android.phone`, but OEMs differ — verify.)*
2. **Read every menu.** Walk `*170#` and confirm the service reads each dialog's text (log it). This is the make-or-break: if it can't read menus, nothing else works.
3. **Drive ≥3 send-money steps.** With a **1 pesewa / 1 cedi** transfer to a teammate (or cancel before PIN), confirm the navigator selects the right option by label and advances: Transfer → MoMo User → recipient → amount.
4. **Detect the PIN, every time.** Confirm that at the PIN prompt the service fires `onPinRequired()` and **does not inject**. Run it 5 times; it must detect on all 5.
5. **Verify PIN silence.** With TalkBack on and "Speak passwords" off, type a PIN into the native dialog and confirm digits are **not** read aloud. Repeat with an earpiece.

**Safety while spiking:** tiny amounts only; cancel before the real PIN when you can; never explore with real money.

**K12 results template (fill on 2 phones):**

| Check | Phone A | Phone B | Notes |
|---|---|---|---|
| Dialer package discovered | | | pkg = ? |
| Reads every menu | Y / N | Y / N | |
| Drives ≥3 send-money steps | Y / N | Y / N | which step breaks? |
| Detects PIN 5/5 (no inject) | /5 | /5 | |
| Native PIN dialog stays silent on digits | Y / N | Y / N | earpiece needed? |

**K12 done when:** the table is filled on two phones.

---

## K13 — GO / NO-GO (decide by end of Day 3)

Don't decide on a feeling. Score the criteria; the diagram (`diagrams/k13_go_no_go.png`) shows the branches.

**Criteria (all must pass for full GO):**

| # | Criterion | Source | Pass = |
|---|---|---|---|
| G1 | Single-shot balance reads aloud | K11 | on ≥2 phones |
| G2 | Service reads every USSD menu | K12.2 | on ≥2 phones |
| G3 | Service drives ≥3 send-money steps | K12.3 | on ≥2 phones |
| G4 | PIN detected every time, never injected | K12.4 | 5/5 on ≥2 phones |
| G5 | Native PIN dialog silent on digits | K12.5 | on ≥2 phones (earpiece allowed) |

**The three outcomes:**

- **All pass → GO (full interactive).** Build voice → intent → confirm → drive USSD → user PIN → result. Keep assisted mode (below) as the demo safety net.
- **G2 passes but G3 flaky → ASSISTED MODE.** Still a real win — see below. Pivot **that day**, don't sink days chasing G3.
- **G2 fails on both phones → RETHINK.** Reading itself is broken; escalate — voice-guided manual, or a different device. Rare, but named so it's not a panic.
- **G5 fails → fix the setting first** (Speak passwords off + earpiece), re-test, then proceed.

**My recommendation as lead:** build for GO, but ship **assisted mode as a runtime safety net** regardless — so if a menu misbehaves on demo day, the app degrades to guiding the user instead of failing on stage. Robust beats impressive when the judges are watching a live run.

### Assisted mode — the fallback, fully specified (so the pivot is instant)

Assisted mode only needs to **read** USSD dialogs (G2), not drive them (G3) — which is why it's robust. The app:
1. confirms the intent up front, same as always ("Send 5 cedis to Kofi?");
2. dials `*170#`, reads each menu aloud in Twi, and **tells the user which option to press** ("Press 1 for Transfer Money");
3. reads the amount + fee before the PIN;
4. at the PIN step, hands off exactly as in GO mode — **the app never touches the PIN**;
5. reads the result in Twi.

Code reuse: same `UssdAccessibilityService` reader, same PIN detection, same TTS. You drop the `inject/choose` calls and replace them with spoken guidance. Small delta from the GO build — which is why keeping both is cheap.

### K13 decision record (fill and commit to the dev log)

```
Date:            ____________
Devices tested:  (A) ______________   (B) ______________
G1 balance aloud:        A [ ] B [ ]   notes: __________
G2 reads menus:          A [ ] B [ ]   notes: __________
G3 drives >=3 steps:     A [ ] B [ ]   notes: __________
G4 PIN detect 5/5:       A __/5 B __/5 notes: __________
G5 PIN dialog silent:    A [ ] B [ ]   notes: __________

DECISION:  [ ] GO (full)   [ ] GO + assisted safety net   [ ] ASSISTED only   [ ] RETHINK
Rationale: ________________________________________________
Signed:    Frederick (lead)  +  ____________ (accessibility lead)
```

**K13 done when** that record is filled, committed, and the team knows which build we're on.

---

## Honest scope note

K11–K13 are on-device experiments. I've delivered the code, the config, the protocols, the fallback design, and the decision framework. The one thing I can't do from here is watch it run on your MTN phone — that's the spike. Everything above makes that spike a half-day of structured testing with a clear yes/no at the end, instead of a week of poking.
