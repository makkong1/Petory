# 탐색탭 검색·필터 레이아웃 개선 + AI 추천 배너 재배치

**날짜:** 2026-05-21  
**상태:** 승인됨  
**대상 파일:** `frontend/src/components/UnifiedMap/`

---

## 문제

모바일에서 `ControlsOverlay`가 지도 위에 세로로 4단 쌓여 높이 150px+를 차지하고, 필터 패널을 열면 `LocationResultSheet`(결과 목록)의 대부분을 가린다. AI 추천 멘트(`RecommendCard`)는 데스크톱 `LeftPanel` 하단에만 표시되어 모바일에서 아예 노출되지 않고, 데스크톱에서도 결과 리스트 높이를 줄인다.

---

## 설계

### 1. `LocationControls.js` — 2-row 레이아웃으로 재설계

#### 변경 전 구조 (세로 4단, ~150px+)
```
[RadiusFilter row — OverlayRow에서 별도 렌더]
[🔍 SearchPill]
[SummaryChips][FilterToggle]
[FilterPanel — 카테고리/반경/정렬 세로 패널]
```

#### 변경 후 구조 (2단 고정, ~80px)
```
Row 1: [🔍 검색_______________] [거리순▾] [필터]
Row 2: ←[전체][동물병원][미용][카페][펜션][식당]→  (overflow-x: scroll)
```

**세부 규격:**
- **Row 1** — `flex` 한 줄. 검색 input(flex:1) + 정렬 사이클 버튼 + 필터 버튼.
- **Row 2** — `overflow-x: auto; white-space: nowrap` 카테고리 칩 가로 스크롤. 활성 칩은 강조색.
- **정렬 버튼** — `거리순 ▾` 형태. 클릭마다 `거리순 → 평점순 → 리뷰순` 순환. `SortSelect` 드롭다운 제거.
- **필터 버튼** — 클릭 시 반경(1·3·5·10km) 선택 패널만 표시. 카테고리·정렬은 이미 Row1·2에 노출되므로 필터 패널에서 제외.
- **제거** — `SummaryRow`(칩 요약 줄), 기존 `FilterPanel` 내 카테고리·정렬 섹션.
- **`hasPendingAreaChange`** — `SearchHint` 및 `SearchAreaButton`은 유지. 위치는 Row1 우측 버튼 자리에 인라인으로.

### 2. `UnifiedPetMapPage.js` — 모바일 오버레이 정리

- `OverlayRow > RadiusFilter` 제거. 반경 컨트롤은 `LocationControls` 필터 패널로 통합.
- `renderLayerControls(false)` → `renderLayerControls(true)` — 모바일에서도 `showRadius={true}` 전달.
- **AI 배너 상태 추가** — `const [aiDismissed, setAiDismissed] = useState(false)`. 카테고리 변경 시 `setAiDismissed(false)` 리셋.
- **모바일 배너 렌더** — `LocationResultSheet` 내부, 결과 카드 위에 `AiInlineBanner` 조건부 렌더.
- **데스크톱 배너 렌더** — `LeftPanelResults` 상단, 결과 헤더 아래에 동일한 `AiInlineBanner` 렌더.

### 3. AI 추천 배너 — 슬림 인라인 배너

**표시 조건:** `CATEGORY_TO_CONTEXT[locationCategory]` 존재 && `aiDismissed === false` && copy 또는 recommendation 텍스트 존재

**배너 구조:**
```
[AI 뱃지] [추천 멘트 텍스트 1~2줄] [✕]
```
- 배경: `primary` 색 투명도 8% + `primary` 색 20% 테두리
- ✕ 클릭 → `setAiDismissed(true)` (카테고리 바꾸면 다시 표시)
- `RecommendCard.js`에 `variant="banner"` prop 추가 — `banner` 모드에서는 기존 큰 카드 대신 슬림 배너 JSX 반환. 기존 카드 모드(`variant` 미전달 또는 `"card"`)는 그대로 유지.
- 트렌드 키워드 태그는 배너 모드에서 멘트 아래 소형으로 표시 (있을 경우).

---

## 영향 범위

| 파일 | 변경 유형 |
|------|---------|
| `controls/LocationControls.js` | 전면 재작성 (레이아웃·스타일) |
| `UnifiedPetMapPage.js` | OverlayRow 제거, AI 배너 상태·렌더 추가 |
| `Recommendation/RecommendCard.js` | `variant="banner"` prop 추가 |

변경 없는 파일: `MeetupControls.js`, `CareControls.js`, `LocationLayer.js` 등 나머지 UnifiedMap 컴포넌트.

---

## 비기능 요구사항

- 데스크톱(`min-width: 769px`) / 모바일(`max-width: 768px`) 양쪽에서 동작.
- 기존 `onSearch`, `onCategoryChange`, `onSortChange`, `onRadiusChange`, `onSearchThisArea` 콜백 시그니처 변경 없음.
- 카테고리 가로 스크롤 시 스크롤바 숨김 (`::-webkit-scrollbar: none`).
