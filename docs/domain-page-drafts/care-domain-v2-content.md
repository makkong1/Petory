# CareDomainV2 페이지 드래프트 점검 및 작성

> 목적: 포트폴리오 repo의 `CareDomainV2.jsx` 현재 내용과 Petory 코드/문서를 비교해, 실제 페이지에 넣을 문구와 수정 포인트를 확정한다.  
> 작성 기준: 현재 구현을 1순위로 보고, `docs/domains/care.md`, Care 아키텍처, care/payment 리팩토링·트러블슈팅 문서는 근거와 맥락으로만 사용한다.

---

## 0. 판단

현재 첨부한 `CareDomainV2.jsx`는 큰 구조와 방향이 좋다. Care는 Payment, Chat과 강하게 연결되므로 포트폴리오 페이지에서도 단순 요청 CRUD보다 "채팅 거래 확정 -> CareApplication 승인/생성 -> 에스크로 생성 -> 완료/취소 지급·환불" 흐름을 중심에 두는 게 맞다.

다만 몇 가지 문구는 최신 코드 기준으로 조정해야 한다.

- `createCareRequest`는 컨트롤러가 `AuthenticatedUserIdResolver`로 현재 로그인 사용자 PK를 DTO에 주입한다. 따라서 "요청 바디 userId를 신뢰"한다고 쓰면 안 된다.
- Care 댓글 작성과 Care 리뷰 작성은 여전히 DTO의 `userId`, `reviewerId`, `revieweeId` 의존이 남아 있다. 이 한계는 생성 요청이 아니라 댓글/리뷰 쪽으로 좁혀서 적는다.
- 댓글 삭제는 `Authentication.getName()`으로 요청자 신원을 확인하고 작성자/관리자 검증을 한다. 첨부 JSX의 `[개선 완료] 댓글 삭제` 문구는 맞다.
- 리뷰는 `CareRequestStatus.COMPLETED`가 아니라 `CareApplicationStatus.ACCEPTED` 기준으로 작성 가능하다. 이건 한계 섹션에 유지한다.
- 성능 수치 `~2,400개 -> 4~5개`, `1,084ms -> 66ms`, `21MB -> 6MB`는 문서 근거가 있다. 다만 기존 N+1 최적화 측정값으로 설명하고, 모든 API 호출의 항상값처럼 쓰지 않는다.
- 페이징 목록 경로는 OneToMany fetch join 대신 batch 전략이 섞인다. "목록 조회는 fetch join + 배치 전략"으로 쓰면 안전하다.

---

## 1. 페이지 상단

### H1

Care 도메인

### 소개 문단

Care 도메인은 반려동물 돌봄이 필요한 사용자와 돌봄 제공자를 연결하는 매칭 영역이다. 구현의 핵심은 요청 등록 자체보다 채팅, 케어 상태, 펫코인 에스크로가 맞물리는 거래 확정 흐름이었다. 두 사용자가 동시에 거래를 확정할 때 발생할 수 있는 Race Condition을 `Conversation` 비관적 락으로 제어하고, 확정 이후에는 `CareApplication` 승인/생성, `CareRequest` 상태 전이, Payment 에스크로 생성을 연결한다. 목록 조회에서는 요청자, 반려동물, 지원자 수, 파일, 예방접종 정보가 얽히며 발생하는 N+1을 fetch join과 batch 전략으로 줄였다.

### 핵심 기능 태그 (`corePillars`)

첨부 JSX의 5개 태그는 그대로 사용 가능하다.

```javascript
const corePillars = [
  'Race Condition 제어',
  'N+1 최적화',
  '에스크로 연동',
  '처리 경로 일원화',
  '위치 기반 조회',
];
```

선택적으로 검색을 강조하려면 `"FULLTEXT 검색"`을 추가할 수 있지만, 페이지가 Payment/Chat 연결 중심이라면 현재 5개가 더 깔끔하다.

---

## 2. `section#intro` - 도메인 개요

### 2-1. 개요 카드 문구

거래 확정 흐름은 Chat, Care, Payment 세 도메인이 만나는 지점이다. `ConversationService.confirmCareDeal()`은 `Conversation`을 비관적 락으로 조회하고, 참여자 2명이 모두 확정했을 때만 케어 거래를 진행한다. `RelatedType.CARE_REQUEST`인 경우 요청자가 아닌 참여자를 provider로 판단하고, 기존 `CareApplication`이 있으면 `ACCEPTED`로 바꾸며 없으면 새로 생성한다. 이후 `CareRequest`를 `IN_PROGRESS`로 전환하고 `PetCoinEscrowService.createEscrow()`로 요청자 코인을 차감해 에스크로를 만든다.

