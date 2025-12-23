# 주변 서비스 지도 UX - 현재 구현 분석 및 평가

## 📋 분석 개요

`map-ux-improvement.md` 문서에서 제시한 문제점과 개선 방안을 바탕으로, 현재 프론트엔드/백엔드 구현을 종합 분석하고 평가합니다.

---

## 0️⃣ 핵심 설계 원칙 (UX 레벨)

### 🔥 원칙 1: "지도는 상태를 바꾸지 않는다"

**현재 구현 (문제)**:
```
지도 이동 → 상태 변경 → 데이터 변경
```

**목표 설계 (개선)**:
```
지도 이동 → "상태 변경 가능" 표시 → 사용자 확인 → 데이터 변경
```

**핵심 문장**:
> **지도는 상태를 직접 변경하지 않고, 상태 변경 "의사"만 만든다**

**설계 의미**:
- 지도는 탐색 UI일 뿐, 데이터를 제어하지 않음
- 사용자의 명시적 확인 없이는 데이터 변경 불가
- 지도 이동 = "이 지역을 탐색할 의사가 있음"을 표시만 함

### 🔥 원칙 2: 초기 로드 vs 탐색 로드 분리

**개념 분리**:

| 구분 | 성격 | 트리거 | 목적 |
|------|------|--------|------|
| **InitialLoadSearch** | 시스템 주도 | 페이지 진입 시 | 사용자에게 초기 컨텍스트 제공 |
| **UserTriggeredSearch** | 사용자 주도 | 명시적 검색 액션 | 사용자가 원하는 지역 탐색 |

**현재 구현 문제**:
- 두 가지가 같은 API 흐름으로 처리됨
- 구분이 없어서 UX 혼란 발생

**개선 방향**:
- `InitialLoadSearch`: 자동 실행 (시스템 주도)
- `UserTriggeredSearch`: 사용자 확인 후 실행 (사용자 주도)

### 🔥 원칙 3: 빈 상태 UX 처리

**빈 상태 시나리오**:
1. 검색 결과 0개
2. 위치 권한 거부
3. 너무 넓은 범위 (전국 단위)

**현재 문제**:
- "지도에는 아무것도 안 나오는데, 이게 정상인지 불안"
- 사용자가 다음 행동을 모름

**개선 방향**:
- 명확한 안내 메시지
- 대안 제시 (다른 지역 검색, 카테고리 변경 등)
- 빈 상태도 하나의 "상태"로 인식

---

## 1️⃣ 현재 구현 흐름 분석

### 1.1 전체 흐름도

```
[사용자 액션]
    ↓
[프론트엔드 LocationServiceMap.js]
    ↓
[API 호출: locationServiceApi.searchPlaces()]
    ↓
[백엔드 LocationServiceController.searchLocationServices()]
    ↓
[LocationServiceService.searchLocationServicesByLocation()]
    ↓
[LocationServiceRepository.findByRadius() - ST_Distance_Sphere]
    ↓
[응답: LocationServiceDTO 리스트]
    ↓
[프론트엔드: 마커 표시 + 리스트 표시]
```

### 1.2 초기 로드 흐름 (InitialLoadSearch)

**개념**: 시스템 주도 검색 - 페이지 진입 시 자동 실행

**프론트엔드** (`LocationServiceMap.js` 초기 useEffect):
```javascript
1. navigator.geolocation.getCurrentPosition()
   ↓
2. setUserLocation(location)
   ↓
3. setMapCenter(location) + setMapLevel(10km 줌)
   ↓
4. locationServiceApi.searchPlaces({
     latitude: location.lat,
     longitude: location.lng,
     radius: 10000
   })
   ↓
5. 거리 계산 및 정렬
   ↓
6. setAllServices() + setServices()
   ↓
7. MapContainer에 services 전달 → 마커 표시
```

**백엔드** (`LocationServiceController` → `LocationServiceService`):
```java
1. LocationServiceController.searchLocationServices()
   - latitude, longitude, radius 파라미터 수신
   ↓
2. LocationServiceService.searchLocationServicesByLocation()
   - ST_Distance_Sphere 쿼리 실행 (527ms, 1021개 레코드)
   - 카테고리 필터링 (필요 시)
   - DTO 변환 (15ms)
   ↓
3. 응답: List<LocationServiceDTO>
```

**평가**:
- ✅ 초기 로드는 명확하고 효율적
- ✅ 백엔드 성능 측정 로깅 잘 구현됨
- ✅ 시스템 주도이므로 자동 실행이 적절함
- ❌ 하지만 지도 이동 시 자동 재조회로 인해 혼란 (UserTriggeredSearch와 혼동)

