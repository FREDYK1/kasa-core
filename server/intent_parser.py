"""
KASA Core — deterministic Twi intent parser.

Pure functions, no I/O, no state: you feed in a transcript (+ contacts) and get
out a schema-valid Intent. Keeping this OUT of main.py makes it unit-testable,
which is our evidence of quality for the mentors.

Lexicon is seeded from team knowledge and MUST be validated/extended by native
speakers (Kelvin, Selorm, accessibility lead) before the demo — that is the
Day 2-3 work. It is marked with TODOs below.
"""
import json
import os
import re
from pathlib import Path

from jsonschema import ValidationError, validate
from rapidfuzz import fuzz

CONFIDENCE_FLOOR = 0.6

MONEY_ACTIONS = {"send_money", "cash_out", "pay_merchant"}
DATA_ACTIONS = {"buy_data", "buy_airtime"}
CONFIRM_ACTIONS = MONEY_ACTIONS | DATA_ACTIONS

# Ordered by priority: on tied keyword length the earlier action wins.
ACTION_KEYWORDS = {
    "send_money": [
        "fa cedi", "fa sika", "ma cedi", "fa kɔma", "ma kɔma", "soma sika",
        "soma", "mena", "bɔ kɔma", "kɔma", "send",
    ],
    "pay_merchant": [
        "tua ka", "tua ɔtɔ", "pay merchant", "tua hɔ", "merchant",
    ],
    "cash_out": [
        "yi sika", "cash out", "withdraw", "yi sika mfiri",
    ],
    "buy_data": [
        "tɔ data", "buy data", "intanɛt", "data",
    ],
    "buy_airtime": [
        "tɔ credit", "buy airtime", "airtime", "credit",
    ],
    "check_balance": [
        "sika dodow", "me sika dodow", "check balance", "akontabuo", "me sika",
        "balance",
    ],
}

# Twi number words (SEED — verify/extend with a native speaker).
UNIT_WORDS = {
    "baako": 1, "mmienu": 2, "mmiɛnsa": 3, "ɛnan": 4, "enum": 5,
    "asia": 6, "ason": 7, "awɔtwe": 8, "akron": 9, "nkron": 9,
}
TEENS = {
    "du": 10, "dubaako": 11, "dumienu": 12, "dumiɛnsa": 13, "duɛnan": 14,
    "duenum": 15, "duasia": 16, "duson": 17, "duawɔtwe": 18, "duakron": 19,
}
TENS_WORDS = {
    "aduonu": 20, "aduasa": 30, "aduanan": 40, "aduonum": 50,
    "aduosia": 60, "aduoson": 70, "aduɔwɔtwe": 80, "aduɔtwe": 80,
    "adukron": 90, "aduakron": 90, "ɔha": 100, "oha": 100,
}
TWI_NUMBERS = {**UNIT_WORDS, **TEENS, **TENS_WORDS}

CURRENCY_MARKERS = ("cedi", "ghs", "ghc", "gh¢", "¢", "sika")
PHONE_RE = re.compile(r"^0\d{9}$")

_SCHEMA_PATH = Path(__file__).resolve().parent.parent / "config" / "intent_schema.json"


# ---- schema validation -----------------------------------------------------

def load_schema():
    with open(_SCHEMA_PATH, encoding="utf-8") as f:
        return json.load(f)

SCHEMA = load_schema()


def validate_intent(obj) -> dict | None:
    """Return the object if it is schema-valid, else None. Nothing else may
    leave the server."""
    try:
        validate(obj, SCHEMA)
        return obj
    except (ValidationError, TypeError):
        return None


# ---- action detection ------------------------------------------------------

def detect_action(text: str) -> str:
    """Longest matched keyword wins; ties go to the earlier-listed action."""
    t = (text or "").lower()
    best_action, best_len = "unknown", -1
    for action, kws in ACTION_KEYWORDS.items():
        for kw in kws:
            if kw in t and len(kw) > best_len:
                best_action, best_len = action, len(kw)
    return best_action


# ---- amounts ---------------------------------------------------------------

def find_twi_number(text: str):
    """
    Return (value, start, end) for the largest Twi-number phrase in text, or
    None. Handles 11-19 as single tokens and compounds like
    'aduonu baako' (21) or 'ɔha aduonum' (150).
    """
    t = (text or "").lower()
    words = t.split()
    if not words:
        return None
    positions = []
    pos = 0
    for w in words:
        positions.append((pos, pos + len(w)))
        pos += len(w) + 1

    best = None
    for i, w in enumerate(words):
        base = TWI_NUMBERS.get(w)
        if base is None:
            continue
        value, j = base, i
        if w in TENS_WORDS:
            k = i + 1
            while k < len(words) and words[k] in TENS_WORDS and TENS_WORDS[words[k]] < 100:
                value += TENS_WORDS[words[k]]
                k += 1
            if k < len(words) and words[k] in UNIT_WORDS:
                value += UNIT_WORDS[words[k]]
                k += 1
            j = k - 1
        s, e = positions[i][0], positions[j][1]
        if best is None or value > best[0]:
            best = (value, s, e)
    return best


