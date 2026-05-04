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

const LocationControls = ({
  keyword,
  category,
  sort = 'distance',
  isAiMode,
  hasPendingAreaChange = false,
  onSearch,
  onCategoryChange,
  onSortChange,
  onSearchThisArea,
  onAiToggle,
}) => {
  const [inputValue, setInputValue] = useState(keyword || '');

  useEffect(() => {
    setInputValue(keyword || '');
  }, [keyword]);

  const handleSubmit = (e) => {
    e.preventDefault();
    onSearch(inputValue.trim());
  };

  return (
    <Wrapper>
      <SearchRow>
        {/* 검색 pill: input + 검색 버튼 합체 */}
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

        <AiButton
          type="button"
          $active={isAiMode}
          onClick={onAiToggle}
          title="AI 추천: 내 주변 최적 시설 TOP 10"
        >
          ✨ AI
        </AiButton>
      </SearchRow>

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

      <FilterRow>
        <FilterGroup>
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
        </FilterGroup>

        {hasPendingAreaChange && (
          <SearchAreaButton type="button" onClick={onSearchThisArea}>
            이 지역 검색
          </SearchAreaButton>
        )}
      </FilterRow>

      {hasPendingAreaChange && (
        <SearchHint $pending>
          지도를 움직였습니다. 현재 화면 기준 결과를 다시 불러옵니다.
        </SearchHint>
      )}
    </Wrapper>
  );
};

export default LocationControls;

const Wrapper = styled.div`
  padding: 8px 12px;
  display: flex;
  flex-direction: column;
  gap: 7px;
`;

const SearchRow = styled.div`
  display: flex;
  gap: 8px;
  align-items: center;
`;

/* 검색창 전체를 하나의 pill로 */
const SearchPill = styled.form`
  flex: 1;
  display: flex;
  align-items: center;
  background: ${props => props.theme.colors.background};
  border: 1.5px solid ${props => props.theme.colors.border};
  border-radius: 999px;
  overflow: hidden;
  transition: border-color 0.15s, box-shadow 0.15s;

  &:focus-within {
    border-color: ${props => props.theme.colors.primary};
    box-shadow: 0 0 0 3px rgba(232, 113, 74, 0.15);
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
  height: 38px;
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
  height: 38px;
  padding: 0 16px;
  border: none;
  background: ${props => props.theme.colors.primary};
  color: white;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  flex-shrink: 0;
  transition: background 0.15s;

  &:hover { background: ${props => props.theme.colors.primaryDark}; }
`;

const AiButton = styled.button`
  height: 38px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1.5px solid ${props => props.$active
    ? props.theme.colors.ai.accent
    : props.theme.colors.border};
  background: ${props => props.$active
    ? props.theme.colors.ai.bg
    : 'transparent'};
  color: ${props => props.$active
    ? props.theme.colors.ai.text
    : props.theme.colors.textSecondary};
  font-size: 13px;
  font-weight: ${props => props.$active ? 700 : 400};
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
  transition: all 0.15s ease;

  &:hover {
    border-color: ${props => props.theme.colors.ai.accent};
    color: ${props => props.theme.colors.ai.text};
  }
`;

const CategoryRow = styled.div`
  display: flex;
  gap: 5px;
  overflow-x: auto;
  padding-bottom: 2px;
  scrollbar-width: none;
  &::-webkit-scrollbar { display: none; }
`;

const CategoryChip = styled.button`
  padding: 3px 11px;
  border-radius: 999px;
  border: 1.5px solid ${props => props.$active
    ? props.theme.colors.domain.location
    : props.theme.colors.border};
  background: ${props => props.$active
    ? props.theme.colors.domain.location
    : 'transparent'};
  color: ${props => props.$active ? 'white' : props.theme.colors.textSecondary};
  font-size: 12px;
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.15s ease;
  flex-shrink: 0;

  &:hover {
    border-color: ${props => props.theme.colors.domain.location};
    color: ${props => props.$active ? 'white' : props.theme.colors.domain.location};
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

const FilterRow = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
`;

const FilterGroup = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
`;

const FilterLabel = styled.span`
  font-size: 12px;
  font-weight: 700;
  color: ${props => props.theme.colors.textSecondary};
`;

const SortSelect = styled.select`
  height: 34px;
  border-radius: 12px;
  border: 1px solid ${props => props.theme.colors.border};
  background: ${props => props.theme.colors.background};
  color: ${props => props.theme.colors.text};
  padding: 0 12px;
  font-size: 12px;
  font-weight: 600;
  outline: none;

  &:focus {
    border-color: ${props => props.theme.colors.primary};
  }
`;

const SearchAreaButton = styled.button`
  height: 34px;
  padding: 0 14px;
  border: none;
  border-radius: 999px;
  background: ${props => props.theme.colors.primary};
  color: white;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.14);

  &:hover {
    background: ${props => props.theme.colors.primaryDark};
  }
`;