### 1.3 지도 이동 시 흐름 (UserTriggeredSearch - 현재 구현)

**개념**: 사용자 주도 검색 - 지도 이동 시 자동 실행 (문제)

**현재 구현 (문제)**:
```javascript
1. 사용자가 지도 드래그
   ↓
2. MapContainer의 idle 이벤트 발생
   ↓
3. handleMapIdle() 호출
   ↓
4. isProgrammaticMoveRef.current === false 확인
   ↓
5. 800ms 디바운싱
   ↓
6. fetchServices({  // ❌ 자동 실행 (문제)
     latitude: newCenter.lat,
     longitude: newCenter.lng,
     radius: 10000
   })
   ↓
7. 백엔드 API 호출 (자동)
   ↓
8. 마커 갱신 (갑자기 쏟아짐)
```

**문제점 분석**:
- ❌ **원칙 위반**: "지도는 상태를 바꾸지 않는다" 위반
  - 지도 이동 → 즉시 상태 변경 (데이터 변경)
  - 사용자 확인 없이 실행
- ❌ **개념 혼동**: InitialLoadSearch와 UserTriggeredSearch 구분 없음
  - 둘 다 같은 방식으로 처리
  - 사용자 의도 파악 불가
- ❌ UX 산만함: 마커가 갑자기 바뀜
- ❌ 서버 부하: 불필요한 API 호출 증가

**목표 구현 (개선)**:
```javascript
1. 사용자가 지도 드래그
   ↓
2. MapContainer의 idle 이벤트 발생
   ↓
3. handleMapIdle() 호출
   ↓
4. isProgrammaticMoveRef.current === false 확인
   ↓
5. setPendingSearchLocation(newCenter)  // ✅ 상태 변경 의사만 표시
   ↓
6. setShowSearchButton(true)  // ✅ "이 지역 검색" 버튼 표시
   ↓
7. [사용자가 버튼 클릭]  // ✅ 사용자 확인
   ↓
8. fetchServices({  // ✅ 명시적 실행
     latitude: pendingSearchLocation.lat,
     longitude: pendingSearchLocation.lng,
     radius: 10000
   })
```

**백엔드**:
- 동일한 API 엔드포인트 사용 (변경 불필요)
- ST_Distance_Sphere 쿼리 실행 (320-527ms)
- 응답: 새로운 위치 기준 서비스 리스트

**평가**:
- ❌ **핵심 문제**: "지도는 상태를 바꾸지 않는다" 원칙 위반
- ❌ **개념 혼동**: UserTriggeredSearch가 InitialLoadSearch처럼 자동 실행됨
- ❌ UX 산만함: 마커가 갑자기 바뀜
- ❌ 서버 부하: 불필요한 API 호출 증가

### 1.3.1 지역 선택 후 지도 이동 시나리오 (추가 문제)

**시나리오**:
```
1. 사용자가 지역 선택으로 "서울특별시 강남구" 선택
   ↓
2. 지도를 다른 곳으로 드래그 (예: 부산으로 이동)
   ↓
3. "이 지역 검색" 버튼 표시
   ↓
4. 버튼 클릭 → 새로운 위치(부산) 기준으로 검색
```

**현재 구현 문제**:
```javascript
// handleSearchButtonClick (현재)
const handleSearchButtonClick = useCallback(() => {
  // ❌ 문제: 지역 선택 상태(selectedSido, selectedSigungu)를 고려하지 않음
  fetchServices({
    latitude: pendingSearchLocation.lat,
    longitude: pendingSearchLocation.lng,
    radius: 10000,
    categoryOverride: effectiveCategoryType,
  });
  // 지역 선택 상태는 그대로 유지됨
}, [pendingSearchLocation, categoryType, fetchServices]);
```

**문제점**:
- ❌ 지역 선택 상태(`selectedSido`, `selectedSigungu`)와 위치 기반 검색이 충돌
- ❌ 사용자가 "서울특별시 강남구"를 선택했는데, 지도를 부산으로 이동 후 검색하면 부산 결과가 나옴
- ❌ 지역 선택 상태가 유지되어 혼란 발생
- ❌ 지역 선택 UI와 실제 검색 결과가 불일치

