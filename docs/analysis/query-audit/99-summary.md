---
date: 2026-07-14
domains: [board, care, chat, location, meetup, notification, payment, report, user, file, statistics, admin]
type: query-audit-summary
problem: full-query-audit-result
status: verified
metric: "전 도메인(12개) · 엔드포인트 62개 실호출 측정 완료. 처방 1~6 적용 + 회귀 테스트 8개. 치명 1건(care 검색 HTTP 500, 공개+admin 양쪽) · 진짜 N+1 1건(admin care, 20건→60쿼리) · 무제한 반환 3건 · 인덱스 부재 2곳(care, users.created_at) · statistics 는 가장 깨끗함 · 유일 잔여: 스케줄러 8개(cron)"
related: [docs/analysis/query-audit/00-plan.md]
---

# 전체 쿼리 감사 — 종합

**모든 수치는 실제 API 를 `curl` 로 호출해서 얻은 것이다.** 손으로 쓴 SQL 이 아니다.

---

## 1. 가장 중요한 결론 3개

### ① N+1 은 **딱 한 군데** 있었다 — 그리고 그건 admin 이었다

> ⚠️ **이 절은 admin 감사 후 정정됐다.** 공개 API 10개 도메인만 봤을 때는 **0건**이었고,
> 그래서 처음엔 "N+1 없음" 이라고 썼다. **admin 을 재보니 있었다.** 감사 범위를 넓히니 결론이 바뀐 것이다.

**공개 API 10개 도메인 — N+1 0건.** `@BatchSize(50)` 과 배치 사후주입(`IN (...)`)이 의도대로 동작한다.
meetup 검색에서 참가자 쿼리가 10회 나왔지만 — 결과 500건 ÷ 배치 50 = **정확히 10** 이다. 배칭이 맞다.

**하지만 `/api/admin/care-requests` 는 진짜 N+1 이다.**

| `size` | pets | 첨부 | 백신 | 총 쿼리 |
|---|---|---|---|---|
| 10 | 10 | 10 | 10 | 36 |
| 20 | 20 | 20 | 20 | 66 |
| 40 | 40 | 40 | 40 | **127** |

**결과 건수에 1:1 로 비례한다** (회당 검사 1행 = PK 룩업). 목록 20건에 **추가 쿼리 60개**.

원인은 **같은 테이블에 fetch join 이 있는 쿼리와 없는 쿼리가 공존**하는 것이다.

```java
// 공개 — JOIN FETCH 있음  ✅       findAllActiveRequestsWithPaging
"SELECT cr FROM CareRequest cr JOIN FETCH cr.user u LEFT JOIN FETCH cr.pet WHERE ..."

// 관리자 — JOIN FETCH 없음  🔴     findAllForAdmin
"SELECT r FROM CareRequest r WHERE (:status IS NULL OR ...) ORDER BY r.createdAt DESC"
```

**공개 쪽은 고쳤는데 관리자 쪽은 안 고쳤다.** `admin board` 는 `JOIN FETCH` 가 있어 멀쩡하다. **care 만 빠졌다.**

> **교훈: "N+1 없음" 은 측정한 범위에 대해서만 참이다.** admin 은 트래픽이 적어서 후순위로 밀렸고,
> 그래서 가장 늦게 봤고, 거기에 있었다.

### ② 진짜 문제는 **"페이징이 없다"** 였다

N+1 처럼 **보이는** 신호의 정체는 전부 이것이었다.

| 엔드포인트 | 반환 건수 | 쿼리 수 | 시간 |
|---|---|---|---|
| `GET /api/pets/type/DOG` | **7,667** (상한 없음) | 155 | 331ms |
| `GET /api/meetups/search` | **500** (`MAX_LIST_SIZE`) | 51 | 247ms |
| `GET /api/meetups/nearby` | ~200 (`maxResults` 기본 **500**) | 21 | 98ms |

**쿼리 수가 결과 건수에 비례한다.** 배칭을 아무리 잘해도 500건을 반환하면 51개 쿼리가 나간다.
**고쳐야 할 것은 배칭이 아니라 결과 크기다.**

### ③ 도메인별 **인덱스 성숙도가 극단적으로 갈린다**

