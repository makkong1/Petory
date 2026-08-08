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

| 구분                 | 주요 파일                                                                                            |
| -------------------- | ---------------------------------------------------------------------------------------------------- |
| 케어 요청 API        | `backend/main/java/com/linkup/Petory/domain/care/controller/CareRequestController.java`              |
| 케어 댓글 API        | `backend/main/java/com/linkup/Petory/domain/care/controller/CareRequestCommentController.java`       |
| 케어 리뷰 API        | `backend/main/java/com/linkup/Petory/domain/care/controller/CareReviewController.java`               |
| 관리자 API           | `backend/main/java/com/linkup/Petory/domain/admin/controller/AdminCareRequestController.java`        |
| 관리자 facade        | `backend/main/java/com/linkup/Petory/domain/admin/service/AdminCareAndMeetupFacade.java`             |
| 케어 요청 서비스     | `backend/main/java/com/linkup/Petory/domain/care/service/CareRequestService.java`                    |
| 케어 댓글 서비스     | `backend/main/java/com/linkup/Petory/domain/care/service/CareRequestCommentService.java`             |
| 케어 리뷰 서비스     | `backend/main/java/com/linkup/Petory/domain/care/service/CareReviewService.java`                     |
| 자동 완료 스케줄러   | `backend/main/java/com/linkup/Petory/domain/care/service/CareRequestScheduler.java`                  |
| 채팅 거래 확정       | `backend/main/java/com/linkup/Petory/domain/chat/service/ConversationService.java`                   |
| Payment 연동         | `backend/main/java/com/linkup/Petory/domain/payment/service/PetCoinEscrowService.java`               |
| 케어 요청 repository | `backend/main/java/com/linkup/Petory/domain/care/repository/SpringDataJpaCareRequestRepository.java` |
| 프론트 케어 API      | `frontend/src/api/careRequestApi.js`                                                                 |
| 프론트 리뷰 API      | `frontend/src/api/careReviewApi.js`                                                                  |
| 프론트 관리자 API    | `frontend/src/api/careRequestAdminApi.js`                                                            |

## 3. 핵심 엔티티

### CareRequest

케어 요청의 중심 엔티티다.

