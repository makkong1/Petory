# Step 3: NLP 코어 — tokenizer + embedding_model + intent_classifier + tag_extractor

## 목표
한국어 형태소 분석(kiwipiepy)과 문장 임베딩(sentence-transformers)을 이용해
반려생활 의도를 분류하고 의도 태그를 추출하는 NLP 코어 모듈을 구현한다.

## 배경
- kiwipiepy: 순수 C++ 기반 한국어 형태소 분석기, JVM 불필요 (`pip install kiwipiepy`)
- sentence-transformers: `jhgan/ko-sroberta-multitask` 모델 사용 (한국어 의미 유사도)
- 분류 방식: `intent_examples.yml`의 레퍼런스 문장들과 cosine similarity 비교 → 가장 유사한 의도 선택
- Step 2에서 생성한 `app/data/intent_examples.yml`, `app/data/intent_tags.yml` 필요

## 생성할 파일

### `petory-nlp-server/app/nlp/tokenizer.py`

```python
from kiwipiepy import Kiwi
from typing import List

_kiwi = None

def get_kiwi() -> Kiwi:
    global _kiwi
    if _kiwi is None:
        _kiwi = Kiwi()
    return _kiwi

def extract_keywords(text: str) -> List[str]:
    """명사(NN*), 동사 어간(VV), 형용사 어간(VA)만 추출."""
    kiwi = get_kiwi()
    tokens = kiwi.tokenize(text)
    keep_tags = {"NNG", "NNP", "NNB", "VV", "VA", "XR"}
    return [t.form for t in tokens if t.tag in keep_tags]
```

### `petory-nlp-server/app/nlp/embedding_model.py`

```python
from sentence_transformers import SentenceTransformer
from typing import List
import numpy as np

_model = None
MODEL_NAME = "jhgan/ko-sroberta-multitask"

def get_model() -> SentenceTransformer:
    global _model
    if _model is None:
        _model = SentenceTransformer(MODEL_NAME)
    return _model

def encode(texts: List[str]) -> np.ndarray:
    return get_model().encode(texts, convert_to_numpy=True, normalize_embeddings=True)
```

### `petory-nlp-server/app/nlp/intent_classifier.py`

```python
import yaml
import numpy as np
from pathlib import Path
from typing import Tuple, Dict, List
from app.nlp.embedding_model import encode

DATA_PATH = Path(__file__).parent.parent / "data" / "intent_examples.yml"

_intent_embeddings: Dict[str, np.ndarray] = {}
_intent_domains: Dict[str, str] = {}
_intent_labels: List[str] = []

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
    _load()
    query_vec = encode([text])[0]
    scores = {
        intent: float(np.dot(query_vec, centroid))
        for intent, centroid in _intent_embeddings.items()
    }
    best_intent = max(scores, key=scores.get)
    return best_intent, _intent_domains[best_intent], scores[best_intent]
```

### `petory-nlp-server/app/nlp/tag_extractor.py`

```python
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

# 한국어 키워드 → 영어 태그 매핑 (간단한 규칙 기반)
_KO_TAG_MAP = {
    "귀": "ear", "눈": "eye", "피부": "skin", "발": "paw",
    "이빨": "tooth", "구토": "vomit", "설사": "diarrhea",
    "긁": "scratch", "가려": "itching", "기침": "cough",
    "절뚝": "limp", "식욕": "appetite_loss", "털": "fur",
    "목욕": "bath", "발톱": "nail", "미용": "trim",
    "간식": "snack", "사료": "kibble", "산책": "walk",
    "카페": "cafe", "식당": "restaurant", "호텔": "hotel",
    "펜션": "pension", "맡길": "boarding", "유치원": "daycare",
    "전시": "exhibition", "미술관": "gallery", "박물관": "museum",
}

def extract_tags(text: str, intent_domain: str) -> List[str]:
    """형태소에서 매칭되는 태그 추출 + 도메인 기본 태그 보완."""
    _load()
    keywords = extract_keywords(text)
    matched = set()
    for kw in keywords:
        for ko, en in _KO_TAG_MAP.items():
            if ko in kw or kw in ko:
                matched.add(en)
    # 매칭된 태그가 없으면 도메인 첫 번째 태그로 보완
    if not matched and intent_domain in _tag_map:
        matched.add(_tag_map[intent_domain][0])
    return list(matched)
```

### `petory-nlp-server/tests/test_intent_classifier.py`

```python
import pytest
from app.nlp.intent_classifier import classify

def test_medical():
    intent, domain, conf = classify("우리 강아지가 귀를 자꾸 긁어요")
    assert domain == "MEDICAL"
    assert conf >= 0.5

def test_grooming():
    intent, domain, conf = classify("강아지 털이 너무 엉켰어요")
    assert domain == "GROOMING"

def test_cafe():
    intent, domain, conf = classify("강아지랑 카페 가고 싶어요")
    assert domain == "CAFE_DINING"

def test_lodging():
    intent, domain, conf = classify("강아지랑 펜션 가고 싶어요")
    assert domain == "LODGING_TRAVEL"
```

## Acceptance Criteria

```bash
cd /Users/maknkkong/project/Petory/petory-nlp-server
source venv/bin/activate
python -m pytest tests/test_intent_classifier.py -v
# 기대: 4개 테스트 모두 PASSED
```

> 첫 실행 시 `jhgan/ko-sroberta-multitask` 모델 다운로드(약 400MB)가 발생한다.
> 이후 실행은 캐시를 사용해 빠르다.
