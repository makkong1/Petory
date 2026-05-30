import yaml
import numpy as np
from pathlib import Path
from typing import Tuple, Dict, List, Optional
from app.nlp.embedding_model import encode

DATA_PATH = Path(__file__).parent.parent / "data" / "intent_examples.yml"

_intent_embeddings: Dict[str, np.ndarray] = {}
_intent_domains: Dict[str, str] = {}
_intent_labels: List[str] = []

_RULES = [
    ("MEDICAL_CONCERN", "MEDICAL", 0.92, ["병원", "약국", "아파", "아프", "구토", "토해", "설사", "기침", "절뚝", "귀", "긁", "피부", "눈물", "충혈", "밥을 안", "안 먹", "무기력"]),
    ("GROOMING_NEED", "GROOMING", 0.90, ["미용", "목욕", "털", "엉켰", "발톱", "그루밍", "스파"]),
    ("FOOD_SNACK_NEED", "FOOD_SNACK", 0.90, ["사료", "간식", "츄르", "영양제", "처방식", "자연식"]),
    ("SUPPLIES_NEED", "SUPPLIES", 0.88, ["용품", "모래", "목줄", "장난감", "캣타워", "케이지", "옷", "펫샵"]),
    ("DAYCARE_BOARDING", "DAYCARE_BOARDING", 0.90, ["맡길", "맡겨", "위탁", "유치원", "데이케어", "펫시터", "돌봐줄"]),
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

def classify(text: str) -> Tuple[str, str, float]:
    """
    Returns: (intent, intentDomain, confidence)
    """
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

def _classify_by_rule(text: str) -> Optional[Tuple[str, str, float]]:
    normalized = text or ""
    for intent, domain, confidence, keywords in _RULES:
        if any(keyword in normalized for keyword in keywords):
            return intent, domain, confidence
    return None
