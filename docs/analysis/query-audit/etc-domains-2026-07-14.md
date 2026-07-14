---
date: 2026-07-14
domains: [chat, location, user, notification, payment, report, file, statistics]
type: query-audit-evidence
problem: unbounded-pets-endpoint-and-scheduler-fullscan
status: verified
metric: "GET /api/pets/type/{type} → 7,667건 무제한 반환 + 백신 쿼리 154회 / 331ms · MeetupChatRoomRecoveryScheduler 5분마다 meetup 5,000행 풀스캔 / 0행 반환 · chat·location·payment 는 인덱스 정상"
related: [docs/analysis/query-audit/00-plan.md, docs/analysis/query-audit/99-summary.md]
---

# 나머지 도메인 + 스케줄러 감사 — 실측 결과

board / care / meetup 은 별도 문서. 이 문서는 **chat · location · user · notification · payment · report · file**
과 **스케줄러**를 다룬다.

---

## 1. 🔴 최악의 발견 — `GET /api/pets/type/{petType}`

**한 번 호출하면 강아지 7,667마리가 전부 온다.**

```
GET /api/pets/type/DOG   →   200   반환 7,667건   331ms

digest:
  SELECT p1_0.idx, ...  (pets)          1회    검사 12,000행
  SELECT v1_0.pet_idx, ... (백신접종)  154회   ← 7,667 ÷ BatchSize 50 = 154
```

```java
@GetMapping("/type/{petType}")
public ResponseEntity<List<PetDTO>> getPetsByType(@PathVariable("petType") String petType) {
    List<PetDTO> pets = petService.getPetsByType(petType);   // ← 페이징 없음. 상한도 없음
    return ResponseEntity.ok(pets);
}
```

**meetup 검색(§meetup-2026-07-14 §4)과 정확히 같은 병이다. 다만 더 나쁘다** —
meetup 은 `MAX_LIST_SIZE = 500` 이라도 있는데, **이 엔드포인트는 상한이 아예 없다.**
펫이 10만 마리가 되면 10만 건을 반환하고 백신 쿼리를 2,000회 날린다.

**인덱스 문제가 아니다.** `idx_pets_type` 은 존재하고 정상적으로 탄다:

```
-> Index lookup on p using idx_pets_type (pet_type='DOG')  (actual rows=7667 loops=1)
```

**처방:** `Pageable` 추가 (기본 size 20). 인덱스는 손댈 필요 없다.

---

## 2. 🔴 스케줄러 — `MeetupChatRoomRecoveryScheduler`

**측정 방법:** 스케줄러 ON 으로 기동하고 **사용자 트래픽 0** 인 상태로 digest 를 봤다.
`fixedDelay` 는 기동 직후 즉시 발화하므로 이렇게 잡힌다.

| 쿼리 | 횟수 | 반환 | 검사 | 인덱스미사용 | 총ms |
|---|---|---|---|---|---|
| `SELECT m1_0.idx ...` (findWithoutChatRoom) | 1 | **0** | **10,000** | **1** | 15 |

**5분마다 1만 행을 검사해서 0행을 반환한다. 하루 288번.**

**EXPLAIN ANALYZE:**

```
-> Nested loop antijoin                              (actual time=20.2..20.2 rows=0 loops=1)
    -> Filter: (m.is_deleted = 0)                    (actual rows=5000 loops=1)
        -> Table scan on m                           (actual rows=5000 loops=1)   ← meetup 풀스캔
    -> Index lookup on c using idx_conversation_related (related_type='MEETUP', related_idx=m.idx)
                                                     (actual rows=1 loops=5000)   ← 5,000번
```

**conversation 쪽은 인덱스를 제대로 탄다** (`idx_conversation_related`). 문제는 **meetup 전체를 매번 훑는 것**이다.

**처방 (효과 순)**
1. **최근 생성된 모임만 검사한다.** 채팅방 누락은 *생성 직후* 실패로만 생긴다.
   `WHERE m.created_at > NOW() - INTERVAL 1 HOUR` 를 붙이면 검사 대상이 5,000 → 수십 행으로 줄어든다.
2. 주기를 5분 → 1시간으로 늦춘다 (미봉책이지만 즉효, 하루 288회 → 24회).
3. 아예 아웃박스 패턴으로 바꾼다 — 채팅방 생성 실패를 테이블에 기록하고 그것만 재시도한다. **폴링이 사라진다.**

> **감사 자체를 오염시켜서 `--petory.scheduling.enabled=false` 스위치를 만들어야 했던 바로 그 놈이다** (`00-plan.md` §2-1).

