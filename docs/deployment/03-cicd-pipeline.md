# CI/CD 파이프라인

## 📋 개요

GitHub Actions를 이용한 CI/CD 파이프라인 구축 방법을 설명합니다.

---

## 🔄 CI/CD 흐름

```
Push to main branch
    ↓
GitHub Actions 트리거
    ↓
├─ 테스트 실행 (Unit Test)
├─ 코드 품질 검사 (Lint, Checkstyle)
├─ 빌드 (Gradle + npm)
├─ Docker 이미지 빌드
├─ Docker Hub Push
└─ 서버 배포 (SSH)
```

---

## 📁 GitHub Actions 워크플로우 구조

```
.github/
└── workflows/
    ├── ci.yml              # CI 파이프라인 (테스트, 빌드)
    ├── cd-production.yml   # 프로덕션 배포
    └── cd-staging.yml      # 스테이징 배포
```

---

## 🔧 CI 파이프라인

### `.github/workflows/ci.yml`

```yaml
name: CI Pipeline

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

jobs:
  backend-test:
    name: Backend Test & Build
    runs-on: ubuntu-latest
    
    services:
      mysql:
        image: mysql:8.0
        env:
          MYSQL_ROOT_PASSWORD: testpassword
          MYSQL_DATABASE: petory_test
        ports:
          - 3306:3306
        options: >-
          --health-cmd="mysqladmin ping"
          --health-interval=10s
          --health-timeout=5s
          --health-retries=3

      redis:
        image: redis:7-alpine
        ports:
          - 6379:6379
        options: >-
          --health-cmd="redis-cli ping"
          --health-interval=10s
          --health-timeout=5s
          --health-retries=3

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'gradle'

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Run tests
        run: ./gradlew test
        env:
          SPRING_DATASOURCE_URL: jdbc:mysql://localhost:3306/petory_test
          SPRING_DATASOURCE_USERNAME: root
          SPRING_DATASOURCE_PASSWORD: testpassword
          SPRING_REDIS_HOST: localhost
          SPRING_REDIS_PORT: 6379

      - name: Build application
        run: ./gradlew build -x test

      - name: Upload test results
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: test-results
          path: build/test-results/
          retention-days: 30

      - name: Upload JAR artifact
        uses: actions/upload-artifact@v4
        with:
          name: backend-jar
          path: build/libs/*.jar
          retention-days: 7

  frontend-test:
    name: Frontend Test & Build
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '18'
          cache: 'npm'
          cache-dependency-path: frontend/package-lock.json

      - name: Install dependencies
        working-directory: ./frontend
        run: npm ci

      - name: Run linter
        working-directory: ./frontend
        run: npm run lint || true

      - name: Run tests
        working-directory: ./frontend
        run: npm test -- --coverage --watchAll=false

      - name: Build application
        working-directory: ./frontend
        run: npm run build

      - name: Upload build artifact
        uses: actions/upload-artifact@v4
        with:
          name: frontend-build
          path: frontend/build/
          retention-days: 7

  docker-build:
    name: Build Docker Images
    runs-on: ubuntu-latest
    needs: [backend-test, frontend-test]
    if: github.event_name == 'push'

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Login to Docker Hub
        uses: docker/login-action@v3
        with:
          username: ${{ secrets.DOCKER_USERNAME }}
          password: ${{ secrets.DOCKER_PASSWORD }}

      - name: Build and push backend image
        uses: docker/build-push-action@v5
        with:
          context: .
          file: ./docker/Dockerfile.backend
          push: true
          tags: |
            ${{ secrets.DOCKER_USERNAME }}/petory-backend:latest
            ${{ secrets.DOCKER_USERNAME }}/petory-backend:${{ github.sha }}
          cache-from: type=registry,ref=${{ secrets.DOCKER_USERNAME }}/petory-backend:buildcache
          cache-to: type=registry,ref=${{ secrets.DOCKER_USERNAME }}/petory-backend:buildcache,mode=max

      - name: Build and push frontend image
        uses: docker/build-push-action@v5
        with:
          context: .
          file: ./docker/Dockerfile.frontend
          push: true
          tags: |
            ${{ secrets.DOCKER_USERNAME }}/petory-frontend:latest
            ${{ secrets.DOCKER_USERNAME }}/petory-frontend:${{ github.sha }}
          cache-from: type=registry,ref=${{ secrets.DOCKER_USERNAME }}/petory-frontend:buildcache
          cache-to: type=registry,ref=${{ secrets.DOCKER_USERNAME }}/petory-frontend:buildcache,mode=max
```

---

## 🚀 프로덕션 배포 파이프라인

### `.github/workflows/cd-production.yml`

```yaml
name: Deploy to Production

on:
  push:
    branches: [ main ]
    tags:
      - 'v*'

jobs:
  deploy:
    name: Deploy to Production Server
    runs-on: ubuntu-latest
    environment: production

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Deploy to server
        uses: appleboy/ssh-action@v1.0.0
        with:
          host: ${{ secrets.PRODUCTION_HOST }}
          username: ${{ secrets.PRODUCTION_USER }}
          key: ${{ secrets.PRODUCTION_SSH_KEY }}
          port: ${{ secrets.PRODUCTION_PORT }}
          script: |
            cd /opt/petory
            
            # Pull latest images
            docker-compose -f docker-compose.prod.yml pull
            
            # Backup current containers
            docker-compose -f docker-compose.prod.yml down --timeout 30
            
            # Update environment variables if needed
            # cp .env.example .env
            # nano .env
            
            # Start new containers
            docker-compose -f docker-compose.prod.yml up -d
            
            # Health check
            sleep 30
            curl -f http://localhost:8080/api/actuator/health || exit 1
            
            # Clean up old images
            docker image prune -f
            
            echo "Deployment completed successfully"
```

