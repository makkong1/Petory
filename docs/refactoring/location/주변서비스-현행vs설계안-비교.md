# 주변 서비스 — 현행 알고리즘 vs 구현 결과 비교

**작성일**: 2026-04-12  
**연관 문서**: `주변서비스-알고리즘-설계안.md`  
**목적**: 리팩토링 전 코드와 실제 구현된 결과를 나란히 비교.

> ✅ = 구현 완료 / ⚠️ = 설계안과 일부 차이 있음 (사유 명시)

---

## 1. 검색 분기 흐름 ✅

### 현행

```
요청 파라미터 수신
        │
        ▼
keyword 있음? ──YES──▶ FULLTEXT 전국 검색
        │                (위도·경도·반경 무시됨)
        NO
        │
        ▼
lat + lng + radius 있음? ──YES──▶ 반경 검색
        │
        NO
        │
        ▼
지역 계층 검색 (sido > sigungu > eupmyeondong > roadName)
        │
        NO (전부 없음)
        │
        ▼
전체 평점순
```

**문제**: keyword가 있으면 좌표·반경이 함께 넘어와도 완전히 무시된다.

---

### 구현 결과 (B 방향)

```
요청 파라미터 수신
        │
        ▼
[정규화] keyword, category, sido, sigungu, eupmyeondong, roadName
  "" → null, "  " → null, "값" → "값".trim()
        │
        ▼
위치(lat+lng) 있음? ──YES──▶ 반경 쿼리
        │                     (keyword·category SQL WHERE 필터)
        NO
        │
        ▼
지역(sido/sigungu/...) 있음? ──YES──▶ 지역 쿼리
        │                              (keyword·category SQL WHERE 필터)
        NO
        │
        ▼
keyword 있음? ──YES──▶ FULLTEXT 전국 검색 (위치 없을 때 fallback)
        │
        NO
        │
        ▼
전체 평점순 (keyword·category SQL WHERE 필터)
```

**핵심 변화**: 위치가 항상 1순위. keyword는 위치 내부에서 필터 역할.

---

## 2. 빈 문자열 정규화 ✅ (신규)

### 현행 — 정규화 없음

```
클라이언트: keyword="" 전송
        │
        ▼
Controller: @RequestParam → "" 그대로 전달
        │
        ▼
Service: StringUtils.hasText("") = false → 분기 판단은 맞음
         BUT keyword="" 를 그대로 repository에 전달
        │
        ▼
SQL: :keyword IS NULL → false (""는 null이 아님)
     name LIKE CONCAT('%', '', '%') = name LIKE '%%' → 전체 name 매칭
```

**문제**: `keyword=""` 요청이 "키워드 없음"이 아닌 "이름 전체 매칭"으로 동작.

---

### 구현 결과

```java
// LocationServiceService — 각 public 메서드 진입 시 정규화
private static String normalize(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
}

// searchLocationServices() 진입 시
keyword      = normalize(keyword);   // "" → null, "  " → null, "동물병원" → "동물병원"
category     = normalize(category);
sido         = normalize(sido);
sigungu      = normalize(sigungu);
eupmyeondong = normalize(eupmyeondong);
roadName     = normalize(roadName);
```

`searchLocationServicesByRegion`, `searchLocationServicesByLocation`,
`searchLocationServicesByKeyword` 3개 메서드 진입 시에도 동일 정규화 적용.

**결과**: null이 SQL에 전달되어 `:keyword IS NULL` → true → name 필터 없이 조회.

---

## 3. 반경 검색 쿼리 ⚠️

### 현행

```sql
SELECT * FROM locationservice
WHERE
  ST_Within(location, ST_GeomFromText(CONCAT('POLYGON((', ...4개 꼭짓점... '))'), 4326))
  AND ST_Distance_Sphere(location, ST_GeomFromText(
      CONCAT('POINT(', :latitude, ' ', :longitude, ')'), 4326)) <= :radiusInMeters
  AND is_deleted = 0
ORDER BY rating DESC
-- 카테고리·keyword 없음 → Java 후처리
```

---

### 구현 결과

