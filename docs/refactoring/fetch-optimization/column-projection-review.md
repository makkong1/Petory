# 목록 조회 오버페칭(Over-fetching) 제거 — DTO Projection 리팩토링

> **한 줄 요약**: 목록 API가 화면에서 쓰지 않는 **컬럼·연관 엔티티까지 통째로 조회**하는 낭비(over-fetching)를, **목록 전용 경량 read model + JPQL 생성자 표현식 projection**으로 제거한다.

> 이 문서와 기존 문서의 관계: [README.md](./README.md)와 도메인별 `Fetch 전략 개선` 문서는 **쿼리 수(N+1)** 축을 다룬다. 이 문서는 "한 번에 가져오는 데이터의 **폭(컬럼·필드)**"을 다루되, 아래 §근본 원인에서 밝히듯 상위 2개 케이스(CareRequest·Users)는 폭 문제이면서 **동시에 추가 쿼리(연관 오버페칭)** 문제라 N+1 축과도 겹친다.

---

## 1. 문제 — 두 종류의 오버페칭

**오버페칭(Over-fetching)** = 필요한 것보다 많은 데이터를 조회하는 것. 이 프로젝트에서는 두 형태로 나타난다.

| 종류 | 정의 | 사례 | 비용 |
| --- | --- | --- | --- |
| **컬럼 오버페칭** | N개 필드만 쓰는데 테이블 전체 컬럼을 SELECT | Board, ChatMessage(sender), LocationServiceReview | SELECT 폭 · DTO 직렬화 · 응답 바이트 |
| **연관 오버페칭** | 목록에서 안 쓰는 연관(컬렉션)까지 로딩 | CareRequest.`applications`, Users.`socialUsers` | 위 + **추가 쿼리**(`@BatchSize` 지연로딩) |

`Users` 엔티티가 28개 필드로 가장 크고, Board/CareRequest/Meetup 목록이 공통으로 이 엔티티를 통째로 로딩한다.

---

## 2. 근본 원인 — "프로젝션 기술 부재"가 아니라 "read model 미분리"

이 문제를 처음 조사할 때는 근본 원인을 *"프로젝트에 DTO Projection 기술이 없어서 항상 엔티티 전체를 가져온다"* 로 봤다. **재검증 결과 이 진단은 틀렸다.**

### (1) 프로젝션은 이미 쓰고 있다 (사실 정정)

"필요한 컬럼만 뽑는 기술이 없다"는 전제는 거짓이다. 스칼라 프로젝션이 이미 코드베이스 곳곳에 있다.

- `SpringDataJpaUsersRepository.findRoleByIdx` → `Optional<Role>` (주석: "role 프로젝션만 SELECT")
- `SpringDataJpaUsersRepository.findIdxByIdString` → `Optional<Long>`
- `SpringDataJpaCareRequestRepository.findIdxByFulltextKeyword` → `List<Long>`
- 다수의 `SELECT COUNT(...)`, `List<Object[]>` 집계 쿼리

→ 즉 목록에서 전체 엔티티를 가져오는 건 **기술이 없어서가 아니다.**

### (2) 진짜 원인: 엔티티당 단일 DTO/컨버터를 목록·상세가 공유

- `CareRequestDTO` 하나가 `applications`/`comments`(상세 전용, `dto/CareRequestDTO.java:67·71`)와 `userId/username/userLocation`(목록용, `:58-60`)을 **동시에** 필드로 보유한다.
- `CareRequestConverter` 하나가 **목록·상세·생성·수정 응답 11곳**(`service/CareRequestService.java`의 `toDTO`/`toDTOList` 호출)을 전부 담당하며, 호출자가 목록인지 상세인지 **구분 없이 항상 `applications`를 채운다**(`converter/CareRequestConverter.java:69-73`).
- 컨버터가 모든 연관을 항상 채우므로, **리포지토리가 목록에서도 그 연관을 공급하도록 강제**된다.

`Users`도 동일하다. `UsersConverter.toDTO`는 **20곳**(로그인 `AuthService`, 프로필 `UsersService`, 관리자 `AdminUserFacade`)에서 공유되며 항상 `socialUsers`를 채운다(`converter/UsersConverter.java:34`).

> **결론**: "프로젝션이 없다"는 **증상**이고, 근본 원인은 **read model이 유스케이스(목록/상세)별로 분리되지 않은 것**이다. 이걸 손대지 않고 컨버터만 줄이면 로그인·상세·프로필 응답까지 깨진다.

### (3) 상위 2개 케이스는 "폭"만이 아니라 "추가 쿼리" 문제 (프레임 정정)

