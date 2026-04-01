# Docker · Redis · 향후 배포

## 📋 이 문서의 역할

- **지금 실제로 쓰는 것**: 로컬에서 Redis를 Docker로 띄우고 Spring Boot(`spring.redis.*`)와 연결하는 방법
- **아직 레포에 없는 것**: `Dockerfile`, `docker-compose.yml` 전체 스택 — **추가 시** 아래 [향후 계획](#향후-계획-dockerfile--compose--cicd)과 [CI/CD](./03-cicd-pipeline.md)를 맞추면 됨

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
| `docker/Dockerfile.*`, `docker-compose*.yml` | **미추가** (문서만으로 설계해 두었던 내용은 정리·축소함)             |
| 로컬 Redis                                   | **`docker run`으로 수동 실행** (아래)                                |
| Spring Boot ↔ Redis                          | `application.properties`의 **`spring.redis.*`** + `RedisConfig.java` |

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

로컬에서는 보통 **호스트에 설치한 MySQL** 또는 별도 Docker 컨테이너를 씁니다.  
전체 스택을 나중에 Compose로 묶을 때는 서비스 이름(예: `mysql`)과 JDBC URL(`jdbc:mysql://mysql:3306/...`)을 맞추면 됩니다.

---

## 향후 계획: Dockerfile · Compose · CI/CD

1. **Dockerfile (백엔드)**

   - 레포 루트에 `build.gradle`, `settings.gradle`, `gradle/`, `backend/` 구조에 맞게 복사 후 `./gradlew bootJar` 등으로 JAR 생성
   - 런타임은 JDK 17 이미지 등
   - 이 레포는 소스가 `backend/main/java`에 있으므로 예전 문서의 `COPY backend/` 만으로는 부족할 수 있음 → **루트 기준 Gradle 빌드**로 맞출 것

2. **docker-compose**

   - `mysql`, `redis`, `backend`(빌드한 이미지), 필요 시 `frontend`·`nginx`
   - 컨테이너 간 통신 시 DB/Redis 호스트명은 `localhost`가 아니라 **서비스 이름**

3. **GitHub Actions**

   - 테스트·빌드 후 이미지 푸시, 서버에서 `docker compose pull && up` 등 — 상세는 [03-cicd-pipeline.md](./03-cicd-pipeline.md)
   - **이미지·Compose 파일이 레포에 생긴 뒤** 워크플로의 빌드·배포 단계를 연결하는 것이 자연스러움

4. **환경 변수**
   - 배포 시 비밀번호·JWT 등은 GitHub Secrets + 서버 `.env` 등으로 분리 — [05-environment-variables.md](./05-environment-variables.md)

---

## 참고로 남겨 두는 명령 (Compose 추가 후)

```bash
docker compose up -d
docker compose logs -f
docker compose down
docker compose down -v   # 볼륨까지 삭제 시 데이터 초기화 주의
```

`docker compose` / `docker-compose` 둘 중 설치된 쪽 사용 — [00-macos-local.md](./00-macos-local.md)

---

## 다음 문서

1. [CI/CD 파이프라인](./03-cicd-pipeline.md)
2. [Nginx 설정](./04-nginx-configuration.md) — 리버스 프록시는 스택 구성 후 적용
