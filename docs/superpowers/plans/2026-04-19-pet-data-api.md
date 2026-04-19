# Pet Data API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 공공데이터 기반 유기동물 데이터를 수집·저장하고 FastAPI REST API로 제공하는 Python 백엔드 서버 구축.

**Architecture:** 공공데이터포털 API에서 매일 19:00 데이터를 수집해 PostgreSQL에 upsert하고, Materialized View로 통계를 제공한다. 조회는 Keyset 페이지네이션, 검색은 pg_trgm GIN 인덱스, 인증은 SHA-256 해싱 API Key를 사용한다.

**Tech Stack:** Python 3.11+, FastAPI, SQLAlchemy 2.0 (async/asyncpg), PostgreSQL 15+, APScheduler, httpx, pydantic-settings, pytest-asyncio

---

> **주의:** 이 프로젝트는 Petory와 별개의 새 레포다. 아래 모든 경로는 `pet-data-api/` 루트 기준.

---

### Task 1: 프로젝트 스캐폴딩

**Files:**
- Create: `requirements.txt`
- Create: `.env.example`
- Create: `app/__init__.py` (빈 파일)
- Create: `app/core/__init__.py`, `app/models/__init__.py`, `app/schemas/__init__.py`, `app/api/__init__.py`, `app/collector/__init__.py`, `app/scheduler/__init__.py`
- Create: `tests/__init__.py`, `tests/conftest.py`
- Create: `migrations/init.sql`

- [ ] **Step 1: 새 디렉토리 생성**

```bash
mkdir -p pet-data-api/{app/{core,models,schemas,api,collector,scheduler},migrations,tests}
cd pet-data-api
touch app/__init__.py app/core/__init__.py app/models/__init__.py
touch app/schemas/__init__.py app/api/__init__.py
touch app/collector/__init__.py app/scheduler/__init__.py
touch tests/__init__.py
```

- [ ] **Step 2: requirements.txt 작성**

`requirements.txt`:
```
fastapi==0.115.0
uvicorn[standard]==0.30.0
sqlalchemy[asyncio]==2.0.35
asyncpg==0.29.0
httpx==0.27.0
apscheduler==3.10.4
pydantic-settings==2.5.2
python-dotenv==1.0.1
pytest==8.3.3
pytest-asyncio==0.24.0
httpx==0.27.0
```

- [ ] **Step 3: 의존성 설치 및 확인**

```bash
python -m venv venv && source venv/bin/activate
pip install -r requirements.txt
python -c "import fastapi, sqlalchemy, asyncpg; print('OK')"
```
Expected: `OK`

- [ ] **Step 4: .env.example 작성**

`.env.example`:
```
DATABASE_URL=postgresql+asyncpg://postgres:password@localhost:5432/petdata
API_KEY_HASH=<sha256-of-your-api-key>
ADMIN_API_KEY_HASH=<sha256-of-your-admin-api-key>
PUBLIC_DATA_API_KEY=<data.go.kr-service-key>
```

- [ ] **Step 5: tests/conftest.py 기본 작성**

`tests/conftest.py`:
```python
import pytest
import pytest_asyncio
from httpx import AsyncClient, ASGITransport


@pytest.fixture(scope="session")
def anyio_backend():
    return "asyncio"
```

- [ ] **Step 6: git init + 첫 커밋**

```bash
git init
echo "venv/\n.env\n__pycache__/\n*.pyc\n.pytest_cache/" > .gitignore
git add .
git commit -m "chore: 프로젝트 초기 스캐폴딩"
```

---

### Task 2: DB 초기화 SQL (테이블 + 인덱스 + Materialized View)

**Files:**
- Create: `migrations/init.sql`

- [ ] **Step 1: PostgreSQL 데이터베이스 생성**

```bash
psql -U postgres -c "CREATE DATABASE petdata;"
psql -U postgres -c "CREATE DATABASE petdata_test;"
```
Expected: `CREATE DATABASE` 두 번

- [ ] **Step 2: init.sql 작성**

