import pytest
from app.nlp.intent_classifier import classify

def test_medical():
    intent, domain, conf = classify("우리 강아지가 귀를 자꾸 긁어요")
    assert domain == "MEDICAL", f"expected MEDICAL, got {domain}"
    assert conf >= 0.45, f"confidence too low: {conf}"

def test_grooming():
    intent, domain, conf = classify("강아지 털이 너무 엉켰어요")
    assert domain == "GROOMING", f"expected GROOMING, got {domain}"

def test_cafe():
    intent, domain, conf = classify("강아지랑 카페 가고 싶어요")
    assert domain == "CAFE_DINING", f"expected CAFE_DINING, got {domain}"

def test_lodging():
    intent, domain, conf = classify("강아지랑 펜션 가고 싶어요")
    assert domain == "LODGING_TRAVEL", f"expected LODGING_TRAVEL, got {domain}"
