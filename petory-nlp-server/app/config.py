from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    app_name: str = "Petory NLP Server"
    debug: bool = False
    confidence_threshold: float = 0.45

settings = Settings()
