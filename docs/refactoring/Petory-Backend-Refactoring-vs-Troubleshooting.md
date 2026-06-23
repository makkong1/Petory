# Petory 백엔드 — 리팩토링 vs 트러블슈팅

`Petory-Backend-Domain-Analysis.md` 및 Location / Meetup / Payment 도메인 점검 결과를 **목적별로 분리**한 문서입니다.

| 구분 | 의미 |
|------|------|
| **트러블슈팅** | 잘못된 동작·보안 허점·동시성 버그·예측 가능한 장애를 **원인 파악 후 수정**하는 항목 |
| **리팩토링** | 동작은 하지만 유지보수·확장·일관성·운영 측면에서 **구조·정책을 개선**하는 항목 |

---

## 1. 트러블슈팅 (버그 · 보안 · 동시성)

우선 수정을 검토할 때는 아래부터 보면 됩니다.

### 1.1 Location

| 증상 / 위험 | 위치(참고) | 메모 | 상태 |
|-------------|------------|------|------|
| 리뷰 CRUD 후 `LocationService.rating` 갱신이 **read → 계산 → write**라 동시 리뷰 시 평균이 어긋날 수 있음 | `LocationServiceReviewService#updateServiceRating` | DB 집계/락/트리거 등으로 **단일 진실원** 확보 권장 | ✅ 수정 완료 |
| 지오코딩·좌표 파라미터가 비정상일 때 **일관되지 않은 HTTP 상태·메시지** (일부는 catch, 일부는 바인딩 단계) | `GeocodingController`, `NaverMapService` | 입력 검증·전역 예외 매핑으로 **400 + 명확한 본문** 정리 | ✅ 수정 완료 |

#### 수정 상세 — Location

**[Fix 1] Rating Race Condition 제거**
- **원인**: `updateServiceRating()`이 `findAverageRatingByServiceIdx` → `findById` → `setRating` → `save` 순서로 동작. 동시 리뷰 저장 시 두 트랜잭션이 같은 평균을 읽고 덮어쓰는 Lost Update 발생 가능.
- **수정**: `SpringDataJpaLocationServiceRepository`에 `@Modifying updateRatingByAvg` 추가. DB가 AVG를 계산하고 한 번의 UPDATE로 기록 → read-modify-write 패턴 제거.
  ```sql
  UPDATE locationservice SET rating = (
    SELECT COALESCE(AVG(r.rating), 0.0) FROM locationservicereview r
    WHERE r.service_idx = :serviceIdx AND (r.is_deleted IS NULL OR r.is_deleted = 0)
  ) WHERE idx = :serviceIdx
  ```
- **수정 파일**:
  - `domain/location/repository/SpringDataJpaLocationServiceRepository.java` — `updateRatingByAvg()` 추가
  - `domain/location/repository/LocationServiceRepository.java` — 인터페이스에 메서드 선언 추가
  - `domain/location/repository/JpaLocationServiceAdapter.java` — 위임 구현 추가
  - `domain/location/service/LocationServiceReviewService.java` — `updateServiceRating()` 내부를 단일 호출로 교체, 불필요한 `Optional<Double>` 조회 제거

**[Fix 2] Geocoding 에러 응답 일관성**
- **원인 1**: `/api/geocoding/address`에서 변환 실패 시 `{success: false}`를 **HTTP 200**으로 반환. 클라이언트가 HTTP 상태만으로 실패 판단 불가.
- **원인 2**: `/api/geocoding/directions`에서 비숫자 좌표 입력 시 `Double.parseDouble()` → `NumberFormatException` → 상위 catch에서 불명확한 메시지 반환 가능.
- **수정**: 변환 실패 분기를 `ResponseEntity.badRequest()` (HTTP 400)으로 변경. `parseDouble` 4개 호출을 `try-catch (NumberFormatException)`으로 감싸 명확한 400 메시지 반환.
- **수정 파일**: `domain/location/controller/GeocodingController.java`

---

### 1.2 Meetup

