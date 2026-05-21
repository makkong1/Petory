# 탐색탭 검색·필터 레이아웃 개선 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 모바일 탐색탭의 검색·필터 컨트롤을 2-row 가로 레이아웃으로 압축하고, AI 추천 멘트를 결과 리스트 상단 인라인 배너로 재배치한다.

**Architecture:** `LocationControls.js`를 2-row(검색+정렬+필터 / 카테고리 가로 스크롤)로 전면 재작성하고, `RecommendCard.js`에 `variant="banner"` prop을 추가해 슬림 배너 모드를 지원한다. `UnifiedPetMapPage.js`에서 모바일 `OverlayRow+RadiusFilter`를 제거하고 AI 배너를 데스크톱·모바일 결과 영역 상단에 각각 렌더링한다.

**Tech Stack:** React 19, styled-components, @testing-library/react

---

## 파일 구조

| 파일 | 변경 유형 |
|------|---------|
| `frontend/src/components/UnifiedMap/controls/LocationControls.js` | 전면 재작성 |
| `frontend/src/components/Recommendation/RecommendCard.js` | `variant="banner"` + `onDismiss` prop 추가 |
| `frontend/src/components/UnifiedMap/UnifiedPetMapPage.js` | OverlayRow 제거, aiDismissed 상태, 배너 렌더 위치 변경 |
| `frontend/src/components/UnifiedMap/controls/LocationControls.test.js` | 신규 테스트 |
| `frontend/src/components/Recommendation/RecommendCard.test.js` | 신규 테스트 |

---

## Task 1: LocationControls.js — 테스트 작성

**Files:**
- Create: `frontend/src/components/UnifiedMap/controls/LocationControls.test.js`

- [ ] **Step 1: 테스트 파일 생성**

```js
// frontend/src/components/UnifiedMap/controls/LocationControls.test.js
import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { ThemeProvider } from 'styled-components';
import { lightTheme } from '../../../styles/theme';
import LocationControls from './LocationControls';

const wrap = (props) =>
  render(
    <ThemeProvider theme={lightTheme}>
      <LocationControls
        keyword=""
        category=""
        sort="distance"
        hasPendingAreaChange={false}
        radius={5}
        onSearch={jest.fn()}
        onCategoryChange={jest.fn()}
        onSortChange={jest.fn()}
        onSearchThisArea={jest.fn()}
        onRadiusChange={jest.fn()}
        {...props}
      />
    </ThemeProvider>
  );

test('카테고리 칩 전체가 가로 스크롤 행에 렌더된다', () => {
  wrap({});
  expect(screen.getByText('전체')).toBeInTheDocument();
  expect(screen.getByText('동물병원')).toBeInTheDocument();
  expect(screen.getByText('미용')).toBeInTheDocument();
});

test('카테고리 칩 클릭 시 onCategoryChange가 해당 value로 호출된다', () => {
  const onCategoryChange = jest.fn();
  wrap({ onCategoryChange });
  fireEvent.click(screen.getByText('동물병원'));
  expect(onCategoryChange).toHaveBeenCalledWith('동물병원');
});

test('정렬 버튼 클릭 시 거리순→평점순 순환한다', () => {
  const onSortChange = jest.fn();
  wrap({ sort: 'distance', onSortChange });
  fireEvent.click(screen.getByLabelText('정렬 변경'));
  expect(onSortChange).toHaveBeenCalledWith('rating');
});

test('정렬 버튼 클릭 시 리뷰순→거리순 순환한다', () => {
  const onSortChange = jest.fn();
  wrap({ sort: 'reviews', onSortChange });
  fireEvent.click(screen.getByLabelText('정렬 변경'));
  expect(onSortChange).toHaveBeenCalledWith('distance');
});

test('필터 버튼 클릭 시 반경 패널이 표시된다', () => {
  wrap({});
  expect(screen.queryByText('5km')).not.toBeInTheDocument(); // 반경 패널 숨김
  fireEvent.click(screen.getByLabelText('반경 필터'));
  expect(screen.getByText('5km')).toBeInTheDocument(); // 반경 패널 노출
});

test('반경 패널에는 반경 옵션만 있다 (카테고리/정렬 없음)', () => {
  wrap({});
  fireEvent.click(screen.getByLabelText('반경 필터'));
  expect(screen.getByText('1km')).toBeInTheDocument();
  expect(screen.getByText('10km')).toBeInTheDocument();
  // 카테고리/정렬 섹션 헤더 없음
  expect(screen.queryByText('카테고리')).not.toBeInTheDocument();
  expect(screen.queryByText('정렬')).not.toBeInTheDocument();
});

test('hasPendingAreaChange=true면 "검색" 대신 "이 지역" 버튼이 표시된다', () => {
  wrap({ hasPendingAreaChange: true });
  expect(screen.getByText('이 지역')).toBeInTheDocument();
  expect(screen.queryByText('검색')).not.toBeInTheDocument();
});

test('"이 지역" 클릭 시 onSearchThisArea가 호출된다', () => {
  const onSearchThisArea = jest.fn();
  wrap({ hasPendingAreaChange: true, onSearchThisArea });
  fireEvent.click(screen.getByText('이 지역'));
  expect(onSearchThisArea).toHaveBeenCalled();
});
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
cd frontend && npm test -- --watchAll=false --testPathPattern=LocationControls.test
```