- CareRequest 페이징 목록 쿼리(`repository/SpringDataJpaCareRequestRepository.java:95`)는 `JOIN FETCH cr.user, cr.pet`만 하고 **`applications`는 FETCH하지 않는다.** 그런데 컨버터가 `getApplications()`를 호출 → `@BatchSize(50)`(`entity/CareRequest.java:129`) 지연로딩으로 **추가 배치 쿼리**(applications + provider) 발생.
- Users 관리자 목록(`findAllForAdmin`, `:140`)도 `socialUsers`를 FETCH하지 않아 컨버터 접근 시 `@BatchSize`(`entity/Users.java:19·67-68`) 추가 쿼리 발생.

→ 이 둘은 제거하면 컬럼만 주는 게 아니라 **쿼리 수도 줄어든다.** "쿼리 수는 그대로"라는 초기 가정은 이 두 케이스에서 성립하지 않는다.

---

## 3. 해결 방향 — Read Model 분리 + JPQL 생성자 표현식 Projection

핵심 원칙: **기존 공유 DTO/컨버터는 그대로 두고, 목록 전용 경량 read model(ListView DTO)을 새로 만들어 projection으로 채운다.**

### 기법 선택

| 기법 | 채택 여부 | 이유 |
| --- | --- | --- |
| **JPQL 생성자 표현식** `SELECT new XxxListDTO(...)` | ✅ 채택 | 이미 `@Query` JPQL을 전반에서 사용 → 도입 비용 낮음. SELECT 절에 필요한 컬럼만 명시. |
| QueryDSL | ❌ | 의존성 자체가 없음. 이 문제 하나 때문에 도입하기엔 과함. |
| Interface Projection | ❌ | 연관 엔티티 접근 시 결국 프록시를 건드려 연관 오버페칭을 못 막음. |

> 참고: (2)에서 밝혔듯 스칼라 프로젝션 전례가 이미 있으므로 "새 기술 도입 비용"은 실제로 크지 않다. 진짜 작업량은 "기술 도입"이 아니라 **"목록 전용 DTO 신설 + 기존 경로 회귀 방지"** 에 있다.

---

## 4. 대상별 진단 + 적용 계획

| 도메인 | 목록 API | 오버페칭 종류 | 실사용 필드 | 소비처 확인 | 적용 방법 | 우선순위 |
| --- | --- | --- | --- | --- | --- | --- |
| **PetCoinTransaction** ✅적용완료 | `GET /api/payment/transactions` | 연관(이미 가진 `user` 재조회) | `userId` 1개(idx) | 프론트 `PetCoinTransactionListModal.js`는 `userId`조차 렌더 안 함 | `@EntityGraph` **한 줄 제거** (projection 불필요) | **1** |
| **CareRequest** ✅적용완료 | **`GET /api/care-requests/nearby`** (지도) | 연관(`applications`@BatchSize, 중첩 pet 파일 N+1) + 컬럼(user 27) | 14개 필드 | **소비처 재확인 결과 문서 초안이 지목한 페이징(`GET /api/care-requests`)은 프론트 미사용(死). 실제 핫패스는 지도 `/nearby`(native).** CareLayer/UnifiedMap이 쓰는 14필드 확인 | native **interface projection**(`CareRequestListView`): 필요 14컬럼만 JOIN·SELECT, applications/중첩pet 제거, `petName` 평면화 | 2 |
| **Users(관리자)** ✅적용완료 | `GET /api/admin/users/paging` | 연관(`socialUsers`, 추가 쿼리) + 컬럼(28) | **12개 필드** | 목록 표 9필드 + **상태관리 모달(`UserStatusModal`)이 목록 행을 재사용**해 `status/warningCount/suspendedUntil` 3개 추가로 필요(누락 시 모달 기본값 회귀). `socialUsers` 미참조 | `AdminUserListDTO`+전용 응답 래퍼 신설, 기존 `UsersConverter`(20곳) 불변 | 3 |
| **ChatMessage** | 채팅 메시지 목록 | 컬럼(sender 28) + 연관(`replyToMessage` `@Lob content`) | sender 2개 + reply idx 1개 | 고빈도 실시간 경로 | 목록 projection: sender 2필드 + reply idx만 | 4 |
| **Meetup** | `GET /api/meetups` | 컬럼(organizer 28) + 연관(`participants` 전체) | organizer 2개 + currentParticipants | `MeetupLayer.js`가 참가자를 **별도 API**로 조회(embed 미사용, grep 확인) | 목록 DTO에서 `participants` 제거 | 5 |
| **Board** ✅적용완료 | `GET /boards` (페이징·닉네임검색) | 컬럼(user 27) | user 3개 | CommunityBoard가 content 포함 사용. 리액션/첨부는 서비스가 배치 사후주입 | `BoardListItemDTO` 생성자 projection(페이징 all/category+닉네임검색). native FULLTEXT·동적 Specification·비페이징 경로는 엔티티 유지 | 6 |
| ConversationParticipant | 채팅방 참여자 목록 | 컬럼(user 28) | username/isDeleted 2개 | ChatMessage와 동일 패턴, 빈도 낮음 | 목록 projection | 7 |
| **Report** ✅적용완료 | `GET /api/admin/reports` | 연관(`reporter`·`handledBy` `@BatchSize` 전체 로딩) + **unpaged** | reporter/handledBy username·idx | 관리자 전용·저빈도이나 **전건 반환(List)이라 신고 누적수에 비례**해 폭증 | **페이징 + JPQL 생성자 projection** (§2차 점검) | 8 |
| **LocationServiceReview** ✅적용완료 | `GET /api/location-service-reviews/service/{id}` | 컬럼(user 28) + **unpaged** | username 1개(프론트 실사용) | `LocationLayer.js`가 `username` 사용. **전건 반환이라 인기 장소 리뷰수에 비례** | **페이징 + JPQL 생성자 projection** (§2차 점검) | 9 |

