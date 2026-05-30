from typing import List
import numpy as np
import hashlib

try:
    from sentence_transformers import SentenceTransformer
except ImportError:
    SentenceTransformer = None

_model = None
MODEL_NAME = "jhgan/ko-sroberta-multitask"
FALLBACK_DIM = 384

def get_model():
    global _model
    if SentenceTransformer is None:
        return None
    if _model is None:
        _model = SentenceTransformer(MODEL_NAME)
    return _model

def encode(texts: List[str]) -> np.ndarray:
    model = get_model()
    if model is not None:
        return model.encode(texts, convert_to_numpy=True, normalize_embeddings=True)
    return np.array([_fallback_encode(text) for text in texts], dtype=np.float32)

def _fallback_encode(text: str) -> np.ndarray:
    """Dependency-light character n-gram vector for local MVP/test fallback."""
    vec = np.zeros(FALLBACK_DIM, dtype=np.float32)
    compact = "".join((text or "").split())
    grams = []
    for n in (1, 2, 3):
        grams.extend(compact[i:i + n] for i in range(max(len(compact) - n + 1, 0)))
    for gram in grams:
        digest = hashlib.md5(gram.encode("utf-8")).digest()
        idx = int.from_bytes(digest[:4], "little") % FALLBACK_DIM
        vec[idx] += 1.0
    norm = np.linalg.norm(vec)
    if norm == 0:
        return vec
    return vec / norm