| 증상 / 위험 | 위치(참고) | 메모 | 상태 |
|-------------|------------|------|------|
| 참가 취소 시 `currentParticipants -= 1`이 **원자적 UPDATE가 아님** → 동시 취소 시 카운트 불일치 가능 | `MeetupService#cancelMeetupParticipation` (대략 참가 증가와 대칭) | `incrementParticipantsIfAvailable`와 유사한 **조건부 감소** 쿼리 권장 | ✅ 수정 완료 |
| **인증만 되면 누구나** 모임 수정·삭제 가능 (주최자 검증 없음) | `MeetupController#updateMeetup` / `MeetupService#updateMeetup`, 삭제도 동일 패턴 확인 | **주최자 또는 역할 검증** 필수 (보안 이슈) | ✅ 수정 완료 |

#### 수정 상세 — Meetup

**[Fix 3] 참가 취소 원자적 감소**
- **원인**: `cancelMeetupParticipation()`에서 `meetup.setCurrentParticipants(Math.max(0, current - 1))` 사용. 참가 증가(`incrementParticipantsIfAvailable`)는 원자적이었지만 감소는 JPA read-modify-write. 동시 취소 시 카운트 불일치 가능.
- **수정**: `decrementParticipantsIfPositive` 원자적 UPDATE 추가. `> 0` 조건으로 음수 방지.
  ```sql
  UPDATE Meetup m SET m.currentParticipants = m.currentParticipants - 1
  WHERE m.idx = :meetupIdx AND m.currentParticipants > 0
  ```
- **수정 파일**:
  - `domain/meetup/repository/SpringDataJpaMeetupRepository.java` — `decrementParticipantsIfPositive()` 추가
  - `domain/meetup/repository/MeetupRepository.java` — 인터페이스 선언 추가
  - `domain/meetup/repository/JpaMeetupAdapter.java` — 위임 구현 추가
  - `domain/meetup/service/MeetupService.java` — `cancelMeetupParticipation()` 내 감소 로직 교체

**[Fix 4] 모임 수정·삭제 주최자 검증**
- **원인**: `updateMeetup(meetupIdx, meetupDTO)`, `deleteMeetup(meetupIdx)` 둘 다 인증 여부만 확인. 인증된 사용자라면 타인 모임도 수정·삭제 가능.
- **수정**:
  - `MeetupForbiddenException`에 `notOrganizer()` 팩토리 메서드 추가 (HTTP 403).
  - `updateMeetup`, `deleteMeetup` 시그니처에 `String userId` 추가.
  - 서비스에서 `organizer.idx != currentUser.idx` 시 `MeetupForbiddenException.notOrganizer()` throw. ADMIN·MASTER 역할은 허용.
  - 컨트롤러에서 `Authentication` 파라미터 주입 후 `userId` 추출하여 서비스로 전달.
- **수정 파일**:
  - `domain/meetup/exception/MeetupForbiddenException.java` — `notOrganizer()` 추가
  - `domain/meetup/service/MeetupService.java` — `updateMeetup`, `deleteMeetup` 시그니처 및 검증 로직 추가
  - `domain/meetup/controller/MeetupController.java` — `updateMeetup`, `deleteMeetup`에 `Authentication` 파라미터 추가

---

### 1.3 Payment · Care

| 증상 / 위험 | 위치(참고) | 메모 | 상태 |
|-------------|------------|------|------|
| Care 요청 상태와 에스크로 처리가 **여러 진입점·재시도·부분 실패**로 갈라지면 이멱등성·순서 이슈 가능 | `CareRequestService#updateStatus` (에스크로 release/refund 블록) | 단일 트랜잭션 내에서는 롤백으로 맞춰지지만, **비동기/재시도 설계 시** 별도 설계 필요 | 🔵 미수정 (설계 결정 필요) |
| 잔액 부족 시 `IllegalStateException` → API/클라이언트가 **구분하기 어려운 예외 타입** | `PetCoinService#deductCoins` 등 | 전용 예외로 통일하면 트러블슈팅·모니터링에 유리 (동작 버그라기보다 **오동작으로 이어질 수 있는 신호 품질** 문제) | ✅ 수정 완료 |

#### 수정 상세 — Payment

**[Fix 5] InsufficientBalanceException 적용**
- **원인**: `deductCoins()`에서 잔액 부족 시 `new IllegalStateException(String.format(...))` throw. `IllegalStateException`은 HTTP 500으로 매핑될 수 있고, `errorCode` 필드가 없어 클라이언트·로그 집계 시스템이 잔액 부족을 일반 서버 오류와 구분 불가.
- **수정**: 이미 존재하는 `InsufficientBalanceException.of(current, required)` 사용 → HTTP 400, `errorCode: "INSUFFICIENT_BALANCE"` 반환.
- **수정 파일**: `domain/payment/service/PetCoinService.java`

