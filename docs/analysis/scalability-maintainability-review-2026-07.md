# Petory 확장성·유지보수성 분석 보고서

> 작성일: 2026-07-05
> 분석 범위: 백엔드 전체(465개 Java 파일, 15개 도메인), 프론트엔드(108개 파일), 인프라(docker-compose, CI/CD), 테스트(51개)
> 분석 방법: 도메인 간 import 의존성 그래프 추출, 클래스 규모 측정, fetch 전략·트랜잭션·락 사용 패턴 검사, 보안 설정·배포 구성 검토

---

## 0. 변경 이력

- **2026-07-05** — "간단×고효과" 항목 착수. 커밋 7개(main push, CI 그린 확인):
  - `build`: micrometer-registry-prometheus 추가 (§3.8)
  - `test`×3: 로컬 잠재 실패 5건 안정화 — 감사필드 어서션 / Hibernate Statistics 런타임 활성화 / 삭제 동시성 소유자 인증 (§3.3 부수 발견)
  - `fix`: OAuth2 SocialUser 중복 생성 레이스 근본 수정 — `socialuser(provider, provider_id)` 유니크 제약 + 재조회 복구 (실제 auth 버그)
  - `test(ci)`+`ci`: CI를 compileJava만 → **전체 테스트 실행**으로 확장 (MySQL/Redis 서비스 + 스키마 스냅샷 + 더미 설정). 첫 실행 그린(2m33s) (§3.3)

---

## 1. 종합 평가

**모놀리스 사이드 프로젝트로서 상위권의 구조 일관성과 검증 문화를 갖췄다.**
다만 두 가지 구조적 한계가 명확하다:

1. **수평 확장 불가** — 앱 인스턴스를 2대 이상으로 늘리면 깨지는 지점이 3곳 존재
2. **도메인 간 순환 의존** — 특히 `user` 도메인이 의존성 허브가 되어 있어 도메인 분리·추출이 불가능한 상태

| 항목                | 평가  | 근거                                                            |
| ------------------- | ----- | --------------------------------------------------------------- |
| 유지보수성 (백엔드) | ★★★★☆ | 4-layer 일관성, 51개 테스트. 순환 의존·600줄 서비스가 감점      |
| 유지보수성 (프론트) | ★★☆☆☆ | 1,000줄 초과 컴포넌트 7개+, 최대 1,994줄                        |
| 확장성 (트래픽)     | ★★☆☆☆ | 단일 인스턴스 전제. SSE/파일/배치 3개 병목                      |
| 확장성 (기능 추가)  | ★★★★☆ | 도메인 패키지 구조 덕에 새 도메인 추가 용이                     |
| 검증 문화           | ★★★★☆ | 동시성 테스트 7개+, 성능 비교 8개. 단 로컬 6건 잠재 실패가 있었고(2026-07-05 수정), 이제 CI 게이트에 연결됨 |
| 보안 구성           | ★★★★☆ | 시크릿 미커밋, prod actuator 최소 노출, WebSocket 인증 인터셉터 |

---

## 2. 강점 (근거 포함)

### 2.1 도메인 패키지 구조의 일관성

- 15개 도메인 전부 `controller / service / entity / repository` 4-layer로 통일 → 코드 위치 예측 가능
- `admin` 도메인은 타 도메인을 **Facade 패턴**으로 감싸서 접근 (`AdminCareAndMeetupFacade` 등, 테스트 존재) — 관리 기능이 도메인 로직을 직접 침범하지 않음

### 2.2 동시성 제어 + 검증

사이드 프로젝트에서 가장 드문 강점. 락 사용에서 끝나지 않고 **테스트로 증명**:

