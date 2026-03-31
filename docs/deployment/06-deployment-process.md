# 배포 프로세스

## 📋 개요

Petory 프로젝트의 실제 배포 프로세스를 단계별로 설명합니다.

**환경 구분**

- **리눅스 서버(프로덕션/스테이징)**: 아래 `ssh`, `/opt/petory` 경로, `systemd`/cron 등은 **서버** 기준입니다.
- **macOS(맥북)**: 로컬에서 Docker로만 연습할 때는 프로젝트 클론 경로(예: `~/project/Petory`)에서 `docker compose`를 실행하면 됩니다. [macOS 로컬 가이드](./00-macos-local.md) 참고. (레포에 Compose 파일이 없으면 [Docker 설정](./02-docker-configuration.md)대로 먼저 추가.)

---

## 🎯 배포 전 준비사항

### 1. 서버 준비

```bash
# 서버 접속
ssh user@your-server-ip

# Docker 및 Docker Compose 설치 확인
docker --version
docker-compose --version

# 디렉토리 생성
mkdir -p /opt/petory
cd /opt/petory
```

### 2. 필수 디렉토리 생성

```bash
# 프로젝트 디렉토리
mkdir -p /opt/petory
cd /opt/petory

# 업로드 파일 디렉토리
mkdir -p uploads

# 로그 디렉토리
mkdir -p logs

# SSL 인증서 디렉토리
mkdir -p docker/nginx/ssl
mkdir -p docker/nginx/certbot

# 백업 디렉토리
mkdir -p backups
```

### 3. 환경 변수 파일 생성

```bash
# .env 파일 생성
cp .env.example .env.production

# 편집
nano .env.production

# 권한 설정
chmod 600 .env.production
```

---

## 🚀 배포 프로세스

### 방법 1: 수동 배포

#### 1단계: 코드 클론 및 업데이트

```bash
cd /opt/petory

# 처음 배포인 경우
git clone https://github.com/your-username/Petory.git .

# 이후 배포 (업데이트)
git pull origin main
```

#### 2단계: 환경 변수 확인

```bash
# .env 파일 존재 확인
ls -la .env.production

# 필수 변수 확인 (스크립트 실행)
./scripts/validate-env.sh
```

#### 3단계: Docker 이미지 빌드 (로컬 빌드인 경우)

```bash
# Backend 이미지 빌드
docker build -f docker/Dockerfile.backend -t petory-backend:latest .

# Frontend 이미지 빌드
docker build -f docker/Dockerfile.frontend -t petory-frontend:latest .
```

#### 4단계: 기존 컨테이너 중지

```bash
# 기존 컨테이너 중지
docker-compose -f docker-compose.prod.yml down --timeout 30

# 또는 특정 서비스만 재시작
docker-compose -f docker-compose.prod.yml restart backend
```

#### 5단계: 새 컨테이너 시작

```bash
# 환경 변수 파일 지정
export $(cat .env.production | xargs)

# 컨테이너 시작
docker-compose -f docker-compose.prod.yml up -d

# 또는 특정 서비스만 시작
docker-compose -f docker-compose.prod.yml up -d backend frontend
```

#### 6단계: Health Check

```bash
# Backend Health Check
sleep 30
curl -f http://localhost:8080/api/actuator/health

# Frontend 확인
curl -f http://localhost:3000

# Nginx 확인
curl -f http://localhost
```

#### 7단계: 로그 확인

```bash
# 모든 컨테이너 로그
docker-compose -f docker-compose.prod.yml logs -f

# 특정 서비스 로그
docker-compose -f docker-compose.prod.yml logs -f backend

# 최근 100줄
docker-compose -f docker-compose.prod.yml logs --tail=100 backend
```

#### 8단계: 정리

```bash
# 사용하지 않는 이미지 삭제
docker image prune -f

# 사용하지 않는 볼륨 확인 (주의: 데이터 삭제됨)
docker volume ls
```

---

### 방법 2: 자동 배포 (CI/CD)

GitHub Actions를 통한 자동 배포는 [CI/CD 파이프라인](./03-cicd-pipeline.md) 문서를 참고하세요.

---

## 🔄 무중단 배포 스크립트

### `scripts/deploy.sh`

