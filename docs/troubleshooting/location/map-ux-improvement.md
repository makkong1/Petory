# 지도 서비스 UX 개선 트러블슈팅

## 0️⃣ 문제 핵심 요약

현재 주변 서비스 지도 화면의 주요 문제점:

1. **지도 이동 = 즉시 API 호출** ❌
   - 지도를 드래그할 때마다 자동으로 서버에 요청
   - 마커가 갑자기 쏟아져 나와 UX 산만함
   - 사용자가 "이 화면에서 뭘 해야 할지" 모르는 상태

2. **지도와 리스트가 따로 놀음** ❌
   - 마커 클릭과 리스트 클릭이 독립적으로 동작
   - 동기화가 약함

3. **기능은 있는데 시나리오가 없음** ❌
   - 지도 자체가 메인 기능처럼 보임
   - 실제로는 "탐색 UI"여야 함

---

## 1️⃣ 현재 구현 상태 분석

### 1.1 초기 로드 흐름

**현재 구현** (`LocationServiceMap.js` 초기 useEffect):
```javascript
1. 내 위치 가져오기
2. 지도 중심을 내 위치로 설정 (10km 줌)
3. 내 주변 10km 반경 서비스 조회
4. 거리순 정렬하여 리스트 표시
```

**문제점**:
- ✅ 초기 로드는 명확함
- ❌ 하지만 지도 이동 시 자동 재조회로 인해 혼란

### 1.2 지도 이동 시 동작

**현재 구현** (`handleMapIdle`):
```javascript
// 지도 드래그 완료 시 (idle 이벤트)
if (!isProgrammaticMoveRef.current) {
  // 800ms 디바운싱 후 자동 API 호출
  fetchServices({
    latitude: newCenter.lat,
    longitude: newCenter.lng,
    radius: 10000,
  });
}
```

**문제점**:
- ❌ 사용자가 지도를 살짝만 움직여도 API 호출
- ❌ 마커가 갑자기 바뀌어서 혼란
- ❌ "이 지역에서 검색" 의도가 없어도 자동 실행

**요청 URL**:
```
GET /api/location-services/search?latitude=37.6674642&longitude=127.0576616&radius=10000
```

### 1.3 마커-리스트 상호작용

**현재 구현** (`handleServiceSelect`):
```javascript
// 서비스 클릭 시
1. setSelectedService(service)
2. 지도 중심 이동 (최대 확대)
3. API 재조회 방지 (isProgrammaticMoveRef)
```

**문제점**:
- ✅ 마커 클릭 → 지도 이동은 잘 동작
- ❌ 리스트 클릭 → 지도 이동은 동작하지만 동기화가 약함
- ❌ 마커 클릭 시 리스트 스크롤 이동 없음

---

## 2️⃣ 지도 서비스의 본질 재정의

### 2.1 현재 인식 (잘못된 접근)
❌ **지도 자체가 메인 기능**
- 지도를 움직이면 자동으로 데이터 변경
- 지도가 모든 것을 제어

### 2.2 올바른 인식 (수정 필요)
✅ **지도는 탐색 UI**
- 지도의 목적: "이 근처에 뭐가 있는지 빠르게 파악"
- 실제 선택과 액션은 리스트에서
- 지도는 위치를 강조만 함

---

## 3️⃣ 추천 UX 흐름 (정석)

```
[지역 선택 or 현재 위치]
        ↓
[지도 중심 이동]
        ↓
[지도 위 최소한의 마커] (10~20개)
        ↓
[하단 / 우측 리스트]
        ↓
[마커 ↔ 리스트 상호작용]
        ↓
[상세 페이지]
```

**핵심 원칙**:
- 지도는 항상 리스트를 위한 도구
- 선택은 항상 리스트에서
- 액션은 항상 상세 페이지에서

---

## 4️⃣ 현재 코드의 주요 문제점

### 문제 1: 지도 이동 = 즉시 API 호출 ❌

**현재 동작**:
```javascript
// handleMapIdle (지도 드래그 완료 시)
if (!isProgrammaticMoveRef.current) {
  setTimeout(() => {
    fetchServices({
      latitude: newCenter.lat,
      longitude: newCenter.lng,
      radius: 10000,
    });
  }, 800);
}
```

**문제**:
- 사용자가 지도를 살짝만 움직여도 자동 재조회
- 마커가 갑자기 바뀌어 혼란
- 의도하지 않은 API 호출로 인한 성능 저하

**영향**:
- UX 산만함
- 서버 부하 증가
- 사용자 혼란 ("왜 갑자기 바뀌지?")

### 문제 2: 지도와 리스트 연결이 약함 ❌

**현재 동작**:
- 마커 클릭 → 지도 이동만 (리스트 스크롤 없음)
- 리스트 클릭 → 지도 이동만 (리스트 포커싱 없음)

**문제**:
- 마커와 리스트가 독립적으로 동작
- 사용자가 어떤 항목이 선택되었는지 파악하기 어려움

