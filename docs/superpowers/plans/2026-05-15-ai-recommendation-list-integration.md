# AI 추천 시설 목록 통합 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** AI 추천 시설(pet-data-api)과 기존 Petory DB 시설을 하나의 목록에 표시하되, AI 항목은 왼쪽 컬러바 + `[AI]` 배지로 구분한다.

**Architecture:** `RecommendCard`에서 내부 시설 목록 렌더링을 제거하고, AI 시설 데이터와 `request_id`를 `onFacilitiesLoaded` 콜백으로 부모에 전달한다. `UnifiedPetMapPage`는 `aiRecommendFacilities`를 Petory `items`와 병합한 `displayItems`를 계산하고 단일 목록에 렌더링한다. M3 click 콜백은 부모가 `recommendApi`를 직접 호출해 처리한다.

**Tech Stack:** React 19, styled-components, Axios (`recommendApi`)

---

### Task 1: RecommendCard.js — 내부 시설 목록 제거 + onFacilitiesLoaded 시그니처 변경

**Files:**
- Modify: `frontend/src/components/Recommendation/RecommendCard.js`

- [ ] **Step 1: styled-components 11개 + helper function 3개 삭제**

  `RecommendCard.js`에서 아래 블록 전체를 삭제한다 (line 73~189).

  삭제 대상:
  - `FacilityList` (line 73–77)
  - `FacilityItem` (line 79–91)
  - `FacilityHeader` (line 93–98)
  - `FacilityName` (line 100–103)
  - `FacilityScore` (line 105–110)
  - `FacilityMeta` (line 113–121)
  - `MetaDot` (line 123–125)
  - `SourceBadge` (line 127–133)
  - `MentionInline` (line 135–138)
  - `ReasonRow` (line 140–145)
  - `ReasonChip` (line 149–159)
  - `reasonLabel` function (line 162–175)
  - `isTrendReason` function (line 177–179)
  - `sourceLabel` function (line 181–189)

  ```js
  // 삭제: line 73 ~ 189 전체
  // FacilityList ~ sourceLabel 함수 끝까지
  ```

- [ ] **Step 2: handleFacilityClick 삭제**

  `RecommendCard.js` 내 `handleFacilityClick` 함수(line 225~233)를 삭제한다. 클릭 이벤트는 이제 부모가 처리한다.

  ```js
  // 삭제:
  const handleFacilityClick = (f) => {
    const requestId = data?.request_id;
    sendRecommendEvents(requestId, [{
      facility_id: f.id ?? null,
      source_id: f.source_id ?? null,
      event: 'click',
      occurred_at: new Date().toISOString(),
    }]);
  };
  ```

- [ ] **Step 3: onFacilitiesLoaded 호출 시그니처 변경 (성공 경로)**

  `useEffect` 내 `onFacilitiesLoaded` 호출부(기존 line 249~254)를 아래와 같이 변경한다.

  변경 전:
  ```js
  if (onFacilitiesLoaded) {
    const withCoords = (main?.facilities ?? []).filter(
      (f) => f.lat != null && f.lng != null
    );
    onFacilitiesLoaded(withCoords);
  }
  ```

  변경 후:
  ```js
  if (onFacilitiesLoaded) {
    const withCoords = (main?.facilities ?? []).filter(
      (f) => f.lat != null && f.lng != null
    );
    onFacilitiesLoaded({ facilities: withCoords, requestId: main?.request_id ?? null });
  }
  ```

- [ ] **Step 4: onFacilitiesLoaded 호출 시그니처 변경 (오류 경로)**

  `.catch()` 핸들러 내 `onFacilitiesLoaded?.([])` 를 아래로 변경한다.

  변경 전:
  ```js
  .catch(() => {
    if (cancelled) return;
    setData(null);
    onFacilitiesLoaded?.([]);
  })
  ```

  변경 후:
  ```js
  .catch(() => {
    if (cancelled) return;
    setData(null);
    onFacilitiesLoaded?.({ facilities: [], requestId: null });
  })
  ```

- [ ] **Step 5: 주변 시설 JSX 블록 삭제**

  `return` 블록 내 `data.facilities?.length > 0` 조건부 렌더링 전체를 삭제한다 (line 334~389).

  삭제 대상:
  ```jsx
  {data.facilities?.length > 0 && (
    <>
      <Title style={{ marginTop: 14 }}>주변 시설</Title>
      <FacilityList>
        {data.facilities.map((f) => {
          // ... 전체
        })}
      </FacilityList>
    </>
  )}
  ```

