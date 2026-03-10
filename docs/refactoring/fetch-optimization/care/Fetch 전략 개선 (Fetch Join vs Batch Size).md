# Care 도메인 Fetch 전략 개선

> **규칙**: 단건 상세 → Fetch Join / 페이징 목록 → Batch Size

---

## 요약

| 구분 | 대상 | 전략 | 상태 |
|------|------|------|------|
| CareRequest 단건 상세 | `getCareRequest` | Fetch Join | ✅ 적용됨 |
| CareRequest 단건 수정 | `updateCareRequest` | Fetch Join | ✅ 적용됨 |
| CareRequest 단건 삭제 | `deleteCareRequest` | Fetch Join | ✅ 적용됨 |
| CareRequest 페이징 목록 | `getCareRequestsWithPaging`, `searchCareRequestsWithPaging` | Batch Size | ✅ 이미 적용됨 |
| CareRequest 비페이징 목록 | `getAllCareRequests`, `getMyCareRequests` 등 | Fetch Join (쿼리) | ✅ 이미 적용됨 |
| CareRequestComment 목록 | `getComments` | Fetch Join | ✅ 적용됨 |
| CareApplication (단건 내) | `findByIdWithApplications` → provider | Fetch Join | ✅ 적용됨 |
| CareReview | `getReviewsByReviewee` 등 | 단순 조회 | N+1 가능성 낮음 |

---

## 1. CareRequest (펫케어 요청)

### 1.1 단건 상세 조회 (getCareRequest) — ✅ 적용됨

**대상**
- `getCareRequest(idx)` — 펫케어 요청 상세 (`GET /api/care-requests/{id}`)

**적용 내용**
- `findByIdWithApplications` 사용 (user, pet, applications, provider 모두 Fetch Join)
- `CareApplicationConverter.toDTO()`에서 provider 접근 시 추가 쿼리 없음

---

### 1.2 단건 수정/삭제 — ✅ 적용됨

**대상**
- `updateCareRequest(idx, dto, currentUserId)` — 수정 (`PUT /api/care-requests/{id}`)
- `deleteCareRequest(idx, currentUserId)` — 삭제 (`DELETE /api/care-requests/{id}`)

**적용 내용**
- `updateCareRequest`: `findByIdWithApplications` 사용 (반환 DTO에 user, pet, applications 필요)
- `deleteCareRequest`: `findByIdWithUser` 사용 (권한 확인용 user만 필요)

---

### 1.3 페이징 목록 — ✅ 이미 적용됨

**대상**
- `getCareRequestsWithPaging` — `GET /api/care-requests?page=&size=`
- `searchCareRequestsWithPaging` — `GET /api/care-requests/search?keyword=`

**현재 상태**
- `findAllActiveRequestsWithPaging`, `findByStatusAndIsDeletedFalseWithPaging`, `searchWithPaging` — user, pet Fetch Join ✅
- `applications`는 페이징 쿼리에 Fetch Join 없음 (Page + OneToMany 제약)
- **`CareRequest.applications`에 `@BatchSize(size=50)` 적용됨** → applications 접근 시 배치 조회

```java
// CareRequest.java (65행)
@OneToMany(mappedBy = "careRequest", cascade = CascadeType.ALL)
@BatchSize(size = 50)  // 페이징 목록 조회 시 CareApplication N+1 방지
private List<CareApplication> applications;
```

→ 메인 쿼리 1회 + count 1회 + applications 배치 1회 = **총 3회** (N+1 해결됨)

---

### 1.4 비페이징 목록 — ✅ 이미 적용됨

**대상**
- `getAllCareRequests`, `getMyCareRequests`, `searchCareRequests`

**현재 상태**
- `findAllActiveRequests`, `findByUserAndIsDeletedFalseOrderByCreatedAtDesc`, `findByStatusAndIsDeletedFalse`, `findByTitleContaining...` — **`LEFT JOIN FETCH cr.applications`** 포함
- user, pet, applications 모두 한 번에 조회

---

### 1.5 updateStatus — ✅ 이미 적용됨

**대상**
- `updateStatus(idx, status, currentUserId)` — 상태 변경

**현재 상태**
- `findByIdWithApplications` 사용 → user, pet, applications Fetch Join ✅

---

## 2. CareRequestComment (펫케어 댓글)

### 2.1 댓글 목록 조회 — ✅ 적용됨

**대상**
- `getComments(careRequestId)` — 요청별 댓글 목록 (`GET /api/care-requests/{id}/comments`)

**적용 내용**
- `findByCareRequestAndIsDeletedFalseOrderByCreatedAtAsc`에 `JOIN FETCH cc.user` 추가
- `CareRequestCommentConverter.toDTO()`에서 user 접근 시 추가 쿼리 없음

---

## 3. CareApplication (펫케어 지원)

### 3.1 CareRequest 단건 내 applications 변환 — ✅ 적용됨

**적용 내용**
- `findByIdWithApplications` 쿼리에 `LEFT JOIN FETCH a.provider` 추가
- applications N개당 provider N회 쿼리 → 1회 쿼리로 수렴

---

## 4. CareReview

### 4.1 리뷰 목록 조회

**대상**
- `getReviewsByReviewee`, `getReviewsByReviewer`, `getReviewsWithAverage`

**현재 상태**
- `findByRevieweeIdxOrderByCreatedAtDesc`, `findByReviewerIdxOrderByCreatedAtDesc` — 단순 조회
- `CareReviewConverter.toDTO()`에서 reviewer, reviewee, careApplication 접근 가능
- 리뷰 수가 많지 않다면 N+1 영향 제한적 → 우선순위 낮음

---

## 5. 적용 불필요 구간

| 구간 | 이유 |
|------|------|
| `CareRequest.createCareRequest` | save 후 toDTO — user, pet 이미 로드, applications 빈 리스트 |
| `CareRequestComment` 단건 (addComment, deleteComment) | 단건 조회, N+1 아님 |
| `CareReview` 단건 | 리뷰 수 적음 |
| `CareApplication` 단독 조회 | `createReview` 등에서 findById — 단건 |

---

## 6. 적용 완료 내역

| 파일 | 적용 내용 |
|------|----------|
| `SpringDataJpaCareRequestRepository` | `findByIdWithUser`, `findByIdWithApplications`에 `LEFT JOIN FETCH a.provider` 추가 |
| `CareRequestRepository`, `JpaCareRequestAdapter` | `findByIdWithUser` 시그니처/구현 추가 |
| `CareRequestService.getCareRequest` | `findByIdWithApplications` 사용 |
| `CareRequestService.updateCareRequest` | `findByIdWithApplications` 사용 |
| `CareRequestService.deleteCareRequest` | `findByIdWithUser` 사용 |
| `SpringDataJpaCareRequestCommentRepository` | `findByCareRequestAndIsDeletedFalseOrderByCreatedAtAsc`에 `JOIN FETCH cc.user` 추가 |

---

## 7. 참고 문서

- [care-request-paging-n-plus-one.md](../../troubleshooting/care/care-request-paging-n-plus-one.md) — 페이징 N+1 분석
- [care-request-n-plus-one-analysis.md](../../troubleshooting/care/care-request-n-plus-one-analysis.md) — 비페이징 경로 분석
