# 01 · BACKEND GUIDE — Selorm

**Your mission:** turn what the user said into a validated `Intent` the rest of the system can trust. You are the brain between speech and action.

> **K05 update (Day 1 design session):** `/parse` and `/understand` no longer take a `contacts` array. Return `recipient.raw` (the raw text you heard); the app resolves the actual payee on-device. Keep the audit log free of PIN and audio. See `06_K05_DESIGN_DECISIONS.md`.



**You own:** the FastAPI server — `/transcribe`, `/parse`, `/understand`, the Twi command grammar, confidence logic, the audit log, and validation against the schema.
**You do NOT own:** the ASR/TTS models themselves (Kelvin gives you functions to call), the app, or the USSD engine.

**The contract you must honour:** Section 4.2 of `00_START_HERE.md`. Your `/parse` returns an `Intent` exactly matching `../config/intent_schema.json`. Nothing else is allowed out of your endpoints.

Start file already in the repo: `../server/main.py`. You are extending it.

---

## Layer by layer

### Step 1 — Environment and skeleton (Day 1, ~1 hour)

```bash
cd server
python3 -m venv .venv && source .venv/bin/activate
pip install fastapi uvicorn python-multipart jsonschema pydantic
uvicorn main:app --reload --host 0.0.0.0 --port 8000
# in another terminal:
ngrok http 8000        # copy the https URL, give it to Richmond
```

Add a health check so the app can test connectivity immediately:

```python
@app.get("/health")
def health():
    return {"status": "ok"}
```

**Done when:** Richmond can hit `/health` from the phone and get `ok`. Now he is unblocked.

### Step 2 — The parser, made trustworthy (Days 1–3)

`main.py` already has a seed grammar. Your job is to make it reliable for the demo command set. Work in `intent_parser.py` (extract the parsing out of `main.py` so it's testable):

1. **Actions** — expand `ACTION_KEYWORDS` with the real Twi phrases. Sit with Kelvin and the accessibility lead and write down how people actually say "send money", "check balance", "buy data" in Twi. This lexicon is the heart of accuracy.
2. **Amounts** — handle both ASR digits (`"50"`) and Twi number words. Extend `TWI_NUMBERS` fully (1–100 at least, plus the common amounts: 1, 2, 5, 10, 20, 50, 100).
3. **Recipients** — match against the `contacts` list the app sends. Use fuzzy matching (e.g. `rapidfuzz`) so "Kofi" matches "Kofi Mensah". If two contacts match, return `matched_contact: null` and low confidence so the app re-asks — **never pick the wrong person.**
4. **Confidence** — reward the slots the action actually needs. `send_money` with no amount or no recipient must score low. Below `0.6` → the app re-asks.

```python
# intent_parser.py
from rapidfuzz import fuzz

def match_recipient(text, contacts):
    best, score = None, 0
    for name in contacts:
        s = fuzz.partial_ratio(name.lower(), text.lower())
        if s > score:
            best, score = name, s
    if score >= 80:
        return {"raw": best, "matched_contact": best, "number": None}
    return None
```

**Done when:** you can run 20 typed Twi commands through `/parse` and get correct Intents, with weak/ambiguous ones scoring below 0.6.

### Step 3 — The LLM fallback, safely (Days 3–4)

Only fires when rule confidence < 0.6. Wire an LLM (OpenAI/Anthropic/OpenRouter — some are free) with a strict instruction:

- Return **only** JSON matching the schema. No prose.
- If unsure, `action: "unknown"`.
- **Never invent** an amount or a recipient.
- Validate the returned JSON against `intent_schema.json` with `jsonschema`. If it fails validation, discard it and return `unknown` (which makes the app re-ask). A money command must never run on unvalidated model output.

```python
from jsonschema import validate, ValidationError
def safe_llm_intent(raw_json_str, schema):
    try:
        obj = json.loads(raw_json_str)
        validate(obj, schema)
        return obj
    except (json.JSONDecodeError, ValidationError):
        return None
```

**Done when:** a phrasing your rules miss (but a human understands) gets parsed correctly, and malformed model output safely falls through to a re-ask.

### Step 4 — `/understand`, the convenience endpoint (Day 4)

The app prefers one call. Combine transcribe + parse:

```python
@app.post("/understand")
async def understand(audio: UploadFile = File(...), contacts: str = Form("[]")):
    text = await run_asr(await audio.read())          # Kelvin's function
    return parse(ParseRequest(transcript=text, language="tw",
                              contacts=json.loads(contacts)))
```

`run_asr` is imported from Kelvin's module. Until it exists, stub it to return a fixed Twi string so you can build and test the wiring.

### Step 5 — The audit log + audio receipt (Days 5–6)

This is our "prove" step and a WCAG-friendly confirmation. **Log the action, never the PIN, never raw audio.**

```python
@app.post("/receipt")
def receipt(event: dict):
    # store: timestamp, action, amount, recipient_name, result_status  -- NO credentials
    record = {...}
    append_to_log(record)
    return {"receipt_id": record["id"], "spoken_summary_tw": build_twi_summary(record)}
```

`build_twi_summary` returns a short Twi sentence the app can speak back later ("Yesterday you sent 5 cedis to Kofi. It was successful."). Keep it simple for the demo.

**Done when:** every completed action leaves a log line with no sensitive data, and returns a Twi summary string.

### Step 6 — Tests (throughout, not at the end)

```bash
pip install pytest
```

Write `test_parser.py` with a table of `(twi_command, expected_action, expected_amount, expected_recipient)`. Run it on every change. This is your safety net and your evidence of quality for the mentors.

---

## How you integrate

- **With Kelvin:** you call his functions `run_asr(bytes) -> str` and (optionally) `synthesize_twi(text) -> bytes`. Agree these signatures on Day 1. Stub them until his models land, then just import the real ones — no other change.
- **With Richmond:** he calls your `/understand` and `/health`. Give him the ngrok URL and one example request/response so he can build against it (or against his own fake) immediately.
- **With Frederick:** you don't call each other directly. Your Intent is what his USSD engine consumes — so the schema is your shared language. If the Intent shape must change, change `../config/intent_schema.json` **by agreement** and tell everyone.

## Your day-by-day

- **Day 1:** env up, `/health` live, ngrok URL shared, parser extracted into `intent_parser.py`.
- **Day 2–3:** grammar solid for balance + send + data; tests passing.
- **Day 4:** `/understand` wired (with ASR stub), LLM fallback validated.
- **Day 5–6:** audit log + receipt; more tests.
- **Day 7–8:** swap ASR stub for Kelvin's real model; integrate end-to-end with Richmond.
- **Day 9+:** harden — handle silence, gibberish, ambiguous recipients gracefully; every failure path returns a clear re-ask, never a crash.
