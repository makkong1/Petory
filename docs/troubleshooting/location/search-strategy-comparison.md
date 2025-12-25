# 검색 전략 개선: 위치 기반 → 시도/시군구 기반 전환

## 📋 문제 상황

### 1. 검색 기준점 불일치 문제

**현재 구현**:
- 초기 로드: 내 위치 기준 5km 반경 검색
- 지도 이동 후 검색: 새로운 위치 기준 5km 반경 검색
- 결과: 이전에 본 서비스가 사라져 일관성 부족

**시나리오 예시**:
```
1. 초기 로드: 내 위치(서울 강남구) 기준 5km 반경 검색
   → 강남구, 서초구 일부 서비스 표시

2. 사용자가 지도를 서초구로 이동

3. "이 지역 검색" 버튼 클릭
   → 서초구 중심 기준 5km 반경 검색
   → 강남구 서비스는 사라지고 서초구 서비스만 표시

4. 사용자 혼란: "아까 본 강남구 서비스가 어디 갔지?"
```

### 2. 성능 이슈

**위치 기반 검색 성능** (실제 측정값, 2025-12-25):
- 쿼리 실행 시간: **191ms** (5km 반경 검색)
- 조회된 레코드 수: **668개** (반경 내 데이터)
- DTO 변환 시간: 0ms
- 전체 처리 시간: **193ms**
- 쿼리: `ST_Distance_Sphere(POINT(longitude, latitude), POINT(?, ?)) <= ?`
- **특징**: 반경 내 데이터만 조회하여 결과 수가 적음 (668개)

**시도/시군구 기반 검색 성능** (실제 측정값, 2025-12-25):
- 쿼리 실행 시간: **986-990ms** (시도만 검색 시)
- 조회된 레코드 수: **22,699개** (시도 전체)
- DTO 변환 시간: 9ms
- 전체 처리 시간: **1,016-1,038ms**
- **문제점**: 시도 단위 검색 시에도 시간이 오래 걸림 (인덱스 미활용 또는 대량 데이터)

### 3. 구현 복잡도

**현재 구현**:
- 검색 기준점 추적 로직 복잡
- 영역 확장 로직 필요
- 상태 관리 복잡

---

## 🎯 해결 방향

### 핵심 원칙

> **지도와 검색 기준은 시군구까지**  
> **읍면동은 정밀 필터로만 사용**  
> **읍면동 좌표/경계는 절대 들고 가지 말 것**

### 1. 검색 기준: 시도/시군구까지

**구현 방식**:
- 초기 로드: 내 위치 → 역지오코딩 → 시도/시군구 추출 → 시도/시군구 검색
- 지도 이동 후 검색: 지도 중심 좌표 → 역지오코딩 → 시도/시군구 추출 → 시도/시군구 검색
- WHERE 조건으로 조회 (인덱스 활용, 성능 향상)

**장점**:
- ✅ 검색 결과 일관성 (시도/시군구로 확정)
- ✅ 성능 우수 (인덱스 활용, 예상 10-50ms)
- ✅ 구현 단순 (WHERE 조건만)
- ✅ 사용자 이해 쉬움 ("서울 강남구" 검색)

### 2. 읍면동: 정밀 필터로만 사용

**구현 방식**:
- DB 쿼리에서 제외
- 프론트엔드에서 클라이언트 사이드 필터링만
- 읍면동 좌표/경계는 사용하지 않음

**이유**:
- 읍면동 경계가 복잡하고 불명확
- DB 쿼리로 사용하면 비효율적
- 프론트엔드 필터링으로 충분

### 3. 거리 계산: 프론트엔드에서 처리 (보조 기능)

**구현 방식**:
- 시도/시군구 검색 후 프론트엔드에서 거리 계산 (표시용)
- 거리순 정렬도 프론트엔드에서 처리

**위치 기반 검색의 역할**:
- ❌ **DB 쿼리에서 위치 기반 검색(ST_Distance_Sphere) 사용하지 않음**
- ✅ **프론트엔드에서 거리 계산만 사용 (표시용, 보조 기능)**
- ✅ **시도/시군구 기반 검색이 주 검색 방식**

**이유**:
- DB 부담 감소 (ST_Distance_Sphere 쿼리 제거)
- 사용자 위치 기준 거리 정보 제공 가능 (프론트엔드 계산)
- 성능 향상 (인덱스 활용 WHERE 조건만 사용)

---

## 📊 전략 비교

### 위치 기반 검색 (현재) vs 시도/시군구 기반 검색 (개선)

