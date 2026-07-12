---

## date: 2026-07-12

domains: [location]
type: performance-evidence
problem: overfetching
status: verified
metric: "worktree 실제 커밋: 22.4MB→100KB, 531.8ms→50.9ms. size=30000 트릭(검증됨, 오차<1%): 22.3MB→100KB (-99.6%), 602ms→49ms (-91.9%); DEFAULT_RADIUS_LIMIT=100 신규 발견"
before_commit: 5ef571d9
after_commit: 162ebc14
related: [docs/troubleshooting/location/initial-load-performance.md]

# Location 초기 로드 재검증 — 실제 API 실측 + EXPLAIN (2026-07-12)

> 목적: `troubleshooting/location/initial-load-performance.md`(22,699건 전체조회 → 1,026건 반경조회, 1,484ms→700ms)를 **현재 코드·현재 API로 다시 실행**해 재현성을 확인한다. 옛 문서 작성 이후 아키텍처가 한 세대 더 진화했기 때문에(공간 인덱스 R-Tree 도입, `size` 파라미터 필수화), 그 차이를 먼저 명시하고 실측한다.

## 0. 먼저 확인한 것 — 아키텍처가 이미 바뀌어 있다

옛 문서의 "before"는 `size=null`(무제한) 파라미터로 **전체 22,699건을 매번 로드**하는 경로였다. 현재 `LocationServiceService`(§1 코드 확인)를 읽어보면:

- `searchLocationServicesByLocation()`(반경검색): `limit = maxResults ?? DEFAULT_RADIUS_LIMIT(100)` — 항상 상한이 걸린다
- `searchLocationServicesByRegion()`(지역/기본검색, sido·sigungu 없을 때 `findByOrderByRatingDesc` 사용): `limit = maxResults ?? 50` — 이것도 기본 50건 제한

**즉 "무제한 전체조회"라는 옛 시나리오 자체가 현재 코드에는 없다.** `size`(=maxResults)를 그대로 `LIMIT :limit`에 꽂는 구조라, 아주 큰 값(예: 30000)을 명시적으로 주지 않는 한 절대 전체 데이터를 반환하지 않는다. 이번 재검증은 `size=30000`**을 강제로 줘서 "사실상 무제한"이던 옛 동작을 재현**하고, 실제 반경조회(파라미터 기본값)와 비교했다.

실제 해결 커밋: `[162ebc14](https://github.com/makkong1/Petory/commit/162ebc14)` (2025-12-21, `주변서비스 로직 수정`). 직전 커밋 `[5ef571d9](https://github.com/makkong1/Petory/commit/5ef571d9)`에 파라미터 없는 `findByOrderByRatingDesc()`(무제한 전체조회) 호출이 실제로 있음을 확인.

## 1. HTTP 레벨 실측 (실제 서버, 실제 API)

### 방법

- 로컬 MySQL(`petory`, locationservice 22,905건 — 옛 문서의 22,699건과 유사 규모, is_deleted=0)에 붙는 서버를 포트 8081로 별도 기동(`./gradlew bootRun --args='--server.port=8081'`)
- `POST /api/auth/register` → 로컬 계정 생성(`dev` 프로필, 이메일 인증 스킵) → `POST /api/auth/login`으로 JWT 획득 → 측정 종료 후 계정 삭제
- `curl -w '%{size_download}'`(응답 바이트) + `%{time_total}` 15회 평균, 동일 서버·동일 DB·동일 JWT

### 결과

|                     | Before 재현 (`size=30000`, 사실상 전체조회)    | After (반경조회, `latitude/longitude/radius=10000`, 기본 `size`)                    |
| ------------------- | ---------------------------------------------- | ----------------------------------------------------------------------------------- |
| 엔드포인트          | `GET /api/location-services/search?size=30000` | `GET /api/location-services/search?latitude=37.5665&longitude=126.978&radius=10000` |
| 응답 바이트         | **23,387,297 B (≈22.3MB)**                     | **102,146 B (≈100KB)**                                                              |
| 평균 응답시간(15회) | **602.2ms**                                    | **48.9ms**                                                                          |
| 반환 건수           | 22,905건(전체)                                 | 100건(`DEFAULT_RADIUS_LIMIT`)                                                       |

