# QueryAuditV2 페이지 드래프트 — 전체 쿼리 감사

> 목적: 포트폴리오에 넣을 **쿼리 감사** 페이지의 문구를 확정한다.
> 작성 기준: `docs/analysis/query-audit/` 8개 문서와 실제 커밋(`0f6374b9`~`b266e9be`)의 **실측치만** 쓴다. 추정치는 쓰지 않는다.

---

## 0. 이 페이지를 따로 만드는 이유

기존 8개 페이지는 전부 **도메인별**(board, care, chat, ...)이다. 이 페이지는 다르다.

- 도메인 지식이 아니라 **일하는 방식**을 보여주는 페이지다.
- 성능 최적화 페이지(`*DomainOptimization.jsx`)는 "무엇을 얼마나 빠르게 했나"를 말한다.
  이 페이지는 **"내가 빠르게 했다고 믿은 게 사실인지 어떻게 확인했나"**를 말한다.
- 면접에서 가장 오래 이야기할 수 있는 소재다. 수치보다 **판단 과정**이 남는다.

> 다른 페이지들과 톤이 겹치지 않게, **성과 자랑이 아니라 "내가 틀렸던 기록"** 을 중심에 둔다.
> 실제로 이 작업에서 내가 틀린 게 **6번** 나왔고, 그게 이 페이지의 진짜 콘텐츠다.

---

## 1. 페이지 상단

### H1

전체 쿼리 감사

### 소개 문단

게시글 목록 쿼리를 튜닝하고 `0.17s → 0.00s`로 고쳤다고 판단했다. 다음 날 실제 API를 호출해보니 **절반만 맞았다.** `Page<>`가 목록 SELECT와 **함께 날리는 COUNT 쿼리**를 보지 못했던 것이다. 그 COUNT는 180,003행을 검사하며 141ms가 걸렸다 — 내가 고친 목록 SELECT(4ms)보다 **35배 비쌌다.**

원인은 명확했다. **API를 한 번도 호출하지 않고, SQL을 손으로 던져 측정했기 때문이다.** 내가 짠 SQL은 앱이 하는 일이 아니었다.

그래서 프로젝트 전체 쿼리를 다시 감사했다. 12개 도메인 / 엔드포인트 62개를 `curl`로 실제 호출하고 `performance_schema`로 앱이 실제로 날린 쿼리를 관측했다. 그 결과 **성능 문제인 줄 알았던 것이 기능 버그로 드러나기도 했고(HTTP 500), N+1일 거라 확신했던 것이 N+1이 아니기도 했다.**

### 핵심 기능 태그 (`corePillars`)

```javascript
const corePillars = [
  '실측 기반 감사',
  'performance_schema digest',
  'A/B/A 인과 증명',
  'N+1 비례성 검증',
  'SPATIAL 인덱스',
  '회귀 테스트 2단계',
];
```

---

## 2. `section#intro` — 감사 개요

### 2-1. 개요 카드 문구

측정 규칙을 먼저 정하고 시작했다. 핵심은 **"내가 짠 SQL이 아니라 앱이 실제로 날리는 쿼리를 본다"** 하나다.

앱을 8081 포트로 띄우고(스케줄러는 끈다), 시드 계정으로 로그인해 토큰을 받고, `performance_schema`의 digest를 비운 뒤, 도메인별 엔드포인트를 `curl`로 한 바퀴 돌린다. 그리고 digest를 **세 가지 기준으로** 스캔한다 — 검사행순(풀스캔·비싼 COUNT), 호출횟수순(N+1), 쓰기 경로(과잉 락). 의심 쿼리는 `EXPLAIN ANALYZE`로 **예상 행수와 실제 행수를 대조**한다. 둘의 차이가 곧 버그의 정체인 경우가 있다.

고친 뒤에는 **A/B/A**로 인과를 증명한다. "고쳤더니 빨라졌다"는 증거가 아니다. 적용 → 제거 → 재적용으로 문제가 재현되는지까지 봐야 원인이 확정된다.

### 2-2. 구조 테이블