def extract_amount(text: str):
    t = (text or "").lower()

    candidates = []  # [value, start, end, near_currency_marker]
    for m in re.finditer(r"\b(\d+(?:\.\d{1,2})?)\b", t):
        if PHONE_RE.match(m.group(1)):
            continue  # a recipient number, not an amount
        candidates.append([float(m.group(1)), m.start(), m.end(), False])

    tw = find_twi_number(t)
    if tw is not None:
        candidates.append([float(tw[0]), tw[1], tw[2], False])

    if not candidates:
        return None

    for c in candidates:
        near = t[max(0, c[1] - 6):c[2] + 6]
        c[3] = any(mk in near for mk in CURRENCY_MARKERS)

    pool = [c for c in candidates if c[3]] or candidates
    return max(pool, key=lambda c: c[0])[0]


# ---- recipients ------------------------------------------------------------

def match_recipient(text: str, contacts: list[str]):
    """Fuzzy-match a name (or a raw Ghana number) from the transcript. If two
    contacts are too close, return None — we NEVER pick the wrong person."""
    t = (text or "").lower()

    m = re.search(r"\b(0\d{9})\b", t)
    if m:
        return {"raw": m.group(1), "matched_contact": None, "number": m.group(1)}

    best, second = None, None
    for name in contacts or []:
        if not name:
            continue
        keys = [name.lower()] + name.lower().split()[:1]  # full + first name
        s = max(fuzz.partial_ratio(k, t) for k in keys)
        if s < 80:
            continue
        if best is None or s > best[1]:
            second = best
            best = (name, s)
        elif s > (second[1] if second else 0):
            second = (name, s)

    if best is None:
        return None
    if second is not None and best[1] - second[1] < 10:
        return None  # ambiguous — force a re-ask
    return {"raw": best[0], "matched_contact": best[0], "number": None}


# ---- confidence ------------------------------------------------------------

def _confidence(action: str, amount, recipient) -> float:
    if action == "unknown":
        return 0.0
    conf = 0.35  # a detected, known action
    if action == "check_balance":
        conf += 0.5   # no slots needed; 0.85 on its own
    elif action in MONEY_ACTIONS:
        if amount is not None:
            conf += 0.25
        else:
            conf -= 0.1
        if recipient is not None:
            conf += 0.25
        else:
            conf -= 0.1
        if amount is not None and recipient is not None:
            conf += 0.1
    elif action in DATA_ACTIONS:
        if amount is not None:
            conf += 0.4
        else:
            conf -= 0.1
    # Anything incomplete funnels below the CONFIDENCE_FLOOR => the app re-asks.
    return round(min(max(conf, 0.0), 1.0), 2)


# ---- the parser ------------------------------------------------------------

def rule_parse(transcript: str, language: str = "tw", contacts: list[str] | None = None) -> dict:
    contacts = contacts or []
    t = transcript or ""
    action = detect_action(t)
    amount = extract_amount(t)
    recipient = match_recipient(t, contacts)
    return {
        "action": action,
        "amount": amount,
        "recipient": recipient,
        "extra": {},
        "confidence": _confidence(action, amount, recipient),
        "needs_confirmation": action in CONFIRM_ACTIONS,
        "transcript": t,
        "language": language,
    }


# ---- LLM fallback (only fires below the confidence floor) ------------------

def safe_llm_intent(raw_json_str: str, schema=None) -> dict | None:
    """
    Parse + validate raw LLM JSON. Money commands NEVER run on unvalidated
    model output: malformed/off-schema JSON returns None, which forces a re-ask.
    """
    try:
        obj = json.loads(raw_json_str)
        validate(obj, schema or SCHEMA)
        return obj
    except (json.JSONDecodeError, ValidationError, TypeError):
        return None


def llm_fallback(transcript: str, language: str = "tw", contacts: list[str] | None = None) -> dict | None:
    """
    Wire an LLM (OpenRouter/OpenAI/Anthropic — some tiers free) with a STRICT
    instruction: return only schema JSON; action='unknown' if unsure; never
    invent an amount or recipient; validate before returning anything.

    No API key configured => returns None, which cleanly forces a re-ask.
    """
    key = os.environ.get("KASA_LLM_API_KEY")
    url = os.environ.get("KASA_LLM_URL")
    if not key or not url:
        return None  # TODO: set env vars when wiring the demo model

    model = os.environ.get("KASA_LLM_MODEL", "openai/gpt-4o-mini")
    prompt = (
        "You convert a Twi/English mobile-money command into STRICT JSON matching this schema: "
        + json.dumps(SCHEMA)
        + ". Rules: only valid JSON, no prose. If unsure, action='unknown'. "
        "Never invent an amount or recipient; use null. "
        f"Contacts available: {json.dumps(contacts or [])}. "
        f'Command: "{transcript}". Language: {language}.'
    )
    try:
        from httpx import Client
        with Client(timeout=20) as c:
            r = c.post(
                url,
                headers={"Authorization": f"Bearer {key}"},
                json={
                    "model": model,
                    "messages": [{"role": "user", "content": prompt}],
                    "temperature": 0,
                },
            )
            r.raise_for_status()
            raw = r.json()["choices"][0]["message"]["content"]
    except Exception:
        return None
    return safe_llm_intent(raw)