**응답 바이트 −99.6%, 응답시간 −91.9%.** 옛 문서(22MB→1MB, −95.5% / 1,484ms→700ms, −52.8%)보다 이번이 더 극적인데, 이유는 명확하다 — 반경조회 결과 자체를 100건으로 상한(`DEFAULT_RADIUS_LIMIT=100`)을 걸어놓은 게 지금 코드에 새로 추가돼 있어서다. 참고로 상한 없이 조회하면(`size=5000`) 반경 10km 내 실제 건수는 **2,499건**이다(§2 EXPLAIN의 필터 결과 행수와 일치). 옛 문서의 "1,026건"은 그 시점 데이터 분포·반경 계산 방식이 달라 직접 비교 대상은 아니다.

## 1.5. worktree 검증 — `size=30000` 트릭이 실제 옛 코드와 같았는지 확인

위 "Before 재현"은 **현재 코드**에 `size=30000`을 줘서 옛 동작을 흉내낸 것이다. 이게 실제로 그 시점 코드와 같은지, `git worktree`로 `5ef571d9`(before)를 실제 checkout해서 **파라미터 없이 자연스럽게 실행되는 진짜 무제한 조회**를 서버로 직접 띄워 측정했다.

이 시점엔 위치 기반(반경) 검색 자체가 아직 없다 — `searchLocationServicesByRegion()`이 sido/sigungu/eupmyeondong/roadName/category가 전부 비어 있으면 무조건 `findByOrderByRatingDesc()`(파라미터 없는 전체조회)를 탄다. 즉 트릭 없이 그냥 `GET /api/location-services/search`(파라미터 없음)만 호출하면 된다.

**방법**: 워크트리를 포트 8082로 별도 기동 → `5ef571d9`(before) 그대로 무제한 조회 실측 → 서버 종료 → 같은 워크트리에서 dev(after)로 checkout해 재기동 → 반경조회 실측. 계정은 측정 후 즉시 삭제.

|                     | Before(`5ef571d9`, 실제 커밋 코드, 파라미터 없음) | After(dev, 실제 반경조회) |
| ------------------- | ------------------------------------------------- | ------------------------- |
| 응답 바이트         | **22,379,448 B (≈22.4MB)**                        | **102,146 B (≈100KB)**    |
| 평균 응답시간(15회) | **531.8ms**                                       | **50.9ms**                |

**Before 수치(22.4MB, 531.8ms)가 위** `size=30000` **트릭 결과(22.3MB, 602.2ms)와 거의 일치한다** — 바이트는 0.4% 차이, 응답시간은 트릭 쪽이 오히려 더 크게 나왔다(단일 측정 변동 범위 안). **트릭이 실제 역사적 동작을 정확히 재현했음이 확인됐다.** After 수치도 오늘 앞서 측정한 dev 결과(102,146B, 48.9ms)와 정확히 같은 바이트, 비슷한 시간으로 재현성도 확인됐다.

## 2. EXPLAIN — 두 쿼리의 실행계획

### Before 재현 — 전체조회

```sql
EXPLAIN ANALYZE SELECT * FROM locationservice WHERE is_deleted=0 ORDER BY rating DESC;
```

```
-> Index lookup on locationservice using idx_locationservice_deleted_rating (is_deleted = 0)
   (cost=3027 rows=10123) (actual time=0.459..146 rows=22905 loops=1)
```

`(is_deleted, rating)` 복합 인덱스를 타서 스캔 자체는 빠르지만(146ms), **22,905행 전부를 인덱스 순서대로 읽어 반환**한다.

### After — 반경조회 (현재 코드, `ST_Within` POLYGON + `ST_Distance_Sphere`)

