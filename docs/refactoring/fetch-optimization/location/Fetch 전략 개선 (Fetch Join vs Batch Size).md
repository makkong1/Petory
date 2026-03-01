# Location 도메인 Fetch 전략 개선

> **규칙**: 단건 상세 → Fetch Join / 페이징 목록 → Batch Size

---

## 요약

| 구분 | 대상 | 전략 | 상태 |
|------|------|------|------|
| LocationService 목록 | `searchLocationServicesByRegion`, `searchLocationServicesByLocation` 등 | N/A | ✅ reviews 미접근 |
| LocationService 단건 | 단건 상세 API 없음 | - | 적용 불필요 |
| LocationServiceReview 목록 (서비스별) | `getReviewsByService` | Fetch Join | ✅ 적용됨 |
| LocationServiceReview 목록 (사용자별) | `getReviewsByUser` | Fetch Join | ✅ 적용됨 |
| LocationServiceReview 단건 | `updateReview`, `deleteReview` | Fetch Join | ✅ 적용됨 |

---

## 1. LocationService (장소 서비스)

### 1.1 목록 조회 — ✅ N+1 없음

**대상**
- `searchLocationServicesByRegion`, `searchLocationServicesByLocation`, `searchLocationServicesByKeyword`
- `getPopularLocationServices`

**현재 상태**
- `LocationServiceConverter.toDTO()`는 값 타입만 사용 (reviews 미접근)
- `LocationService.reviews`는 목록/상세 응답에 포함되지 않음
- **N+1 발생 없음**

### 1.2 단건 조회

- `GET /api/location-services/{id}` 단건 상세 API 없음
- `deleteService`는 `findById`만 사용, reviews 미접근

---

## 2. LocationServiceReview (장소 리뷰)

### 2.1 서비스별 리뷰 목록 — ✅ 적용됨

**대상**
- `getReviewsByService(serviceIdx)` — `GET /api/location-service-reviews/service/{serviceIdx}`

**적용 내용**
- `findByServiceIdxOrderByCreatedAtDesc`에 `JOIN FETCH r.user` 추가
- user N+1 해결

### 2.2 사용자별 리뷰 목록 — ✅ 적용됨

**대상**
- `getReviewsByUser(userIdx)` — `GET /api/location-service-reviews/user/{userIdx}`

**적용 내용**
- `findByUserIdxOrderByCreatedAtDesc`에 `JOIN FETCH r.service` 추가
- service N+1 해결

### 2.3 단건 수정/삭제 — ✅ 적용됨

**대상**
- `updateReview(reviewIdx, dto)` — `PUT /api/location-service-reviews/{reviewIdx}`
- `deleteReview(reviewIdx)` — `DELETE /api/location-service-reviews/{reviewIdx}`

**적용 내용**
- `findByIdWithUserAndService` 추가 및 `updateReview`, `deleteReview`에서 사용
- 3회 쿼리 → 1회 쿼리로 수렴

---

## 3. 적용 불필요 구간

| 구간 | 이유 |
|------|------|
| LocationService 목록 | Converter가 reviews 미접근 |
| LocationService 단건 | 단건 상세 API 없음 |
| LocationServiceReview.createReview | save 후 toDTO — service, user 이미 로드됨 |
| LocationService.reviews | 목록/상세 응답에 reviews 미포함 |

---

## 4. 적용 완료 내역

| 파일 | 적용 내용 |
|------|----------|
| `SpringDataJpaLocationServiceReviewRepository` | `findByServiceIdxOrderByCreatedAtDesc`에 `JOIN FETCH r.user` 추가 |
| `SpringDataJpaLocationServiceReviewRepository` | `findByUserIdxOrderByCreatedAtDesc`에 `JOIN FETCH r.service` 추가 |
| `SpringDataJpaLocationServiceReviewRepository` | `findByIdWithUserAndService(Long idx)` 추가 |
| `LocationServiceReviewRepository`, `JpaLocationServiceReviewAdapter` | `findByIdWithUserAndService` 시그니처/구현 추가 |
| `LocationServiceReviewService.updateReview`, `deleteReview` | `findByIdWithUserAndService` 사용 |

---

## 5. 참고

- LocationService 대부분의 조회는 **native query** (ST_Within, FULLTEXT 등) 사용
- JPQL Fetch Join 적용 대상은 **LocationServiceReview**만 해당
