# 배포 프로세스

## 📋 개요

Petory 프로젝트의 실제 배포 프로세스를 단계별로 설명합니다.

**환경 구분**

- **리눅스 서버(프로덕션/스테이징)**: 아래 `ssh`, `/opt/petory` 경로, `systemd`/cron 등은 **서버** 기준입니다.
- **macOS(맥북)**: 로컬에서 Docker로만 연습할 때는 프로젝트 클론 경로(예: `~/project/Petory`)에서 `docker compose`를 실행하면 됩니다. [macOS 로컬 가이드](./00-macos-local.md) 참고. Compose 파일은 레포 루트의 `docker-compose.yml` — 상세는 [Docker 설정](./02-docker-configuration.md).

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

# SSL 인증서 디렉토리 (docker-compose.yml의 nginx 서비스가 마운트하는 경로: ./nginx/ssl)
mkdir -p nginx/ssl

# 백업 디렉토리
mkdir -p backups
```

### 3. 환경 변수 파일 생성

```bash
# .env 파일 생성 (docker-compose.yml은 정확히 .env 파일명을 읽음)
cp .env.example .env

# 편집
nano .env

# 권한 설정
chmod 600 .env
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
# .env 파일 존재 확인 (docker-compose.yml이 정확히 .env를 읽음)
ls -la .env
```

> `scripts/validate-env.sh` 같은 검증 스크립트는 레포에 **아직 없음** — 직접 만들어 쓸 경우 `.env.example`의 필수 키(DB_*, REDIS_*, JWT_SECRET 등) 목록을 기준으로 작성

#### 3단계: 프론트엔드 빌드 (nginx가 서빙할 정적 파일 생성)

```bash
cd frontend
npm ci
npm run build   # frontend/build 생성 — docker-compose.yml의 nginx 서비스가 이 폴더를 볼륨으로 마운트함
cd ..
```

#### 4단계: 기존 컨테이너 중지

```bash
docker compose down --timeout 30

# 또는 특정 서비스만 재시작
docker compose restart app
```

#### 5단계: 이미지 빌드 + 새 컨테이너 시작

레포에는 아직 이미지 레지스트리 push/pull 파이프라인이 없어서(CD 미구축), 서버에서 직접 빌드한다:

```bash
# mysql/redis/nlp-server/app/nginx 전체 빌드+기동 (보통 app만 코드가 바뀌므로 app만 재빌드됨)
docker compose up --build -d

# 또는 app만 재빌드
docker compose up --build -d app
```

#### 6단계: Health Check

```bash
# Backend Health Check
sleep 30
curl -f http://localhost:8080/actuator/health

# Nginx(프론트+리버스프록시) 확인
curl -f http://localhost
```

#### 7단계: 로그 확인

```bash
# 모든 컨테이너 로그
docker compose logs -f

# 특정 서비스 로그
docker compose logs -f app

# 최근 100줄
docker compose logs --tail=100 app
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

> **참고**: `scripts/deploy.sh` 같은 자동화 스크립트는 레포에 **아직 없음**. 지금은 이미지 레지스트리 push/pull(CD)이 없어서 서버에서 직접 `docker compose up --build`로 재빌드하는 방식 — 아래는 필요 시 만들어 쓸 수 있는 예시.

```bash
#!/bin/bash
# 예시: scripts/deploy.sh (레포에 없음, 참고용)
set -e

PROJECT_DIR="/opt/petory"
HEALTH_CHECK_URL="http://localhost:8080/actuator/health"

cd $PROJECT_DIR

echo "🚀 Starting deployment..."

# 1. 환경 변수 검증
if [ ! -f .env ]; then
    echo "❌ .env file not found"
    exit 1
fi

# 2. 최신 코드 반영
git pull origin main

# 3. 백업 (선택적)
BACKUP_DIR="./backups/$(date +%Y%m%d_%H%M%S)"
mkdir -p $BACKUP_DIR
docker exec petory-mysql mysqldump -u root -p${DB_ROOT_PASSWORD} petory > $BACKUP_DIR/mysql_backup.sql 2>/dev/null || echo "⚠️ Backup skipped"

# 4. 프론트엔드 재빌드
(cd frontend && npm ci && npm run build)

# 5. 이미지 재빌드 + 컨테이너 교체
docker compose up --build -d

# 6. Health Check
echo "⏳ Waiting for health check..."
MAX_RETRIES=10
RETRY_COUNT=0
while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    if curl -f $HEALTH_CHECK_URL > /dev/null 2>&1; then
        echo "✅ Health check passed"
        break
    fi
    RETRY_COUNT=$((RETRY_COUNT + 1))
    echo "⏳ retrying... ($RETRY_COUNT/$MAX_RETRIES)"
    sleep 10
done

if [ $RETRY_COUNT -eq $MAX_RETRIES ]; then
    echo "❌ Health check failed — 아래 롤백 프로세스 참고"
    exit 1
fi

docker image prune -f
echo "✅ Deployment completed successfully!"
```

---

## 🔙 롤백 프로세스

이미지 레지스트리/태그 관리가 아직 없으므로, **git 기준 이전 커밋으로 되돌려 재빌드**하는 방식이 현재로선 가장 현실적:

```bash
cd /opt/petory

# 1. 이전 커밋으로 되돌리기 (직전 배포 커밋 해시 확인 후)
git log --oneline -5
git checkout <이전_커밋_해시>

# 2. 재빌드 + 재기동
docker compose up --build -d

# 3. Health Check
sleep 30
curl -f http://localhost:8080/actuator/health
```

> CI가 이미지를 레지스트리에 push하도록 확장되면(`03-cicd-pipeline.md` 참고), 그때는 `docker tag`/`docker pull`로 이전 이미지 태그를 되돌리는 방식으로 더 빠르게 롤백할 수 있음.

---

## 📊 배포 후 확인사항

### 1. 서비스 상태 확인

```bash
# 컨테이너 상태
docker compose ps

# 리소스 사용량
docker stats --no-stream

# 네트워크 상태
docker network ls
docker network inspect petory_default
```

### 2. 애플리케이션 로그 확인

```bash
# Backend 로그
docker logs petory-app --tail 100 -f

# Frontend 로그
docker logs petory-nginx --tail 100 -f

# MySQL 로그
docker logs petory-mysql --tail 100 -f

# Redis 로그
docker logs petory-redis --tail 100 -f
```

### 3. 데이터베이스 연결 확인

```bash
# MySQL 연결 테스트
docker exec -it petory-mysql mysql -u petory -p

# Redis 연결 테스트
docker exec -it petory-redis redis-cli -a ${REDIS_PASSWORD} ping
```

### 4. API 엔드포인트 테스트

```bash
# Health Check
curl http://localhost:8080/actuator/health

# API 테스트
curl http://localhost/api/boards
```

---

## 🔍 문제 해결

### 컨테이너가 시작되지 않을 때

```bash
# 로그 확인
docker compose logs app

# 컨테이너 상태 확인
docker ps -a

# 재시작
docker compose restart app
```

### 데이터베이스 연결 오류

```bash
# MySQL 상태 확인
docker exec petory-mysql mysqladmin ping -h localhost

# 네트워크 확인
docker network inspect petory_default

# 환경 변수 확인
docker exec petory-app env | grep SPRING_DATASOURCE
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
# 포트 변경 (docker-compose.yml)
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

