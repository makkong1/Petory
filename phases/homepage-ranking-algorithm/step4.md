# Step 4 — Frontend: HomePage API 연동 업데이트

## 목표
Step 1~3에서 만든 백엔드 엔드포인트를 HomePage에 연결한다.
- 주변서비스: `sort=score` 파라미터 추가
- 모임: `/home` 엔드포인트 사용 (프론트 폴백 로직 제거 — 서버에서 처리)
- 실종신고: `/home` 엔드포인트 사용

## 변경 파일
- `frontend/src/api/meetupApi.js`
- `frontend/src/api/missingPetApi.js`
- `frontend/src/components/Home/HomePage.js`

---

## 변경 상세

### 1. `meetupApi.js` — getHomeMeetups 추가

`getRecruitingMeetups` 바로 아래에 삽입:

```js
getHomeMeetups: (lat, lng, size = 6) => {
  if (isDemoMode()) return mockResolve(DEMO_MEETUPS.slice(0, size));
  const params = { size };
  if (lat != null && lng != null) {
    params.lat = lat;
    params.lng = lng;
  }
  return api.get('/home', { params });
},
```

### 2. `missingPetApi.js` — getHomeMissing 추가

`list` 메서드 바로 아래에 삽입:

```js
getHomeMissing: (lat, lng, size = 6) => {
  if (isDemoMode()) {
    const { DEMO_MISSING_PETS } = require('../mock/demoData');
    return Promise.resolve({ data: DEMO_MISSING_PETS.slice(0, size) });
  }
  const params = { size };
  if (lat != null && lng != null) {
    params.lat = lat;
    params.lng = lng;
  }
  return api.get('/home', { params });
},
```

> `missingPetApi.js` 상단에 이미 `DEMO_MISSING_PETS` import가 있으면 `require` 대신 그대로 사용.

### 3. `HomePage.js` — fetchTabData 업데이트

`fetchTabData` 내부의 각 탭 처리 블록을 아래와 같이 변경:

#### service 탭 — `sort: 'score'` 추가

현재:
```js
const params = { sort: 'rating', size: 6 };
```

변경 후:
```js
const params = { sort: 'score', size: 6 };
```

#### meetup 탭 — getHomeMeetups로 교체

현재:
```js
} else if (tabKey === 'meetup') {
  const lat = userCoords?.lat ?? 37.5665;
  const lng = userCoords?.lng ?? 126.9780;
  const res = await meetupApi.getNearbyMeetups(lat, lng, 50, 6);
  items = toArr(res.data?.meetups ?? res.data?.content ?? res.data);
  if (items.length === 0) {
    const fallback = await meetupApi.getRecruitingMeetups(6);
    items = toArr(fallback.data?.meetups ?? fallback.data?.content ?? fallback.data);
  }
}
```

변경 후:
```js
} else if (tabKey === 'meetup') {
  const res = await meetupApi.getHomeMeetups(
    userCoords?.lat ?? null,
    userCoords?.lng ?? null,
    6
  );
  items = toArr(res.data?.meetups ?? res.data?.content ?? res.data);
}
```

#### missing 탭 — getHomeMissing으로 교체

현재:
```js
} else if (tabKey === 'missing') {
  const res = await missingPetApi.list({ page: 0, size: 6, status: 'MISSING' });
  items = toArr(res.data?.boards ?? res.data);
}
```

변경 후:
```js
} else if (tabKey === 'missing') {
  const res = await missingPetApi.getHomeMissing(
    userCoords?.lat ?? null,
    userCoords?.lng ?? null,
    6
  );
  items = toArr(res.data?.boards ?? res.data);
}
```

---

## AC (검증)

```bash
cd /Users/maknkkong/project/Petory/frontend && npm run build
# BUILD SUCCESSFUL
```

런타임:
- 주변서비스 탭: 복합 스코어 순으로 정렬된 서비스 표시
- 모임 탭: 거리+긴급도+여유 스코어 순 모임 표시 (위치 없으면 날짜순)
- 실종신고 탭: 최신성+거리 스코어 순 표시 (위치 없으면 lostDate 최신순)