| 항목 | 내용 |
|---|---|
| 감사 대상 | 컨트롤러 34개 / 엔드포인트 189개 |
| 실제 측정 | 12개 도메인 / 엔드포인트 **62개 실호출** |
| 측정 도구 | `performance_schema.events_statements_summary_by_digest` |
| 스캔 방식 | 3-패스 (검사행순 / 호출횟수순 / 쓰기 경로) |
| 진단 | `EXPLAIN` + `EXPLAIN ANALYZE` (예상 vs 실제 행수 대조) |
| 인과 증명 | A/B/A (적용 → 제거 → 재적용) |
| 데이터 규모 | board 5만 / comment 15만 / reaction 17.5만 / users 1만 / pets 1.2만 |
| 회귀 방지 | 테스트 3클래스 8케이스 (2단계 검증) |

### 2-3. 성과 테이블

> 측정 조건: 로컬 MySQL 8.4, 시드 데이터(board 5만행 기준), 엔드포인트당 `curl` 1회, digest 초기화 후 측정.

| 엔드포인트 | Before | After |
|---|---|---|
| care 검색 (공개 + 관리자) | **HTTP 500** (항상 실패) | **200** |
| 관리자 케어요청 목록 (20건) | 쿼리 **66개** | 쿼리 **7개** |
| `/api/pets/type/DOG` | **7,667건** · 155쿼리 · 331ms | **20건** · 5쿼리 · 37ms |
| 모임 검색 | 500건 · 53쿼리 · **583ms** | 20건 · 6쿼리 · **43ms** |
| 관리자 사용자 목록 | **10,021행** 검사 + filesort | **20행** |
| care 목록 | **3,060행** 검사 + filesort | **30행** |
| care 주변검색 | **3,000행 풀스캔** | **208행** (SPATIAL) |

---

## 3. `section#design` — 기술 결정

### A. 계기판부터 고쳤다 — 도메인 감사를 시작하기 전에

감사 계획서를 쓰고 나서, **그 계획서가 맞는지부터 검증**했다. 세 군데가 틀려 있었다.

**A-1. `SUM_LOCK_TIME`으로는 락을 잴 수 없다**

락 경합을 `SUM_LOCK_TIME`으로 재려 했다. 실제로 쿼리를 돌려보니 값이 전부 0이었다. 그런데 같은 DB의 전역 카운터는 이랬다.

```
Innodb_row_lock_waits = 377
Innodb_row_lock_time  = 152,502ms
```

**행 락 대기가 실제로 377번 있었는데 digest의 `SUM_LOCK_TIME`은 전부 0이었다.** MySQL의 `LOCK_TIME`은 **테이블 락 전용**이라 InnoDB 행 락이 들어오지 않는다. 컬럼 이름만 보고 쿼리를 돌려보지 않은 것이다.

→ 패스 C를 **쓰기 경로 스캔**(`검사행 >> 변경행` = 과잉 락)으로 바꿨다.

**A-2. `DIGEST_TEXT`는 1024자에서 잘린다**

스케줄러 쿼리를 `DIGEST_TEXT LIKE '%meetup%'`로 찾으려 했는데 **한 건도 안 잡혔다.** 쿼리는 digest에 분명히 있었는데도.

```
max_digest_length = 1024          ← MySQL 기본값
저장된 digest 길이: 948자
digest 끝부분: ... `o1_0`.`warning_count`, `m1_0`.`status`   ← SELECT 목록 중간에서 끊김
```

컬럼이 많아서 **`FROM meetup ... WHERE`까지 도달하지 못하고 잘렸다.** 테이블명이 digest에 아예 존재하지 않았다. 이건 `FOR UPDATE`(비관적 락)도 똑같이 못 잡는다는 뜻이다 — 쿼리 맨 끝이니까.

→ `max_digest_length = 4096`으로 올렸다.

**A-3. N+1은 "검사행"으로는 안 보인다**

N+1은 개별 쿼리가 **값싸고 대신 수백 번 반복**된다. 검사행순으로 정렬하면 상위권에 아예 안 뜬다.

→ **호출횟수순 패스(패스 B)를 추가**했다. 결과적으로 이 패스가 이번 감사에서 가장 많은 걸 잡았다.

> **교훈: 계기판이 틀린 채로 감사를 시작하면 11개 도메인 문서를 전부 다시 돌려야 한다.**

### B. 스케줄러가 모든 측정을 오염시키고 있었다

