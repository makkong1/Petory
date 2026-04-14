# Care & Payment 도메인 코드 리뷰 결과

> **날짜**: 2026-04-14
> **대상**: `domain/care/`, `domain/payment/` 전체
> **리뷰 기준**: `.claude/skills/review.md` 체크리스트 A~E
>
> **리팩토링 적용 서술**(발생 위치 → 개선 코드 → **개선 완료**):  
> → `docs/refactoring/care/care-payment-refactoring-2026-04-14.md` 를 본문으로 본다.

---

## 점수판 요약

| 카테고리 | Critical | Warning | Info |
|---------|----------|---------|------|
| JPA/쿼리 (A) | 1 | 2 | 1 |
| 트랜잭션 (B) | 1 | 2 | 0 |
| 보안 (C) | 4 | 0 | 0 |
| 정합성 (D) | 0 | 1 | 0 |
| 코드품질 (E) | 0 | 2 | 2 |
| **합계** | **6** | **7** | **3** |

**판정**: ~~🔴 수정 필요 (Critical 6개)~~ → ✅ Critical 전부 수정 완료 (2026-04-14)

---

## 🔴 Critical (6건)

### 1. [B2] updateStatus() 트랜잭션 범위 과대

**파일**: `CareRequestService.java:278~347`

**문제**: `@Transactional` 하나에 상태 변경 + 에스크로 비관적 락 조회 + 코인 지급/환불 + DB 저장이 전부 묶여있다. 에스크로 락 보유 시간이 길어지고, 코인 지급 실패 시 상태 변경까지 전체 롤백된다.

**개선 방향**:
- 상태 변경과 코인 처리를 분리
- 에스크로 처리를 별도 빈의 `REQUIRES_NEW` 트랜잭션으로 분리하거나, `@TransactionalEventListener`로 분리

---

### 2. [C1] CareRequestController - @PreAuthorize 누락

**파일**: `CareRequestController.java:71~101`

**문제**: `POST`, `PUT`, `DELETE`, `PATCH` 엔드포인트에 `@PreAuthorize` 없음. `SecurityConfig` catch-all에만 의존하여 역할 제한 불가.

**대상 메서드**:
- `createCareRequest` (POST)
- `updateCareRequest` (PUT)
- `deleteCareRequest` (DELETE)
- `updateStatus` (PATCH)

**개선**: `@PreAuthorize("hasRole('USER')")` 또는 `@PreAuthorize("isAuthenticated()")` 추가

---

### 3. [C1] CareReviewController - POST @PreAuthorize 누락

**파일**: `CareReviewController.java:23~25`

**문제**: `createReview()`에 권한 체크 없음.

**개선**: `@PreAuthorize("hasRole('USER')")` 추가

---

### 4. [C1] PetCoinController - POST /charge @PreAuthorize 누락

**파일**: `PetCoinController.java:87~104`

**문제**: 코인 충전 엔드포인트에 역할 제한 없음.

**개선**: `@PreAuthorize("hasRole('USER')")` 추가

---

### 5. [C3] getMyCareRequests() - userId를 쿼리파라미터로 받음

**파일**: `CareRequestController.java:92~95`

**문제**: `GET /my-requests?userId=123`으로 다른 사용자의 요청 목록 조회 가능. 인증된 사용자 ID를 클라이언트에서 전달받는 보안 취약점.

**현재 코드**:
```java
@GetMapping("/my-requests")
public ResponseEntity<List<CareRequestDTO>> getMyCareRequests(@RequestParam Long userId) {
    return ResponseEntity.ok(careRequestService.getMyCareRequests(userId));
}
```

**개선 코드**:
```java
@GetMapping("/my-requests")
public ResponseEntity<List<CareRequestDTO>> getMyCareRequests() {
    Long currentUserId = getCurrentUserId();
    return ResponseEntity.ok(careRequestService.getMyCareRequests(currentUserId));
}
```

---

### 6. [A5] updateStatus() - 소프트 삭제 체크 누락

**파일**: `CareRequestService.java:278~280`

**문제**: `findByIdWithApplications(idx)` 조회 후 `isDeleted` 확인 없이 상태 변경. 삭제된 요청도 상태 변경 가능.