```sql
EXPLAIN ANALYZE
SELECT * FROM locationservice ls WHERE
  ST_Within(ls.location, ST_GeomFromText(<10km POLYGON>, 4326))
  AND ST_Distance_Sphere(ls.location, ST_GeomFromText(<POINT>, 4326)) <= 10000
  AND ls.is_deleted = 0
ORDER BY ST_Distance_Sphere(...) ASC LIMIT 100;
```

```
-> Limit: 100 row(s)  (actual time=81.8..81.8 rows=100 loops=1)
    -> Sort: st_distance_sphere(...), limit input to 100 row(s) per chunk  (actual time=81.8..81.8 rows=100)
        -> Filter: (is_deleted=0 and st_within(...) and st_distance_sphere(...)<=10000)
           (cost=625 rows=1389) (actual time=3.96..62.5 rows=2499 loops=1)
            -> Index range scan on idx_locationservice_location_spatial over (location ...)
               (cost=625 rows=1389) (actual time=1.74..28.3 rows=3075 loops=1)
```

**SPATIAL 인덱스(`idx_locationservice_location_spatial`, R-Tree)**로 후보 3,075행만 골라낸 뒤, `ST_Within`/`ST_Distance_Sphere` 필터로 2,499행으로 좁히고, 거리순 정렬 후 100건만 반환한다. actual time **81.8ms** — 순수 DB 실행시간은 전체조회(146ms)보다도 짧다.

### 해석

|              | 스캔 방식               | 실제 처리 행수    | DB 실행시간 |
| ------------ | ----------------------- | ----------------- | ----------- |
| Before(전체) | 인덱스지만 전체 순회    | 22,905행          | 146ms       |
| After(반경)  | 공간 인덱스로 후보 축소 | 3,075→2,499→100행 | 81.8ms      |

DB 실행시간 격차(146→81.8ms, 44%)보다 **HTTP 레벨 격차(602→49ms, 92%)가 훨씬 큰 이유**는 응답 페이로드 크기다. 22,905행을 JSON 직렬화해 22MB를 네트워크로 내보내는 비용이, DB 쿼리 자체보다 훨씬 크게 응답시간에 반영된다. 이건 Board N+1 재검증(§EXPLAIN)에서 확인한 "JDBC 왕복 오버헤드가 DB 실행시간보다 크다"는 패턴과 다른 종류지만, 같은 교훈이다 — **엔드투엔드 응답시간은 DB EXPLAIN만으로는 다 설명되지 않고, 반환 데이터량·직렬화·전송이 지배적일 수 있다.**

## 3. 재현 방법

```bash
./gradlew bootRun --args='--server.port=8081'

TOKEN=$(curl -s -X POST localhost:8081/api/auth/register -H 'Content-Type: application/json' \
  -d '{"id":"loctest","password":"Loctest1234!","username":"loctest","nickname":"측정","email":"loctest@petory.local","role":"USER"}' > /dev/null; \
  curl -s -X POST localhost:8081/api/auth/login -H 'Content-Type: application/json' \
  -d '{"id":"loctest","password":"Loctest1234!"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['accessToken'])")

# Before 재현 (사실상 전체조회)
curl -s -o /dev/null -w '%{size_download} %{time_total}\n' -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8081/api/location-services/search?size=30000"

# After (반경조회)
curl -s -o /dev/null -w '%{size_download} %{time_total}\n' -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8081/api/location-services/search?latitude=37.5665&longitude=126.9780&radius=10000"

# 측정 후 계정 삭제
mysql -uroot -p petory -e "DELETE FROM users WHERE id='loctest';"
```

## 4. 관련 문서

- 원본(2025-12-21 측정, 아키텍처 이전 세대): `[troubleshooting/location/initial-load-performance.md](../../../troubleshooting/location/initial-load-performance.md)`
- 공간 인덱스 세대 정보: `docs/analysis/리팩토링-순서-2026-07.md` §3
- 대표 사례 선정: `[portfolio-refactoring-troubleshooting-selection.md](../../portfolio-refactoring-troubleshooting-selection.md)`
