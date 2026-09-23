"""
End-to-end tests of the FastAPI endpoints using an in-process TestClient.
Covers the HTTP contract in 00_START_HERE.md §4.2. Run: pytest server/test_api.py

The /understand endpoint uses the stubbed ASR (fixed Twi transcript), which is
exactly how Selorm builds before Kelvin's model lands.

Per K05 Decision 3, this server never receives contacts and never resolves a
name to a contact — /parse and /understand take no contacts field, and
recipient.matched_contact is always null in every response. Matching the raw
recipient text against a person's local payees (and refusing when two are too
close) is done on-device; see PayeesRepository.kt.
"""
import json

import pytest
from fastapi.testclient import TestClient

from main import app

client = TestClient(app)


def _parse(transcript):
    return client.post("/parse", json={"transcript": transcript, "language": "tw"})


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
    assert body["recipient"]["raw"] == "Kofi"
    assert body["recipient"]["matched_contact"] is None  # server never resolves this
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


def test_parse_never_claims_to_resolve_a_contact():
    # The server has no contacts to be ambiguous about; it only ever returns
    # the raw spoken text. Picking (or refusing to pick) the right person from
    # a payees list is entirely the app's job now.
    body = _parse("fa aduonum kɔma Kofi").json()
    assert body["recipient"]["matched_contact"] is None


def test_parse_ignores_a_contacts_field_if_sent_by_an_old_client():
    # Backward compatibility: an old client that still posts "contacts" must
    # not break the server; the field is simply not part of the contract.
    r = client.post("/parse", json={
        "transcript": "check balance", "language": "tw",
        "contacts": ["Kofi Mensah"],
    })
    assert r.status_code == 200
    assert r.json()["language"] == "tw"


# ---- /transcribe (stubbed until Kelvin) ------------------------------------

def test_transcribe_returns_twi_transcript():
    r = client.post("/transcribe", files={"audio": ("x.wav", b"bytes", "audio/wav")})
    assert r.status_code == 200
    body = r.json()
    assert body["language"] == "tw"
    assert isinstance(body["transcript"], str)  # stub -> fixed Twi string


# ---- /understand (the call Richmond prefers) --------------------------------

def test_understand_returns_intent():
    r = client.post("/understand", files={"audio": ("x.wav", b"bytes", "audio/wav")})
    assert r.status_code == 200
    body = r.json()
    assert body["action"] == "send_money"      # stub transcript is a send command
    assert body["needs_confirmation"] is True
    assert body["recipient"]["matched_contact"] is None


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
