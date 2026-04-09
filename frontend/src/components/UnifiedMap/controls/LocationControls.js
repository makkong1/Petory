import React, { useState } from 'react';
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

const LocationControls = ({ keyword, category, isAiMode, onSearch, onCategoryChange, onAiToggle }) => {
  const [inputValue, setInputValue] = useState(keyword || '');

  const handleSubmit = (e) => {
    e.preventDefault();
    onSearch(inputValue.trim());
  };

  return (
    <Wrapper>
      <SearchRow onSubmit={handleSubmit}>
        <SearchInput
          value={inputValue}
          onChange={e => setInputValue(e.target.value)}
          placeholder="시설명, 주소 검색..."
        />
        <SearchButton type="submit">검색</SearchButton>
        <AiButton
          type="button"
          $active={isAiMode}
          onClick={onAiToggle}
          title="AI 추천: 내 주변 최적 시설 TOP 10"
        >
          ✨ AI 추천
        </AiButton>
      </SearchRow>
      <CategoryRow>
        {KEYWORD_CATEGORIES.map(cat => (
          <CategoryChip
            key={cat.value}
            $active={category === cat.value}
            onClick={() => onCategoryChange(cat.value)}
          >
            {cat.label}
          </CategoryChip>
        ))}
      </CategoryRow>
    </Wrapper>
  );
};

export default LocationControls;

const Wrapper = styled.div`
  background: ${props => props.theme.colors.surface};
  border-bottom: 1px solid ${props => props.theme.colors.border};
  padding: 8px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
`;

const SearchRow = styled.form`
  display: flex;
  gap: 6px;
  align-items: center;
`;

const SearchInput = styled.input`
  flex: 1;
  padding: 7px 12px;
  border: 1px solid ${props => props.theme.colors.border};
  border-radius: 8px;
  background: ${props => props.theme.colors.background};
  color: ${props => props.theme.colors.text};
  font-size: 13px;
  outline: none;

  &:focus {
    border-color: ${props => props.theme.colors.primary};
  }
`;

const SearchButton = styled.button`
  padding: 7px 14px;
  border-radius: 8px;
  border: none;
  background: ${props => props.theme.colors.primary};
  color: white;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;

  &:hover { opacity: 0.9; }
`;

const AiButton = styled.button`
  padding: 7px 12px;
  border-radius: 8px;
  border: 1.5px solid ${props => props.$active ? '#F5A623' : props.theme.colors.border};
  background: ${props => props.$active ? '#FFF8EC' : props.theme.colors.background};
  color: ${props => props.$active ? '#c47d00' : props.theme.colors.textSecondary};
  font-size: 13px;
  font-weight: ${props => props.$active ? 700 : 400};
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s ease;

  &:hover {
    border-color: #F5A623;
    color: #c47d00;
  }
`;

const CategoryRow = styled.div`
  display: flex;
  gap: 6px;
  overflow-x: auto;
  padding-bottom: 2px;

  /* 스크롤바 숨기기 */
  scrollbar-width: none;
  &::-webkit-scrollbar { display: none; }
`;

const CategoryChip = styled.button`
  padding: 4px 12px;
  border-radius: 14px;
  border: 1px solid ${props => props.$active ? props.theme.colors.primary : props.theme.colors.border};
  background: ${props => props.$active ? props.theme.colors.primary : props.theme.colors.background};
  color: ${props => props.$active ? 'white' : props.theme.colors.text};
  font-size: 12px;
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.15s ease;

  &:hover {
    border-color: ${props => props.theme.colors.primary};
    color: ${props => props.$active ? 'white' : props.theme.colors.primary};
  }
`;