**조사했지만 대상 아님**: `LocationService`(34필드 중 33개 실사용), `Notification`(9컬럼, `getIdx()`만 사용), `AdminAuditLog`/`AttachmentFile`/`SocialUser`/`PlaceInteractionLog`/`SignalInteractionLog`/`UserPetIntentSignal` — 컬럼 수가 작거나 큰 연관을 로딩하지 않음.

---

## 5. 케이스별 코드 (AS-IS → TO-BE)

### PetCoinTransaction — projection조차 불필요 (연관 오버페칭 극단 사례) ✅ 적용완료

`findByUserOrderByCreatedAtDesc`(`repository/SpringDataJpaPetCoinTransactionRepository.java:27-28`)는 `Users user`를 **파라미터로 이미 받으면서** `@EntityGraph(attributePaths="user")`로 그 user를 또 즉시 로딩한다. 컨버터(`:25`)는 `getUser().getIdx()`만 쓰고, `user`는 `FetchType.LAZY`(`entity/PetCoinTransaction.java:41`)라 idx는 프록시에서 쿼리 없이 나온다.

```java
// TO-BE: 어노테이션 한 줄 제거
Page<PetCoinTransaction> findByUserOrderByCreatedAtDesc(Users user, Pageable pageable);
```
착수 비용 사실상 0. (착수 전 확인: 서비스 레이어가 `getUser()`에서 idx 외 다른 필드에 접근하지 않는지)

### Board — 컬럼 오버페칭 케이스 ✅ 적용완료

> **착수 중 발견**: Board 목록 흐름(`BoardService.mapBoardsWithReactionsBatch`)은 컨버터 결과 DTO에 **리액션 배치 카운트(`applyReactionCounts`)와 첨부파일 배치(`applyAttachmentInfo`)를 사후 주입(mutate)** 한다. 그래서 projection DTO를 곧바로 반환할 수 없다. **해결**: enrichment를 `enrichBoardDTOs(List<BoardDTO>)`로 추출해 엔티티 경로(`mapBoardsWithReactionsBatch`)와 projection 경로(`mapBoardListItemsBatch`)가 공유하도록 리팩터한 뒤, 페이징 목록(all/category)+닉네임검색을 projection으로 전환했다. **native FULLTEXT 검색·동적 Specification(관리자)·비페이징 legacy 경로는** 생성자 표현식이 부적합해 엔티티 경로를 유지하되 동일 enrichment를 공유한다. 검증: `BoardServiceListProjectionTest`(3건, 필드매핑·enum·페이징) 통과.

```java
// AS-IS: SELECT b.*(13) + u.*(28) — JOIN FETCH b.user
//        BoardConverter.toDTO는 user에서 idx/username/location 3개만 사용

// TO-BE:
@Query("SELECT new com.linkup.Petory.domain.board.dto.BoardListItemDTO(" +
       "  b.idx, b.title, b.category, b.status, b.createdAt, b.isDeleted, b.deletedAt, " +
       "  b.commentCount, b.likeCount, b.dislikeCount, b.viewCount, b.lastReactionAt, " +
       "  u.idx, u.username, u.location) " +
       "FROM Board b JOIN b.user u " +
       "WHERE b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' " +
       "ORDER BY b.createdAt DESC")
Page<BoardListItemDTO> findBoardListItems(Pageable pageable);
```
JOIN은 유지(username/location 필요), **SELECT 컬럼만 축소.** 쿼리 수 동일 → 순수 컬럼 폭 효과 측정용.

