# DB 개념 어필 포인트 — Location 도메인

> 코드베이스 실측 데이터 기준 (실제 파일 확인)
>
> 참조 파일:
> - `backend/main/java/com/linkup/Petory/domain/location/repository/SpringDataJpaLocationServiceRepository.java`
> - `backend/main/java/com/linkup/Petory/domain/location/service/LocationServiceService.java`
> - `backend/main/resources/sql/migration/locationservice-index-optimization.sql`
> - `backend/main/resources/sql/migration/locationservice-add-review-count-column.sql`
> - `backend/main/resources/sql/migration/locationservicereview-add-soft-delete-columns.sql`
> - `docs/troubleshooting/location/initial-load-performance.md`

---

## 1. 공간 쿼리 — ST_Within 바운딩박스 + ST_Distance_Sphere 이중 조건

### 어필 포인트

- DB 스키마에 `location` 컬럼(`POINT`, SRID 4326)이 별도로 존재하며, 공간 인덱스(`idx_locationservice_location_spatial`)가 설정되어 있다.
- JPA 엔티티에는 `latitude`/`longitude`(`Double`)만 매핑하고, 공간 컬럼(`POINT`)은 엔티티에 매핑하지 않는다(Hibernate Spatial 미도입). 반경 검색은 네이티브 쿼리로만 처리한다.
- `findByRadius` 쿼리는 1차 필터(`ST_Within` — POLYGON 근사 사각형으로 공간 인덱스를 타게 함) + 2차 필터(`ST_Distance_Sphere` — 원형 정밀 조건)를 조합한다.

```sql
-- SpringDataJpaLocationServiceRepository.findByRadius (핵심 발췌)
SELECT * FROM locationservice ls WHERE
ST_Within(ls.location, ST_GeomFromText(
    CONCAT('POLYGON((', :lat - (:r / 111000.0), ' ', :lng - (:r / (111000.0 * COS(RADIANS(:lat)))), ', ',
                        ...
    '))', 4326))
AND
ST_Distance_Sphere(ls.location,
    ST_GeomFromText(CONCAT('POINT(', :latitude, ' ', :longitude, ')'), 4326)) <= :radiusInMeters
AND ls.is_deleted = 0
AND (:keyword IS NULL OR ls.name LIKE CONCAT('%', :keyword, '%'))
AND (:category IS NULL OR ls.category3 = :category OR ls.category2 = :category OR ls.category1 = :category)
ORDER BY
    CASE WHEN :sort = 'reviews' THEN ls.review_count END DESC,
    CASE WHEN :sort = 'rating'  THEN ls.rating END DESC,
    ST_Distance_Sphere(...) ASC, ls.rating DESC, ls.idx ASC
```

- `ST_Within`으로 공간 인덱스(R-Tree)를 활용해 후보를 빠르게 줄이고, `ST_Distance_Sphere`로 원형 경계를 정밀하게 검증하는 2단계 구조다.
- 위도 1도 ≈ 111000m, 경도 1도 ≈ 111000m × cos(lat)를 사용해 동적으로 POLYGON을 생성한다.
- 정렬은 `reviews`, `rating`, `distance`(기본) 세 가지를 단일 쿼리로 처리한다. `review_count`는 캐시 컬럼을 사용한다(§3 참고).

### 말할 내용

> "위치 기반 반경 검색에서 `ST_Within`과 `ST_Distance_Sphere`를 이중으로 쓴 이유가 있습니다. `ST_Distance_Sphere`만 단독으로 쓰면 공간 인덱스를 타지 않아 풀스캔이 됩니다. 그래서 먼저 위도·경도 범위로 동적 POLYGON을 만들어 `ST_Within`으로 공간 인덱스를 타게 하고, 그 후보 집합에 `ST_Distance_Sphere`로 원형 정밀 필터를 추가했습니다. JPA 엔티티에는 `latitude`/`longitude` Double 필드만 두고, 공간 컬럼 `location`(POINT)은 엔티티에 매핑하지 않습니다. Hibernate Spatial 없이 네이티브 쿼리로만 처리합니다."

