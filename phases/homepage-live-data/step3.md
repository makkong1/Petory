# Step 3 — Frontend: 모임 탭 nearby 빈값 시 전체모임 폴백

## 목표
`getNearbyMeetups`가 빈 배열을 반환하면 `GET /api/meetups?status=RECRUITING&size=6`으로 폴백한다.

## 변경 파일
- `frontend/src/api/meetupApi.js` — `getRecruitingMeetups` 메서드 추가
- `frontend/src/components/Home/HomePage.js` — meetup 탭 폴백 로직

---

## 변경 상세

### 1. `meetupApi.js` — 메서드 추가

기존 `getMeetupById` 앞에 삽입:

```js
getRecruitingMeetups: (size = 6) => {
  if (isDemoMode()) return mockResolve(DEMO_MEETUPS.slice(0, size));
  return api.get('', { params: { status: 'RECRUITING', size, page: 0 } });
},
```

### 2. `HomePage.js` — meetup 탭 폴백

`fetchTabData` 내 meetup 블록 변경:

현재:
```js
} else if (tabKey === 'meetup') {
  const lat = userCoords?.lat ?? 37.5665;
  const lng = userCoords?.lng ?? 126.9780;
  const res = await meetupApi.getNearbyMeetups(lat, lng, 50, 6);
  items = toArr(res.data?.content ?? res.data);
}
```

변경 후:
```js
} else if (tabKey === 'meetup') {
  const lat = userCoords?.lat ?? 37.5665;
  const lng = userCoords?.lng ?? 126.9780;
  const res = await meetupApi.getNearbyMeetups(lat, lng, 50, 6);
  items = toArr(res.data?.content ?? res.data);
  if (items.length === 0) {
    const fallback = await meetupApi.getRecruitingMeetups(6);
    items = toArr(fallback.data?.content ?? fallback.data);
  }
}
```

## AC (검증)
```bash
cd frontend && npm run build
# BUILD SUCCESSFUL
```

런타임: 주변 모임 없어도 모집중인 모임이 있으면 홈 화면 모임 탭에 표시됨.
