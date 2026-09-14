"""
KASA Core — inference server (FastAPI).

Two endpoints:
  POST /transcribe  audio -> Twi text            (Whisper / dcshcilab_Whisper)
  POST /parse       Twi text -> strict intent JSON (deterministic grammar + LLM-JSON fallback)

Run for the prototype on a team laptop:
    pip install fastapi uvicorn
    uvicorn server.main:app --host 0.0.0.0 --port 8000
    # expose to the phone with: ngrok http 8000

This is a starting skeleton. The Twi lexicon MUST be expanded with native speakers
(Kelvin/Selorm + the accessibility lead). The ASR/LLM calls are left as clearly marked stubs.
"""
from fastapi import FastAPI, UploadFile, File
from pydantic import BaseModel
import re, json

app = FastAPI(title="KASA Core Inference")

CONFIDENCE_FLOOR = 0.6

# ---- Twi command lexicon (SEED — expand with native speakers) --------------------------
ACTION_KEYWORDS = {
    "check_balance": ["sika dodow", "balance", "me sika", "check balance", "akontabuo"],
    "send_money":    ["fa kɔma", "send", "soma", "mena", "tua"],
    "buy_data":      ["data", "intanɛt", "buy data"],
    "buy_airtime":   ["airtime", "credit", "tɔ credit"],
    "cash_out":      ["cash out", "yi sika", "withdraw"],
}
# Twi number words (SEED) -> ints. Extend fully; also accept spoken digits from ASR.
TWI_NUMBERS = {
    "baako": 1, "mmienu": 2, "mmiɛnsa": 3, "ɛnan": 4, "enum": 5,
    "asia": 6, "ason": 7, "awotwe": 8, "akron": 9, "du": 10,
    "aduonu": 20, "aduonum": 50, "ɔha": 100,
}

class ParseRequest(BaseModel):
    transcript: str
    language: str = "tw"
    contacts: list[str] = []   # names from the device, matched on-device ideally

def detect_action(text: str):
    t = text.lower()
    for action, kws in ACTION_KEYWORDS.items():
        if any(k in t for k in kws):
            return action
    return "unknown"

def extract_amount(text: str):
    m = re.search(r"\b(\d+(?:\.\d{1,2})?)\b", text)     # digits from ASR e.g. "50"
    if m:
        return float(m.group(1))
    for word, val in TWI_NUMBERS.items():                # Twi number words
        if word in text.lower():
            return float(val)
    return None

def match_recipient(text: str, contacts: list[str]):
    for name in contacts:
        if name and name.lower() in text.lower():
            return {"raw": name, "matched_contact": name, "number": None}
    m = re.search(r"\b(0\d{9})\b", text)                 # a raw phone number
    if m:
        return {"raw": m.group(1), "matched_contact": None, "number": m.group(1)}
    return None

def rule_parse(req: ParseRequest) -> dict:
    action = detect_action(req.transcript)
    amount = extract_amount(req.transcript)
    recipient = match_recipient(req.transcript, req.contacts)

    # crude but honest confidence: reward the slots the action actually needs
    conf = 0.0
    if action != "unknown": conf += 0.5
    if action == "check_balance": conf += 0.4
    if action in ("send_money", "cash_out"):
        if amount is not None: conf += 0.25
        if recipient is not None: conf += 0.25
    if action in ("buy_data", "buy_airtime") and amount is not None: conf += 0.4

    return {
        "action": action,
        "amount": amount,
        "recipient": recipient,
        "extra": {},
        "confidence": round(min(conf, 1.0), 2),
        "needs_confirmation": action not in ("check_balance", "unknown"),
        "transcript": req.transcript,
        "language": req.language,
    }

def llm_fallback(req: ParseRequest) -> dict:
    """
    Only used when rule confidence < floor. Call an LLM with a STRICT instruction:
    return ONLY JSON matching config/intent_schema.json; if unsure, action='unknown';
    never invent an amount or recipient. Validate the JSON against the schema before use.
    Left as a stub so money-critical parsing never silently depends on it.
    """
    return None  # TODO: wire an LLM call, validate output, or return None to force a re-ask

@app.post("/parse")
def parse(req: ParseRequest):
    intent = rule_parse(req)
    if intent["confidence"] < CONFIDENCE_FLOOR:
        alt = llm_fallback(req)
        if alt is not None:
            intent = alt
    # The app NEVER executes on this alone: if needs_confirmation, it must get explicit approval,
    # and if confidence is still low it must re-ask rather than proceed.
    return intent

@app.post("/transcribe")
async def transcribe(audio: UploadFile = File(...)):
    """
    Load dcshcilab_Whisper (or whisper-small fine-tuned on our command audio), bias decoding
    toward the command lexicon + digits, return the Twi transcript. Stubbed here.
    """
    _ = await audio.read()
    return {"transcript": "", "language": "tw", "note": "wire Whisper here"}