- `CareDealConcurrencyTest`, `PetCoinServiceRaceConditionTest`, `MeetupServiceRaceConditionTest`
- `AuthServiceConcurrencyTest`, `OAuth2ServiceConcurrencyTest`, `UserSanctionServiceConcurrencyTest`, `UsersServiceConcurrencyTest`, `MissingPetBoardConcurrencyTest`
- 전략 분화: 펫코인·에스크로는 비관적 락(`findByIdForUpdate`), 경고 횟수·모임 인원은 DB 원자적 증가 쿼리

### 2.3 성능 의식

- `FetchType.EAGER` 사용 **0건**, JOIN FETCH / `@EntityGraph` 사용 20개 파일
- `*PerformanceTest` 8개 (채팅 읽음 처리, 알림 조회, 모임 서브쿼리, 태그 매칭 등) — "N+1을 잡았다"는 주장에 측정 근거가 붙어 있음
- 통계는 실시간 쿼리 대신 자정 배치 집계 (Daily Summary Pattern)
- 베이스라인 스키마에 인덱스 정의 200여 건, FULLTEXT ngram·공간(spatial) 인덱스 마이그레이션 존재

### 2.4 이벤트 기반 분리의 시작

- `ApplicationEventPublisher` 8개 파일에서 사용 (모임 채팅방 생성, 알림 디스패치, 펫 의도 시그널)
- `MeetupChatRoomRecoveryScheduler` — 이벤트 유실 대비 복구 스케줄러까지 존재

### 2.5 인프라·보안 구성

- docker-compose: healthcheck + `depends_on: condition: service_healthy`로 기동 순서 보장, nginx 리버스 프록시
- CI(컴파일 검증) + CD(GHCR 이미지 빌드/푸시, 경로 필터로 불필요 빌드 방지)
- 시크릿 미커밋 확인: `firebase-service-account.json`, `application.properties`, `application-prod.properties` 모두 git 미포함
- prod actuator 노출 최소화: `health,info`만, health 상세는 ADMIN 롤 한정
- WebSocket: 핸드셰이크 핸들러 + STOMP 채널 인터셉터로 인증 처리 (테스트 존재)
- AOP 기반 로깅(`ServiceLoggingAspect`, `RepositoryLoggingAspect`), 예외 핸들러 12종의 `GlobalExceptionHandler`

---

## 3. 약점 (심각도 순)

### 3.1 [Critical·유지보수] 도메인 간 순환 의존

import 분석으로 확인된 순환:

```
care ↔ payment      care ↔ user       chat ↔ meetup
user ↔ meetup       location ↔ petRecommendation
```

전체 의존 그래프 (→ 는 import 방향):

```
activity → board, care, user
admin    → board, care, file, location, meetup, report, statistics, user  (Facade — 의도된 패턴)
board    → chat, file, notification, petRecommendation, user
care     → file, notification, payment, petRecommendation, user
chat     → care, file, meetup, payment, user
location → petRecommendation, user
meetup   → chat, user
payment  → care, statistics, user
petRecommendation → location, notification
report   → board, care, user
statistics → board, care, meetup, report, user
user     → care, file, location, meetup, report   ← 문제 지점
```

**핵심 문제: `user`가 잎(leaf)이 아니라 허브다.** 모든 도메인이 user에 의존하는 것은 자연스럽지만, user가 역으로 care·meetup·report·location·file을 import하는 순간 그래프가 순환한다. 결과:

- 어떤 도메인도 독립 모듈/서비스로 추출 불가
- 한 도메인 수정이 무관한 도메인의 컴파일·테스트에 파급
- 신규 참여자가 변경 영향 범위를 예측하기 어려움

**개선 방향:** 이미 사용 중인 이벤트 패턴을 확대. 예를 들어 회원 탈퇴 시 user가 care/meetup을 직접 호출하는 대신 `UserDeletedEvent`를 발행하고 각 도메인이 리스너로 수신. 역방향 의존(user→타 도메인)부터 우선 제거.

### 3.2 [Critical·확장성] 수평 확장을 막는 3개 지점