Expected: 여러 테스트 FAIL (LocationControls가 아직 변경 전이므로)

---

## Task 2: LocationControls.js — 구현

**Files:**
- Modify: `frontend/src/components/UnifiedMap/controls/LocationControls.js`

- [ ] **Step 3: LocationControls.js 전면 재작성**

파일 전체를 아래 내용으로 교체한다:

```jsx
// frontend/src/components/UnifiedMap/controls/LocationControls.js
import React, { useEffect, useState } from 'react';
import styled from 'styled-components';

const KEYWORD_CATEGORIES = [
  { value: '', label: '전체' },
  { value: '동물병원', label: '동물병원' },
  { value: '동물약국', label: '동물약국' },
  { value: '미용', label: '미용' },
  { value: '카페', label: '카페' },
  { value: '펜션', label: '펜션' },
  { value: '식당', label: '식당' },
  { value: '위탁관리', label: '위탁관리' },
  { value: '반려동물용품', label: '용품' },
  { value: '호텔', label: '호텔' },
];

const SORT_OPTIONS = ['distance', 'rating', 'reviews'];
const SORT_LABELS = { distance: '거리순', rating: '평점순', reviews: '리뷰순' };
const RADIUS_OPTIONS = [1, 3, 5, 10];

const LocationControls = ({
  keyword,
  category,
  sort = 'distance',
  hasPendingAreaChange = false,
  radius,
  onSearch,
  onCategoryChange,
  onSortChange,
  onSearchThisArea,
  onRadiusChange,
}) => {
  const [inputValue, setInputValue] = useState(keyword || '');
  const [isFilterOpen, setIsFilterOpen] = useState(false);

  useEffect(() => {
    setInputValue(keyword || '');
  }, [keyword]);

  const handleSubmit = (e) => {
    e.preventDefault();
    onSearch(inputValue.trim());
  };

  const handleSortCycle = () => {
    const idx = SORT_OPTIONS.indexOf(sort);
    onSortChange?.(SORT_OPTIONS[(idx + 1) % SORT_OPTIONS.length]);
  };

  return (
    <Wrapper>
      {/* Row 1: 검색 + 정렬 사이클 버튼 + 필터 버튼 */}
      <TopRow>
        <SearchPill onSubmit={handleSubmit}>
          <SearchIcon aria-hidden="true">🔍</SearchIcon>
          <SearchInput
            value={inputValue}
            onChange={e => setInputValue(e.target.value)}
            placeholder="시설명, 주소 검색..."
            aria-label="시설 검색"
          />
          {hasPendingAreaChange ? (
            <AreaBtn type="button" onClick={onSearchThisArea}>이 지역</AreaBtn>
          ) : (
            <SearchBtn type="submit">검색</SearchBtn>
          )}
        </SearchPill>
        <SortCycleBtn type="button" onClick={handleSortCycle} aria-label="정렬 변경">
          {SORT_LABELS[sort]} ▾
        </SortCycleBtn>
        <FilterBtn
          type="button"
          $active={isFilterOpen}
          onClick={() => setIsFilterOpen(o => !o)}
          aria-expanded={isFilterOpen}
          aria-label="반경 필터"
        >
          필터
        </FilterBtn>
      </TopRow>

      {/* Row 2: 카테고리 가로 스크롤 */}
      <CategoryScrollRow role="group" aria-label="카테고리 필터">
        {KEYWORD_CATEGORIES.map(cat => (
          <CategoryChip
            key={cat.value}
            type="button"
            $active={category === cat.value}
            onClick={() => onCategoryChange(cat.value)}
          >
            {cat.label}
          </CategoryChip>
        ))}
      </CategoryScrollRow>

      {/* 반경 패널 — 필터 버튼 클릭 시에만 표시 */}
      {isFilterOpen && onRadiusChange && (
        <RadiusPanel>
          <FilterLabel>반경</FilterLabel>
          <RadiusRow>
            {RADIUS_OPTIONS.map(r => (
              <RadiusChip
                key={r}
                type="button"
                $active={radius === r}
                onClick={() => onRadiusChange(r)}
              >
                {r}km
              </RadiusChip>
            ))}
          </RadiusRow>
        </RadiusPanel>
      )}
    </Wrapper>
  );
};

export default LocationControls;

/* ── Styled Components ── */

const Wrapper = styled.div`
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
`;

const TopRow = styled.div`
  display: flex;
  gap: 6px;
  align-items: center;
