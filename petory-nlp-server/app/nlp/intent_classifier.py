import yaml
import numpy as np
from pathlib import Path
from typing import Tuple, Dict, List, Optional
from app.nlp.embedding_model import encode
from app.nlp.tokenizer import extract_keywords

DATA_PATH = Path(__file__).parent.parent / "data" / "intent_examples.yml"

_intent_embeddings: Dict[str, np.ndarray] = {}
_intent_domains: Dict[str, str] = {}
_intent_labels: List[str] = []

_RULES = [
    # (intent, domain, rule_confidence, keywords)
    # rule_confidence: 규칙 매칭 강도 휴리스틱 (0.88~0.92).
    # embedding path의 코사인 유사도([-1,1])와 수치 범위는 비슷하지만 의미가 다르다.
    # rule hit 시 embedding 없이 즉시 반환 — 두 값은 직접 경쟁하지 않는다.
    ("MEDICAL_CONCERN", "MEDICAL", 0.92, ["병원", "약국", "아프", "구토", "토하", "설사", "기침", "절뚝", "귀", "긁", "피부", "눈물", "충혈", "밥을 안", "안 먹", "무기력"]),
    # "아파" 제거(아프 중복), "토해" → "토하"(VV lemma), "귀"는 1음절 → _classify_by_rule에서 형태소 exact match
    ("GROOMING_NEED", "GROOMING", 0.90, ["미용", "목욕", "털", "엉켰", "발톱", "그루밍", "스파"]),
    ("FOOD_SNACK_NEED", "FOOD_SNACK", 0.90, ["사료", "간식", "츄르", "영양제", "처방식", "자연식"]),
    ("SUPPLIES_NEED", "SUPPLIES", 0.88, ["용품", "모래", "목줄", "장난감", "캣타워", "케이지", "펫샵"]),
    # "옷" 제거(1음절 오탐 가능, 펫 용품 의도가 약함)
    ("DAYCARE_BOARDING", "DAYCARE_BOARDING", 0.90, ["맡기", "위탁", "유치원", "데이케어", "펫시터", "돌봐줄"]),
    ("LODGING_TRAVEL", "LODGING_TRAVEL", 0.90, ["호텔", "펜션", "숙박", "리조트", "글램핑", "1박", "여행 숙소"]),
    ("CAFE_DINING", "CAFE_DINING", 0.90, ["카페", "식당", "맛집", "외식", "브런치", "레스토랑"]),
    ("WALK_OUTING", "WALK_OUTING", 0.88, ["산책", "공원", "나들이", "뛰어놀", "야외", "관광지", "여행지"]),
    ("CULTURE_SPACE", "CULTURE_SPACE", 0.88, ["미술관", "박물관", "전시", "공연", "문화", "문예회관"]),
]

def _load():
    global _intent_embeddings, _intent_domains, _intent_labels
    if _intent_embeddings:
        return
    with open(DATA_PATH) as f:
        data = yaml.safe_load(f)
    for intent_name, info in data["intents"].items():
        examples = info["examples"]
        vecs = encode(examples)
        _intent_embeddings[intent_name] = vecs.mean(axis=0)
        _intent_domains[intent_name] = info["domain"]
        _intent_labels.append(intent_name)

def classify(text: str, pet_type: Optional[str] = None) -> Tuple[str, str, float]:
    """
    Returns: (intent, intentDomain, confidence)
    pet_type: "DOG" | "CAT" | "OTHER" | None — 시그니처만 열어둠, 분류 로직 미사용.
    이후 DOG/CAT 규칙 분기 시 이 파라미터로 확장 예정.
    """
    _ = pet_type  # DOG/CAT rule branching reserved for future use
    rule_result = _classify_by_rule(text)
    if rule_result is not None:
        return rule_result
    _load()
    query_vec = encode([text])[0]
    scores = {
        intent: float(np.dot(query_vec, centroid))
        for intent, centroid in _intent_embeddings.items()
    }
    best_intent = max(scores, key=scores.get)
    return best_intent, _intent_domains[best_intent], scores[best_intent]


def warm_up() -> None:
    """서버 시작 시 centroid를 미리 계산해 첫 embedding path 요청 지연을 방지한다."""
    _load()


def _classify_by_rule(text: str) -> Optional[Tuple[str, str, float]]:
    """순수 한글 1-2음절 키워드는 형태소 exact match, 구문/3음절+는 raw substring."""
    normalized = text or ""
    _kw_tokens: Optional[set] = None  # lazy — rule miss가 많으면 호출 안 함

    for intent, domain, confidence, keywords in _RULES:
        for keyword in keywords:
            if " " in keyword:
                # 구문 키워드("밥을 안", "안 먹" 등): raw substring
                matched = keyword in normalized
            elif len(keyword) <= 2 and keyword.isalpha():
                # 순수 한글 1-2음절("귀", "털" 등): 오탐 방지 — 형태소 exact match
                if _kw_tokens is None:
                    _kw_tokens = set(extract_keywords(normalized))
                matched = keyword in _kw_tokens
            else:
                # 3음절+ 또는 숫자 포함("엉켰", "맡길", "1박" 등): raw substring
                matched = keyword in normalized
            if matched:
                return intent, domain, confidence
    return None