현재 구조는 앱 인스턴스 1대를 전제한다. 2대로 늘리는 순간:

| 지점                                           | 문제                                               | 해법                                                        |
| ---------------------------------------------- | -------------------------------------------------- | ----------------------------------------------------------- |
| SSE + STOMP 인메모리                           | A 서버 접속 유저는 B 서버 발생 알림·채팅을 못 받음 | Redis pub/sub 브리지 또는 외부 STOMP 브로커(RabbitMQ relay) |
| 파일 업로드 로컬 볼륨 (`uploads:/app/uploads`) | 인스턴스마다 파일 저장소가 갈라짐                  | S3 계열 오브젝트 스토리지 이전                              |
| 자정 통계 배치 `@Scheduled`                    | 인스턴스 수만큼 중복 집계                          | ShedLock 등 분산 락, 또는 배치 전용 인스턴스 분리           |

면접 대응: "지금은 단일 인스턴스 트래픽으로 충분하지만, 스케일아웃 시 바꿔야 할 지점 3개를 알고 있다"로 정리하면 한계가 오히려 강점이 된다.

### 3.3 [High·CI] 테스트가 CI에서 실행되지 않음 — ✅ 해결됨 (2026-07-05)

(원래 문제) `ci.yml`은 `./gradlew compileJava`만 수행했다. 동시성·성능 테스트를 만들어 놓고 **CI가 검증하지 않아** 회귀가 조용히 통과될 수 있었다.

**조치:** GitHub Actions `services:`로 MySQL 8.0 + Redis 7 컨테이너를 띄우고, 실제 시크릿이 없는 CI 전용 더미 설정(`.github/ci/application-ci.properties`) + 스키마 스냅샷(`.github/ci-schema.sql`, `ddl-auto=none` 대응)을 로드한 뒤 `./gradlew test`를 실행하도록 확장했다. 첫 CI 실행에서 전체 스위트 그린(2m33s).

**부수 발견(중요):** CI에 붙이려고 로컬 전체 스위트를 처음 돌리자 **185개 중 6개가 실패**하고 있었다 — 최대 강점이라던 테스트가 실제로는 로컬에서도 깨져 있었다. 원인 4종:
- 목 단위 테스트가 JPA Auditing 감사필드(`@CreatedDate`)를 검증 → 어서션 제거
- 성능 테스트가 Hibernate Statistics 비활성 환경에서 `0<0` → 런타임 활성화
- 삭제 동시성 테스트가 SecurityContext 인증 미설정 → 소유자 인증 주입
- **OAuth2 기존계정 연결의 실제 레이스** — `socialuser(provider, provider_id)` 유니크 제약 부재로 동시 연결 시 SocialUser 중복 생성 → 유니크 제약 + 재조회 복구로 근본 수정

즉 "CI에 테스트를 붙인다"가 단순 워크플로 수정이 아니라, **잠재 실패 6건(실제 auth 버그 1건 포함)을 드러내고 고치는** 작업이었다. 이제 그 테스트들이 진짜 회귀 방어선으로 동작한다.

### 3.4 [Medium·유지보수] 비대한 서비스 클래스

| 클래스                      | 줄 수 |
| --------------------------- | ----- |
| `ConversationService`       | 650   |
| `MeetupService`             | 611   |
| `BoardService`              | 597   |
| `MissingPetBoardService`    | 581   |
| `PublicDataLocationService` | 576   |
| `UsersService`              | 529   |

600줄 안팎 서비스가 여럿. 같은 도메인 안에서 조회 전용 `~QueryService` 분리(CQRS-lite)만 해도 트랜잭션 경계(`readOnly`)가 명확해지고 파일 크기가 절반으로 줄어든다.

### 3.5 [Medium·유지보수] 엔티티 절반이 `@Setter`

