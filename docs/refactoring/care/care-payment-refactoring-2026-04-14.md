# Care & Payment 리팩토링 기록 (적용 완료)

> **목적**: 코드 리뷰(`care-payment-code-review-2026-04-14.md`)에서 나온 항목을 **「어디가 문제였는지 → 무엇으로 바꿨는지 → 끝났는지」** 순서로만 정리한다.  
> **적용일**: 2026-04-14

---

## 1. Care 요청 API — 변경·삭제·상태 변경에 명시적 인가

### 발생 위치
- `backend/.../care/controller/CareRequestController.java` — `createCareRequest`, `updateCareRequest`, `deleteCareRequest`, `updateStatus`

### 문제 / 개선점
- `POST` / `PUT` / `DELETE` / `PATCH`에 메서드 단 `@PreAuthorize`가 없어, 역할·표현 수준의 인가가 한눈에 보이지 않음(전역 `SecurityConfig`에만 의존).

### 개선 코드 (요지)
```java
@PostMapping
@PreAuthorize("isAuthenticated()")
public ResponseEntity<CareRequestDTO> createCareRequest(@RequestBody CareRequestDTO dto) { ... }

@PutMapping("/{id}")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<CareRequestDTO> updateCareRequest(...) { ... }

@DeleteMapping("/{id}")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<Void> deleteCareRequest(...) { ... }

@PatchMapping("/{id}/status")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<CareRequestDTO> updateStatus(...) { ... }
```

### 상태
**개선 완료** (2026-04-14)

---

## 2. 내 요청 목록 — IDOR 제거 (쿼리 `userId` 폐기)

### 발생 위치
- `CareRequestController#getMyCareRequests`
- `frontend/src/api/careRequestApi.js` — `getMyCareRequests`

### 문제 / 개선점
- `GET /api/care-requests/my-requests?userId=` 형태로 **클라이언트가 임의 userId**를 넘길 수 있어, 인증된 사용자가 타인 목록을 조회할 수 있음(IDOR).

### 개선 코드 (요지)
```java
@GetMapping("/my-requests")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<List<CareRequestDTO>> getMyCareRequests() {
    Long currentUserId = getCurrentUserId();
    return ResponseEntity.ok(careRequestService.getMyCareRequests(currentUserId));
}
```
```javascript
// careRequestApi.js — 인자 제거, 쿼리 없이 호출
getMyCareRequests: () => api.get('/my-requests'),
```

### 상태
**개선 완료** (2026-04-14)

---

## 3. Care 리뷰 작성 API — 명시적 인가

### 발생 위치
- `backend/.../care/controller/CareReviewController.java` — `createReview`

### 문제 / 개선점
- `POST`에 `@PreAuthorize` 미부찴 시 의도가 불명확하고, 메서드 단위 정책을 읽기 어려움.

### 개선 코드 (요지)
```java
@PostMapping
@PreAuthorize("isAuthenticated()")
public ResponseEntity<CareReviewDTO> createReview(@RequestBody CareReviewDTO dto) { ... }
```

### 상태
**개선 완료** (2026-04-14)

---

## 4. 펫코인 충전 API — 명시적 인가

### 발생 위치
- `backend/.../payment/controller/PetCoinController.java` — `chargeCoins`

### 문제 / 개선점
- 동일하게 `POST /charge`에 메서드 단 인가 표기가 없었음.

### 개선 코드 (요지)
```java
@PostMapping("/charge")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<PetCoinTransactionDTO> chargeCoins(@RequestBody PetCoinChargeRequest request) { ... }
```

### 상태
**개선 완료** (2026-04-14)

---

## 5. 케어 요청 상태 변경 — 소프트 삭제 요청 차단

### 발생 위치
- `backend/.../care/service/CareRequestService.java` — `updateStatus`

### 문제 / 개선점
- `findByIdWithApplications` 직후 `isDeleted`를 보지 않아, 삭제된 요청도 상태 전이·에스크로 로직이 실행될 수 있음.

### 개선 코드 (요지)
```java
CareRequest request = careRequestRepository.findByIdWithApplications(idx)
        .orElseThrow(() -> new CareRequestNotFoundException());

if (Boolean.TRUE.equals(request.getIsDeleted())) {
    throw new CareRequestNotFoundException();
}
// 이하 권한·상태·에스크로 처리
```

### 상태
**개선 완료** (2026-04-14)

---

## 6. Care 리뷰 서비스 — 읽기 전용 트랜잭션 + 타입 표기 정리

### 발생 위치
- `backend/.../care/service/CareReviewService.java` — `getReviewsByReviewee`, `getReviewsByReviewer` (및 `getAverageRating` 내 스트림)

### 문제 / 개선점
- 조회 메서드에 `@Transactional(readOnly = true)`가 없어 읽기 최적화·세션 일관성이 약함.
- `List<com.linkup.Petory...CareReview>` 등 FQCN으로 가독성 저하.

### 개선 코드 (요지)
```java
@Transactional(readOnly = true)
public List<CareReviewDTO> getReviewsByReviewee(Long revieweeIdx) {
    List<CareReview> reviews = reviewRepository.findByRevieweeIdxOrderByCreatedAtDesc(revieweeIdx);
    ...
}
```
- `CareReview` 단순 클래스명 + import로 통일.

### 상태
**개선 완료** (2026-04-14)

---

## 7. Care 리뷰 저장 — 동시성 시 DB 유니크 위반을 409로 매핑

### 발생 위치
- `CareReviewService#createReview` — `reviewRepository.save(review)`

### 문제 / 개선점
- `exists` 체크와 `save` 사이 레이스 시 DB 유니크 제약 위반 → 그대로면 500 계열로 노출될 수 있음.

### 개선 코드 (요지)
```java
try {
    CareReview saved = reviewRepository.save(review);
    return reviewConverter.toDTO(saved);
} catch (DataIntegrityViolationException e) {
    throw CareConflictException.alreadyReviewed();
}
```

### 상태
**개선 완료** (2026-04-14)

---

## 8. PetCoinBalanceResponse

### 발생 위치
- `backend/.../payment/dto/PetCoinBalanceResponse.java`

### 문제 / 개선점
- 리뷰에서 record 전환 후보라고 했으나, **이미 `record`로 정의되어 있어 변경 없음**.

### 개선 코드
- 해당 없음 (현행 유지).

### 상태
**개선 불필요** (이미 충족, 2026-04-14 확인)

---

## 미적용 (별도 작업 권장)

| 주제 | 사유 |
|------|------|
| `updateStatus` 트랜잭션 범위·에스크로 분리 | 범위 큼, 별도 PR |
| 댓글 알림을 트랜잭션 밖으로 | `@TransactionalEventListener` 등 설계 필요 |
| 케어 검색 `LIKE %` → FULLTEXT | 트래픽·데이터량 보고 결정 |

---

## 관련 문서

- 룰 기반 리뷰 원문: `docs/refactoring/care/care-payment-code-review-2026-04-14.md`
- 도메인 스펙: `docs/domains/care.md`, `docs/domains/payment.md`
