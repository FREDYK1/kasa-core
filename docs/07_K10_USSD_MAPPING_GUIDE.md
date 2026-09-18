# 07 · K10 — USSD Menu Mapping (the map the engine drives)

**Task:** K10 — dial `*170#`, map the real MTN MoMo menu tree, commit it to `config/ussd_scripts.json`.
**Owner:** Frederick. **Feeds:** K12 (interactive spike) and K31b (the state machine). **Priority:** Critical.

I've completed the parts that don't need the SIM: the navigation *design*, the expected tree, the capture worksheet, and the engine that consumes the map. The one part that can only be done on the phone — confirming each label and option number — is a dial-and-record job, and this guide is how you do it in about an hour.

---

## 1. The design decision that makes this robust: navigate by label, not by number

The obvious approach is to record "press 1, press 1, type amount, type PIN" and replay it. **Don't build it that way.** MoMo menus reorder — a promo inserts a new item and every number after it shifts, and your send-money flow silently starts doing something else. That's a catastrophic failure mode for a money app.

Instead the engine reads each menu, finds the option whose *label* matches ("Transfer Money"), and selects **that** option's number dynamically. The recorded number is only a fallback. So your job in K10 is to capture, at every node: the exact on-screen text, the option labels, and the number each label currently has. The config (`config/ussd_scripts.json`) already encodes this shape — you're filling in verified values.

---

## 2. Before you dial — three safety rules

- **Use tiny amounts.** Map send-money with 1 pesewa or 1 cedi to a teammate, or cancel before the final PIN. Never explore with real amounts.
- **Watch for fees.** The review screen shows amount + fee + e-levy. Record where the total appears — the engine reads it aloud in Twi before the PIN, which is a genuine safety feature for a blind user.
- **Map on two phones.** Different OEM skins render USSD dialogs differently. Test on at least two (ideally one low-end). Record which package renders the dialog (the accessibility service needs it).

---

## 3. The correction you must know before mapping

**MoMo wallet balance needs the PIN.** It lives inside a menu (My Wallet → Balance → PIN), so `check_balance` also ends in a PIN hand-off — it is *not* a PIN-free single-shot. The only PIN-free balance is airtime balance via `*124#`, which is a different, non-MoMo thing. Plan every MoMo flow to end at a PIN hand-off.

---

## 4. The capture worksheet — fill one row per screen, per flow, per phone

For each flow (`send_money`, `check_balance`, `buy_data`), walk it and record:

| Node id (`at`) | Exact on-screen text (top line) | Options shown (number → label) | Which we choose (label) | Verified number | Notes (fees, timeouts, PIN text) |
|---|---|---|---|---|---|
| main_menu | "MTN MoMo" | 1 Transfer Money · 2 Airtime & Data · 5 My Wallet … | Transfer Money | 1 | |
| transfer_menu | … | 1 MoMo User · 2 … | MoMo User | 1 | |
| recipient | "Enter recipient number" | (input) | — | — | |
| amount | "Enter amount" | (input) | — | — | e-levy shown next? |
| review | "Send GHS… fee GHS…" | 1 Confirm · 2 Cancel | Confirm | 1 | **record where total appears** |
| pin_prompt | "Enter PIN" | (input) | — | — | **exact PIN wording → detect.pin_markers** |
| result | "Payment successful. Ref…" | — | — | — | **exact success wording → success_markers** |

Do the same for `check_balance` (My Wallet → Balance → PIN → result) and `buy_data` (Airtime & Data → Buy Data → Self → bundle → PIN → result). The expected shape is in `diagrams/k10_menu_tree.png`.

The three things that matter most to capture exactly, because the engine keys off them:
1. **PIN prompt wording** → goes into `detect.pin_markers` (this triggers the hand-off).
2. **Success wording** → `detect.success_markers` (nothing is "done" until this shows).
3. **Failure/timeout wording** → `detect.failure_markers` / `timeout_markers` (fail closed).

---

## 5. Commit — turn the worksheet into the config

Open `config/ussd_scripts.json` and, for each flow:
- set each step's `choose_label` to the real labels you saw (lowercase),
- set `fallback_option` to the number that label currently has,
- confirm the `input` steps are in the right order (recipient → amount → reference),
- update the `detect` markers with the **exact** PIN / success / failure / timeout wording.

Then fill the `_verification` block: `status: "VERIFIED"`, your name, the date, and the two devices. That block flipping to VERIFIED is the real "Done" signal for K10.

---

## 6. How the engine consumes your map (so you know what you're feeding)

`app/UssdNavigator.kt` drives the map. At every dialog it:
1. checks terminal states first — failure, timeout, PIN, success;
2. if it's the PIN prompt, calls `onPinRequired()` and **stops** — never injects;
3. otherwise reads the menu aloud (Twi), matches the current step's label to an on-screen option, chooses it, and advances;
4. reads amount + fee aloud before the confirm step;
5. **fails closed** — if a menu doesn't match what the flow expects, it stops and tells the user, rather than pressing a number blindly on an unexpected screen.

The matcher parses lines like `"1. Transfer Money"` into (number, label) and returns the number whose label matches — the fallback number is used only if the label isn't found.

---

## 7. Definition of done for K10

- [ ] `send_money`, `check_balance`, `buy_data` walked on **two** phones; worksheet filled.
- [ ] `config/ussd_scripts.json` labels + fallback numbers match reality.
- [ ] `detect` markers use the **exact** PIN / success / failure wording seen on screen.
- [ ] The dialer/USSD package name recorded (for the accessibility service `packageNames`).
- [ ] Fee/total location recorded so the engine reads it before the PIN.
- [ ] `_verification.status` set to **VERIFIED** with name, date, devices.

Until that checkbox set is complete, the map is a template. The engine is ready for it; the truth comes from the SIM.

---

## 8. One honest note on scope (for the record)

Automating USSD keypresses via the accessibility service is fine for a sponsored hackathon prototype. A production launch would run on an MTN partnership / operator API — which the abstract already frames as the scale path. K10 maps the prototype rail; it does not commit us to shipping screen-automation at national scale.