다만 현재 구현은 에스크로 생성 실패를 다시 던지지 않고 로그만 남긴다. 따라서 코인 차감/에스크로 생성이 실패해도 `CareApplication.ACCEPTED`와 `CareRequest.IN_PROGRESS` 상태 전이는 유지될 수 있다. 이 점은 Payment와 Care의 원자성 정책을 논할 때 페이지의 주요 한계로 보여주는 것이 좋다.

목록 조회는 요청자, 반려동물, 지원자 수, 파일, 예방접종 정보가 같이 필요하다. 비페이징 목록은 `JOIN FETCH`로 핵심 연관을 같이 가져오고, 파일과 예방접종은 배치 조회 또는 `@BatchSize`로 보완한다. 페이징 목록은 JPA Page와 OneToMany fetch join 제약 때문에 batch 전략을 함께 사용한다.

### 2-2. 구조 테이블

| 항목 | 현재 코드 기준 |
|---|---|
| 사용자 API | `CareRequestController`의 `/api/care-requests` |
| 댓글 API | `CareRequestCommentController`의 `/api/care-requests/{id}/comments` |
| 리뷰 API | `CareReviewController`의 `/api/care-reviews` |
| 생성 사용자 기준 | `AuthenticatedUserIdResolver.requireCurrentUserIdx()`로 DTO `userId` 주입 |
| 생성 시 코인 처리 | 잔액 확인만 수행, 실제 차감은 거래 확정 시 에스크로 생성에서 수행 |
| 채팅 확정 | `ConversationService.confirmCareDeal()`에서 비관적 락 |
| 매칭 기록 | `CareApplication` 기존 값 승인 또는 새로 생성 |
| 거래 상태 | 양쪽 확정 후 `CareRequestStatus.IN_PROGRESS` |
| 완료/취소 | `CareRequestService.updateStatus()`에서 Payment 지급/환불 호출 |
| 지도 조회 | `lat/lng/radius/limit`, `OPEN`/`IN_PROGRESS`만 조회 |
| 검색 | title/description FULLTEXT 검색 |
| 댓글 작성 | `SERVICE_PROVIDER`만 가능, DTO `userId` 기반 |
| 리뷰 작성 | 요청자 -> 제공자, `CareApplication.ACCEPTED` 기준 |

### 2-3. 성능 테이블

첨부 페이지의 수치는 사용 가능하다. 단, 문구는 "케어 요청 목록 N+1 최적화 측정"으로 명확히 둔다.

| 지표 | Before | After |
|---|---|---|
| 목록 조회 쿼리 수 | 약 2,400개 | 4~5개 |
| 응답 시간 | 1,084ms | 66ms |
| 메모리 | 21MB | 6MB |

보조 설명:

- 이 수치는 `docs/troubleshooting/care/care-request-n-plus-one-analysis.md`의 기존 측정값이다.
- 측정 맥락은 1004개 케어 요청 데이터 기준의 목록 조회 최적화다.
- 페이징 API, 검색 API, 지도 API는 경로가 다르므로 동일 수치로 단정하지 않는다.

### 2-4. 데이터 흐름 카드

문구:

시퀀스 다이어그램은 도메인별 페이지에 반복하지 않고 통합 흐름 페이지로 분리한다. Care 탭에서는 요청 생성, 지도 조회, 채팅 거래 확정, 완료/취소 지급·환불 흐름을 보고, Chat 절에서는 방 생성, 메시지, 읽음 처리, 거래 확정 진입점을 별도로 확인하게 한다.

내부 링크:

- `/domains/flows?tab=care`
- `/domains/flows?tab=care&seq=chat`

---

## 3. `section#design` - 기술 결정

첨부 JSX의 A~E 구성은 적절하다. 아래 문구와 스니펫 기준으로 다듬으면 된다.

### A. 거래 확정 Race Condition

핵심 문구:

거래 확정은 두 사용자가 같은 채팅방에서 거의 동시에 버튼을 누를 수 있는 구간이다. 둘 중 한 요청만 상태 전이를 보고 다른 요청은 이전 상태를 보는 식으로 엇갈리면, `CareApplication` 생성/승인이나 `IN_PROGRESS` 전환이 누락될 수 있다. 현재 구현은 `Conversation`을 비관적 락으로 조회해 확정 로직을 순차 처리하고, 참여자 2명이 모두 `dealConfirmed=true`가 되었을 때만 매칭 상태를 바꾼다.