**개선**: `getCareRequest()`처럼 조회 직후 `isDeleted` 체크 추가

---

## 🟡 Warning (7건)

### 1. [A3] CareRequest 검색 - LIKE '%keyword%'

**파일**: `SpringDataJpaCareRequestRepository.java:48~55, 113~115`

`LOWER(cr.title) LIKE LOWER(CONCAT('%', :keyword, '%'))` → B-tree 인덱스 미사용, 풀 스캔. 데이터량 증가 시 FULLTEXT INDEX 전환 필요.

**조치 (완료)**: `MATCH(cr.title, cr.description) AGAINST(:keyword IN NATURAL LANGUAGE MODE)`로 전환. 목록 검색은 `findIdxByFulltextKeyword` + `findByIdxInWithAssociations`, 페이징은 네이티브 `searchWithPaging` 후 `JpaCareRequestAdapter`에서 동일 FETCH 재조회. DB에 `idx_carerequest_title_description` FULLTEXT 인덱스 적용 필수 (`docs/migration/db/indexes.sql`).

---

### 2. [A3] 페이징 location 필터 - LIKE '%location%'

**파일**: `SpringDataJpaCareRequestRepository.java:86~88`

`u.location LIKE CONCAT('%', :location, '%')` → 풀 스캔. 지역 필드 분리(sido, sigungu) 또는 접두사 검색 권장.

**조치 (완료)**: `u.location LIKE CONCAT(:location, '%')` 접두사 검색으로 변경. 전체 목록 API(`getAllCareRequests`)도 동일하게 `startsWith`로 맞춤. 중간 일치는 더 이상 지원하지 않음. `users.location` 인덱스는 `idx_users_location` 참고.

---

### 3. [B4] createReview() - 동시 요청 시 중복 리뷰 예외 미처리

**파일**: `CareReviewService.java:112~116`

`existsBy...` 체크 → `save()` 사이 동시 요청 가능. Entity `@UniqueConstraint`로 DB에서 막히지만 `DataIntegrityViolationException` catch 없음. 사용자에게 500 대신 409 응답을 주려면 catch 처리 필요.

---

### 4. [D2] 에스크로 중복 생성 예외 미변환

**파일**: `PetCoinEscrowService.java:51~54`

`findByCareRequest` → 동시 요청 시 중복 가능. `care_request_idx UNIQUE`로 DB에서 막히지만, `DataIntegrityViolationException` → 도메인 예외 변환 없음.

---

### 5. [E1] CareReviewService - @Transactional(readOnly = true) 누락

**파일**: `CareReviewService.java:39~45, 50~56`

`getReviewsByReviewee()`, `getReviewsByReviewer()`에 트랜잭션 어노테이션 없음. 읽기 전용 최적화 누락.

---

### 6. [E3] PetCoinBalanceResponse - record 변환 가능

필드 2개(userId, balance)만 있는 DTO. record로 전환하면 코드 감소.

---

### 7. [B2] addComment() 트랜잭션 안에서 알림 발송

**파일**: `CareRequestCommentService.java:89~101`

`@Transactional` 안에서 `notificationService.createNotification()` 호출. 알림 실패 시 댓글 저장 롤백. `@TransactionalEventListener` 분리 권장.

---

## 🟢 Info (3건)

| # | 파일 | 내용 |
|---|------|------|
| 1 | CareReviewService.java:40,51,79,87 | 불필요한 FQCN 사용 (이미 import됨) |
| 2 | CareRequestCommentDTO, CareReviewDTO | record 변환 검토 대상 |
| 3 | CareRequestService.getAllCareRequests() | 비페이징 전체 조회 잔존 (페이징 버전 있음, deprecated 처리 가능) |

---

## ✅ 잘된 점

- **PetCoinService**: 모든 금액 변경에 `findByIdForUpdate()` 비관적 락 일관 적용
- **PetCoinEscrowService**: 에스크로 지급/환불에도 `findByIdForUpdate()` 이중 락 적용
- **Entity fetch 전략**: 전체 `@ManyToOne`에 `FetchType.LAZY` 명시
- **도메인 Repository 분리**: 인터페이스 + JPA 어댑터 패턴 일관 적용
- **`@BatchSize(50)`**: CareRequest.applications에 적용하여 목록 조회 N+1 방지
- **AdminPaymentController**: 클래스 레벨 `@PreAuthorize("hasAnyRole('ADMIN', 'MASTER')")` 적용
- **소프트 삭제**: 쿼리에서 `isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE'` 일관 적용

