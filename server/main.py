"""
KASA Core — inference server (FastAPI).

Endpoints:
  GET  /health       server up?                    -> {"status":"ok"}
  POST /transcribe   audio -> Twi text             (Kelvin's run_asr; stubbed)
  POST /parse        Twi text -> strict Intent     (deterministic + LLM fallback)
  POST /understand   audio -> Intent (transcribe then parse)
  POST /receipt      log a completed action (NO PIN, NO raw audio)

Run for the prototype on a team laptop:
    pip install -r server/requirements.txt
    uvicorn server.main:app --host 0.0.0.0 --port 8000
    ngrok http 8000   # give the https URL to Richmond

Contracts live in ../config/intent_schema.json. Nothing leaves these endpoints
except a schema-valid Intent (or an explicit error) — see 00_START_HERE.md §4.
"""
import json
import time
import uuid
from pathlib import Path

from fastapi import File, FastAPI, Form, UploadFile
from pydantic import BaseModel

from intent_parser import (
    CONFIDENCE_FLOOR,
    llm_fallback,
    rule_parse,
    validate_intent,
)

app = FastAPI(title="KASA Core Inference")

AUDIT_LOG: list[dict] = []
LOG_FILE = Path(__file__).resolve().parent / "audit_log.jsonl"


class ParseRequest(BaseModel):
    transcript: str
    language: str = "tw"
    contacts: list[str] = []   # names from the device


def _unknown_intent(req: ParseRequest) -> dict:
    return {
        "action": "unknown",
        "amount": None,
        "recipient": None,
        "extra": {},
        "confidence": 0.0,
        "needs_confirmation": False,
        "transcript": req.transcript,
        "language": req.language,
    }


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/parse")
def parse(req: ParseRequest):
    intent = rule_parse(req.transcript, req.language, req.contacts)
    if intent["confidence"] < CONFIDENCE_FLOOR:
        alt = llm_fallback(req.transcript, req.language, req.contacts)
        if alt is not None:
            intent = alt

    validated = validate_intent(intent)
    if validated is None:              # a money command never runs unvalidated
        return _unknown_intent(req)

    # needs_confirmation must be true for any action that moves money.
    if validated["action"] in ("send_money", "cash_out", "pay_merchant",
                               "buy_data", "buy_airtime"):
        validated["needs_confirmation"] = True
    return validated


@app.post("/transcribe")
async def transcribe(audio: UploadFile = File(...)):
    """
    Load dcshcilab_Whisper (or whisper-small fine-tuned on our command audio),
    bias decoding toward the command lexicon + digits, return the Twi
    transcript. Kelvin owns this — until then we stub it.
    """
    audio_bytes = await audio.read()
    transcript = await run_asr(audio_bytes)
    return {"transcript": transcript, "language": "tw"}


async def run_asr(audio_bytes: bytes) -> str:
    """
    Agreed signature with Kelvin: run_asr(bytes) -> str.
    Stub returns a fixed Twi sentence until his model lands — then we import
    his module and this function disappears. No other code changes.
    """
    del audio_bytes
    return "fa aduonum kɔma Kofi"


@app.post("/understand")
async def understand(audio: UploadFile = File(...), contacts: str = Form("[]")):
    """The one call Richmond prefers: audio -> transcript -> parsed Intent."""
    audio_bytes = await audio.read()
    transcript = await run_asr(audio_bytes)
    try:
        contact_list = json.loads(contacts or "[]")
    except json.JSONDecodeError:
        contact_list = []
    return parse(ParseRequest(transcript=transcript, language="tw", contacts=contact_list))


# ---- audit log / receipt ---------------------------------------------------

_ACTION_TW = {
    "send_money": "somaa sika",
    "cash_out": "yii sika",
    "pay_merchant": "tuaa ka",
    "buy_data": "tɔɔ data",
    "buy_airtime": "tɔɔ credit",
    "check_balance": "hwɛɛ wo balance",
}


def build_twi_summary(record: dict) -> str:
    """Short Twi sentence the app can speak back later. No PIN, no raw audio."""
    action = _ACTION_TW.get(record.get("action"), record.get("action", ""))
    if record.get("action") == "check_balance":
        return "Wohwɛɛ wo sika dodow."
    name = record.get("recipient_name") or "ɔyɔnko"
    amount = record.get("amount")
    if record.get("result_status") != "success":
        return "Wo " + action + " anyɛ adwuma. Mesrɛ wo, sɔ no bio."
    if amount is not None:
        return f"Wosomaa {amount:g} cedi kɔma {name}. Ɛyɛɛ adwuma yie."
    return f"Wo {action} fũyɛɛ adwuma yie."


@app.post("/receipt")
def receipt(event: dict):
    record = {
        "id": str(uuid.uuid4()),
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "action": event.get("action"),
        "amount": event.get("amount"),
        "recipient_name": (event.get("recipient") or {}).get("matched_contact"),
        "result_status": event.get("result_status", "success"),
        # NEVER the PIN. NEVER raw audio.
    }
    AUDIT_LOG.append(record)
    with LOG_FILE.open("a", encoding="utf-8") as f:
        f.write(json.dumps(record, ensure_ascii=False) + "\n")
    return {"receipt_id": record["id"], "spoken_summary_tw": build_twi_summary(record)}