---

## 2. 검색 분기 우선순위 설계 — 위치 우선, 빈 문자열 정규화

### 어필 포인트

- 통합 검색 진입점 `searchLocationServices`는 파라미터 조합에 따라 4단계로 분기한다:
  1. `latitude` + `longitude` 있음 → 반경 검색 (keyword·category는 SQL WHERE 필터)
  2. sido/sigungu/eupmyeondong/roadName 중 하나라도 있음 → 지역 계층 검색
  3. keyword만 있음 → FULLTEXT 전국 검색 (fallback)
  4. 아무 조건도 없음 → 전체 평점순

- 빈 문자열 정규화: 클라이언트가 `keyword=""`를 전송하면 SQL에서 `:keyword IS NULL`이 false가 되어 `name LIKE '%%'` 전체 매칭으로 동작하는 버그가 있었다. `normalize(value)` 메서드로 모든 빈 문자열·공백을 null로 변환한다.

```java
// LocationServiceService.normalize()
private static String normalize(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
}
```

- 위치 분기에서 keyword는 FULLTEXT가 아닌 `name LIKE '%keyword%'`로 처리된다. FULLTEXT는 위치·지역이 모두 없을 때만 사용하는 fallback이다.
- `radius`가 null이거나 0 이하이면 서비스 레이어에서 **10000m(10km)** 로 치환한다(`DEFAULT_RADIUS_METERS = 10_000`).

### 말할 내용

> "검색 파라미터 조합이 다양해서 분기 설계가 중요했습니다. 위치 좌표가 있으면 무조건 반경 검색이 우선이고, keyword는 그 안에서 SQL WHERE 필터로 처리합니다. keyword가 있더라도 좌표가 있으면 반경 검색이 먼저입니다. 또 클라이언트가 빈 문자열을 보내면 SQL의 `:keyword IS NULL` 조건이 의도대로 동작하지 않는 문제가 있었습니다. 이를 `normalize()` 메서드로 진입 시 null로 변환해 해결했습니다."

---

## 3. 인덱스 전략 — 지역별 복합 인덱스 + SPATIAL + FULLTEXT + review_count 캐시 컬럼

### 어필 포인트

#### locationservice 테이블 인덱스 (SHOW INDEX 실측)

**SPATIAL 인덱스** (R-Tree):

| 인덱스명 | 타입 | 컬럼 | 용도 |
|---------|------|------|------|
| `idx_locationservice_location_spatial` | SPATIAL | (location) | ST_Within 바운딩박스 1차 필터 — 공간 인덱스를 타게 해 풀스캔 방지 |

**지역별 복합 인덱스** (USE INDEX 힌트로 명시 지정):

| 인덱스명 | 타입 | 컬럼 | 사용 쿼리 |
|---------|------|------|---------|
| `idx_locationservice_sido_deleted_rating` | BTREE | (sido, is_deleted, rating) | `findBySido` |
| `idx_locationservice_sigungu_deleted_rating` | BTREE | (sigungu, is_deleted, rating) | `findBySigungu` |
| `idx_locationservice_eupmyeondong_deleted_rating` | BTREE | (eupmyeondong, is_deleted, rating) | `findByEupmyeondong` |
| `idx_road_name_deleted_rating` | BTREE | (road_name, is_deleted, rating) | `findByRoadName` |
| `idx_locationservice_deleted_rating` | BTREE | (is_deleted, rating) | 전체 평점순 fallback 조회 |
| `idx_category3_deleted_rating` | BTREE | (category3, is_deleted, rating) | 카테고리별 필터 조회 |
| `idx_name_address` | BTREE | (name, address) | 이름·주소 복합 조회 |

- 각 지역 쿼리에서 `USE INDEX` 힌트로 옵티마이저가 다른 인덱스를 선택하지 않도록 강제한다.
- `rating`을 인덱스에 포함시켜 `ORDER BY rating DESC` 를 인덱스 순회만으로 처리한다.
- 중복 인덱스 제거: `idx_lat_lng`(latitude, longitude)는 공간 인덱스와 중복·미사용으로 삭제, `idx_address_detail`도 `idx_name_address`에 커버되어 삭제했다.