**해결 방안**:
```javascript
// 개선된 handleSearchButtonClick
const handleSearchButtonClick = useCallback(() => {
  if (!pendingSearchLocation) {
    return;
  }

  const effectiveCategoryType = categoryType !== CATEGORY_DEFAULT && categoryType !== CATEGORY_CUSTOM
    ? categoryType
    : undefined;

  // ✅ 해결: 지역 선택 상태 초기화 (위치 기반 검색으로 전환)
  setSelectedSido('');
  setSelectedSigungu('');
  setSelectedEupmyeondong('');
  setCurrentMapView('sido');
  
  console.log('📍 [UserTriggeredSearch] 지역 선택 해제 후 위치 기반 검색:', pendingSearchLocation);
  
  // 위치 기반 검색 실행
  fetchServices({
    latitude: pendingSearchLocation.lat,
    longitude: pendingSearchLocation.lng,
    radius: 10000,
    categoryOverride: effectiveCategoryType,
  });

  // 버튼 숨기기 및 대기 위치 초기화
  setShowSearchButton(false);
  setPendingSearchLocation(null);
}, [pendingSearchLocation, categoryType, fetchServices]);
```

**설계 원칙**:
- ✅ **지역 선택 vs 위치 기반 검색은 상호 배타적**
- ✅ 지도 이동 후 "이 지역 검색" 버튼 클릭 = 지역 선택 해제 + 위치 기반 검색
- ✅ 사용자가 명시적으로 위치를 선택했으므로 지역 선택보다 우선순위 높음

### 1.4 마커-리스트 상호작용 (현재 구현)

**마커 클릭** (`handleMarkerClick` → `handleServiceSelect`):
```javascript
1. 마커 클릭
   ↓
2. handleServiceSelect(service)
   ↓
3. setSelectedService(service)
   ↓
4. setMapCenter() + setMapLevel(3) // 최대 확대
   ↓
5. isProgrammaticMoveRef.current = true // API 재조회 방지
```

**리스트 클릭** (`ServiceListItem onClick`):
```javascript
1. 리스트 항목 클릭
   ↓
2. handleServiceSelect(service)
   ↓
3. 동일한 로직 (지도 이동 + 최대 확대)
```

**평가**:
- ✅ 마커 클릭 → 지도 이동: 잘 동작
- ✅ 리스트 클릭 → 지도 이동: 잘 동작
- ❌ **문제**: 리스트 스크롤 이동 없음
- ❌ **문제**: 마커 하이라이트 없음
- ❌ **문제**: 양방향 동기화 약함

### 1.5 마커 표시 로직 (현재 구현)

**MapContainer.js**:
```javascript
1. services prop 받음
   ↓
2. 마커 개수 제한: maxMarkers = 500
   ↓
3. 배치 처리: 50개씩 생성
   ↓
4. 네이버맵 Marker 생성
   ↓
5. 마커 클릭 이벤트: onServiceClick(service)
```

**평가**:
- ⚠️ 마커 개수: 500개는 여전히 많음 (문서 권장: 20개)
- ✅ 배치 처리: 성능 최적화 잘 구현됨
- ❌ **문제**: 마커가 너무 많아 지도 복잡함
- ❌ **문제**: 마커 클릭 시 상세 팝업 표시 (정보 과다)

### 1.6 빈 상태 UX (현재 구현)

**현재 빈 상태 처리**:

#### 시나리오 1: 검색 결과 0개
```javascript
// 현재 구현
if (response.data?.services?.length === 0) {
  setStatusMessage('주변에 표시할 장소가 없습니다.');
  setServices([]);
  setAllServices([]);
}
```

**문제점**:
- ❌ 지도에는 아무것도 안 나옴
- ❌ 사용자가 "이게 정상인지" 불안
- ❌ 다음 행동을 모름

#### 시나리오 2: 위치 권한 거부
```javascript
// 현재 구현
catch (error) {
  console.warn('위치 정보를 가져올 수 없습니다:', error);
  // 기본 위치(서울)로 설정하고 전체 조회
  setMapCenter(DEFAULT_CENTER);
  setMapLevel(10);
  // 전체 조회 실행
}
```

**문제점**:
- ⚠️ 위치 권한 거부 시 안내 메시지 없음
- ⚠️ 사용자가 왜 서울이 나오는지 모를 수 있음

#### 시나리오 3: 너무 넓은 범위 (전국 단위)
```javascript
// 현재 구현
// size 파라미터 없으면 전체 반환 (1021개)
// 마커 500개 제한
```

**문제점**:
- ⚠️ 전국 단위 검색 시 결과가 너무 많음
- ⚠️ 사용자가 "이게 내 주변인가?" 혼란

