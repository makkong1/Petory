# Actuator + Spring Boot Admin 모니터링 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Spring Boot Actuator와 Spring Boot Admin UI를 추가해 로컬 개발 환경에서 서버 health, metrics, info를 시각적으로 모니터링한다.

**Architecture:** 동일한 Spring Boot 앱(포트 8080) 안에 Actuator 엔드포인트와 Admin Server를 함께 내장. Admin UI는 `/admin-ui` 컨텍스트 경로로 접근. 앱이 자기 자신을 Admin에 클라이언트로 등록해 메트릭을 표시.

**Tech Stack:** Spring Boot 3.5.7, spring-boot-starter-actuator, spring-boot-admin-starter-server 3.3.3, spring-boot-admin-starter-client 3.3.3

---

## 파일 변경 목록

| 파일 | 작업 |
|------|------|
| `build.gradle` | Actuator, Admin Server, Admin Client 의존성 추가 |
| `backend/main/resources/application.properties` | Actuator 설정, Admin 클라이언트 설정 추가 |
| `backend/main/java/com/linkup/Petory/global/security/SecurityConfig.java` | `/actuator/**`, `/admin-ui/**` permitAll 추가 |
| `backend/main/java/com/linkup/Petory/PetoryApplication.java` | `@EnableAdminServer` 추가 |

---

### Task 1: 의존성 추가

**Files:**
- Modify: `build.gradle`

- [ ] **Step 1: `build.gradle` 에 의존성 3줄 추가**

`dependencies { ... }` 블록 안에 아래 내용을 추가한다. 기존 `implementation 'org.springframework.boot:spring-boot-starter-web'` 줄 바로 아래에 넣으면 된다.

