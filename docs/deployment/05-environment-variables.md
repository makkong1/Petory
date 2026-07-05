# 환경 변수 관리

## 📋 개요

Petory 프로젝트의 환경 변수 관리 방법과 보안 전략을 설명합니다.

**macOS(맥북)**: 아래 셸 명령(`chmod`, `openssl`)은 터미널.app / Cursor 통합 터미널에서 그대로 사용할 수 있습니다. 로컬에서 Docker만 쓸 때는 [macOS 로컬 가이드](./00-macos-local.md)와 함께 보세요.

---

## 🔐 환경 변수 분류

### 1. 공개 가능한 변수
- 설정 값 (포트, 타임아웃 등)
- 기능 플래그
- 기본 설정

### 2. 민감한 정보 (비밀번호, 키 등)
- 데이터베이스 비밀번호
- JWT 시크릿
- OAuth2 클라이언트 시크릿
- API 키

---

## 📁 환경 변수 파일 구조

```
Petory/
└── .env.example              # 환경 변수 템플릿 (레포 루트, 이거 하나뿐)
```

실제로는 `.env.example` 하나만 있고, 이걸 `.env`로 복사해서 값을 채우면 `docker-compose.yml`이 그대로 읽는다. `application-prod.properties`가 `${DB_HOST}`처럼 **커스텀 이름의 플레이스홀더**를 직접 쓰기 때문에, Spring Boot의 relaxed binding 컨벤션(`SPRING_DATASOURCE_URL` 같은 자동 매핑 이름)이 아니라 **아래 실제 변수명 그대로** 맞춰야 인식된다.

---

## 🔧 Backend 환경 변수

### `.env.example`(실제 파일 전문)

```bash
# ── MySQL ───────────────────────────────────────────────────
DB_HOST=mysql
DB_PORT=3306
DB_NAME=petory
DB_USERNAME=petory_app
DB_PASSWORD=your_strong_db_password_here
DB_ROOT_PASSWORD=your_strong_root_password_here

# ── Redis ───────────────────────────────────────────────────
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=your_strong_redis_password_here

# ── JWT ─────────────────────────────────────────────────────
# openssl rand -base64 64 로 생성 (최소 256bit = 32자 이상)
JWT_SECRET=
# Access JWT TTL (ms). 생략 시 900000(15분)
# JWT_ACCESS_TOKEN_EXPIRATION_MS=900000

# ── 프론트엔드 URL ───────────────────────────────────────────
FRONTEND_URL=https://your-domain.com

# ── Google / Naver OAuth2 ────────────────────────────────────
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
NAVER_CLIENT_ID=
NAVER_CLIENT_SECRET=

# ── 네이버 지도 API ──────────────────────────────────────────
NAVER_MAP_CLIENT_ID=
NAVER_MAP_CLIENT_SECRET=

# ── Gmail SMTP ──────────────────────────────────────────────
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=

# ── 파일 업로드 ──────────────────────────────────────────────
FILE_UPLOAD_DIR=/app/uploads

# ── Ollama (AI) ─────────────────────────────────────────────
OLLAMA_BASE_URL=http://ollama:11434
OLLAMA_MODEL=llama3

# ── Spring Boot Admin ────────────────────────────────────────
ADMIN_SERVER_URL=http://localhost:8080/admin-ui
```

> **필수 주의**: `GOOGLE_CLIENT_ID`/`SECRET`, `NAVER_CLIENT_ID`/`SECRET` 등이 비어 있으면 `ClientRegistrationRepository` 빈 생성 자체가 실패해서 앱이 부팅되지 않는다. 실제 OAuth2 자격증명이 없으면 더미 문자열이라도 채워야 한다. `JWT_SECRET`은 반드시 값을 채워야 함 (빈 값이면 부팅 실패).

### petory-nlp-server 연동

`.env.example`에는 없고, `docker-compose.yml`의 `app` 서비스 `environment`에 직접 박혀 있다 (컨테이너 토폴로지 값이라 `.env`로 뺄 필요가 없음):

```yaml
PET_INTENT_BASE_URL: http://nlp-server:8000
```

