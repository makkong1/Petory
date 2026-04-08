# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

반려동물 케어 & 커뮤니티 통합 플랫폼. Spring Boot 3.5.7 (Java 17) 백엔드 + React 19 프론트엔드.

## 필수 서비스

| 서비스     | 기본값                          | 시작 명령                           |
| ---------- | ------------------------------- | ----------------------------------- |
| MySQL 8.0+ | `localhost:3306`, DB명 `petory` | `sudo mysqld --user=mysql &`        |
| Redis      | `localhost:6379`                | `sudo redis-server --daemonize yes` |

## 빌드 & 실행

```bash
# 백엔드 실행 (루트에서, 포트 8080)
./gradlew bootRun

# 백엔드 컴파일만
./gradlew compileJava

# 백엔드 테스트 (MySQL + Redis 실행 상태 필요)
./gradlew test

# 프론트엔드 실행 (frontend/, 포트 3000)
npm start

# 프론트엔드 빌드 / 테스트
npm run build
npm test
```

프론트엔드는 `package.json`의 `"proxy"` 설정으로 백엔드(8080)에 자동 프록시.

## 설정 (application.properties)

`backend/main/resources/application.properties`는 gitignore 처리됨. 직접 생성 필요:

- `spring.datasource.*` — DB 연결
- `jwt.secret`, `jwt.expiration` — JWT 설정
- OAuth2 클라이언트 등록 (더미값이라도 필수 — 없으면 `ClientRegistrationRepository` 빈 생성 실패)
- Redis는 레거시 속성명 사용: `spring.redis.host` / `spring.redis.port` (`spring.data.redis.*` 아님)
- `spring.profiles.active=dev` 설정 시 이메일 인증 스킵

## 소스 구조

```
backend/main/java/com/linkup/Petory/
  domain/        # 도메인별 패키지 (board, care, chat, location, meetup,
                 #   notification, payment, report, statistics, user, file)
  filter/        # JwtAuthenticationFilter
  global/        # SecurityConfig, GlobalExceptionHandler, 공통 응답 DTO
  util/          # JwtUtil 등
backend/main/resources/
frontend/src/
  components/    # React 컴포넌트
  api/           # Axios API 모듈
  contexts/      # Auth, Theme Context
```

각 도메인 패키지는 `controller / service / entity / repository` 4-layer 구조.

## 아키텍처 핵심 패턴

- **인증**: JWT Access Token (15분) + Refresh Token (1일, DB 저장). `JwtAuthenticationFilter` → `SecurityConfig`.
- **권한**: `@PreAuthorize` 메서드 레벨 제어. Role 계층: USER < SERVICE_PROVIDER < ADMIN < MASTER.
- **Redis 용도 3가지**: 알림 캐시 (최신 50개, TTL 24h) / 게시글 상세 캐시 (`@Cacheable`) / 이메일 인증 임시 저장 (TTL 24h).
- **통계**: 실시간 쿼리 대신 매일 자정 배치로 `DailyStatistics` 테이블에 집계 (Daily Summary Pattern).
- **동시성 제어**: 펫코인·에스크로는 비관적 락(`findByIdForUpdate`), 경고 횟수·모임 인원은 DB 레벨 원자적 증가 쿼리.
- **채팅**: WebSocket(STOMP) 기반. 케어 요청 생성 시 1:1 채팅방, 모임 생성 시 그룹 채팅방 자동 생성.
- **알림**: SSE 실시간 푸시 + Redis/MySQL 이중 저장 후 병합(중복 제거).

## 주의사항 (Gotchas)

- Gradle toolchain이 시스템 Java가 21이어도 JDK 17을 자동 다운로드함 — 정상 동작.
- `GET /api/boards`에 `@PreAuthorize("permitAll()")` 있어도 `SecurityConfig`의 `/api/**` catch-all 때문에 실제로는 인증 필요.
- 회원가입 payload에 `"role":"USER"` 필드 필요.
- OAuth2 소셜 로그인은 실제 credentials 없으면 동작 안 함 — 로컬 인증은 정상 동작.
