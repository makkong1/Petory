# Care 도메인

> 기준: 현재 코드를 단일 진실로 본다. 이 문서는 펫케어 요청, 채팅 기반 매칭 연결, 케어 댓글, 리뷰, 관리자 운영을 다룬다. 펫코인 차감·에스크로·지급/환불의 내부 구현은 Payment 도메인에서 별도로 다룬다.

## 1. 범위

Care 도메인은 보호자가 펫케어 요청을 등록하고, 서비스 제공자와 채팅으로 조건을 확정한 뒤, 진행/완료/리뷰까지 이어지는 흐름을 담당한다.

포함 범위:

- 케어 요청 목록/상세/생성/수정/삭제
- 지도용 반경 기반 근처 케어 요청 조회
- 상태·지역 필터링과 FULLTEXT 검색
- 요청자 펫 연결
- 채팅 거래 확정 후 `CareApplication` 승인/생성 연결
- 케어 상태 변경
- 만료 요청 자동 완료 스케줄러
- 케어 요청 댓글 조회/작성/삭제
- 케어 리뷰 작성/조회/평균 평점
- 관리자 케어 요청 조회/상태 변경/삭제/복구
- Payment 에스크로 호출 지점

비범위:

- 펫코인 잔액 증감, 에스크로 상태, 거래 내역 상세
- 채팅 메시지 송수신과 WebSocket
- 사용자 프로필/펫 상세 관리
- 알림 전송 인프라 자체
- 운영 가격 가이드 계산 로직

## 2. 주요 코드

| 구분 | 주요 파일 |
|---|---|
| 케어 요청 API | `backend/main/java/com/linkup/Petory/domain/care/controller/CareRequestController.java` |
| 케어 댓글 API | `backend/main/java/com/linkup/Petory/domain/care/controller/CareRequestCommentController.java` |
| 케어 리뷰 API | `backend/main/java/com/linkup/Petory/domain/care/controller/CareReviewController.java` |
| 관리자 API | `backend/main/java/com/linkup/Petory/domain/admin/controller/AdminCareRequestController.java` |
| 관리자 facade | `backend/main/java/com/linkup/Petory/domain/admin/service/AdminCareAndMeetupFacade.java` |
| 케어 요청 서비스 | `backend/main/java/com/linkup/Petory/domain/care/service/CareRequestService.java` |
| 케어 댓글 서비스 | `backend/main/java/com/linkup/Petory/domain/care/service/CareRequestCommentService.java` |
| 케어 리뷰 서비스 | `backend/main/java/com/linkup/Petory/domain/care/service/CareReviewService.java` |
| 자동 완료 스케줄러 | `backend/main/java/com/linkup/Petory/domain/care/service/CareRequestScheduler.java` |
| 채팅 거래 확정 | `backend/main/java/com/linkup/Petory/domain/chat/service/ConversationService.java` |
| Payment 연동 | `backend/main/java/com/linkup/Petory/domain/payment/service/PetCoinEscrowService.java` |
| 케어 요청 repository | `backend/main/java/com/linkup/Petory/domain/care/repository/SpringDataJpaCareRequestRepository.java` |
| 프론트 케어 API | `frontend/src/api/careRequestApi.js` |
| 프론트 리뷰 API | `frontend/src/api/careReviewApi.js` |
| 프론트 관리자 API | `frontend/src/api/careRequestAdminApi.js` |

## 3. 핵심 엔티티

### CareRequest

케어 요청의 중심 엔티티다.