68개 엔티티 중 34개가 클래스 레벨 `@Setter`. 어디서든 상태 변경이 가능해 불변식(invariant)이 깨지기 쉽다. 의도가 드러나는 도메인 메서드(`approve()`, `cancel()`, `increaseWarningCount()`)로 대체하는 것이 정석. 신규 엔티티부터 적용하고 기존 것은 수정 시점에 점진 전환 권장.

### 3.6 [Medium·프론트] 거대 컴포넌트

| 컴포넌트                 | 줄 수 |
| ------------------------ | ----- |
| `CommunityBoard.js`      | 1,994 |
| `ChatRoom.js`            | 1,624 |
| `UserProfileModal.js`    | 1,358 |
| `CommunityDetailPage.js` | 1,333 |
| `MissingPetBoardForm.js` | 1,315 |

api/hooks/contexts 레이어 분리는 잘 되어 있으나, 페이지 컴포넌트가 상태·핸들러·마크업을 전부 안고 있다. 현재 백엔드보다 프론트가 유지보수성이 더 취약하다. 커스텀 훅으로 상태 로직 추출 + 하위 컴포넌트 분해 권장.

### 3.7 [Low·운영] 스키마 마이그레이션이 수동 관리

`sql/migration/applied/` 폴더로 적용 이력을 수동 관리 중. Flyway/Liquibase 부재로 "어떤 환경에 어떤 스크립트가 적용됐는지"를 사람이 추적해야 한다. 파일 네이밍이 잘 정리되어 있어 Flyway 전환 비용은 낮은 편.

### 3.8 [Low·관측] 메트릭 수집 부재 — ✅ 착수 (2026-07-05)

actuator는 있으나 micrometer/prometheus 미도입 상태였다. `micrometer-registry-prometheus` 의존성을 추가(build.gradle)해 `PrometheusMeterRegistry`가 자동 구성되도록 했다. 엔드포인트 노출(`management.endpoints.web.exposure.include`에 `prometheus`)은 `application*.properties`(gitignore)에서 환경별로 관리하며, 운영은 인증/네트워크로 제한 노출 권장. 이후 대시보드(Grafana) 연동은 별도 과제.

---

## 4. 우선순위 로드맵

| 순위 | 작업                                                         | 효과                                        | 예상 규모            |
| ---- | ------------------------------------------------------------ | ------------------------------------------- | -------------------- |
| ~~1~~ ✅ | ~~CI에 테스트 실행 추가~~ **완료 (2026-07-05)** — 실측은 "소"가 아니라 중(더미설정·스키마 부트스트랩·잠재 실패 6건 수정 동반) | 기존 테스트가 회귀 방어선이 됨 | 중 (완료)            |
| 2    | `user → care/meetup/report` 역방향 의존을 이벤트로 절단      | 순환 제거의 출발점, 도메인 분리 가능성 확보 | 중                   |
| 3    | 파일 저장소 S3 이전 + SSE/STOMP Redis pub/sub                | 수평 확장 경로 확보                         | 중~대                |
| 4    | 프론트 거대 컴포넌트 상위 3개 분해                           | 프론트 유지보수성 개선                      | 중                   |
| 5    | Flyway 도입, ~~micrometer 추가~~ ✅(micrometer 착수 완료)    | 운영 안정성·관측성                          | 소                   |

---

## 5. 면접 활용 포인트

- **강점 서사**: "동시성 제어를 락 종류별로 분화하고(비관적 락 vs 원자적 UPDATE), 각각 레이스 컨디션 테스트로 검증했다" — 테스트 파일명을 근거로 제시 가능
- **한계 서사**: "단일 인스턴스 모놀리스의 확장 한계 3가지(SSE 인메모리, 로컬 파일, 배치 중복)를 알고 있고 각각의 해법도 안다" — 약점을 아는 것이 모르는 것보다 높은 평가
- **개선 서사**: "이벤트 발행을 8곳에 도입해 도메인 결합을 낮추는 중이며, user 도메인 역방향 의존 제거가 다음 단계"
