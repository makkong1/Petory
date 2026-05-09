# CLAUDE.md

## 최상위: 기본 작업 규격

이 저장소에서 에이전트가 코드를 작성·수정·리뷰·리팩터링할 때 적용하는 **행동 규격의 근원**은 이 파일이다. **사용자가 명시한 요청과 충돌하면 사용자 요청이 우선**한다. 펫토리 도메인·기술 스택 요구(DB 인덱스, 트랜잭션, 보안 등)는 아래 각 절과 **함께** 만족해야 한다.

- **영어 원문 미러(Cursor Rules):** `.cursor/rules/karpathy-guidelines.mdc` (`alwaysApply: true`)
- **슬래시 리마인더(Claude Code):** `/karpathy-guidelines` → `.claude/commands/karpathy-guidelines.md`

규격이 서로 비교되면 **`CLAUDE.md`가 우선**한다. 채널 간 매핑과 원문은 **`docs/AGENT_TOOLING.md`** 참고.

### 에이전트 행동 가이드 (`docs/AGENT_TOOLING.md` §2와 동일 철학)

**1. 코딩 전에 생각하기** — 추측으로 진행하지 말 것. 불확실하면 가정을 말하고 질문할 것. 해석이 여러 개면 조용히 한 가지를 고르지 말고 제시할 것. 더 단순한 방법이 있으면 말하고, 필요하면 과잉 설계를 거절할 것.

**2. 단순함 우선** — 문제를 푸는 최소 코드만 쓸 것. 요청받지 않은 기능·추상화·설정 가능성·불가능한 시나리오용 과잉 에러 처리를 넣지 말 것. “시니어가 과하다고 할까?”에 예라면 줄일 것.

**3. 외과적 변경** — 손대야 할 줄만 수정할 것. 인접한 “개선”, 무관한 리포맷·리팩터는 하지 말 것. 기존 스타일에 맞출 것. 무관해 보이는 죽은 코드는 삭제하지 말고 언급만 할 것. **내 변경 때문에** 불필요해진 import·변수·함수만 제거할 것.

**4. 목표 기준 실행** — 작업을 검증 가능한 목표로 바꿀 것(예: 버그 수정 → 재현 테스트 후 통과). 다단계일 때 짧은 계획과 각 단계의 검증 기준을 둘 것. 완료 주장 전에 이 문서의 **빌드·테스트**로 근거를 남길 것(환경 불가 등은 명시).

**워크플로 슬래시**(`/review`, `/test`, `workflow` 등)는 위 규격을 지키는 **순서·체크리스트 레이어**이며, 기본 태도 정의는 여전히 위 네 가지다.

---

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 필수 기술 역량

### 데이터베이스

- **인덱스**: 인덱스 설계 및 최적화 능력 필수
- **트랜잭션**: 트랜잭션 관리 및 동시성 제어 능력 필수
- **락**: 비관적 락, 낙관적 락 등 동시성 제어 기법 이해 및 적용

### 프로그래밍

- **프레임워크**: Spring Boot 등 주요 프레임워크 중급 이상 사용 능력
- **언어**: Java, JavaScript 등 사용 언어에 대한 중급 이상 수준의 이해

### 인프라 및 도구

- **네트워크**: HTTP/HTTPS, REST API, 웹소켓 등 네트워크 프로토콜 이해
- **컨테이너**: Docker, Kubernetes 등 컨테이너 기술 활용 능력
- **Git**: Git을 활용한 버전 관리 및 협업 능력

### 설계 및 아키텍처

- **아키텍처 패턴**: 마이크로서비스, 레이어드 아키텍처 등 주요 아키텍처 패턴 이해
- **디자인 패턴**: GoF 디자인 패턴 및 도메인별 패턴 적용 능력

### 프로젝트 경험

- **사이드 프로젝트**: 실제 사이드 프로젝트 개발 및 운영 경험
- **실운영 경험**: 실제 서비스 운영 경험이 있으면 더욱 우대

## 코드 작성 시 고려사항

1. **성능 최적화**: 인덱스 활용, 쿼리 최적화, N+1 문제 해결
2. **동시성 제어**: 트랜잭션 격리 수준, 락 전략 고려
3. **아키텍처 일관성**: 프로젝트의 아키텍처 패턴 준수
4. **디자인 패턴 적용**: 적절한 디자인 패턴 활용으로 코드 품질 향상
5. **실운영 고려**: 실제 운영 환경을 고려한 에러 처리, 로깅, 모니터링

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

## Skills (커스텀 명령)

`.claude/skills/` 디렉토리에 정의된 전용 Skill 파일:

| Skill | 파일 | 핵심 기능 |
|-------|------|----------|
| **기본 작업 규격** | `CLAUDE.md` 상단 · `.cursor/rules/karpathy-guidelines.mdc` · `/karpathy-guidelines` | 과잉 변경 방지·단순함·외과식 수정·검증 가능 목표 (`docs/AGENT_TOOLING.md`) |
| 코드 리뷰 | `.claude/skills/review.md` | 룰 기반 체크리스트(JPA/트랜잭션/보안) + [문제→원인→개선코드] 형식 + 점수판 |
| 커밋+푸시 | `.claude/skills/commit.md` | 파일 필터링(민감파일 자동제외) → type/scope 자동분류 → 도메인별 커밋 분리 제안 |
| 문서화 | `.claude/skills/docs-sync.md` | 변경 파일 기반 영향 문서 자동 탐지 → 코드 사실 확인 → 문서 현행화 |
| 리팩토링 | `.claude/skills/refactor.md` | 3가지 타입(구조/성능/가독성) 분류 + 측정 기준(쿼리 수, 코드량) 포함 계획 |
| 트러블슈팅 | `.claude/skills/fix.md` | 재현 가능성 체크 → 빠른 해결(Hotfix) + 근본 해결(Proper Fix) 2단계 제시 |
| 테스트 | `.claude/skills/test.md` | 변경 코드 기반 테스트 자동 생성 (정상/예외/경계값 3종 필수, 동시성 테스트 포함) |
| **순서(파이프라인)** | `.claude/skills/workflow.md` | review 이후 fix·refactor 분기 → test → commit → docs (분기·예외 규칙) |
| DB 리뷰 | `.claude/skills/db-review.md` | N+1·인덱스 누락·트랜잭션 범위·동시성 제어 점검 (JPA/Repository 변경 시) |

## 개발 워크플로우 파이프라인

Skill 간 자동 연계 흐름:

```
코드 수정
  │
  ▼
/review  (룰 기반 점검)
  │
  ├─ Critical 있음 → /refactor (수정) 또는 /fix (버그)
  │                      │
  │                      ▼
  │                   /test (검증)
  │                      │
  ├─ Critical 없음 ──────┤
  │                      ▼
  │                   /commit (커밋+푸시)
  │                      │
  │                      ▼
  └──────────────────  /docs (문서 현행화)
```

상세 분기·예외(핫픽스)는 **`.claude/skills/workflow.md`** 참고.

### 워크플로우 규칙
- `/review` → Critical 0개일 때만 "커밋 가능" 판정
- `/refactor` 또는 `/fix` 완료 후 → 반드시 `/test` 제안
- `/test` 전부 통과 후 → `/commit` 제안
- 코드 변경이 있는 커밋 후 → `/docs` 영향 문서 확인 제안