`migrations/init.sql`:
```sql
-- pg_trgm 확장 (한국어 포함 trigram 유사도 검색)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 유기동물 테이블
CREATE TABLE IF NOT EXISTS abandoned_animals (
    id          SERIAL PRIMARY KEY,
    notice_no   VARCHAR(100) NOT NULL UNIQUE,
    animal_type VARCHAR(20),
    breed       VARCHAR(100),
    age         VARCHAR(50),
    gender      VARCHAR(10),
    region      VARCHAR(100),
    shelter_name VARCHAR(200),
    status      VARCHAR(30),
    notice_date DATE,
    collected_at TIMESTAMP DEFAULT NOW()
);

-- 수집 로그 테이블
CREATE TABLE IF NOT EXISTS collection_logs (
    id            SERIAL PRIMARY KEY,
    source        VARCHAR(100) NOT NULL,
    status        VARCHAR(20)  NOT NULL,  -- success / partial / failed
    total_fetched INT          DEFAULT 0,
    total_saved   INT          DEFAULT 0,
    error_message TEXT,
    started_at    TIMESTAMP    NOT NULL,
    finished_at   TIMESTAMP
);

-- 인덱스
CREATE UNIQUE INDEX IF NOT EXISTS idx_animals_notice_no
    ON abandoned_animals(notice_no);

CREATE INDEX IF NOT EXISTS idx_animals_region_status_type
    ON abandoned_animals(region, status, animal_type);

CREATE INDEX IF NOT EXISTS idx_animals_notice_date
    ON abandoned_animals(notice_date);

CREATE INDEX IF NOT EXISTS idx_animals_breed_trgm
    ON abandoned_animals USING gin(breed gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_animals_shelter_trgm
    ON abandoned_animals USING gin(shelter_name gin_trgm_ops);

-- Materialized View (통계)
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_region_stats AS
SELECT
    region,
    DATE(notice_date)                                    AS date,
    COUNT(*)                                             AS total_count,
    COUNT(*) FILTER (WHERE status = '입양')              AS adopted_count,
    COUNT(*) FILTER (WHERE status = '안락사')            AS euthanized_count
FROM abandoned_animals
GROUP BY region, DATE(notice_date);

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_region_stats_region_date
    ON mv_region_stats(region, date);
```

- [ ] **Step 3: SQL 실행 및 확인**

```bash
psql -U postgres -d petdata -f migrations/init.sql
psql -U postgres -d petdata_test -f migrations/init.sql
psql -U postgres -d petdata -c "\dt"
```
Expected: `abandoned_animals`, `collection_logs` 테이블 목록 출력

- [ ] **Step 4: 커밋**

```bash
git add migrations/init.sql
git commit -m "chore: DB 초기화 SQL (테이블, 인덱스, Materialized View)"
```

---

### Task 3: Core Config + DB 세션

**Files:**
- Create: `app/core/config.py`
- Create: `app/core/database.py`

- [ ] **Step 1: .env 파일 생성 (로컬 개발용)**

```bash
cp .env.example .env
# .env 파일에 실제 값 입력:
# DATABASE_URL=postgresql+asyncpg://postgres:password@localhost:5432/petdata
# API_KEY_HASH=<아래 Step에서 생성>
# ADMIN_API_KEY_HASH=<아래 Step에서 생성>
# PUBLIC_DATA_API_KEY=<data.go.kr에서 발급받은 키>
```

- [ ] **Step 2: API Key 해시 생성 (개발용)**

```python
# 터미널에서 실행:
python3 -c "
import secrets, hashlib
key = secrets.token_hex(32)
hashed = hashlib.sha256(key.encode()).hexdigest()
print(f'KEY={key}')
print(f'HASH={hashed}')
"
```
출력된 HASH 값을 `.env`의 `API_KEY_HASH`, `ADMIN_API_KEY_HASH`에 각각 입력.

- [ ] **Step 3: config.py 작성**

`app/core/config.py`:
```python
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    DATABASE_URL: str
    API_KEY_HASH: str
    ADMIN_API_KEY_HASH: str
    PUBLIC_DATA_API_KEY: str

    model_config = {"env_file": ".env"}


settings = Settings()
```

- [ ] **Step 4: database.py 작성**

`app/core/database.py`:
```python
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
from sqlalchemy.orm import DeclarativeBase
from app.core.config import settings

engine = create_async_engine(settings.DATABASE_URL, echo=False)
AsyncSessionLocal = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)


class Base(DeclarativeBase):
    pass


async def get_db():
    async with AsyncSessionLocal() as session:
        yield session
```