| 항목 | 위치 기반 검색 | 시도 단위 검색 | 시군구 단위 검색 |
|------|--------------|-------------|----------------|
| 쿼리 | ST_Distance_Sphere (복잡) | WHERE sido = ? | WHERE sido = ? AND sigungu = ? |
| 성능 | **191ms** (5km 반경) | **986-990ms** ⚠️ | **51ms** ✅ |
| 일관성 | 검색 기준점이 매번 바뀜 | 시도로 확정 | 시군구로 확정 |
| 구현 복잡도 | 높음 (영역 확장 로직) | 낮음 (역지오코딩 후 조회) | 낮음 (역지오코딩 후 조회) |
| 사용자 이해 | "5km 반경" (모호함) | "서울특별시" (명확함) | "서울 강남구" (명확함) |
| 조회 레코드 수 | 반경 내 데이터만 (**668개**) | 시도 전체 (**22,699개**) | 시군구 단위 (**392개**) |
| DTO 변환 시간 | 0ms | 9ms | 0ms |
| 전체 처리 시간 | **193ms** | **1,016-1,038ms** | **53ms** ✅ |

**성능 분석**:
- ✅ **시군구 단위 검색: 51ms** - 가장 빠르고 효율적 (인덱스 활용 가능성)
- ⚠️ **시도 단위 검색: 986-990ms** - 느림 (대량 데이터, 인덱스 최적화 필요)
- ✅ **위치 기반 검색: 191ms** - 빠르지만 일관성 문제로 초기 로드에만 사용
- **하이브리드 전략**: 초기 로드는 위치 기반(빠름), 이후 검색은 **시군구 기반**(일관성 + 빠름)

---

## 🔧 구현 방안

### 1. 초기 로드

**현재**:
```javascript
// 위치 기반 검색
const response = await locationServiceApi.searchPlaces({
  latitude: location.lat,
  longitude: location.lng,
  radius: 5000,
});
```

**개선**:
```javascript
// 1. 내 위치를 역지오코딩하여 시도/시군구 추출
const addressData = await geocodingApi.coordinatesToAddress(
  location.lat,
  location.lng
);

// 2. 시도/시군구 추출
const sido = extractSido(addressData.address); // "서울특별시"
const sigungu = extractSigungu(addressData.address); // "강남구"

// 3. 시도/시군구 기반 검색
const response = await locationServiceApi.searchPlaces({
  sido: sido,
  sigungu: sigungu,
});

// 4. 거리 계산은 프론트엔드에서 (표시용)
const servicesWithDistance = response.data.services.map(service => ({
  ...service,
  distance: calculateDistance(location, service),
}));
```

### 2. 지도 이동 후 검색 ("이 지역 검색" 버튼)

**현재**:
```javascript
// 새로운 위치 기준 반경 검색 (ST_Distance_Sphere 사용)
fetchServices({
  latitude: pendingSearchLocation.lat,
  longitude: pendingSearchLocation.lng,
  radius: 5000,
});
```

**개선**:
```javascript
// 1. 지도 중심 좌표를 역지오코딩
const addressData = await geocodingApi.coordinatesToAddress(
  pendingSearchLocation.lat,
  pendingSearchLocation.lng
);

// 2. 시도/시군구 추출
const sido = extractSido(addressData.address);
const sigungu = extractSigungu(addressData.address);

// 3. 시도/시군구 기반 검색 (주 검색 방식)
fetchServices({
  sido: sido,
  sigungu: sigungu,
});

// 4. 거리 계산은 프론트엔드에서 (표시용, 보조 기능)
const servicesWithDistance = response.data.services.map(service => ({
  ...service,
  distance: calculateDistance(pendingSearchLocation, service),
}));
```

**위치 기반 검색의 역할**:
- ❌ **DB 쿼리에서 위치 기반 검색(ST_Distance_Sphere) 사용하지 않음**
- ✅ **프론트엔드에서 거리 계산만 사용 (표시용, 보조 기능)**
- ✅ **시도/시군구 기반 검색이 주 검색 방식**

### 3. 읍면동 필터링

**구현**:
```javascript
// 프론트엔드에서 클라이언트 사이드 필터링만
const filteredServices = services.filter(service => {
  if (selectedEupmyeondong && service.eupmyeondong !== selectedEupmyeondong) {
    return false;
  }
  return true;
});
```

**주의사항**:
- ❌ 읍면동 좌표/경계는 사용하지 않음
- ❌ DB 쿼리에서 읍면동 조건 사용하지 않음
- ✅ 프론트엔드 필터링만 사용

### 4. 백엔드 쿼리 최적화

**현재**:
```java
// ST_Distance_Sphere 쿼리 (복잡)
List<LocationService> findByRadius(Double latitude, Double longitude, Double radiusInMeters);
```