- [ ] **Step 6: ESLint 통과 확인**

  ```bash
  cd frontend && npx eslint src/components/Recommendation/RecommendCard.js
  ```

  Expected: 에러 0개 (경고만 허용)

- [ ] **Step 7: 커밋**

  ```bash
  git add frontend/src/components/Recommendation/RecommendCard.js
  git commit -m "refactor(recommend): remove internal facility list from RecommendCard"
  ```

---

### Task 2: UnifiedPetMapPage.js — 상태/핸들러 추가 + displayItems 계산

**Files:**
- Modify: `frontend/src/components/UnifiedMap/UnifiedPetMapPage.js`

- [ ] **Step 1: recommendApi import 추가**

  파일 상단 import 목록에 추가한다 (line 16 아래).

  ```js
  import { recommendApi } from '../../api/recommendApi';
  ```

- [ ] **Step 2: aiRequestId 상태 추가**

  `aiRecommendFacilities` 상태 선언(line 82) 바로 아래에 추가한다.

  ```js
  const [aiRecommendFacilities, setAiRecommendFacilities] = useState([]);
  const [aiRequestId, setAiRequestId] = useState(null);  // 추가
  ```

- [ ] **Step 3: handleAiRecommendLoad 콜백 추가**

  `handleLocationResultClick` 정의(line 321) 바로 위에 추가한다.

  ```js
  const handleAiRecommendLoad = useCallback(({ facilities, requestId }) => {
    setAiRecommendFacilities(facilities);
    setAiRequestId(requestId);
  }, []);
  ```

- [ ] **Step 4: handleAiItemClick 콜백 추가**

  `handleAiRecommendLoad` 바로 아래에 추가한다.

  ```js
  const handleAiItemClick = useCallback((rawFacility, requestId) => {
    if (!requestId) return;
    const event = {
      facility_id: rawFacility.id ?? null,
      source_id: rawFacility.source_id ?? null,
      event: 'click',
      occurred_at: new Date().toISOString(),
    };
    if (event.facility_id == null && event.source_id == null) return;
    recommendApi.sendEvents({ requestId, events: [event] }).catch(() => {});
  }, []);
  ```

- [ ] **Step 5: displayItems 계산식 추가**

  `handleAiItemClick` 바로 아래에 추가한다. 렌더링에만 사용되는 파생 값이다.

  ```js
  const aiDisplayItems = aiRecommendFacilities.map((f, i) => ({
    id: `ai-${i}`,
    type: 'ai_recommend',
    latitude: f.lat,
    longitude: f.lng,
    name: f.name,
    title: f.name,
    subtitle: f.address ?? '',
    distanceM: f.distance_m,
    isAiRecommend: true,
    rawAiFacility: f,
  }));
  const displayItems = [...aiDisplayItems, ...items];
  ```

- [ ] **Step 6: handleTabChange에서 aiRequestId 초기화 추가**

  `handleTabChange` 내 `setAiRecommendFacilities([]);` 줄(line 235) 바로 아래에 추가한다.

  ```js
  setAiRecommendFacilities([]);
  setAiRequestId(null);  // 추가
  ```

- [ ] **Step 7: onCategoryChange, onSortChange에서 aiRequestId 초기화 추가**

  `LocationControls`의 `onCategoryChange`, `onSortChange` 핸들러 내 `setAiRecommendFacilities([]);` 아래 각각 추가한다.

  ```js
  onCategoryChange={(cat) => {
    setLocationCategory(cat);
    setAiRecommendFacilities([]);
    setAiRequestId(null);  // 추가
    cacheRef.current = {};
    commitLocationSearch(mapViewportCenter, cat ? 'category' : 'user-triggered');
  }}
  onSortChange={(sort) => {
    setLocationSort(sort);
    setAiRecommendFacilities([]);
    setAiRequestId(null);  // 추가
    cacheRef.current = {};
    commitLocationSearch(mapViewportCenter, 'user-triggered');
  }}
  ```

- [ ] **Step 8: RecommendCard의 onFacilitiesLoaded prop 변경**

  ```jsx
  // 변경 전
  <RecommendCard
    lat={userLocation.lat}
    lng={userLocation.lng}
    context={CATEGORY_TO_CONTEXT[locationCategory]}
    onFacilitiesLoaded={setAiRecommendFacilities}
  />

  // 변경 후
  <RecommendCard
    lat={userLocation.lat}
    lng={userLocation.lng}
    context={CATEGORY_TO_CONTEXT[locationCategory]}
    onFacilitiesLoaded={handleAiRecommendLoad}
  />
  ```

- [ ] **Step 9: ESLint 통과 확인**

  ```bash
  cd frontend && npx eslint src/components/UnifiedMap/UnifiedPetMapPage.js
  ```

  Expected: 에러 0개