**FULLTEXT 인덱스**:
```sql
-- ft_search: name, description, category1, category2, category3 5개 컬럼
MATCH(name, description, category1, category2, category3)
AGAINST(CONCAT(:keyword, '*') IN BOOLEAN MODE)
```

- 위치·지역 없이 keyword만 있는 fallback 경로에서만 사용한다.
- Boolean Mode + 접두사 와일드카드(`*`)로 부분 일치 검색을 지원한다.

#### locationservicereview 테이블 인덱스 (SHOW INDEX 실측)

| 인덱스명 | 타입 | 컬럼 | 비고 |
|---------|------|------|------|
| `idx_locationservicereview_service_deleted` | BTREE | (service_idx, is_deleted) | Soft Delete 포함 서비스별 조회 |
| `idx_locationservicereview_user_deleted` | BTREE | (user_idx, is_deleted) | Soft Delete 포함 사용자별 조회 |
| `service_idx` | BTREE | (service_idx) | **레거시/중복** — 복합 인덱스 `idx_locationservicereview_service_deleted`에 커버됨 |
| `user_idx` | BTREE | (user_idx) | **레거시/중복** — 복합 인덱스 `idx_locationservicereview_user_deleted`에 커버됨 |

- `service_idx`, `user_idx` 단일 인덱스는 각각 복합 인덱스의 선두 컬럼으로 커버되므로 실질적으로 중복이다. 향후 DROP INDEX로 정리 가능하다.

**review_count 캐시 컬럼**:
```sql
ALTER TABLE locationservice
  ADD COLUMN review_count INT NOT NULL DEFAULT 0;
```

- 반경 검색 `ORDER BY reviews` 정렬 시 상관 서브쿼리로 리뷰 수를 세면 후보 행마다 비용이 반복된다. `review_count` 컬럼을 캐시로 두고 `updateReviewStats()`에서 원자적으로 갱신한다.
- 활성 리뷰만 집계: `is_deleted IS NULL OR is_deleted = 0` 조건 적용.

### 말할 내용

> "공간 인덱스(`idx_locationservice_location_spatial`)는 SPATIAL 타입 R-Tree 인덱스로, `ST_Within` 바운딩박스 1차 필터에서 활용합니다. `ST_Distance_Sphere`만 단독으로 쓰면 공간 인덱스를 타지 않아 풀스캔이 발생합니다. 지역 검색 쿼리에는 (sigungu, is_deleted, rating) 같은 복합 인덱스를 만들고 USE INDEX 힌트를 명시했습니다. rating을 인덱스에 포함시킨 건 ORDER BY rating DESC를 인덱스 순회만으로 처리하기 위해서입니다. 또 반경 검색에서 reviews 정렬이 필요한데, 서브쿼리로 리뷰 수를 매번 세는 건 후보 행마다 비용이 반복돼서 review_count 캐시 컬럼을 추가했습니다. locationservicereview 테이블에는 (service_idx, is_deleted), (user_idx, is_deleted) 복합 인덱스가 있는데, 단일 인덱스 service_idx·user_idx는 복합 인덱스에 커버되는 중복 인덱스입니다."

---

## 4. 평점·리뷰수 원자적 갱신 — Lost Update 방지

### 어필 포인트

- 기존 방식(read → AVG 계산 → write)은 동시 리뷰 작성 시 Lost Update가 발생할 수 있었다.
- 단일 `UPDATE` 쿼리로 `rating`(평균)과 `review_count`(건수)를 동시에 갱신한다. MySQL의 "You can't specify target table" 오류를 피하기 위해 인라인 뷰로 감쌌다.