**개선 방향**:
- ✅ 명확한 안내 메시지
- ✅ 대안 제시 (다른 지역 검색, 카테고리 변경)
- ✅ 빈 상태도 하나의 "상태"로 인식

---

## 2️⃣ 백엔드 로직 평가

### 2.1 API 엔드포인트

**엔드포인트**: `GET /api/location-services/search`

**파라미터**:
- `latitude`, `longitude`, `radius`: 위치 기반 검색
- `sido`, `sigungu`, `eupmyeondong`, `roadName`: 지역 계층별 검색
- `category`: 카테고리 필터링
- `size`: 최대 결과 수 제한

**평가**:
- ✅ 유연한 파라미터 설계
- ✅ 위치 기반 / 지역 기반 검색 모두 지원
- ✅ 성능 측정 로깅 잘 구현됨

### 2.2 쿼리 성능

**현재 성능** (실제 로그 기준):
- 쿼리 실행 시간: 320-527ms
- 조회 레코드 수: 1021개
- DTO 변환 시간: 0-16ms
- 전체 처리 시간: 321-549ms

**평가**:
- ⚠️ ST_Distance_Sphere 쿼리는 전체 테이블 스캔 필요
- ⚠️ 1021개 레코드는 많음 (마커 500개 제한과 불일치)
- ✅ 성능 측정 로깅으로 모니터링 가능
- ⚠️ 인덱스 최적화 필요 (공간 인덱스 고려)

### 2.3 백엔드 개선 여지

**현재 상태**:
- ✅ 로직은 명확하고 효율적
- ✅ 성능 측정 로깅 잘 구현됨
- ⚠️ 결과 수 제한 기본값 없음 (size 파라미터 없으면 전체 반환)

**개선 제안**:
- 기본 결과 수 제한 추가 (예: 100개)
- 공간 인덱스 활용 검토
- 캐싱 전략 고려 (동일 위치 반복 요청 시)

---

## 3️⃣ 프론트엔드 로직 평가

### 3.1 문제점 체크리스트

#### ❌ 문제 1: 지도 이동 = 즉시 API 호출
**현재 구현**:
- `handleMapIdle`에서 800ms 디바운싱 후 자동 호출
- 사용자 의도와 무관하게 실행

**심각도**: 🔴 **높음**
- UX 혼란
- 서버 부하
- 불필요한 네트워크 트래픽

#### ❌ 문제 2: 마커-리스트 동기화 약함
**현재 구현**:
- 마커 클릭 → 지도 이동만
- 리스트 클릭 → 지도 이동만
- 리스트 스크롤 이동 없음
- 마커 하이라이트 없음

**심각도**: 🟡 **중간**
- 사용자 혼란
- 선택 상태 파악 어려움

#### ⚠️ 문제 3: 마커 개수 과다
**현재 구현**:
- 최대 500개 마커 표시
- 배치 처리로 성능 최적화는 되어 있음

**심각도**: 🟡 **중간**
- 지도 복잡함
- 문서 권장: 20개

#### ⚠️ 문제 4: 마커 정보 과다
**현재 구현**:
- 마커 클릭 → 상세 패널 표시
- 2단계 클릭 필요

**심각도**: 🟢 **낮음**
- UX는 불편하지만 기능적으로는 동작

### 3.2 잘 구현된 부분

#### ✅ 초기 로드 로직
- 명확한 단계별 처리
- 에러 처리 잘 구현됨
- 거리 계산 및 정렬

#### ✅ 성능 최적화
- 배치 처리 (50개씩)
- 마커 개수 제한 (500개)
- 메모이제이션 (`useMemo`)

#### ✅ 프로그래매틱 이동 처리
- `isProgrammaticMoveRef`로 API 재조회 방지
- 서비스 클릭 시 자동 재조회 없음

---

## 4️⃣ 문서 제안 vs 현재 구현 비교

### 4.1 개선 1: "지도는 상태를 바꾸지 않는다" 원칙 적용

**핵심 원칙**:
> 지도는 상태를 직접 변경하지 않고, 상태 변경 "의사"만 만든다

**문서 제안**:
- 지도 이동 → "상태 변경 가능" 표시
- 사용자 확인 → 데이터 변경

**현재 구현**:
- 지도 이동 → 즉시 상태 변경 (데이터 변경)
- 사용자 확인 없음

