# Docker · Redis · 전체 스택

## 📋 이 문서의 역할

- **전체 스택(권장)**: 레포 루트의 `Dockerfile` + `docker-compose.yml`로 mysql·redis·app·nginx를 한 번에 기동 — 아래 [전체 스택 Docker Compose](#전체-스택-docker-compose) 참고
- **로컬 개발용 Redis만 따로**: 백엔드는 `./gradlew bootRun`으로 호스트에서 직접 돌리고 Redis만 Docker로 띄우는 기존 방식 — 아래 [로컬 Redis (Docker, 수동 실행)](#로컬-redis-docker-수동-실행) 참고

---

## docker 명령어 정리

실행 / 종료

시작
docker start petory-redis
docker start petory-mysql
종료
docker stop petory-redis
docker stop petory-mysql

재시작
docker restart petory-redis
docker restart petory-mysql

로그 확인
docker logs petory-redis
docker logs petory-mysql

👉 문제 생기면 무조건 이거 먼저 본다

컨테이너 삭제
docker rm petory-redis
docker rm petory-mysql

상태 확인
docker ps # 실행 중
docker ps -a # 전체 (꺼진 것 포함)

접속 (자주 씀)
MySQL
docker exec -it petory-mysql mysql -u root -p
Redis
docker exec -it petory-redis redis-cli

---

## 현재 레포 상태

| 항목                                         | 상태                                                                 |
| -------------------------------------------- | -------------------------------------------------------------------- |
| `Dockerfile`(레포 루트)                      | **있음** — 멀티스테이지 빌드 (`eclipse-temurin:17-jdk-jammy` → `17-jre-jammy`), non-root 유저(`petory`)로 실행 |
| `docker-compose.yml`(레포 루트)              | **있음** — `mysql`, `redis`, `nlp-server`, `app`, `nginx` 5개 서비스. `docker compose up --build -d`로 전체 스택 기동 검증 완료 |
| `petory-nlp-server/Dockerfile`                | **있음** — Python 3.9-slim, FastAPI, non-root 유저. `app`은 `PetIntentClient`로 `http://nlp-server:8000` 호출 |
| DB 스키마 초기화                              | `sql/migration/000-baseline-schema.sql`이 `docker-entrypoint-initdb.d`로 마운트되어 최초 기동 시 자동 실행됨 (전체 스키마 baseline dump) |
| 로컬 Redis만 단독 실행                        | `docker run`으로 수동 실행 가능 (아래, 백엔드를 호스트에서 `bootRun`으로 돌릴 때)                                |
| Spring Boot ↔ Redis                          | `application.properties`(또는 `.env`)의 **`spring.redis.*`** + `RedisConfig.java` — `spring.data.redis.*` 아님 주의 |

---

## 로컬 Redis (Docker, 수동 실행)

데이터·설정을 호스트에 두고 컨테이너에 마운트하는 방식 예시입니다. **경로는 본인 환경에 맞게 수정**하세요.

### 구성 요약

- 컨테이너 이름: `petory-redis`
- 포트: `6379` (호스트 ↔ 컨테이너 동일)
- 볼륨: 호스트 `data` → 컨테이너 `/data`, 호스트 `redis.conf` → 컨테이너 내 설정 경로
- `redis.conf` 예: `requirepass`, `appendonly yes`, `maxmemory`, `maxmemory-policy allkeys-lru` 등

### 실행 예시 (macOS 경로 예: `~/petory_docker_data/redis/...`)

```bash
docker run -d \
  --name petory-redis \
  -p 6379:6379 \
  -v "$HOME/petory_docker_data/redis/data:/data" \
  -v "$HOME/petory_docker_data/redis/conf/redis.conf:/usr/local/etc/redis/redis.conf" \
  redis \
  redis-server /usr/local/etc/redis/redis.conf
```

- 컨테이너를 지워도 **호스트 볼륨**에 RDB/AOF가 남으면 데이터 유지 가능
- Redis를 끈 상태에서 Spring만 띄우면 캐시·알림 등 Redis 사용 구간에서 오류 날 수 있음 → **Redis 먼저 기동** 권장

### Spring Boot 연동

애플리케이션은 **호스트에서 실행**한다고 가정할 때 `localhost:6379`로 붙습니다.  
`RedisConfig`는 **`spring.redis.host` / `spring.redis.port` / `spring.redis.password`** 를 읽습니다 (`spring.data.redis.*` 아님).

`application.properties` 예:

```properties
spring.redis.host=localhost
spring.redis.port=6379
spring.redis.password=${REDIS_PASSWORD:실제비밀번호}
```

---

## MySQL

전체 스택을 Compose로 띄울 때는 `docker-compose.yml`의 `mysql` 서비스가 담당하며, 백엔드 컨테이너는 서비스 이름(`mysql`)과 JDBC URL(`jdbc:mysql://mysql:3306/...`)로 접속합니다 (`application-prod.properties`의 `${DB_HOST}`가 `docker-compose.yml`의 `environment.DB_HOST: mysql`로 채워짐).

로컬에서 백엔드를 호스트에서 직접 돌릴 때는(방법 B) **호스트에 설치한 MySQL**을 씁니다.

---

## 전체 스택 Docker Compose

레포 루트의 `Dockerfile`/`docker-compose.yml` 구성:

1. **Dockerfile (백엔드, 멀티스테이지)**

   - Build stage: `eclipse-temurin:17-jdk-jammy` — `gradlew`, `gradle/`, `build.gradle`, `settings.gradle`, `backend/` 복사 후 `./gradlew bootJar`
   - Runtime stage: `eclipse-temurin:17-jre-jammy` — non-root 유저(`petory`)로 JAR 실행
   - **주의**: `eclipse-temurin:17-*-alpine` 계열은 arm64(Apple Silicon Mac) 매니페스트가 없어 M시리즈 맥에서 빌드 자체가 실패함 → jammy(Debian 기반) 사용 필수

2. **docker-compose.yml**

   - 서비스: `mysql`, `redis`, `nlp-server`(`petory-nlp-server/Dockerfile`로 빌드), `app`(빌드한 이미지), `nginx`
   - 컨테이너 간 통신은 `localhost`가 아니라 **서비스 이름**(`mysql`, `redis`, `nlp-server`, `app`) 사용
   - `mysql` 서비스는 `sql/migration/000-baseline-schema.sql`을 `docker-entrypoint-initdb.d`로 마운트 — MySQL 데이터 볼륨이 비어있을 때(최초 기동) 자동으로 전체 스키마가 생성됨. `sql/migration/applied/`(하위 폴더)는 MySQL이 하위 디렉토리를 스캔하지 않아 자동실행 대상에서 제외됨
   - `app`은 `depends_on`에 `nlp-server: condition: service_healthy`가 걸려 있어, nlp-server가 `/health` 응답을 시작한 뒤에야 기동됨
   - `nlp-server`는 임베딩 모델(`jhgan/ko-sroberta-multitask`)을 최초 기동 시 HuggingFace에서 다운로드하며, `nlp_model_cache` 볼륨에 캐시해 재기동 시 재다운로드하지 않음
   - 로컬 macOS에서 네이티브 MySQL(3306)/Redis(6379)를 이미 쓰고 있으면 호스트 포트가 충돌 — `ports`를 `3307:3306`, `6380:6379` 등으로 조정 (컨테이너 간 통신엔 영향 없음)

3. **GitHub Actions**

   - 현재는 빌드·테스트까지 — 이미지 push, 서버에서 `docker compose pull && up`까지 이어지는 CD는 아직 없음. 상세는 [03-cicd-pipeline.md](./03-cicd-pipeline.md)

4. **환경 변수**
   - `.env.example`을 `.env`로 복사해 채움 — [05-environment-variables.md](./05-environment-variables.md)
   - OAuth2 client-id/secret, 메일 계정 등은 실제 값이 없어도 **더미값**은 넣어야 함 (비어 있으면 `ClientRegistrationRepository` 빈 생성 실패로 부팅 자체가 안 됨)

---

## 자주 쓰는 명령

```bash
docker compose up --build -d   # 전체 스택 빌드+기동
docker compose ps              # 상태 확인
docker compose logs -f app     # 특정 서비스 로그
docker compose down            # 컨테이너만 종료 (데이터 볼륨은 유지됨)
docker compose down -v         # 볼륨까지 삭제 → 다음 up 때 DB가 완전히 새로 초기화됨
```

`docker compose` / `docker-compose` 둘 중 설치된 쪽 사용 — [00-macos-local.md](./00-macos-local.md)

---

## 다음 문서

1. [CI/CD 파이프라인](./03-cicd-pipeline.md)
2. [Nginx 설정](./04-nginx-configuration.md) — 리버스 프록시는 스택 구성 후 적용
