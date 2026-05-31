from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    app_name: str = "Petory NLP Server"
    debug: bool = False
    # R9: Python 1차 필터 (0.45 미만 → UNKNOWN 반환)
    # Spring UserPetIntentSignalService의 2차 필터(0.60 미만 → signal 저장 거부)와 조합하여 품질 보호.
    confidence_threshold: float = 0.45


settings = Settings()