`application-prod.properties`의 `app.pet-intent.base-url=${PET_INTENT_BASE_URL:http://localhost:8000}`이 이 값을 받는다. (예전에 쓰였던 `PET_DATA_API_URL`/`PET_DATA_API_KEY`라는 이름은 실제 코드 어디에서도 참조되지 않는 죽은 변수였고, 실제 연동은 `PetIntentClient` + `app.pet-intent.base-url` 뿐이다.)

---

## 🎨 Frontend 환경 변수

`frontend/`에 `.env`, `.env.production.local`, `.env.capacitor` 등이 상황별로 있음 (모두 gitignore 대상). 코드에서 실제로 읽는 변수(`process.env.REACT_APP_*` 기준 확인):

```bash
REACT_APP_API_BASE_URL=http://localhost:8080     # apiClient.js
REACT_APP_NAVER_MAPS_CLIENT_ID=your_naver_map_client_id   # MapContainer.js, MiniMapPicker.js
REACT_APP_NAVER_MAPS_KEY_ID=your_naver_map_key_id
REACT_APP_DEMO_MODE=false                        # isDemoMode.js
```

React는 빌드 타임에 환경 변수가 주입되므로 `REACT_APP_` 접두사가 필요하고, `docker-compose.yml`에는 별도 프론트 빌드 스테이지가 없으므로(로컬에서 `npm run build`로 `frontend/build`를 만들어 nginx가 서빙) **빌드를 실행하는 시점**에 이 값들이 잡혀 있어야 한다.

---

## 🐳 Docker Compose 환경 변수

### `docker-compose.yml`(실제 파일)

실제 `docker-compose.yml`은 `.env`(레포 루트, `.env.example`을 복사해 채움)를 읽어서 각 서비스에 전달한다. 서비스명은 `mysql`/`redis`/`app`/`nginx` 4개뿐이고, `frontend`는 별도 서비스가 아니라 `app` 이미지 없이 `nginx`가 `frontend/build`를 정적으로 서빙한다.

```yaml
services:
  mysql:
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_ROOT_PASSWORD}
      MYSQL_DATABASE: ${DB_NAME:-petory}
      MYSQL_USER: ${DB_USERNAME}
      MYSQL_PASSWORD: ${DB_PASSWORD}

  redis:
    command: redis-server --requirepass ${REDIS_PASSWORD} ...

  app:
    env_file:
      - .env          # JWT_SECRET, OAuth2/메일/네이버맵 등 나머지 값은 .env에서 그대로 주입
    environment:
      DB_HOST: mysql            # docker-compose가 서비스명으로 덮어씀 (.env의 DB_HOST=mysql과 동일)
      REDIS_HOST: redis
      SPRING_PROFILES_ACTIVE: prod

  nginx:
    volumes:
      - ./frontend/build:/usr/share/nginx/html:ro   # 프론트 빌드 결과물 정적 서빙
```

`.env`에 필요한 키 전체 목록은 레포 루트 `.env.example` 참고 (`DB_*`, `REDIS_*`, `JWT_SECRET`, `GOOGLE_*`/`NAVER_*` OAuth2, `MAIL_*`, `NAVER_MAP_*` 등). **OAuth2 client-id/secret, 메일 계정 등은 실제 값이 없어도 더미값은 채워야 함** — 비어 있으면 `ClientRegistrationRepository` 빈 생성 실패로 앱이 아예 안 뜸.

---

## 🔒 보안 전략

### 1. .env 파일 보안

#### `.gitignore`에 추가

```gitignore
# Environment variables
.env
.env.local
.env.development.local
.env.test.local
.env.production.local
.env.production
.env.staging
```

#### 파일 권한 설정

```bash
# 서버(Linux)에서 실행
chmod 600 .env.production
chown app:app .env.production
```

**macOS(맥북)**: `chmod 600 .env.production`은 동일하게 사용합니다. `chown app:app`은 로컬에 `app` 유저가 없을 수 있으므로 보통 생략하고, 저장소에 `.env*`가 올라가지 않게만 확인합니다.

### 2. GitHub Secrets 활용

CI/CD에서 민감한 정보는 GitHub Secrets 사용:

```yaml
# .github/workflows/cd-production.yml (CD는 아직 없음, 추가 시 예시)
env:
  DB_PASSWORD: ${{ secrets.DB_PASSWORD }}
  JWT_SECRET: ${{ secrets.JWT_SECRET }}
```