`MeetupChatRoomRecoveryScheduler`는 `@Scheduled(fixedDelay = 300_000)` — **5분마다** 돈다. 도메인 하나 감사에 5분 넘게 걸리니 **반드시 측정 중에 끼어든다.** 하필 이게 **1만 행 검사 / 0행 반환 / 인덱스 미사용**인 그놈이라, 패스 A 상위권에 뜨면서 **감사 중인 도메인이 범인인 것처럼 보인다.**

`@EnableScheduling`이 `PetoryApplication`에 직접 붙어 있어 프로퍼티로 끌 수 없었다. 별도 설정으로 분리했다.

```java
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "petory.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig { }
```

기본값이 `true`라 **운영 동작은 그대로**고, 감사할 때만 끈다.

**A/B로 검증했다.** `fixedDelay`는 기동 직후 즉시 발화하므로 이걸로 대조가 된다.

| | digest 결과 |
|---|---|
| 스케줄러 ON | `SELECT m1_0.idx, ...` **1회 / 10,000행 검사** |
| 스케줄러 OFF | **5,000행 이상 쿼리 0건** |

> 스케줄러 **로그가 안 찍히는 것은 증거가 아니다.** 고아 모임이 0건이면 이 스케줄러는 로그를 남기지 않는다. digest를 봐야 알 수 있었다.

### C. 성능 감사를 하다가 기능 버그를 찾았다

```
GET /api/care-requests/search?keyword=산책   →   HTTP 500

java.sql.SQLException: Can't find FULLTEXT index matching the column list
```

쿼리는 `MATCH(cr.title, cr.description) AGAINST(...)`를 쓰는데 **`carerequest`에 FULLTEXT 인덱스가 없었다.**

**MySQL은 FULLTEXT 인덱스 없이 `MATCH ... AGAINST`를 실행하지 못한다. 느린 게 아니라 에러다.** 데이터가 0건이든 100만 건이든 **항상 500**이다. 즉 이건 성능 문제가 아니라 **기능이 통째로 죽어 있던 것**이다.

`board`는 같은 형태의 인덱스를 처음부터 갖고 있었다. **care만 빠져 있었다.**

```sql
-- V2__care_search_fulltext_index.sql
ALTER TABLE carerequest ADD FULLTEXT INDEX idx_carerequest_title_desc (title, description);
```

**A/B/A로 증명했다:** 인덱스 있음(200) → 제거(**500**) → 재적용(200).

인덱스 하나로 **공개 API와 관리자 API 두 개가 함께 살아났다.** 둘이 같은 `MATCH()` 쿼리를 쓰고 있었다.

> **SQL만 손으로 던졌다면 영원히 못 봤을 문제다.** 실제 API를 호출했기 때문에 나왔다.

### D. N+1인 줄 알았던 것이 N+1이 아니었다

패스 B(호출횟수순)가 meetup에서 신호를 줬다. curl 9회를 던졌는데 users 62회, 참가자 17회.

N+1이라고 결론 내리기 직전에 **숫자를 맞춰봤다.**

```
검색 결과 500건  ÷  @BatchSize(50)  =  참가자 쿼리 10회   ✅ 정확히 일치
```

**N+1이었다면 500회가 나왔어야 한다. 10회가 나왔다. 배칭은 제대로 되고 있었다.**

진짜 원인은 **결과가 500건이라는 것 자체**였다. 검색에는 페이징 파라미터가 아예 없었고, DB에서 전량 조회한 뒤 `subList`로 메모리에서 잘랐다 — **DB는 이미 500건을 다 읽은 뒤였다. 자르는 시점이 틀렸다.**

```java
// Before — DB가 일을 다 한 뒤에 메모리에서 자른다
List<Meetup> meetups = meetupRepository.findByKeyword(keyword);
return converter.toDTOList(meetups.size() > MAX_LIST_SIZE
        ? meetups.subList(0, MAX_LIST_SIZE) : meetups);

// After — FULLTEXT 2단계 쿼리의 1단계에 Pageable을 태워 DB LIMIT으로 내린다
List<Long> ids = findIdxByFulltextKeyword(keyword, pageable);
return findByIdxInWithOrganizer(ids);
```

**500건 / 53쿼리 / 583ms → 20건 / 6쿼리 / 43ms.**

> **N+1을 고치려 들었다면 이미 잘 짜인 배칭 코드를 건드릴 뻔했다.**
> 신호를 봤을 때 바로 이름을 붙이지 말고, **숫자를 맞춰봐야 한다.** 500 ÷ 50 = 10.