### 문제 3: 마커에 정보가 너무 많음 ❌

**현재 동작**:
- 모든 서비스를 마커로 표시 (최대 500개)
- 마커 클릭 → 상세 패널 표시

**문제**:
- 마커가 너무 많아서 지도가 복잡함
- 마커 클릭 → 작은 말풍선 → 또 클릭 (2단계)

---

## 5️⃣ 개선 방안

### 개선 1: 지도 이동 시 자동 API 호출 제거 ✅

**제안 1: "이 지역 검색" 버튼 추가**
```javascript
// handleMapIdle 수정
const handleMapIdle = useCallback((mapInfo) => {
  if (!mapInfo || !mapInfo.lat || !mapInfo.lng) {
    return;
  }

  const newCenter = {
    lat: mapInfo.lat,
    lng: mapInfo.lng,
  };

  // 위치가 변경되었을 때만 상태 업데이트 (API 호출 없음)
  const isLocationChanged = !mapCenter ||
    Math.abs(mapCenter.lat - newCenter.lat) > 0.0001 ||
    Math.abs(mapCenter.lng - newCenter.lng) > 0.0001;

  if (isLocationChanged && !isProgrammaticMoveRef.current) {
    // 지도 중심만 업데이트 (API 호출 없음)
    setMapCenter(newCenter);
    
    // "이 지역 검색" 버튼 표시
    setShowSearchButton(true);
    setPendingSearchLocation(newCenter);
  }
}, [mapCenter]);
```

**제안 2: Idle 이벤트 + 더 긴 디바운싱 + 사용자 확인**
```javascript
// 3초 이상 지도가 멈춰있을 때만 확인 다이얼로그
if (isLocationChanged && !isProgrammaticMoveRef.current) {
  mapIdleTimeoutRef.current = setTimeout(() => {
    // 사용자에게 확인
    if (confirm('이 지역의 서비스를 검색하시겠습니까?')) {
      fetchServices({
        latitude: newCenter.lat,
        longitude: newCenter.lng,
        radius: 10000,
      });
    }
  }, 3000); // 3초 디바운싱
}
```

**추천**: 제안 1 (버튼 방식)이 더 명확함

### 개선 2: 마커-리스트 동기화 강화 ✅

**제안: 양방향 동기화**
```javascript
// 마커 클릭 시
const handleMarkerClick = useCallback((service) => {
  // 1. 지도 이동
  setMapCenter({ lat: service.latitude, lng: service.longitude });
  setMapLevel(3);
  
  // 2. 리스트에서 해당 항목 찾아서 스크롤
  const serviceElement = document.querySelector(`[data-service-idx="${service.idx}"]`);
  if (serviceElement) {
    serviceElement.scrollIntoView({ behavior: 'smooth', block: 'center' });
    serviceElement.classList.add('highlight'); // 하이라이트
  }
  
  // 3. 선택 상태 업데이트
  setSelectedService(service);
}, []);

// 리스트 클릭 시
const handleServiceListItemClick = useCallback((service) => {
  // 1. 지도 이동
  setMapCenter({ lat: service.latitude, lng: service.longitude });
  setMapLevel(3);
  
  // 2. 선택 상태 업데이트
  setSelectedService(service);
  
  // 3. 리스트에서 해당 항목 하이라이트
  const serviceElement = document.querySelector(`[data-service-idx="${service.idx}"]`);
  if (serviceElement) {
    serviceElement.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }
}, []);
```

### 개선 3: 마커 개수 제한 및 정보 최소화 ✅

**제안: 대표 마커만 표시**
```javascript
// 초기 로드 시 마커 개수 제한
const MAX_MARKERS = 20; // 최대 20개만 표시

// 거리순 정렬 후 상위 20개만 마커로 표시
const markersToShow = servicesWithDistance
  .slice(0, MAX_MARKERS)
  .filter(service => service.latitude && service.longitude);

// 나머지는 리스트에만 표시
```

**제안: 마커는 식별자만**
- 마커 클릭 → 리스트 포커싱 (상세 팝업 없음)
- 상세 정보는 리스트에서 확인

---

## 6️⃣ 펫 서비스에 맞는 UX 재정의

### 6.1 목적
📍 **"이 근처에 반려동물 관련 시설이 뭐가 있는지 빠르게 파악"**

### 6.2 추천 흐름

**1️⃣ 첫 진입**
```
현재 위치 기준
→ 지도 중심 이동 (10km 줌)
→ 주변 10km 반경 서비스 조회
→ 거리순 정렬하여 리스트 표시
→ 대표 20개만 마커로 표시
```

**2️⃣ 지도 이동**
```
사용자가 지도 드래그
→ 지도 중심만 변경 (API 호출 없음)
→ "이 지역 검색" 버튼 표시
→ 버튼 클릭 시에만 API 호출
```

**3️⃣ 마커 클릭**
```
마커 클릭
→ 지도 중심 이동 (최대 확대)
→ 리스트에서 해당 항목 스크롤 및 하이라이트
→ 상세 정보는 리스트에서 확인
```

