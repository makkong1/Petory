# Location 도메인 잠재 이슈 및 리팩토링 메모

**작성일**: 2026-04-11  
**목적**: 주변 서비스(`LocationService`) 검색·임포트 경로에서 확인된 **성능·정확도·트랜잭션** 이슈를 코드 근거와 함께 기록하고, 개선 시 우선순위를 잡기 위함.

---

## 요약

| 순위 | 이슈 | 핵심 | 개선 방향 (요약) | 상태 |
|------|------|------|------------------|------|
| 1 | 카테고리 필터가 애플리케이션 메모리에서 처리 | 지역/반경으로 DB에서 많이 읽은 뒤 Java에서 `category1~3` 필터 | SQL `WHERE`에 카테고리 조건 통합 또는 전용 쿼리 | ✅ 해결 |
| 2 | 반경 검색의 바운딩 POLYGON 근사 | `ST_Within` 직사각형 + `COS(RADIANS(lat))` 보정 후 `ST_Distance_Sphere`로 최종 필터 | 근사 박스 누락 방지 검토, 또는 원형 조건만으로 단순화·인덱스 전략 정리 | ⚠️ 모니터링 대기 |
| 3 | `findTop10ByCategoryOrderByRatingDesc`에 상위 N 제한 없음 | 메서드명은 Top10, JPQL에는 `LIMIT`/setMaxResults 없음 → 카테고리별 전량 조회 가능 | `LIMIT 10` 또는 `setMaxResults(10)`, 메서드명·캐시 키 정합성 | ✅ 해결 |
| 4 | `PublicDataLocationService.saveBatch`의 트랜잭션 | `private` + `@Transactional(REQUIRES_NEW)` + 동일 클래스 self-invocation → AOP 미적용 가능성 | `public` + 별도 빈 호출 또는 `TransactionTemplate` | ✅ 해결 |

---

## 1. 카테고리 필터를 Java에서 처리

### 현상

- `LocationServiceService.searchLocationServicesByRegion` / `searchLocationServicesByLocation`(및 키워드 경로)에서 DB 조회 후, `applyCategoryFilter` → `matchesCategory`로 **리스트 전체를 스트림 필터**한다.
- 지역 검색 시 `findBySigungu` 등으로 **해당 지역 행이 많으면** 그만큼 힙으로 올린 뒤 카테고리로 줄인다.

### 코드 위치

- `LocationServiceService`: `applyCategoryFilter`, `matchesCategory`, `categoryFieldMatches`
- 호출: `searchLocationServicesByRegion`, `searchLocationServicesByLocation`, `searchLocationServicesByKeyword` 내부

### 리스크

- 데이터 규모가 커질수록 **불필요한 I/O·메모리** 사용.
- 지역 단일 조건 + 카테고리 조합이 흔한 사용 패턴이면 체감이 큼.

### 리팩토링 방향

- 지역별·반경별 **네이티브/JPQL에 `category1/2/3` 조건**을 넣어 DB에서 걸러낸 뒤 정렬·LIMIT.
- 키워드(FULLTEXT)와의 조합 정책은 제품 규칙에 맞춰 별도 설계.

### 해결 내역

- `LocationServiceService`에서 `applyCategoryFilter` / `matchesCategory` / `categoryFieldMatches` 메서드를 제거.
- 모든 조회 쿼리(`findBySigungu`, `findBySido`, `findByEupmyeondong`, `findByRoadName`, `findByOrderByRatingDesc`, `findByRadius`, `findByNameContaining`)에 다음 조건 추가:
  ```sql
  AND (:category IS NULL
       OR category3 = :category
       OR category2 = :category
       OR category1 = :category)
  ```
- 서비스 메서드는 `category` 파라미터를 정규화(`normalize`)한 뒤 그대로 쿼리에 전달. Java 스트림 필터 없음.

### 관련 문서

- `docs/refactoring/location/검색-분기-및-카테고리-필터-통합.md`

---

## 2. 반경 검색: `ST_Within` POLYGON + 거리 재필터

### 현상

- `SpringDataJpaLocationServiceRepository.findByRadius`에서  
  `ST_Within(location, POLYGON(...))` 으로 **위·경도에 보정을 넣은 직사각형** 범위를 쓰고,  
  이어서 `ST_Distance_Sphere(location, POINT(...)) <= :radiusInMeters` 로 **구면 거리**로 한 번 더 걸러낸다.

### 리스크

- 바운딩 박스는 **원을 완전히 덮는다**는 보장을 위도/경도·미터 환산 근사만으로는 모든 위도에서 엄밀히 말하기 어렵다. 박스가 **실제 원보다 좁으면** 일부 행이 누락될 **이론적 위험**이 있다 (반대로 박스가 넓으면 후단 `ST_Distance_Sphere`가 잘라 줌).

### 현재 상태