```sql
-- SpringDataJpaLocationServiceRepository.updateReviewStats()
UPDATE locationservice SET
    rating = (
        SELECT avg_rating FROM (
            SELECT COALESCE(AVG(r.rating), 0.0) AS avg_rating
            FROM locationservicereview r
            WHERE r.service_idx = :serviceIdx
              AND (r.is_deleted IS NULL OR r.is_deleted = 0)
        ) avg_stats
    ),
    review_count = (
        SELECT review_count FROM (
            SELECT COUNT(*) AS review_count
            FROM locationservicereview r
            WHERE r.service_idx = :serviceIdx
              AND (r.is_deleted IS NULL OR r.is_deleted = 0)
        ) review_stats
    )
WHERE idx = :serviceIdx
```

- `@Modifying` + `@Transactional`로 리뷰 저장/삭제와 통계 갱신이 하나의 트랜잭션으로 묶인다.
- soft delete 제외(`is_deleted = 0`) 조건을 통계 쿼리에도 동일하게 적용해 삭제된 리뷰가 평점에 반영되지 않는다.

### 말할 내용

> "리뷰 작성·수정·삭제 후 서비스 평균 평점과 리뷰 수를 갱신할 때, 처음에는 read → AVG 계산 → write 방식을 썼습니다. 동시 요청이 오면 Lost Update가 발생할 수 있다는 걸 파악하고, 단일 UPDATE 쿼리로 rating과 review_count를 같은 트랜잭션에서 한 번에 갱신하도록 변경했습니다. MySQL에서 UPDATE 대상 테이블을 서브쿼리에서 참조할 수 없는 제약이 있어 인라인 뷰로 한 번 감쌌습니다."

---

## 5. Soft Delete 설계 — 조회·통계에서 일관된 제외

### 어필 포인트

- `LocationService`와 `LocationServiceReview` 모두 `is_deleted` + `deleted_at` 필드로 Soft Delete를 구현한다.
- 모든 조회 쿼리에 `(is_deleted IS NULL OR is_deleted = 0)` 조건이 적용된다. JPQL과 네이티브 쿼리 양쪽에 일관되게 적용한다.
- `locationservicereview` 테이블에는 `(service_idx, is_deleted)`, `(user_idx, is_deleted)` 복합 인덱스를 추가해 조회 시 풀스캔을 방지한다.
- 리뷰 중복 방지 체크(`existsByServiceIdxAndUserIdx`)도 삭제된 리뷰는 제외한다 — 삭제 후 재작성이 가능하다.
- 이미 삭제된 항목을 재삭제하면 `LocationServiceAlreadyDeletedException` / `LocationReviewAlreadyDeletedException`을 던진다.

### 말할 내용

> "물리적으로 삭제하지 않고 is_deleted 플래그로 Soft Delete를 구현했습니다. 데이터 복구 가능성과 히스토리 보존이 목적입니다. 주의한 점은 통계 쿼리에도 같은 조건을 적용하는 것입니다. review_count와 rating 갱신 쿼리에서도 is_deleted = 0인 활성 리뷰만 집계해 삭제된 리뷰가 평점에 반영되지 않도록 했습니다."

---

## 6. 초기 로드 성능 개선 — 전체 조회에서 반경 검색으로

### 어필 포인트

실측 성능 데이터 (측정일: 2025-12-21, `docs/troubleshooting/location/initial-load-performance.md`):

| 항목 | 개선 전 | 개선 후 | 변화 |
|-----|--------|--------|------|
| 조회 데이터 수 | 22,699개 | 1,026개 | **95.5% 감소** |
| 백엔드 DB 쿼리 시간 | 841ms | 500ms | **40.4% 개선** |
| 백엔드 전체 처리 시간 | 885ms | 530ms | **40.1% 개선** |
| 프론트엔드 전체 시간 | 1,484ms | 700ms | **52.8% 개선** |
| 네트워크 전송량 | 22 MB | 1 MB | **95.5% 감소** |
| 메모리 사용량 | 78.90 MB | 28.6 MB | **63.8% 감소** |

