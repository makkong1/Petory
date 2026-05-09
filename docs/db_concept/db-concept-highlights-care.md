# DB 개념 어필 포인트 — Care 도메인

> 코드베이스 실측 데이터 기준 (실제 파일 확인)

---

## 1. N+1 문제 — 3단계 점진적 해결

### 어필 포인트

- **문제 발견**: `GET /api/care-requests` 호출 시 CareRequest 1004개 기준으로 약 2,400개 쿼리 발생
  - CareApplication: N번 (각 CareRequest마다 LAZY 로드)
  - Pet File: N번 (각 Pet마다 `attachmentFileService.getAttachments()` 호출)
  - PetVaccination: N번 (Pet 엔티티의 `vaccinations` LAZY 로드)

- **1단계**: `LEFT JOIN FETCH cr.applications` + `DISTINCT` 추가
  - 대상 쿼리: `findAllActiveRequests`, `findByStatusAndIsDeletedFalse`, `findByUserAndIsDeletedFalseOrderByCreatedAtDesc`
  - 효과: 쿼리 수 ~2400 → ~1400 (42% 감소), 백엔드 실행 시간 1084ms → 306ms (72% 감소)

- **2단계**: `PetConverter.toDTOList()` 배치 조회 구현
  - Pet idx 목록 수집 후 `getAttachmentsBatch(FileTargetType.PET, petIndices)`로 File 1회 일괄 조회
  - 효과: 쿼리 수 ~1400 → ~700 (50% 추가 감소), 백엔드 실행 시간 306ms → 208ms (32% 감소)

- **3단계**: `@BatchSize(size = 50)` 적용
  - `Pet` 엔티티 `vaccinations`에 적용해 50개 단위 배치 조회
  - 이후 `CareRequest.applications`에도 동일 패턴 적용 (페이징 경로 N+1 해결)

- **최종 실측치** (문서 `care-request-n-plus-one-analysis.md` 기준):
  - 쿼리 수: 2,400개 → **4-5개** (99.8% 감소)
  - 백엔드 실행 시간: 1,084ms → **66ms** (94% 감소)
  - 메모리 사용: 21MB → **6MB** (71% 감소)

### 코드 스니펫

```java
// CareRequest 엔티티 — @BatchSize로 페이징 경로 N+1 방지
@OneToMany(mappedBy = "careRequest", cascade = CascadeType.ALL)
@BatchSize(size = 50)
private List<CareApplication> applications;
```

```java
// SpringDataJpaCareRequestRepository — 비페이징 경로 FETCH JOIN
@Query("SELECT DISTINCT cr FROM CareRequest cr JOIN FETCH cr.user u " +
       "LEFT JOIN FETCH cr.pet LEFT JOIN FETCH cr.applications " +
       "WHERE cr.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' " +
       "ORDER BY cr.createdAt DESC")
List<CareRequest> findAllActiveRequests();
```

### 말할 내용
> "펫케어 요청 목록 조회 시 N+1 문제를 단계적으로 해결한 경험이 있습니다. 1004개 데이터 기준 약 2,400개의 쿼리가 발생했는데, Fetch Join·배치 조회 패턴·@BatchSize를 3단계로 적용해 최종 4-5개로 줄였습니다. 실행 시간은 1,084ms에서 66ms로 94%, 메모리는 21MB에서 6MB로 71% 개선됐습니다. 특히 Hibernate의 중첩 컬렉션 Fetch Join 제한 때문에 PetVaccination에는 @BatchSize를 선택했고, 페이징 쿼리에 JOIN FETCH를 추가하면 HHH90003004 경고가 발생하는 문제도 동일 @BatchSize로 해결했습니다."

---

## 2. 페이징 쿼리 N+1 — @BatchSize 적용 경로

### 어필 포인트

- **이중 N+1 경로**: 비페이징(`findAllActiveRequests`)과 페이징(`findAllActiveRequestsWithPaging`) 쿼리가 분리된 구조라 페이징 경로에는 `LEFT JOIN FETCH cr.applications`가 누락됨
- **발생 패턴**: 메인 쿼리 1번 + count 쿼리 1번 + careapplication N번 → page size 20이면 22번, 100이면 102번
- **해결**: `CareRequest.applications`에 `@BatchSize(size = 50)` 추가
  - 결과: careapplication 쿼리 N번 → IN 절 배치 1번 (총 3번 DB 왕복)