### 나머지 8개 스케줄러 — ⬜ 미측정 (정직하게 기록)

`StatisticsScheduler`(자정) · `LocationServiceScoreScheduler`(자정) · `BoardPopularityScheduler`(18:30) ·
`BoardListQueryPlanMaintainer`(03:10) · `CareRequestScheduler` · `UserSanctionScheduler` · `UserDormantScheduler` · `MeetupScheduler`(매시)

**전부 cron 기반이라 감사 시각에 발화하지 않았다.** digest 로는 못 잡는다.
측정하려면 cron 을 앞당기거나 메서드를 직접 호출해야 한다 — **후속 과제.**

> `BoardListQueryPlanMaintainer` 는 기동 시 `ANALYZE TABLE users, board` + 히스토그램 갱신을 수행하는 것이
> digest 에서 확인됐다 (검사행 0, 비용 낮음).

---

## 3. ✅ chat — 인덱스 설계 양호

엔드포인트 6개 전부 200. 최대 검사행 **104행**. N+1 없음.

```
-> Covering index lookup on cm using idx_chat_message_conversation_created (conversation_idx=?)
   (actual time=0.0236..0.0289 rows=9 loops=1)
```

`idx_chat_message_conversation_created (conversation_idx, created_at)` 로 **커버링 인덱스 룩업**. 이상적이다.

### ⚠️ 단, 이 측정은 신호가 약하다 (한계를 명시함)

| | 값 |
|---|---|
| 대화방 수 | 6,120 |
| 총 메시지 | 30,600 |
| **대화방당 메시지** | **평균 5건 / 최다 9건** |

**대화방 하나에 메시지가 몇 건 없어서 페이징에 부하가 걸리지 않는다.**
"chat 은 문제없다" 가 아니라 **"현재 시드로는 문제를 만들 수 없다"** 가 정확한 결론이다.
대규모 대화방(수천 메시지) 시나리오는 **미검증** — 시드를 보강해야 한다.

---

## 4. ✅ location — 프로젝트에서 가장 잘 튜닝된 도메인

`locationservice` 인덱스 **9개**:

```
idx_locationservice_location_spatial       (location)  SPATIAL
ft_search   (name, description, category1, category2, category3)  FULLTEXT
idx_locationservice_sido_deleted_rating    (sido, is_deleted, rating)
idx_locationservice_sigungu_deleted_rating (sigungu, is_deleted, rating)
idx_locationservice_eupmyeondong_deleted_rating
idx_category3_deleted_rating / idx_road_name_deleted_rating / idx_name_address ...
```

**공간 인덱스 + FULLTEXT + 지역별 복합 인덱스가 전부 갖춰져 있다.** 서비스 검색 38ms, 리뷰 목록 18ms.

> **`care` 와 비교하라** — care 는 인덱스가 PK/FK 3개뿐이고 FULLTEXT 가 없어 검색이 **HTTP 500** 이다.
> **같은 프로젝트, 같은 종류의 기능(주변 검색 + 키워드 검색), 정반대의 성숙도.**

---

## 5. ✅ payment / notification / report / file

| 도메인 | 결과 |
|---|---|
| payment | 잔액 7ms / 거래내역 17ms. 최대 검사 2행. **문제 없음** |
| notification | 목록 33ms. 반환 0건 (시드에 알림 없음) — **신호 없음, 미검증** |
| report | 엔드포인트가 `POST` 하나뿐 (쓰기). 조회 경로 없음 |
| file | 업로드(POST) / 다운로드(GET). **시드가 `file` 테이블을 만들지 않아 미측정** (baseline §5 와 동일) |

> notification·file·report 는 **"문제없음" 이 아니라 "측정할 데이터가 없음"** 이다. 시드 보강 후 재측정 대상.

---

## 6. 요약

| 도메인 | 판정 |
|---|---|
| **user (pets)** | 🔴 `/api/pets/type/{type}` 무제한 반환 (7,667건) + 백신 154쿼리 / 331ms |
| **스케줄러** | 🔴 `MeetupChatRoomRecoveryScheduler` 5분마다 meetup 풀스캔 / 0행 반환. **나머지 8개 미측정** |
| **chat** | ✅ 인덱스 양호 (커버링). ⚠️ 시드가 작아 대규모 시나리오 미검증 |
| **location** | ✅ 공간+FULLTEXT+복합 인덱스 완비. **모범 사례** |
| **payment** | ✅ 문제 없음 |
| **notification / report / file** | ⬜ 측정할 데이터가 없음 (시드 보강 필요) |
