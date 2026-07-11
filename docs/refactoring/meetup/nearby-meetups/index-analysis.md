# 인덱스 분석 및 쿼리 최적화 결과

## 📋 현재 인덱스 현황

| 인덱스명                       | 컬럼                    | 타입             | 설명                      |
| ------------------------------ | ----------------------- | ---------------- | ------------------------- |
| `idx_meetup_geo_point_spatial` | `geo_point` (POINT)     | SPATIAL (R-Tree) | 위치 조회용 (4단계, 현행) |
| `idx_meetup_location`          | `(latitude, longitude)` | COMPOSITE INDEX  | 위치 조회용 (3단계)       |
| `idx_meetup_date_status`       | `(date, status)`        | COMPOSITE INDEX  | 날짜+상태 필터링용        |
| `idx_meetup_date`              | `date`                  | INDEX            | 날짜 필터링용             |
| `idx_meetup_status`            | `status`                | INDEX            | 상태 필터링용             |

> `geo_point`은 `POINT NOT NULL SRID 4326` 컬럼이며, `latitude/longitude` INSERT·UPDATE 시 트리거(`trg_meetup_set_geo_point_*`)로 자동 동기화된다.

---

## ✅ 최종 적용된 쿼리 (Bounding Box 방식)

```sql
SELECT m.* FROM meetup m
WHERE m.date > :currentDate
  AND (m.status IS NULL OR m.status != 'COMPLETED')
  AND (m.is_deleted = false OR m.is_deleted IS NULL)
  AND m.latitude BETWEEN (:lat - :radius / 111.0) AND (:lat + :radius / 111.0)
  AND m.longitude BETWEEN (:lng - :radius / (111.0 * cos(radians(:lat))))
                      AND (:lng + :radius / (111.0 * cos(radians(:lat))))
  AND (6371 * acos(...)) <= :radius
ORDER BY (6371 * acos(...)) ASC, m.date ASC
```

**Bounding Box 계산식**:

- 위도 1도 ≈ 111km
- 경도 1도 ≈ 111km × cos(위도)
- 반경 5km → 위도 ±0.045도, 경도 ±0.045/cos(위도)도

---

## 📊 EXPLAIN 실행 계획 결과

### Before (인덱스 미사용)

```
type: ALL
key: NULL
rows: 2958
filtered: 4.86%
Extra: Using where; Using filesort
```

### After (Bounding Box - 인덱스 사용 성공) ✅

```
type: range
key: idx_meetup_location
key_len: 18
rows: 117
filtered: 0.60%
Extra: Using index condition; Using where; Using filesort
```

### 개선 효과

| 항목     | Before  | After               | 개선율         |
| -------- | ------- | ------------------- | -------------- |
| **type** | ALL     | range               | ✅ 인덱스 사용 |
| **key**  | NULL    | idx_meetup_location | ✅ 인덱스 활용 |
| **rows** | 2958 개 | 117 개              | **96.0% 감소** |

---

## 💡 최적화 과정

1. ❌ 조건 순서 재배치 → 인덱스 미사용
2. ❌ 서브쿼리 방식 → 인덱스 미사용
3. ✅ **Bounding Box 방식** → `idx_meetup_location` 인덱스 사용 성공

**성공 이유**: `BETWEEN` 조건으로 인덱스 활용 가능 (`IS NOT NULL` 조건 제거)

---

## 🚀 4단계 (현행): 공간 인덱스(R-Tree)로 전환

### 배경: B+Tree 복합 인덱스의 2D 한계

3단계의 `idx_meetup_location (latitude, longitude)`는 **1차원 정렬 구조**다. `lat BETWEEN … AND lng BETWEEN …`를 날려도 선두 컬럼(`latitude`)만 인덱스 range로 좁히고, 그 위도 밴드 안에서 `longitude`는 인덱스로 가지치기되지 않고 사후 필터링(ICP)된다. 3단계 EXPLAIN이 `range`인데도 117행을 스캔한 것이 이 때문이다 — 경도 차원이 인덱스로 잘리지 않았다. 데이터가 같은 위도대에 넓게 분포할수록 이 "가로 띠 스캔" 비용은 선형 증가한다.

### 적용 쿼리 (`geo_point` + `ST_Within` + `ST_Distance_Sphere`)

`SpringDataJpaMeetupRepository.findNearbyMeetupIds` 실 코드 기준:

```sql
SELECT m.idx FROM meetup m
WHERE m.date > :currentDate
  AND (m.status IS NULL OR m.status NOT IN ('COMPLETED', 'CANCELLED'))
  AND (m.is_deleted = false OR m.is_deleted IS NULL)
  AND m.latitude IS NOT NULL AND m.longitude IS NOT NULL
  AND ST_Within(m.geo_point, ST_GeomFromText(CONCAT('POLYGON((' … '))'), 4326))   -- 공간 인덱스 사용
  AND ST_Distance_Sphere(m.geo_point,
        ST_GeomFromText(CONCAT('POINT(', :lat, ' ', :lng, ')'), 4326)) <= (:radius * 1000)  -- 정밀 반경
ORDER BY ST_Distance_Sphere(...) ASC, m.date ASC
LIMIT :limit
```

- `ST_Within(geo_point, 폴리곤)`: MBR 포함 여부를 **R-Tree로 2D 동시 가지치기** → 후보를 질의 박스 면적에 비례하게 축소
- `ST_Distance_Sphere`: SRID 4326 구면 거리(m 단위)로 정밀 필터 → 평면 근사(111km/도) 대비 의미적으로 정확
- ID만 반환 후 `findByIdxInWithOrganizer`로 배치 페치 → 후보 로딩 최소화 + N+1 방지

