# Pet Data API — 설계 스펙

**작성일:** 2026-04-19  
**프로젝트 유형:** Python 사이드 프로젝트 (GitHub 포트폴리오용)  
**목적:** 공공데이터 기반 반려동물 데이터 수집 & 분석 REST API. Java/Spring(Petory)과 함께 멀티스택 백엔드 포트폴리오 구성.

---

## 기술 스택

| 역할 | 기술 |
|------|------|
| 웹 프레임워크 | FastAPI |
| DB ORM | SQLAlchemy 2.0 (async) |
| DB | PostgreSQL |
| 스케줄러 | APScheduler |
| HTTP 클라이언트 | httpx (async) |
| 인증 | API Key (`X-API-Key` 헤더) |
| 환경변수 | python-dotenv |

---

## 프로젝트 구조

```
pet-data-api/
├── app/
│   ├── api/          # FastAPI 라우터 (엔드포인트)
│   ├── collector/    # 공공데이터 API 수집기
│   ├── scheduler/    # APScheduler 스케줄 설정
│   ├── models/       # SQLAlchemy 모델
│   ├── schemas/      # Pydantic 요청/응답 스키마
│   ├── core/         # 설정, DB 연결, API Key 인증 미들웨어
│   └── main.py       # 앱 진입점
├── .env
├── requirements.txt
└── README.md
```

---

## 데이터 소스

모두 공공데이터포털(data.go.kr) Open API. 합법적 사용, 무료, 키 발급 필요.

| API | 제공기관 | 설명 |
|-----|----------|------|
| 국가동물보호정보시스템 구조동물 조회 | 농림축산검역본부 | 유기동물 공고, 상태, 보호소 정보 (핵심 데이터) |
| 전국동물보호센터 정보 | 공공데이터포털 | 전국 보호소 위치, 운영시간, 좌표 |
| 반려동물 영업장 정보 | 농림축산검역본부 | 동물판매·생산·미용 등 영업장 목록 |
| 동물병원 현황 | 행정안전부 | 전국 동물병원 인허가 정보 |

---

## DB 모델

### `abandoned_animals`
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | SERIAL PK | |
| notice_no | VARCHAR UNIQUE | 공고번호 (upsert 기준키) |
| animal_type | VARCHAR | 개 / 고양이 / 기타 |
| breed | VARCHAR | 품종 |
| age | VARCHAR | 나이 |
| gender | VARCHAR | 성별 |
| region | VARCHAR | 지역 |
| shelter_name | VARCHAR | 보호소명 |
| status | VARCHAR | 보호중 / 입양 / 안락사 등 |
| notice_date | DATE | 공고일 |
| collected_at | TIMESTAMP | 수집 시각 |

### `daily_region_stats`
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | SERIAL PK | |
| region | VARCHAR | 지역 |
| date | DATE | 집계 날짜 |
| total_count | INT | 전체 유기동물 수 |
| adopted_count | INT | 입양 수 |
| euthanized_count | INT | 안락사 수 |

> Petory의 DailyStatistics 배치 집계 패턴과 동일한 개념.

---

## API 엔드포인트

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/animals` | 유기동물 목록 (지역/상태/종류 필터, 페이지네이션) |
| GET | `/animals/{id}` | 유기동물 상세 |
| GET | `/stats/region` | 지역별 현황 통계 |
| GET | `/stats/trend` | 월별 추이 (year, month 파라미터) |
| POST | `/collect/trigger` | 수동 수집 트리거 (관리자용) |

모든 엔드포인트에 `X-API-Key` 헤더 필수.

---

## 수집 스케줄

- **실행 시각:** 매일 오후 7시
- **방식:** APScheduler → 공공API 호출 → DB upsert (notice_no 기준)
- **실패 처리:** httpx 자동 재시도 3회 → 실패 시 로그 기록, 기존 DB 데이터 보존

---

## 인증

API Key 두 종류:
- **일반 키** (`API_KEY`) — 조회/통계 엔드포인트 접근 가능
- **관리자 키** (`ADMIN_API_KEY`) — `/collect/trigger` 등 관리 엔드포인트 추가 접근

키는 `.env`에 저장, FastAPI 의존성 주입으로 검증.

---

## 에러 처리

| 코드 | 상황 |
|------|------|
| 400 | 잘못된 파라미터 |
| 401 | API Key 없거나 틀림 |
| 403 | 일반 키로 관리자 전용 엔드포인트 접근 |
| 404 | 해당 ID 없음 |
| 500 | 공공API 호출 실패 |