### 3. 비밀 관리 도구 (선택)

#### HashiCorp Vault

```bash
# Vault에서 비밀 가져오기
vault kv get -field=password secret/petory/database
```

#### AWS Secrets Manager / Azure Key Vault

```bash
# AWS Secrets Manager
aws secretsmanager get-secret-value --secret-id petory/database
```

---

## 🔄 환경별 설정 전략

### 개발 환경

```bash
# .env.development
SPRING_PROFILES_ACTIVE=dev
SPRING_JPA_SHOW_SQL=true
LOGGING_LEVEL_ROOT=DEBUG
```

### 스테이징 환경

```bash
# .env.staging
SPRING_PROFILES_ACTIVE=staging
SPRING_JPA_SHOW_SQL=false
LOGGING_LEVEL_ROOT=INFO
```

### 프로덕션 환경

```bash
# .env.production
SPRING_PROFILES_ACTIVE=prod
SPRING_JPA_SHOW_SQL=false
LOGGING_LEVEL_ROOT=WARN
SERVER_ERROR_INCLUDE_MESSAGE=never
SERVER_ERROR_INCLUDE_STACKTRACE=never
```

---

## 📝 Spring Boot에서 환경 변수 사용

`application-prod.properties`(실제 파일)에서 커스텀 이름의 플레이스홀더를 직접 씀 — Spring Boot의 `SPRING_*` 자동 relaxed-binding이 아니라 **이 플레이스홀더 이름과 정확히 같은 환경변수**를 설정해야 반영된다.

```properties
spring.datasource.url=jdbc:mysql://${DB_HOST}:${DB_PORT:3306}/${DB_NAME:petory}?useSSL=true&...
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}

spring.redis.host=${REDIS_HOST}
spring.redis.port=${REDIS_PORT:6379}
spring.redis.password=${REDIS_PASSWORD}

jwt.secret=${JWT_SECRET}
jwt.expiration=900000
```

> Refresh Token은 별도 시크릿으로 서명하는 게 아니라 **DB에 저장**해서 관리한다 (`jwt.refresh-secret` 같은 속성은 없음).

---

## 🛡️ 비밀 키 생성

### JWT Secret 생성

```bash
# 256비트 (32바이트) 랜덤 키 생성
openssl rand -base64 32

# 또는
openssl rand -hex 32
```

### 데이터베이스 비밀번호 생성

```bash
# 강력한 비밀번호 생성
openssl rand -base64 24
```

---

## ✅ 환경 변수 검증

### 배포 전 검증 스크립트

```bash
#!/bin/bash
# validate-env.sh

REQUIRED_VARS=(
    "DB_PASSWORD"
    "DB_ROOT_PASSWORD"
    "REDIS_PASSWORD"
    "JWT_SECRET"
    "MAIL_USERNAME"
    "MAIL_PASSWORD"
)

MISSING_VARS=()

for var in "${REQUIRED_VARS[@]}"; do
    if [ -z "${!var}" ]; then
        MISSING_VARS+=("$var")
    fi
done

if [ ${#MISSING_VARS[@]} -ne 0 ]; then
    echo "❌ Missing required environment variables:"
    printf '%s\n' "${MISSING_VARS[@]}"
    exit 1
fi

echo "✅ All required environment variables are set"
```

---

## 📋 체크리스트

### 배포 전 확인사항

- [ ] 모든 민감한 정보가 `.env` 파일에 설정됨
- [ ] `.env` 파일이 `.gitignore`에 포함됨
- [ ] `.env.example` 파일이 최신 상태
- [ ] JWT Secret이 충분히 강력함 (최소 256비트)
- [ ] 데이터베이스 비밀번호가 강력함
- [ ] OAuth2 클라이언트 시크릿이 설정됨
- [ ] 프로덕션 환경 변수 파일 권한이 600으로 설정됨
- [ ] 환경 변수 검증 스크립트 실행 통과

---

## 📝 다음 단계

1. [배포 프로세스](./06-deployment-process.md) - 실제 배포 가이드
2. [모니터링 및 로깅](./07-monitoring-logging.md) - 운영 모니터링