`;

const SearchPill = styled.form`
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  background: ${p => p.theme.colors.background};
  border: 1.5px solid ${p => p.theme.colors.border};
  border-radius: 999px;
  overflow: hidden;
  transition: border-color 0.2s, box-shadow 0.2s;
  box-shadow: ${p => p.theme.shadows.sm};
  &:focus-within {
    border-color: ${p => p.theme.colors.primary};
    box-shadow: ${p => p.theme.shadows.focus}, ${p => p.theme.shadows.sm};
  }
`;

const SearchIcon = styled.span`
  padding: 0 4px 0 12px;
  font-size: 13px;
  flex-shrink: 0;
  opacity: 0.5;
`;

const SearchInput = styled.input`
  flex: 1;
  height: 36px;
  padding: 0 4px;
  border: none;
  background: transparent;
  color: ${p => p.theme.colors.text};
  font-size: 13px;
  outline: none;
  min-width: 0;
  &::placeholder { color: ${p => p.theme.colors.textMuted}; }
`;

const SearchBtn = styled.button`
  height: 36px;
  padding: 0 12px;
  border: none;
  border-radius: 0 999px 999px 0;
  background: ${p => p.theme.colors.primary};
  color: ${p => p.theme.colors.textInverse};
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  flex-shrink: 0;
  transition: background 0.15s;
  &:hover { background: ${p => p.theme.colors.primaryDark}; }
`;

const AreaBtn = styled.button`
  height: 36px;
  padding: 0 10px;
  border: none;
  border-radius: 0 999px 999px 0;
  background: ${p => p.theme.colors.primary};
  color: ${p => p.theme.colors.textInverse};
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
  flex-shrink: 0;
  white-space: nowrap;
  transition: background 0.15s;
  &:hover { background: ${p => p.theme.colors.primaryDark}; }
`;

const SortCycleBtn = styled.button`
  height: 36px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1.5px solid ${p => p.theme.colors.border};
  background: ${p => p.theme.colors.surface};
  color: ${p => p.theme.colors.textSecondary};
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
  transition: all 0.15s;
  &:hover {
    border-color: ${p => p.theme.colors.primary};
    color: ${p => p.theme.colors.primary};
  }