- [ ] **Step 5: DB 연결 확인**

```python
# 터미널에서 실행:
python3 -c "
import asyncio
from app.core.database import engine
async def check():
    async with engine.connect() as conn:
        print('DB connected OK')
asyncio.run(check())
"
```
Expected: `DB connected OK`

- [ ] **Step 6: 커밋**

```bash
git add app/core/config.py app/core/database.py
git commit -m "feat: core config, async DB 세션 설정"
```

---

### Task 4: SQLAlchemy 모델

**Files:**
- Create: `app/models/animal.py`
- Create: `app/models/log.py`

- [ ] **Step 1: 실패 테스트 작성**

`tests/test_models.py`:
```python
from app.models.animal import AbandonedAnimal
from app.models.log import CollectionLog


def test_abandoned_animal_tablename():
    assert AbandonedAnimal.__tablename__ == "abandoned_animals"


def test_collection_log_tablename():
    assert CollectionLog.__tablename__ == "collection_logs"
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
pytest tests/test_models.py -v
```
Expected: `ImportError` 또는 `ModuleNotFoundError`

- [ ] **Step 3: animal.py 모델 작성**

`app/models/animal.py`:
```python
from datetime import datetime
from sqlalchemy import Integer, String, Date, DateTime
from sqlalchemy.orm import Mapped, mapped_column
from app.core.database import Base


class AbandonedAnimal(Base):
    __tablename__ = "abandoned_animals"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    notice_no: Mapped[str] = mapped_column(String(100), unique=True, nullable=False)
    animal_type: Mapped[str | None] = mapped_column(String(20))
    breed: Mapped[str | None] = mapped_column(String(100))
    age: Mapped[str | None] = mapped_column(String(50))
    gender: Mapped[str | None] = mapped_column(String(10))
    region: Mapped[str | None] = mapped_column(String(100))
    shelter_name: Mapped[str | None] = mapped_column(String(200))
    status: Mapped[str | None] = mapped_column(String(30))
    notice_date: Mapped[datetime | None] = mapped_column(Date)
    collected_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)
```

- [ ] **Step 4: log.py 모델 작성**

`app/models/log.py`:
```python
from datetime import datetime
from sqlalchemy import Integer, String, Text, DateTime
from sqlalchemy.orm import Mapped, mapped_column
from app.core.database import Base


class CollectionLog(Base):
    __tablename__ = "collection_logs"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    source: Mapped[str] = mapped_column(String(100), nullable=False)
    status: Mapped[str] = mapped_column(String(20), nullable=False)
    total_fetched: Mapped[int] = mapped_column(Integer, default=0)
    total_saved: Mapped[int] = mapped_column(Integer, default=0)
    error_message: Mapped[str | None] = mapped_column(Text)
    started_at: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    finished_at: Mapped[datetime | None] = mapped_column(DateTime)
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
pytest tests/test_models.py -v
```
Expected: 2 PASSED

- [ ] **Step 6: 커밋**

```bash
git add app/models/ tests/test_models.py
git commit -m "feat: AbandonedAnimal, CollectionLog SQLAlchemy 모델"
```

---

### Task 5: API Key 인증

**Files:**
- Create: `app/core/auth.py`
- Create: `tests/test_auth.py`

- [ ] **Step 1: 실패 테스트 작성**

`tests/test_auth.py`:
```python
import hashlib
import pytest
from fastapi import HTTPException
from app.core.auth import hash_key, verify_key, require_api_key, require_admin_key


def test_hash_key_is_sha256():
    key = "testkey"
    expected = hashlib.sha256(key.encode()).hexdigest()
    assert hash_key(key) == expected


def test_verify_key_correct():
    key = "mykey"
    hashed = hash_key(key)
    assert verify_key(key, hashed) is True


def test_verify_key_wrong():
    assert verify_key("wrong", hash_key("correct")) is False
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
pytest tests/test_auth.py -v
```
Expected: `ImportError`

- [ ] **Step 3: auth.py 작성**

