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

const SORT_OPTIONS = [
  { value: 'distance', label: '거리순' },
  { value: 'rating', label: '평점순' },
  { value: 'reviews', label: '리뷰순' },
];

const RADIUS_OPTIONS = [1, 3, 5, 10];

const FILTER_PANEL_ID = 'location-filter-panel';

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

  const selectedCategory = KEYWORD_CATEGORIES.find(cat => cat.value === category)?.label || '전체';
  const selectedSort = SORT_OPTIONS.find(option => option.value === sort)?.label || '거리순';

  return (
    <Wrapper>
      <SearchRow>
        <SearchPill onSubmit={handleSubmit}>
          <SearchIcon aria-hidden="true">🔍</SearchIcon>
          <SearchInput
            value={inputValue}
            onChange={e => setInputValue(e.target.value)}
            placeholder="시설명, 주소 검색..."
            aria-label="시설 검색"
          />
          <SearchButton type="submit">검색</SearchButton>
        </SearchPill>

      </SearchRow>

      {/* 현재 조건 요약 + 필터 토글 */}
      <SummaryRow>
        <SummaryChips aria-label="적용된 검색 조건">
          <SummaryChip>{selectedCategory}</SummaryChip>
          {radius != null && <SummaryChip>{radius}km</SummaryChip>}
          <SummaryChip>{selectedSort}</SummaryChip>
        </SummaryChips>
        <SummaryActions>
          {hasPendingAreaChange && (
            <SearchAreaButton type="button" onClick={onSearchThisArea}>
              이 지역 검색
            </SearchAreaButton>
          )}
          <FilterToggle
            type="button"
            onClick={() => setIsFilterOpen(open => !open)}
            aria-expanded={isFilterOpen}
            aria-controls={FILTER_PANEL_ID}
            $active={isFilterOpen}
          >
            필터 {isFilterOpen ? '▲' : '▼'}
          </FilterToggle>
        </SummaryActions>
      </SummaryRow>

      {/* 접을 수 있는 필터 패널 */}
      {isFilterOpen && (
        <FilterPanel id={FILTER_PANEL_ID}>
          <FilterSection>
            <FilterLabel>카테고리</FilterLabel>
            <CategoryRow role="group" aria-label="카테고리 필터">
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
            </CategoryRow>
          </FilterSection>

          {onRadiusChange && (
            <FilterSection>
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
            </FilterSection>
          )}

          <FilterSection>
            <FilterLabel>정렬</FilterLabel>
            <SortSelect
              value={sort}
              onChange={e => onSortChange?.(e.target.value)}
              aria-label="정렬 기준"
            >
              {SORT_OPTIONS.map(option => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </SortSelect>
          </FilterSection>
        </FilterPanel>
      )}

      {hasPendingAreaChange && (
        <SearchHint $pending>
          지도를 움직였습니다. 현재 화면 기준으로 다시 검색합니다.
        </SearchHint>
      )}
    </Wrapper>
  );
};

export default LocationControls;

const Wrapper = styled.div`
  padding: 10px 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
`;

const SearchRow = styled.div`
  display: flex;
  gap: 8px;
  align-items: center;
`;

const SearchPill = styled.form`
  flex: 1;
  display: flex;
  align-items: center;
  background: ${props => props.theme.colors.background};
  border: 1.5px solid ${props => props.theme.colors.border};
  border-radius: 999px;
  overflow: hidden;
  transition: border-color 0.2s, box-shadow 0.2s;
  box-shadow: ${props => props.theme.shadows.sm};

  &:focus-within {
    border-color: ${props => props.theme.colors.primary};
    box-shadow: ${props => props.theme.shadows.focus}, ${props => props.theme.shadows.sm};
  }
`;

const SearchIcon = styled.span`
  padding: 0 4px 0 14px;
  font-size: 14px;
  flex-shrink: 0;
  opacity: 0.5;
`;

const SearchInput = styled.input`
  flex: 1;
  height: 40px;
  padding: 0 6px;
  border: none;
  background: transparent;
  color: ${props => props.theme.colors.text};
  font-size: 14px;
  outline: none;
  min-width: 0;

  &::placeholder { color: ${props => props.theme.colors.textMuted}; }
`;

const SearchButton = styled.button`
  height: 40px;
  padding: 0 14px;
  border: none;
  border-radius: 0 999px 999px 0;
  background: ${props => props.theme.colors.primary};
  color: ${props => props.theme.colors.textInverse};
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  flex-shrink: 0;
  transition: background 0.15s;

  &:hover { background: ${props => props.theme.colors.primaryDark}; }
`;

const SummaryRow = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
`;

const SummaryChips = styled.div`
  display: flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
  overflow: hidden;
`;

const SummaryChip = styled.span`
  max-width: 96px;
  padding: 4px 9px;
  border-radius: 999px;
  background: ${props => props.theme.colors.domain.location + '1A'};
  color: ${props => props.theme.colors.domain.location};
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const SummaryActions = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
`;

const FilterToggle = styled.button`
  height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1.5px solid ${props => props.$active
    ? props.theme.colors.primary
    : props.theme.colors.border};
  background: ${props => props.$active
    ? props.theme.colors.primarySoft
    : props.theme.colors.surface};
  color: ${props => props.$active
    ? props.theme.colors.primary
    : props.theme.colors.textSecondary};
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s ease;

  &:hover {
    border-color: ${props => props.theme.colors.primary};
    color: ${props => props.theme.colors.primary};
  }
`;

const FilterPanel = styled.div`
  padding: 12px;
  border: 1.5px solid ${props => props.theme.colors.border};
  border-radius: 18px;
  background: ${props => props.theme.colors.background};
  display: flex;
  flex-direction: column;
  gap: 12px;
`;

const FilterSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: 7px;
`;

const FilterLabel = styled.span`
  font-size: 11px;
  font-weight: 700;
  color: ${props => props.theme.colors.textSecondary};
  letter-spacing: 0.02em;
`;

const CategoryRow = styled.div`
  display: flex;
  gap: 5px;
  flex-wrap: wrap;
`;

const CategoryChip = styled.button`
  padding: 4px 12px;
  border-radius: 999px;
  border: 1.5px solid ${props => props.$active
    ? props.theme.colors.domain.location
    : props.theme.colors.border};
  background: ${props => props.$active
    ? props.theme.colors.domain.location + '22'
    : 'transparent'};
  color: ${props => props.$active
    ? props.theme.colors.domain.location
    : props.theme.colors.textSecondary};
  font-size: 12px;
  font-weight: ${props => props.$active ? 600 : 400};
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.15s ease;
  flex-shrink: 0;

  &:hover {
    border-color: ${props => props.theme.colors.domain.location};
    color: ${props => props.theme.colors.domain.location};
    background: ${props => props.theme.colors.domain.location + '14'};
  }
`;

const RadiusRow = styled.div`
  display: flex;
  gap: 5px;
`;

const RadiusChip = styled.button`
  padding: 4px 14px;
  border-radius: 999px;
  border: 1.5px solid ${props => props.$active ? props.theme.colors.primary : props.theme.colors.border};
  background: ${props => props.$active ? props.theme.colors.primary + '18' : 'transparent'};
  color: ${props => props.$active ? props.theme.colors.primary : props.theme.colors.textSecondary};
  font-size: 12px;
  font-weight: ${props => props.$active ? 700 : 400};
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
  transition: all 0.15s ease;

  &:hover {
    border-color: ${props => props.theme.colors.primary};
    color: ${props => props.theme.colors.primary};
  }
`;

const SortSelect = styled.select`
  height: 34px;
  border-radius: 999px;
  border: 1.5px solid ${props => props.theme.colors.border};
  background: ${props => props.theme.colors.background};
  color: ${props => props.theme.colors.text};
  padding: 0 12px;
  font-size: 12px;
  font-weight: 600;
  outline: none;
  flex-shrink: 0;
  cursor: pointer;
  transition: border-color 0.15s;
  align-self: flex-start;

  &:focus, &:hover {
    border-color: ${props => props.theme.colors.primary};
  }
`;

const SearchAreaButton = styled.button`
  height: 30px;
  padding: 0 11px;
  border: none;
  border-radius: 999px;
  background: ${props => props.theme.colors.primary};
  color: ${props => props.theme.colors.textInverse};
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
  box-shadow: ${props => props.theme.shadows.md};
  transition: background 0.15s;

  &:hover {
    background: ${props => props.theme.colors.primaryDark};
  }
`;

const SearchHint = styled.p`
  margin: 0;
  font-size: 11px;
  line-height: 1.4;
  color: ${props => props.$pending
    ? props.theme.colors.primary
    : props.theme.colors.textMuted};
`;
