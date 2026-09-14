# KASA Core

A Twi voice assistant that drives mobile money USSD for blind and low-vision users —
**without ever holding your PIN**. Tɛkyerɛma Pa Hackathon 2026, Team KASA Core.

## Why this design
- The money-moving action happens inside the carrier's own USSD session, so we never touch a
  wallet API and never store a PIN.
- The command set is small and closed, so we need a constrained Twi command grammar, not
  general Twi NLU.

Full reasoning: `KASA_Core_Technical_Plan.md`.


## Developer build docs (start here)
Every developer reads `docs/00_START_HERE.md` first (the shared contracts), then their own guide:
- `docs/01_BACKEND_GUIDE.md` — Selorm (server, intent parser)
- `docs/02_ML_GUIDE.md` — Kelvin (Twi ASR + TTS)
- `docs/03_FRONTEND_ANDROID_GUIDE.md` — Richmond (Android app + accessibility)
- `docs/04_USSD_ENGINE_GUIDE.md` — Frederick (USSD engine, the riskiest module)
- `docs/05_INTEGRATION_AND_TESTING.md` — how it all comes together + the demo

## Repo
- `KASA_Core_Technical_Plan.md` — decisions, architecture, sprint plan, roles, demo definition.
- `config/intent_schema.json` — strict intent contract the parser must emit.
- `config/ussd_scripts.json` — USSD menu navigation config. **Digits are placeholders — map them
  against a real *170# tree on an MTN SIM.**
- `app/UssdSpike.kt` — Kotlin skeleton for the day 1–3 spike (balance + interactive + PIN hand-off).
- `server/main.py` — FastAPI: `/transcribe` and `/parse` with the Twi grammar + LLM-JSON fallback.

## Day 1–3 — prove the riskiest thing
1. Real Android phone + MTN SIM (not an emulator).
2. Build the balance path via `sendUssdRequest` — the easy win.
3. Stand up the AccessibilityService and drive the first send-money steps; detect the PIN prompt.
4. **Go/no-go on day 3.** If interactive automation is fragile, switch to assisted read-aloud mode.

## Run the server (prototype)
```
pip install fastapi uvicorn
uvicorn server.main:app --host 0.0.0.0 --port 8000
ngrok http 8000   # so the phone can reach it
```

## Non-negotiables (keep true to the finale)
Accessibility is the architecture, not an add-on · meaningful Twi (not English + translation) ·
usable on low-end phones and weak networks · **we never hold the PIN** · test with blind users
continuously and show what changed because of them.