- [ ] **Step 10: 커밋**

  ```bash
  git add frontend/src/components/UnifiedMap/UnifiedPetMapPage.js
  git commit -m "feat(unified-map): add displayItems merge and AI click event handler"
  ```

---

### Task 3: UnifiedPetMapPage.js — 렌더링 업데이트 (AI 배지 + 병합 목록)

**Files:**
- Modify: `frontend/src/components/UnifiedMap/UnifiedPetMapPage.js`

- [ ] **Step 1: AiItemBadge styled-component 추가**

  파일 하단 styled-components 영역의 `ResultRankBadge` 정의 바로 아래에 추가한다.

  ```js
  const AiItemBadge = styled.span`
    display: inline-flex;
    align-items: center;
    padding: 2px 6px;
    border-radius: 4px;
    background: ${props => props.theme.colors.primaryLight || '#eef2ff'};
    color: ${props => props.theme.colors.primary};
    font-size: 10px;
    font-weight: 700;
  `;
  ```

- [ ] **Step 2: ResultCard에 $isAiRecommend 왼쪽 컬러바 스타일 추가**

  기존 `ResultCard` styled-component에 `$isAiRecommend` 조건을 추가한다.

  ```js
  const ResultCard = styled.button`
    width: 100%;
    text-align: left;
    border: 1px solid ${props => props.$selected
      ? props.theme.colors.domain.location
      : props.theme.colors.border};
    ${props => props.$isAiRecommend && `
      border-left: 3px solid ${props.theme.colors.primary};
    `}
    background: ${props => props.$selected
      ? props.theme.colors.domain.location + '1A'
      : props.theme.colors.background};
    border-radius: 18px;
    padding: 14px 14px 13px;
    cursor: pointer;
    transition: border-color 0.15s ease, transform 0.15s ease, box-shadow 0.15s ease, background 0.15s ease;

    &:hover {
      border-color: ${props => props.theme.colors.domain.location};
      transform: translateY(-1px);
      box-shadow: ${props => props.theme.shadows.lg};
    }
  `;
  ```

- [ ] **Step 3: 데스크톱 LeftPanelResults — displayItems 사용 + AI 배지 추가**

  `LeftPanelResults` 블록(line 459~511)에서 `items` 참조를 `displayItems`로 교체하고, `ResultCard` 렌더링에 AI 배지와 클릭 핸들러를 추가한다.

  변경 전:
  ```jsx
  {!loading && !error && items.length === 0 && mapViewportCenter && (
    <PanelStatusMsg>반경 {radius}km 내 결과가 없습니다.</PanelStatusMsg>
  )}
  {!loading && !error && items.length > 0 && (
    <>
      <PanelResultHeader>
        <div>
          <PanelResultTitle>주변 시설</PanelResultTitle>
          <PanelResultSubtitle>
            {searchMode === 'initial' ? '초기 검색' : '현재 검색 기준'} · 반경 {radius}km · {SORT_LABELS[locationSort]}
          </PanelResultSubtitle>
        </div>
        <PanelResultCount>{items.length}개</PanelResultCount>
      </PanelResultHeader>
      <PanelResultList>
        {items.map((item, index) => {
          const isSelected = selectedItem?.id === item.id;
          const isRecommended = recommendedMap?.has(item.idx);
          return (
            <ResultCard
              key={item.id}
              type="button"
              $selected={isSelected}
              onClick={() => handleLocationResultClick(item)}
              onMouseEnter={() => setHoveredLocationItem(item)}
              onMouseLeave={() => setHoveredLocationItem(current =>
                current?.id === item.id ? null : current
              )}
            >
              <ResultCardTop>
                <ResultCardTitle>
                  {isRecommended && (
                    <ResultRankBadge>TOP {recommendedMap.get(item.idx)}</ResultRankBadge>
                  )}
                  {item.title || item.name || `시설 ${index + 1}`}
                </ResultCardTitle>
                {item.raw?.distance != null && (
                  <ResultDistance>{Math.round(item.raw.distance)}m</ResultDistance>
                )}
              </ResultCardTop>
              <ResultCardSubtitle>
                {item.subtitle || item.raw?.address || '주소 정보 없음'}
              </ResultCardSubtitle>
            </ResultCard>
          );
        })}
      </PanelResultList>
    </>
  )}
  ```

  변경 후:
  ```jsx
  {!loading && !error && displayItems.length === 0 && mapViewportCenter && (
    <PanelStatusMsg>반경 {radius}km 내 결과가 없습니다.</PanelStatusMsg>
  )}
  {!loading && !error && displayItems.length > 0 && (
    <>
      <PanelResultHeader>
        <div>
          <PanelResultTitle>주변 시설</PanelResultTitle>
          <PanelResultSubtitle>
            {searchMode === 'initial' ? '초기 검색' : '현재 검색 기준'} · 반경 {radius}km · {SORT_LABELS[locationSort]}
          </PanelResultSubtitle>
        </div>
        <PanelResultCount>{displayItems.length}개</PanelResultCount>
      </PanelResultHeader>
      <PanelResultList>
        {displayItems.map((item, index) => {
          const isSelected = selectedItem?.id === item.id;
          const isRecommended = recommendedMap?.has(item.idx);
          return (
            <ResultCard
              key={item.id}
              type="button"
              $selected={isSelected}
              $isAiRecommend={item.isAiRecommend}
              onClick={() => {
                handleLocationResultClick(item);
                if (item.isAiRecommend) handleAiItemClick(item.rawAiFacility, aiRequestId);
              }}
              onMouseEnter={() => setHoveredLocationItem(item)}
              onMouseLeave={() => setHoveredLocationItem(current =>
                current?.id === item.id ? null : current
              )}
            >
              <ResultCardTop>
                <ResultCardTitle>
                  {item.isAiRecommend && <AiItemBadge>AI</AiItemBadge>}
                  {isRecommended && (
                    <ResultRankBadge>TOP {recommendedMap.get(item.idx)}</ResultRankBadge>
                  )}
                  {item.title || item.name || `시설 ${index + 1}`}
                </ResultCardTitle>
                {item.distanceM != null
                  ? <ResultDistance>{item.distanceM}m</ResultDistance>
                  : item.raw?.distance != null && <ResultDistance>{Math.round(item.raw.distance)}m</ResultDistance>
                }
              </ResultCardTop>
              <ResultCardSubtitle>
                {item.subtitle || item.raw?.address || '주소 정보 없음'}
              </ResultCardSubtitle>
            </ResultCard>
          );
        })}
      </PanelResultList>
    </>
  )}
  ```