```bash
#!/bin/bash
set -e

COMPOSE_FILE="docker-compose.prod.yml"
PROJECT_DIR="/opt/petory"
HEALTH_CHECK_URL="http://localhost:8080/api/actuator/health"

cd $PROJECT_DIR

echo "🚀 Starting deployment..."

# 1. 환경 변수 검증
echo "📋 Validating environment variables..."
if [ ! -f .env.production ]; then
    echo "❌ .env.production file not found"
    exit 1
fi

# 2. 최신 이미지 Pull (Docker Hub에서)
echo "📥 Pulling latest images..."
docker-compose -f $COMPOSE_FILE pull || echo "⚠️ Image pull failed, using local images"

# 3. 백업 (선택적)
echo "💾 Creating backup..."
BACKUP_DIR="./backups/$(date +%Y%m%d_%H%M%S)"
mkdir -p $BACKUP_DIR
docker exec petory-mysql-prod mysqldump -u root -p${MYSQL_ROOT_PASSWORD} petory > $BACKUP_DIR/mysql_backup.sql 2>/dev/null || echo "⚠️ Backup skipped"

# 4. 기존 컨테이너 중지
echo "🛑 Stopping current containers..."
docker-compose -f $COMPOSE_FILE down --timeout 30

# 5. 새 컨테이너 시작
echo "▶️ Starting new containers..."
docker-compose -f $COMPOSE_FILE up -d

# 6. Health Check 대기
echo "⏳ Waiting for services to be healthy..."
sleep 30

# 7. Health Check
echo "🔍 Performing health check..."
MAX_RETRIES=10
RETRY_COUNT=0

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    if curl -f $HEALTH_CHECK_URL > /dev/null 2>&1; then
        echo "✅ Health check passed"
        break
    fi
    
    RETRY_COUNT=$((RETRY_COUNT + 1))
    echo "⏳ Health check failed, retrying... ($RETRY_COUNT/$MAX_RETRIES)"
    sleep 10
done

if [ $RETRY_COUNT -eq $MAX_RETRIES ]; then
    echo "❌ Health check failed after $MAX_RETRIES retries"
    echo "🔄 Rolling back..."
    
    # 롤백: 이전 이미지 사용
    docker-compose -f $COMPOSE_FILE down
    docker tag petory-backend:previous petory-backend:latest 2>/dev/null || true
    docker-compose -f $COMPOSE_FILE up -d
    
    echo "❌ Deployment failed, rolled back to previous version"
    exit 1
fi

# 8. 정리
echo "🧹 Cleaning up..."
docker image prune -f

echo "✅ Deployment completed successfully!"
```

### 실행 권한 부여

```bash
chmod +x scripts/deploy.sh
```

---

## 🔙 롤백 프로세스

### 수동 롤백

```bash
#!/bin/bash
# scripts/rollback.sh

cd /opt/petory

echo "🔄 Rolling back to previous version..."

# 1. 기존 컨테이너 중지
docker-compose -f docker-compose.prod.yml down

# 2. 이전 이미지로 태그 변경
PREVIOUS_TAG=$(docker images petory-backend --format "{{.Tag}}" | grep -v latest | head -1)

if [ -z "$PREVIOUS_TAG" ]; then
    echo "❌ No previous image found"
    exit 1
fi

echo "📦 Rolling back to tag: $PREVIOUS_TAG"
docker tag petory-backend:$PREVIOUS_TAG petory-backend:latest

# 3. 컨테이너 재시작
docker-compose -f docker-compose.prod.yml up -d

# 4. Health Check
sleep 30
curl -f http://localhost:8080/api/actuator/health

echo "✅ Rollback completed"
```

---

## 📊 배포 후 확인사항

### 1. 서비스 상태 확인

```bash
# 컨테이너 상태
docker-compose -f docker-compose.prod.yml ps

# 리소스 사용량
docker stats --no-stream

# 네트워크 상태
docker network ls
docker network inspect petory_petory-network
```

### 2. 애플리케이션 로그 확인

```bash
# Backend 로그
docker logs petory-backend-prod --tail 100 -f

# Frontend 로그
docker logs petory-frontend-prod --tail 100 -f

# MySQL 로그
docker logs petory-mysql-prod --tail 100 -f

# Redis 로그
docker logs petory-redis-prod --tail 100 -f
```

### 3. 데이터베이스 연결 확인

```bash
# MySQL 연결 테스트
docker exec -it petory-mysql-prod mysql -u petory -p

# Redis 연결 테스트
docker exec -it petory-redis-prod redis-cli -a ${REDIS_PASSWORD} ping
```

### 4. API 엔드포인트 테스트

```bash
# Health Check
curl http://localhost:8080/api/actuator/health

# API 테스트
curl http://localhost/api/boards
```

---

## 🔍 문제 해결

### 컨테이너가 시작되지 않을 때

```bash
# 로그 확인
docker-compose -f docker-compose.prod.yml logs backend

# 컨테이너 상태 확인
docker ps -a

# 재시작
docker-compose -f docker-compose.prod.yml restart backend
```

### 데이터베이스 연결 오류

```bash
# MySQL 상태 확인
docker exec petory-mysql-prod mysqladmin ping -h localhost

# 네트워크 확인
docker network inspect petory_petory-network

# 환경 변수 확인
docker exec petory-backend-prod env | grep SPRING_DATASOURCE
```

### 포트 충돌

```bash
# 포트 사용 확인 (Linux)
sudo netstat -tulpn | grep :8080
sudo netstat -tulpn | grep :3306

# macOS(맥북) — netstat 옵션이 다르므로 예:
lsof -nP -iTCP:8080 -sTCP:LISTEN
lsof -nP -iTCP:3306 -sTCP:LISTEN
```

```yaml
# 포트 변경 (docker-compose.prod.yml)
ports:
  - "8081:8080"  # 외부 포트 변경
```

---

## 📈 모니터링 설정

### 배포 후 모니터링 체크리스트

- [ ] Health Check 엔드포인트 응답 확인
- [ ] 데이터베이스 연결 정상
- [ ] Redis 연결 정상
- [ ] API 엔드포인트 응답 확인
- [ ] Frontend 정상 로드
- [ ] 에러 로그 확인
- [ ] 리소스 사용량 확인

---

## 📝 다음 단계

1. [모니터링 및 로깅](./07-monitoring-logging.md) - 운영 모니터링 설정
2. [트러블슈팅](./08-troubleshooting.md) - 일반적인 문제 해결

