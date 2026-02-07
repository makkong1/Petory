# Meetup 도메인 리팩토링 요약

## 개요
Meetup 도메인의 백엔드(19개 파일)와 프론트엔드(4개 파일)를 분석하여 도출한 리팩토링 포인트입니다.

---

## 분석 대상 파일

### Backend (19개)
| 카테고리 | 파일명 |
|---------|--------|
| Entity | `Meetup.java`, `MeetupParticipants.java`, `MeetupParticipantsId.java`, `MeetupStatus.java` |
| Repository | `MeetupRepository.java`, `MeetupParticipantsRepository.java`, `JpaMeetupAdapter.java`, `JpaMeetupParticipantsAdapter.java`, `SpringDataJpaMeetupRepository.java`, `SpringDataJpaMeetupParticipantsRepository.java` |
| Service | `MeetupService.java`, `MeetupChatRoomEventListener.java` |
| Controller | `MeetupController.java`, `AdminMeetupController.java` |
| Converter | `MeetupConverter.java`, `MeetupParticipantsConverter.java` |
| DTO | `MeetupDTO.java`, `MeetupParticipantsDTO.java` |
| Event | `MeetupCreatedEvent.java` |

### Frontend (4개)
| 파일명 | 라인 수 |
|--------|---------|
| `MeetupPage.js` | 2,889 |
| `MeetupManagementSection.js` | 345 |
| `meetupApi.js` | 59 |
| `meetupAdminApi.js` | 27 |

---

## 우선순위별 요약

### 🔴 Critical (긴급) - 6개

| # | 위치 | 이슈 | 해결 방안 |
|---|------|------|-----------|
| 1 | BE - `MeetupService.java` | `getNearbyMeetups()` 인메모리 필터링 | 기존 DB 쿼리 `findNearbyMeetups()` 활용 |
| 2 | BE - `SpringDataJpaMeetupParticipantsRepository.java` | N+1 쿼리 위험 | JOIN FETCH 추가 |
| 3 | BE - `AdminMeetupController.java` | 인메모리 필터링 | DB 쿼리로 필터링 이동 |
| 4 | FE - `MeetupPage.js` | 비용 큰 함수 메모이제이션 누락 | `useCallback` 적용 |
| 5 | FE - `MeetupPage.js` | 대용량 상수 매 렌더 재생성 | 컴포넌트 외부로 이동 |
| 6 | FE - `MeetupPage.js` | 디바운싱 누락 | `debounce` 적용 |

### 🟠 High Priority - 4개

| # | 위치 | 이슈 | 해결 방안 |
|---|------|------|-----------|
| 1 | BE - `SpringDataJpaMeetupRepository.java` | 비효율적 서브쿼리 | JOIN + GROUP BY + HAVING |
| 2 | BE - `MeetupParticipantsConverter.java` | Converter N+1 위험 | 호출 쿼리에 JOIN FETCH 확인 |
| 3 | FE - `MeetupPage.js` | 과다한 개별 state | `useReducer`로 그룹화 |
| 4 | FE - `MeetupPage.js` | 중복 API 호출 | `Promise.all`로 병렬화 |

### 🟡 Medium Priority - 8개

| # | 위치 | 이슈 |
|---|------|------|
| 1 | BE | 중복 DB 쿼리 |
| 2 | BE | 비효율적 Stream 연산 |
| 3 | BE | 누락된 DB 인덱스 |
| 4 | BE | 중복 성능 측정 코드 |
| 5 | BE | 캐싱 누락 |
| 6 | FE | Admin 검색 디바운싱 |
| 7 | FE | 리스트 메모이제이션 |
| 8 | FE | 로딩 상태 누락 |

### 🟢 Low Priority - 6개

| # | 위치 | 이슈 |
|---|------|------|
| 1 | BE | LIKE 쿼리 최적화 |
| 2 | BE | 불필요한 save 연산 |
| 3 | FE | 컴포넌트 분리 (2,889 lines) |
| 4 | FE | Custom hooks 추출 |
| 5 | FE | Optimistic Update |
| 6 | FE | Error Boundary |

---

## 예상 효과

### Backend
| 지표 | Before | After |
|------|--------|-------|
| 메모리 사용 | O(n) 전체 로드 | O(1) 필요한 것만 |
| 쿼리 수 | N+1 다수 발생 | 최적화된 단일 쿼리 |
| 응답 시간 | ~500ms | ~50ms |

### Frontend
| 지표 | Before | After |
|------|--------|-------|
| 불필요한 리렌더 | 많음 | 최소화 |
| API 호출 패턴 | 순차 3-4개 | 병렬 1-2개 |
| 지도 이동 시 호출 | 매 이동 | 300ms 디바운스 |
| 초기 렌더 | 느림 | 빠름 |

---

## 관련 문서

- [Backend 성능 최적화 상세](./backend-performance-optimization.md)
- [Frontend 성능 최적화 상세](./frontend-performance-optimization.md)
- [기존 DTO Record 리팩토링](../recordType/meetup/dto-record-refactoring.md)

---

## 진행 상태

- [ ] Critical 이슈 해결
- [ ] High Priority 이슈 해결
- [ ] Medium Priority 이슈 해결
- [ ] Low Priority 이슈 해결
- [ ] 성능 테스트 및 검증
