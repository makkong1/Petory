import yaml
from pathlib import Path
from typing import List
from app.nlp.tokenizer import extract_keywords

DATA_PATH = Path(__file__).parent.parent / "data" / "intent_tags.yml"

_tag_map: dict = {}

def _load():
    global _tag_map
    if _tag_map:
        return
    with open(DATA_PATH) as f:
        data = yaml.safe_load(f)
    _tag_map = data["tags"]

_KO_TAG_MAP = {
    "귀": "ear", "눈": "eye", "피부": "skin", "발": "paw",
    "이빨": "tooth", "치아": "tooth", "구토": "vomit", "설사": "diarrhea",
    "긁": "scratch", "가렵": "itching", "기침": "cough",   # "가려" → "가렵" (ㅂ 불규칙 VA lemma)
    "절뚝": "limp", "절뚝거리": "limp", "식욕": "appetite_loss", "눈물": "discharge",
    "붓": "swelling", "털": "fur", "목욕": "bath", "발톱": "nail",  # "부어" → "붓" (ㅅ 불규칙 VV lemma)
    "미용": "trim", "사료": "kibble", "간식": "snack",
    "영양제": "supplement", "산책": "walk", "공원": "park",
    "카페": "cafe", "식당": "restaurant", "호텔": "hotel",
    "펜션": "pension", "맡기": "boarding", "유치원": "daycare",  # "맡길" → "맡기" (VV 어간)
    "전시": "exhibition", "미술관": "gallery", "박물관": "museum",
    "목줄": "leash", "장난감": "toy", "캣타워": "toy",
}

def extract_tags(text: str, intent_domain: str) -> List[str]:
    """형태소에서 매칭되는 태그 추출 + 도메인 기본 태그 보완."""
    _load()
    keywords = extract_keywords(text)
    matched = set()
    for kw in keywords:
        for ko, en in _KO_TAG_MAP.items():
            if ko == kw:
                matched.add(en)
    # 매칭된 태그가 없으면 도메인 첫 번째 태그로 보완
    if not matched and intent_domain in _tag_map:
        matched.add(_tag_map[intent_domain][0])
    return list(matched)
