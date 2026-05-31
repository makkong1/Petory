from contextlib import asynccontextmanager
from fastapi import FastAPI
from app.config import settings
from app.api.pet_intent_router import router as pet_intent_router


@asynccontextmanager
async def lifespan(app: FastAPI):
    # R8: 서버 시작 시 임베딩 모델 pre-load — 첫 요청 timeout 방지
    import asyncio
    loop = asyncio.get_event_loop()
    from app.nlp.embedding_model import get_model
    await loop.run_in_executor(None, get_model)
    yield


app = FastAPI(title=settings.app_name, lifespan=lifespan)
app.include_router(pet_intent_router)


@app.get("/health")
def health():
    return {"status": "ok", "service": settings.app_name}