### EXPLAIN 실측 결과 (현재 데이터 기준)

측정 환경(lat=37.5665, lng=126.9780, radius=5.0, limit=500) 파라미터 치환본은 `explain-queries.sql` 참조. `EXPLAIN ANALYZE` 실측 계획(요약):

```
-> Limit: 500 row(s)
  -> Sort: st_distance_sphere(...), m.date, limit input to 500 row(s) per chunk
    -> Filter: (... status/is_deleted ...) and st_within(m.geo_point, <POLYGON>)
               and (st_distance_sphere(m.geo_point, <POINT>) <= 5000)
      -> Index range scan on m using idx_meetup_date
         over ('2026-07-11 ...' < date), with index condition: (m.date > now())
```

| 항목           | 기대                           | **실측 (현재 데이터)**                 |
| -------------- | ------------------------------ | -------------------------------------- |
| 선택된 인덱스  | `idx_meetup_geo_point_spatial` | **`idx_meetup_date`** (date range)     |
| 공간 술어 처리 | 인덱스로 2D 가지치기           | **post-filter** (Filter 단계에서 평가) |

> ⚠️ **공간 인덱스가 존재하지만 옵티마이저가 선택하지 않았다.** 이는 오류가 아니라 비용 기반 옵티마이저의 정상 판단이다.

### 왜 옵티마이저가 공간 인덱스를 안 골랐나 (해석)

MySQL 옵티마이저는 공간 인덱스를 후보에 두되, 다른 접근 경로와 **비용을 비교**해 최저 비용 경로를 고른다. 현재 데이터에서는:

- `date > NOW()` 조건이 소량 데이터에서 이미 극도로 선택적 → 옵티마이저 추정 `rows=1`
- 이 상태면 `idx_meetup_date`의 B+Tree range 스캔이 가장 싸다고 계산됨
- 결과적으로 date 인덱스로 소수 행만 뽑고, `ST_Within`/`ST_Distance_Sphere`는 그 행들에 대한 post-filter로 처리

즉 **데이터가 적어 date 필터만으로 충분히 좁혀지는 지금은 공간 인덱스가 비용상 이득이 없다.**

### 데이터가 늘어나면 달라진다 (forward-looking)

모임 수가 크게 늘고 **미래 날짜 모임이 흔해지면** `date > NOW()`의 선택도가 급격히 떨어진다(대부분 행이 조건 통과). 그러면:

- date 인덱스 경로는 넓은 date 범위를 스캔하게 되어 비용 상승
- 상대적으로 `ST_Within`(질의 박스 포함) 공간 술어가 가장 선택적인 필터가 됨
- 이 시점에서 옵티마이저가 `idx_meetup_geo_point_spatial`로 전환할 여지가 생김

따라서 공간 인덱스는 **현재 즉효 최적화가 아니라, 데이터 규모·밀도 증가에 대비한 선제적 구조 결정**이다. 소규모 테스트셋(현재)에서는 date 인덱스가 이기고, 대규모·지리적 밀집 환경에서 공간 인덱스의 2D 가지치기 이점이 드러난다.

> 검증 팁: `FORCE INDEX (idx_meetup_geo_point_spatial)`로 공간 경로를 강제해 두 경로의 실측 비용을 직접 비교할 수 있다. 단, 현재 데이터에서는 옵티마이저 기본 선택(date 인덱스)이 더 빠를 수 있다.

> ⚠️ 3단계 측정 환경(테스트 데이터 1,000건)과 동일 조건의 1~3단계 재실측은 미기록이다. 위 4단계 실측은 현재 데이터 기준 EXPLAIN 계획이며, 구조적 선택 근거와 함께 해석한다.

### 선택 이유 (구조적 근거)

| #   | 항목              | B+Tree 복합 (3단계)                      | 공간 인덱스 R-Tree (4단계)                        |
| --- | ----------------- | ---------------------------------------- | ------------------------------------------------- |
| 1   | 차원 가지치기     | 위도 1축만, 경도는 사후 필터             | 위도·경도 2축 동시 가지치기                       |
| 2   | 확장성(밀도↑)     | 위도 밴드에 무관한 행 급증 → 선택도 악화 | 후보 수가 질의 박스 **면적**에 비례 → 격차 확대   |
| 3   | 의미적 정확성     | 평면 근사(111km/도) + WHERE 절 Haversine | SRID 4326 지오메트리 + `ST_Distance_Sphere`(구면) |
| 4   | 옵티마이저 견고성 | 컬럼 순서·`IS NOT NULL`·함수 래핑에 취약 | `ST_Within` 공간 전용 술어 → SPATIAL 인덱스 안정  |
| 5   | 확장 여지         | 점(point) 조회 한정                      | 폴리곤(행정구역)·라인 등 지오메트리 질의로 확장   |

핵심은 **2번(확장성)** 이다. 소규모 테스트셋에선 3단계도 충분하지만, 서울처럼 동일 위도에 데이터가 옆으로 넓게 퍼지는 밀집 환경에서 B+Tree는 위도 밴드 전체를 긁어 선택도가 무너지고, R-Tree는 박스 면적에만 비례해 버틴다.

---

## 📝 참고

- EXPLAIN 쿼리: `explain-queries.sql` 참조
- 성능 비교: [performance-comparison.md](./performance-comparison.md)