**평가**:
- ❌ **원칙 위반**: 현재는 지도가 직접 상태를 변경함
- ❌ **문서 제안이 더 나음**: 사용자 의도 명확화
- ✅ 버튼 방식이 원칙에 부합함

**구현 방안**:
```javascript
// handleMapIdle 수정
const handleMapIdle = useCallback((mapInfo) => {
  // ... 위치 변경 확인 ...
  
  if (isLocationChanged && !isProgrammaticMoveRef.current) {
    // ❌ 기존: 즉시 API 호출
    // fetchServices({ latitude, longitude, radius });
    
    // ✅ 개선: 상태 변경 의사만 표시
    setPendingSearchLocation(newCenter);
    setShowSearchButton(true);
  }
}, []);

// "이 지역 검색" 버튼 클릭 시
const handleSearchButtonClick = useCallback(() => {
  if (pendingSearchLocation) {
    // ✅ 추가: 지역 선택 상태 초기화 (위치 기반 검색으로 전환)
    setSelectedSido('');
    setSelectedSigungu('');
    setSelectedEupmyeondong('');
    setCurrentMapView('sido');
    
    fetchServices({
      latitude: pendingSearchLocation.lat,
      longitude: pendingSearchLocation.lng,
      radius: 10000,
    });
    setShowSearchButton(false);
    setPendingSearchLocation(null);
  }
}, [pendingSearchLocation]);
```

**구현 난이도**: 🟢 **쉬움**
- `handleMapIdle` 수정 (API 호출 제거)
- 버튼 UI 추가
- 상태 관리 추가 (`pendingSearchLocation`, `showSearchButton`)
- **추가**: 지역 선택 상태 초기화 로직

### 4.1-1 개선 1-1: 지역 선택 후 지도 이동 시나리오 처리

**문제 시나리오**:
1. 사용자가 지역 선택으로 "서울특별시 강남구" 선택
2. 지도를 다른 곳으로 드래그 (예: 부산으로 이동)
3. "이 지역 검색" 버튼 클릭
4. **문제**: 지역 선택 상태가 유지되어 혼란 발생

**현재 구현**:
- 지역 선택 상태(`selectedSido`, `selectedSigungu`)와 위치 기반 검색이 충돌
- 지역 선택 UI와 실제 검색 결과가 불일치

**해결 방안**:
```javascript
// handleSearchButtonClick 수정
const handleSearchButtonClick = useCallback(() => {
  if (!pendingSearchLocation) {
    return;
  }

  // ✅ 지역 선택 상태 초기화 (위치 기반 검색으로 전환)
  setSelectedSido('');
  setSelectedSigungu('');
  setSelectedEupmyeondong('');
  setCurrentMapView('sido');
  
  // 위치 기반 검색 실행
  fetchServices({
    latitude: pendingSearchLocation.lat,
    longitude: pendingSearchLocation.lng,
    radius: 10000,
    categoryOverride: effectiveCategoryType,
  });

  setShowSearchButton(false);
  setPendingSearchLocation(null);
}, [pendingSearchLocation, categoryType, fetchServices]);
```

**설계 원칙**:
- ✅ **지역 선택 vs 위치 기반 검색은 상호 배타적**
- ✅ 지도 이동 후 "이 지역 검색" 버튼 클릭 = 지역 선택 해제 + 위치 기반 검색
- ✅ 사용자가 명시적으로 위치를 선택했으므로 지역 선택보다 우선순위 높음

**구현 난이도**: 🟢 **쉬움**
- `handleSearchButtonClick`에 지역 선택 상태 초기화 로직 추가

### 4.2 개선 2: InitialLoadSearch vs UserTriggeredSearch 분리

**개념 분리**:

| 구분 | 트리거 | 실행 방식 | 목적 |
|------|--------|-----------|------|
| **InitialLoadSearch** | 페이지 진입 | 자동 실행 | 초기 컨텍스트 제공 |
| **UserTriggeredSearch** | 명시적 액션 | 사용자 확인 후 | 사용자 의도 탐색 |

**현재 구현**:
- 두 가지가 같은 `fetchServices` 함수로 처리
- 구분 없이 모두 자동 실행

**개선 방안**:
```javascript
// InitialLoadSearch (시스템 주도)
const performInitialLoad = useCallback(async () => {
  // 자동 실행
  const location = await getCurrentLocation();
  await fetchServices({
    latitude: location.lat,
    longitude: location.lng,
    radius: 10000,
    searchType: 'INITIAL_LOAD', // 구분자 추가
  });
}, []);

// UserTriggeredSearch (사용자 주도)
const performUserTriggeredSearch = useCallback(async (location) => {
  // 사용자 확인 후 실행
  await fetchServices({
    latitude: location.lat,
    longitude: location.lng,
    radius: 10000,
    searchType: 'USER_TRIGGERED', // 구분자 추가
  });
}, []);
```