`app/core/auth.py`:
```python
import hashlib
from fastapi import Header, HTTPException, status
from app.core.config import settings


def hash_key(key: str) -> str:
    return hashlib.sha256(key.encode()).hexdigest()


def verify_key(key: str, hashed: str) -> bool:
    return hash_key(key) == hashed


async def require_api_key(x_api_key: str = Header(..., alias="X-API-Key")):
    if not (
        verify_key(x_api_key, settings.API_KEY_HASH)
        or verify_key(x_api_key, settings.ADMIN_API_KEY_HASH)
    ):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid API Key")


async def require_admin_key(x_api_key: str = Header(..., alias="X-API-Key")):
    if not verify_key(x_api_key, settings.ADMIN_API_KEY_HASH):
        if verify_key(x_api_key, settings.API_KEY_HASH):
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Admin key required")
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid API Key")
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
pytest tests/test_auth.py -v
```
Expected: 3 PASSED

- [ ] **Step 5: 커밋**

```bash
git add app/core/auth.py tests/test_auth.py
git commit -m "feat: SHA-256 API Key 인증 (일반/관리자 분리)"
```

---

### Task 6: Pydantic 스키마

**Files:**
- Create: `app/schemas/animal.py`
- Create: `app/schemas/stats.py`

- [ ] **Step 1: animal.py 스키마 작성**

`app/schemas/animal.py`:
```python
from datetime import date, datetime
from pydantic import BaseModel


class AnimalResponse(BaseModel):
    id: int
    notice_no: str
    animal_type: str | None
    breed: str | None
    age: str | None
    gender: str | None
    region: str | None
    shelter_name: str | None
    status: str | None
    notice_date: date | None
    collected_at: datetime

    model_config = {"from_attributes": True}


class AnimalListResponse(BaseModel):
    items: list[AnimalResponse]
    next_cursor: int | None
    has_next: bool
```

- [ ] **Step 2: stats.py 스키마 작성**

`app/schemas/stats.py`:
```python
from datetime import date
from pydantic import BaseModel


class RegionStatResponse(BaseModel):
    region: str
    date: date
    total_count: int
    adopted_count: int
    euthanized_count: int


class TrendResponse(BaseModel):
    region: str
    year: int
    month: int
    total_count: int
    adopted_count: int
    euthanized_count: int
```

- [ ] **Step 3: 스키마 임포트 확인**

```bash
python3 -c "from app.schemas.animal import AnimalResponse, AnimalListResponse; from app.schemas.stats import RegionStatResponse, TrendResponse; print('OK')"
```
Expected: `OK`

- [ ] **Step 4: 커밋**

```bash
git add app/schemas/
git commit -m "feat: Pydantic 응답 스키마 (animal, stats)"
```

---

### Task 7: 공공데이터 수집기

**Files:**
- Create: `app/collector/client.py`
- Create: `app/collector/parser.py`
- Create: `app/collector/runner.py`
- Create: `tests/test_collector.py`

- [ ] **Step 1: 실패 테스트 작성**

`tests/test_collector.py`:
```python
import pytest
from app.collector.parser import parse_animal_item


def test_parse_animal_item_maps_fields():
    raw = {
        "noticeNo": "충남-천안-2024-00001",
        "upKindNm": "개",
        "kindNm": "믹스견",
        "age": "2023(년생)",
        "sexCd": "M",
        "orgNm": "충남 천안시",
        "careNm": "천안시보호소",
        "processState": "보호중",
        "noticeSdt": "20240101",
    }
    result = parse_animal_item(raw)
    assert result["notice_no"] == "충남-천안-2024-00001"
    assert result["animal_type"] == "개"
    assert result["breed"] == "믹스견"
    assert result["gender"] == "M"
    assert result["status"] == "보호중"


def test_parse_animal_item_missing_field_returns_none():
    raw = {"noticeNo": "test-001"}
    result = parse_animal_item(raw)
    assert result["notice_no"] == "test-001"
    assert result["breed"] is None
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
pytest tests/test_collector.py -v
```
Expected: `ImportError`

- [ ] **Step 3: client.py 작성**

`app/collector/client.py`:
```python
import asyncio
import httpx

BASE_URL = "http://apis.data.go.kr/1543061/abandonmentPublicSrvc/abandonmentPublic"
RETRY_DELAYS = [1, 2, 4]


async def fetch_abandoned_animals(service_key: str, page: int = 1, num_of_rows: int = 1000) -> dict:
    params = {
        "serviceKey": service_key,
        "pageNo": page,
        "numOfRows": num_of_rows,
        "_type": "json",
    }
    last_error = None
    for delay in [0] + RETRY_DELAYS:
        if delay:
            await asyncio.sleep(delay)
        try:
            async with httpx.AsyncClient(timeout=30) as client:
                response = await client.get(BASE_URL, params=params)
                response.raise_for_status()
                return response.json()
        except Exception as e:
            last_error = e
    raise last_error
```