### E. 진짜 N+1은 관리자 API에 딱 하나 있었다

공개 API 10개 도메인에는 N+1이 **0건**이었다. 그래서 종합 문서에 "N+1 없음"이라고 썼다. **그리고 admin을 재보니 있었다.**

`/api/admin/care-requests`를 `size`를 바꿔가며 쟀다.

| size | pets | 첨부 | 백신 | 총 쿼리 |
|---|---|---|---|---|
| 10 | 10 | 10 | 10 | 36 |
| 20 | 20 | 20 | 20 | 66 |
| 40 | 40 | 40 | 40 | **127** |

**결과 건수에 1:1로 정확히 비례한다.** 회당 검사행은 1행(PK 룩업). 배칭이었다면 size 40에서도 `IN (...)` 쿼리 1개여야 한다.

원인이 선명했다 — **같은 테이블에 fetch join이 있는 쿼리와 없는 쿼리가 공존**했다.

```java
// 공개 API — JOIN FETCH 있음  ✅
"SELECT cr FROM CareRequest cr JOIN FETCH cr.user u LEFT JOIN FETCH cr.pet WHERE ..."

// 관리자 API — JOIN FETCH 없음  🔴   ← 공개 쪽만 고치고 여기는 빠뜨렸다
"SELECT r FROM CareRequest r WHERE (:status IS NULL OR ...) ORDER BY r.createdAt DESC"
```

**교훈: "N+1 없음"은 측정한 범위에 대해서만 참이다.** admin은 트래픽이 적어 후순위로 밀렸고, 그래서 가장 늦게 봤고, 거기에 있었다.

### F. 지리 검색은 B-tree로 안 된다

care 주변검색이 3,000행 풀스캔이었다. 복합 인덱스를 걸었다.

```sql
ALTER TABLE carerequest ADD INDEX idx_carerequest_geo (is_deleted, latitude, longitude);
```

**측정해보니 아무 효과가 없었다.** 3,261행 → 3,141행. 여전히 `Table scan on cr`.

**왜 안 되는가 — 두 가지가 겹쳤다:**

1. **`is_deleted`는 선택도가 0이다.** 전 행이 `0`이라 인덱스 선두 컬럼으로서 아무것도 걸러내지 못한다.
2. **`longitude`는 범위 조건으로 쓰이지 못한다.** B-tree 복합 인덱스는 **범위 조건을 선두에서 하나만** 쓸 수 있다. `latitude BETWEEN ...`이 범위를 잡아먹으면 뒤의 `longitude BETWEEN ...`은 인덱스로 못 거른다.

> **이건 SPATIAL 인덱스가 존재하는 이유 그 자체다.**

`meetup`·`locationservice`가 이미 쓰는 방식을 그대로 따랐다 — **POINT 컬럼 + SPATIAL 인덱스 + BEFORE INSERT/UPDATE 트리거.** 트리거가 위·경도에서 자동으로 채우므로 **엔티티 변경이 없고 `ddl-auto=validate`도 그대로 통과한다.**

쿼리도 `BETWEEN` → `ST_Within` + `ST_Distance_Sphere`로 바꿨다. **인덱스만 만들고 쿼리를 안 바꾸면 무용지물이다.**

| | Before | After |
|---|---|---|
| 계획 | `Table scan on cr` | `Index range scan using idx_carerequest_geo_point_spatial` |
| 검사행 | **3,000** | **208** |
| 선택도 추정 | 예상 3.77행 / 실제 783행 (**208배 오판**) | 예상 190행 / 실제 208행 (**거의 정확**) |

> 옵티마이저가 208배나 오판한 이유는 **위도와 경도를 독립 조건으로 곱했기** 때문이다. 실제로는 강하게 상관되어 있다 — 서울 안의 점은 위도도 경도도 같이 서울 범위다.

### G. 고치는 도중에 내가 버그를 두 개 만들었고, 재측정으로 잡았다

**하나. `Page.map(converter::toDTO)`로 N+1을 도로 만들었다.**

pets에 페이징을 붙이고 재보니 20건에 쿼리가 **24개**(예상 5개)였다. `Page.map()`은 **단건 변환기를 행마다 호출**하는데, 그 단건 `toDTO`가 펫마다 첨부를 조회한다.