### CareRequest — 지도 native 경로 (연관+컬럼, 실제 핫패스) ✅ 적용완료

> **착수 중 발견 (문서 초안 정정)**: 초안은 페이징 엔드포인트 `GET /api/care-requests`(`getCareRequestsWithPaging`)를 타겟했으나, **프론트 소비처를 전수 조사하니 이 엔드포인트는 아무도 호출하지 않는 死엔드포인트였다.** 케어의 실제 목록 핫패스는 **지도의 `GET /api/care-requests/nearby`**(`getNearby` → native haversine 쿼리)다. 즉 "실제 낭비를 줄인다"는 목표 기준에서 최적화 대상은 페이징이 아니라 지도 native 경로다.

기존 `getNearby`는 `SELECT cr.*`로 엔티티를 읽은 뒤 컨버터가 **작성자(Users) 전체 + 중첩 `PetDTO`(파일 경로 조회) + `applications`(@BatchSize 추가 쿼리)** 까지 채웠다. 지도 레이어(`CareLayer`/`UnifiedMap`)가 실제 쓰는 필드는 14개뿐이라, native **인터페이스 projection**(`CareRequestListView`)으로 그 14컬럼만 JOIN·SELECT 하도록 재작성했다.

```java
// TO-BE (적용): 필요한 14컬럼만 JOIN·SELECT. WHERE(haversine 반경)/ORDER는 기존과 동일.
@Query(value = "SELECT cr.idx AS idx, cr.title AS title, cr.description AS description, cr.`date` AS `date`, " +
        "  cr.schedule_mode AS scheduleMode, cr.estimated_duration_minutes AS estimatedDurationMinutes, " +
        "  cr.offered_coins AS offeredCoins, cr.status AS status, " +
        "  cr.latitude AS latitude, cr.longitude AS longitude, cr.address AS address, " +
        "  u.idx AS userId, u.username AS username, p.pet_name AS petName " +
        "FROM carerequest cr JOIN users u ON u.idx = cr.user_idx LEFT JOIN pets p ON p.idx = cr.pet_idx " +
        "WHERE ...(haversine 반경 조건 동일)... ORDER BY cr.created_at DESC LIMIT :limit", nativeQuery = true)
List<CareRequestListView> findNearbyCareRequests(...);
```
- **연관 오버페칭 제거**: `applications` @BatchSize 추가 쿼리 + 중첩 pet 파일 N+1 소멸(지도 이동마다 발생하던 낭비).
- **컬럼 오버페칭 제거**: 작성자 전체(27) → `userId/username`(2), pet 전체 → `petName`(1)만.
- **부가 효과**: 프론트가 읽던 평면 `raw.petName`과 DTO의 중첩 `pet.name` **불일치도 해소**(projection이 `petName`을 평면 제공).
- 기존 `CareRequestConverter`/`CareRequestDTO`는 상세·생성·수정용으로 **그대로 유지** → 회귀 없음. 검증: `CareRequestNearbyProjectionTest`(2건, native·예약어 별칭 `date`·enum컬럼·JOIN·petName 매핑) 통과.

> 문서 초안이 제시했던 페이징 projection(`CareRequestListDTO` + COUNT 서브쿼리)은 死엔드포인트라 실효 없어 적용하지 않았다. 필요 시 동일 기법으로 즉시 적용 가능.

### Users 관리자 목록 — 공유 컨버터 회피 ✅ 적용완료

`AdminUserListDTO`(**12필드**) 신설. `socialUsers` 배치 쿼리 제거. 기존 `UsersConverter`(20곳 공유)는 **손대지 않는다.**

**필드 12개 = 목록 표 9 + 상태 모달 3.** 착수 전 프론트를 검증하니 `UserList.js`가 목록 행 객체를 그대로 `UserStatusModal`에 넘기고(`handleEditUser(user)`), 모달이 `user.status/warningCount/suspendedUntil`을 폼 초기값으로 읽는다. 이 3필드를 빼면 모달이 항상 `ACTIVE/0/빈값`을 표시하는 회귀가 발생하므로 목록 DTO에 포함한다.
- 목록 표(`UserList.js`): `idx/id/nickname/username/email/role/isDeleted/isDormant/createdAt`
- 상태 모달(`UserStatusModal.js`): `status/warningCount/suspendedUntil`

