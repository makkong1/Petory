# Location 도메인 잠재 이슈 및 리팩토링 메모

**작성일**: 2026-04-11  
**목적**: 주변 서비스(`LocationService`) 검색·임포트 경로에서 확인된 **성능·정확도·트랜잭션** 이슈를 코드 근거와 함께 기록하고, 개선 시 우선순위를 잡기 위함.

---

## 요약

| 순위 | 이슈 | 핵심 | 개선 방향 (요약) |
|------|------|------|------------------|
| 1 | 카테고리 필터가 애플리케이션 메모리에서 처리 | 지역/반경으로 DB에서 많이 읽은 뒤 Java에서 `category1~3` 필터 | SQL `WHERE`에 카테고리 조건 통합 또는 전용 쿼리 |
| 2 | 반경 검색의 바운딩 POLYGON 근사 | `ST_Within` 직사각형 + `COS(RADIANS(lat))` 보정 후 `ST_Distance_Sphere`로 최종 필터 | 근사 박스 누락 방지 검토, 또는 원형 조건만으로 단순화·인덱스 전략 정리 |
| 3 | `findTop10ByCategoryOrderByRatingDesc`에 상위 N 제한 없음 | 메서드명은 Top10, JPQL에는 `LIMIT`/setMaxResults 없음 → 카테고리별 전량 조회 가능 | `LIMIT 10` 또는 `setMaxResults(10)`, 메서드명·캐시 키 정합성 |
| 4 | `PublicDataLocationService.saveBatch`의 트랜잭션 | `private` + `@Transactional(REQUIRES_NEW)` + 동일 클래스 self-invocation → AOP 미적용 가능성 | `public` + 별도 빈 호출 또는 `TransactionTemplate` |

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

### 관련 문서

- `docs/refactoring/location/검색-분기-및-카테고리-필터-통합.md` (분기·필터 공통화는 이미 반영; **필터 위치를 DB로 옮기는 작업**은 별도)

---

## 2. 반경 검색: `ST_Within` POLYGON + 거리 재필터

### 현상

- `SpringDataJpaLocationServiceRepository.findByRadius`에서  
  `ST_Within(location, POLYGON(...))` 으로 **위·경도에 보정을 넣은 직사각형** 범위를 쓰고,  
  이어서 `ST_Distance_Sphere(location, POINT(...)) <= :radiusInMeters` 로 **구면 거리**로 한 번 더 걸러낸다.

### 리스크

- 바운딩 박스는 **원을 완전히 덮는다**는 보장을 위도/경도·미터 환산 근사만으로는 모든 위도에서 엄밀히 말하기 어렵다. 박스가 **실제 원보다 좁으면** 일부 행이 누락될 **이론적 위험**이 있다 (반대로 박스가 넓으면 후단 `ST_Distance_Sphere`가 잘라 줌).

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

### 리팩토링 방향

- 네이티브 `LIMIT 10` 또는 `TypedQuery#setMaxResults(10)`.
- 메서드명을 실제 동작과 맞추거나, 주석으로 “인기 10건”이 **DB에서 잘린다**는 것을 명시.

---

## 4. `PublicDataLocationService.saveBatch` — `@Transactional(REQUIRES_NEW)`와 프록시

### 현상

- `saveBatch`가 **`private`** 이고, 같은 클래스의 `importFromCsv` 등에서 **직접 호출**(self-invocation).
- Spring `@Transactional`은 **프록시의 public 메서드**에 대해 동작하는 것이 일반적이며, **private 메서드에는 적용되지 않는다**고 문서화되어 있다.
- 따라서 **`REQUIRES_NEW`(배치마다 독립 커밋)** 의도가 실제로는 **`saveAll`/`save`에 의한 기본 트랜잭션 경계**에 더 가깝게 동작할 수 있다.

### 코드 위치

- `PublicDataLocationService.saveBatch`

### 리팩토링 방향

- 배치 커밋을 분리하려면: **`public` 메서드를 별도 스프링 빈**(예: `LocationServiceBatchWriter`)으로 옮겨 외부에서 호출하거나, **`TransactionTemplate`** 으로 명시적 `REQUIRES_NEW` 실행.
- 의도(배치 단위 부분 실패 허용)와 문서 주석을 코드에 맞게 정리.

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
