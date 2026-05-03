# Pet Data API & Petory Recommendation — 통합 아키텍처

![Python](https://img.shields.io/badge/Python-3.11-3776AB?logo=python&logoColor=white)
![FastAPI](https://img.shields.io/badge/FastAPI-0.115-009688?logo=fastapi&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-4169E1?logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white)
![Ollama](https://img.shields.io/badge/Ollama-llama3-000000)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-6DB33F?logo=springboot&logoColor=white)

> **pet-data-api**: 공공데이터 + 네이버 블로그 트렌드 + Ollama LLM 기반 반려동물 추천·시설·트렌드 REST API (별도 Python 레포).  
> **Petory**: BFF로 `GET /api/recommend`를 제공하고, 내부에서 **Pet Data API** `POST /recommend`를 호출한다.

이 문서는 (1) **pet-data-api** 서비스의 구성과 API, (2) **Petory `domain.recommendation`**의 실제 코드 기준 흐름, (3) **양쪽의 요청/응답 계약**을 한곳에 정리한다. Petory 쪽 상세 API 표는 [Recommendation 도메인](../domains/recommendation.md)을 참고한다.

---

## 1. 문서 범위

| 대상                        | 설명                                                                                                                                              |
| --------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| **pet-data-api**            | FastAPI, Postgres, Redis, Ollama — 추천 파이프라인이 동작하는 **독립 백엔드** (이 레포 밖)                                                        |
| **Petory `recommendation`** | `RecommendController` → `RecommendService` → `PetDataApiClient` — MySQL에 추천 결과를 저장하지 않는 **프록시/BFF**                                |
| **Location 도메인**         | `GET /api/location-services/recommend` 는 **별도** (Petory DB + `LocationRecommendAgentService` 등). 본 문서의 Pet Data API 연동과 혼동하지 말 것 |

**로드맵**: 위 두 “주변 추천” 경로는 최종적으로 **Pet Data API·`GET /api/recommend` 단일 계약으로 통합**할 예정이다. 통합 후 Location의 `/location-services/recommend` 쪽은 폐기한다. 검증 완료 전까지는 코드·문서 양쪽을 유지한다. ([Recommendation 도메인 §1.4](../domains/recommendation.md))

---

## 2. E2E 연동 (Petory ↔ pet-data-api)

### 2.1 시퀀스

```
[React]  GPS(lat,lng) + context + (세션/쿠키로 로그인)
   │
   │  GET /api/recommend?lat=&lng=&context=
   ▼
[Petory Spring]
  RecommendController
    → SecurityContext에서 userId
    → RecommendService
         → PetRepository: 등록된 반려동물(미삭제) 목록 → 첫 마리로 PetInfo
         → RecommendRequest { lat, lng, context, radius_km=10, top_n=5, pet? }
    → PetDataApiClient: RestClient POST {baseUrl}/recommend
         Header: X-API-Key: {app.pet-data-api.api-key}
   │
   ▼
[pet-data-api FastAPI]  POST /recommend
  → Postgres (Haversine 반경 시설)
  → Redis (트렌드)
  → Ollama (자연어 recommendation)
   │
   ▼
JSON → Petory → 그대로 200. 예외 시 Petory 5xx. 서비스가 null이면(현재 거의 없음) 503.
```

### 2.2 통합 블록 다이어그램

```
┌──────────────────────── Petory (Java/Spring) ────────────────────────┐
│  GET /api/recommend  (인증 필수)                                        │
│      │                                                                 │
│      ├── RecommendService: User의 Pet(첫 마리) → type, breed, age_months│
│      └── PetDataApiClient: POST {baseUrl}/recommend + X-API-Key         │
└───────────────────────────────────────────┬──────────────────────────┘
                                            │
                                            ▼
┌──────────────────────── pet-data-api (Python/FastAPI) ─────────────────┐
│  ┌─ 수집 파이프라인 (APScheduler) ───────────────────────────────────┐  │
│  │ [행안부] → Collector → Kakao 지오코더 → Postgres (pet_facilities) │  │
│  │ [네이버 블로그] → kiwipiepy → Redis (Sorted Set 트렌드)            │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│  POST /recommend ─► Postgres (반경) + Redis (trends) + Ollama (문단)     │
│  GET /facilities · /stats/summary · /trends/{category} …               │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Petory: recommendation 도메인 (코드 기준)

### 3.1 엔드포인트

| 항목                        | 내용                                                     |
| --------------------------- | -------------------------------------------------------- |
| URL                         | `GET /api/recommend`                                     |
| 쿼리                        | `lat`, `lng`, `context` (필수)                           |
| 인증                        | 필수. 미인증 시 `UnauthenticatedException`               |
| 성공                        | `200` + Pet Data API 응답 JSON (`RecommendResponse`)     |
| `RecommendService`가 `null` | `503` (바디 없음) — 현행 클라이언트는 대부분 예외로 실패 |
| `PetDataApiClient` 예외     | `RuntimeException` → 전역 핸들러에 따라 5xx              |

### 3.2 `RecommendService`가 넣는 고정값

- `radius_km`: **10.0**
- `top_n`: **5**
- 반려동물: `findByUserIdAndNotDeleted` 결과의 **첫 요소**만 사용. 없으면 `pet` 생략.

### 3.3 Petory → pet-data-api 요청 DTO (JSON)

Java record `RecommendRequest` 직렬화 (필드명 스네이크 케이스):

```json
{
  "lat": 37.5665,
  "lng": 126.978,
  "context": "grooming",
  "radius_km": 10.0,
  "top_n": 5,
  "pet": {
    "type": "dog",
    "breed": "말티즈",
    "age_months": 24
  }
}
```

- `pet`는 선택: 등록된 펫이 없으면 `@JsonInclude(NON_NULL)`로 통째로 생략 가능.
- **pet-data-api** 쪽 Pydantic 스키마는 `age_months` 또는 기존 `age` 문자열 중 무엇을 받을지 **저장소와 일치**시킬 것 (Petory는 **`age_months`만** 보낸다).

### 3.4 Spring 설정 (Petory)

`application.properties` 예:

```properties
app.pet-data-api.base-url=http://localhost:8000
app.pet-data-api.api-key=${PET_DATA_API_KEY:}
```

- 키 이름은 **`app.pet-data-api.*`** (레거시 `pet-data-api.*` 가 아님).
- Ollama 대기로 pet-data-api 응답이 길 수 있으면, 향후 `RestClient`에 **read timeout**을 별도 `ClientHttpRequestFactory`로 두는 것을 권장 (현행 코드는 기본 타임아웃).

### 3.5 관련 클래스 (Petory)

| 역할   | 클래스                                                                                                       |
| ------ | ------------------------------------------------------------------------------------------------------------ |
| REST   | `com.linkup.Petory.domain.recommendation.controller.RecommendController`                                     |
| 서비스 | `com.linkup.Petory.domain.recommendation.service.RecommendService`                                           |
| HTTP   | `com.linkup.Petory.domain.recommendation.client.PetDataApiClient` (`@Component`, `RestClient` + `X-API-Key`) |
| DTO    | `RecommendRequest`, `RecommendResponse`                                                                      |

---

## 4. pet-data-api: 프로젝트 소개

반려동물 생활 정보를 **세 가지 신호**로 엮어 한 번에 제공하는 Python 백엔드이다.

- **공공데이터 (행안부)** — 전국 동물미용업·동물병원 공식 등록 정보를 `pet_facilities`에 적재. 주소는 **Kakao 로컬 API**로 지오코딩해 `lat`/`lng` 저장.
- **네이버 블로그 API + 형태소 분석 (kiwipiepy)** — 카테고리별 키워드를 Redis Sorted Set에 일일 갱신.
- **Ollama llama3** — GPS·반려동물·반경 내 시설·트렌드를 묶어 한 문단 추천 생성.

---

## 5. pet-data-api: 기술 스택

| 역할            | 기술                     | 선택 이유                    |
| --------------- | ------------------------ | ---------------------------- |
| 웹 프레임워크   | FastAPI                  | async 네이티브, 자동 Swagger |
| DB ORM          | SQLAlchemy 2.0 (asyncpg) | async 쿼리                   |
| DB              | PostgreSQL 15            | pg_trgm, Haversine 반경 쿼리 |
| 캐시            | Redis 7                  | Sorted Set 트렌드            |
| 지오코딩        | Kakao Local API          | 한국 주소                    |
| LLM             | Ollama `llama3`          | 로컬, 비용 없음 (교체 가능)  |
| 형태소          | kiwipiepy                | pip 설치                     |
| 스케줄러        | APScheduler              | `max_instances=1`            |
| HTTP 클라이언트 | httpx                    | async, 재시도                |

---

## 6. pet-data-api: 내부 시스템 아키텍처

```
┌──────────────────────────────────────────────────────────────┐
│  수집 파이프라인 (매일 자동 실행, APScheduler)                │
│                                                              │
│  [행안부 공공API] ──► Collector ──► Kakao 지오코더 ──► Postgres│
│        (시설 + 병원)            (주소→lat/lng)                │
│                                                              │
│  [네이버 블로그API] ──► Collector ──► kiwipiepy ──► Redis     │
│                                 (형태소·빈도)                 │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│  API 서빙 (FastAPI)                                          │
│                                                              │
│  Petory (PetDataApiClient)                                   │
│      │  X-API-Key                                            │
│      ▼                                                       │
│  POST /recommend ──► Postgres (Haversine 반경 쿼리)          │
│                  └─► Redis    (카테고리 트렌드)              │
│                  └─► Ollama   (시설+트렌드+펫 → 추천문)      │
│                                                              │
│  GET /facilities · /stats/summary · /trends/{category}        │
└──────────────────────────────────────────────────────────────┘
```

### 핵심 기술 결정 (요약)

- **Haversine**: `6371000 * acos(...)` Pure SQL, `ORDER BY distance_m LIMIT :top_n`. 상세는 pet-data-api의 `app/recommender/facilities.py` 등.
- **좌표 없는 시설**: Kakao 지오코딩 실패 시 `NULL` → 반경 쿼리에서 제외.
- **LLM 가드**: 반경 시설 0건이면 Ollama 생략, `recommendation: null`. 프롬프트에 “제공 시설 외 가상 시설명 금지”.
- **Redis 장애**: `trends: []` degrading.
- **Ollama 장애/타임아웃**: `recommendation: null`, 시설·트렌드는 유지 (pet-data-api 동작; Petory는 HTTP 레벨에서 5xx일 수 있음).

---

## 7. pet-data-api: API 명세

모든 엔드포인트는 **`X-API-Key`** 헤더가 필요하다. Swagger: `/docs`.

| 메서드 | 경로                 | 설명                         |
| ------ | -------------------- | ---------------------------- |
| POST   | `/recommend`         | AI 추천 + 주변 시설 + 트렌드 |
| GET    | `/facilities`        | 시설 목록 (cursor 등)        |
| GET    | `/facilities/{id}`   | 시설 상세                    |
| GET    | `/stats/summary`     | 통계                         |
| GET    | `/trends/{category}` | 카테고리별 키워드            |
| POST   | `/collect/trigger`   | 수동 수집 (관리자)           |

### 7.1 `context` (Petory `context` 쿼리와 동일 문자열 권장)

| context    | 의미 | 시설 타입  | 트렌드 카테고리 |
| ---------- | ---- | ---------- | --------------- |
| `grooming` | 미용 | `BUSINESS` | `grooming`      |
| `hospital` | 병원 | `HOSPITAL` | `hospital`      |
| `snack`    | 간식 | (없음)     | `snack`         |
| `food`     | 사료 | (없음)     | `food`          |
| `clothes`  | 의류 | (없음)     | `clothes`       |

`snack` / `food` / `clothes` 는 공인 시설이 없을 수 있어 **트렌드 중심**으로 구성.

### 7.2 `POST /recommend` — pet-data-api가 받는 body (스키마 정합)

Petory가 보내는 형식은 위 **3.3절 요청 DTO JSON**과 같다. 아래는 **참고용** 예시이며, **반드시** pet-data-api Pydantic 모델과 맞출 것.

| 필드         | 설명                                                    |
| ------------ | ------------------------------------------------------- |
| `lat`, `lng` | 필수                                                    |
| `context`    | 위 표 값 권장                                           |
| `radius_km`  | Petory는 **10.0** 고정 (API 기본이 3이어도 요청이 우선) |
| `top_n`      | Petory는 **5** 고정                                     |
| `pet`        | 선택. Petory: `type`, `breed`, `age_months`             |

**응답** (Petory `RecommendResponse`와 맞출 것):

```json
{
  "context": "grooming",
  "facilities": [
    {
      "name": "해피독 미용실",
      "distance_m": 320,
      "address": "서울시 마포구 ...",
      "lat": 37.5672,
      "lng": 126.9765
    }
  ],
  "trends": [{ "keyword": "스포팅컷", "score": 41 }],
  "recommendation": "…",
  "generated_at": "2026-04-21T10:00:00+00:00"
}
```

- 반경 시설 0건 → `facilities: []`, `recommendation: null` 가능.
- Petory는 성공 시 이 JSON을 **그대로** 반환. 프론트는 `recommendation == null`이면 문단만 숨기고 시설/트렌드만 표시하는 폴백을 쓰면 됨.

---

## 8. 장애·운영 (양쪽)

| 상황                           | pet-data-api                | Petory                                     |
| ------------------------------ | --------------------------- | ------------------------------------------ |
| Ollama 다운/타임아웃           | `recommendation: null` 가능 | 200 + JSON (문단 null) — 연결/파싱 성공 시 |
| pet-data-api 다운/타임아웃/5xx | -                           | `PetDataApiClient` 예외 → **5xx**          |
| Redis 다운                     | `trends: []`                | 200 + 빈 trends 가능                       |
| API Key 오류                   | 401/403                     | 그대로 전파                                |

Petory `application.properties`에 **유효한** `app.pet-data-api.base-url`이 없으면 애플리케이션 기동 시 주입 실패로 이어질 수 있다(필수 프로퍼티).

---

## 9. pet-data-api: 실행·환경 (별도 레포)

### 9.1 사전 준비

- Python 3.11+
- PostgreSQL 15+
- Redis 7+
- Ollama + `llama3`
- 네이버·Kakao·공공데이터 키 (수집·지오코딩용)

### 9.2 빠른 시작 (요약)

```bash
cd pet-data-api
python -m venv venv && source venv/bin/activate
pip install -r requirements.txt
# DB 마이그레이션, .env 설정 후
ollama pull llama3
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

- Swagger: `http://localhost:8000/docs`

### 9.3 환경변수 (pet-data-api `.env` 예)

| 변수명            | 설명                          |
| ----------------- | ----------------------------- |
| `DATABASE_URL`    | `postgresql+asyncpg://...`    |
| `API_KEY_HASH`    | 일반 키 SHA-256               |
| `REDIS_URL`       | `redis://localhost:6379/0`    |
| `OLLAMA_BASE_URL` | 기본 `http://localhost:11434` |

API Key: 호출 측(Petory `app.pet-data-api.api-key` 원문) ↔ 서버 `API_KEY_HASH` 짝.

### 9.4 pet-data-api 프로젝트 구조 (레포 루트)

```
pet-data-api/
├── app/
│   ├── api/          # FastAPI 라우터
│   ├── analyzer/     # kiwipiepy
│   ├── cache/        # Redis
│   ├── collector/    # 공공·네이버, Kakao 지오코더
│   ├── recommender/  # Haversine, 프롬프트, Ollama
│   ├── core/         # config, DB, auth
│   ├── models/       # SQLAlchemy
│   ├── scheduler/
│   ├── schemas/      # Pydantic
│   └── main.py
├── migrations/
└── tests/
```

---

## 10. Petory 문서 링크

- [Recommendation 도메인 (상세)](../domains/recommendation.md) — API 표, `Location` 추천과 구분, 체크리스트
- [README - 맞춤 추천 요약](../../README.md) (핵심 기능 섹션)

---

## 11. pet-data-api 레포 내부 문서 (참고)

> 아래 경로는 **pet-data-api** 저장소 루트 기준. Petory monorepo와는 별도이다.

- `docs/PROJECT-OVERVIEW.md` — 역할·경계
- `docs/USAGE.md` — curl 예시
- `docs/superpowers/specs/...-pet-recommendation-pipeline-design.md` — 추천 파이프라인
- `docs/superpowers/specs/...-pet-trend-pipeline-design.md` — 트렌드 파이프라인
