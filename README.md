# Petory (펫토리)

> **반려동물 통합 플랫폼**

![Status](https://img.shields.io/badge/Status-Active-success) ![Java](https://img.shields.io/badge/Java-17-orange) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.7-green) ![React](https://img.shields.io/badge/React-19-blue) ![Python](https://img.shields.io/badge/Python-FastAPI-3776AB) ![Capacitor](https://img.shields.io/badge/Capacitor-8-blueviolet)

---

## 프로젝트 소개

**Petory**는 반려동물 보호자를 위한 웹·모바일 통합 플랫폼입니다. 도메인별 역할은 [포트폴리오](https://makkong1.github.io/makkong1-github.io/portfolio/petory)와 동일하게 정리했습니다.

| 도메인             | 설명                                                                                |
| ------------------ | ----------------------------------------------------------------------------------- |
| **User**           | 사용자 인증/인가, 프로필 관리, 반려동물 등록, 제재 시스템                           |
| **Board**          | 커뮤니티 게시판, 댓글, 좋아요/싫어요, 인기글 스냅샷                                 |
| **Care**           | 펫케어 요청/지원, 채팅 기반 매칭, 거래 확정, 리뷰 시스템                            |
| **Missing Pet**    | 실종 동물 신고 및 관리, 위치 기반 검색, 목격 정보 댓글                              |
| **Location**       | 공공데이터 기반 위치 서비스, 통합 검색, 네이버맵·리뷰                               |
| **Recommendation** | 커뮤니티·케어·검색어 intent 분석 → 주변서비스 탭 추천 카드 → Location 카테고리 검색 |
| **Meetup**         | 오프라인 모임 생성/참여, 위치 기반 검색, 상태 관리                                  |
| **Chat**           | 실시간 채팅, WebSocket(STOMP), 펫케어 거래 확정                                     |

공통으로 **Payment**(펫코인 에스크로), **Notification**(SSE·FCM), **Report**(신고·제재), **Statistics·Admin**(Daily Summary·감사 로그)이 각 도메인과 연동됩니다.

본 프로젝트는 기능 구현 이후, 도메인별로 발생 가능성이 높은 성능·동시성 문제를 가정하고 테스트 코드로 의도적으로 재현한 뒤 **측정 → 개선 → 재검증**을 반복하는 방식으로 진행되었습니다.

---

## Tech Stack

| 영역         | 기술                                                             |
| ------------ | ---------------------------------------------------------------- |
| **Backend**  | Java 17, Spring Boot 3.5.7, Spring Security, JPA, Gradle         |
| **Frontend** | React 19, Styled-components, Axios, Recharts                     |
| **Mobile**   | Capacitor 8 (Android / iOS), FCM                                 |
| **NLP**      | Python, FastAPI, `petory-nlp-server` (한국어 반려생활 의도 분석) |
| **Data**     | MySQL 8.0, Redis                                                 |
| **Realtime** | WebSocket(STOMP), SSE, Firebase Admin SDK (FCM)                  |

### Redis 용도

- 알림 최신 50건 캐시 (TTL 24h)
- 게시글 상세 `@Cacheable`
- 회원가입 전 이메일 인증 임시 저장 (TTL 24h)
- Location 검색 NLP **중복 호출 방지** (user + keyword TTL)

---

## 핵심 기능

### Recommendation — 의도 기반 추천 카드

별도 장소 목록 API 없이, **볼 카테고리만 제안**하고 클릭 시 Location 검색으로 이어집니다.

```
커뮤니티 글 / 케어 요청 / 주변서비스 검색어
  → Spring Event (@Async, petIntentExecutor)
  → petory-nlp-server POST /api/pet-intent/analyze
  → user_pet_intent_signal 저장 (원문 미저장, TTL 7일)
  → GET /api/pet-recommend/signals → 추천 카드
  → 클릭 시 /api/location-services/search?category=...
```

- Python **rule + embedding** hybrid 분류, confidence 2단계 필터 (Python 0.45 / Spring 0.60)
- Board·Care는 `@TransactionalEventListener(AFTER_COMMIT)` — 본 트랜잭션과 분리
- NLP·Redis·실행 풀 장애 시 **게시·케어·검색은 정상** (부가 기능 우선 축소)

→ 상세: [`docs/domains/recommendation.md`](./docs/domains/recommendation.md)

### Location — 주변 서비스

- **통합 검색** `GET /api/location-services/search`: 위치 반경 → 지역 계층 → FULLTEXT → 전체 평점순
- `ST_Distance_Sphere` 반경 검색, 카테고리·키워드 SQL `WHERE` 일원화
- 네이버맵 Geocoding / 역지오코딩 / 길찾기 (백엔드 프록시)
- 공공 CSV 배치 임포트, 리뷰·평점
- UX: **「이 지역 검색」** — 지도 이동만으로는 API 재호출하지 않음

→ 상세: [`docs/domains/location.md`](./docs/domains/location.md)

### Care · Payment · Meetup · Chat

- **Care**: OPEN → IN_PROGRESS → COMPLETED 상태, 케어 요청 시 1:1 채팅방 자동 생성
- **Payment**: 펫코인 에스크로, 비관적 락, Care/Chat 거래 확정 연동
- **Meetup**: 반경 검색, 인원 원자적 증가, 그룹 채팅
- **Chat**: STOMP 실시간, 읽음 상태, 역할 기반 참여

### Board · Missing Pet · Report

- Magazine + Smart Grid, 인기 게시글 스냅샷, FULLTEXT 검색
- 실종 제보·목격 댓글, 지도 기반 UX
- 신고 누적 블라인드, 경고 3회 자동 이용제한, `UserSanction` 이력

### Notification · Admin · Statistics

- Redis + MySQL 이중 저장, SSE 실시간 + FCM 푸시
- `DailyStatistics` 자정 배치 (Daily Summary Pattern)
- `admin_audit_log`, `system_config`, Recharts 대시보드

---

## 아키텍처 요약

| 주제         | 방식                                                               |
| ------------ | ------------------------------------------------------------------ |
| **인증**     | JWT Access(기본 15분) + Refresh(1일, DB), OAuth2(Google/Naver)     |
| **권한**     | `@PreAuthorize`, USER → SERVICE_PROVIDER → ADMIN → MASTER          |
| **트랜잭션** | Service 레이어 `@Transactional`, 읽기 전용 기본                    |
| **동시성**   | 펫코인·에스크로 비관적 락, 경고·모임 인원 DB 원자적 증가, ShedLock |
| **NLP 부하** | `petIntentExecutor` 전용 풀, Location 검색 2단 필터, fail-closed   |
| **캐시**     | Redis 알림·게시글·이메일 인증, Spring Cache                        |

---

## 프로젝트 구조

```
Petory/
├── backend/main/java/com/linkup/Petory/
│   ├── domain/
│   │   ├── activity/          # 활동 통계
│   │   ├── admin/             # 관리자·감사·시스템 설정
│   │   ├── board/             # 커뮤니티·실종 제보
│   │   ├── care/              # 펫 케어
│   │   ├── chat/              # 채팅
│   │   ├── location/          # 주변 서비스
│   │   ├── meetup/            # 모임
│   │   ├── notification/      # 알림·FCM
│   │   ├── payment/           # 펫코인
│   │   ├── petRecommendation/ # 의도 signal·추천 API
│   │   ├── report/            # 신고·제재
│   │   ├── statistics/        # 일별·월별 통계
│   │   ├── user/              # 인증·유저·OAuth
│   │   └── file/              # 첨부 파일
│   ├── filter/                # JwtAuthenticationFilter
│   ├── global/                # SecurityConfig, 예외, 공통 DTO
│   └── util/
├── backend/main/resources/
│   └── sql/migration/         # DDL·마이그레이션
├── frontend/src/              # React SPA (+ Capacitor webDir)
├── petory-nlp-server/         # FastAPI NLP (port 8000)
│   └── app/                   # intent_classifier, rules, data/
├── android/ · ios/            # Capacitor 네이티브 프로젝트
└── docs/                      # 도메인·아키텍처·리팩토링 문서
```

---

## 빠른 시작

### 필수 서비스

| 서비스       | 기본                          | 비고                |
| ------------ | ----------------------------- | ------------------- |
| MySQL 8.0+   | `localhost:3306`, DB `petory` |                     |
| Redis        | `localhost:6379`              |                     |
| Python 3.10+ | NLP 서버용                    | `petory-nlp-server` |

### 설정

`backend/main/resources/application.properties`는 gitignore입니다. 로컬에서 직접 생성:

- `spring.datasource.*` — DB 연결
- `jwt.secret` (필수), `jwt.access-token-expiration-ms` (선택)
- OAuth2 클라이언트 등록 (더미값이라도 필수)
- Redis: `spring.redis.host` / `spring.redis.port`
- NLP: `app.pet-intent.base-url=http://localhost:8000` (선택)
- `spring.profiles.active=dev` — 이메일 인증 스킵

### 실행

```bash
# 1. NLP 서버 (추천 카드·signal 사용 시)
cd petory-nlp-server && ./run.sh
# 또는 프로젝트 루트에서: npm run nlp

# 2. Backend (루트, port 8080)
./gradlew bootRun --args='--spring.profiles.active=dev'

# 3. Frontend (port 3000 → proxy 8080)
cd frontend && npm install && npm start
```

### 빌드·테스트

```bash
./gradlew compileJava
./gradlew test                    # MySQL + Redis 필요
cd frontend && npm run build && npm test
```

### DB migration (추천 기능)

```bash
# user_pet_intent_signal 등
mysql petory < backend/main/resources/sql/migration/user-pet-intent-signal-table.sql
```

### 모바일 (Capacitor)

```bash
cd frontend && npm run build
npm run cap:sync:android   # 또는 cap:sync:ios (루트 package.json)
npm run cap:open:android
```

FCM 사용 시 `backend/main/resources/firebase-service-account.json` 필요 — [`docs/deployment/09-mobile-capacitor.md`](./docs/deployment/09-mobile-capacitor.md)

---

## 주요 엔티티

| 엔티티                                  | 설명                             |
| --------------------------------------- | -------------------------------- |
| `Users`, `Pet`, `UserSanction`          | 유저·반려동물·제재               |
| `Board`, `Comment`, `MissingPet`        | 커뮤니티·실종                    |
| `CareRequest`, `CareApplication`        | 펫 케어                          |
| `LocationService`                       | 주변 시설 (POINT, SPATIAL INDEX) |
| `UserPetIntentSignal`                   | 추천 intent signal (원문 미저장) |
| `Meetup`, `Conversation`, `ChatMessage` | 모임·채팅                        |
| `PetCoinTransaction`, `PetCoinEscrow`   | 펫코인                           |
| `Notification`, `FcmToken`              | 알림·푸시                        |
| `DailyStatistics`, `AdminAuditLog`      | 통계·감사                        |

---

## 문서

### 도메인

[`docs/domains/`](./docs/domains/) — board, care, location, **recommendation**, payment, chat, meetup, user, notification, report, statistics, admin 등

### Recommendation (2026-05)

- [도메인 개요](./docs/domains/recommendation.md)
- [리팩토링 백로그](./docs/refactoring/petRecommendation/pet-recommendation-refactoring-2026-05-31.md)
- [버그·보안](./docs/troubleshooting/petRecommendation/pet-recommendation-bugs-2026-05-31.md)
- [NLP 호출·부하 정책](./docs/refactoring/petRecommendation/pet-recommendation-nlp-traffic-policy-2026-05-31.md)

### 아키텍처·알고리즘

- [전체 아키텍처](./docs/architecture/전체%20아키텍처.md)
- [위치 기반 서비스](./docs/architecture/위치%20기반%20서비스%20아키텍처.md)
- [트랜잭션·동시성 사례](./docs/concurrency/transaction-concurrency-cases.md)
- [알고리즘 개요](./docs/algorithm/00-algorithm-overview.md)

### 성능 리팩토링

- [Board](./docs/refactoring/board/board-backend-performance-optimization.md)
- [User](./docs/refactoring/user/user-backend-performance-optimization.md)
- [Payment](./docs/refactoring/payment/payment-backend-performance-optimization.md)
- [Meetup](./docs/refactoring/meetup/meetup-backend-performance-optimization.md)

### 에이전트·개발 규격

- [`CLAUDE.md`](./CLAUDE.md) · [`AGENTS.md`](./AGENTS.md) · [`docs/AGENT_TOOLING.md`](./docs/AGENT_TOOLING.md)

---

## 라이선스

개인 포트폴리오 프로젝트입니다.