**구현 난이도**: 🟡 **보통**
- 검색 타입 구분자 추가
- 로직 분리
- 로깅 및 분석을 위한 구분

### 4.3 개선 3: 마커-리스트 동기화 강화

**문서 제안**:
- 마커 클릭 → 리스트 스크롤 + 하이라이트
- 리스트 클릭 → 지도 이동 + 마커 하이라이트

**현재 구현**:
- 마커 클릭 → 지도 이동만
- 리스트 클릭 → 지도 이동만

**평가**:
- ❌ **문서 제안이 더 나음**
- 현재는 동기화가 약함

**구현 난이도**: 🟡 **보통**
- `scrollIntoView` 사용
- 하이라이트 스타일 추가
- `data-service-idx` 속성 추가 필요

### 4.4 개선 4: 마커 개수 제한

**문서 제안**:
- 최대 20개만 마커로 표시
- 나머지는 리스트에만 표시

**현재 구현**:
- 최대 500개 마커 표시

**평가**:
- ❌ **문서 제안이 더 나음**
- 500개는 여전히 많음

**구현 난이도**: 🟢 **쉬움**
- `maxMarkers` 값만 변경 (500 → 20)
- `services.slice(0, 20)` 적용

### 4.5 개선 5: 마커 정보 최소화

**문서 제안**:
- 마커 클릭 → 상세 팝업 제거
- 마커 클릭 → 리스트 포커싱만

**현재 구현**:
- 마커 클릭 → 상세 패널 표시

**평가**:
- ⚠️ **선택적 개선**
- 현재도 동작은 하지만 UX 개선 가능

**구현 난이도**: 🟡 **보통**
- 상세 패널 표시 로직 수정
- 리스트 포커싱 로직 추가

### 4.6 개선 6: 빈 상태 UX 개선

**시나리오별 개선 방안**:

#### 시나리오 1: 검색 결과 0개
```javascript
// 개선 방안
if (services.length === 0) {
  return (
    <EmptyState>
      <EmptyIcon>📍</EmptyIcon>
      <EmptyTitle>이 지역에 표시할 장소가 없습니다</EmptyTitle>
      <EmptyMessage>
        다른 지역을 검색하거나 카테고리를 변경해보세요.
      </EmptyMessage>
      <EmptyActions>
        <Button onClick={handleResetSearch}>전국 보기</Button>
        <Button onClick={handleChangeCategory}>카테고리 변경</Button>
      </EmptyActions>
    </EmptyState>
  );
}
```

#### 시나리오 2: 위치 권한 거부
```javascript
// 개선 방안
catch (error) {
  return (
    <EmptyState>
      <EmptyIcon>🔒</EmptyIcon>
      <EmptyTitle>위치 정보를 사용할 수 없습니다</EmptyTitle>
      <EmptyMessage>
        위치 권한을 허용하면 내 주변 서비스를 찾을 수 있습니다.
        <br />
        또는 지역을 직접 선택해주세요.
      </EmptyMessage>
      <EmptyActions>
        <Button onClick={handleRequestLocation}>위치 권한 요청</Button>
        <Button onClick={handleSelectRegion}>지역 선택</Button>
      </EmptyActions>
    </EmptyState>
  );
}
```

#### 시나리오 3: 너무 넓은 범위
```javascript
// 개선 방안
if (services.length > 1000) {
  return (
    <EmptyState>
      <EmptyIcon>🌐</EmptyIcon>
      <EmptyTitle>검색 결과가 너무 많습니다</EmptyTitle>
      <EmptyMessage>
        전국 단위 검색 결과입니다. 지역을 선택하거나 카테고리를 변경하면 더 정확한 결과를 볼 수 있습니다.
      </EmptyMessage>
      <EmptyActions>
        <Button onClick={handleSelectRegion}>지역 선택</Button>
        <Button onClick={handleChangeCategory}>카테고리 변경</Button>
      </EmptyActions>
    </EmptyState>
  );
}
```

**구현 난이도**: 🟡 **보통**
- 빈 상태 컴포넌트 추가
- 시나리오별 메시지 및 액션 정의
- 사용자 가이드 제공

---

## 5️⃣ 종합 평가

