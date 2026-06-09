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
    keep_tags = {"NNG", "NNP", "NNB", "VV", "VV-I", "VA", "VA-I", "XR"}
    return [t.form for t in tokens if t.tag in keep_tags]