| 필드 | 의미 |
|---|---|
| `idx` | 케어 요청 PK |
| `user` | 요청자 |
| `pet` | 연결된 펫, 선택값 |
| `title`, `description` | 요청 제목/본문 |
| `date` | 고정 일정 또는 희망 일정 |
| `scheduleMode` | `FIXED`, `FLEXIBLE_CHAT` |
| `estimatedDurationMinutes` | 예상 돌봄 시간, 선택값 |
| `offeredCoins` | 요청자가 제시한 코인 금액 |
| `status` | `OPEN`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED` |
| `latitude`, `longitude`, `address` | 지도 표출/반경 검색용 위치 |
| `isDeleted`, `deletedAt` | soft delete 상태 |
| `completedAt` | 완료 시각, 통계 집계용 |

상태를 `COMPLETED`로 변경할 때 `transitionTo()`가 `completedAt`을 기록한다.

### CareApplication

케어 요청과 제공자의 매칭 기록이다.

| 필드 | 의미 |
|---|---|
| `careRequest` | 대상 케어 요청 |
| `provider` | 케어 제공자 |
| `status` | `PENDING`, `ACCEPTED`, `REJECTED` |
| `message` | 지원 메시지 |

`care_request_idx + provider_idx` unique 제약이 있다.

현재 사용자-facing Care API에는 별도 지원 신청 endpoint가 없고, 채팅 거래 확정 흐름에서 `CareApplication`을 생성하거나 `ACCEPTED`로 변경한다.

### CareRequestComment

케어 요청 댓글이다. `SERVICE_PROVIDER` 역할 사용자만 작성할 수 있다. 삭제는 soft delete다.

### CareReview

요청자가 제공자에게 작성하는 리뷰다. 하나의 `CareApplication`과 작성자 조합에 대해 중복 리뷰를 막는다.

## 4. 사용자 케어 요청 API

### `/api/care-requests`

| API | 인증 | 설명 |
|---|---|---|
| `GET /api/care-requests/nearby?lat&lng&radius&limit` | 보안 설정 확인 필요 | 지도용 근처 케어 요청 조회 |
| `GET /api/care-requests?status&location&page&size` | 보안 설정 확인 필요 | 케어 요청 목록 페이징 조회 |
| `GET /api/care-requests/{id}` | 보안 설정 확인 필요 | 케어 요청 상세 조회 |
| `POST /api/care-requests` | 인증 필요 | 케어 요청 생성 |
| `PUT /api/care-requests/{id}` | 인증 필요 | 케어 요청 수정 |
| `DELETE /api/care-requests/{id}` | 인증 필요 | 케어 요청 soft delete |
| `GET /api/care-requests/my-requests` | 인증 필요 | 내 케어 요청 목록 |
| `PATCH /api/care-requests/{id}/status?status=COMPLETED` | 인증 필요 | 케어 요청 상태 변경 |
| `GET /api/care-requests/search?keyword&page&size` | 보안 설정 확인 필요 | 케어 요청 검색 |

생성 시 컨트롤러가 `AuthenticatedUserIdResolver`로 현재 로그인 사용자 PK를 구해 `dto.userId`에 넣는다. 즉, 요청 생성은 클라이언트가 보낸 userId를 신뢰하지 않는다.

## 5. 케어 요청 생성

생성 흐름:

1. 현재 로그인 사용자 조회
2. 이메일 인증 확인
3. 요청자 펫코인 잔액이 `offeredCoins` 이상인지 확인
4. `scheduleMode`가 없으면 `FIXED` 사용
5. 제목, 설명, 일정, 예상 시간, 코인, 주소, 좌표 저장
6. `petIdx`가 있으면 펫 존재와 소유자 확인
7. `status=OPEN`으로 저장
8. `CareRequestCreatedEvent` 발행

이메일 인증 purpose:

- `PET_CARE`

주의:

- 서버는 `offeredCoins >= 1` DTO validation과 잔액 확인을 수행한다.
- 시간당 최소 코인, 펫 크기별 가중치 같은 가격 가이드 공식은 현재 백엔드에 없다.
- 펫 연결은 선택이다.

## 6. 목록, 검색, 지도 조회

### 목록

`getCareRequestsWithPaging(status, location, page, size)`가 사용자 목록 API의 기본 경로다.

조건:

- 삭제되지 않은 요청
- 작성자 `isDeleted=false`
- 작성자 `status=ACTIVE`
- status가 있으면 해당 상태만
- location이 있으면 작성자 위치 접두사 검색

정렬:

- `createdAt` 내림차순

location 검색은 `LIKE '값%'` 형태라 B-tree 인덱스 활용을 고려한 접두사 검색이다.

### 검색

`GET /api/care-requests/search`는 `searchCareRequestsWithPaging()`을 사용한다.

쿼리:

```sql
MATCH(cr.title, cr.description) AGAINST(:keyword IN NATURAL LANGUAGE MODE)
```

검색도 삭제되지 않은 요청과 활성 작성자만 포함한다. `JpaCareRequestAdapter`는 검색 결과 idx를 기준으로 연관 엔티티를 다시 fetch하여 DTO 변환 시 N+1을 줄인다.

### 지도 근처 조회

`GET /api/care-requests/nearby`는 `lat`, `lng`, `radius`, `limit`을 받는다.

정책:

- limit은 1~500 사이로 보정한다.
- `latitude IS NOT NULL`인 요청만 포함한다.
- `OPEN`, `IN_PROGRESS` 상태만 포함한다.
- 위도/경도 bounding box로 먼저 좁힌 뒤 Haversine 거리 조건을 적용한다.

## 7. 수정과 삭제

수정:

- 작성자 또는 `ADMIN`/`MASTER`만 가능하다.
- 제목, 설명, 날짜, 일정 모드, 예상 시간, 주소, 좌표를 부분 수정한다.
- `petIdx`가 있으면 해당 펫이 요청자 소유인지 확인하고 연결한다.
- `petIdx == null`이고 기존 펫이 있으면 펫 연결을 해제한다.

삭제:

- 작성자 또는 `ADMIN`/`MASTER`만 가능하다.
- hard delete가 아니라 `softDelete()`로 `isDeleted=true`, `deletedAt=now` 처리한다.

주의:

- 수정 DTO에서 `petIdx`가 누락된 경우와 명시적 null을 구분하지 못한다. 현재 구현상 기존 펫 연결이 해제될 수 있다.
- 수정 메서드는 삭제된 요청 여부를 별도로 거르지 않는다.

## 8. 상태 전이와 Payment 연결

CareRequest 상태:

```text
OPEN -> IN_PROGRESS -> COMPLETED
OPEN/IN_PROGRESS -> CANCELLED
```

상태 변경 권한:

- 관리자면 우회한다.
- 일반 사용자는 요청자 또는 `ACCEPTED` 상태의 제공자만 가능하다.
- 스케줄러는 `currentUserId=null`로 호출하므로 권한 검증을 생략한다.

`COMPLETED` 처리:

1. `transitionTo(COMPLETED)`로 상태 변경 및 `completedAt` 기록
2. 해당 CareRequest의 에스크로 조회
3. 에스크로가 있고 상태가 `HOLD`이면 `PetCoinEscrowService.releaseToProvider()` 호출
4. Payment 도메인이 비관적 락으로 에스크로를 다시 조회한 뒤 제공자에게 코인을 지급

`CANCELLED` 처리:

1. 상태를 `CANCELLED`로 변경
2. 에스크로가 있고 상태가 `HOLD`이면 `PetCoinEscrowService.refundToRequester()` 호출
3. Payment 도메인이 비관적 락으로 에스크로를 다시 조회한 뒤 요청자에게 환불

Payment 상세는 [Payment 도메인](payment.md)과 [펫케어 코인 관련 흐름](../architecture/care/펫케어 코인 관련 흐름.md)을 기준으로 본다.

## 9. 채팅 기반 매칭

케어 매칭의 실제 확정은 Chat 도메인의 `ConversationService.confirmCareDeal()`에서 일어난다.

흐름:

1. `Conversation`을 비관적 락으로 조회
2. `RelatedType`이 `CARE_REQUEST` 또는 `CARE_APPLICATION`인지 확인
3. 현재 사용자의 `ConversationParticipant.dealConfirmed=true` 저장
4. 활성 참여자 2명이 모두 확정했는지 확인
5. `RelatedType.CARE_REQUEST`이고 요청 상태가 `OPEN`이면 처리
6. 채팅 참여자 중 요청자가 아닌 사용자를 provider로 판단
7. 기존 `CareApplication`이 있으면 `ACCEPTED`로 변경
8. 없으면 새 `CareApplication(status=ACCEPTED)` 생성
9. `CareRequest`를 `IN_PROGRESS`로 변경
10. `PetCoinEscrowService.createEscrow()`로 요청자 코인을 차감하고 에스크로 생성

중요한 현재 동작:

- `createEscrow()` 실패 시 현재 코드는 예외를 다시 던지지 않고 로그만 남긴다.
- 따라서 코인 차감/에스크로 생성이 실패해도 채팅 거래 확정과 `IN_PROGRESS` 상태 전이는 유지될 수 있다.
- 운영 정책상 결제 실패 시 매칭 자체를 롤백해야 한다면 이 지점은 개선 대상이다.

## 10. 자동 완료 스케줄러

`CareRequestScheduler`가 만료 요청을 자동 완료한다.

스케줄:

- 매 시간 정각
- 매일 자정

대상:

- `date < now`
- `status IN (OPEN, IN_PROGRESS)`

처리:

- 스케줄러 자체에는 큰 트랜잭션을 걸지 않는다.
- 각 요청마다 `careRequestService.updateStatus(idx, "COMPLETED", null)`을 호출한다.
- 개별 요청 실패는 로그로 남기고 다음 요청을 계속 처리한다.
- 완료 처리도 일반 상태 변경과 같은 Payment 지급 경로를 탄다.

## 11. 케어 댓글 API

### `/api/care-requests/{careRequestId}/comments`

| API | 설명 |
|---|---|
| `GET /api/care-requests/{careRequestId}/comments` | 댓글 목록 조회 |
| `POST /api/care-requests/{careRequestId}/comments` | 댓글 작성 |
| `DELETE /api/care-requests/{careRequestId}/comments/{commentId}` | 댓글 soft delete |

댓글 작성 정책:

- `dto.userId`로 작성자를 조회한다.
- 작성자 role이 `SERVICE_PROVIDER`가 아니면 거절한다.
- 첨부파일은 `FileTargetType.CARE_COMMENT`로 연결한다.
- 현재 `syncSingleAttachment`를 사용하므로 첫 번째 파일만 저장한다.
- 댓글 작성자가 요청자와 다르면 `CARE_REQUEST_COMMENT` 알림을 생성한다.

댓글 삭제 정책:

- 댓글이 해당 케어 요청에 속하는지 확인한다.
- 댓글 작성자 또는 `ADMIN`/`MASTER`만 삭제할 수 있다.
- 삭제는 soft delete다.

주의:

- 댓글 작성은 서비스에서 `dto.userId`를 사용한다. 현재 인증 사용자와 일치하는지 서비스에서 검증하지 않는다.
- 컨트롤러 메서드에는 별도 `@PreAuthorize`가 없다. 실제 접근 가능 여부는 `SecurityConfig`의 `/api/**` 정책까지 함께 봐야 한다.
- 댓글 목록은 댓글마다 첨부파일을 개별 조회한다. 대량 댓글에서는 batch 조회 개선 여지가 있다.

## 12. 케어 리뷰 API

### `/api/care-reviews`

| API | 인증 | 설명 |
|---|---|---|
| `POST /api/care-reviews` | 인증 필요 | 리뷰 작성 |
| `GET /api/care-reviews/reviewee/{revieweeIdx}` | 보안 설정 확인 필요 | 특정 사용자가 받은 리뷰 목록 |
| `GET /api/care-reviews/reviewer/{reviewerIdx}` | 보안 설정 확인 필요 | 특정 사용자가 작성한 리뷰 목록 |
| `GET /api/care-reviews/average-rating/{revieweeIdx}` | 보안 설정 확인 필요 | 특정 사용자의 평균 평점 |

리뷰 작성 조건:

- `careApplicationId`가 필요하다.
- 대상 `CareApplication` 상태가 `ACCEPTED`여야 한다.
- 동일 `CareApplication + reviewer` 조합의 중복 리뷰를 막는다.
- reviewer는 케어 요청자여야 한다.
- reviewee는 제공자여야 한다.

주의:

- 리뷰 작성은 요청 DTO의 `reviewerId`, `revieweeId`를 사용한다. 현재 인증 사용자와 reviewerId 일치 여부를 서비스에서 검증하지 않는다.
- 리뷰는 `CareRequest`가 `COMPLETED`인지 확인하지 않고, `CareApplication.ACCEPTED` 여부만 확인한다.

## 13. 관리자 API

### `/api/admin/care-requests`

`ADMIN`, `MASTER` 접근 가능.

| API | 설명 |
|---|---|
| `GET /api/admin/care-requests?status&deleted&q&page&size` | 관리자 케어 요청 페이징 조회 |
| `GET /api/admin/care-requests/{id}` | 관리자 단건 조회 |
| `PATCH /api/admin/care-requests/{id}/status?status=...` | 상태 변경 |
| `POST /api/admin/care-requests/{id}/delete` | soft delete |
| `POST /api/admin/care-requests/{id}/restore` | 복구 |

관리자 목록:

- status 필터
- deleted 필터
- q가 있으면 title/description FULLTEXT 검색
- q가 없으면 JPQL 필터 조회

관리자 변경 작업은 `AdminCareAndMeetupFacade`를 거치며 `AdminAuditService`에 감사 로그를 남긴다.

## 14. 도메인 간 연결

User:

- 요청자, 제공자, 댓글 작성자, 리뷰 작성자/대상.
- 요청 생성 시 이메일 인증 확인.
- 목록/검색에서는 활성 사용자 요청만 노출.

Pet:

- 요청에 선택적으로 연결된다.
- 요청자 본인 소유 펫만 연결 가능하다.

Chat:

- 케어 거래 확정의 실제 진입점이다.
- 양쪽 확정 시 `CareApplication`과 `CareRequest` 상태를 변경한다.

Payment:

- 거래 확정 시 에스크로 생성.
- 완료 시 제공자 지급.
- 취소 시 요청자 환불.

File:

- 케어 댓글 첨부파일.

Notification:

- 케어 댓글 작성 시 요청자에게 알림.

Recommendation:

- 케어 요청 생성 시 `CareRequestCreatedEvent`를 발행한다.

Statistics:

- 완료 시각 `completedAt`과 Payment 지급 기록이 통계 집계에 사용된다.

## 15. 한계와 개선

- 별도 사용자-facing 케어 지원 신청/승인 API가 없다. 현재 매칭 전이는 채팅 거래 확정에 강하게 묶여 있다.
- 채팅 거래 확정에서 에스크로 생성 실패를 롤백하지 않는다.
- 케어 댓글 작성과 리뷰 작성은 요청 DTO의 사용자 ID를 사용하고, 인증 사용자와의 일치 검증이 약하다.
- 리뷰는 완료 상태가 아니라 `CareApplication.ACCEPTED`만 요구한다.
- 케어 수정 DTO는 `petIdx` 누락과 명시적 null을 구분하지 못해 기존 펫 연결을 해제할 수 있다.
- 케어 수정은 soft-deleted 요청 여부를 별도 차단하지 않는다.
- 댓글 첨부파일 조회는 댓글별 개별 조회라 N+1 가능성이 있다.
- 가격 가이드 문서에 있는 시간/체급 기반 최소 코인 정책은 현재 서버 코드에 구현되어 있지 않다.
- 스케줄러가 `OPEN` 상태의 만료 요청도 `COMPLETED`로 전환한다. 실제 매칭되지 않은 요청을 완료로 볼지 정책 확인이 필요하다.

## 16. 관련 문서

- [펫 케어 & 매칭 아키텍처](../architecture/care/펫 케어 & 매칭 아키텍처.md)
- [펫케어 코인 관련 흐름](../architecture/care/펫케어 코인 관련 흐름.md)
- [Payment 도메인](payment.md)
- [Care 요청 N+1 분석](../troubleshooting/care/care-request-n-plus-one-analysis.md)
- [Care 요청 페이징 N+1](../troubleshooting/care/care-request-paging-n-plus-one.md)
- [Care 도메인 기술 분석](../troubleshooting/care/care-domain-technical-analysis.md)
- [Care 거래 확정 Race Condition](../troubleshooting/care/care-deal-confirmation-race-condition.md)
- [Care Payment 리팩토링](../refactoring/care/care-payment-refactoring-2026-04-14.md)
- [Care Payment 코드 리뷰](../refactoring/care/care-payment-code-review-2026-04-14.md)
