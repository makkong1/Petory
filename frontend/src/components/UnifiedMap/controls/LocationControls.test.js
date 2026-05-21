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
  expect(screen.queryByText('5km')).not.toBeInTheDocument();
  fireEvent.click(screen.getByLabelText('반경 필터'));
  expect(screen.getByText('5km')).toBeInTheDocument();
});

test('반경 패널에는 반경 옵션만 있다 (카테고리/정렬 없음)', () => {
  wrap({});
  fireEvent.click(screen.getByLabelText('반경 필터'));
  expect(screen.getByText('1km')).toBeInTheDocument();
  expect(screen.getByText('10km')).toBeInTheDocument();
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