`;

const FilterBtn = styled.button`
  height: 36px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1.5px solid ${p => p.$active ? p.theme.colors.primary : p.theme.colors.border};
  background: ${p => p.$active ? p.theme.colors.primarySoft : p.theme.colors.surface};
  color: ${p => p.$active ? p.theme.colors.primary : p.theme.colors.textSecondary};
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
  transition: all 0.15s;
  &:hover {
    border-color: ${p => p.theme.colors.primary};
    color: ${p => p.theme.colors.primary};
  }
`;

const CategoryScrollRow = styled.div`
  display: flex;
  gap: 5px;
  overflow-x: auto;
  padding-bottom: 2px;
  &::-webkit-scrollbar { display: none; }
  scrollbar-width: none;
`;

const CategoryChip = styled.button`
  padding: 4px 12px;
  border-radius: 999px;
  border: 1.5px solid ${p => p.$active ? p.theme.colors.domain.location : p.theme.colors.border};
  background: ${p => p.$active ? p.theme.colors.domain.location + '22' : 'transparent'};
  color: ${p => p.$active ? p.theme.colors.domain.location : p.theme.colors.textSecondary};
  font-size: 12px;
  font-weight: ${p => p.$active ? 600 : 400};
  white-space: nowrap;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.15s;
  &:hover {
    border-color: ${p => p.theme.colors.domain.location};
    color: ${p => p.theme.colors.domain.location};
    background: ${p => p.theme.colors.domain.location + '14'};
  }
`;

const RadiusPanel = styled.div`
  padding: 8px 10px;
  border: 1.5px solid ${p => p.theme.colors.border};
  border-radius: 14px;
  background: ${p => p.theme.colors.background};
  display: flex;
  align-items: center;
  gap: 10px;
`;

const FilterLabel = styled.span`
  font-size: 11px;
  font-weight: 700;
  color: ${p => p.theme.colors.textSecondary};
  flex-shrink: 0;
`;

const RadiusRow = styled.div`
  display: flex;
  gap: 5px;
`;

const RadiusChip = styled.button`
  padding: 4px 14px;
  border-radius: 999px;
  border: 1.5px solid ${p => p.$active ? p.theme.colors.primary : p.theme.colors.border};
  background: ${p => p.$active ? p.theme.colors.primary + '18' : 'transparent'};
  color: ${p => p.$active ? p.theme.colors.primary : p.theme.colors.textSecondary};
  font-size: 12px;
  font-weight: ${p => p.$active ? 700 : 400};
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
  transition: all 0.15s;
  &:hover {
    border-color: ${p => p.theme.colors.primary};
    color: ${p => p.theme.colors.primary};
  }
`;
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
cd frontend && npm test -- --watchAll=false --testPathPattern=LocationControls.test
```

Expected: 8개 테스트 모두 PASS

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/components/UnifiedMap/controls/LocationControls.js \
        frontend/src/components/UnifiedMap/controls/LocationControls.test.js
git commit -m "feat(location): 검색·필터 컨트롤 2-row 가로 레이아웃으로 재설계"
```

---

## Task 3: RecommendCard.js — banner variant 테스트 작성

**Files:**
- Create: `frontend/src/components/Recommendation/RecommendCard.test.js`

- [ ] **Step 6: RecommendCard 테스트 파일 생성**

```js
// frontend/src/components/Recommendation/RecommendCard.test.js
import React from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider } from 'styled-components';
import { lightTheme } from '../../styles/theme';
import RecommendCard from './RecommendCard';

// recommendApi 모킹 — 실제 API 호출 방지
jest.mock('../../api/recommendApi', () => ({
  recommendApi: {
    getRecommendation: jest.fn().mockResolvedValue({
      data: {
        request_id: 'test-req-1',
        recommendation: '근처 미용실 중 리뷰가 많은 곳을 추천해요',
        trends: [{ keyword: '미용' }, { keyword: '예약' }],
        facilities: [{ id: 1, name: '털뭉치 미용', lat: 37.5, lng: 126.9, distance_m: 300 }],
      },
    }),
    getCopy: jest.fn().mockResolvedValue({
      data: { recommendation: '주말 예약은 미리 하세요' },
    }),
    sendEvents: jest.fn().mockResolvedValue({}),
  },
}));