| 도메인 | 인덱스 | 상태 |
|---|---|---|
| **location** | **9개** (SPATIAL + FULLTEXT + 지역 복합) | ✅ 모범 |
| **board** | 6개 (FULLTEXT + 복합) | ✅ 양호 |
| **meetup** | 8개 (SPATIAL + FULLTEXT) | ✅ 양호 |
| **chat** | 7개 (커버링) | ✅ 양호 |
| 🔴 **care** | **3개 — 전부 PK/FK** | 🔴 **방치** |

**같은 프로젝트, 같은 기능(주변 검색 + 키워드 검색)인데 location 은 완비돼 있고 care 는 하나도 없다.**

---

## 2. 🔴 치명 — care 검색은 **죽어 있다** (HTTP 500)

```
GET /api/care-requests/search?keyword=산책   →   500

java.sql.SQLException: Can't find FULLTEXT index matching the column list
```

`MATCH(cr.title, cr.description)` 를 쓰는데 **FULLTEXT 인덱스가 없다.**
MySQL 은 FULLTEXT 없이 `MATCH ... AGAINST` 를 **실행 자체를 못 한다.** 느린 게 아니라 **항상 500** 이다.

**성능 감사를 하다가 기능 버그를 찾았다. API 를 실제로 호출했기 때문에 나왔다.**

---

## 3. 전체 발견 목록

