from contextlib import asynccontextmanager
import asyncio
from fastapi import FastAPI
from app.config import settings
from app.api.pet_intent_router import router as pet_intent_router
from app.nlp.embedding_model import get_model
from app.nlp.intent_classifier import warm_up


@asynccontextmanager
async def lifespan(app: FastAPI):
    loop = asyncio.get_event_loop()
    # 1. 임베딩 모델 로드 (R8: 첫 요청 timeout 방지)
    await loop.run_in_executor(None, get_model)
    # 2. centroid preload — warm_up은 encode()를 호출하므로 반드시 get_model() 완료 후
    await loop.run_in_executor(None, warm_up)
    yield


app = FastAPI(title=settings.app_name, lifespan=lifespan)
app.include_router(pet_intent_router)


@app.get("/health")
def health():
    return {"status": "ok", "service": settings.app_name}
