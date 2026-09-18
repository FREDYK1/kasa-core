"""
Table-driven safety net for the parser. Run: pytest server/test_parser.py
Each row: (twi_command, expected_action, expected_amount, expected_recipient)
    expected_recipient is the matched_contact full name, or None.
"""
import json

import pytest

from intent_parser import (
    CONFIDENCE_FLOOR,
    extract_amount,
    match_recipient,
    rule_parse,
    safe_llm_intent,
    validate_intent,
)

CONTACTS = ["Kofi Mensah", "Ama Serwaa", "Yaw Boateng", "Abena Owusu", "Kwame Asante"]

# (transcript, action, amount, matched_contact)
POSITIVE_CASES = [
    # check balance
    ("check balance",                     "check_balance", None, None),
    ("me sika dodow",                     "check_balance", None, None),
    ("hwɛ me balance",                    "check_balance", None, None),
    ("sika dodow",                        "check_balance", None, None),
    # send money — Twi amounts and words
    ("fa aduonum kɔma Kofi",              "send_money", 50.0, "Kofi Mensah"),
    ("fa aduonu baako kɔma Ama",          "send_money", 21.0, "Ama Serwaa"),
    ("soma enum cedi kɔma Yaw",           "send_money", 5.0, "Yaw Boateng"),
    ("send 5 cedis to Kofi",              "send_money", 5.0, "Kofi Mensah"),
    ("fa ɔha aduonum kɔma Abena",         "send_money", 150.0, "Abena Owusu"),
    ("mena cedi dubaako kɔma Kwame",      "send_money", 11.0, "Kwame Asante"),
    ("send 20 to 0551234567",             "send_money", 20.0, None),
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
    "fa kɔma Kofi",           # send_money, no amount -> low confidence
    "soma aduasa",            # send_money, no recipient -> low confidence
    "tɔ data",                # buy_data, no amount -> low confidence
    "kɔma Ama erɔwa",         # aside: nonsense recipient should stay low
]

AMBIGUOUS_CONTACTS = ["Kofi Mensah", "Kofi Owusu", "Kwame Asante"]


@pytest.mark.parametrize("text,action,amount,recipient", POSITIVE_CASES)
def test_rule_parse_positive(text, action, amount, recipient):
    intent = rule_parse(text, contacts=CONTACTS)
    assert intent["action"] == action, text
    if amount is None:
        assert intent["amount"] is None, text
    else:
        assert intent["amount"] == pytest.approx(amount), text
    if recipient is None:
        assert intent["recipient"] is None or intent["recipient"]["number"], text
    else:
        assert intent["recipient"] is not None, text
        assert intent["recipient"]["matched_contact"] == recipient, text
    assert validate_intent(intent) is not None, text  # schema-valid


@pytest.mark.parametrize("text", NEGATIVE_CASES)
def test_rule_parse_low_confidence(text):
    intent = rule_parse(text, contacts=CONTACTS)
    assert intent["confidence"] < CONFIDENCE_FLOOR, text
    assert validate_intent(intent) is not None, text


def test_never_pick_wrong_person():
    # Two contacts match "Kofi" equally well -> must refuse, never guess.
    intent = rule_parse("fa aduonum kɔma Kofi", contacts=AMBIGUOUS_CONTACTS)
    assert intent["recipient"] is None
    assert intent["confidence"] < CONFIDENCE_FLOOR


def test_phone_number_recipient():
    r = match_recipient("send 20 cedis to 0551234567 now", CONTACTS)
    assert r is not None
    assert r["number"] == "0551234567"
    assert r["matched_contact"] is None


def test_send_money_confirms():
    intent = rule_parse("fa aduonum kɔma Kofi", contacts=CONTACTS)
    assert intent["needs_confirmation"] is True


def test_check_balance_no_confirmation():
    intent = rule_parse("check balance", contacts=CONTACTS)
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
        "recipient": {"raw": "Kofi", "matched_contact": "Kofi Mensah", "number": None},
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