**컨버터 javadoc에 이미 경고가 있었다.**

```java
/**
 * Entity → DTO 변환 (단일 객체용, File 개별 조회)
 * [주의] 리스트 변환 시에는 toDTOList() 사용 권장 (배치 조회로 N+1 방지)
 */
public PetDTO toDTO(Pet pet) { ... }
```

경고를 안 읽고 밟은 것이다. `toDTOList`(배치 `IN` 조회)로 바꿔 24 → 5로 떨어뜨렸다.

**둘. 페이징을 붙이자 없던 COUNT가 생겼다.**

`List` → `Page<>`로 바꾸면 **COUNT 쿼리가 따라온다.** 그 COUNT가 pets 12,000행짜리 테이블에서 **19,667행**을 검사했다.

```
-> Intersect rows sorted by row ID                              ← 인덱스 머지
    -> Index range scan using idx_pets_deleted (is_deleted=0)   → 12,000행
    -> Index range scan using idx_pets_type (pet_type='DOG')    →  7,667행
                                                    합계 = 19,667행
```

복합 인덱스가 없어서 MySQL이 **두 인덱스를 각각 읽고 교집합**을 구했다. `(pet_type, is_deleted)` 복합 인덱스를 추가해 7,667행으로 줄였다.

> **이게 이 감사의 출발점이 된 실수와 정확히 같은 종류다** — 목록 SELECT만 보고 COUNT를 놓치는 것.
> **재측정을 안 했다면 두 버그를 그대로 둔 채 "고쳤다"고 보고했을 것이다.**

**막다른 길도 하나 기록해둔다.** 이 저장소는 `BoardListQueryPlanMaintainer`에서 히스토그램으로 선택도 오판을 고친 전례가 있어 먼저 시도했다. **안 먹혔다** — MySQL은 인덱스가 있는 컬럼에는 히스토그램을 쓰지 않는다. 안 쓰이는 변경은 남기면 안 되니 걷어냈다.

### H. 회귀 테스트 — "초록불인데 아무것도 검증하지 않는" 상태를 피하기

**"고치면 테스트를 붙인다"로는 부족하다.** 2단계를 요구했다.
**① 수정 전 상태에서 테스트가 실제로 빨간불이 되는지 먼저 확인** → ② 수정 후 초록불을 확인.
①을 건너뛰면 아무것도 검증하지 않는 테스트가 초록불만 켜고 있게 된다.

**핵심 불변식은 "쿼리 수가 결과 건수에 비례하지 않는다"** 이다.

```java
long small = countQueries(() -> adminFacade.getCareRequests(null, null, null, 0, 5));
long large = countQueries(() -> adminFacade.getCareRequests(null, null, null, 0, 40));

assertThat(large - small).isLessThanOrEqualTo(3);   // 8배 늘려도 쿼리는 늘지 않아야 한다
```

쿼리 수 **상한**(예: "20개 미만")만 걸면 **결과가 10건일 때는 통과하고 100건일 때만 터지는 테스트**가 된다. N+1의 정의가 "결과 수에 비례"이므로, size를 바꿔 **증가분**을 봐야 한다.

**그리고 테스트를 짜다가 계기판이 세 번 고장났다.**

**H-1. `getQueryExecutionCount()`는 지연로딩을 세지 않는다.**
이 지표로 쿼리를 셌더니 `JOIN FETCH`를 제거해도 **테스트가 통과했다.** 그래서 "JOIN FETCH는 불필요했다"고 판단하고 **되돌렸다.** 실 API로 재보니 27/47/88로 여전히 비례했다 — Hibernate의 `getQueryExecutionCount()`는 **JPQL/네이티브 쿼리만 세고 지연로딩 엔티티 로드(PK 단건 조회)는 세지 않는다.** `getPrepareStatementCount()`로 바꾸니 그제야 잡혔다(15 → 85).

> **눈먼 지표 때문에 맞는 수정을 되돌릴 뻔했다.**

**H-2. Gradle이 테스트를 안 돌리고 이전 결과를 재생했다.**
인덱스를 지우고 돌렸는데 통과하길래 파보니 `Task :test UP-TO-DATE`였다. **DB 상태는 Gradle의 입력이 아니라서**, 코드가 안 바뀌면 아예 실행을 건너뛴다. `cleanTest`가 필요했다.