> **미수정 항목 (에스크로 이멱등성)**: `CareRequestService#updateStatus`에서 상태 변경 → 에스크로 릴리즈 순서 이슈는 단일 트랜잭션 내 롤백으로 현재 동작에는 문제없음. 비동기·재시도 아키텍처 도입 시 Saga 패턴 설계가 선행돼야 함.

---

## ⚠️ 주의사항 (수정 전 반드시 확인)

각 Fix를 실제 코드에 반영할 때 놓치기 쉬운 포인트입니다.

### Fix 1 — Rating Race Condition

> **MySQL 서브쿼리 UPDATE 호환성 확인 필요**

`UPDATE ... SET rating = (SELECT AVG(...) FROM ...)` 패턴은 MySQL 버전에 따라  
`"You can't specify target table 'X' for update in FROM clause"` 에러가 발생할 수 있습니다.

- **Native Query** (`nativeQuery = true`)로 작성해야 JPQL 파서 우회 가능
- 실제 동작을 `EXPLAIN` 또는 통합 테스트로 검증 필요
- 불안하면 **서브쿼리를 인라인 뷰로 감싸는** 방식으로 우회 가능:
  ```sql
  UPDATE locationservice SET rating = (
    SELECT avg_rating FROM (
      SELECT COALESCE(AVG(r.rating), 0.0) AS avg_rating
      FROM locationservicereview r
      WHERE r.service_idx = :serviceIdx
        AND (r.is_deleted IS NULL OR r.is_deleted = 0)
    ) t
  ) WHERE idx = :serviceIdx
  ```

---

### Fix 2 — Geocoding 에러 응답

> **`/api/geocoding/address` 실패 분기도 400인지 별도 확인 필요**

`GeocodingController`에서 `/address`와 `/directions`의 실패 처리 구조가 다릅니다.

- `/directions` — `String`으로 받아 `Double.parseDouble()` 직접 호출 → Fix 2에서 catch 추가
- `/address` — `{success: false, message: "..."}` 를 **HTTP 200**으로 내던 부분을 400으로 교체했는지 확인
- `/coordinates` — `@RequestParam double` 타입 바인딩 실패 시 스프링이 자동으로 400을 내지만, **전역 예외 핸들러에서 메시지 형식**을 통일했는지 확인

---

### Fix 3 — 참가 취소 원자적 감소

> **`updated == 0` 반환값 처리 여부 확인**

`incrementParticipantsIfAvailable`은 `updated == 0`일 때 `MeetupConflictException.fullCapacity()`를 던집니다.  
감소 쿼리도 같은 방식으로 반환값을 확인해야 합니다.

- `currentParticipants`가 이미 0인 비정상 상태에서 호출되면 **조용히 실패** (no-op)
- 최소한 `updated == 0` 시 **경고 로그** 추가 권장
- 참가자 삭제(`meetupParticipantsRepository.delete`)와 감소 쿼리의 **실행 순서**가 중요. 삭제 후 감소 순으로 배치해야 외래키·참조 무결성 이슈 방지

---

### Fix 4 — 모임 수정·삭제 주최자 검증

> **채팅방 미정리 이슈는 이 Fix로 해결되지 않음**

권한 검증은 추가됐지만, **삭제 후 채팅방(Conversation)이 여전히 Active 상태로 남는** 문제는 별도입니다.

- `deleteMeetup` 시 해당 모임의 Conversation을 `CLOSED` 처리하거나 시스템 메시지를 남기는 로직이 없으면 참가자가 채팅방을 계속 볼 수 있음
- ADMIN이 삭제하는 경우에도 같은 문제 발생
- 이 부분은 **리팩토링 2.3 항목 "소프트 삭제 시 채팅방 정리"** 와 함께 처리 권장

---

### Fix 5 — InsufficientBalanceException

> **GlobalExceptionHandler 등록 여부 확인 필수**

