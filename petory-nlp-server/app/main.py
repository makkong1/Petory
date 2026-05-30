from fastapi import FastAPI
from app.config import settings
from app.api.pet_intent_router import router as pet_intent_router

app = FastAPI(title=settings.app_name)
app.include_router(pet_intent_router)

@app.get("/health")
def health():
    return {"status": "ok", "service": settings.app_name}