코드 스니펫 후보:

```java
Conversation conversation = conversationRepository.findByIdWithLock(conversationIdx)
        .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

participant.setDealConfirmed(true);
participant.setDealConfirmedAt(LocalDateTime.now());
participantRepository.save(participant);

boolean allConfirmed = allParticipants.stream()
        .allMatch(p -> Boolean.TRUE.equals(p.getDealConfirmed()));

if (allConfirmed && allParticipants.size() == 2) {
    // CareApplication 승인/생성
    // CareRequest IN_PROGRESS 전환
    // 에스크로 생성 시도
}
```

에스크로 한계 문구:

- `createEscrow()` 실패는 catch 후 로그만 남긴다.
- 운영 정책상 결제 실패 시 매칭도 실패해야 한다면 이 지점은 트랜잭션 정책을 다시 잡아야 한다.

근거:

- `backend/main/java/com/linkup/Petory/domain/chat/service/ConversationService.java`
- `docs/troubleshooting/care/care-deal-confirmation-race-condition.md`
- `docs/domains/care.md`

### B. 목록 N+1 최적화

핵심 문구:

Care 목록은 단순 요청 목록이 아니라 요청자, 반려동물, 지원자 수, 파일, 예방접종 정보까지 함께 보여줘야 한다. 처음 구조에서는 `CareRequest`마다 `CareApplication`, File, PetVaccination 조회가 반복되며 약 2,400개 쿼리까지 늘어났다. 현재는 fetch join, file batch 조회, `@BatchSize`를 조합해 쿼리 수를 크게 줄였다.

코드 스니펫 후보:

```java
// 핵심 연관은 목록 쿼리에서 함께 로딩
@Query("SELECT DISTINCT cr FROM CareRequest cr " +
       "JOIN FETCH cr.user u " +
       "LEFT JOIN FETCH cr.pet " +
       "LEFT JOIN FETCH cr.applications " +
       "WHERE cr.isDeleted = false ...")
List<CareRequest> findAllActiveRequests();
```

주의:

- 페이징 목록은 `Page` + OneToMany fetch join 제약이 있어 batch 전략이 섞인다.
- 첨부 JSX의 "총 4~5개로 수렴"은 기존 비페이징 N+1 측정 맥락에서 쓰고, 모든 목록 경로의 보장값처럼 표현하지 않는다.

근거:

- `backend/main/java/com/linkup/Petory/domain/care/repository/SpringDataJpaCareRequestRepository.java`
- `docs/troubleshooting/care/care-request-n-plus-one-analysis.md`
- `docs/refactoring/fetch-optimization/care/Fetch 전략 개선 (Fetch Join vs Batch Size).md`

### C. 완료/취소 처리 일원화

핵심 문구:

Care 상태 변경은 사용자 수동 완료, 사용자 취소, 스케줄러 자동 완료가 모두 같은 `updateStatus()`를 거치도록 정리되어 있다. `COMPLETED` 전환에서는 `completedAt`이 기록되고, HOLD 상태 에스크로가 있으면 Payment에 제공자 지급을 요청한다. `CANCELLED` 전환에서는 HOLD 에스크로를 요청자에게 환불한다.

코드 스니펫 후보:

```java
if (oldStatus != CareRequestStatus.COMPLETED && newStatus == CareRequestStatus.COMPLETED) {
    PetCoinEscrow escrow = petCoinEscrowService.findByCareRequest(request);
    if (escrow != null && escrow.getStatus() == EscrowStatus.HOLD) {
        petCoinEscrowService.releaseToProvider(escrow);
    }
}

if (newStatus == CareRequestStatus.CANCELLED) {
    PetCoinEscrow escrow = petCoinEscrowService.findByCareRequest(request);
    if (escrow != null && escrow.getStatus() == EscrowStatus.HOLD) {
        petCoinEscrowService.refundToRequester(escrow);
    }
}
```

근거:

- `backend/main/java/com/linkup/Petory/domain/care/service/CareRequestService.java`
- `backend/main/java/com/linkup/Petory/domain/care/service/CareRequestScheduler.java`
- `backend/main/java/com/linkup/Petory/domain/payment/service/PetCoinEscrowService.java`

### D. 서비스 레벨 권한 검증

핵심 문구:

Care는 상태 변경과 삭제가 곧 결제 흐름으로 이어질 수 있어 컨트롤러 annotation만으로 끝내지 않고 서비스 내부에서 요청자/제공자/관리자 조건을 다시 확인한다. 요청 수정·삭제는 요청자 또는 관리자만 가능하고, 상태 변경은 요청자 또는 `ACCEPTED` 제공자만 가능하다. 스케줄러는 `currentUserId=null`로 호출해 시스템 작업으로 처리한다.

