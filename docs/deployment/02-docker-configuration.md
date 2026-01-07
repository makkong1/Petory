# Docker 설정

## 📋 개요

Petory 프로젝트의 Docker 컨테이너 설정 방법을 설명합니다.

---

## 🏗️ Docker 파일 구조

```
Petory/
├── docker/
│   ├── Dockerfile.backend          # Backend Dockerfile
│   ├── Dockerfile.frontend         # Frontend Dockerfile
│   └── nginx/
│       └── default.conf            # Nginx 설정
├── docker-compose.yml              # 개발 환경
├── docker-compose.prod.yml         # 프로덕션 환경
└── .env.example                    # 환경 변수 예시
```

---

## 🔧 Backend Dockerfile

### `docker/Dockerfile.backend`

```dockerfile
# Build Stage
FROM gradle:7.6-jdk17 AS build
WORKDIR /app

# Copy Gradle files
COPY build.gradle settings.gradle ./
COPY gradle/ ./gradle/

# Copy source code
COPY backend/ ./backend/

# Build application
RUN gradle clean build -x test --no-daemon

# Runtime Stage
FROM openjdk:17-jdk-slim
WORKDIR /app

# Copy JAR file
COPY --from=build /app/build/libs/*.jar app.jar

# Environment variables
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/api/actuator/health || exit 1

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### 빌드 명령

```bash
docker build -f docker/Dockerfile.backend -t petory-backend:latest .
```

---

## 🎨 Frontend Dockerfile

### `docker/Dockerfile.frontend`

```dockerfile
# Build Stage
FROM node:18-alpine AS build
WORKDIR /app

# Copy package files
COPY frontend/package*.json ./

# Install dependencies
RUN npm ci

# Copy source code
COPY frontend/ ./

# Build React app
RUN npm run build

# Production Stage
FROM nginx:alpine
WORKDIR /usr/share/nginx/html

# Copy build result
COPY --from=build /app/build /usr/share/nginx/html

# Copy nginx configuration
COPY docker/nginx/default.conf /etc/nginx/conf.d/default.conf

# Expose port
EXPOSE 80

# Start nginx
CMD ["nginx", "-g", "daemon off;"]
```

### 빌드 명령

```bash
docker build -f docker/Dockerfile.frontend -t petory-frontend:latest .
```

---

## 🐳 Docker Compose 설정

### 개발 환경: `docker-compose.yml`

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    container_name: petory-mysql-dev
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-rootpassword}
      MYSQL_DATABASE: ${MYSQL_DATABASE:-petory}
      MYSQL_USER: ${MYSQL_USER:-petory}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD:-petory}
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
      - ./backend/main/resources/sql:/docker-entrypoint-initdb.d
    networks:
      - petory-network
    command: --default-authentication-plugin=mysql_native_password

  redis:
    image: redis:7-alpine
    container_name: petory-redis-dev
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    networks:
      - petory-network
    command: redis-server --appendonly yes

  backend:
    build:
      context: .
      dockerfile: docker/Dockerfile.backend
    container_name: petory-backend-dev
    environment:
      SPRING_PROFILES_ACTIVE: dev
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/${MYSQL_DATABASE:-petory}
      SPRING_DATASOURCE_USERNAME: ${MYSQL_USER:-petory}
      SPRING_DATASOURCE_PASSWORD: ${MYSQL_PASSWORD:-petory}
      SPRING_REDIS_HOST: redis
      SPRING_REDIS_PORT: 6379
    ports:
      - "8080:8080"
    depends_on:
      - mysql
      - redis
    networks:
      - petory-network
    volumes:
      - ./uploads:/app/uploads

  frontend:
    build:
      context: .
      dockerfile: docker/Dockerfile.frontend
    container_name: petory-frontend-dev
    ports:
      - "3000:80"
    depends_on:
      - backend
    networks:
      - petory-network

volumes:
  mysql_data:
  redis_data:

networks:
  petory-network:
    driver: bridge
```

