from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    app_name: str = "Petory NLP Server"
    debug: bool = False
    # R9: Python 1차 필터 — 임베딩 경로(코사인 유사도)에만 적용. 규칙 경로는 항상 통과.
    # 규칙 hit confidence(0.88~0.92)는 휴리스틱 값으로 코사인 유사도와 직접 비교 불가.
    # Spring UserPetIntentSignalService 2차 필터(0.60 미만 → 저장 거부)와 조합으로 이중 품질 보호.
    confidence_threshold: float = 0.45


settings = Settings()