코드 스니펫 후보:

```java
boolean isRequester = request.getUser().getIdx().equals(currentUserId);
boolean isAcceptedProvider = request.getApplications().stream()
        .anyMatch(app -> app.getStatus() == CareApplicationStatus.ACCEPTED
                && app.getProvider().getIdx().equals(currentUserId));

if (!isRequester && !isAcceptedProvider) {
    throw CareForbiddenException.ownerOrApprovedProvider();
}
```

생성 권한 설명:

- `createCareRequest()`는 body userId가 아니라 인증 사용자 PK를 컨트롤러에서 주입한다.
- `petIdx`가 있으면 요청자 소유 펫인지 서비스에서 확인한다.

근거:

- `backend/main/java/com/linkup/Petory/domain/care/controller/CareRequestController.java`
- `backend/main/java/com/linkup/Petory/domain/care/service/CareRequestService.java`

### E. 위치 기반 조회

핵심 문구:

통합 지도에서 Care 레이어는 위치 기반 요청 조회를 사용한다. API는 `lat`, `lng`, `radius`, `limit`을 받고, limit을 1~500 사이로 보정한다. repository는 위도/경도 bounding box로 후보를 먼저 줄인 뒤 Haversine 거리 조건을 적용하고, 지도에 표시할 수 있는 `OPEN`, `IN_PROGRESS` 요청만 반환한다.

코드 스니펫 후보:

```java
public List<CareRequestDTO> getNearby(double lat, double lng, double radiusKm, int limit) {
    int effectiveLimit = Math.min(Math.max(limit, 1), 500);
    return careRequestRepository.findNearby(lat, lng, radiusKm, effectiveLimit)
            .stream()
            .map(careRequestConverter::toDTO)
            .toList();
}
```

근거:

- `backend/main/java/com/linkup/Petory/domain/care/service/CareRequestService.java`
- `backend/main/java/com/linkup/Petory/domain/care/repository/SpringDataJpaCareRequestRepository.java`
- `frontend/src/api/unifiedMapApi.js`

---

## 4. `section#limits` - 한계 & 다음 개선

문구:

Care 도메인은 Race Condition과 목록 N+1처럼 바로 장애나 병목으로 이어질 수 있는 문제를 먼저 해결했지만, Payment 원자성과 인증 기준에는 아직 정리할 부분이 남아 있다.

목록:

- 에스크로 원자성: `IN_PROGRESS` 저장 후 에스크로 생성 실패를 로그로만 남겨 상태 전이와 결제가 원자적으로 묶이지 않는다.
- 리뷰 시점: `COMPLETED`가 아닌 `CareApplicationStatus.ACCEPTED` 기준으로 리뷰 작성이 가능하다.
- 댓글 작성: `CareRequestCommentService.addComment()`는 DTO의 `userId`로 사용자를 조회한다. 생성 요청처럼 인증 사용자 주입 방식으로 맞출 여지가 있다.
- 리뷰 작성: `CareReviewService.createReview()`는 DTO의 `reviewerId`, `revieweeId`를 기준으로 검증한다. 인증 사용자 기준으로 reviewer를 고정하는 개선 여지가 있다.
- 스케줄러 상태 변경: `updateStatus(idx, "COMPLETED", null)` 경로는 시스템 작업이라 권한 검증을 생략한다. 의도된 설계지만 페이지에서는 일반 사용자 경로와 구분한다.
- 페이징 목록: `Page`와 OneToMany fetch join 제약 때문에 모든 연관을 한 번에 fetch join하지 않고 batch 전략을 병행한다.
- 댓글 목록: 현재 `getComments()`는 댓글별 attachment 조회가 반복될 수 있어 댓글 수가 커지면 batch 조회 개선 여지가 있다.
- API 인증: controller상 공개 GET처럼 보이는 목록·상세·검색도 실제 접근 가능 여부는 `SecurityConfig /api/**` 인증 규칙 영향을 함께 봐야 한다.

---

## 5. `section#docs` - 관련 페이지

### 내부 링크

- `/domains/flows?tab=care` - Care 요청·채팅·결제 시퀀스
- `/domains/flows?tab=care&seq=chat` - Chat과 Care 연결 흐름
- `/domains/care/optimization` - N+1, Fetch Join, Before/After
- `/domains/care/refactoring` - Care/Payment 연결 리팩토링
- `/domains/payment` - 펫코인, 에스크로, 지급/환불
- `/domains/chat` - 거래 확정 진입점
- `/domains/file` - 댓글/펫 파일 연결
- `/domains/notification` - 케어 댓글 알림

