import React from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider } from 'styled-components';
import { lightTheme } from '../../styles/theme';
import RecommendCard from './RecommendCard';
import { recommendApi } from '../../api/recommendApi';

jest.mock('../../api/recommendApi', () => ({
  recommendApi: {
    getRecommendation: jest.fn(),
    getCopy: jest.fn(),
    sendEvents: jest.fn(),
  },
}));

beforeEach(() => {
  recommendApi.getRecommendation.mockResolvedValue({
    data: {
      request_id: 'test-req-1',
      recommendation: '근처 미용실 중 리뷰가 많은 곳을 추천해요',
      trends: [{ keyword: '미용' }, { keyword: '예약' }],
      facilities: [{ id: 1, name: '털뭉치 미용', lat: 37.5, lng: 126.9, distance_m: 300 }],
    },
  });
  recommendApi.getCopy.mockResolvedValue({
    data: { recommendation: '주말 예약은 미리 하세요' },
  });
  recommendApi.sendEvents.mockResolvedValue({});
});

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