- [ ] **Step 4: parser.py 작성**

`app/collector/parser.py`:
```python
from datetime import date


def parse_animal_item(raw: dict) -> dict:
    notice_date_str = raw.get("noticeSdt")
    notice_date = None
    if notice_date_str and len(notice_date_str) == 8:
        try:
            notice_date = date(
                int(notice_date_str[:4]),
                int(notice_date_str[4:6]),
                int(notice_date_str[6:8]),
            )
        except ValueError:
            pass

    return {
        "notice_no": raw.get("noticeNo"),
        "animal_type": raw.get("upKindNm"),
        "breed": raw.get("kindNm"),
        "age": raw.get("age"),
        "gender": raw.get("sexCd"),
        "region": raw.get("orgNm"),
        "shelter_name": raw.get("careNm"),
        "status": raw.get("processState"),
        "notice_date": notice_date,
    }


def extract_items(response: dict) -> list[dict]:
    try:
        items = response["response"]["body"]["items"]["item"]
        if isinstance(items, dict):
            items = [items]
        return [parse_animal_item(item) for item in items]
    except (KeyError, TypeError):
        return []
```

- [ ] **Step 5: runner.py 작성**

`app/collector/runner.py`:
```python
from datetime import datetime
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession
from app.collector.client import fetch_abandoned_animals
from app.collector.parser import extract_items
from app.core.config import settings
from app.models.log import CollectionLog


async def run_collection(db: AsyncSession) -> CollectionLog:
    log = CollectionLog(
        source="abandonmentPublic",
        status="failed",
        started_at=datetime.utcnow(),
    )
    db.add(log)
    await db.flush()

    try:
        response = await fetch_abandoned_animals(settings.PUBLIC_DATA_API_KEY)
        items = extract_items(response)
        log.total_fetched = len(items)

        saved = 0
        for item in items:
            if not item.get("notice_no"):
                continue
            await db.execute(
                text("""
                    INSERT INTO abandoned_animals
                        (notice_no, animal_type, breed, age, gender, region,
                         shelter_name, status, notice_date, collected_at)
                    VALUES
                        (:notice_no, :animal_type, :breed, :age, :gender, :region,
                         :shelter_name, :status, :notice_date, NOW())
                    ON CONFLICT (notice_no) DO UPDATE SET
                        animal_type  = EXCLUDED.animal_type,
                        breed        = EXCLUDED.breed,
                        age          = EXCLUDED.age,
                        gender       = EXCLUDED.gender,
                        region       = EXCLUDED.region,
                        shelter_name = EXCLUDED.shelter_name,
                        status       = EXCLUDED.status,
                        notice_date  = EXCLUDED.notice_date,
                        collected_at = NOW()
                """),
                item,
            )
            saved += 1

        log.total_saved = saved
        log.status = "success" if saved == len(items) else "partial"
        await db.commit()

        # 통계 MV 갱신
        await db.execute(text("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_region_stats"))
        await db.commit()

    except Exception as e:
        await db.rollback()
        log.error_message = str(e)
        log.status = "failed"
        await db.commit()

    log.finished_at = datetime.utcnow()
    await db.commit()
    return log
```

- [ ] **Step 6: 파서 테스트 통과 확인**

```bash
pytest tests/test_collector.py -v
```
Expected: 2 PASSED

- [ ] **Step 7: 커밋**

```bash
git add app/collector/ tests/test_collector.py
git commit -m "feat: 공공데이터 수집기 (client, parser, runner)"
```

---

### Task 8: Animals API

**Files:**
- Create: `app/api/animals.py`
- Create: `tests/test_animals.py`

- [ ] **Step 1: 실패 테스트 작성**