- 문제: 초기 로드 시 전체 22,000개를 조회하고 프론트엔드에서 필터링했다. 실제 사용자가 보는 데이터는 주변 10km 이내 약 1,000개였다.
- 해결: 사용자 위치가 있으면 백엔드에서 반경 검색(`ST_Distance_Sphere`)으로 필터링. 프론트엔드 거리 계산도 백엔드에서 Haversine 공식으로 처리해 DTO에 포함시켰다.
- 현재 성능 측정 로그: 서비스 레이어 각 검색 경로에서 DB 쿼리 시간, DTO 변환 시간, 전체 처리 시간을 `log.info("[성능 측정]")`로 기록한다.

### 말할 내용

> "초기 로드에서 22,000개를 전부 조회해서 프론트엔드에서 필터링하는 구조였습니다. 실제 보여주는 건 주변 1,000개 정도인데 22MB를 전송했습니다. ST_Distance_Sphere로 백엔드에서 반경 필터링을 하고, 거리 계산도 백엔드 Haversine 공식으로 처리해 DTO에 담아 보내도록 바꿨습니다. 결과적으로 데이터 95.5% 감소, 전체 응답 시간 52.8% 개선, 메모리 63.8% 감소 효과가 있었습니다."

---

## 핵심 키워드

- ST_Within + ST_Distance_Sphere 이중 필터 (공간 인덱스 R-Tree 활용)
- POINT(SRID 4326) — 엔티티 미매핑, 네이티브 쿼리 전용
- 복합 인덱스 (sigungu/sido/eupmyeondong/road_name, is_deleted, rating DESC) + USE INDEX 힌트
- FULLTEXT 인덱스 (ft_search: name·description·category1-3, Boolean Mode)
- review_count 캐시 컬럼 (반경 검색 reviews 정렬 최적화)
- 원자적 UPDATE (인라인 뷰로 Lost Update 방지)
- Soft Delete + 통계 쿼리 일관성
- 빈 문자열 정규화 (normalize → null)
- 검색 분기 우선순위: 위치 → 지역 → 키워드 FULLTEXT → 평점순

---

## 관련 파일

- `SpringDataJpaLocationServiceRepository.java` — 반경/지역/FULLTEXT 쿼리, updateReviewStats
- `LocationServiceService.java` — 검색 분기, normalize, calculateDistance, 성능 측정 로그
- `locationservice-index-optimization.sql` — 중복 인덱스 제거, road_name 복합 인덱스 추가
- `locationservice-add-review-count-column.sql` — review_count 캐시 컬럼 추가·백필
- `locationservicereview-add-soft-delete-columns.sql` — soft delete 컬럼·인덱스 추가
- `docs/troubleshooting/location/initial-load-performance.md` — 성능 개선 전후 실측값

---

## 면접 대답 구성

### 질문: "위치 기반 검색을 어떻게 구현했나요?"

1. **구조 설명** (30초)
   - "DB 스키마에 POINT(SRID 4326) 컬럼을 두고 공간 인덱스를 설정했습니다. JPA 엔티티에는 latitude/longitude만 매핑하고 네이티브 쿼리로만 처리합니다."

2. **쿼리 설계** (1분)
   - "ST_Within으로 위도·경도 범위를 POLYGON으로 변환해 공간 인덱스를 타게 합니다. 그 후보에 ST_Distance_Sphere로 원형 정밀 필터를 적용합니다. ST_Distance_Sphere만 단독으로 쓰면 공간 인덱스를 타지 않아 풀스캔이 됩니다."

3. **성능 결과** (30초)
   - "전체 22,000개 조회에서 반경 검색으로 바꾼 후 데이터 95.5% 감소, 응답 시간 52.8% 개선, 메모리 63.8% 감소를 실측했습니다."

### 질문: "동시성 문제를 어떻게 처리했나요?"

1. "리뷰 평점 갱신에서 read → 계산 → write 패턴은 Lost Update 위험이 있어서 단일 UPDATE 쿼리로 rating과 review_count를 한 번에 갱신합니다."
2. "MySQL에서 UPDATE 대상 테이블을 서브쿼리에서 참조할 수 없어서 인라인 뷰로 감쌌습니다."
3. "리뷰 작성과 통계 갱신은 @Transactional로 하나의 트랜잭션에 묶입니다."
