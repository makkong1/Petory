# Petory 배포 가이드

## 맥북(macOS)에서 로컬로 띄울 때

로컬 개발용으로 Docker를 쓰는 경우 **먼저** [macOS 로컬 가이드](./00-macos-local.md)(Docker Desktop, `docker compose`, 포트 확인 등)를 보는 것을 권장합니다. 아래 “빠른 시작”은 Docker가 이미 설치되어 있다고 가정합니다.

---

## 📋 목차

0. [macOS(맥북) 로컬 Docker](./00-macos-local.md) — 로컬 맥 전용
1. [배포 전략 개요](./01-deployment-strategy.md)
2. [Docker 설정](./02-docker-configuration.md)
3. [CI/CD 파이프라인](./03-cicd-pipeline.md)
4. [Nginx 설정](./04-nginx-configuration.md)
5. [환경 변수 관리](./05-environment-variables.md)
6. [배포 프로세스](./06-deployment-process.md)
7. [모니터링 및 로깅](./07-monitoring-logging.md)
8. [트러블슈팅](./08-troubleshooting.md)

---

## 🏗️ 배포 아키텍처

`docker-compose.yml`(레포 루트)로 5개 컨테이너가 뜬다. 프론트엔드는 별도 컨테이너 없이, nginx가 `frontend/build` 정적 파일을 직접 서빙하면서 `/api` 등은 backend로 리버스 프록시한다.

```
                    ┌─────────────┐
                    │   Internet  │
                    └──────┬──────┘
                           │
                    ┌──────▼──────────────┐
                    │   petory-nginx      │
                    │  (정적 파일 서빙 +    │
                    │   /api 리버스 프록시) │
                    │   80, 443           │
                    └──────┬──────────────┘
                           │
                    ┌──────▼──────┐
                    │ petory-app  │
                    │ (Spring Boot)│
                    │  8080       │
                    └──────┬──────┘
                           │
         ┌─────────────────┼─────────────────┐
         │                 │                 │
  ┌──────▼──────┐  ┌───────▼───────┐  ┌──────▼─────────────┐
  │ petory-mysql│  │ petory-redis  │  │ petory-nlp-server   │
  │  MySQL 8.0  │  │  Redis 7      │  │ FastAPI, 의도 분석    │
  └─────────────┘  └───────────────┘  │  8000               │
                                       └──────────────────────┘
```

---

## 🛠️ 기술 스택

### Frontend
- **웹 서버**: Nginx (별도 컨테이너 없이 `petory-nginx`가 정적 파일 직접 서빙)
- **빌드 도구**: npm (React Scripts)

### Backend
- **컨테이너 베이스 이미지**: `eclipse-temurin:17-jdk-jammy`(빌드) / `17-jre-jammy`(런타임) — 멀티스테이지 빌드
  - Alpine 계열(`17-jdk-alpine` 등)은 arm64(Apple Silicon) 매니페스트가 없어 M시리즈 맥에서 빌드가 안 됨 → jammy(Debian) 사용
- **애플리케이션**: Spring Boot 3.5.7
- **빌드 도구**: Gradle (컨테이너 내부에서 `./gradlew bootJar`)
- **실행 유저**: `petory`(non-root, `groupadd`/`useradd`로 생성)

### Infrastructure
- **데이터베이스**: MySQL 8.0 — `docker-compose.yml`의 `mysql` 서비스. 최초 기동 시 `backend/main/resources/sql/migration/000-baseline-schema.sql`이 자동 실행되어 전체 스키마(테이블 40개)가 생성됨
- **캐시**: Redis 7 — `docker-compose.yml`의 `redis` 서비스
- **NLP 서버**: `petory-nlp-server`(FastAPI, Python 3.9) — `docker-compose.yml`의 `nlp-server` 서비스. 반려생활 의도 분석(`POST /api/pet-intent/analyze`) 담당, `app`이 `PetIntentClient`로 호출 (`app.pet-intent.base-url=http://nlp-server:8000`)
- **전체 스택 Compose**: 레포 루트 `docker-compose.yml`에 존재 (mysql·redis·nlp-server·app·nginx 5개 서비스)
- **CI/CD**: GitHub Actions ([문서](./03-cicd-pipeline.md)) — 현재는 빌드/테스트까지, 이미지 push·서버 자동배포(CD)는 아직 없음

---

## 🚀 빠른 시작

### 방법 A: Docker Compose로 전체 스택 (권장, 도커 학습/배포 연습용)

```bash
# 1. .env 생성 (docker-compose.yml이 참조하는 값들)
cp .env.example .env
# .env 편집: DB_PASSWORD, DB_ROOT_PASSWORD, REDIS_PASSWORD, JWT_SECRET 등 채우기
# (OAuth2/메일 등 실제 자격증명 없으면 더미값이라도 넣어야 부팅됨 — SecurityConfig가 빈 값이면 실패)

# 2. 전체 스택 기동 (mysql, redis, nlp-server, app, nginx)
docker compose up --build -d

# 3. 상태 확인
docker compose ps
curl http://localhost:8080/actuator/health
```

- macOS에서 로컬 네이티브 MySQL(3306)/Redis(6379)를 이미 쓰고 있으면 포트 충돌 남 → `docker-compose.yml`의 `ports`를 다른 값(예: `3307:3306`, `6380:6379`)으로 바꿔서 회피. 컨테이너 간 통신은 서비스명 기반이라 호스트 포트를 바꿔도 앱 동작엔 영향 없음.
- nginx는 `nginx/ssl/`에 실제 인증서(`fullchain.pem` 등)가 없으면 기동 실패함 — 로컬 검증 목적이면 무시 가능, 실제 서버 배포 시 Let's Encrypt 등으로 발급 필요.
- `nlp-server`는 `sentence-transformers`/`torch`/`kiwipiepy` 설치 때문에 최초 빌드가 오래 걸림(수 분). 임베딩 모델(`jhgan/ko-sroberta-multitask`)은 컨테이너 최초 기동 시 HuggingFace에서 다운로드하며 `nlp_model_cache` 볼륨에 캐시되어 이후 재기동은 빠름 (인터넷 연결 필요).
- 상세: [Docker 설정](./02-docker-configuration.md)

### 방법 B: 로컬 네이티브 실행 (기존 개발 방식)

> **macOS**: [00-macos-local.md](./00-macos-local.md)

1. MySQL·Redis 준비 후 `backend/main/resources/application.properties`에 접속 정보 설정
2. 백엔드 (레포 루트): `./gradlew bootRun --no-daemon --args='--spring.profiles.active=dev'`
3. 프론트엔드 (`frontend/`): `npm start`

---

## 📚 상세 문서

각 항목에 대한 상세한 설명은 위 목차의 문서를 참고하세요.