`tests/test_animals.py`:
```python
import hashlib
import pytest
from httpx import AsyncClient, ASGITransport
from unittest.mock import AsyncMock, patch


API_KEY = "testkey"
API_KEY_HASH = hashlib.sha256(API_KEY.encode()).hexdigest()
HEADERS = {"X-API-Key": API_KEY}


@pytest.fixture
def mock_settings(monkeypatch):
    monkeypatch.setattr("app.core.config.settings.API_KEY_HASH", API_KEY_HASH)
    monkeypatch.setattr("app.core.config.settings.ADMIN_API_KEY_HASH", "different_hash")


@pytest.mark.asyncio
async def test_list_animals_requires_api_key(mock_settings):
    from app.main import app
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        response = await client.get("/animals")
    assert response.status_code == 422  # Header missing


@pytest.mark.asyncio
async def test_list_animals_invalid_key(mock_settings):
    from app.main import app
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        response = await client.get("/animals", headers={"X-API-Key": "wrongkey"})
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_limit_max_100(mock_settings):
    from app.main import app
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        response = await client.get("/animals?limit=200", headers=HEADERS)
    assert response.status_code == 422
```

- [ ] **Step 2: app/main.py 기본 뼈대 작성 (테스트 실행을 위해)**

`app/main.py`:
```python
from fastapi import FastAPI

app = FastAPI(title="Pet Data API")
```

- [ ] **Step 3: 테스트 실패 확인 (앱은 뜨지만 /animals 없음)**

```bash
pytest tests/test_animals.py::test_list_animals_requires_api_key -v
```
Expected: FAIL (404 아닌 422 기대하는데 404 나옴)

- [ ] **Step 4: animals.py API 작성**

`app/api/animals.py`:
```python
from fastapi import APIRouter, Depends, Query, HTTPException, status
from sqlalchemy import select, text
from sqlalchemy.ext.asyncio import AsyncSession
from app.core.database import get_db
from app.core.auth import require_api_key
from app.models.animal import AbandonedAnimal
from app.schemas.animal import AnimalListResponse, AnimalResponse

router = APIRouter(prefix="/animals", tags=["animals"])


@router.get("", response_model=AnimalListResponse)
async def list_animals(
    cursor: int = Query(0, ge=0),
    limit: int = Query(20, ge=1, le=100),
    region: str | None = None,
    status_filter: str | None = Query(None, alias="status"),
    animal_type: str | None = None,
    search: str | None = None,
    db: AsyncSession = Depends(get_db),
    _: None = Depends(require_api_key),
):
    if search:
        result = await db.execute(
            text("""
                SELECT * FROM abandoned_animals
                WHERE (:cursor = 0 OR id > :cursor)
                  AND breed % :search
                ORDER BY similarity(breed, :search) DESC, id ASC
                LIMIT :limit
            """),
            {"cursor": cursor, "search": search, "limit": limit + 1},
        )
        rows = result.mappings().all()
        items = [AbandonedAnimal(**dict(r)) for r in rows]
    else:
        stmt = select(AbandonedAnimal)
        if cursor:
            stmt = stmt.where(AbandonedAnimal.id > cursor)
        if region:
            stmt = stmt.where(AbandonedAnimal.region == region)
        if status_filter:
            stmt = stmt.where(AbandonedAnimal.status == status_filter)
        if animal_type:
            stmt = stmt.where(AbandonedAnimal.animal_type == animal_type)
        stmt = stmt.order_by(AbandonedAnimal.id).limit(limit + 1)
        result = await db.execute(stmt)
        items = list(result.scalars().all())

    has_next = len(items) > limit
    items = items[:limit]
    next_cursor = items[-1].id if has_next and items else None
    return AnimalListResponse(items=items, next_cursor=next_cursor, has_next=has_next)


@router.get("/{animal_id}", response_model=AnimalResponse)
async def get_animal(
    animal_id: int,
    db: AsyncSession = Depends(get_db),
    _: None = Depends(require_api_key),
):
    result = await db.execute(
        select(AbandonedAnimal).where(AbandonedAnimal.id == animal_id)
    )
    animal = result.scalar_one_or_none()
    if not animal:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Animal not found")
    return animal
```

- [ ] **Step 5: main.py에 라우터 등록**

`app/main.py`:
```python
from fastapi import FastAPI
from app.api.animals import router as animals_router

app = FastAPI(title="Pet Data API")
app.include_router(animals_router)
```

- [ ] **Step 6: 테스트 통과 확인**

