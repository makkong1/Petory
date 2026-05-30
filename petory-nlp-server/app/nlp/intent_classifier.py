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
