# 홈페이지 리디자인 — 도메인 섹션 세로 배치

**날짜:** 2026-05-16  
**범위:** `frontend/src/components/Home/HomePage.js` 단일 파일

---

## 1. 배경 및 목표

기존 홈은 탭 전환 구조라 첫 화면에 콘텐츠가 빈약하게 보임. 탭을 제거하고 4개 도메인 섹션을 세로로 쌓아 스크롤 한 번에 앱 전체 기능을 훑을 수 있게 한다.

---

## 2. 레이아웃 구조

```
[헤더: 닉네임 인사 + 알림 버튼]

[섹션 1] 🔴 실종신고        ← 감정 hook, 맨 위 배치
  가로 스크롤 카드 × 4

[섹션 2] 📍 주변 서비스
  가로 스크롤 카드 × 4

[섹션 3] 👥 모임
  가로 스크롤 카드 × 4

[섹션 4] 💬 커뮤니티 인기글
  가로 스크롤 카드 × 4

[관리자 섹션] (role=ADMIN/MASTER 일 때만)
```

---

## 3. 섹션별 카드 정보

| 섹션 | 제목 필드 | 부제목 필드 | 색상 |
|------|-----------|-------------|------|
| 실종신고 | `petName \|\| title` | `breed + lostDate` | `#EF4444` |
| 주변 서비스 | `name` | `category + 거리(km)` | `#3B82F6` |
| 모임 | `title` | `currentParticipants/maxParticipants + 모집상태` | `#10B981` |
| 커뮤니티 | `boardTitle \|\| title` | `❤️ likeCount  👁 viewCount` | `#8B5CF6` |

카드 구조: 이미지 영역(이모지 placeholder) + 제목 + 부제목. 가로 스크롤 `overflow-x: auto`.

---

## 4. 데이터 페칭

기존 API 그대로 사용, 탭 기반 lazy fetch → 마운트 시 4개 병렬 fetch로 변경.

```
실종신고  → missingPetApi.getHomeMissing(lat, lng, 6)
주변서비스 → locationServiceApi.searchPlaces({ sort:'score', size:6, lat, lng, radius:10000 })
모임      → meetupApi.getHomeMeetups(lat, lng, 6)
커뮤니티  → boardApi.getPopularBoards('WEEKLY')
```

위치(lat/lng)는 `navigator.geolocation` — 기존 로직 유지. 실패 시 좌표 없이 요청.

---

## 5. 인터랙션

- **카드 클릭** → `setActiveTab()` 호출로 해당 탭 이동 (상세 모달 없음)
  - 실종신고 → `'missing-pets'`
  - 주변 서비스 → `'unified-map'`
  - 모임 → `'unified-map'`
  - 커뮤니티 → `'community'`
- **"전체보기 →"** → 동일하게 해당 탭 이동
- 홈의 탭 UI(`TabsWrap`, `TabBtn`) 완전 제거

---

## 6. 로딩 상태

섹션별 독립 스켈레톤. 각 섹션이 개별적으로 shimmer 애니메이션 → 데이터 들어오는 순서대로 자연스럽게 교체.

---

## 7. 컴포넌트 분리

`HomePage.js` 안에 `SectionRow` 컴포넌트 하나로 4개 섹션 재사용:

```jsx
<SectionRow
  title="실종신고"
  emoji="🔴"
  color="#EF4444"
  items={missingItems}
  loading={loadingMissing}
  getLabel={(item) => ({ title: item.petName, sub: item.breed })}
  onViewAll={() => setActiveTab('missing-pets')}
/>
```

---

## 8. 변경 파일

- `frontend/src/components/Home/HomePage.js` — 전면 교체 (단일 파일)

외부 의존성 추가 없음. 기존 API 모듈 그대로 사용.
