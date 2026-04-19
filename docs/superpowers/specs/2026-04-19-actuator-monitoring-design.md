# Spring Boot Actuator + Admin 모니터링 설계

**날짜:** 2026-04-19  
**범위:** 로컬 개발 환경 모니터링 (health, metrics, info)  
**대상:** Petory 백엔드 (Spring Boot 3.5.7, Java 17)

---

## 목표

- 서버 생존 여부(health), JVM/HTTP 메트릭(metrics), 앱 정보(info)를 확인할 수 있는 모니터링 엔드포인트 추가
- Spring Boot Admin 웹 UI로 시각적으로 확인 가능하게 구성
- 로컬 개발 전용 (인증 없이 접근, 외부 배포 시 별도 보안 설정 필요)

---

## 아키텍처

```
[Petory 백엔드 :8080]
    └─ Spring Boot Actuator 활성화
        ├─ /actuator/health   → DB, Redis 연결 상태
        ├─ /actuator/metrics  → JVM 메모리, HTTP 요청 수 등
        └─ /actuator/info     → 앱 버전 정보

[Spring Boot Admin Server :9090]
    └─ Actuator 엔드포인트 수집 → 웹 UI 표시
```

- Admin Server는 동일 프로젝트 내 `admin` Spring 프로파일로 실행 (포트 9090)
- Actuator 클라이언트가 Admin Server에 자동 등록

---

## 의존성

`build.gradle`에 추가:

```groovy
implementation 'org.springframework.boot:spring-boot-starter-actuator'
implementation 'de.codecentric:spring-boot-admin-starter-server:3.3.3'
implementation 'de.codecentric:spring-boot-admin-starter-client:3.3.3'
```

---

## 설정 파일

### application.properties (기존 파일에 추가)

```properties
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=always
spring.boot.admin.client.url=http://localhost:9090
spring.application.name=Petory
```

### application-admin.properties (신규)

```properties
server.port=9090
spring.application.name=Petory-Admin
```

---

## SecurityConfig 변경

`/actuator/**` 경로를 permitAll 처리:

```java
.requestMatchers("/actuator/**").permitAll()
```

기존 `/api/**` authenticated 규칙보다 앞에 위치해야 함.

---

## Admin Server 진입점

`AdminServerApplication.java` (신규) — `admin` 프로파일 활성화 시에만 동작:

```java
@SpringBootApplication
@EnableAdminServer
@Profile("admin")
public class AdminServerApplication { ... }
```

---

## 실행 방법

```bash
# 백엔드 (기존)
./gradlew bootRun

# Admin Server (별도 터미널)
./gradlew bootRun --args='--spring.profiles.active=admin'
```

브라우저에서 `http://localhost:9090` 접속 → Petory 앱 등록 확인

---

## 검증 커맨드

```bash
# health 확인
curl http://localhost:8080/actuator/health

# metrics 목록
curl http://localhost:8080/actuator/metrics

# JVM 메모리 사용량
curl http://localhost:8080/actuator/metrics/jvm.memory.used
```

---

## 제외 범위

- Prometheus / Grafana 연동 (2단계)
- 외부 배포 환경 보안 설정
- 알림(Alert) 설정