```java
// TO-BE (적용): WHERE/ORDER는 기존 findAllForAdmin과 동일, SELECT만 생성자 표현식으로 축소
@Query("SELECT new com.linkup.Petory.domain.user.dto.AdminUserListDTO(" +
       "  u.idx, u.id, u.nickname, u.username, u.email, CAST(u.role AS string), " +
       "  u.isDeleted, u.isDormant, u.createdAt, CAST(u.status AS string), u.warningCount, u.suspendedUntil) " +
       "FROM Users u WHERE ...동일... ORDER BY u.createdAt DESC")
Page<AdminUserListDTO> findAdminUserListItems(...);
```
`role`/`status`는 기존 DTO(`String`)와 JSON 호환을 위해 `CAST(... AS string)`으로 enum명 문자열화(기존 WHERE의 CAST 전례와 동일). 응답 envelope는 죽은 경로(`getAllUsersWithPaging`)가 참조하는 공유 `UserPageResponseDTO`를 건드리지 않도록 `AdminUserPageResponseDTO`(동일 JSON 형태)를 분리 신설. 기존 통합 테스트(`AdminUserFacadeGetUsersTest`, 6건)가 새 projection 쿼리(CAST 필터 포함)를 그대로 검증 → 통과.

### ChatMessage — 고빈도 경로

sender는 `username/isDeleted` 2필드, `replyToMessage`는 `idx`만 projection → 대용량 `@Lob content`(`entity/ChatMessage.java:35-37`)와 자기참조 fetch join 회피. **착수 전 확인**: 컨버터 주석대로 서비스 레이어가 답장 미리보기를 별도 주입하는지(그렇다면 목록 쿼리에서 reply를 안 실어도 됨).

### Meetup

목록 전용 DTO에서 `participants` 배열 제거, `currentParticipants`만. `MeetupLayer.js`가 참가자를 `getParticipants` 별도 API(`/api/meetups/{idx}/participants`, `MeetupLayer.js:30·33`)로 조회함을 grep으로 확인 → 착수 가능(런타임 확정은 아니므로 적용 시 회귀 확인 권장).

---

## 6. 효과 검증 (Before/After) — 실측

### 왜 측정을 두 레벨(DB / HTTP)로 하나 — "두 구간, 두 낭비" 모델

데이터는 두 구간을 흐르고, **오버페칭은 그 두 구간 중 어디서든 생길 수 있다.** 그래서 한 가지 측정만으로는 전체 낭비가 안 잡힌다.

```
[MySQL] ──(구간1: SELECT로 끌어옴)──▶ [Spring 앱/JPA] ──DTO 변환(안 쓰는 필드 버림)──▶ [JSON 응답] ──(구간2)──▶ [클라이언트]
```

| 구간 | 낭비의 형태 | 클라이언트까지 가나? | 재는 도구 |
| --- | --- | --- | --- |
| **구간1** (DB→앱) | 쿼리가 안 쓰는 컬럼·연관까지 `SELECT`/로딩 | ✗ (DTO 변환에서 버려짐) | **DB레벨** `SUM(LENGTH())` = 끌어와 버리는 바이트. 그 낭비는 **HTTP 응답시간**으로도 새어나옴(덜 읽으니 빨라짐) |
| **구간2** (앱→클라이언트) | DTO 자체에 안 쓰는 필드가 있어 응답 JSON이 큼 | ✓ | **HTTP 응답 바이트** |

**이 모델이 Board의 "이상한" 결과를 설명한다.** Board는 **구간1에만** 낭비가 있었다 — 쿼리는 작성자 27컬럼을 끌어오지만 DTO는 원래부터 3필드만 담아 보낸다.
- DB레벨 **행 페이로드 −56%** (작성자 조인 부분만 보면 5,065→753B = **−85%**): 구간1 낭비(27→3컬럼)를 바이트로 직접 포착.
- HTTP 응답 바이트 **0%**: 구간2엔 원래 낭비가 없어(DTO 불변) 응답 크기는 그대로.
- HTTP 응답시간 **−25%**: 구간1 낭비가 사라져 서버가 덜 읽으니 빨라짐.