### 5.1 현재 상태 점수

| 항목 | 점수 | 평가 |
|------|------|------|
| 초기 로드 로직 | 8/10 | ✅ 명확하고 효율적 |
| 지도 이동 처리 | 3/10 | ❌ 자동 API 호출 문제 |
| 마커-리스트 동기화 | 5/10 | ⚠️ 기본 동작만 있음 |
| 마커 개수 관리 | 4/10 | ⚠️ 500개는 여전히 많음 |
| 백엔드 성능 | 7/10 | ✅ 성능 측정 잘 구현됨 |
| **전체 평균** | **5.4/10** | ⚠️ **개선 필요** |

### 5.2 핵심 문제 우선순위

#### 🔴 최우선 (즉시 수정)
1. **"지도는 상태를 바꾸지 않는다" 원칙 적용**
   - 지도 이동 시 자동 API 호출 제거
   - "이 지역 검색" 버튼 추가
   - **추가**: 지역 선택 후 지도 이동 시나리오 처리 (지역 선택 상태 초기화)
   - 영향: UX 혼란 해소, 서버 부하 감소, 설계 원칙 준수, 지역 선택과 위치 기반 검색 충돌 해결
   - 난이도: 🟢 쉬움
   - 예상 효과: 높음

2. **InitialLoadSearch vs UserTriggeredSearch 분리**
   - 개념 분리 및 로직 구분
   - 영향: 코드 명확성, 유지보수성 향상
   - 난이도: 🟡 보통
   - 예상 효과: 중간

#### 🟡 우선 (단기 개선)
3. **마커-리스트 동기화 강화**
   - 마커 클릭 → 리스트 스크롤
   - 리스트 클릭 → 지도 이동 + 마커 하이라이트
   - 영향: 사용자 혼란 감소
   - 난이도: 🟡 보통
   - 예상 효과: 중간

4. **마커 개수 제한 (500 → 20)**
   - 영향: 지도 복잡함 해소
   - 난이도: 🟢 쉬움
   - 예상 효과: 중간

5. **빈 상태 UX 개선**
   - 검색 결과 0개, 위치 권한 거부, 너무 넓은 범위 처리
   - 영향: 사용자 불안 해소, 다음 행동 가이드
   - 난이도: 🟡 보통
   - 예상 효과: 중간

#### 🟢 선택적 (중기 개선)
6. **마커 정보 최소화**
   - 마커 클릭 → 상세 팝업 제거
   - 마커 클릭 → 리스트 포커싱만
   - 영향: UX 개선
   - 난이도: 🟡 보통
   - 예상 효과: 낮음

### 5.3 백엔드 평가

**강점**:
- ✅ 로직이 명확하고 효율적
- ✅ 성능 측정 로깅 잘 구현됨
- ✅ 유연한 파라미터 설계

**개선 여지**:
- ⚠️ 기본 결과 수 제한 없음 (전체 반환 가능)
- ⚠️ 공간 인덱스 활용 검토 필요
- ⚠️ 캐싱 전략 고려 (동일 위치 반복 요청)

**종합 평가**: 7/10
- 백엔드는 잘 구현되어 있음
- 프론트엔드 UX 개선이 더 시급함

---

## 6️⃣ 구현 로드맵

### Phase 1: 핵심 문제 해결 (1-2일)

#### 작업 1: "지도는 상태를 바꾸지 않는다" 원칙 적용
```javascript
// handleMapIdle 수정
- API 호출 제거
- "이 지역 검색" 버튼 추가
- pendingSearchLocation 상태 추가
- showSearchButton 상태 추가
```

**예상 시간**: 2-3시간

#### 작업 1-1: 지역 선택 후 지도 이동 시나리오 처리 (추가)
```javascript
// handleSearchButtonClick 수정
- 지역 선택 상태 초기화 (selectedSido, selectedSigungu, selectedEupmyeondong)
- 위치 기반 검색 실행
- 지역 선택 UI와 검색 결과 일치 보장
```

**예상 시간**: 30분

#### 작업 2: InitialLoadSearch vs UserTriggeredSearch 분리
```javascript
// 검색 타입 구분자 추가
- performInitialLoad() 함수 분리
- performUserTriggeredSearch() 함수 분리
- searchType 파라미터 추가
```

**예상 시간**: 1-2시간

#### 작업 3: 마커 개수 제한 (500 → 20)
```javascript
// MapContainer.js 수정
const maxMarkers = 20; // 500 → 20
```

**예상 시간**: 10분