---

## 🧪 스테이징 배포 파이프라인

### `.github/workflows/cd-staging.yml`

```yaml
name: Deploy to Staging

on:
  push:
    branches: [ develop ]

jobs:
  deploy:
    name: Deploy to Staging Server
    runs-on: ubuntu-latest
    environment: staging

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Deploy to server
        uses: appleboy/ssh-action@v1.0.0
        with:
          host: ${{ secrets.STAGING_HOST }}
          username: ${{ secrets.STAGING_USER }}
          key: ${{ secrets.STAGING_SSH_KEY }}
          port: ${{ secrets.STAGING_PORT }}
          script: |
            cd /opt/petory-staging
            docker-compose -f docker-compose.prod.yml pull
            docker-compose -f docker-compose.prod.yml up -d
            docker image prune -f
```

---

## 🔐 GitHub Secrets 설정

### 필수 Secrets

Repository Settings → Secrets and variables → Actions에서 다음을 설정:

#### Docker Hub
- `DOCKER_USERNAME`: Docker Hub 사용자명
- `DOCKER_PASSWORD`: Docker Hub 액세스 토큰

#### 프로덕션 서버
- `PRODUCTION_HOST`: 서버 IP 또는 도메인
- `PRODUCTION_USER`: SSH 사용자명
- `PRODUCTION_SSH_KEY`: SSH private key
- `PRODUCTION_PORT`: SSH 포트 (기본: 22)

#### 스테이징 서버
- `STAGING_HOST`: 서버 IP 또는 도메인
- `STAGING_USER`: SSH 사용자명
- `STAGING_SSH_KEY`: SSH private key
- `STAGING_PORT`: SSH 포트

---

## 🔍 배포 스크립트 (서버 측)

### `deploy.sh` (서버에 저장)

```bash
#!/bin/bash
set -e

COMPOSE_FILE="docker-compose.prod.yml"
PROJECT_DIR="/opt/petory"

cd $PROJECT_DIR

echo "Pulling latest images..."
docker-compose -f $COMPOSE_FILE pull

echo "Stopping current containers..."
docker-compose -f $COMPOSE_FILE down --timeout 30

echo "Starting new containers..."
docker-compose -f $COMPOSE_FILE up -d

echo "Waiting for health check..."
sleep 30

echo "Checking backend health..."
if curl -f http://localhost:8080/api/actuator/health; then
    echo "✅ Deployment successful"
else
    echo "❌ Health check failed, rolling back..."
    docker-compose -f $COMPOSE_FILE down
    # 롤백 로직 추가
    exit 1
fi

echo "Cleaning up old images..."
docker image prune -f

echo "Deployment completed"
```

---

## 📊 배포 전략 옵션

### 1. Blue-Green 배포

```yaml
- name: Blue-Green Deployment
  script: |
    # Green 환경 시작
    docker-compose -f docker-compose.green.yml up -d
    
    # Health check
    sleep 30
    curl -f http://localhost:8081/api/actuator/health
    
    # Nginx 설정 변경 (Green으로 전환)
    cp nginx/green.conf /etc/nginx/conf.d/default.conf
    nginx -s reload
    
    # Blue 환경 중지
    docker-compose -f docker-compose.blue.yml down
```

### 2. Canary 배포 (점진적 배포)

```yaml
- name: Canary Deployment
  script: |
    # 10% 트래픽만 새 버전으로
    docker-compose -f docker-compose.prod.yml up -d --scale backend=1
    # Nginx에서 10%만 새 버전으로 라우팅
    
    # 모니터링 후 점진적 확대
    # 50% → 100%
```

---

## 🔔 배포 알림

### Slack 알림 추가

```yaml
- name: Notify Slack
  uses: 8398a7/action-slack@v3
  with:
    status: ${{ job.status }}
    text: |
      Deployment Status: ${{ job.status }}
      Branch: ${{ github.ref }}
      Commit: ${{ github.sha }}
    webhook_url: ${{ secrets.SLACK_WEBHOOK }}
  if: always()
```

---

## 📈 모니터링 통합

### 배포 후 모니터링 체크

```yaml
- name: Post-deployment checks
  script: |
    # API 응답 시간 확인
    response_time=$(curl -o /dev/null -s -w '%{time_total}' http://localhost:8080/api/actuator/health)
    
    if (( $(echo "$response_time > 5.0" | bc -l) )); then
      echo "⚠️ High response time: ${response_time}s"
      exit 1
    fi
    
    # 에러 로그 확인
    error_count=$(docker logs petory-backend-prod --tail 100 | grep -i error | wc -l)
    
    if [ $error_count -gt 10 ]; then
      echo "⚠️ High error count: $error_count"
      exit 1
    fi
```

---

## 🛡️ 롤백 전략

### 자동 롤백 스크립트

```bash
#!/bin/bash
# rollback.sh

PREVIOUS_IMAGE_TAG=$(docker images --format "{{.Tag}}" petory-backend | grep -v latest | head -1)

if [ -z "$PREVIOUS_IMAGE_TAG" ]; then
    echo "No previous image found"
    exit 1
fi

echo "Rolling back to $PREVIOUS_IMAGE_TAG..."

docker-compose -f docker-compose.prod.yml down
docker tag petory-backend:$PREVIOUS_IMAGE_TAG petory-backend:latest
docker-compose -f docker-compose.prod.yml up -d

echo "Rollback completed"
```

---

## 📝 다음 단계

1. [Nginx 설정](./04-nginx-configuration.md) - 리버스 프록시 구성
2. [환경 변수 관리](./05-environment-variables.md) - 보안 설정
3. [배포 프로세스](./06-deployment-process.md) - 실제 배포 가이드