### 프로덕션 환경: `docker-compose.prod.yml`

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    container_name: petory-mysql-prod
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: ${MYSQL_DATABASE}
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
    volumes:
      - mysql_data:/var/lib/mysql
    networks:
      - petory-network
    restart: unless-stopped
    command: --default-authentication-plugin=mysql_native_password --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci

  redis:
    image: redis:7-alpine
    container_name: petory-redis-prod
    volumes:
      - redis_data:/data
    networks:
      - petory-network
    restart: unless-stopped
    command: redis-server --appendonly yes --requirepass ${REDIS_PASSWORD}

  backend:
    image: petory-backend:latest
    container_name: petory-backend-prod
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/${MYSQL_DATABASE}
      SPRING_DATASOURCE_USERNAME: ${MYSQL_USER}
      SPRING_DATASOURCE_PASSWORD: ${MYSQL_PASSWORD}
      SPRING_REDIS_HOST: redis
      SPRING_REDIS_PORT: 6379
      SPRING_REDIS_PASSWORD: ${REDIS_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      JWT_REFRESH_SECRET: ${JWT_REFRESH_SECRET}
    ports:
      - "8080:8080"
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_started
    networks:
      - petory-network
    restart: unless-stopped
    volumes:
      - ./uploads:/app/uploads
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/api/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

  frontend:
    image: petory-frontend:latest
    container_name: petory-frontend-prod
    ports:
      - "3000:80"
    depends_on:
      - backend
    networks:
      - petory-network
    restart: unless-stopped

  nginx:
    image: nginx:alpine
    container_name: petory-nginx-prod
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./docker/nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./docker/nginx/default.conf:/etc/nginx/conf.d/default.conf:ro
      - ./docker/nginx/ssl:/etc/nginx/ssl:ro
    depends_on:
      - frontend
      - backend
    networks:
      - petory-network
    restart: unless-stopped

volumes:
  mysql_data:
  redis_data:

networks:
  petory-network:
    driver: bridge
```

---

## 🔧 환경 변수 파일

### `.env.example`

```bash
# Database
MYSQL_ROOT_PASSWORD=your_root_password
MYSQL_DATABASE=petory
MYSQL_USER=petory
MYSQL_PASSWORD=your_db_password

# Redis
REDIS_PASSWORD=your_redis_password

# JWT
JWT_SECRET=your_jwt_secret_key_min_256_bits
JWT_REFRESH_SECRET=your_jwt_refresh_secret_key_min_256_bits

# Spring Profile
SPRING_PROFILES_ACTIVE=prod

# Email (if needed)
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=your_email@gmail.com
SPRING_MAIL_PASSWORD=your_app_password

# OAuth2 (if needed)
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret
NAVER_CLIENT_ID=your_naver_client_id
NAVER_CLIENT_SECRET=your_naver_client_secret

# File Upload
UPLOAD_DIR=/app/uploads
MAX_FILE_SIZE=10485760
```

---

## 🚀 실행 방법

### 개발 환경

```bash
# 환경 변수 파일 생성
cp .env.example .env
# .env 파일 수정

# 컨테이너 시작
docker-compose up -d

# 로그 확인
docker-compose logs -f

# 컨테이너 중지
docker-compose down

# 데이터까지 삭제
docker-compose down -v
```

### 프로덕션 환경

```bash
# 이미지 빌드
docker-compose -f docker-compose.prod.yml build

# 컨테이너 시작
docker-compose -f docker-compose.prod.yml up -d

# 로그 확인
docker-compose -f docker-compose.prod.yml logs -f backend

# 컨테이너 재시작
docker-compose -f docker-compose.prod.yml restart backend

# 컨테이너 중지
docker-compose -f docker-compose.prod.yml down
```

---

## 📊 리소스 제한 설정

### `docker-compose.prod.yml`에 추가

```yaml
services:
  backend:
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          cpus: '1'
          memory: 1G

  mysql:
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          cpus: '1'
          memory: 1G

  redis:
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 512M
        reservations:
          cpus: '0.25'
          memory: 256M
```

---

## 🔍 Health Check 설정

### Backend Health Check

Spring Boot Actuator 의존성 필요 (`build.gradle`):

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
}
```

`application.properties`:

```properties
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=when-authorized
management.health.db.enabled=true
management.health.redis.enabled=true
```

---

## 🗄️ 볼륨 관리

### 데이터 영속성

```yaml
volumes:
  mysql_data:
    driver: local
  redis_data:
    driver: local
```

### 백업 스크립트 예시

```bash
#!/bin/bash
# backup.sh

DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="./backups"

mkdir -p $BACKUP_DIR

# MySQL 백업
docker exec petory-mysql-prod mysqldump -u root -p${MYSQL_ROOT_PASSWORD} petory > $BACKUP_DIR/mysql_${DATE}.sql

# Redis 백업 (RDB 파일 복사)
docker cp petory-redis-prod:/data/dump.rdb $BACKUP_DIR/redis_${DATE}.rdb
```

---

## 🔐 보안 권장사항

1. **비밀번호 강도**: 환경 변수로 관리, 복잡한 비밀번호 사용
2. **네트워크 격리**: 컨테이너 간 통신만 허용
3. **이미지 스캔**: 정기적으로 보안 취약점 스캔
4. **최신 이미지**: 베이스 이미지 정기 업데이트
5. **비root 사용자**: 컨테이너 내 비root 사용자로 실행

---

## 📝 다음 단계

1. [CI/CD 파이프라인](./03-cicd-pipeline.md) - 자동 빌드 및 배포
2. [Nginx 설정](./04-nginx-configuration.md) - 리버스 프록시 구성

