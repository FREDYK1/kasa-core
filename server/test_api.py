"""
End-to-end tests of the FastAPI endpoints using an in-process TestClient.
Covers the HTTP contract in 00_START_HERE.md §4.2. Run: pytest server/test_api.py

The /understand endpoint uses the stubbed ASR (fixed Twi transcript), which is
exactly how Selorm builds before Kelvin's model lands.
"""
import json

import pytest
from fastapi.testclient import TestClient

from main import app

client = TestClient(app)

CONTACTS = ["Kofi Mensah", "Ama Serwaa", "Yaw Boateng", "Abena Owusu", "Kwame Asante"]


def _parse(transcript, contacts=CONTACTS):
    return client.post("/parse", json={"transcript": transcript, "language": "tw",
                                      "contacts": contacts})


# ---- /health ---------------------------------------------------------------

def test_health():
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json() == {"status": "ok"}


# ---- /parse: happy path ----------------------------------------------------

def test_parse_send_money():
    r = _parse("fa aduonum kɔma Kofi")
    assert r.status_code == 200
    body = r.json()
    assert body["action"] == "send_money"
    assert body["amount"] == 50.0
    assert body["recipient"]["matched_contact"] == "Kofi Mensah"
    assert body["confidence"] >= 0.6
    assert body["needs_confirmation"] is True


def test_parse_check_balance():
    body = _parse("check balance").json()
    assert body["action"] == "check_balance"
    assert body["needs_confirmation"] is False


def test_parse_buy_data():
    body = _parse("tɔ data aduonu").json()
    assert body["action"] == "buy_data"
    assert body["amount"] == 20.0
    assert body["needs_confirmation"] is True


# ---- /parse: every response must be schema-valid ---------------------------

@pytest.mark.parametrize("transcript", [
    "check balance",
    "fa aduonum kɔma Kofi",
    "tɔ data aduonu",
    "yi sika aduonum",
    "gibberish zzz",
    "",
])
def test_parse_always_schema_valid(transcript):
    body = _parse(transcript).json()
    keys = {"action", "amount", "recipient", "extra", "confidence",
            "needs_confirmation", "transcript", "language"}
    assert set(body.keys()) == keys
    assert isinstance(body["action"], str)
    assert isinstance(body["confidence"], (int, float)) and 0 <= body["confidence"] <= 1


# ---- /parse: fail-closed behaviour -----------------------------------------

def test_parse_nonsense_stays_unknown():
    body = _parse("zzz random noise").json()
    assert body["action"] == "unknown"
    assert body["confidence"] < 0.6


def test_parse_incomplete_send_money_under_floor():
    body = _parse("fa kɔma Kofi").json()   # no amount
    assert body["action"] == "send_money"
    assert body["confidence"] < 0.6        # app must re-ask, never guess


def test_parse_missing_recipient_under_floor():
    body = _parse("soma aduasa").json()    # no recipient
    assert body["confidence"] < 0.6


def test_parse_ambiguous_never_picks_wrong_person():
    body = _parse("fa aduonum kɔma Kofi", contacts=["Kofi Mensah", "Kofi Owusu"]).json()
    assert body["recipient"] is None
    assert body["confidence"] < 0.6


def test_parse_unknown_language_field():
    body = _parse("check balance", contacts=[]).json()
    assert body["language"] == "tw"


# ---- /transcribe (stubbed until Kelvin) ------------------------------------

def test_transcribe_returns_twi_transcript():
    r = client.post("/transcribe", files={"audio": ("x.wav", b"bytes", "audio/wav")})
    assert r.status_code == 200
    body = r.json()
    assert body["language"] == "tw"
    assert isinstance(body["transcript"], str)  # stub -> fixed Twi string


# ---- /understand (the call Richmond prefers) --------------------------------

def test_understand_returns_intent():
    r = client.post("/understand",
                    files={"audio": ("x.wav", b"bytes", "audio/wav")},
                    data={"contacts": json.dumps(CONTACTS)})
    assert r.status_code == 200
    body = r.json()
    assert body["action"] == "send_money"      # stub transcript is a send command
    assert body["needs_confirmation"] is True


def test_understand_bad_contacts_does_not_crash():
    r = client.post("/understand",
                    files={"audio": ("x.wav", b"bytes", "audio/wav")},
                    data={"contacts": "not-json"})
    assert r.status_code == 200
    assert r.json()["action"] in {"send_money"}


# ---- /receipt ---------------------------------------------------------------

def test_receipt_logs_and_returns_twi_summary():
    r = client.post("/receipt", json={
        "action": "send_money",
        "amount": 50.0,
        "recipient": {"matched_contact": "Kofi Mensah"},
        "result_status": "success",
    })
    assert r.status_code == 200
    body = r.json()
    assert body["receipt_id"]
    assert "Kofi Mensah" in body["spoken_summary_tw"]
    assert "50" in body["spoken_summary_tw"]


def test_receipt_never_contains_credentials():
    # A hostile client sends a PIN field; it must never land in the log/response.
    pins = ["1234", "0000"]
    for pin in pins:
        r = client.post("/receipt", json={
            "action": "send_money",
            "amount": 20.0,
            "pin": pin,
            "recipient": {"matched_contact": "Ama Serwaa"},
        })
        assert pin not in json.dumps(r.json())
    # And the raw audit entries have none of those keys either.
    from main import AUDIT_LOG
    for entry in AUDIT_LOG:
        assert "pin" not in str(entry).lower()
        assert "password" not in str(entry).lower()