```bash
pytest tests/test_animals.py -v
```
Expected: 3 PASSED

- [ ] **Step 7: 커밋**

```bash
git add app/api/animals.py app/main.py tests/test_animals.py
git commit -m "feat: GET /animals (keyset pagination, trigram 검색), GET /animals/{id}"
```

---

### Task 9: Stats API

**Files:**
- Create: `app/api/stats.py`
- Create: `tests/test_stats.py`

- [ ] **Step 1: 실패 테스트 작성**

`tests/test_stats.py`:
```python
import hashlib
import pytest
from httpx import AsyncClient, ASGITransport

API_KEY = "testkey"
API_KEY_HASH = hashlib.sha256(API_KEY.encode()).hexdigest()
HEADERS = {"X-API-Key": API_KEY}


@pytest.fixture
def mock_settings(monkeypatch):
    monkeypatch.setattr("app.core.config.settings.API_KEY_HASH", API_KEY_HASH)
    monkeypatch.setattr("app.core.config.settings.ADMIN_API_KEY_HASH", "different_hash")


@pytest.mark.asyncio
async def test_stats_region_requires_auth(mock_settings):
    from app.main import app
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        response = await client.get("/stats/region", headers={"X-API-Key": "bad"})
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_stats_trend_requires_auth(mock_settings):
    from app.main import app
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        response = await client.get("/stats/trend?year=2024&month=1", headers={"X-API-Key": "bad"})
    assert response.status_code == 401
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
pytest tests/test_stats.py -v
```
Expected: 404 (라우터 미등록)

- [ ] **Step 3: stats.py API 작성**

`app/api/stats.py`:
```python
from fastapi import APIRouter, Depends, Query
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession
from app.core.database import get_db
from app.core.auth import require_api_key
from app.schemas.stats import RegionStatResponse, TrendResponse

router = APIRouter(prefix="/stats", tags=["stats"])


@router.get("/region", response_model=list[RegionStatResponse])
async def region_stats(
    db: AsyncSession = Depends(get_db),
    _: None = Depends(require_api_key),
):
    result = await db.execute(
        text("SELECT region, date, total_count, adopted_count, euthanized_count FROM mv_region_stats ORDER BY date DESC, region")
    )
    return [RegionStatResponse(**dict(r)) for r in result.mappings()]


@router.get("/trend", response_model=list[TrendResponse])
async def trend_stats(
    year: int = Query(..., ge=2000, le=2100),
    month: int = Query(..., ge=1, le=12),
    db: AsyncSession = Depends(get_db),
    _: None = Depends(require_api_key),
):
    result = await db.execute(
        text("""
            SELECT
                region,
                EXTRACT(YEAR FROM date)::int  AS year,
                EXTRACT(MONTH FROM date)::int AS month,
                SUM(total_count)::int         AS total_count,
                SUM(adopted_count)::int       AS adopted_count,
                SUM(euthanized_count)::int    AS euthanized_count
            FROM mv_region_stats
            WHERE EXTRACT(YEAR FROM date) = :year
              AND EXTRACT(MONTH FROM date) = :month
            GROUP BY region, year, month
            ORDER BY region
        """),
        {"year": year, "month": month},
    )
    return [TrendResponse(**dict(r)) for r in result.mappings()]
```

- [ ] **Step 4: main.py에 stats 라우터 등록**

`app/main.py`:
```python
from fastapi import FastAPI
from app.api.animals import router as animals_router
from app.api.stats import router as stats_router

app = FastAPI(title="Pet Data API")
app.include_router(animals_router)
app.include_router(stats_router)
```

- [ ] **Step 5: 테스트 통과 확인**

```bash
pytest tests/test_stats.py -v
```
Expected: 2 PASSED

- [ ] **Step 6: 커밋**

```bash
git add app/api/stats.py app/main.py tests/test_stats.py
git commit -m "feat: GET /stats/region, GET /stats/trend (Materialized View 조회)"
```

---

### Task 10: Admin Collect API + Scheduler + main.py 완성

**Files:**
- Create: `app/api/collect.py`
- Create: `app/scheduler/jobs.py`
- Modify: `app/main.py`

- [ ] **Step 1: collect.py 작성 (관리자 수동 트리거)**