- `findByRadius` 쿼리 구조는 문서 작성 당시와 동일하게 유지 중.
- 후단 `ST_Distance_Sphere` 필터가 있어 **결과 정확도에는 실제 문제 없음**.
- 이론적으로 POLYGON 박스가 원보다 좁게 계산될 경우 일부 행이 1차 필터에서 누락될 수 있으나, 운영 데이터 기준 재현된 사례 없음.
- **EXPLAIN 및 운영 데이터 모니터링 후 필요 시 개선** 방향으로 유지.

### 리팩토링 방향

- 운영 데이터·EXPLAIN 기준으로 **누락/성능** 재검증.
- 필요 시 **원형 조건 단일화**(인덱스·쿼리 플랜과 트레이드오프) 또는 MySQL 공간 함수·SRID 정책 문서화.

### 관련 자료

- `docs/migration/db/index/location/locationservice_spatial_index.sql` 등 공간 인덱스 스크립트
- `docs/refactoring/location/거리-계산-중복-제거.md`

---

## 3. `findTop10ByCategoryOrderByRatingDesc` — 이름과 실제 쿼리 불일치

### 현상

- JPQL에 **`ORDER BY rating DESC`만 있고 상위 10건 제한이 없음.**
- Spring Data **파생 쿼리 이름**의 `Top10`처럼 자동 `LIMIT`이 붙는 것이 아니라, **`@Query`로 이름만 유사**한 상태.
- `LocationServiceService.getPopularLocationServices`는 `@Cacheable`로 완화되나, **최초 로드·캐시 미스** 시 해당 카테고리 조건의 **전체 행**을 읽을 수 있음.

### 코드 위치

- `SpringDataJpaLocationServiceRepository.findTop10ByCategoryOrderByRatingDesc`
- `LocationServiceService.getPopularLocationServices`

### 해결 내역

- `@Query`를 네이티브 쿼리로 전환하고 `ORDER BY rating DESC LIMIT 10` 추가.
- 메서드명 `findTop10ByCategoryOrderByRatingDesc`이 실제 동작(DB에서 10건 제한)과 일치.

```sql
SELECT * FROM locationservice WHERE
(:category IS NULL OR category3 = :category OR category2 = :category OR category1 = :category)
AND is_deleted = 0
ORDER BY rating DESC
LIMIT 10
```

---

## 4. `PublicDataLocationService.saveBatch` — `@Transactional(REQUIRES_NEW)`와 프록시

### 현상

- `saveBatch`가 **`private`** 이고, 같은 클래스의 `importFromCsv` 등에서 **직접 호출**(self-invocation).
- Spring `@Transactional`은 **프록시의 public 메서드**에 대해 동작하는 것이 일반적이며, **private 메서드에는 적용되지 않는다**고 문서화되어 있다.
- 따라서 **`REQUIRES_NEW`(배치마다 독립 커밋)** 의도가 실제로는 **`saveAll`/`save`에 의한 기본 트랜잭션 경계**에 더 가깝게 동작할 수 있다.

### 코드 위치

- `PublicDataLocationService.saveBatch`

### 해결 내역

- `saveBatch` 로직을 `LocationServiceBatchWriter` 별도 스프링 빈으로 분리.
- `PublicDataLocationService`에서 `batchWriter.saveBatch(batch)` 형태로 외부 빈 호출 → Spring AOP 프록시 정상 적용.
- `@Transactional(propagation = Propagation.REQUIRES_NEW)` 가 배치 단위로 실제 독립 트랜잭션으로 동작.

```
Before: PublicDataLocationService.importFromCsv() → this.saveBatch()  (self-invocation, AOP 미적용)
After:  PublicDataLocationService.importFromCsv() → batchWriter.saveBatch()  (별도 빈, REQUIRES_NEW 적용)
```

### 관련 문서

- `docs/architecture/위치서비스_공공데이터_CSV_배치_임포트_구현.md`

---

## 우선순위 제안

1. **Top10 쿼리에 실제 LIMIT** — 변경 범위가 작고, 인기 조회·메모리 이슈를 직접 줄임.
2. **카테고리 조건을 DB로** — 검색 패턴·데이터량에 따라 단계적 적용.
3. **saveBatch 트랜잭션 경계 정리** — 임포트 안정성·롤백 범위를 명확히 할 때.
4. **반경 쿼리 기하 정밀도** — 모니터링·EXPLAIN 이후 필요 시.

---

## 관련 문서

| 문서 | 내용 |
|------|------|
| `docs/refactoring/location/검색-분기-및-카테고리-필터-통합.md` | 검색 분기·카테고리 필터 메서드 통합 |
| `docs/troubleshooting/location/search-strategy-comparison.md` | 검색 전략 비교 |
| `docs/troubleshooting/location/initial-load-performance.md` | 초기 로드 성능 |
| `docs/architecture/위치 기반 서비스 아키텍처.md` | 위치 도메인 아키텍처 (루트 `docs/architecture/`) |