**H-3. 테스트는 개발 DB가 아니라 `petory_test`를 쓴다.**
`build.gradle`이 테스트 DB를 분리해놨다. `petory`에서 인덱스를 지워봐야 테스트엔 아무 영향이 없었다.

> **세 번 다 "초록불인데 실제로는 아무것도 검증하지 않는" 상태였다.**
> 2단계 검증(①먼저 빨간불 확인)이 없었다면 셋 다 놓쳤을 것이다.

---

## 4. `section#limits` — 한계 & 다음 개선

이 감사로 **관측할 수 없는 것**을 명시해둔다. "문제없음"과 "측정 못 함"은 다르다.

- **락 경합은 이 감사로 원리적으로 관측 불가능하다.** `curl`을 한 번에 하나씩 던지므로 경합할 상대가 없다. 관측 가능한 건 **과잉 락**(잠글 필요 없는 행까지 잠금 — 요청 1개로도 보인다)이고, 경합 자체는 별도의 동시성 부하 테스트가 필요하다. 단, 과잉 락을 먼저 없애면 경합은 대부분 따라 사라진다.
- **자동생성 COUNT 16개가 남아 있다.** `countQuery`를 명시하지 않으면 Hibernate가 **본문 쿼리의 JOIN을 그대로 물고 COUNT를 만든다.** care 목록의 SELECT는 30행으로 고쳤지만 **그 옆의 COUNT는 아직 6,000행**이다. 이 감사의 출발점이 "목록만 보고 COUNT를 놓친 것"이라 특히 눈에 걸린다.
- **board 깊은 페이지.** `page=2500`에서 목록 SELECT가 **100,000행 검사 / 0행 반환 / 129ms**로 폭발한다. 인덱스는 정상적으로 타는데 `OFFSET` 자체가 5만 행을 만들어 버리는 구조라 **인덱스로는 못 고친다.** 키셋 페이징이면 COUNT 문제까지 같이 사라지지만 "N페이지 점프"를 포기해야 한다 — **성능이 아니라 제품 결정이다.**
- **스케줄러 8개 미측정.** 전부 cron(일/시) 기반이라 감사 시각에 발화하지 않았다. `MeetupChatRoomRecoveryScheduler`(5분마다 1만 행 / 0행 반환)만 잡았다.
- **notification / file 도메인은 측정할 데이터가 없다.** 시드에 알림이 0건이고 `file` 테이블이 비어 있다. **"문제없음"이 아니라 "미검증"이다.**
- **chat은 시드가 작아 대규모 시나리오를 못 만든다.** 대화방당 평균 5건(최다 9건)이라 페이징에 부하가 안 걸린다. 인덱스 설계는 커버링 인덱스로 이상적이지만, 수천 메시지짜리 대화방은 검증되지 않았다.

---

## 5. `section#docs` — 관련 페이지

- 내부: `/domains/care`, `/domains/meetup`, `/domains/board` (각 도메인 페이지)
- 내부: `/domains/board/optimization` (게시글 목록 N+1 — 이 감사의 출발점이 된 작업)
- GitHub: `docs/analysis/query-audit/00-plan.md` — 감사 방법론
- GitHub: `docs/analysis/query-audit/99-summary.md` — 전체 결과 종합
- GitHub: `docs/analysis/query-audit/fixes-2026-07-14.md` — 처방 6건 + 회귀 테스트
- GitHub: PR #246 — 실제 반영 커밋

---

## 6. 이 페이지에서 하지 말아야 할 것 (작성 시 주의)

- **"성능을 N배 개선했습니다"로 시작하지 말 것.** 이 페이지의 가치는 수치가 아니라 **틀린 걸 어떻게 발견했는가**다.
- **실패를 숨기지 말 것.** 6번 틀렸고, 그게 콘텐츠다:
  ① COUNT 쿼리를 놓침 → ② `SUM_LOCK_TIME` 오용 → ③ digest 절단 → ④ N+1 오진 직전 → ⑤ `Page.map`으로 N+1 재생산 → ⑥ 눈먼 지표로 맞는 수정을 되돌릴 뻔.
- **미측정 범위를 "문제없음"으로 쓰지 말 것.** limits 섹션에 그대로 남긴다.
- 수치는 전부 `docs/analysis/query-audit/`에 근거가 있다. **근거 없는 숫자를 새로 만들지 말 것.**