```sql
SELECT * FROM locationservice
WHERE
  ST_Within(location, ST_GeomFromText(CONCAT('POLYGON((', ...4개 꼭짓점... '))'), 4326))
  AND ST_Distance_Sphere(location, ST_GeomFromText(
      CONCAT('POINT(', :latitude, ' ', :longitude, ')'), 4326)) <= :radiusInMeters
  AND is_deleted = 0
  AND (:keyword IS NULL OR name LIKE CONCAT('%', :keyword, '%'))
  AND (:category IS NULL
       OR category3 = :category
       OR category2 = :category
       OR category1 = :category)
ORDER BY rating DESC
```

> ⚠️ **설계안과 차이**: 설계안은 POLYGON bbox 제거(ST_Distance_Sphere 단일)를 제안했으나,
> MySQL 공간 인덱스가 `ST_Within`에만 작동하고 `ST_Distance_Sphere` 단독으로는 Full Scan이 발생하므로
> 성능 유지를 위해 POLYGON bbox 구조를 유지하고 keyword·category 필터만 추가했다.

---

## 4. 지역 검색 쿼리 ✅ (eupmyeondong 예시)

### 현행

```sql
SELECT * FROM locationservice
USE INDEX (idx_locationservice_eupmyeondong_deleted_rating)
WHERE eupmyeondong = :eupmyeondong
  AND is_deleted = 0
ORDER BY rating DESC

-- → Java: applyCategoryFilter() → stream().filter(matchesCategory)
```

---

### 구현 결과

```sql
SELECT * FROM locationservice
USE INDEX (idx_locationservice_eupmyeondong_deleted_rating)
WHERE eupmyeondong = :eupmyeondong
  AND is_deleted = 0
  AND (:keyword IS NULL OR name LIKE CONCAT('%', :keyword, '%'))
  AND (:category IS NULL
       OR category3 = :category
       OR category2 = :category
       OR category1 = :category)
ORDER BY rating DESC
```

findBySido, findBySigungu, findByRoadName, findByOrderByRatingDesc 동일 패턴 적용.
findByRoadName은 JPQL → 네이티브 쿼리로 전환 (DB 컬럼명 `road_name` 직접 참조).

---

## 5. 카테고리 필터 처리 방식 ✅

### 현행 — Java 메모리 필터링

```
DB 조회 (카테고리 조건 없음)
        │
전체 결과 힙 로드 (서울 전체면 수천 건 가능)
        │
applyCategoryFilter()
  └─ stream().filter(matchesCategory).collect()
       └─ category3, category2, category1 순서로 toLowerCase 비교
        │
maxResults limit → DTO 변환
```

---

### 구현 결과 — SQL WHERE 처리

```
DB 조회 (카테고리 조건 포함)
        │
필터된 결과만 힙 로드
        │
maxResults limit → DTO 변환
```

**삭제된 Java 메서드**:
- `applyCategoryFilter()` — `LocationServiceService`
- `matchesCategory()` — `LocationServiceService`
- `categoryFieldMatches()` — `LocationServiceService`

---

## 6. keyword 전용 검색 (위치 없을 때 fallback) ✅

### 현행 — 모든 keyword 요청에 적용

```java
if (StringUtils.hasText(keyword)) {
    return searchLocationServicesByKeyword(keyword, category, maxResults);
}
```

---

### 구현 결과 — 위치 없을 때만 fallback

```java
if (hasKeyword) {
    return searchLocationServicesByKeyword(keyword, category, maxResults);
}
// (이 분기에 도달하는 시점에 이미 hasLocation, hasRegion이 false)
```

FULLTEXT 쿼리(`findByNameContaining`)에 category WHERE 조건 추가.

> **동작 차이 메모**: 반경·지역 경로의 keyword는 `name LIKE '%keyword%'`(이름만).
> FULLTEXT fallback은 이름·설명·카테고리 전체 필드 검색.
> 동일 키워드라도 경로에 따라 결과가 달라질 수 있음 — 설계상 의도된 트레이드오프.

---

## 7. 인기 서비스 조회 (findTop10) ✅

### 현행

```sql
-- 메서드명만 Top10, 실제 LIMIT 없음
SELECT ls FROM LocationService ls
WHERE (:category IS NULL OR ls.category3 = :category OR ...)
  AND (ls.isDeleted IS NULL OR ls.isDeleted = false)
ORDER BY ls.rating DESC
-- LIMIT 없음 → 캐시 미스 시 해당 카테고리 전체 조회
```

