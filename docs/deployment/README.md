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

```
                    ┌─────────────┐
                    │   Internet  │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │     Nginx   │
                    │  (Reverse   │
                    │   Proxy)    │
                    └──────┬──────┘
                           │
           ┌───────────────┼───────────────┐
           │                               │
    ┌──────▼──────┐              ┌────────▼────────┐
    │  Frontend   │              │    Backend      │
    │   (React)   │              │  (Spring Boot)  │
    │  nginx:80   │              │   Docker:8080   │
    └─────────────┘              └────────┬────────┘
                                           │
                          ┌────────────────┼────────────────┐
                          │                                │
                   ┌──────▼──────┐              ┌─────────▼─────────┐
                   │   MySQL     │              │      Redis        │
                   │  Docker:3306│              │    Docker:6379    │
                   └─────────────┘              └───────────────────┘
```

---

## 🛠️ 기술 스택

### Frontend
- **웹 서버**: Nginx
- **빌드 도구**: npm (React Scripts)
- **정적 파일 서빙**: Nginx

### Backend
- **컨테이너**: Docker
- **런타임**: OpenJDK 17
- **애플리케이션**: Spring Boot 3.5.7
- **빌드 도구**: Gradle

### Infrastructure
- **데이터베이스**: MySQL 8.0 (로컬 설치 또는 Docker — [Docker 설정](./02-docker-configuration.md))
- **캐시**: Redis (로컬은 Docker `docker run` 등으로 실행 가능)
- **전체 스택 Compose**: 레포에 **아직 없음** — 추가 시 [Docker 설정](./02-docker-configuration.md) 참고
- **CI/CD**: GitHub Actions ([문서](./03-cicd-pipeline.md)) — 이미지·Compose가 생기면 연결

---

## 🚀 빠른 시작 (현실적인 로컬)

> **macOS**: [00-macos-local.md](./00-macos-local.md)  
> 전체를 Docker로만 돌리는 스크립트는 **레포에 아직 없음**.

### 1. MySQL · Redis · `application.properties`

- MySQL·Redis 준비 후 `backend/main/resources/application.properties`에 접속 정보 설정  
- Redis를 Docker로 띄우는 방법: [Docker 설정 — 로컬 Redis](./02-docker-configuration.md#로컬-redis-docker-수동-실행)

### 2. 백엔드 (레포 루트)

```bash
./gradlew bootRun --no-daemon --args='--spring.profiles.active=dev'
```

### 3. 프론트엔드 (`frontend/`)

```bash
npm start
```

### 4. (선택) 향후 Docker Compose로 통합 시

`docker-compose.yml` 등이 추가되면 그때 `.env` + `docker compose up` 흐름으로 [02-docker-configuration.md](./02-docker-configuration.md)를 갱신하면 됨.

---

## 📚 상세 문서

각 항목에 대한 상세한 설명은 위 목차의 문서를 참고하세요.

