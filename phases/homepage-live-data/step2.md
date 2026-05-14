# Step 2 — Frontend: HomePage 브라우저 Geolocation 연동

## 목표
`navigator.geolocation`으로 사용자 실제 좌표를 얻어 meetup/service API에 전달한다.
권한 거부 시 하드코딩 좌표(서울) 대신 좌표 없이 API 호출한다.

## 변경 파일
`frontend/src/components/Home/HomePage.js`

---

## 변경 상세

### 1. 좌표 state 추가

`HomePage` 컴포넌트 상단 state 선언부에 추가:

```js
const [userCoords, setUserCoords] = useState(null); // { lat, lng } | null
```

### 2. Geolocation useEffect 추가

`fetchTabData` useCallback 선언 **이전**에 삽입:

```js
useEffect(() => {
  if (!navigator.geolocation) return;
  navigator.geolocation.getCurrentPosition(
    (pos) => setUserCoords({ lat: pos.coords.latitude, lng: pos.coords.longitude }),
    () => setUserCoords(null), // 거부 시 null 유지
    { timeout: 5000, maximumAge: 60000 }
  );
}, []);
```

### 3. `fetchTabData` — meetup/service 호출 시 좌표 적용

현재 코드 (service 탭):
```js
if (tabKey === 'service') {
  const res = await locationServiceApi.searchPlaces({ sort: 'rating', size: 6 });
```

변경 후:
```js
if (tabKey === 'service') {
  const params = { sort: 'rating', size: 6 };
  if (userCoords) {
    params.latitude = userCoords.lat;
    params.longitude = userCoords.lng;
    params.radius = 10000; // 10km
  }
  const res = await locationServiceApi.searchPlaces(params);
```

현재 코드 (meetup 탭):
```js
} else if (tabKey === 'meetup') {
  const res = await meetupApi.getNearbyMeetups(37.5665, 126.9780, 50, 6);
```

변경 후:
```js
} else if (tabKey === 'meetup') {
  const lat = userCoords?.lat ?? 37.5665;
  const lng = userCoords?.lng ?? 126.9780;
  const res = await meetupApi.getNearbyMeetups(lat, lng, 50, 6);
```

### 4. `fetchTabData`의 deps — `userCoords` 추가

```js
}, [userCoords]);
```

그리고 `fetchedTabsRef` 초기화 로직을 `userCoords` 변경 시 재실행되도록:

```js
useEffect(() => {
  fetchedTabsRef.current = new Set();
  fetchTabData('service');
}, [userCoords, fetchTabData]);
```

기존의 `fetchTabData('service')` 최초 호출 useEffect를 위 코드로 교체한다.

## AC (검증)
```bash
cd frontend && npm run build
# BUILD SUCCESSFUL
```

런타임: 브라우저에서 위치 권한 허용 → 모임 탭 위치 기반 결과, 거부 → 서울 기준 결과.
