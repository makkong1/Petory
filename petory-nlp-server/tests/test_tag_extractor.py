import pytest
from app.nlp.tag_extractor import extract_tags
from app.nlp.intent_classifier import _classify_by_rule


# ── 오탐 방지 (tag_extractor) ────────────────────────────────────────────────

def test_no_ear_tag_for_ghost():
    tags = extract_tags("귀신이 나타났어요", "UNKNOWN")
    assert "ear" not in tags


def test_no_eye_tag_for_snowman():
    tags = extract_tags("눈사람 만들었어요", "UNKNOWN")
    assert "eye" not in tags


def test_no_paw_tag_for_development():
    tags = extract_tags("강아지 발전이 빨라요", "UNKNOWN")
    assert "paw" not in tags


# ── 정상 매칭 (tag_extractor) ─────────────────────────────────────────────────

def test_ear_tag_for_ear_scratch():
    tags = extract_tags("강아지가 귀를 자꾸 긁어요", "MEDICAL")
    assert "ear" in tags


def test_scratch_tag_for_scratch():
    tags = extract_tags("강아지가 긁어요", "MEDICAL")
    assert "scratch" in tags


def test_itching_tag():
    # "가려워요" → Kiwi → "가렵"(VA) → _KO_TAG_MAP["가렵"] = "itching"
    tags = extract_tags("강아지가 가려워해요", "MEDICAL")
    assert "itching" in tags


def test_swelling_tag():
    # "부었어요" → Kiwi → "붓"(VV ㅅ 불규칙) → _KO_TAG_MAP["붓"] = "swelling"
    tags = extract_tags("발이 부었어요", "MEDICAL")
    assert "swelling" in tags


def test_boarding_tag():
    # "맡길" → Kiwi → "맡기"(VV) → _KO_TAG_MAP["맡기"] = "boarding"
    tags = extract_tags("강아지 맡길 곳 찾아요", "DAYCARE_BOARDING")
    assert "boarding" in tags


def test_limp_tag():
    # "절뚝"은 XR(어근) 포함 → Kiwi가 "절뚝" 반환 여부 확인
    tags = extract_tags("강아지가 절뚝거려요", "MEDICAL")
    assert "limp" in tags


# ── 오탐 방지 (_classify_by_rule) ────────────────────────────────────────────

def test_ghost_not_medical():
    assert _classify_by_rule("귀신이 나타났어요") is None


def test_cute_not_medical():
    assert _classify_by_rule("강아지가 귀여워요") is None


def test_apartment_not_medical():
    # "아프" substring이 "아파트"에 매칭되지 않아야 함
    assert _classify_by_rule("아파트에 살아요") is None


# ── 정상 분류 (_classify_by_rule) ─────────────────────────────────────────────

def test_ear_scratch_is_medical():
    result = _classify_by_rule("강아지가 귀를 자꾸 긁어요")
    assert result is not None
    _, domain, _ = result
    assert domain == "MEDICAL"


def test_vomit_is_medical():
    result = _classify_by_rule("강아지가 토해요")
    assert result is not None
    _, domain, _ = result
    assert domain == "MEDICAL"


def test_grooming_rule():
    result = _classify_by_rule("강아지 털이 엉켰어요")
    assert result is not None
    _, domain, _ = result
    assert domain == "GROOMING"


def test_daycare_rule():
    result = _classify_by_rule("강아지 맡길 곳 찾아요")
    assert result is not None
    _, domain, _ = result
    assert domain == "DAYCARE_BOARDING"
