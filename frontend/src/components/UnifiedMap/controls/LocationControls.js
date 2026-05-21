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
    onSearch?.(inputValue.trim());
  };

  const handleSortCycle = () => {
    const idx = SORT_OPTIONS.indexOf(sort);
    onSortChange?.(SORT_OPTIONS[(idx + 1) % SORT_OPTIONS.length]);
  };

  return (
    <Wrapper>
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
          aria-controls="radius-filter-panel"
          aria-label="반경 필터"
        >
          필터
        </FilterBtn>
      </TopRow>

      <CategoryScrollRow role="group" aria-label="카테고리 필터">
        {KEYWORD_CATEGORIES.map(cat => (
          <CategoryChip
            key={cat.value}
            type="button"
            $active={category === cat.value}
            onClick={() => onCategoryChange?.(cat.value)}
          >
            {cat.label}
          </CategoryChip>
        ))}
      </CategoryScrollRow>

      {isFilterOpen && onRadiusChange && (
        <RadiusPanel id="radius-filter-panel">
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