`InsufficientBalanceException`이 `@RestControllerAdvice`(`GlobalExceptionHandler` 등)에 **명시적으로 매핑**되어 있지 않으면 스프링 기본 처리로 넘어가 여전히 **HTTP 500**이 나올 수 있습니다.

- `@ExceptionHandler(InsufficientBalanceException.class)` → `ResponseEntity(HttpStatus.BAD_REQUEST)` 확인
- `errorCode: "INSUFFICIENT_BALANCE"` 응답 본문 형식이 클라이언트 기대값과 맞는지 확인
- `deductCoins` 외에 같은 패턴으로 `IllegalStateException`을 쓰는 다른 메서드가 있다면 함께 교체 권장

---

### 공통 주의사항

| 체크 항목 | 이유 |
|-----------|------|
| 수정된 Repository 메서드에 `@Transactional` 또는 `@Modifying` 누락 여부 | `@Modifying` 없으면 UPDATE 쿼리가 SELECT처럼 실행되어 예외 발생 |
| 4-Layer 구조 준수 (SpringDataJpa → Adapter → Repository 인터페이스) | Fix 1·3이 해당 레이어를 모두 거쳐야 아키텍처 일관성 유지 |
| 관련 테스트(`@SpringBootTest` 또는 `@DataJpaTest`) 실행 확인 | Race Condition 수정은 단위 테스트보다 **동시성 통합 테스트**로 검증 |

---

## 2. 리팩토링 (구조 · 정책 · 기술 부채)

“고장”이라기보다 **의도적으로 나중에 할 일** 또는 **품질·운영 개선**에 가깝습니다.

### 2.1 공통 (여러 도메인)

| 항목 | 설명 |
|------|------|
| 리스트 API **페이지네이션** | 전량 조회는 데이터 증가 시 응답 크기·OOM 리스크. `Pageable` / 커서 기반 등 도입 |
| **`@Valid` / `@Validated`** | DTO·쿼리 파라미터에 Bean Validation으로 **조기 실패·일관 메시지** |
| **타임존** | `LocalDateTime.now()` 서버 로컬 의존 → `ZoneId`·UTC 저장·API 계약 명시 등 정책 결정 |
| 자료구조/알고리즘 개선 후보 검증 | 도메인별 후보를 문제로 단정하지 않고 재현 테스트·EXPLAIN·성능 비교로 판정: [validation-matrix.md](./ds-algorithm/validation-matrix.md) |

### 2.2 Location

| 항목 | 설명 |
|------|------|
| 카테고리 필터를 **DB WHERE**로 | 메모리 필터링이면 불필요한 로드·스케일 한계 |
| `LocationServiceReviewDTO` **rating 범위** | 1~5 등 스키마에 맞는 `@Min`/`@Max` |
| `LocationServiceConverter#cleanZipCode` | `"123.45"` → `"12345"` 등 **점 제거 규칙**이 의도와 맞는지 재검토 (엑셀 float vs 잘못된 입력) |
| Location과 Care/Meetup 엔티티 **연결 없음** | 제품 의도(카탈로그만)인지, 추후 추천/연동이 필요한지에 따라 모델링 |

### 2.3 Meetup

| 항목 | 설명 |
|------|------|
| 모임 생성 외 **DTO 검증** (`@Valid`, 좌표 범위 등) | 빈 값·범위 오류를 서비스 진입 전에 차단 |
| 채팅방 생성 실패 **재시도** | `MeetupChatRoomEventListener` 등 TODO 성격 |
| 모임 소프트 삭제 시 **채팅방 정리** | 아카이브·시스템 메시지 등 정책 |
| `RECRUITING` → `CLOSED` / 날짜 경과 → `COMPLETED` 등 **상태 자동 전환** | 스케줄러 또는 도메인 서비스 |

### 2.4 Payment

| 항목 | 설명 |
|------|------|
| 코인 직접 충전 **rate limiting** | PG/운영 연동 시 악용·과금 스팸 방지 |
| 장기 미종료 케어에 대한 **에스크로 HOLD 자동 환불** | 스케줄러·만료 정책 |
| 에스크로 서비스 **권한 모델** (`@PreAuthorize` 등) | Care만 호출한다는 가정을 코드로도 명시할지 |
| **부분 환불/분할 결제** | 현재 전액 일괄만 지원이면 제품 제약으로 문서화 |
| **`DISPUTED` 등 분쟁 상태** | 에스크로 동결·운영 개입 워크플로 |