### GitHub 링크 상수 후보

첨부 JSX의 세 상수는 유지 가능하다. 아키텍처와 핵심 소스 링크를 추가하면 Care/Payment/Chat 경계가 더 명확하다.

```javascript
const PETORY_CARE_DOMAIN_DOC =
  'https://github.com/makkong1/Petory/blob/main/docs/domains/care.md';
const PETORY_CARE_ARCH_DOC =
  'https://github.com/makkong1/Petory/blob/main/docs/architecture/care/%ED%8E%AB%20%EC%BC%80%EC%96%B4%20%26%20%EB%A7%A4%EC%B9%AD%20%EC%95%84%ED%82%A4%ED%85%8D%EC%B2%98.md';
const PETORY_CARE_N1_DOC =
  'https://github.com/makkong1/Petory/blob/main/docs/troubleshooting/care/care-request-n-plus-one-analysis.md';
const PETORY_CARE_RACE_DOC =
  'https://github.com/makkong1/Petory/blob/main/docs/troubleshooting/care/care-deal-confirmation-race-condition.md';
const PETORY_CARE_SERVICE =
  'https://github.com/makkong1/Petory/blob/main/backend/main/java/com/linkup/Petory/domain/care/service/CareRequestService.java';
const PETORY_CONVERSATION_SERVICE =
  'https://github.com/makkong1/Petory/blob/main/backend/main/java/com/linkup/Petory/domain/chat/service/ConversationService.java';
const PETORY_ESCROW_SERVICE =
  'https://github.com/makkong1/Petory/blob/main/backend/main/java/com/linkup/Petory/domain/payment/service/PetCoinEscrowService.java';
```

관련 문서:

- `docs/domains/care.md`
- `docs/domains/payment.md`
- `docs/architecture/care/펫 케어 & 매칭 아키텍처.md`
- `docs/architecture/care/펫케어 코인 관련 흐름.md`
- `docs/architecture/payment/펫코인 결제 아키텍처.md`
- `docs/troubleshooting/care/care-request-n-plus-one-analysis.md`
- `docs/troubleshooting/care/care-deal-confirmation-race-condition.md`
- `docs/troubleshooting/care/care-request-paging-n-plus-one.md`
- `docs/refactoring/fetch-optimization/care/Fetch 전략 개선 (Fetch Join vs Batch Size).md`
- `docs/refactoring/care/care-payment-refactoring-2026-04-14.md`
- `docs/refactoring/care/care-payment-code-review-2026-04-14.md`

---

## 6. 첨부 `CareDomainV2.jsx` 반영 체크

그대로 진행해도 되는 부분:

- 섹션 순서: `pillars -> intro -> design -> limits -> docs`
- Care를 Chat/Payment와 연결된 도메인으로 설명하는 방향
- Race Condition, N+1, 에스크로, 상태 처리 일원화, 위치 조회를 핵심 축으로 잡은 구성
- 성능 표의 수치와 측정 조건 문구
- `/domains/flows?tab=care`, `/domains/flows?tab=care&seq=chat` 링크
- "에스크로 생성 실패 시 거래 확정 상태는 유지될 수 있음" 한계
- "리뷰는 COMPLETED가 아니라 ACCEPTED 기준" 한계

수정하고 진행할 부분:

- 한계 섹션의 `"createCareRequest·댓글·리뷰 작성은 요청 바디 userId를 신뢰"` 문구는 고친다. 생성은 인증 사용자 주입으로 개선되어 있고, body ID 의존은 댓글/리뷰로 좁혀 쓴다.
- 목록 N+1 설명에서 `"JOIN FETCH로 요청자·반려동물·지원자 수·파일"`처럼 쓰면 안 된다. 파일은 batch 조회, 예방접종은 `@BatchSize` 맥락이다.
- 성능 표는 기존 측정 맥락으로만 사용하고, 현재 페이징/검색/지도 전체 경로의 보장값처럼 쓰지 않는다.
- 댓글 목록은 현재 댓글별 attachment 조회가 반복될 수 있으므로 "댓글까지 완전 배치 최적화 완료"처럼 표현하지 않는다.
- 상태 변경 스케줄러의 `currentUserId=null`은 "권한 검증 누락 버그"보다는 "시스템 작업 경로라 검증 생략, 일반 사용자 경로와 구분 필요"로 표현한다.
