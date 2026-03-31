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
├── .env.example              # 환경 변수 템플릿
├── .env.development          # 개발 환경 (로컬)
├── .env.staging              # 스테이징 환경
├── .env.production           # 프로덕션 환경 (서버에서만)
└── docker/
    └── .env.prod.example     # Docker Compose용 예시
```

---

## 🔧 Backend 환경 변수

### `.env.example`

```bash
# ============================================
# Application Profile
# ============================================
SPRING_PROFILES_ACTIVE=prod

# ============================================
# Database Configuration
# ============================================
SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/petory
SPRING_DATASOURCE_USERNAME=petory
SPRING_DATASOURCE_PASSWORD=your_database_password
SPRING_DATASOURCE_DRIVER_CLASS_NAME=com.mysql.cj.jdbc.Driver

# ============================================
# JPA/Hibernate Configuration
# ============================================
SPRING_JPA_HIBERNATE_DDL_AUTO=none
SPRING_JPA_SHOW_SQL=false
SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.dialect.MySQL8Dialect
SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL=false

# ============================================
# Redis Configuration
# ============================================
SPRING_REDIS_HOST=redis
SPRING_REDIS_PORT=6379
SPRING_REDIS_PASSWORD=your_redis_password
SPRING_REDIS_TIMEOUT=2000ms

# ============================================
# JWT Configuration
# ============================================
JWT_SECRET=your_jwt_secret_key_minimum_256_bits_required_for_hs256
JWT_REFRESH_SECRET=your_jwt_refresh_secret_key_minimum_256_bits_required
JWT_ACCESS_TOKEN_VALIDITY=900000
JWT_REFRESH_TOKEN_VALIDITY=86400000

# ============================================
# Email Configuration
# ============================================
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=your_email@gmail.com
SPRING_MAIL_PASSWORD=your_app_specific_password
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
SPRING_MAIL_FROM_ADDRESS=noreply@petory.com

# ============================================
# OAuth2 Configuration
# ============================================
# Google OAuth2
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID=your_google_client_id
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET=your_google_client_secret
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_REDIRECT_URI=https://your-domain.com/api/oauth2/callback/google

# Naver OAuth2
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_NAVER_CLIENT_ID=your_naver_client_id
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_NAVER_CLIENT_SECRET=your_naver_client_secret
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_NAVER_REDIRECT_URI=https://your-domain.com/api/oauth2/callback/naver

# ============================================
# File Upload Configuration
# ============================================
UPLOAD_DIR=/app/uploads
MAX_FILE_SIZE=10485760
ALLOWED_FILE_TYPES=jpg,jpeg,png,gif,pdf

# ============================================
# External API Keys
# ============================================
# Naver Map API
NAVER_MAP_CLIENT_ID=your_naver_map_client_id
NAVER_MAP_CLIENT_SECRET=your_naver_map_client_secret

# ============================================
# Server Configuration
# ============================================
SERVER_PORT=8080
SERVER_ERROR_INCLUDE_MESSAGE=never
SERVER_ERROR_INCLUDE_STACKTRACE=never

# ============================================
# Logging Configuration
# ============================================
LOGGING_LEVEL_ROOT=INFO
LOGGING_LEVEL_COM_LINKUP_PETORY=INFO
LOGGING_FILE_NAME=logs/petory.log
LOGGING_FILE_MAX_SIZE=10MB
LOGGING_FILE_MAX_HISTORY=30
```

---

## 🎨 Frontend 환경 변수

### `frontend/.env.example`

```bash
# API Base URL
REACT_APP_API_URL=http://localhost:8080/api

# Environment
REACT_APP_ENV=production

# Naver Map API Key (Frontend에서 사용)
REACT_APP_NAVER_MAP_CLIENT_ID=your_naver_map_client_id

# WebSocket URL
REACT_APP_WS_URL=ws://localhost:8080/ws

# Feature Flags
REACT_APP_ENABLE_CHAT=true
REACT_APP_ENABLE_NOTIFICATIONS=true
```

### Frontend 빌드 시 주입

React는 빌드 타임에 환경 변수가 주입되므로, `REACT_APP_` 접두사가 필요합니다.

```dockerfile
# Dockerfile.frontend
ARG REACT_APP_API_URL
ARG REACT_APP_NAVER_MAP_CLIENT_ID

ENV REACT_APP_API_URL=$REACT_APP_API_URL
ENV REACT_APP_NAVER_MAP_CLIENT_ID=$REACT_APP_NAVER_MAP_CLIENT_ID

RUN npm run build
```

---

## 🐳 Docker Compose 환경 변수

### `docker-compose.prod.yml`

```yaml
version: '3.8'

services:
  mysql:
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: ${MYSQL_DATABASE}
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}

  redis:
    command: redis-server --requirepass ${REDIS_PASSWORD}

  backend:
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE}
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/${MYSQL_DATABASE}
      SPRING_DATASOURCE_USERNAME: ${MYSQL_USER}
      SPRING_DATASOURCE_PASSWORD: ${MYSQL_PASSWORD}
      SPRING_REDIS_HOST: redis
      SPRING_REDIS_PASSWORD: ${REDIS_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      JWT_REFRESH_SECRET: ${JWT_REFRESH_SECRET}
      SPRING_MAIL_USERNAME: ${SPRING_MAIL_USERNAME}
      SPRING_MAIL_PASSWORD: ${SPRING_MAIL_PASSWORD}
      # ... 기타 환경 변수

  frontend:
    build:
      context: .
      dockerfile: docker/Dockerfile.frontend
      args:
        REACT_APP_API_URL: ${REACT_APP_API_URL}
        REACT_APP_NAVER_MAP_CLIENT_ID: ${REACT_APP_NAVER_MAP_CLIENT_ID}
```

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
# .github/workflows/cd-production.yml
env:
  SPRING_DATASOURCE_PASSWORD: ${{ secrets.DB_PASSWORD }}
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

### `application.properties`

```properties
# 기본값 설정
spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:mysql://localhost:3306/petory}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:root}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:}

# JWT
jwt.secret=${JWT_SECRET}
jwt.refresh-secret=${JWT_REFRESH_SECRET}
```

### `@ConfigurationProperties` 사용

```java
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties {
    private String secret;
    private String refreshSecret;
    private long accessTokenValidity;
    private long refreshTokenValidity;
}
```

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
    "MYSQL_PASSWORD"
    "REDIS_PASSWORD"
    "JWT_SECRET"
    "JWT_REFRESH_SECRET"
    "SPRING_MAIL_USERNAME"
    "SPRING_MAIL_PASSWORD"
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