- [ ] **Step 4: 모바일 renderLocationResults — displayItems 사용 + AI 배지 추가**

  `renderLocationResults` 함수(line 329~376)에서도 동일하게 교체한다.

  변경 전 (line 330):
  ```js
  if (activeLayer !== 'location' || loading || error || items.length === 0) {
  ```
  변경 후:
  ```js
  if (activeLayer !== 'location' || loading || error || displayItems.length === 0) {
  ```

  변경 전 (line 344, 347):
  ```jsx
  <ResultSheetMeta>{items.length}개</ResultSheetMeta>
  ...
  {items.map((item, index) => {
  ```
  변경 후:
  ```jsx
  <ResultSheetMeta>{displayItems.length}개</ResultSheetMeta>
  ...
  {displayItems.map((item, index) => {
  ```

  그리고 모바일 ResultCard에도 동일한 AI 배지 + 클릭 핸들러 추가 (Step 3의 변경 후 JSX 패턴 그대로 적용).

- [ ] **Step 5: ESLint 통과 확인**

  ```bash
  cd frontend && npx eslint src/components/UnifiedMap/UnifiedPetMapPage.js
  ```

  Expected: 에러 0개

- [ ] **Step 6: 빌드 확인**

  ```bash
  cd frontend && npm run build 2>&1 | tail -20
  ```

  Expected: `Compiled successfully.` 또는 `webpack compiled with 0 errors`

- [ ] **Step 7: 브라우저 동작 확인**

  아래 시나리오를 순서대로 확인한다.

  1. `npm start` 실행 후 지도 페이지 → "미용" 카테고리 선택
  2. 좌측 패널에 AI 항목(파란 왼쪽 바 + `AI` 배지)과 Petory 항목이 같은 목록에 표시되는지 확인
  3. AI 항목에 hover → 배경 변화 확인
  4. AI 항목 클릭 → 지도 해당 위치 포커스 + 브라우저 Network 탭에서 `POST /api/recommend/events` (event:"click") 발생 확인
  5. RecommendCard 내부에 "주변 시설" 섹션이 사라지고 LLM 카피만 표시되는지 확인
  6. 카테고리 변경 시 AI 항목이 초기화되고 새 카테고리 AI 항목으로 교체되는지 확인

- [ ] **Step 8: 커밋**

  ```bash
  git add frontend/src/components/UnifiedMap/UnifiedPetMapPage.js
  git commit -m "feat(unified-map): merge AI facilities into main list with badge distinction"
  ```