| # | 도메인 | 발견 | 심각도 | 문서 |
|---|---|---|---|---|
| 1 | care | **검색 HTTP 500** — FULLTEXT 인덱스 없음 | 🔴🔴 | [care](care-2026-07-14.md) |
| 2 | user | `/api/pets/type/{t}` **7,667건 무제한 반환** + 백신 154쿼리 | 🔴 | [etc](etc-domains-2026-07-14.md) |
| 3 | meetup | 검색 **페이징 없음** (500건, 51쿼리) · 주변 `maxResults` 기본 500 | 🔴 | [meetup](meetup-2026-07-14.md) |
| 4 | board | **깊은 페이지 OFFSET** — 100,000행 검사 / 0행 반환 / 129ms | 🔴 | [board](board-2026-07-14.md) |
| 5 | care | **인덱스 부재** — 목록 풀스캔 + filesort, 주변검색 선택도 **208배** 오판 | 🔴 | [care](care-2026-07-14.md) |
| 6 | 스케줄러 | `MeetupChatRoomRecovery` **5분마다** meetup 풀스캔 / **0행 반환** | 🔴 | [etc](etc-domains-2026-07-14.md) |
| 7 | 전역 | **자동생성 COUNT 16개** — 본문 JOIN 을 물고 COUNT (board 60,001행/호출) | 🟡 | [board](board-2026-07-14.md) |
| 8 | board | `my-posts` 필수 파라미터 누락 → **500** (400이어야 함) + userId 노출 | 🐛 | [board](board-2026-07-14.md) |
| 9 | 시드 | `PetGender` 불일치 (`MALE`/`FEMALE` ↔ enum `M`/`F`) — **12,000행** | 🐛 수정함 | [care](care-2026-07-14.md) |
| 10 | **admin** | **`/api/admin/care-requests` 진짜 N+1** — 20건에 60쿼리, 결과 수에 1:1 비례 | 🔴 | [admin](admin-2026-07-14.md) |
| 11 | **admin** | care 검색 **admin 에서도 HTTP 500** (FULLTEXT 부재, #1과 동일 원인) | 🔴 | [admin](admin-2026-07-14.md) |
| 12 | **admin** | 사용자 목록 **users 풀스캔 + filesort** — `created_at` 인덱스 없음 (1만 행) | 🔴 | [admin](admin-2026-07-14.md) |

---

## 4. ✅ 문제 없는 것 (확인됨)

| 항목 | 근거 |
|---|---|
| **N+1** (공개 API) | 패스 B 로 확인. 공개 10개 도메인 **0건**. ⚠️ **admin 은 1건 있음** (위 #10) |
| **쓰기 경로 과잉 락** | board 조회수 UPDATE = `검사 1행 = 변경 1행`. PK 로 정확히 잠근다 |
| **공간 인덱스** | meetup(`ST_Within`+SPATIAL) · location(SPATIAL) 정상 동작 |
| **배칭** | `@BatchSize(50)` · `IN (...)` 배치 사후주입 정상 |
| **chat 인덱스** | 커버링 인덱스 룩업 |
| **statistics** | Daily Summary Pattern 정상. 4개 엔드포인트 **최대 검사 1행**. `@Cacheable` 2회차부터 쿼리 0건 |

---

## 5. ⬜ 아직 측정하지 못한 것 (정직하게)

**"문제없음" 이 아니라 "측정 못 함" 이다. 섞지 말 것.**

| 범위 | 왜 못 했나 |
|---|---|
| **스케줄러 8개** (statistics 자정 배치 포함) | 전부 cron(일/시) 기반이라 감사 시각에 발화하지 않음. 직접 호출 필요. **집계를 *만드는* 쪽이라 읽기보다 무거울 수 있다** |
| **notification / report / file** | **시드에 데이터가 없다** (알림 0건, file 테이블 미생성) |
| **chat 대규모 대화방** | 시드가 대화방당 평균 **5건**. 부하가 안 걸림 |
| **락 경합** | 단일 curl 감사로는 **원리적으로 관측 불가**. 동시성 테스트 필요 (`00-plan.md` §5.4) |

---

## 6. 처방 우선순위

> ✅ **상위 4개 적용 완료 (2026-07-14).** 결과는 **[fixes-2026-07-14.md](fixes-2026-07-14.md)**.
> care 검색 **500 → 200**(A/B/A 증명) · admin care **66 → 7쿼리** · pets **155 → 5쿼리** · meetup 검색 **583ms → 43ms**.

| 순위 | 할 일 | 근거 | 상태 |
|---|---|---|---|
| **1** | **care FULLTEXT 인덱스 추가** | 기능이 죽어 있다. Flyway 한 줄 | ✅ **완료** (`V2`) |
| **2** | **`findAllForAdmin` 에 `JOIN FETCH` 추가** | 유일한 진짜 N+1. 20건에 60쿼리 | ✅ **완료** |
| **3** | **`/api/pets/type/{t}` 에 페이징** | 7,667건 무제한. 데이터 늘면 선형 악화 | ✅ **완료** (+`V3` COUNT 인덱스) |
| **4** | **meetup 검색 페이징 + `maxResults` 기본값 500→20** | 한 줄 수정으로 51쿼리 → 몇 개 | ✅ **완료** |
| **5** | **`users` 에 `created_at` 인덱스** | admin 목록이 1만 행 풀스캔+filesort | ✅ **완료** (`V4`) |
| **6** | **care 인덱스 3종** (deleted_created / status / SPATIAL) | 목록·주변검색 풀스캔 | ✅ **완료** (`V4`) |
| **7** | **스케줄러 범위 축소** (최근 1시간 모임만) | 하루 288회 × 1만 행 | ⬜ |
| **8** | board 깊은 페이지 (키셋 or 지연 조인) | 크롤러 대비 | ⬜ |
| **9** | 자동생성 COUNT 16개 (COUNT 캐싱 or `Slice`) | 가장 넓게 퍼졌으나 개별 비용은 중간 | ⬜ |

> **모든 수정은 A/B/A 로 인과를 증명했다** (`00-plan.md` 원칙 5).
> ✅ **CI 회귀 테스트 8개 추가 — 원칙 6의 2단계(①회귀 재현 → ②수정 확인)를 전부 거쳤다.**
> 테스트를 짜다가 계기판이 세 번 고장났고(지연로딩 미집계 · Gradle UP-TO-DATE · 테스트 DB 분리), 전부 [fixes](fixes-2026-07-14.md) §4-2 에 기록했다.

---

## 7. 방법론이 실제로 값을 했는가

| 계기판 | 없었다면 |
|---|---|
| **패스 B (호출횟수순)** | pets 154쿼리 · meetup 51쿼리를 **못 봤다.** 검사행순 상위 20 밖으로 밀린다 |
| **실제 API 호출 (원칙 1)** | care 검색 **HTTP 500** 을 못 봤다. SQL 만 던졌으면 영원히 몰랐다 |
| **스케줄러 OFF (§2-1)** | 1만 행 스케줄러 쿼리가 **모든 도메인 측정에 섞였다** |
| **`max_digest_length` 4096 (§3-1)** | digest 가 948자에서 잘려 테이블명 필터가 무력화됐다 |
| **호출 횟수 기록 (분모)** | users 62회를 N+1 로 오진했을 것이다 (실제론 인증 9회 + 배칭) |

**계기판을 먼저 고친 것이 옳았다.**
