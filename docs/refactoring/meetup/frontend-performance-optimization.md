# Meetup 프론트엔드 성능 최적화 리팩토링

## 개요
Meetup 도메인의 프론트엔드 코드 분석을 통해 발견된 성능 이슈 및 리팩토링 포인트를 정리합니다.

**분석 대상 파일**:
- `frontend/src/components/Meetup/MeetupPage.js` (2,889 lines)
- `frontend/src/components/Admin/sections/MeetupManagementSection.js` (345 lines)
- `frontend/src/api/meetupApi.js` (59 lines)
- `frontend/src/api/meetupAdminApi.js` (27 lines)

---

## 🔴 Critical (긴급)

### 1. 메모이제이션 누락 - 비용이 큰 함수들

**파일**: `MeetupPage.js`

**문제 위치**:
- Lines 353-362: `calculateDistance()` - 매 렌더마다 재생성
- Lines 529-545: `formatDate()` - 매 렌더마다 재생성
- Lines 949-965: `getCalendarDays()` - 매 렌더마다 재생성

**현재 코드**:
```javascript
const calculateDistance = (lat1, lng1, lat2, lng2) => {
  // Haversine 계산...
};
```

**해결 방안**:
```javascript
const calculateDistance = useCallback((lat1, lng1, lat2, lng2) => {
  // Haversine 계산...
}, []);
```

---

### 2. 대용량 상수 매 렌더마다 재생성

**파일**: `MeetupPage.js` (Lines 549-652)

**문제**: `SIDOS`, `SIDO_CENTERS`, `SIGUNGUS`, `EUPMYEONDONGS` 등 대용량 객체가 컴포넌트 내부에서 매 렌더마다 재생성

**해결 방안**:
```javascript
// 컴포넌트 외부로 이동
const SIDOS = ['서울특별시', '부산광역시', ...];
const SIDO_CENTERS = { ... };

// 또는 useMemo 사용
const SIDOS = useMemo(() => ['서울특별시', ...], []);
```

---

### 3. 디바운싱 누락

**파일**: `MeetupPage.js`

**문제 1 - Lines 366-391**: `handleMapIdle()` - 지도 이동 시 매번 호출
```javascript
// 현재: 디바운스 없음
const handleMapIdle = () => {
  fetchMeetups();
};
```

**해결**:
```javascript
const debouncedFetchMeetups = useMemo(
  () => debounce(() => fetchMeetups(), 300),
  [fetchMeetups]
);

const handleMapIdle = () => {
  debouncedFetchMeetups();
};
```

**문제 2 - Lines 1171-1194**: Geocoding API가 `mapCenter` 변경 시마다 호출

**해결**: 동일하게 debounce 적용 (300-500ms)

---

### 4. 중복 API 호출

**파일**: `MeetupPage.js`

**문제 1 - Lines 440-465 (`handleJoinMeetup`)**:
```javascript
// 현재: 순차 호출
await fetchParticipants(meetupIdx);
await getMeetupById(meetupIdx);
await fetchMeetups();
```

**해결**:
```javascript
// 병렬 호출 + 불필요한 호출 제거
await Promise.all([
  fetchParticipants(meetupIdx),
  getMeetupById(meetupIdx)
]);
// fetchMeetups()는 getMeetupById 결과로 대체 가능하면 제거
```

**문제 2 - Lines 500-513 (`handleMeetupClick`)**:
```javascript
// 현재: 순차 호출
await fetchParticipants(meetup.idx);
await checkParticipation(meetup.idx);
```

**해결**:
```javascript
const [participants, isParticipating] = await Promise.all([
  fetchParticipants(meetup.idx),
  checkParticipation(meetup.idx)
]);
```

---

## 🟠 High Priority

### 5. 과다한 개별 State 변수

**파일**: `MeetupPage.js` (Lines 62-112)

**현재**: 20개 이상의 개별 useState

**해결 - useReducer로 그룹화**:
```javascript
// 폼 상태 그룹
const [formState, formDispatch] = useReducer(formReducer, {
  title: '',
  description: '',
  date: null,
  maxParticipants: 10,
  // ...
});

// UI 상태 그룹
const [uiState, uiDispatch] = useReducer(uiReducer, {
  showCreateModal: false,
  showDetailModal: false,
  showCalendar: false,
  // ...
});

// 위치 상태 그룹
const [locationState, locationDispatch] = useReducer(locationReducer, {
  sido: '',
  sigungu: '',
  mapCenter: { lat: 37.5665, lng: 126.978 },
  // ...
});
```

---

### 6. 클라이언트 사이드 필터링

**파일**: `MeetupPage.js` (Lines 267-350)

**문제**: 전체 데이터 fetch 후 클라이언트에서 필터링

