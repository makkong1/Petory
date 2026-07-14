---
date: 2025-12-21
domains: [location]
type: performance-evidence
problem: overfetching
status: superseded
metric: "22,699→1,026건 (-95.5%), 1484ms→700ms (-52.8%), 22MB→1MB"
related: [docs/refactoring/location/evidence/initial-load-reverify-2026-07-12.md]
---

# Location 도메인 초기 로드 성능 문제

## 📋 개요

Location 도메인에서 위치 서비스 초기 로드 시 발생하는 성능 문제와 개선 과정을 정리한 문서입니다.

---

## 🔴 1. 문제 상황

### 1.1 기존 구조

**프론트엔드 (`LocationServiceMap.js`)**:
```javascript
// 초기 로드 시 전체 데이터 조회
const response = await locationServiceApi.searchPlaces({
  category: apiCategory,
  size: null, // 전체 조회 (제한 없음)
});

// 전체 데이터를 allServices에 저장
setAllServices(allFetchedServices);

// 프론트엔드에서 지역 필터링 및 거리 계산
filterServicesByRegion(allFetchedServices, ...);
```

**백엔드 (`LocationServiceController.java`)**:
```java
@GetMapping("/search")
public ResponseEntity<Map<String, Object>> searchLocationServices(
    @RequestParam(required = false) String sido,
    @RequestParam(required = false) String sigungu,
    @RequestParam(required = false) String eupmyeondong,
    @RequestParam(required = false) String roadName,
    @RequestParam(required = false) String category,
    @RequestParam(required = false) Integer size) {
    
    List<LocationServiceDTO> services = locationServiceService
        .searchLocationServicesByRegion(sido, sigungu, eupmyeondong, roadName, category, size);
    // ...
}
```

**서비스 레이어 (`LocationServiceService.java`)**:
```java
public List<LocationServiceDTO> searchLocationServicesByRegion(...) {
    // 모든 파라미터가 없으면 전체 조회
    if (!StringUtils.hasText(roadName) && !StringUtils.hasText(eupmyeondong) 
        && !StringUtils.hasText(sigungu) && !StringUtils.hasText(sido)) {
        services = locationServiceRepository.findByOrderByRatingDesc();
    }
    // ...
}
```

### 1.2 문제점

1. **전체 데이터 조회**
   - 초기 로드 시 모든 위치 서비스 데이터를 한 번에 조회 (약 22,000개)
   - 불필요한 데이터까지 네트워크로 전송하여 대역폭 낭비

2. **프론트엔드 필터링**
   - 전체 데이터를 프론트엔드로 전송 후 클라이언트 사이드에서 필터링
   - 사용자가 실제로 보는 데이터는 일부인데 전체를 로드

3. **거리 계산 오버헤드**
   - 모든 서비스에 대해 거리 계산 수행 (약 22,000개)
   - 프론트엔드에서 계산하므로 CPU 사용량 증가

4. **확장성 문제**
   - 데이터가 증가할수록 로딩 시간이 선형적으로 증가
   - 모바일 환경에서 더 큰 성능 저하 예상

### 1.3 성능 측정 결과 (수정 전)

**측정 일시**: 2025-12-21

| 측정 항목 | 값 | 비고 |
|----------|-----|------|
| **조회 데이터 수** | 22,699개 | 실제 측정값 |
| **백엔드 DB 쿼리 실행 시간** | 841ms | 전체의 57% (가장 큰 병목) |
| **백엔드 DTO 변환 시간** | 43ms | 전체의 3% |
| **백엔드 전체 처리 시간** | 885ms | 실제 측정값 |
| **네트워크 전송 시간** | 약 591ms | 전체의 40% (두 번째 병목) |
| **네트워크 전송량** | 약 22 MB | 브라우저 네트워크 탭 |
| **프론트엔드 API 호출 시간** | 1,476ms | 백엔드 + 네트워크 |
| **프론트엔드 거리 계산 시간** | 6.3ms | 22,699개 레코드 처리 |
| **프론트엔드 필터링 시간** | 1.0ms | 매우 빠름 |
| **프론트엔드 전체 처리 시간** | 1,484ms | 실제 측정값 |
| **메모리 사용량 (프론트엔드)** | 78.90 MB | 실제 측정값 |
| **실제 표시되는 데이터 수** | 최대 100개 | 주변 10km 이내만 표시 |

**시간 분해 분석**:
- 백엔드: 885ms (60%)
  - DB 쿼리: 841ms (57%) ⚠️ **가장 큰 병목**
  - DTO 변환: 43ms (3%)