```
-- 해결 전 (page size=20)
SELECT carerequest ... LIMIT 20       -- 1번
SELECT COUNT(carerequest) ...         -- 1번
SELECT careapplication WHERE care_request_idx=? -- 20번 반복
총 22번

-- 해결 후
SELECT carerequest ... LIMIT 20       -- 1번
SELECT COUNT(carerequest) ...         -- 1번
SELECT careapplication WHERE care_request_idx IN (?,?,...) -- 1번 (배치)
총 3번
```

### 말할 내용
> "페이징 API에 별도 쿼리 경로가 있었는데, 비페이징 쪽에만 Fetch Join을 적용하고 페이징 쪽은 놓쳐서 동일한 N+1이 재발했습니다. Page + OneToMany Fetch Join 조합 시 Hibernate가 메모리에서 페이징을 처리하는 문제가 있어서, 페이징 쿼리는 그대로 두고 엔티티 레벨에서 @BatchSize를 적용하는 방법을 선택했습니다."

---

## 3. 동시성 제어 — 비관적 락으로 Race Condition 해결

### 어필 포인트

- **문제**: 채팅방에서 두 사용자가 거의 동시에 '거래 확정' 버튼을 누를 때 Stuck State 발생
- **원인**: MySQL 기본 격리 수준(REPEATABLE READ)에서 상대방의 미커밋 변경사항을 읽지 못해 `allConfirmed = false`로 평가되어 후속 처리(CareRequest 상태 변경)가 양쪽 모두 실행되지 않음
- **해결**: `Conversation`에 `PESSIMISTIC_WRITE` 락 적용 → 한 트랜잭션이 완료될 때까지 다른 트랜잭션 대기 → 대기 해제 후 커밋된 최신 데이터 읽어 정상 처리
- **추가 처리**: `CareApplication` 신규 생성 시 `TransientObjectException` 방지를 위해 `careRequestRepository.getReferenceById()` + `saveAndFlush()` 적용
- **검증**: `CareDealConcurrencyTest` 테스트로 동시 호출 시 CareRequest 상태가 OPEN → IN_PROGRESS로 정상 전이함을 확인 (테스트 통과)

```java
// ConversationRepository — 비관적 락 쿼리
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT c FROM Conversation c WHERE c.idx = :idx")
Optional<Conversation> findByIdWithLock(@Param("idx") Long idx);
```

```java
// ConversationService — saveAndFlush로 즉시 반영
CareRequest careRequestRef = careRequestRepository.getReferenceById(relatedIdx);
CareApplication newApplication = CareApplication.builder()
        .careRequest(careRequestRef)
        .provider(...)
        .status(CareApplicationStatus.ACCEPTED)
        .build();
careApplicationRepository.saveAndFlush(newApplication);
```

### 말할 내용
> "채팅 거래 확정 시 Race Condition을 발견했습니다. REPEATABLE READ에서 두 트랜잭션이 동시에 실행되면 상대방 변경이 보이지 않아 후속 로직이 양쪽 모두 건너뛰는 Stuck State가 발생했습니다. 해결책은 부모 엔티티인 Conversation에 PESSIMISTIC_WRITE 락을 걸어 순차 처리를 강제하는 것이었습니다. 비관적 락 적용 후 CareApplication 신규 생성에서 TransientObjectException도 발생했는데, getReferenceById로 Proxy 참조를 쓰고 saveAndFlush로 즉시 플러시해서 해결했습니다. 동시 호출 테스트로 최종 검증했습니다."

---

## 4. 에스크로 기반 결제 — 비관적 락 + 상태 전이

### 어필 포인트

- **패턴**: 펫코인은 거래 확정 시 에스크로(HOLD)로 임시 보관 → COMPLETED 전환 시 제공자에게 지급, CANCELLED 시 요청자에게 환불
- **비관적 락 적용**: `petCoinEscrowService.releaseToProvider()` / `refundToRequester()` 내부에서 에스크로 행에 락 적용
- **주의 포인트**: 서비스에서 `findByCareRequestForUpdate`(비관적 락)를 호출한 후 `releaseToProvider`에서 재차 락을 잡으면 같은 트랜잭션 내에서 rollback-only 타이밍 충돌로 `UnexpectedRollbackException` 발생 → 이중 락 제거, 서비스에서는 `findByCareRequest`(락 없음)으로 조회
- **상태 정합성**: `completedAt` 컬럼을 COMPLETED 전환 시 기록하여 통계 집계(`countByCompletedAtBetween`)에 활용 (케어 예정일 `date` 기준 집계는 오류)