---

### 구현 결과

```sql
SELECT * FROM locationservice
WHERE (:category IS NULL
       OR category3 = :category
       OR category2 = :category
       OR category1 = :category)
  AND is_deleted = 0
ORDER BY rating DESC
LIMIT 10
```

---

## 8. 배치 임포트 트랜잭션 ✅

### 현행

```java
// private + self-invocation → @Transactional(REQUIRES_NEW) AOP 미작동
@Transactional(propagation = Propagation.REQUIRES_NEW)
private int saveBatch(List<LocationService> batch) { ... }
```

---

### 구현 결과

```java
// LocationServiceBatchWriter.java — 별도 빈, public 메서드
@Service
public class LocationServiceBatchWriter {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int saveBatch(List<LocationService> batch) { ... }  // AOP 프록시 정상 작동
}

// PublicDataLocationService
private final LocationServiceBatchWriter batchWriter;  // 주입
batchWriter.saveBatch(batch);  // 외부 빈 호출 → REQUIRES_NEW 실제 적용
```

배치 사이즈 고정값(`1000`) → `@Value("${app.location.import.batch-size:1000}")` 설정값으로 외부화.

---

## 9. AdminLocationController Java 키워드 필터 제거 ✅ (연쇄 수정)

### 현행

```java
// 서비스 호출 후 Java에서 키워드 필터링
List<LocationServiceDTO> services = locationServiceService
    .searchLocationServicesByRegion(sido, sigungu, eupmyeondong, roadName, category, size);

if (q != null && !q.isBlank()) {
    services = services.stream()
        .filter(s -> s.getName().toLowerCase().contains(q.toLowerCase()) || ...)
        .collect(Collectors.toList());
}
```

---

### 구현 결과

```java
// q를 keyword로 서비스에 전달 — SQL WHERE에서 처리
List<LocationServiceDTO> services = locationServiceService
    .searchLocationServicesByRegion(sido, sigungu, eupmyeondong, roadName, q, category, size);
```

---

## 변경 범위 요약

| 항목 | 현행 동작 | 구현 결과 | 상태 |
|---|---|---|---|
| 검색 분기 | keyword 있으면 위치 무시 | 위치 우선, keyword는 필터 | ✅ |
| 빈 문자열 정규화 | 없음 → SQL `name LIKE '%%'` | `normalize()` — "" → null | ✅ |
| 반경 쿼리 필터 | Java 카테고리 후처리 | SQL keyword·category 통합 (POLYGON 구조 유지) | ✅ ⚠️ |
| 지역 쿼리 4개 | 카테고리 없음, Java 후처리 | SQL keyword·category 통합 | ✅ |
| findByRoadName | JPQL | 네이티브 쿼리 전환 | ✅ |
| 카테고리 Java 필터 | 3개 메서드 존재 | 전부 삭제 | ✅ |
| keyword fallback | 항상 FULLTEXT 최우선 | 위치 없을 때만 | ✅ |
| findTop10 LIMIT | 없음 (전체 조회) | LIMIT 10 | ✅ |
| 배치 트랜잭션 | private self-invocation (미작동) | 별도 빈 public 메서드 | ✅ |
| 배치 사이즈 | 하드코딩 1000 | 설정값 외부화 | ✅ |
| AdminController 필터 | Java stream filter | SQL 위임 | ✅ |

---

## 영향받는 파일 목록

```
backend/main/java/com/linkup/Petory/domain/location/
  service/
    LocationServiceService.java                   ← 분기 로직, 정규화, Java 필터 삭제
    PublicDataLocationService.java                ← saveBatch 분리, batchSize 외부화
    LocationServiceBatchWriter.java               ← (신규) saveBatch 이관
  repository/
    SpringDataJpaLocationServiceRepository.java   ← 쿼리 전체 수정 (keyword·category 추가, LIMIT)
    LocationServiceRepository.java                ← 인터페이스 시그니처 동기화
    JpaLocationServiceAdapter.java                ← 어댑터 위임 동기화

backend/main/java/com/linkup/Petory/domain/admin/
  controller/
    AdminLocationController.java                  ← Java 키워드 필터 제거, q → keyword 전달
```