- 네트워크 전송: 591ms (40%) ⚠️ **두 번째 병목** (22MB 전송)
- 프론트엔드 처리: 7.3ms (0.5%) ✅ 매우 빠름
  - 거리 계산: 6.3ms
  - 필터링: 1.0ms

**핵심 문제**:
- DB 쿼리와 네트워크 전송이 병목
- 실제 필요한 데이터는 1,000개 정도인데 22,699개를 모두 조회
- 네트워크 대역폭과 메모리 낭비

---

## ✅ 2. 개선 작업

### 2.1 개선 방안

**위치 기반 초기 로드 적용**:
- 사용자 위치가 있으면 주변 10km 반경 내 서비스만 조회
- 백엔드에서 위치 기반 필터링 수행 (MySQL `ST_Distance_Sphere` 사용)
- 프론트엔드에서 거리 계산 불필요 (백엔드에서 처리)

### 2.2 구현 내용

#### 백엔드 수정

**1. Repository에 위치 기반 검색 메서드 추가**:
```java
@Query(value = "SELECT * FROM locationservice WHERE " +
        "latitude IS NOT NULL AND longitude IS NOT NULL AND " +
        "ST_Distance_Sphere(POINT(longitude, latitude), POINT(?2, ?1)) <= ?3 " +
        "ORDER BY rating DESC", nativeQuery = true)
List<LocationService> findByRadius(@Param("latitude") Double latitude,
        @Param("longitude") Double longitude,
        @Param("radiusInMeters") Double radiusInMeters);
```

**2. Service에 위치 기반 검색 로직 추가**:
```java
public List<LocationServiceDTO> searchLocationServicesByRegion(
        String sido, String sigungu, String eupmyeondong, String roadName, String category,
        Integer maxResults, Double latitude, Double longitude, Double radius) {
    
    long methodStartTime = System.currentTimeMillis();
    List<LocationService> services;

    long queryStartTime = System.currentTimeMillis();
    if (latitude != null && longitude != null && radius != null) {
        // 위치 기반 검색 우선
        services = locationServiceRepository.findByRadius(latitude, longitude, radius);
        log.info("위치 기반 검색: lat={}, lng={}, radius={}, 결과={}개", 
                latitude, longitude, radius, services.size());
    } else if (StringUtils.hasText(roadName)) {
        services = locationServiceRepository.findByRoadName(roadName);
    } else {
        // 기존 지역 기반 검색
        services = locationServiceRepository.findByOrderByRatingDesc();
    }
    long queryTime = System.currentTimeMillis() - queryStartTime;
    log.info("DB 쿼리 실행 시간: {}ms, 조회된 레코드 수: {}개", queryTime, services.size());

    // 카테고리 필터링
    // DTO 변환
    // ...
}
```

**3. Controller에 위치 파라미터 추가**:
```java
@GetMapping("/search")
public ResponseEntity<Map<String, Object>> searchLocationServices(
        @RequestParam(required = false) String sido,
        @RequestParam(required = false) String sigungu,
        @RequestParam(required = false) String eupmyeondong,
        @RequestParam(required = false) String roadName,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) Double latitude,  // 추가
        @RequestParam(required = false) Double longitude,  // 추가
        @RequestParam(required = false) Double radius) {   // 추가
        
    List<LocationServiceDTO> services = locationServiceService
        .searchLocationServicesByRegion(sido, sigungu, eupmyeondong, roadName, 
                                      category, size, latitude, longitude, radius);
    // ...
}
```

#### 프론트엔드 수정

**1. 초기 로드 시 위치 기반 검색 적용**:
```javascript
// 초기 로드 시 전략 선택
if (isInitialLoad) {
  const targetLocation = userLocationOverride || userLocation;

  if (targetLocation) {
    // 사용자 위치가 있으면 위치 기반 검색 (10km 반경)
    console.log('📍 [위치 기반 검색] 사용자 위치 기반으로 10km 반경 검색');
    initialLoadTypeRef.current = 'location-based';
    response = await locationServiceApi.searchPlaces({
      latitude: targetLocation.lat,
      longitude: targetLocation.lng,
      radius: 10000, // 10km
      category: apiCategory,
    });
  } else {
    // 사용자 위치가 없으면 전체 조회
    console.log('🌐 [전체 검색] 사용자 위치 없음 - 전체 조회');
    initialLoadTypeRef.current = 'all';
    response = await locationServiceApi.searchPlaces({
      category: apiCategory,
      size: null, // 전체 조회
    });
  }
}
```