| 필드                               | 의미                                            |
| ---------------------------------- | ----------------------------------------------- |
| `idx`                              | 케어 요청 PK                                    |
| `user`                             | 요청자                                          |
| `pet`                              | 연결된 펫, 선택값                               |
| `title`, `description`             | 요청 제목/본문                                  |
| `date`                             | 고정 일정 또는 희망 일정                        |
| `scheduleMode`                     | `FIXED`, `FLEXIBLE_CHAT`                        |
| `estimatedDurationMinutes`         | 예상 돌봄 시간, 선택값                          |
| `offeredCoins`                     | 요청자가 제시한 코인 금액                       |
| `status`                           | `OPEN`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED` |
| `latitude`, `longitude`, `address` | 지도 표출/반경 검색용 위치                      |
| `isDeleted`, `deletedAt`           | soft delete 상태                                |
| `completedAt`                      | 완료 시각, 통계 집계용                          |
| `requesterCompletedAt`             | 요청자가 이행 완료를 확인한 시각 (`NULL` = 미확인) |
| `providerCompletedAt`              | 제공자가 이행 완료를 확인한 시각 (`NULL` = 미확인) |
| `offeredCoinsUpdatedAt`            | 제시 금액이 마지막으로 바뀐 시각. 표시용        |

상태를 `COMPLETED`로 변경할 때 `transitionTo()`가 `completedAt`을 기록한다.

`transitionTo()`는 허용된 전이만 통과시킨다. `COMPLETED`/`CANCELLED`는 종착이라 어디로도 가지 않는다.
같은 상태로의 재요청은 예외 없이 무시된다(재시도 안전).

```
OPEN        → IN_PROGRESS | CANCELLED
IN_PROGRESS → COMPLETED   | CANCELLED
COMPLETED   → (없음)
CANCELLED   → (없음)
```

> 가드가 없던 시절 `COMPLETED → CANCELLED`가 통했고, 그때 에스크로는 이미 `RELEASED`라 환불이
> 스킵되어 "상태는 취소인데 돈은 제공자에게" 남는 불일치가 생겼다.
> → `docs/refactoring/care/care-settlement-integrity-2026-08-08.md`

### CareApplication

케어 요청과 제공자의 매칭 기록이다.

| 필드          | 의미                              |
| ------------- | --------------------------------- |
| `careRequest` | 대상 케어 요청                    |
| `provider`    | 케어 제공자                       |
| `status`      | `PENDING`, `ACCEPTED`, `REJECTED` |
| `message`     | 지원 메시지                       |

`care_request_idx + provider_idx` unique 제약이 있다.

현재 사용자-facing Care API에는 별도 지원 신청 endpoint가 없고, 채팅 거래 확정 흐름에서 `CareApplication`을 생성하거나 `ACCEPTED`로 변경한다.

### CareRequestComment

케어 요청 댓글이다. `SERVICE_PROVIDER` 역할 사용자만 작성할 수 있다. 삭제는 soft delete다.

### CareReview

요청자가 제공자에게 작성하는 리뷰다. 하나의 `CareApplication`과 작성자 조합에 대해 중복 리뷰를 막는다.

## 4. 사용자 케어 요청 API

### `/api/care-requests`

| API                                                     | 인증                | 설명                       |
| ------------------------------------------------------- | ------------------- | -------------------------- |
| `GET /api/care-requests/nearby?lat&lng&radius&limit`    | 보안 설정 확인 필요 | 지도용 근처 케어 요청 조회 |
| `GET /api/care-requests?status&location&page&size`      | 보안 설정 확인 필요 | 케어 요청 목록 페이징 조회 |
| `GET /api/care-requests/{id}`                           | 보안 설정 확인 필요 | 케어 요청 상세 조회        |
| `POST /api/care-requests`                               | 인증 필요           | 케어 요청 생성             |
| `PUT /api/care-requests/{id}`                           | 인증 필요           | 케어 요청 수정             |
| `DELETE /api/care-requests/{id}`                        | 인증 필요           | 케어 요청 soft delete      |
| `GET /api/care-requests/my-requests`                    | 인증 필요           | 내 케어 요청 목록          |
| `PATCH /api/care-requests/{id}/status?status=...`       | 인증 필요           | 케어 요청 상태 변경. `COMPLETED`는 관리자만 |
| `POST /api/care-requests/{id}/complete`                 | 인증 필요           | 이행 완료 확인. 양쪽이 확인해야 정산 |
| `GET /api/care-requests/search?keyword&page&size`       | 보안 설정 확인 필요 | 케어 요청 검색             |

생성 시 컨트롤러가 `AuthenticatedUserIdResolver`로 현재 로그인 사용자 PK를 구해 `dto.userId`에 넣는다. 즉, 요청 생성은 클라이언트가 보낸 userId를 신뢰하지 않는다.

## 5. 케어 요청 생성

생성 흐름:

1. 현재 로그인 사용자 조회
2. 이메일 인증 확인
3. `scheduleMode`가 없으면 `FIXED` 사용
4. 제목, 설명, 일정, 예상 시간, 코인, 주소, 좌표 저장
5. `petIdx`가 있으면 펫 존재와 소유자 확인
6. `status=OPEN`으로 저장
7. **`offeredCoins`를 에스크로에 잡는다**(`holdForRequest`) — 비관적 락 안에서 잔액을 검사하고 차감한다
8. `CareRequestCreatedEvent` 발행

7번이 실패하면 같은 트랜잭션이라 **글도 남지 않는다.** 잔액이 모자라면 요청 자체가 등록되지 않는다.

이메일 인증 purpose:

- `PET_CARE`

주의:

- 서버는 `offeredCoins >= 100` DTO validation을 수행하고, 잔액은 에스크로 차감 시 비관적 락 안에서 검사한다.
  (이전에는 잔액을 "확인"만 하고 잡지 않아, 확인과 실제 차감 사이에 잔액을 쓰면 거래 확정 순간에 깨지는 TOCTOU 였다.)
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

FULLTEXT 인덱스(`idx_carerequest_title_desc`)는 `ngram` 파서를 쓴다(V9). 기본 InnoDB 파서는 `innodb_ft_min_token_size=3`이라 `산책`·`병원` 같은 2글자 한글 단어를 색인하지 못하고, 공백 기준 토큰화라 `강아지산책` 안의 `산책`을 부분 매칭하지 못한다. ngram은 2글자 단위로 분해해 이 갭을 메운다(board·meetup과 동일). V2에서 검색 500(인덱스 부재)을 먼저 막고, ngram 전환은 V9로 분리했다.

검색도 삭제되지 않은 요청과 활성 작성자만 포함한다. `JpaCareRequestAdapter`는 검색 결과 idx를 기준으로 연관 엔티티를 다시 fetch하여 DTO 변환 시 N+1을 줄인다.

### 지도 근처 조회

`GET /api/care-requests/nearby`는 `lat`, `lng`, `radius`(기본 5.0km), `limit`(선택)을 받는다.

정책:

- **결과 상한은 `NearbySearchPolicy`가 반경으로 정한다** (2km까지 100 / 5km 200 / 10km 350 / 20km 500 / 그 외 800).
  `limit` 파라미터를 주면 그 값과 정책값 중 **작은 쪽**을 쓴다. 안 주면 정책값이다.
  예전에는 프론트 `ZOOM_LIMIT_TABLE`이 **줌 레벨** 기준으로 20~400을 보냈는데, 쿼리가 읽을 행 수를
  정하는 건 줌이 아니라 반경이라 둘이 어긋났다(반경 5km로 두고 지도만 축소하면 상한이 400으로 뜀).
- `latitude IS NOT NULL`인 요청만 포함한다.
- `OPEN`, `IN_PROGRESS` 상태만 포함한다.
- 요청자 `status=ACTIVE`, `isDeleted=false`인 요청만 포함한다.
- **정렬은 거리 오름차순 + `idx` 오름차순이다** (2026-07-31 지도 반경검색 통일).
  예전에는 `created_at DESC`였다. `created_at`에는 정렬용 인덱스(`idx_carerequest_deleted_created`)가
  있어서, 매치가 `LIMIT`을 채우면 옵티마이저가 "정렬 인덱스를 역주행하다 멈추는" 계획을 골라
  **공간 인덱스를 아예 쓰지 않았다**(실측: 반경 5km는 SPATIAL 208행, 10km부터 created_at 인덱스 1,622행).
  지도 네 도메인 중 care만 이 탈출구가 있어 계획이 혼자 다르게 뒤집혔다. 거리순으로 통일하면
  정렬용 인덱스가 없어져 계획이 예측 가능해지고, 지도에서는 "가까운 순"이 의미상으로도 맞다.
  `idx ASC`는 거리 동점 시 순서를 고정해 지도를 옮겨도 결과가 흔들리지 않게 한다.
- `ST_Within`으로 사각형 범위를 먼저 좁힌 뒤 `ST_Distance_Sphere`로 정확한 반경을 적용한다.
  - `geo_point`(POINT, SRID 4326)에 SPATIAL 인덱스를 걸었고, 값은 BEFORE INSERT/UPDATE 트리거가 위·경도에서 자동으로 채운다.
    (`meetup`·`locationservice`와 동일한 패턴. 트리거로 채우므로 엔티티에 필드가 없고 `ddl-auto=validate`를 통과한다.)
  - 예전에는 `latitude`/`longitude` BETWEEN + Haversine이었으나 **인덱스를 타지 못해 풀스캔**이었다.
    B-tree는 범위 조건을 선두에서 하나만 쓸 수 있어 `longitude`가 걸러지지 않았고, 옵티마이저가 위·경도를
    독립 조건으로 곱해 선택도를 208배 오판했다(예상 3.77행 / 실제 783행). → 3,000행 풀스캔에서 208행으로.

## 7. 수정과 삭제

수정:

- 작성자 또는 `ADMIN`/`MASTER`만 가능하다.
- **`OPEN` 상태에서만 가능하다. 관리자도 예외가 아니다** — 관리자가 우회할 수 있으면 가드가 아니다.
  (이전에는 상태 가드가 없어 이미 `COMPLETED`된 케어의 날짜·장소·펫을 사후에 바꿀 수 있었다.)
- 제목, 설명, 날짜, 일정 모드, 예상 시간, 주소, 좌표를 부분 수정한다.
- **`offeredCoins`도 수정한다.** 목표 금액을 받아 에스크로와의 차액만 정산한다(증액=추가 차감, 감액=환불).
  증분이 아니라 목표값이라 같은 요청이 두 번 와도 두 번째는 차액이 0이 되어 **멱등키 없이 재시도 안전**하다.
  변경 시각(`offeredCoinsUpdatedAt`)을 함께 남긴다.
- `petIdx`가 있으면 해당 펫이 요청자 소유인지 확인하고 연결한다.
- `petIdx == null`이고 기존 펫이 있으면 펫 연결을 해제한다.

삭제:

- 작성자 또는 `ADMIN`/`MASTER`만 가능하다.
- **`OPEN` 상태에서만 가능하다.** 상대가 있는 계약을 한쪽이 지우면 보관 코인의 귀속이 사라진다.
- **보관 중인 에스크로를 요청자에게 환불한 뒤** `softDelete()`로 `isDeleted=true`, `deletedAt=now` 처리한다.
  내부 코인은 플랫폼이 돌려주지 않으면 회수 수단이 없어, 환불 경로를 빠뜨리면 그대로 묶인다.

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
- 일반 사용자가 `COMPLETED` 또는 `CANCELLED`로 전환할 때 요청자나 `ACCEPTED` 제공자 중 제재 사용자가 있으면 거절한다.
- **`COMPLETED`로의 전환은 관리자만 가능하다.** 일반 사용자의 완료는 아래 양방향 확인 경로로만 이뤄진다.
  두 경로가 모두 열려 있으면 양방향 확인 가드가 그대로 우회되기 때문이다.
  관리자를 남긴 것은 당사자 합의가 불가능한 분쟁 조정을 위해서다.

### 이행 완료 확인 (`POST /api/care-requests/{id}/complete`)

요청자와 제공자가 각자 호출하고, **양쪽이 모두 확인해야** `COMPLETED`가 되며 그때 정산한다.

1. `CareRequest`를 **비관적 락**으로 조회 (`findByIdForUpdate`)
2. `IN_PROGRESS`인지 확인
3. 호출자가 요청자인지 `ACCEPTED` 제공자인지 판별 (아니면 403)
4. 자기 쪽 확인 시각 기록. 이미 확인했으면 덮어쓰지 않는다(재시도 안전)
5. 양쪽 다 확인됐으면 `transitionTo(COMPLETED)` + `releaseToProvider()`

> **왜 비관적 락인가.** 양쪽이 동시에 누르면 둘 다 "상대도 확인했나"를 읽고 각자 정산으로 넘어갈 수 있다.
> 읽고 판단해서 쓰는 구간이라 원자적 UPDATE로 대체되지 않는다.
>
> **왜 필요했나.** 이전에는 `updateStatus`를 요청자 **또는** 제공자 아무나 호출할 수 있었고
> `COMPLETED`가 되는 순간 정산돼, **제공자가 혼자 눌러 요청자 동의 없이 돈을 가져갈 수 있었다.**

`COMPLETED` 처리(관리자 경로):

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
10. `PetCoinEscrowService.assignProvider()`로 **이미 보관 중인 에스크로에 지급 대상을 배정**

3단계 앞에 금액 합의 검사가 있다. 확정은 양쪽이 따로 누르고 금액은 `OPEN` 동안 바뀔 수 있어,
아무 장치가 없으면 제공자는 5,000에 동의하고 요청자는 1,000에 동의한 채 계약이 성립한다.

| 장치 | 막는 것 |
| --- | --- |
| `expectedAmount` (확정 요청 파라미터) | **지금 내가** 화면에서 보고 동의하는 값이 실제와 같은가 → 다르면 409 |
| `ConversationParticipant.confirmedOfferedCoins` | **이미 있는 동의**가 현재 금액과 같은 금액에 대한 것인가 → 다르면 무효화하고 다시 받는다 |

무효화를 chat 쪽에서 하는 이유는 도메인 참조 방향이다. `dealConfirmed`는 chat에 있고 현재 참조는
`chat → care` 단방향이라, care가 participant를 건드리면 순환이 된다. care는 금액만 노출한다.

중요한 현재 동작:

- **코인 차감은 이 시점이 아니라 요청 등록 시점에 이미 일어났다.** 확정에서 하는 일은 지급 대상 배정뿐이라
  여기서 잔액 부족으로 깨지는 일이 없다. (§5 참고)
- `assignProvider()` 실패 시 예외를 그대로 전파한다. 이전에는 `try/catch`로 삼키고 "확정은 진행한다"고
  주석까지 달아뒀지만, `REQUIRED`로 같은 트랜잭션에 합류하므로 실패가 rollback-only를 남겨
  **그 동작은 애초에 불가능했다** — 삼켜도 바깥 커밋에서 `UnexpectedRollbackException`이 날 뿐이었다.
  전파로 바꾼 실효는 롤백을 만드는 게 아니라 원인을 남기는 것이다(HTTP 500 → 원인 명시).
- ⚠️ **5~10단계(CARE_REQUEST 분기)는 현재 운영 흐름에서 도달 불가능하다.** 채팅방을 만드는 유일한 경로인 `ConversationService.createCareRequestConversation()`은 항상 `relatedType = CARE_APPLICATION`으로 방을 생성하며, 코드 전체에서 `RelatedType.CARE_REQUEST`는 비교문에만 있고 값으로 대입되는 곳이 없다. 실제로 생성되는 `CARE_APPLICATION` 분기는 로그만 남기고 상태 전이·에스크로 생성을 하지 않는다. 자세한 내용은 [chat.md §6.1·§9](chat.md)를 본다.

## 10. 만료 요청 정리 스케줄러

`CareRequestScheduler`가 예정일이 지난 요청을 정리한다.

| 상태 | 처리 |
| --- | --- |
| `OPEN` | `CANCELLED`로 전환. 성사되지 않은 요청이므로 보관 코인은 환불된다 |
| `IN_PROGRESS` | **건드리지 않는다.** 경고 로그만 남긴다 |

> **왜 `IN_PROGRESS`를 두는가.** 이전에는 이것도 `COMPLETED`로 바꿨고, `updateStatus`가 `COMPLETED`에서
> 에스크로를 제공자에게 지급한다. 즉 **아무도 완료를 누르지 않아도 예정일만 지나면 매시간 돈이 넘어갔다.**
> 실제 이행 여부는 당사자만 알 수 있으므로 자동 정산하지 않는다.

스케줄:

- 매 시간 정각
- 매일 자정 (시간별 메서드를 그대로 호출한다)

대상:

- `date < now`
- `status IN (OPEN, IN_PROGRESS)`

처리:

- 스케줄러 자체에는 큰 트랜잭션을 걸지 않는다.
- 조회 시 요청자와 지원 제공자를 함께 fetch한다.
- 요청자 또는 `ACCEPTED` 제공자가 `isSanctioned()` 상태이면 자동 처리하지 않고 로그만 남긴다.
- `IN_PROGRESS`는 스킵하고 경고 로그를 남긴다.
- `OPEN`은 `careRequestService.updateStatus(idx, "CANCELLED", null)`을 호출한다.
- 개별 요청 실패는 로그로 남기고 다음 요청을 계속 처리한다.
- 취소 처리도 일반 상태 변경과 같은 Payment 환불 경로를 탄다.

## 11. 케어 댓글 API

### `/api/care-requests/{careRequestId}/comments`

| API                                                              | 설명             |
| ---------------------------------------------------------------- | ---------------- |
| `GET /api/care-requests/{careRequestId}/comments`                | 댓글 목록 조회   |
| `POST /api/care-requests/{careRequestId}/comments`               | 댓글 작성        |
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

| API                                                  | 인증                | 설명                           |
| ---------------------------------------------------- | ------------------- | ------------------------------ |
| `POST /api/care-reviews`                             | 인증 필요           | 리뷰 작성                      |
| `GET /api/care-reviews/reviewee/{revieweeIdx}`       | 보안 설정 확인 필요 | 특정 사용자가 받은 리뷰 목록   |
| `GET /api/care-reviews/reviewer/{reviewerIdx}`       | 보안 설정 확인 필요 | 특정 사용자가 작성한 리뷰 목록 |
| `GET /api/care-reviews/average-rating/{revieweeIdx}` | 보안 설정 확인 필요 | 특정 사용자의 평균 평점        |

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

| API                                                       | 설명                         |
| --------------------------------------------------------- | ---------------------------- |
| `GET /api/admin/care-requests?status&deleted&q&page&size` | 관리자 케어 요청 페이징 조회 |
| `GET /api/admin/care-requests/{id}`                       | 관리자 단건 조회             |
| `PATCH /api/admin/care-requests/{id}/status?status=...`   | 상태 변경                    |
| `POST /api/admin/care-requests/{id}/delete`               | soft delete                  |
| `POST /api/admin/care-requests/{id}/restore`              | 복구                         |

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

## 15. 제재 정책 (2026-06-28~)

> 코드 기준: `CareRequestService`, `CareRequestCommentService`, `CareRequestScheduler`, `UserSanctionCareEventListener`, `SpringDataJpaCareRequestRepository`

### 실시간 차단 (요청 진입 시점)

| 시점                              | 적용 대상                                             | 동작                                               |
| --------------------------------- | ----------------------------------------------------- | -------------------------------------------------- |
| `POST /api/care-requests`         | SUSPENDED·BANNED 요청자                               | `CareForbiddenException.sanctioned()` (403)        |
| 케어 댓글 작성                    | SUSPENDED·BANNED 사용자                               | `CareForbiddenException.sanctioned()` (403)        |
| 케어 상세 조회                    | 제재 요청자의 `OPEN`/`CANCELLED` 사전 매칭 요청       | 일반 사용자에게 404처럼 비노출. 관리자는 조회 가능 |
| 상태 변경 `COMPLETED`/`CANCELLED` | 제재 요청자 또는 제재 `ACCEPTED` 제공자가 포함된 케어 | 일반 사용자 상태 변경 거절                         |

### 제재 이벤트 후속 처리 (`UserSanctionAppliedEvent`)

- **BANNED** 시에만 이벤트 리스너(`UserSanctionCareEventListener`)가 실행된다.
- `AFTER_COMMIT` 단계에서 `REQUIRES_NEW` 트랜잭션으로 실행된다.
- 해당 사용자의 `OPEN` 상태 케어 요청을 모두 `CANCELLED`로 변경한다.
- SUSPENDED 사용자의 OPEN 케어는 취소하지 않는다. 대신 `findNearbyCareRequests` 쿼리의 `INNER JOIN users u ON u.idx = cr.user_idx AND u.status = 'ACTIVE'` 조건으로 노출 목록에서 자동 제외된다.

### 자동 완료 스케줄러 예외

`CareRequestScheduler`가 만료 케어를 자동 완료로 전환할 때 요청자 또는 `ACCEPTED` 제공자가 `isSanctioned()` 상태이면 해당 케어를 건너뛴다. `SUSPENDED` 요청자의 `OPEN` 케어도 해제 후 재노출 가능성을 보존하기 위해 자동 완료하지 않는다.

## 16. 한계와 개선

- 별도 사용자-facing 케어 지원 신청/승인 API가 없다. 현재 매칭 전이는 채팅 거래 확정에 강하게 묶여 있다.
- `confirmCareDeal()`의 `RelatedType.CARE_REQUEST` 분기(상태 전이·에스크로 생성)는 채팅방 생성 경로가 항상 `CARE_APPLICATION`만 만들기 때문에 현재 도달 불가능하다(§9 참고). `CARE_REQUEST`/`CARE_APPLICATION` 연결 정책을 정리해야 한다.
- 채팅 거래 확정에서 에스크로 생성 실패를 롤백하지 않는다.
- 케어 댓글 작성과 리뷰 작성은 요청 DTO의 사용자 ID를 사용하고, 인증 사용자와의 일치 검증이 약하다.
- 리뷰는 완료 상태가 아니라 `CareApplication.ACCEPTED`만 요구한다.
- 케어 수정 DTO는 `petIdx` 누락과 명시적 null을 구분하지 못해 기존 펫 연결을 해제할 수 있다.
- 케어 수정은 soft-deleted 요청 여부를 별도 차단하지 않는다.
- 댓글 첨부파일 조회는 댓글별 개별 조회라 N+1 가능성이 있다.
- 가격 가이드 문서에 있는 시간/체급 기반 최소 코인 정책은 현재 서버 코드에 구현되어 있지 않다.
- 스케줄러가 `OPEN` 상태의 만료 요청도 `COMPLETED`로 전환한다. 단, 제재 당사자가 포함된 요청은 건너뛴다. 실제 매칭되지 않은 요청을 완료로 볼지 정책 확인이 필요하다.

## 17. 관련 문서

- [펫 케어 & 매칭 아키텍처](../architecture/care/펫 케어 & 매칭 아키텍처.md)
- [펫케어 코인 관련 흐름](../architecture/care/펫케어 코인 관련 흐름.md)
- [Payment 도메인](payment.md)
- [Care 요청 N+1 분석](../troubleshooting/care/care-request-n-plus-one-analysis.md)
- [Care 요청 페이징 N+1](../troubleshooting/care/care-request-paging-n-plus-one.md)
- [Care 도메인 기술 분석](../troubleshooting/care/care-domain-technical-analysis.md)
- [Care 거래 확정 Race Condition](../troubleshooting/care/care-deal-confirmation-race-condition.md)
- [Care Payment 리팩토링](../refactoring/care/care-payment-refactoring-2026-04-14.md)
- [Care Payment 코드 리뷰](../refactoring/care/care-payment-code-review-2026-04-14.md)