**개선**:
```java
// 단순 WHERE 조건 (인덱스 활용)
List<LocationService> findBySidoAndSigungu(String sido, String sigungu);

// 인덱스 추가 (필수)
CREATE INDEX idx_sido_sigungu ON locationservice(sido, sigungu);
```

**⚠️ 성능 이슈 (2025-12-25 측정)**:
- 시도 단위 검색 시 986-990ms 소요 (인덱스 미활용 가능성)
- 시도 전체 조회 시 22,699개 레코드 반환
- **인덱스 확인 및 최적화 필요**

---

## ✅ 예상 효과 및 실제 측정값

### 성능 개선 (예상)
- 쿼리 실행 시간: 320-527ms → 10-50ms (예상, 인덱스 활용 시)
- 인덱스 활용으로 빠른 조회
- 데이터가 많아져도 성능 유지

### 실제 측정값 (2025-12-25)

**위치 기반 검색 (5km 반경)**:
```
쿼리: ST_Distance_Sphere(POINT(longitude, latitude), POINT(?, ?)) <= ?
위치: latitude=37.5275469, longitude=126.9180569, radius=5000
- DB 쿼리 실행 시간: 191ms
- 조회된 레코드 수: 668개
- DTO 변환 시간: 0ms
- 전체 처리 시간: 193ms
```

**시도 단위 검색 시**:
```
쿼리: WHERE sido = ?
- DB 쿼리 실행 시간: 986-990ms ⚠️
- 조회된 레코드 수: 22,699개 (시도 전체)
- DTO 변환 시간: 9ms
- 전체 처리 시간: 1,016-1,038ms
```

**시군구 단위 검색 시** (실제 측정값, 2025-12-25):
```
쿼리: WHERE sido = ? AND sigungu = ?
- DB 쿼리 실행 시간: 51ms ✅
- 조회된 레코드 수: 392개 (시군구 단위)
- DTO 변환 시간: 0ms
- 전체 처리 시간: 53ms ✅
```

**성능 분석**:
- ✅ **시군구 단위 검색: 51ms** - 매우 빠름 (인덱스 활용 가능성)
- ⚠️ **시도 단위 검색: 986-990ms** - 느림 (인덱스 미활용 또는 대량 데이터)
- ✅ **위치 기반 검색: 191ms** - 빠르지만 일관성 문제로 초기 로드에만 사용
- **결론**: 시군구 단위 검색이 가장 효율적 (51ms, 적절한 데이터 양)

### 사용자 경험 개선
- 검색 결과 일관성 (시도/시군구로 확정)
- 사용자 이해 쉬움 ("서울 강남구" 검색)
- 이전 서비스 유지 (같은 시도/시군구 내)

### 구현 단순화
- 복잡한 영역 확장 로직 제거
- 검색 기준점 추적 로직 단순화
- 상태 관리 간소화

---

## 📝 요약

### 문제 상황
1. 검색 기준점 불일치: 초기 로드와 지도 이동 후 검색의 기준점이 다름
2. 성능 이슈: ST_Distance_Sphere 쿼리 복잡 (320-527ms)
3. 구현 복잡도: 영역 확장 로직, 검색 기준점 추적 필요

### 해결 방향
1. **검색 기준: 시도/시군구까지 (주 검색 방식)**
   - 초기 로드: 내 위치 → 역지오코딩 → 시도/시군구 추출 → 시도/시군구 검색
   - 지도 이동 후 검색 ("이 지역 검색" 버튼): 지도 중심 좌표 → 역지오코딩 → 시도/시군구 추출 → 시도/시군구 검색
   - **위치 기반 검색(ST_Distance_Sphere)은 DB 쿼리에서 사용하지 않음**

2. **읍면동: 정밀 필터로만 사용**
   - DB 쿼리에서 제외
   - 프론트엔드에서 클라이언트 사이드 필터링만
   - 읍면동 좌표/경계는 사용하지 않음

3. **거리 계산: 프론트엔드에서 처리 (보조 기능)**
   - 시도/시군구 검색 후 프론트엔드에서 거리 계산 (표시용)
   - 거리순 정렬도 프론트엔드에서 처리
   - **위치 기반 검색은 프론트엔드 거리 계산으로만 사용 (보조 기능)**

### 핵심 원칙
> **지도와 검색 기준은 시군구까지**  
> **읍면동은 정밀 필터로만 사용**  
> **읍면동 좌표/경계는 절대 들고 가지 말 것**

---

## 📚 관련 문서

- [Location 도메인 상세 설명](../../domains/location.md)
- [현재 구현 분석](./current-implementation-analysis.md)