```groovy
implementation 'org.springframework.boot:spring-boot-starter-actuator'
implementation 'de.codecentric:spring-boot-admin-starter-server:3.3.3'
implementation 'de.codecentric:spring-boot-admin-starter-client:3.3.3'
```

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL` (에러 없음)

- [ ] **Step 3: 커밋**

```bash
git add build.gradle
git commit -m "chore(monitoring): Actuator + Spring Boot Admin 의존성 추가"
```

---

### Task 2: Actuator & Admin 설정

**Files:**
- Modify: `backend/main/resources/application.properties`

- [ ] **Step 1: `application.properties` 하단에 설정 추가**

파일 맨 아래에 아래 내용을 붙여넣는다.

```properties
# =========================
# Actuator 모니터링 설정
# =========================
# health, info, metrics 3가지만 노출 (env, loggers 등 민감한 것 제외)
management.endpoints.web.exposure.include=health,info,metrics
# health 엔드포인트에서 DB/Redis 상태 상세 표시
management.endpoint.health.show-details=always
# 앱 이름 (Admin UI에 표시됨)
spring.application.name=Petory
# Admin Client: 자기 자신을 Admin Server에 등록
spring.boot.admin.client.url=http://localhost:8080/admin-ui
# Admin UI 컨텍스트 경로 (루트 '/'를 기존 API와 분리)
spring.boot.admin.context-path=/admin-ui
```

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 커밋**

```bash
git add backend/main/resources/application.properties
git commit -m "chore(monitoring): Actuator 및 Admin 클라이언트 설정 추가"
```

---

### Task 3: SecurityConfig 수정

**Files:**
- Modify: `backend/main/java/com/linkup/Petory/global/security/SecurityConfig.java`

현재 SecurityConfig에서 `/api/**`에 `.authenticated()` 규칙이 있어 `/actuator/**`도 막혀버린다. Admin UI 경로와 Actuator 경로를 인증 없이 열어야 한다.

- [ ] **Step 1: `filterChain` 메서드의 `authorizeHttpRequests` 블록 수정**

아래 두 줄을 기존 `.requestMatchers("/error").permitAll()` 줄 **바로 다음**에 추가한다.

```java
.requestMatchers("/actuator/**").permitAll()   // 모니터링 엔드포인트 (로컬 전용)
.requestMatchers("/admin-ui/**").permitAll()   // Spring Boot Admin UI (로컬 전용)
```

수정 후 해당 블록은 다음 순서여야 한다:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/auth/**").permitAll()
    .requestMatchers("/api/users/register").permitAll()
    .requestMatchers(HttpMethod.GET, "/api/users/id/check").permitAll()
    .requestMatchers(HttpMethod.GET, "/api/users/nickname/check").permitAll()
    .requestMatchers(HttpMethod.GET, "/api/users/email/verify/**").permitAll()
    .requestMatchers(HttpMethod.POST, "/api/users/email/verify/pre-registration").permitAll()
    .requestMatchers(HttpMethod.GET, "/api/users/email/verify/pre-registration/check").permitAll()
    .requestMatchers("/oauth2/**").permitAll()
    .requestMatchers(HttpMethod.GET, "/api/uploads/**").permitAll()
    .requestMatchers("/api/geocoding/**").permitAll()
    .requestMatchers("/error").permitAll()
    .requestMatchers("/actuator/**").permitAll()   // ← 추가
    .requestMatchers("/admin-ui/**").permitAll()   // ← 추가
    .requestMatchers("/ws/**", "/chat/**").permitAll()
    .requestMatchers("/api/master/**").hasRole("MASTER")
    .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "MASTER")
    .requestMatchers("/api/**").authenticated()
    .anyRequest().permitAll()
)
```

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 커밋**

```bash
git add backend/main/java/com/linkup/Petory/global/security/SecurityConfig.java
git commit -m "chore(monitoring): Actuator, Admin UI 경로 Security permitAll 추가"
```

---

### Task 4: @EnableAdminServer 추가

**Files:**
- Modify: `backend/main/java/com/linkup/Petory/PetoryApplication.java`

- [ ] **Step 1: import 추가**

파일 상단 import 목록에 추가:

```java
import de.codecentric.boot.admin.server.config.EnableAdminServer;
```

- [ ] **Step 2: 클래스에 어노테이션 추가**

`@EnableCaching` 줄 바로 아래에 `@EnableAdminServer`를 추가한다:

```java
@SpringBootApplication
@ConfigurationProperties(prefix = "app")
@EnableScheduling
@EnableAsync
@EnableCaching
@EnableAdminServer   // ← 추가
@EnableJpaAuditing
@EnableMethodSecurity(prePostEnabled = true)
public class PetoryApplication {
    // 기존 코드 그대로 유지
}
```

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 커밋**

```bash
git add backend/main/java/com/linkup/Petory/PetoryApplication.java
git commit -m "chore(monitoring): @EnableAdminServer 활성화"
```

---

### Task 5: 통합 검증

**Files:** 없음 (실행 및 확인만)

MySQL과 Redis가 실행 중인 상태에서 진행한다.

- [ ] **Step 1: 서버 실행**

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

서버가 뜨는 동안 로그에서 아래 메시지 확인:
```
Started PetoryApplication in X.XXX seconds
```

- [ ] **Step 2: health 엔드포인트 확인**

새 터미널에서:

```bash
curl -s http://localhost:8080/actuator/health | python3 -m json.tool
```

Expected (DB, Redis 정상 시):
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "redis": { "status": "UP" },
    "diskSpace": { "status": "UP" }
  }
}
```

- [ ] **Step 3: metrics 엔드포인트 확인**

```bash
curl -s http://localhost:8080/actuator/metrics | python3 -m json.tool
```

Expected: `names` 배열에 `jvm.memory.used`, `http.server.requests` 등이 포함된 JSON

- [ ] **Step 4: JVM 메모리 메트릭 상세 확인**

```bash
curl -s "http://localhost:8080/actuator/metrics/jvm.memory.used" | python3 -m json.tool
```

Expected:
```json
{
  "name": "jvm.memory.used",
  "measurements": [{ "statistic": "VALUE", "value": ... }]
}
```

- [ ] **Step 5: Admin UI 브라우저 확인**

브라우저에서 `http://localhost:8080/admin-ui` 접속.

Expected: Spring Boot Admin 대시보드가 열리고, "Petory" 앱이 **green(UP)** 상태로 등록되어 있음.

- [ ] **Step 6: 최종 커밋 (검증 완료 후)**

```bash
git add .
git commit -m "docs(monitoring): 모니터링 설정 완료 검증"
```

---

## 트러블슈팅

| 증상 | 원인 | 해결 |
|------|------|------|
| Admin UI 접속 시 빈 화면 | 앱이 자신을 등록 못 함 | `spring.boot.admin.client.url` 값이 `http://localhost:8080/admin-ui`인지 확인 |
| health → 401 Unauthorized | Security 규칙 순서 문제 | `/actuator/**` permitAll이 `/api/**` authenticated보다 앞에 있는지 확인 |
| Redis status DOWN | Redis 미실행 | `sudo redis-server --daemonize yes` 실행 후 재시도 |
| `@EnableAdminServer` import 에러 | 의존성 미반영 | `./gradlew --refresh-dependencies compileJava` |
