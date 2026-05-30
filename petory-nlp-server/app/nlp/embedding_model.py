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
