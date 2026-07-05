# 모니터링 및 로깅

## 📋 개요

Petory 프로젝트의 모니터링 및 로깅 전략을 설명합니다.

**macOS**: 아래 `docker stats`, `docker logs` 명령은 Docker Desktop 설치 환경에서 동일하게 동작합니다. 백그라운드 스케줄(예: 헬스체크 cron)은 로컬 맥이 아니라 **배포 서버**에 두는 경우가 많습니다. 로컬 Docker만 쓸 때는 [00-macos-local.md](./00-macos-local.md)를 참고하세요.

---

## 📊 모니터링 전략

### 1. 애플리케이션 모니터링

#### Spring Boot Actuator

`build.gradle`에 추가:

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'io.micrometer:micrometer-registry-prometheus'
}
```

`application.properties`:

```properties
# Actuator 엔드포인트 활성화
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.show-details=when-authorized
management.metrics.export.prometheus.enabled=true

# Health Check 상세 정보
management.health.db.enabled=true
management.health.redis.enabled=true
```

#### 주요 엔드포인트

- `/actuator/health`: 헬스 체크
- `/api/actuator/metrics`: 메트릭 목록
- `/api/actuator/prometheus`: Prometheus 메트릭

---

### 2. 컨테이너 모니터링

#### Docker Stats

```bash
# 실시간 리소스 사용량
docker stats

# 특정 컨테이너만
docker stats petory-app petory-mysql petory-redis

# JSON 형식으로 출력
docker stats --no-stream --format json
```

#### cAdvisor (선택)

```yaml
# docker-compose.yml에 추가
services:
  cadvisor:
    image: gcr.io/cadvisor/cadvisor:latest
    container_name: petory-cadvisor
    ports:
      - "8081:8080"
    volumes:
      - /:/rootfs:ro
      - /var/run:/var/run:ro
      - /sys:/sys:ro
      - /var/lib/docker/:/var/lib/docker:ro
    networks:
      - petory-network
```

---

### 3. 로그 관리

#### 로그 수집 구조

```
logs/
├── application.log          # 애플리케이션 로그
├── error.log                # 에러 로그
└── access.log               # 접근 로그
```

#### 로그 설정 (`application.properties`)

```properties
# 로그 파일 설정
logging.file.name=logs/petory.log
logging.file.max-size=10MB
logging.file.max-history=30
logging.file.total-size-cap=1GB

# 로그 레벨
logging.level.root=INFO
logging.level.com.linkup.Petory=INFO
logging.level.org.springframework.web=WARN
logging.level.org.hibernate.SQL=WARN

# 로그 포맷
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} - %msg%n
logging.pattern.file=%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n
```

---

## 📝 로그 수집 스크립트

### `scripts/collect-logs.sh`

```bash
#!/bin/bash
# 로그 수집 스크립트

LOG_DIR="./logs/archive/$(date +%Y%m%d)"
mkdir -p $LOG_DIR

# Backend 로그
docker logs petory-app --since 24h > $LOG_DIR/backend.log 2>&1

# Frontend 로그
docker logs petory-nginx --since 24h > $LOG_DIR/frontend.log 2>&1

# MySQL 로그
docker logs petory-mysql --since 24h > $LOG_DIR/mysql.log 2>&1

# Redis 로그
docker logs petory-redis --since 24h > $LOG_DIR/redis.log 2>&1

# Nginx 로그
docker logs petory-nginx-prod --since 24h > $LOG_DIR/nginx.log 2>&1

echo "✅ Logs collected in $LOG_DIR"
```

---

## 🔔 알림 설정

### Health Check 모니터링

```bash
#!/bin/bash
# health-check-monitor.sh

HEALTH_URL="http://localhost:8080/actuator/health"
ALERT_EMAIL="admin@petory.com"

if ! curl -f $HEALTH_URL > /dev/null 2>&1; then
    echo "❌ Health check failed"
    
    # 이메일 알림 (sendmail 필요)
    echo "Health check failed at $(date)" | mail -s "Petory Health Check Failed" $ALERT_EMAIL
    
    # Slack 알림 (curl 사용)
    curl -X POST -H 'Content-type: application/json' \
        --data '{"text":"❌ Petory Health Check Failed"}' \
        $SLACK_WEBHOOK_URL
    
    exit 1
fi

echo "✅ Health check passed"
```

### Crontab 설정

```bash
# 5분마다 헬스 체크
*/5 * * * * /opt/petory/scripts/health-check-monitor.sh >> /var/log/health-check.log 2>&1
```

---

## 📈 메트릭 수집

### Prometheus 설정 (선택)

`prometheus.yml`:

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'petory-backend'
    static_configs:
      - targets: ['backend:8080']
    metrics_path: '/api/actuator/prometheus'
```

---

## 🗄️ 데이터베이스 모니터링

### MySQL 모니터링 쿼리

```sql
-- 연결 수
SHOW STATUS LIKE 'Threads_connected';

-- 쿼리 성능
SHOW STATUS LIKE 'Slow_queries';

-- 테이블 크기
SELECT 
    table_name,
    ROUND(((data_length + index_length) / 1024 / 1024), 2) AS 'Size (MB)'
FROM information_schema.TABLES
WHERE table_schema = 'petory'
ORDER BY (data_length + index_length) DESC;
```

---

## 📊 성능 모니터링

### 응답 시간 모니터링

```bash
#!/bin/bash
# response-time-monitor.sh

API_URL="http://localhost/api/boards"
THRESHOLD=2.0  # 초

RESPONSE_TIME=$(curl -o /dev/null -s -w '%{time_total}' $API_URL)

if (( $(echo "$RESPONSE_TIME > $THRESHOLD" | bc -l) )); then
    echo "⚠️ High response time: ${RESPONSE_TIME}s (threshold: ${THRESHOLD}s)"
fi
```

---

## 📝 다음 단계

1. [트러블슈팅](./08-troubleshooting.md) - 일반적인 문제 해결