`app/api/collect.py`:
```python
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from app.core.database import get_db
from app.core.auth import require_admin_key
from app.collector.runner import run_collection

router = APIRouter(prefix="/collect", tags=["admin"])


@router.post("/trigger")
async def trigger_collection(
    db: AsyncSession = Depends(get_db),
    _: None = Depends(require_admin_key),
):
    log = await run_collection(db)
    return {
        "status": log.status,
        "total_fetched": log.total_fetched,
        "total_saved": log.total_saved,
        "error_message": log.error_message,
        "started_at": log.started_at,
        "finished_at": log.finished_at,
    }
```

- [ ] **Step 2: jobs.py 작성 (APScheduler 19:00 스케줄)**

`app/scheduler/jobs.py`:
```python
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from app.core.database import AsyncSessionLocal
from app.collector.runner import run_collection

scheduler = AsyncIOScheduler()


async def scheduled_collection():
    async with AsyncSessionLocal() as db:
        await run_collection(db)


def start_scheduler():
    scheduler.add_job(
        scheduled_collection,
        trigger="cron",
        hour=19,
        minute=0,
        max_instances=1,
        id="daily_collection",
    )
    scheduler.start()


def stop_scheduler():
    scheduler.shutdown(wait=False)
```

- [ ] **Step 3: main.py 최종 완성 (lifespan으로 스케줄러 관리)**

`app/main.py`:
```python
from contextlib import asynccontextmanager
from fastapi import FastAPI
from app.api.animals import router as animals_router
from app.api.stats import router as stats_router
from app.api.collect import router as collect_router
from app.scheduler.jobs import start_scheduler, stop_scheduler


@asynccontextmanager
async def lifespan(app: FastAPI):
    start_scheduler()
    yield
    stop_scheduler()


app = FastAPI(title="Pet Data API", lifespan=lifespan)
app.include_router(animals_router)
app.include_router(stats_router)
app.include_router(collect_router)
```

- [ ] **Step 4: 403 권한 분리 테스트**

```bash
# 터미널에서 직접 테스트
uvicorn app.main:app --reload &
sleep 2

# 일반 키로 /collect/trigger 접근 → 403
curl -s -o /dev/null -w "%{http_code}" \
  -X POST http://localhost:8000/collect/trigger \
  -H "X-API-Key: <일반-API-KEY>"
# Expected: 403

# 관리자 키로 접근 → 200
curl -s -o /dev/null -w "%{http_code}" \
  -X POST http://localhost:8000/collect/trigger \
  -H "X-API-Key: <ADMIN-API-KEY>"
# Expected: 200
```

- [ ] **Step 5: 전체 테스트 통과 확인**

```bash
pytest tests/ -v
```
Expected: 전체 PASSED

- [ ] **Step 6: Swagger UI 확인**

```bash
# 브라우저에서 열기
open http://localhost:8000/docs
```
Expected: 5개 엔드포인트 (`GET /animals`, `GET /animals/{id}`, `GET /stats/region`, `GET /stats/trend`, `POST /collect/trigger`) 표시

- [ ] **Step 7: 최종 커밋**

```bash
git add app/api/collect.py app/scheduler/jobs.py app/main.py
git commit -m "feat: POST /collect/trigger (admin), APScheduler 매일 19:00 수집, main.py lifespan 완성"
```

---

## 스펙 커버리지 체크

| 스펙 요구사항 | 구현 태스크 |
|---|---|
| FastAPI + PostgreSQL + asyncpg | Task 1, 3 |
| 테이블 + 인덱스 (notice_no UNIQUE, region/status/type 복합, notice_date, trgm) | Task 2 |
| Materialized View + REFRESH CONCURRENTLY | Task 2, 7 |
| collection_logs 테이블 | Task 2, 4 |
| SHA-256 API Key 인증 (일반/관리자) | Task 5 |
| 401 / 403 분리 | Task 5, 10 |
| Keyset pagination (cursor 기반) | Task 8 |
| trigram 유사도 검색 | Task 8 |
| GET /stats/region, /stats/trend | Task 9 |
| POST /collect/trigger (admin) | Task 10 |
| APScheduler 매일 19:00 | Task 10 |
| httpx 지수 백오프 재시도 3회 | Task 7 |
| 배치 트랜잭션 + 롤백 | Task 7 |
| 부분 성공 (partial) 처리 | Task 7 |