→ 세 숫자는 모순이 아니라 **하나의 개선(구간1 축소)을 세 각도에서 본 것**이다. HTTP 응답 바이트만 봤다면 "Board는 개선 0%"라고 잘못 결론 냈을 것. 반면 **Users·CareRequest는 구간1+구간2 둘 다** 낭비여서(DTO 자체가 무거움) 바이트·시간이 함께 줄었다. 요컨대 **DB레벨은 구간1을, HTTP레벨은 (구간1을 시간으로 + 구간2를 바이트로) 잰다** — 그래서 두 측정을 함께 봐야 낭비의 전체 지도가 완성된다.

---

로컬 MySQL 실데이터(board 10,264 · users 6,775 · carerequest 1,014행)에 대해 **현재 쿼리 형태**와 **projection 적용 후 쿼리 형태**를 직접 실행해 비교했다. 원자료·재현 쿼리: [`evidence/measurement-2026-07-10.md`](./evidence/measurement-2026-07-10.md).

- **측정 대상**: `SUM(LENGTH(COALESCE(col,'')))` — DB에서 읽어 서비스로 전달됐다가 DTO 변환에서 **버려지는 실데이터 바이트**. HTTP 응답이 아니라 애플리케이션이 끌어오는 데이터 폭을 격리 측정.
- **범위**: 목록 첫 페이지 `page=0, size=20`, 실제 `WHERE`/`ORDER BY` 반영.
- **AFTER 쿼리**: JPQL 생성자 표현식 projection이 실행할 SELECT 절과 동일한 컬럼으로 실제 실행한 결과(추정 아님).

> **⚠️ 이 수치는 보수적 하한선.** 더미데이터가 대용량/토큰 필드를 거의 안 채운다(`password` 16자 vs 실제 bcrypt 60자, `pet_info` @Lob 대부분 빈값, `refresh_token` 144/6,775, `profile_image` 136/6,775). 운영에선 이 필드들이 채워져 절감 폭이 더 크다.

### 실측 결과 (page=0, size=20 기준)

| 케이스 | 끌어오는 데이터(전→후) | SELECT 컬럼 수(전→후) | 총 쿼리 수(전→후) | 오버페칭 유형 |
| --- | --- | --- | --- | --- |
| **Board** | 행 페이로드 **7,698 B → 3,386 B (−56%)** · 작성자 컬럼만 **5,065→753 B (−85%)** | user 27 → 3 | 1 → 1 | 순수 컬럼 |
| **Users(관리자)** | **3,622 B → 2,403 B (−33.7%)** | 27 → 12 | **2 → 1** (socialUsers 배치 제거) | 컬럼 + 연관 |
| **CareRequest** | DB바이트 미측정(연관 중심·좌표 데이터 부족 → HTTP/쿼리수로 검증) | user 27 → 3 (지도 native projection) | 지도 경로 배치(user/pet/applications) 제거 | 연관 중심 |
| PetCoinTransaction | 어노테이션 제거(이미 가진 user 재조회 제거) | - | - | 연관(극단) |
| ChatMessage / Meetup | 미측정(데이터 규모 부족/미착수) | - | - | 컬럼+연관 |

- Board −56%는 `content`를 양쪽 다 유지한 상태의 감소분 → **순수하게 user 컬럼 27→3 축소 효과**(작성자 조인 부분만 보면 85%가 버려지고 있었음).
- Users는 컬럼 폭 축소(27→12) **동시에 socialUsers 배치 쿼리도 제거**(연관 오버페칭 해소).
- 위 수치는 방금 실측(2026-07-10). 이전 스냅샷과 소폭 다른 이유는 그 사이 DB 행이 바뀌어 page-0 대상이 달라졌기 때문 — 방법·비율의 결론은 동일.

### HTTP 엔드포인트 레벨 실측 (전/후)

**실험 설계**: *가정* — 목록/지도 응답이 화면 미사용 컬럼·연관을 실어 나른다면 projection 후 응답 바이트나 응답시간이 줄어야 한다. *통제* — 동일 DB·동일 JWT·동일 요청·동일 포트, **유일 변수 = `git stash`로 토글한 리팩토링 코드.** *절차* — BEFORE(리팩토링 전) 앱 기동→15회 측정 → `stash pop`으로 AFTER 적용→동일 측정. `curl` 응답 바이트 + `time_total` 15회 평균. 원자료: [`evidence/measurement-2026-07-10.md`](./evidence/measurement-2026-07-10.md).

| 엔드포인트 | 응답 바이트(전→후) | 평균 응답시간(전→후) |
| --- | --- | --- |
| `GET /api/boards?page=0&size=20` | 9,351 → 9,351 B (**0%**) | 61.3 → **46.0 ms (−25%)** |
| `GET /api/admin/users/paging` | 8,647 → **5,829 B (−33%)** | 30.2 → **25.8 ms (−15%)** |
| `GET /api/care-requests/nearby`(20건) | 17,621 → **7,421 B (−58%)** | 38.3 → **9.9 ms (−74%)** |