```java
// CareRequestService.updateStatus() — COMPLETED 전환 시 에스크로 지급
if (oldStatus != CareRequestStatus.COMPLETED && newStatus == CareRequestStatus.COMPLETED) {
    request.setCompletedAt(java.time.LocalDateTime.now());
    PetCoinEscrow escrow = petCoinEscrowService.findByCareRequest(request); // 락 없이 조회
    if (escrow != null && escrow.getStatus() == EscrowStatus.HOLD) {
        petCoinEscrowService.releaseToProvider(escrow); // 내부에서 비관적 락
    }
}
```

### 말할 내용
> "펫코인 결제는 에스크로 패턴으로 구현했습니다. 거래 확정 시 요청자 잔액에서 차감해 에스크로에 HOLD 상태로 보관하고, 서비스 완료 시 제공자에게 지급, 취소 시 환불합니다. 처음에는 서비스 레이어에서 비관적 락으로 에스크로를 조회한 뒤 releaseToProvider를 호출했는데, releaseToProvider 내부에서 동일 행에 재차 락을 시도하면서 같은 트랜잭션에서 UnexpectedRollbackException이 발생했습니다. 이중 락을 제거하고 서비스에서는 일반 조회, 실제 변경 메서드 내부에서만 락을 잡도록 수정했습니다."

---

## 5. 인덱스 설계

### 어필 포인트

실제 DB SHOW INDEX 결과 기준 인덱스:

| 테이블 | 인덱스명 | 컬럼 | 종류 | 용도 |
|--------|----------|------|------|------|
| `carerequest` | `user_idx` | `user_idx` | BTREE | 사용자별 요청 조회 (`getMyCareRequests`) |
| `carerequest` | `fk_carerequest_pet` | `pet_idx` | BTREE | 외래키 JOIN |
| `careapplication` | `care_request_idx` | `care_request_idx` | BTREE | 요청별 지원 조회 |
| `careapplication` | `provider_idx` | `provider_idx` | BTREE | 제공자별 지원 조회 |
| `carerequest_comment` | `fk_care_request_comment_request` | `care_request_idx` | BTREE | 요청별 댓글 조회 |
| `carerequest_comment` | `fk_care_request_comment_user` | `user_idx` | BTREE | 사용자별 댓글 조회 |
| `carereview` | `care_application_idx` | `care_application_idx` | BTREE | 지원별 리뷰 조회 |
| `carereview` | `reviewee_idx` | `reviewee_idx` | BTREE | 대상자별 리뷰 조회 |
| `carereview` | `reviewer_idx` | `reviewer_idx` | BTREE | 작성자별 리뷰 조회 |
| `pet_coin_escrow` | `care_application_idx` | `care_application_idx` | BTREE | 지원별 에스크로 조회 |
| `pet_coin_escrow` | `idx_created_at` | `created_at` | BTREE | 생성일 범위 조회·통계 |
| `pet_coin_escrow` | `idx_provider` | `provider_idx` | BTREE | 제공자별 에스크로 조회 |
| `pet_coin_escrow` | `idx_requester` | `requester_idx` | BTREE | 요청자별 에스크로 조회 |
| `pet_coin_escrow` | `idx_status` | `status` | BTREE | 상태별 필터링 |
| `pet_coin_escrow` | `uk_care_request` | `care_request_idx` | UNIQUE | 케어 요청당 에스크로 1건 보장 |
| `pet_coin_transaction` | `idx_created_at` | `created_at` | BTREE | 생성일 범위 조회·통계 |
| `pet_coin_transaction` | `idx_related` | `related_type, related_idx` | BTREE | 관련 엔티티별 트랜잭션 조회 |
| `pet_coin_transaction` | `idx_status` | `status` | BTREE | 상태별 필터링 |
| `pet_coin_transaction` | `idx_transaction_type` | `transaction_type` | BTREE | 거래 유형별 필터링 |
| `pet_coin_transaction` | `idx_user_idx` | `user_idx` | BTREE | 사용자별 거래 내역 조회 |

- **위치 필터 설계**: `users.location LIKE CONCAT(:location, '%')` — 접두사 일치만 허용하여 B-tree 인덱스 활용. 중간 일치(`LIKE '%값%'`)는 인덱스 사용 불가라 의도적으로 제외
- **에스크로 UNIQUE 제약**: `uk_care_request`(실제 인덱스명)가 `care_request_idx`에 UNIQUE 제약을 두어 동일 케어 요청에 에스크로가 중복 생성되는 것을 DB 레벨에서 방지

