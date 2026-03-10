import React, { useState, useEffect } from 'react';
import styled from 'styled-components';

/**
 * 공통 페이징 컴포넌트
 * 이전 / 페이지번호(입력 가능) / 다음
 * - totalCount, pageSize로 최대 페이지 계산
 * - 페이지 입력 시 해당 페이지로 이동
 */
const PageNavigation = ({
  currentPage,
  totalCount,
  pageSize,
  onPageChange,
  loading = false,
  showTotal = true,
}) => {
  const totalPages = Math.max(1, Math.ceil(totalCount / pageSize));
  const [inputValue, setInputValue] = useState(String(currentPage + 1));

  useEffect(() => {
    setInputValue(String(currentPage + 1));
  }, [currentPage]);

  const handlePrev = () => {
    if (currentPage > 0 && !loading) {
      onPageChange(currentPage - 1);
    }
  };

  const handleNext = () => {
    if (currentPage < totalPages - 1 && !loading) {
      onPageChange(currentPage + 1);
    }
  };

  const handleInputChange = (e) => {
    const val = e.target.value.replace(/\D/g, '');
    setInputValue(val);
  };

  const handleInputKeyDown = (e) => {
    if (e.key === 'Enter') {
      handleGoToPage();
    }
  };

  const handleGoToPage = () => {
    const num = parseInt(inputValue, 10);
    if (Number.isNaN(num) || num < 1 || num > totalPages) {
      setInputValue(String(currentPage + 1));
      return;
    }
    if (num - 1 !== currentPage && !loading) {
      onPageChange(num - 1);
    }
  };

  const handleInputBlur = () => {
    handleGoToPage();
  };

  if (totalCount <= 0) return null;

  return (
    <Wrapper>
      {showTotal && <TotalText>총 {totalCount.toLocaleString()}건</TotalText>}
      <NavGroup>
        <NavButton
          type="button"
          onClick={handlePrev}
          disabled={currentPage <= 0 || loading}
          aria-label="이전 페이지"
        >
          이전
        </NavButton>
        <PageInputGroup>
          <PageInput
            type="text"
            inputMode="numeric"
            value={inputValue}
            onChange={handleInputChange}
            onKeyDown={handleInputKeyDown}
            onBlur={handleInputBlur}
            disabled={loading}
            aria-label="페이지 번호"
          />
          <PageSeparator>/</PageSeparator>
          <MaxPageText>{totalPages}</MaxPageText>
        </PageInputGroup>
        <NavButton
          type="button"
          onClick={handleNext}
          disabled={currentPage >= totalPages - 1 || loading}
          aria-label="다음 페이지"
        >
          다음
        </NavButton>
      </NavGroup>
    </Wrapper>
  );
};

export default PageNavigation;

const Wrapper = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: ${(p) => p.theme?.spacing?.md || '16px'};
  flex-wrap: wrap;
  font-size: ${(p) => p.theme?.typography?.caption?.fontSize || '14px'};
  color: ${(p) => p.theme?.colors?.textSecondary || '#64748b'};
`;

const TotalText = styled.span``;

const NavGroup = styled.div`
  display: flex;
  align-items: center;
  gap: ${(p) => p.theme?.spacing?.sm || '8px'};
`;

const NavButton = styled.button`
  padding: 6px 12px;
  border: 1px solid ${(p) => p.theme?.colors?.border || '#e2e8f0'};
  background: ${(p) => p.theme?.colors?.surface || '#fff'};
  color: ${(p) => p.theme?.colors?.text || '#1e293b'};
  border-radius: 6px;
  cursor: pointer;
  font-size: inherit;

  &:hover:not(:disabled) {
    background: ${(p) => p.theme?.colors?.surfaceHover || '#f1f5f9'};
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

const PageInputGroup = styled.div`
  display: flex;
  align-items: center;
  gap: 4px;
`;

const PageInput = styled.input`
  width: 48px;
  padding: 6px 8px;
  border: 1px solid ${(p) => p.theme?.colors?.border || '#e2e8f0'};
  border-radius: 6px;
  background: ${(p) => p.theme?.colors?.surface || '#fff'};
  color: ${(p) => p.theme?.colors?.text || '#1e293b'};
  font-size: inherit;
  text-align: center;

  &:focus {
    outline: none;
    border-color: ${(p) => p.theme?.colors?.primary || '#3b82f6'};
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
`;

const PageSeparator = styled.span`
  color: ${(p) => p.theme?.colors?.textSecondary || '#64748b'};
`;

const MaxPageText = styled.span`
  min-width: 24px;
  color: ${(p) => p.theme?.colors?.textSecondary || '#64748b'};
`;