---

### 1.4 Statistics

| 증상 / 위험 | 위치(참고) | 메모 | 상태 |
|-------------|------------|------|------|
| `completedCares` 집계가 **케어 예정일(`date`)** 기준 — 실제 완료 날짜와 무관한 통계 | `StatisticsScheduler`, `StatisticsService` | `completedAt` 필드 추가 후 해당 기준으로 집계 | ✅ 수정 완료 |
| 스케줄러 예외 발생 시 **서비스 전체에 전파** 가능 | `StatisticsScheduler#aggregateDailyStatistics` | try-catch로 격리, 수동 복구 안내 로그 추가 | ✅ 수정 완료 |
| 오늘 통계 조회 시 `calculateTodayStatistics()` **매번 DB 풀 쿼리** (관리자 대시보드 폴링 시 N회 실행) | `StatisticsService#calculateTodayStatistics` | `@Cacheable(todayStats, 1분)` + self-proxy 패턴 적용 | ✅ 수정 완료 |
| `startDate > endDate` 입력 시 **빈 리스트 또는 오작동** (쿼리가 그냥 실행됨) | `StatisticsService#getDailyStatistics` | 입력 검증 추가, `IllegalArgumentException` throw | ✅ 수정 완료 |
| PUT `/scheduler/time` 이 **HTTP 200 + 성공 메시지** 반환 — 실제로는 아무것도 변경되지 않음 | `AdminStatisticsController#setSchedulerTime` | HTTP 501 Not Implemented + 수동 적용 안내로 교체 | ✅ 수정 완료 |

#### 수정 상세 — Statistics

**[Fix 6] completedAt 필드 추가 및 통계 기준 교체**
- **원인**: `StatisticsScheduler`와 `StatisticsService` 모두 `careRequestRepository.countByDateBetweenAndStatus()`를 사용. `date` 필드는 케어 *예정일*이라 "오늘 완료된 케어"가 아닌 "오늘 예정된 케어 중 완료된 것"을 집계하여 날짜가 다른 날 완료된 케어 누락.
- **수정**:
  - `CareRequest` 엔티티에 `@Column(name = "completed_at") LocalDateTime completedAt` 필드 추가.
  - `CareRequestService#updateStatus()`에서 상태가 `COMPLETED`로 전환될 때 `completedAt = LocalDateTime.now()` 설정.
  - `SpringDataJpaCareRequestRepository`에 `countByCompletedAtBetween` 쿼리 메서드 추가.
  - 기존 `countByDateBetweenAndStatus`를 `@Deprecated` 처리.
- **수정 파일**:
  - `domain/care/entity/CareRequest.java` — `completedAt` 필드 추가
  - `domain/care/service/CareRequestService.java` — COMPLETED 전환 시 `completedAt` 설정
  - `domain/care/repository/SpringDataJpaCareRequestRepository.java` — `countByCompletedAtBetween` 추가, 기존 `countByDateBetweenAndStatus` deprecated 처리
  - `domain/care/repository/CareRequestRepository.java` — 인터페이스에 `countByCompletedAtBetween` 선언, 기존 `@Deprecated` 추가
  - `domain/care/repository/JpaCareRequestAdapter.java` — 위임 구현 추가
  - `domain/statistics/service/StatisticsScheduler.java` — `countByCompletedAtBetween` 사용으로 교체
  - `domain/statistics/service/StatisticsService.java` — `countByCompletedAtBetween` 사용으로 교체

**[Fix 7] 스케줄러 예외 격리**
- **원인**: `aggregateDailyStatistics()`가 예외를 전파하면 Spring `@Scheduled` 실행 스레드가 종료되거나 로그에 스택 트레이스만 남고, 관리자가 수동 복구 방법을 알 수 없음.
- **수정**: try-catch로 예외를 잡아 `log.error()`로 날짜·원인·수동 복구 커맨드(`POST /api/admin/statistics/init?days=1`)를 로그에 남기고 스케줄러는 계속 실행.
- **수정 파일**: `domain/statistics/service/StatisticsScheduler.java`

