"""
Table-driven safety net for the parser. Run: pytest server/test_parser.py
Each row: (twi_command, expected_action, expected_amount, expected_raw_recipient)

Per K05 Decision 3, this server never sees contacts and never resolves a name
to a contact — it only pulls the raw recipient text out of the transcript.
Matching that raw text against a person's local trusted-payees list (and
refusing when two payees are too close) is an on-device concern now; see
app/src/main/java/gh/ug/kasacore/payees/PayeesRepository.kt.
"""
import json

import pytest

from intent_parser import (
    CONFIDENCE_FLOOR,
    extract_amount,
    extract_recipient,
    rule_parse,
    safe_llm_intent,
    validate_intent,
)

# (transcript, action, amount, expected_raw_recipient)
POSITIVE_CASES = [
    # check balance
    ("check balance",                     "check_balance", None, None),
    ("me sika dodow",                     "check_balance", None, None),
    ("hwɛ me balance",                    "check_balance", None, None),
    ("sika dodow",                        "check_balance", None, None),
    # send money — Twi amounts and words
    ("fa aduonum kɔma Kofi",              "send_money", 50.0, "Kofi"),
    ("fa aduonu baako kɔma Ama",          "send_money", 21.0, "Ama"),
    ("soma enum cedi kɔma Yaw",           "send_money", 5.0, "Yaw"),
    ("send 5 cedis to Kofi",              "send_money", 5.0, "Kofi"),
    ("fa ɔha aduonum kɔma Abena",         "send_money", 150.0, "Abena"),
    ("mena cedi dubaako kɔma Kwame",      "send_money", 11.0, "Kwame"),
    ("send 20 to 0551234567",             "send_money", 20.0, "0551234567"),
    # buy data / airtime
    ("tɔ data aduonu",                    "buy_data", 20.0, None),
    ("buy data 30",                       "buy_data", 30.0, None),
    ("tɔ credit du",                      "buy_airtime", 10.0, None),
    # cash out
    ("yi sika aduonum",                   "cash_out", 50.0, None),
]

# Commands that must NOT reach the USSD engine unconfirmed / as wrong guesses.
NEGATIVE_CASES = [
    "gibberish zzz zzz",      # unknown action
    "hello",                  # unknown action
    "",                       # empty
    "soma aduasa",            # send_money, no recipient -> low confidence
    "tɔ data",                # buy_data, no amount -> low confidence
]


@pytest.mark.parametrize("text,action,amount,recipient", POSITIVE_CASES)
def test_rule_parse_positive(text, action, amount, recipient):
    intent = rule_parse(text)
    assert intent["action"] == action, text
    if amount is None:
        assert intent["amount"] is None, text
    else:
        assert intent["amount"] == pytest.approx(amount), text
    if recipient is None:
        assert intent["recipient"] is None, text
    else:
        assert intent["recipient"] is not None, text
        assert intent["recipient"]["raw"] == recipient, text
        assert intent["recipient"]["matched_contact"] is None, text  # server never resolves this
    assert validate_intent(intent) is not None, text  # schema-valid


@pytest.mark.parametrize("text", NEGATIVE_CASES)
def test_rule_parse_low_confidence(text):
    intent = rule_parse(text)
    assert intent["confidence"] < CONFIDENCE_FLOOR, text
    assert validate_intent(intent) is not None, text


def test_phone_number_recipient():
    r = extract_recipient("send 20 cedis to 0551234567 now")
    assert r is not None
    assert r["number"] == "0551234567"
    assert r["matched_contact"] is None


def test_name_recipient_is_raw_only_never_resolved():
    # The server extracts the spoken name span but never claims to know who it is.
    r = extract_recipient("fa aduonum kɔma Kofi")
    assert r == {"raw": "Kofi", "matched_contact": None, "number": None}


def test_send_money_confirms():
    intent = rule_parse("fa aduonum kɔma Kofi")
    assert intent["needs_confirmation"] is True


def test_check_balance_no_confirmation():
    intent = rule_parse("check balance")
    assert intent["needs_confirmation"] is False


def test_extract_amount_digit_and_word():
    assert extract_amount("send 50 to kofi") == 50.0
    assert extract_amount("fa aduonum kɔma") == 50.0
    assert extract_amount("fa ɔha aduonum baako") == 151.0


@pytest.mark.parametrize("raw,expected", [
    ("", None),
    ("not json at all", None),
    ("{}", None),                                  # missing required fields
    ("{'action': 'send_money'}", None),            # bad JSON (single quotes)
])
def test_safe_llm_rejects_bad_output(raw, expected):
    assert safe_llm_intent(raw) == expected


def test_safe_llm_accepts_good_output():
    good = json.dumps({
        "action": "send_money", "amount": 5.0,
        "recipient": {"raw": "Kofi", "matched_contact": None, "number": None},
        "extra": {}, "confidence": 0.95, "needs_confirmation": True,
        "transcript": "send five to kofi", "language": "tw",
    })
    assert safe_llm_intent(good) is not None


def test_schema_validator_against_real_schema():
    # A valid intent passes; one with an extra top-level key is rejected.
    good = {"action": "check_balance", "amount": None, "recipient": None,
            "extra": {}, "confidence": 0.9, "needs_confirmation": False,
            "transcript": "check balance", "language": "tw"}
    assert validate_intent(good) is not None
    assert validate_intent({**good, "evil": True}) is None