> **핵심 통찰 — Board는 응답 바이트가 그대로다.** 위 "두 구간, 두 낭비" 모델대로, Board의 낭비는 **구간1(DB→앱)** 에만 있어 응답 DTO(`BoardDTO`)가 불변 → 클라이언트로 가는 바이트는 그대로고, 개선이 **응답 크기가 아니라 지연시간(−25%)** 으로 나타난다(DB레벨 −56%와 같은 개선의 다른 얼굴). 반면 Users·CareRequest는 구간1+구간2 둘 다 낭비여서 바이트도 함께 줄었다.
>
> 측정 한계: `time_total`은 localhost 왕복이라 상대 개선폭 지표다. care nearby는 좌표+OPEN 데이터가 부족해 20건 시드 후 측정·즉시 삭제(합성 데이터, 전/후 비율로 해석).

---

## 7. 포트폴리오 요약

- **문제**: 목록 API가 화면에서 쓰지 않는 컬럼·연관 엔티티까지 조회하는 오버페칭.
- **원인 분석**: 표면적 원인("프로젝션 기술 부재")이 아니라, **엔티티당 단일 DTO/컨버터를 목록·상세가 공유**해 컨버터가 모든 연관을 항상 채우고 → 리포지토리가 전체 로딩을 강제당하는 구조. (스칼라 프로젝션 전례는 이미 존재함을 확인해 표면 원인을 반증)
- **해결**: **목록 전용 read model 분리 + projection**(JPQL 생성자 표현식 / native 인터페이스 projection). 기존 공유 경로(로그인·상세) 불변 유지로 회귀 방지.
- **측정(실데이터, 전/후)**:
  - *DB 레벨(서버가 끌어오는 폭)*: Board 행 페이로드 **7,698B→3,386B(−56%)**(작성자 컬럼만 −85%), Users **3,622B→2,403B(−33.7%)+쿼리 2→1**, CareRequest 지도 native projection으로 배치(user/pet/applications) 제거.
  - *HTTP 레벨(앱 기동, `git stash` 전/후 비교)*: care nearby **응답 17,621B→7,421B(58%↓)·응답시간 38→9.9ms(74%↓)**, admin users **8,647B→5,829B(33%↓)**, boards **응답 바이트 동일·응답시간 61→46ms(25%↓)**. → Board 오버페칭은 서버 내부(DB→앱)라 응답 크기가 아닌 **지연시간**으로 나타남을 실측이 입증.
- **트레이드오프**: DTO·쿼리 중복 증가 vs 조회 효율. 목록처럼 고빈도·대량 경로에만 선택 적용(상세는 기존 유지).
- **깊이 포인트**: ① 오버페칭을 "컬럼 폭"과 "연관(추가 쿼리)"으로 구분하고 근본 원인을 아키텍처 레벨(read model 미분리)로 재정의. ② **문서의 계획을 그대로 믿지 않고 실제 소비처(프론트 화면·모달·지도 레이어)를 전수 검증** — 그 결과 (a) Users는 문서의 9필드가 아니라 모달 재사용 때문에 12필드가 필요했고, (b) CareRequest는 문서가 지목한 페이징 엔드포인트가 死엔드포인트이고 실제 핫패스가 지도 native 경로임을 발견해 최적화 대상을 바로잡았다.

### 구현 현황 (2026-07-10)

| 케이스 | 상태 | 기법 | 검증 |
| --- | --- | --- | --- |
| PetCoinTransaction | ✅ | `@EntityGraph` 제거 | 컴파일·소비처 확인 |
| Users(관리자) | ✅ | JPQL 생성자 projection `AdminUserListDTO`(12필드) + 전용 응답 래퍼 | `AdminUserFacadeGetUsersTest` 6건 |
| Board | ✅ | JPQL 생성자 projection `BoardListItemDTO`, enrichment 공유 리팩터 | `BoardServiceListProjectionTest` 3건 |
| CareRequest(지도) | ✅ | native 인터페이스 projection `CareRequestListView`(14필드) | `CareRequestNearbyProjectionTest` 2건 |

HTTP 엔드포인트 레벨(응답 바이트·시간)도 `git stash` 전/후 비교로 실측 완료(§6).

---

## 8. 2차 점검 (2026-07-11) — Report·Review 추가 적용 & 미적용 도메인 사유