const wrap = (props) =>
  render(
    <ThemeProvider theme={lightTheme}>
      <RecommendCard lat={37.5} lng={126.9} context="grooming" {...props} />
    </ThemeProvider>
  );

test('variant="banner": AI 뱃지와 추천 멘트가 슬림 배너로 렌더된다', async () => {
  wrap({ variant: 'banner' });
  expect(await screen.findByText('AI')).toBeInTheDocument();
  expect(await screen.findByText(/주말 예약은 미리 하세요/)).toBeInTheDocument();
});

test('variant="banner": 트렌드 태그가 배너 내부에 렌더된다', async () => {
  wrap({ variant: 'banner' });
  expect(await screen.findByText('# 미용')).toBeInTheDocument();
});

test('variant="banner": onDismiss 클릭 시 콜백이 호출된다', async () => {
  const onDismiss = jest.fn();
  wrap({ variant: 'banner', onDismiss });
  const closeBtn = await screen.findByLabelText('AI 추천 닫기');
  await userEvent.click(closeBtn);
  expect(onDismiss).toHaveBeenCalledTimes(1);
});

test('variant 미전달(card 모드): 큰 카드 타이틀 "AI 추천"이 렌더된다', async () => {
  wrap({});
  expect(await screen.findByText('AI 추천')).toBeInTheDocument();
});
```

- [ ] **Step 7: 테스트가 실패하는지 확인**

```bash
cd frontend && npm test -- --watchAll=false --testPathPattern=RecommendCard.test
```

Expected: banner 관련 테스트 FAIL (variant prop 미구현)

---

## Task 4: RecommendCard.js — banner variant 구현

**Files:**
- Modify: `frontend/src/components/Recommendation/RecommendCard.js`

- [ ] **Step 8: variant prop 및 배너 styled components 추가**

파일 하단 styled components 블록 뒤에 아래를 추가한다 (기존 `Card`, `Title` 등은 그대로 유지):

```jsx
// 파일 하단 — 기존 styled components 뒤에 추가
const BannerWrapper = styled.div`
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 0 0 8px;
  padding: 8px 10px;
  background: ${({ theme }) => theme.colors.primarySoft};
  border: 1px solid ${({ theme }) => theme.colors.primary + '33'};
  border-radius: 10px;
`;

const BannerContent = styled.div`
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
`;

const BannerBadge = styled.span`
  align-self: flex-start;
  background: ${({ theme }) => theme.colors.primary};
  color: ${({ theme }) => theme.colors.textInverse};
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
`;

const BannerText = styled.p`
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: ${({ theme }) => theme.colors.text};
`;

const BannerTrendRow = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
`;

const BannerTag = styled.span`
  background: ${({ theme }) => theme.colors.primary + '18'};
  color: ${({ theme }) => theme.colors.primary};
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 999px;
`;

const DismissBtn = styled.button`
  flex-shrink: 0;
  background: none;
  border: none;
  font-size: 14px;
  cursor: pointer;
  color: ${({ theme }) => theme.colors.textSecondary};
  padding: 0;
  line-height: 1;
  opacity: 0.6;
  &:hover { opacity: 1; }
`;
```

- [ ] **Step 9: RecommendCard 함수 시그니처 및 배너 분기 추가**

`function RecommendCard({` 를 찾아 아래처럼 수정한다:

```jsx
// 변경 전
function RecommendCard({ lat, lng, context, onFacilitiesLoaded }) {

// 변경 후
function RecommendCard({ lat, lng, context, onFacilitiesLoaded, variant = 'card', onDismiss }) {
```

그리고 기존 `if (loadingMain) return <Spinner .../>` 바로 아래에 배너 분기를 추가한다:

```jsx
  // 기존 코드 (변경 없음)
  if (loadingMain) return <Spinner text="추천 정보 불러오는 중..." />;
  if (
    !data ||
    (!data.recommendation && !data.trends?.length && !data.facilities?.length)
  )
    return null;

  const displayedCopy = copy?.recommendation || data.recommendation;

  // 배너 모드 분기 — 여기 추가
  if (variant === 'banner') {
    if (!displayedCopy && !data.trends?.length) return null;
    return (
      <BannerWrapper>
        <BannerContent>
          <BannerBadge>AI</BannerBadge>
          {displayedCopy && <BannerText>{displayedCopy}</BannerText>}
          {data.trends?.length > 0 && (
            <BannerTrendRow>
              {data.trends.slice(0, 3).map(t => (
                <BannerTag key={t.keyword}># {t.keyword}</BannerTag>
              ))}
            </BannerTrendRow>
          )}
        </BannerContent>
        {onDismiss && (
          <DismissBtn type="button" onClick={onDismiss} aria-label="AI 추천 닫기">
            ✕
          </DismissBtn>
        )}
      </BannerWrapper>
    );
  }

  // 기존 card 모드 return (변경 없음)
  return (
    <Card>
      ...
```

- [ ] **Step 10: 테스트 통과 확인**

```bash
cd frontend && npm test -- --watchAll=false --testPathPattern=RecommendCard.test
```

Expected: 4개 테스트 모두 PASS

- [ ] **Step 11: 커밋**

```bash
git add frontend/src/components/Recommendation/RecommendCard.js \
        frontend/src/components/Recommendation/RecommendCard.test.js
git commit -m "feat(recommendation): RecommendCard variant='banner' 슬림 배너 모드 추가"
```

---

## Task 5: UnifiedPetMapPage.js — 통합

**Files:**
- Modify: `frontend/src/components/UnifiedMap/UnifiedPetMapPage.js`

- [ ] **Step 12: aiDismissed 상태 추가**

`aiRequestId` 상태 선언 바로 아래(line ~89)에 추가:

```jsx
// 변경 전
const [aiRequestId, setAiRequestId] = useState(null);

// 변경 후
const [aiRequestId, setAiRequestId] = useState(null);
const [aiDismissed, setAiDismissed] = useState(false);
```

- [ ] **Step 13: 카테고리·탭 변경 시 aiDismissed 리셋**

`handleTabChange` 함수 첫 줄에 추가:

```jsx
const handleTabChange = (layer) => {
  setActiveLayer(layer);
  setAiDismissed(false);  // 추가
  setSelectedItem(null);
  ...
```

`renderLayerControls` 내 `onCategoryChange` 핸들러에 추가:

```jsx
onCategoryChange={(cat) => {
  setLocationCategory(cat);
  setAiDismissed(false);     // 추가
  setAiRecommendFacilities([]);
  setAiRequestId(null);
  cacheRef.current = {};
  commitLocationSearch(
    mapViewportCenter,
    cat ? "category" : "user-triggered"
  );
}}
```

- [ ] **Step 14: 모바일 ControlsOverlay — OverlayRow 제거 및 showRadius=true**

```jsx
// 변경 전
<ControlsOverlay>
  <OverlayRow>
    <RadiusFilter
      radius={radius}
      onRadiusChange={handleRadiusChange}
    />
  </OverlayRow>
  {renderLayerControls(false)}
</ControlsOverlay>

// 변경 후
<ControlsOverlay>
  {renderLayerControls(true)}
</ControlsOverlay>
```

- [ ] **Step 15: 데스크톱 LeftPanel — RecommendCard를 결과 리스트 상단 배너로 이동**

`LeftPanelResults` JSX 내부 첫 번째 자식으로 배너 추가:

```jsx
// 변경 전
<LeftPanelResults>
  {loading && <PanelStatusMsg>검색 중...</PanelStatusMsg>}
  ...

// 변경 후
<LeftPanelResults>
  {!aiDismissed && userLocation && CATEGORY_TO_CONTEXT[locationCategory] && (
    <RecommendCard
      lat={userLocation.lat}
      lng={userLocation.lng}
      context={CATEGORY_TO_CONTEXT[locationCategory]}
      onFacilitiesLoaded={handleAiRecommendLoad}
      variant="banner"
      onDismiss={() => setAiDismissed(true)}
    />
  )}
  {loading && <PanelStatusMsg>검색 중...</PanelStatusMsg>}
  ...
```

그리고 기존 `LeftPanel` 하단의 구 `RecommendCard` 블록(아래)을 **삭제**:

```jsx
// 삭제 대상 (LeftPanel 닫는 태그 바로 위)
{activeLayer === "location" &&
  userLocation &&
  CATEGORY_TO_CONTEXT[locationCategory] && (
    <RecommendCard
      lat={userLocation.lat}
      lng={userLocation.lng}
      context={CATEGORY_TO_CONTEXT[locationCategory]}
      onFacilitiesLoaded={handleAiRecommendLoad}
    />
  )}
```

- [ ] **Step 16: 모바일 LocationResultSheet — AI 배너 추가**

`renderLocationResults` 함수 내 `ResultList` 바로 위에 추가:

```jsx
// 변경 전
return (
  <LocationResultSheet>
    <ResultSheetHandle aria-hidden="true" />
    <ResultSheetHeader>
      ...
    </ResultSheetHeader>
    <ResultList>

// 변경 후
return (
  <LocationResultSheet>
    <ResultSheetHandle aria-hidden="true" />
    <ResultSheetHeader>
      ...
    </ResultSheetHeader>
    {!aiDismissed && userLocation && CATEGORY_TO_CONTEXT[locationCategory] && (
      <RecommendCard
        lat={userLocation.lat}
        lng={userLocation.lng}
        context={CATEGORY_TO_CONTEXT[locationCategory]}
        variant="banner"
        onDismiss={() => setAiDismissed(true)}
      />
    )}
    <ResultList>
```

> **주의:** 모바일 배너에는 `onFacilitiesLoaded`를 전달하지 않는다. AI 지도 마커는 데스크톱 `LeftPanel`의 `RecommendCard`에서만 로드한다.

- [ ] **Step 17: 빌드 확인**

```bash
cd frontend && npm run build 2>&1 | tail -20
```

Expected: `Compiled successfully.` (warnings는 무시, errors는 수정)

- [ ] **Step 18: 커밋**

```bash
git add frontend/src/components/UnifiedMap/UnifiedPetMapPage.js
git commit -m "feat(map): OverlayRow 제거, AI 추천 배너 결과 리스트 상단으로 재배치"
```

---

## 자체 검토 결과

**스펙 커버리지:**
- ✅ LocationControls 2-row 레이아웃 (Task 1-2)
- ✅ SummaryRow 제거, SortSelect → 사이클 버튼 (Task 2)
- ✅ 필터 패널: 반경만 표시 (Task 2)
- ✅ OverlayRow+RadiusFilter 제거 (Task 5 Step 14)
- ✅ `showRadius=true` 모바일에도 전달 (Task 5 Step 14)
- ✅ aiDismissed 상태 + 카테고리 변경 시 리셋 (Task 5 Steps 12-13)
- ✅ 데스크톱 배너 (Task 5 Step 15)
- ✅ 모바일 배너 (Task 5 Step 16)
- ✅ RecommendCard `variant="banner"` (Task 3-4)

**알려진 트레이드오프:**
- 모바일에서 `LeftPanel`(CSS hidden)의 `RecommendCard`와 `LocationResultSheet`의 `RecommendCard`가 동시에 마운트되어 API를 2회 호출한다. 동일 파라미터 요청이므로 기능적 문제는 없으나 API 호출이 1회 추가된다. 개선이 필요하다면 `LeftPanel`을 CSS가 아닌 조건부 렌더링으로 전환하면 된다 (현재 스코프 밖).