**해결**: 백엔드 API에 필터 파라미터 전달
```javascript
// 현재
const meetups = await getAllMeetups();
const filtered = meetups.filter(m => m.sido === selectedSido);

// 개선
const meetups = await getMeetups({ sido: selectedSido, sigungu: selectedSigungu });
```

---

## 🟡 Medium Priority

### 7. Admin 검색 디바운싱

**파일**: `MeetupManagementSection.js` (Lines 84-90)

**문제**: 키 입력마다 API 호출

**해결**:
```javascript
const [searchTerm, setSearchTerm] = useState('');
const debouncedSearchTerm = useDebounce(searchTerm, 300);

useEffect(() => {
  fetchMeetups({ keyword: debouncedSearchTerm });
}, [debouncedSearchTerm]);
```

---

### 8. 리스트 메모이제이션

**파일**: `MeetupPage.js` (Lines 1307-1315, 1385-1400)

**문제**: `meetups.map()`이 매 렌더마다 새 배열 생성

**해결**:
```javascript
// useMemo로 리스트 캐싱
const meetupList = useMemo(() => 
  meetups.map(meetup => (
    <MeetupItem key={meetup.idx} meetup={meetup} />
  )),
  [meetups]
);

// MeetupItem을 React.memo로 감싸기
const MeetupItem = React.memo(({ meetup }) => {
  // ...
});
```

---

### 9. 로딩 상태 추가

**파일**: `MeetupPage.js` (Lines 419-426, 429-437)

**문제**: `fetchParticipants`, `checkParticipation`에 로딩 인디케이터 없음

**해결**:
```javascript
const [loadingParticipants, setLoadingParticipants] = useState(false);

const fetchParticipants = async (meetupIdx) => {
  setLoadingParticipants(true);
  try {
    const data = await getParticipants(meetupIdx);
    setParticipants(data);
  } finally {
    setLoadingParticipants(false);
  }
};
```

---

## 🟢 Low Priority

### 10. 컴포넌트 분리

**파일**: `MeetupPage.js` (2,889 lines)

**문제**: 단일 컴포넌트가 너무 많은 책임

**해결 - 컴포넌트 분리**:
```
components/Meetup/
├── MeetupPage.js (메인 컨테이너)
├── MeetupMap.js (지도 렌더링)
├── MeetupList.js (목록 표시)
├── MeetupForm.js (생성 폼)
├── MeetupDetailModal.js (상세 보기)
├── RegionControls.js (지역 선택)
└── hooks/
    ├── useMeetups.js
    ├── useLocation.js
    └── useMeetupForm.js
```

---

### 11. Optimistic Update 구현

**파일**: `MeetupManagementSection.js` (Lines 37-45)

**현재**: 삭제 후 전체 refetch

**해결**:
```javascript
const handleDelete = async (meetupIdx) => {
  // Optimistic update
  setMeetups(prev => prev.filter(m => m.idx !== meetupIdx));
  
  try {
    await deleteMeetup(meetupIdx);
  } catch (error) {
    // 실패 시 롤백
    fetchMeetups();
    showError('삭제에 실패했습니다.');
  }
};
```

---

### 12. Error Boundary 추가

**문제**: API 실패 시 적절한 에러 처리 없음

**해결**:
```javascript
class MeetupErrorBoundary extends React.Component {
  state = { hasError: false };
  
  static getDerivedStateFromError(error) {
    return { hasError: true };
  }
  
  render() {
    if (this.state.hasError) {
      return <ErrorFallback onRetry={() => this.setState({ hasError: false })} />;
    }
    return this.props.children;
  }
}
```

---

## 체크리스트

### High Priority
- [ ] `calculateDistance`, `formatDate`, `getCalendarDays` 메모이제이션
- [ ] 대용량 상수 컴포넌트 외부로 이동
- [ ] `handleMapIdle` 디바운스 적용
- [ ] Geocoding API 디바운스 적용
- [ ] API 호출 병렬화 (`Promise.all`)

### Medium Priority
- [ ] 관련 state `useReducer`로 그룹화
- [ ] 검색 디바운싱 적용
- [ ] 리스트 `useMemo` 적용
- [ ] 로딩 상태 추가
- [ ] 클라이언트 필터링 → 백엔드 이동

### Low Priority
- [ ] 컴포넌트 분리
- [ ] Custom hooks 추출
- [ ] Optimistic update 구현
- [ ] Error boundary 추가

---

## 예상 효과

| 항목 | Before | After |
|------|--------|-------|
| 불필요한 리렌더 | 많음 | 최소화 |
| API 호출 수 | 순차 3-4개 | 병렬 1-2개 |
| 지도 이동 시 호출 | 매 이동마다 | 300ms 디바운스 |
| 초기 렌더 시간 | 느림 (상수 재생성) | 빠름 |
| 사용자 경험 | 로딩 피드백 없음 | 로딩 인디케이터 표시 |