### 말할 내용
> "조회 패턴을 기준으로 인덱스를 설계했습니다. 위치 필터는 중간 일치 LIKE를 쓰면 인덱스를 타지 않아서 접두사 일치로 제한했습니다. 에스크로 테이블은 케어 요청·제공자·요청자·상태·생성일 각각에 단일 인덱스를 두고, care_request_idx에 UNIQUE 제약을 걸어 요청당 에스크로 1건을 DB 레벨에서 보장했습니다. 펫코인 거래 내역은 사용자·상태·거래 유형·(관련 엔티티 타입 + idx) 복합 인덱스로 다양한 필터 조회를 지원합니다."

---

## 6. Soft Delete + 삭제 상태 일관성

### 어필 포인트

- `CareRequest.isDeleted` / `CareRequestComment.isDeleted` 모두 Soft Delete
- `updateStatus()`에서 `isDeleted == true`이면 `CareRequestNotFoundException` 반환 — 소프트 삭제된 요청의 상태 변경 불가
- `getCareRequest()`(단건 조회)도 `isDeleted == true`이면 404 반환
- 단, `restoreForAdmin()`은 `findById()`(삭제 여부 무시)로 조회 후 `isDeleted = false`로 복구

```java
// CareRequestService.updateStatus()
if (Boolean.TRUE.equals(request.getIsDeleted())) {
    throw new CareRequestNotFoundException();
}
```

### 말할 내용
> "Soft Delete를 적용했는데, 단순히 삭제 표시만 하는 것이 아니라 상태 변경 API에서도 삭제된 요청이면 Not Found를 반환하도록 일관성을 유지했습니다. 관리자 복구 경로는 isDeleted를 무시하는 findById를 따로 사용해 분리했습니다."

---

## 7. 트랜잭션 처리 패턴

### 어필 포인트

- **읽기 전용 분리**: 조회 메서드 전체에 `@Transactional(readOnly = true)` — Flush 생략, 스냅샷 미생성
- **쓰기 트랜잭션**: 생성/수정/삭제/상태변경은 `@Transactional`
- **스케줄러 권한 우회**: `CareRequestScheduler`가 `updateStatus(idx, "COMPLETED", null)`을 호출할 때 `currentUserId == null`이면 권한 검증 생략 (시스템 경로 전용)

```java
// CareRequestService.updateStatus() — 스케줄러 경로 처리
if (!isAdmin()) {
    if (currentUserId != null) { // null이면 스케줄러 호출 → 검증 생략
        boolean isRequester = ...;
        boolean isAcceptedProvider = ...;
        if (!isRequester && !isAcceptedProvider) {
            throw CareForbiddenException.ownerOrApprovedProvider();
        }
    }
}
```

- **리뷰 중복 방지**: `existsByCareApplicationIdxAndReviewerIdx`로 선체크 후 `save`. DB 유니크 제약이 있어 동시 요청 시 `DataIntegrityViolationException`도 동일한 `CareConflictException.alreadyReviewed()`로 매핑

### 말할 내용
> "스케줄러가 만료된 요청을 자동 완료 처리할 때 권한 체크를 통과해야 했습니다. currentUserId를 null로 전달하는 규약을 두고, null이면 시스템 경로로 간주해 검증을 생략하도록 했습니다. 리뷰 중복 방지는 exists 체크와 DB 유니크 제약을 이중으로 두고, DataIntegrityViolationException도 동일한 비즈니스 예외로 변환해 응답 일관성을 유지했습니다."

---

## 핵심 키워드

- N+1 문제 — FETCH JOIN + 배치 조회 + @BatchSize 3단계 해결
- 비관적 락 (PESSIMISTIC_WRITE) — Race Condition 해결
- 에스크로 패턴 — HOLD/RELEASE/REFUND 상태 전이
- FULLTEXT 인덱스 — 키워드 검색 최적화
- @BatchSize(size=50) — Hibernate 중첩 컬렉션 / 페이징 N+1 해결
- Soft Delete + 삭제 상태 일관성
- @Transactional(readOnly = true) — 읽기/쓰기 분리
- saveAndFlush — 락 보유 중 즉시 DB 반영

---

## 관련 문서

- N+1 분석(비페이징): `docs/troubleshooting/care/care-request-n-plus-one-analysis.md`
- N+1 분석(페이징): `docs/troubleshooting/care/care-request-paging-n-plus-one.md`
- Race Condition 분석: `docs/troubleshooting/care/care-deal-confirmation-race-condition.md`
- 코드 리뷰: `docs/refactoring/care/care-payment-code-review-2026-04-14.md`
- 리팩토링 기록: `docs/refactoring/care/care-payment-refactoring-2026-04-14.md`
- Care 도메인 전체 설명: `docs/domains/care.md`