1차에서 남긴 후보(우선순위 7~9)를 다시 훑다가 **판정 오류를 하나 잡았다.** Report·LocationServiceReview를 "저빈도·소량"이라 스킵 후보로 뒀는데, "소량"은 **지금 더미 DB가 비어서** 그렇게 보였을 뿐이고 **두 목록 모두 `List` 반환 = unpaged**였다. 즉 데이터가 쌓이면 조회량이 행 수에 비례해 커지는 구조라, 여기선 "빈도"가 아니라 **페이징 부재**가 근본 문제였다. → **페이징을 먼저 넣고 projection을 함께 적용**했다.

| 케이스 | 상태 | 기법 | 검증 |
| --- | --- | --- | --- |
| **Report(관리자)** | ✅ | 페이징 + JPQL 생성자 projection `findReportListItems`(reporter/handledBy idx·username만) + 응답 래퍼 `AdminReportPageResponseDTO`. 프론트는 이미 클라이언트 슬라이싱 중이던 `ReportManagementSection`을 서버 페이징으로 전환 | `ReportListProjectionTest` 2건 |
| **LocationServiceReview(장소별)** | ✅ | 페이징 + JPQL 생성자 projection `findReviewListItems`(user idx·username만, serviceName은 `CAST(NULL AS string)`). 평균·총개수는 페이지 무관 전체 집계로 별도 제공. 프론트 `LocationLayer`는 '첫 페이지 + 더보기' 누적으로 전환 | `LocationReviewListProjectionTest` 2건 |

- **정렬 변경(주의)**: Report는 기존에 전건 인메모리 집계로 "신고횟수 DESC" 정렬했으나(=바로 이 unpaged 낭비의 원인), 페이징 후 DB `createdAt DESC`(최신순)로 바꿨다. 인메모리 집계하던 `reportCount`는 프론트 소비처가 없어 제거(projection에서 `CAST(NULL AS integer)`).
- **Review UX 변경**: 장소 패널이 전체 리뷰를 한 번에 그리던 것을 '더보기' 누적으로 바꾸고, 클라이언트가 배열로 계산하던 평균/개수를 **서버 집계값**으로 대체(페이지 누적과 무관하게 총계가 안 흔들림).

### 나머지 도메인 — 적용 안 한 이유

| 도메인 | 판정 | 사유 |
| --- | --- | --- |
| **ConversationParticipant** | 스킵(정당) | 저빈도 + **규모가 구조적으로 bounded**(1:1=2명, 그룹도 소수) → 데이터가 늘어도 한 방 참여자 수는 안 늘어남. Report·Review와 달리 "소량"이 데이터 아티팩트가 아님. projection 이득 < DTO/쿼리 중복 비용. |
| **ChatMessage** | 보류 | 남은 것 중 **유일한 고빈도**(채팅 스크롤)이고 `LEFT JOIN FETCH replyToMessage`가 답장 `@Lob content`까지 통째 로딩하는 실질 낭비가 있어 값어치는 가장 큼. 그러나 컨버터가 답장 미리보기·읽음·첨부를 **서비스 레이어에서 사후 주입**하는 구조(Board enrichment와 유사)라 projection 도입 비용·회귀위험이 커서 보류. 착수 시 Board처럼 enrichment 공유 리팩터 선행 필요. |
| **Meetup** | 보류(선택) | 목록(`findAllNotDeleted`)은 이미 `@EntityGraph(organizer)`로 **페이징**돼 폭주 위험 없음. 남은 건 컨버터가 `getParticipants()`를 호출해 발생하는 @BatchSize 연관 오버페칭뿐인데, 프론트가 참가자를 **별도 API**로 조회하므로 목록 컨버터에서 호출만 빼면 저비용 제거 가능(필수 아님, 회귀 확인 후). |

> 이번 2차 점검의 교훈: **"저빈도라 스킵"의 근거를 빈도(아키텍처)와 규모(데이터 의존)로 분리**해야 한다. 규모 근거는 unpaged 여부·현재 데이터량에 오염되기 쉽다 — Report·Review가 그 사례였다.

---

## 참고 문서

- [README.md](./README.md) — Fetch Join vs Batch Size 규칙(쿼리 수 축)
- [care/Fetch 전략 개선 (Fetch Join vs Batch Size).md](<./care/Fetch%20전략%20개선%20(Fetch%20Join%20vs%20Batch%20Size).md>) — CareRequest N+1 해결 이력
- [catesian/cartesian-product-verification.md](./catesian/cartesian-product-verification.md) — 컬렉션 fetch join 행 폭증 분석