### Phase 2: UX 개선 (2-3일)

#### 작업 4: 마커-리스트 동기화
```javascript
// handleServiceSelect 수정
- 리스트 스크롤 추가 (scrollIntoView)
- 하이라이트 추가
- data-service-idx 속성 추가
```

**예상 시간**: 3-4시간

#### 작업 5: 빈 상태 UX 개선
```javascript
// EmptyState 컴포넌트 추가
- 검색 결과 0개 처리
- 위치 권한 거부 처리
- 너무 넓은 범위 처리
- 대안 액션 버튼 추가
```

**예상 시간**: 2-3시간

#### 작업 6: 마커 정보 최소화 (선택적)
```javascript
// 마커 클릭 시 상세 팝업 제거
// 리스트 포커싱만
```

**예상 시간**: 2-3시간

### Phase 3: 백엔드 개선 (선택적)

#### 작업 5: 기본 결과 수 제한
```java
// LocationServiceController 수정
int defaultSize = 100; // 기본값 추가
```

**예상 시간**: 30분

---

## 7️⃣ 결론 및 권장사항

### 7.1 현재 상태 요약

**잘 구현된 부분**:
- ✅ 초기 로드 로직 명확
- ✅ 백엔드 성능 측정 로깅
- ✅ 기본적인 마커-리스트 상호작용

**개선이 필요한 부분**:
- ❌ 지도 이동 시 자동 API 호출 (최우선)
- ⚠️ 마커-리스트 동기화 약함
- ⚠️ 마커 개수 과다 (500개)

### 7.2 권장 우선순위

1. **즉시 수정** (Phase 1)
   - "지도는 상태를 바꾸지 않는다" 원칙 적용
   - InitialLoadSearch vs UserTriggeredSearch 분리
   - 마커 개수 제한 (500 → 20)

2. **단기 개선** (Phase 2)
   - 마커-리스트 동기화 강화
   - 빈 상태 UX 개선

3. **중기 개선** (Phase 3)
   - 마커 정보 최소화
   - 백엔드 기본 결과 수 제한

### 7.3 예상 효과

**Phase 1 완료 시**:
- UX 혼란 해소
- 서버 부하 감소
- 사용자 의도 명확화

**Phase 2 완료 시**:
- 마커-리스트 동기화 강화
- 사용자 혼란 추가 감소

**전체 완료 시**:
- 문서에서 제시한 "조잡함" 제거
- 명확한 UX 흐름 확립

---

## 8️⃣ 참고: 현재 vs 목표 비교

### 현재 상태
```
사용자 행동:
1. 지도 드래그 → 자동 데이터 변경 (의도 없음)
2. 마커 클릭 → 상세 팝업
3. 리스트 클릭 → 상세 패널
4. 지역 선택 → 데이터 변경
5. 카테고리 선택 → 데이터 변경

결과: ❌ NO (너무 많은 행동)
```

### 목표 상태
```
사용자 행동:
1. 지도 드래그 → "이 지역 검색" 버튼 표시 (상태 변경 의사만 표시)
2. "이 지역 검색" 버튼 클릭 → 데이터 변경 (사용자 확인 후)
3. 마커/리스트 클릭 → 상세 정보 확인

결과: ✅ YES (명확한 행동 흐름)
```

### 핵심 원칙 적용

**"지도는 상태를 바꾸지 않는다"**:
- ✅ 지도 이동 = 상태 변경 의사 표시만
- ✅ 실제 데이터 변경 = 사용자 확인 후
- ✅ 지도는 탐색 UI일 뿐, 데이터 제어하지 않음

**InitialLoadSearch vs UserTriggeredSearch**:
- ✅ InitialLoadSearch: 시스템 주도, 자동 실행
- ✅ UserTriggeredSearch: 사용자 주도, 확인 후 실행
- ✅ 명확한 개념 분리로 코드 가독성 향상

**빈 상태 UX**:
- ✅ 검색 결과 0개 → 명확한 안내 + 대안 제시
- ✅ 위치 권한 거부 → 안내 메시지 + 대안 제시
- ✅ 너무 넓은 범위 → 안내 메시지 + 범위 축소 유도

---

## 📚 관련 문서

- [지도 UX 개선 트러블슈팅](./map-ux-improvement.md)
- [초기 로드 성능 최적화](./initial-load-performance.md)
- [지도 드래그 시 리로드 문제](./map-drag-reload-issue.md)
- [Location 도메인 상세 설명](../../domains/location.md)

