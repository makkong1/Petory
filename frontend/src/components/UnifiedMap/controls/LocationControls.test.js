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
        onCategoryPick={jest.fn()}
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
  expect(screen.getByText('반려의료')).toBeInTheDocument();
  expect(screen.getByText('용품·서비스')).toBeInTheDocument();
});

test('카테고리 칩 클릭 시 onCategoryPick이 해당 category와 groupId로 호출된다', () => {
  const onCategoryPick = jest.fn();
  wrap({ onCategoryPick });
  fireEvent.click(screen.getByText('반려의료'));
  expect(onCategoryPick).toHaveBeenCalledWith({
    category: '반려의료',
    groupId: 'medical',
  });
});

test('정렬 버튼 클릭 시 추천순→거리순 순환한다', () => {
  const onSortChange = jest.fn();
  wrap({ sort: 'stable', onSortChange });
  fireEvent.click(screen.getByLabelText('정렬 변경'));
  expect(onSortChange).toHaveBeenCalledWith('distance');
});

test('정렬 버튼 클릭 시 거리순→평점순 순환한다', () => {
  const onSortChange = jest.fn();
  wrap({ sort: 'distance', onSortChange });
  fireEvent.click(screen.getByLabelText('정렬 변경'));
  expect(onSortChange).toHaveBeenCalledWith('rating');
});

test('정렬 버튼 클릭 시 리뷰순→추천순 순환한다', () => {
  const onSortChange = jest.fn();
  wrap({ sort: 'reviews', onSortChange });
  fireEvent.click(screen.getByLabelText('정렬 변경'));
  expect(onSortChange).toHaveBeenCalledWith('stable');
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

test('정렬 버튼 클릭 시 평점순→리뷰순 순환한다', () => {
  const onSortChange = jest.fn();
  wrap({ sort: 'rating', onSortChange });
  fireEvent.click(screen.getByLabelText('정렬 변경'));
  expect(onSortChange).toHaveBeenCalledWith('reviews');
});

test('필터 버튼 재클릭 시 반경 패널이 닫힌다', () => {
  wrap({});
  const filterBtn = screen.getByLabelText('반경 필터');
  fireEvent.click(filterBtn);
  expect(screen.getByText('5km')).toBeInTheDocument();
  fireEvent.click(filterBtn);
  expect(screen.queryByText('5km')).not.toBeInTheDocument();
});