**2. API 클라이언트에 위치 파라미터 추가**:
```javascript
// locationServiceApi.js
searchPlaces: ({
  sido,
  sigungu,
  eupmyeondong,
  roadName,
  category,
  size,
  latitude,  // 추가
  longitude,  // 추가
  radius      // 추가
} = {}) =>
  api.get('/search', {
    params: {
      // ... 기존 파라미터 ...
      ...(latitude && { latitude }),
      ...(longitude && { longitude }),
      ...(radius && { radius }),
    },
  }),
```

### 2.3 성능 측정 결과 (수정 후)

**측정 일시**: 2025-12-21 (3회 측정 평균)

**측정 조건**: 사용자 위치 기반 10km 반경 검색

| 측정 항목 | 측정 1 | 측정 2 | 측정 3 | 평균 | 개선율 |
|----------|--------|--------|--------|------|--------|
| **조회 데이터 수** | 1,026개 | 1,026개 | 1,027개 | 1,026개 | **95.5% 감소** (22,699 → 1,026) |
| **백엔드 DB 쿼리 실행 시간** | 499ms | 419-440ms | 572-574ms | 약 500ms | **40.4% 개선** (841ms → 500ms) |
| **백엔드 DTO 변환 시간** | 17ms | 35-46ms | 8-10ms | 약 20ms | **53.5% 개선** (43ms → 20ms) |
| **백엔드 전체 처리 시간** | 518-519ms | 474-485ms | 584-587ms | 약 530ms | **40.1% 개선** (885ms → 530ms) |
| **프론트엔드 API 호출 시간** | 677ms | 689-697ms | 724-735ms | 약 700ms | **52.6% 개선** (1,476ms → 700ms) |
| **프론트엔드 필터링 시간** | 0.4ms | 0.2ms | 0.3ms | 약 0.3ms | **70% 개선** (1.0ms → 0.3ms) |
| **프론트엔드 전체 처리 시간** | 683ms | 691ms | 727ms | 약 700ms | **52.8% 개선** (1,484ms → 700ms) |
| **메모리 사용량 (프론트엔드)** | 25.19 MB | 31.91 MB | 28.76 MB | 약 28.6 MB | **63.8% 감소** (78.90 MB → 28.6 MB) |
| **네트워크 전송량 (추정)** | 약 1 MB | 약 1 MB | 약 1 MB | 약 1 MB | **95.5% 감소** (22 MB → 1 MB) |

**시간 분해 분석**:
- 백엔드: 530ms (76%)
  - DB 쿼리: 500ms (71%) ⚠️ 여전히 가장 큰 병목이지만 데이터 양 감소로 개선
  - DTO 변환: 20ms (3%)
- 네트워크 전송: 170ms (24%) ✅ **대폭 개선** (1MB 전송, 22MB → 1MB)
- 프론트엔드 처리: 0.3ms (0.04%) ✅ 매우 빠름
  - 필터링: 0.3ms (거리 계산 불필요, 백엔드에서 처리)

### 2.4 개선 효과 요약

| 항목 | 수정 전 | 수정 후 | 개선율 |
|------|---------|---------|--------|
| **조회 데이터 수** | 22,699개 | 1,026개 | **95.5% 감소** |
| **백엔드 처리 시간** | 885ms | 530ms | **40.1% 개선** |
| **프론트엔드 전체 처리 시간** | 1,484ms | 700ms | **52.8% 개선** |
| **네트워크 전송량** | 22 MB | 1 MB | **95.5% 감소** |
| **메모리 사용량** | 78.90 MB | 28.6 MB | **63.8% 감소** |

**주요 성과**:
- ✅ 초기 로딩 시간 **2.1배 빠름** (1.5초 → 0.7초)
- ✅ 네트워크 대역폭 **95.5% 절약**
- ✅ 메모리 사용량 **63.8% 감소**
- ✅ 사용자 경험 **대폭 개선**

---

## 📝 참고사항

- 현재 전체 데이터 수: 약 22,000개
- **위치 기반 검색 적용 후**: 사용자 위치가 있으면 주변 10km 이내 약 1,000개만 조회
- **초기 로드 전략**: 
  - 사용자 위치 있음 → 위치 기반 검색 (10km 반경, 약 1,000개)
  - 사용자 위치 없음 → 전체 조회 (약 22,000개)
- 향후 데이터 증가 시에도 위치 기반 검색으로 성능 유지 가능

---