---

## 수정 우선순위

| 순위 | 항목 | 난이도 | 영향도 |
|------|------|--------|--------|
| 1 | 보안 (C1, C3): @PreAuthorize 추가 + my-requests 수정 | 낮음 | 🔴 높음 |
| 2 | 소프트 삭제 (A5): updateStatus() isDeleted 체크 | 낮음 | 🔴 높음 |
| 3 | 중복 예외 처리 (B4, D2): DataIntegrityViolation catch | 낮음 | 🟡 중간 |
| 4 | 트랜잭션 분리 (B2): updateStatus() 에스크로 처리 | 중간 | 🟡 중간 |
| 5 | 알림 분리 (B2): 댓글 알림 트랜잭션 밖으로 | 중간 | 🟡 중간 |
| 6 | 검색 최적화 (A3): FULLTEXT 전환 | 높음 | 🟢 낮음 (데이터량 의존) |

---

## 관련 문서

- 도메인 스펙: `docs/domains/care.md`, `docs/domains/payment.md`
- 기존 트러블슈팅: `docs/troubleshooting/care/potential-issues.md`
- 기존 리팩토링: `docs/refactoring/payment/petcoin-service-race-condition.md`

---

## 리팩토링 적용 이력 (2026-04-14)

| # | 항목 | 타입 | 파일 | 상태 |
|---|------|------|------|------|
| 1 | [C1] @PreAuthorize 추가 | 🏗️ Structure | CareRequestController | ✅ 완료 |
| 2 | [C3] my-requests userId → 토큰 기반 | 🏗️ Structure | CareRequestController, careRequestApi.js | ✅ 완료 |
| 3 | [C1] @PreAuthorize 추가 | 🏗️ Structure | CareReviewController | ✅ 완료 |
| 4 | [C1] @PreAuthorize 추가 | 🏗️ Structure | PetCoinController | ✅ 완료 |
| 5 | [A5] isDeleted 체크 추가 | 🏗️ Structure | CareRequestService | ✅ 완료 |
| 6 | [E1] @Transactional(readOnly) 추가 | 📖 Readability | CareReviewService | ✅ 완료 |
| 7 | [E2] FQCN → import 클래스명 | 📖 Readability | CareReviewService | ✅ 완료 |
| 8 | [B4] DataIntegrityViolation catch | 🏗️ Structure | CareReviewService | ✅ 완료 |
| - | [E3] PetCoinBalanceResponse record | - | - | 이미 record 확인 |

### 미적용 (추후 검토)
| 항목 | 사유 |
|------|------|
| [B2] updateStatus() 트랜잭션 분리 | 구조 변경 규모 큼, 별도 PR 권장 |
| [B2] addComment() 알림 분리 | @TransactionalEventListener 도입 필요, 별도 작업 |
| [A3] FULLTEXT 전환 | 데이터량 적을 때는 현행 유지, 성능 이슈 발생 시 적용 |

---

## 도메인 문서 동기화 (docs-sync, 2026-04-14)

코드가 진실인 전제로 `docs/domains/care.md`, `docs/domains/payment.md`를 아래와 같이 맞춤.

| 문서 | 반영 내용 |
|------|-----------|
| `care.md` | §2 로직 4·서비스 표: `updateStatus` **소프트 삭제 거부**, `getCareRequest`/`getMyCareRequests`/`CareReviewService` 동작; §3.4 API·**보안 참고**(@PreAuthorize, my-requests 무파라미터, `careRequestApi.js`); §7 링크 문구 |
| `payment.md` | §8.3·§10.0: **`POST /charge`에 `@PreAuthorize("isAuthenticated()")`**; 관련 문서 링크 문구 |
| `care-payment-refactoring-2026-04-14.md` | **신규** — 위 리팩토링을 「발생 위치 → 문제 → 개선 코드 → 상태」형으로 정리 |