**[Fix 8] 오늘 통계 캐싱 (self-proxy 패턴)**
- **원인**: `StatisticsService#calculateTodayStatistics()`가 `private`이고 self-call로 호출되어 Spring AOP 프록시가 적용되지 않음. `@Cacheable`을 붙여도 캐싱이 동작하지 않아 관리자 대시보드 폴링마다 8개 DB 쿼리 실행.
- **수정**:
  - `ApplicationContext` 주입 후 `getThis()` 헬퍼로 프록시를 통해 자기 자신 호출.
  - `calculateTodayStatistics()`를 `public`으로 변경하고 `@Cacheable(value = "todayStats", key = "'today'")` 추가.
  - `RedisConfig`에 `todayStats` 캐시 설정 1분 TTL 추가.
- **수정 파일**:
  - `domain/statistics/service/StatisticsService.java` — self-proxy 패턴 적용, `@Cacheable` 추가
  - `global/security/RedisConfig.java` — `todayStats` 1분 TTL 캐시 설정 추가

**[Fix 9] 날짜 범위 입력 검증**
- **원인**: `getDailyStatistics(startDate, endDate)`에서 `startDate > endDate`이면 DB 쿼리가 빈 결과를 반환하거나 예기치 않은 동작. 호출자가 오입력임을 알 수 없음.
- **수정**: 메서드 진입부에서 `startDate.isAfter(endDate)` 검사 후 `IllegalArgumentException` throw.
- **수정 파일**: `domain/statistics/service/StatisticsService.java`

**[Fix 10] PUT `/scheduler/time` → HTTP 501**
- **원인**: `AdminStatisticsController#setSchedulerTime()`이 `@Scheduled` cron은 컴파일 타임에 결정되므로 런타임 변경이 불가능함에도 HTTP 200 + "설정되었습니다." 메시지를 반환. 실제로는 아무것도 변경되지 않아 운영자 혼동 유발.
- **수정**: `ResponseEntity.status(501 NOT_IMPLEMENTED)`로 교체. 응답 본문에 수동 적용 방법(`application.properties` + 재시작) 안내 포함.
- **수정 파일**: `domain/admin/controller/AdminStatisticsController.java`

---

### 1.4 Statistics 주의사항

> **`completedAt` 컬럼 마이그레이션 필요**

`CareRequest` 엔티티에 `completed_at` 컬럼이 추가됩니다. 기존 DB에 이 컬럼이 없으면 서버 시작 시 `@Column` 매핑 오류 또는 `DDL-auto: validate` 모드에서 예외 발생.

- `ddl-auto: update` / `create` 환경에서는 자동 추가되지만, 프로덕션에서는 **수동 마이그레이션** 필요:
  ```sql
  ALTER TABLE care_request ADD COLUMN completed_at DATETIME NULL;
  ```
- 기존 `COMPLETED` 상태 데이터는 `completed_at = NULL`이 됨 → 과거 통계 backfill 시 해당 레코드가 집계에서 제외됨. 이전 데이터 복구가 필요하면 `COMPLETED` 전환 시각을 추정해 일괄 UPDATE 필요.

> **self-proxy 패턴 주의사항**

`StatisticsService.getThis()`는 `ApplicationContext.getBean()`으로 프록시 빈을 가져옵니다. Spring이 완전히 초기화된 후에만 안전하게 동작하며, 순환 빈 참조 문제는 없습니다. 다만 이 패턴은 유지보수성이 낮으므로, 향후 `calculateTodayStatistics()`를 별도 `@Service`(예: `TodayStatisticsQueryService`)로 분리하는 리팩토링 권장.

---

## 3. 이 문서를 쓰는 방법

1. **장애·재현 가능한 잘못된 결과** → 섹션 1(트러블슈팅)부터 이슈 트래킹.
2. **스프린트 여유·기술 부채 정리** → 섹션 2(리팩토링) 백로그에 넣고 우선순위만 별도로 매김.
3. 한 항목이 **양쪽에 걸리는 경우** (예: 우편번호 파싱) — 제품적으로 “버그”면 1, “규칙 명확화”면 2에 두고, PR에서는 한 번에 하나의 목적으로만 쓰기.

---

*작성 기준: Location / Meetup / Payment 도메인 코드 리뷰 및 `Petory-Backend-Domain-Analysis.md` 구조.*