**4️⃣ 리스트 클릭**
```
리스트 항목 클릭
→ 지도 중심 이동 (최대 확대)
→ 해당 마커 하이라이트
→ 상세 패널 표시 (선택적)
```

---

## 7️⃣ 구현 우선순위

### Phase 1: 핵심 문제 해결 (즉시)
1. ✅ **지도 이동 시 자동 API 호출 제거**
   - `handleMapIdle`에서 API 호출 제거
   - "이 지역 검색" 버튼 추가

2. ✅ **마커-리스트 동기화 강화**
   - 마커 클릭 → 리스트 스크롤
   - 리스트 클릭 → 지도 이동 + 마커 하이라이트

### Phase 2: UX 개선 (단기)
3. ✅ **마커 개수 제한**
   - 최대 20개만 마커로 표시
   - 나머지는 리스트에만 표시

4. ✅ **마커 정보 최소화**
   - 마커 클릭 → 상세 팝업 제거
   - 마커 클릭 → 리스트 포커싱만

### Phase 3: 고급 기능 (중기)
5. ✅ **빈 상태 UX 개선**
   - 검색 결과 없을 때 안내 메시지
   - 다른 지역 검색 유도

6. ✅ **검색어 + 지도 조합**
   - 검색어 입력 → 지도 중심 이동
   - 지도 이동 → 검색어 자동 업데이트 (선택적)

---

## 8️⃣ 핵심 질문: "조잡함" 제거 기준

**이 질문 하나만 통과하면 성공** 👇

> **"지금 이 화면에서 유저가 할 행동은 단 하나인가?"**

**YES** → 잘 만든 지도  
**NO** → 조잡한 지도

### 현재 상태 체크

**현재 화면에서 유저가 할 수 있는 행동**:
1. 지도를 드래그 → 자동으로 데이터 변경 (의도하지 않음)
2. 마커 클릭 → 상세 팝업
3. 리스트 클릭 → 상세 패널
4. 지역 선택 → 데이터 변경
5. 카테고리 선택 → 데이터 변경

**결과**: ❌ **NO** (너무 많은 행동이 가능)

### 목표 상태

**목표 화면에서 유저가 할 수 있는 행동**:
1. 지도를 드래그 → "이 지역 검색" 버튼 표시
2. "이 지역 검색" 버튼 클릭 → 데이터 변경
3. 마커/리스트 클릭 → 상세 정보 확인

**결과**: ✅ **YES** (명확한 행동 흐름)

---

## 9️⃣ 다음 단계 제안

### 즉시 수정할 항목 (우선순위 높음)

1. **지도 이동 시 자동 API 호출 제거**
   - `handleMapIdle` 수정
   - "이 지역 검색" 버튼 추가

2. **마커-리스트 동기화**
   - 양방향 동기화 구현
   - 스크롤 및 하이라이트 추가

### 단기 개선 항목

3. **마커 개수 제한**
   - 최대 20개로 제한
   - 성능 및 UX 개선

4. **마커 정보 최소화**
   - 마커는 식별자만
   - 정보는 리스트에서

---

## 🔟 참고: 깔끔한 지도 UX의 핵심 규칙 5가지

1. **지도는 정보를 말하지 않는다**
   - 지도는 위치를 강조만 함
   - 정보는 리스트에

2. **선택은 항상 리스트**
   - 마커 클릭 → 리스트 포커싱
   - 실제 선택은 리스트에서

3. **액션은 항상 상세 페이지**
   - 상세 정보 확인
   - 예약/길찾기 등 액션

4. **지도는 한 번에 하나의 의미만 가진다**
   - 현재 위치 기준
   - 또는 선택한 지역 기준
   - 둘 다 아님

5. **지도는 리스트를 위한 도구**
   - 지도가 메인이 아님
   - 탐색을 위한 보조 UI

---

## 📝 체크리스트

### 현재 상태 점검
- [ ] 지도 이동 시 자동 API 호출 여부: ✅ **있음** (문제)
- [ ] 마커-리스트 동기화: ❌ **약함** (문제)
- [ ] 마커 개수: ❌ **너무 많음** (500개, 문제)
- [ ] 마커 정보: ❌ **너무 많음** (상세 팝업, 문제)
- [ ] 사용자 행동 명확성: ❌ **불명확** (문제)

### 개선 후 목표
- [ ] 지도 이동 시 자동 API 호출: ❌ **없음** (목표)
- [ ] 마커-리스트 동기화: ✅ **강함** (목표)
- [ ] 마커 개수: ✅ **적절함** (20개, 목표)
- [ ] 마커 정보: ✅ **최소화** (식별자만, 목표)
- [ ] 사용자 행동 명확성: ✅ **명확** (목표)

---

## 📚 관련 문서

- [초기 로드 성능 최적화](./initial-load-performance.md)
- [지도 드래그 시 리로드 문제](./map-drag-reload-issue.md)
- [Location 도메인 상세 설명](../../domains/location.md)

