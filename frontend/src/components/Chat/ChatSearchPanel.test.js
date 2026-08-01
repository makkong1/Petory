// frontend/src/components/Chat/ChatSearchPanel.test.js
import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { ThemeProvider } from 'styled-components';
import { lightTheme } from '../../styles/theme';
import ChatSearchPanel from './ChatSearchPanel';
import { searchMessages } from '../../api/chatApi';

jest.mock('../../api/chatApi', () => ({
  searchMessages: jest.fn(),
}));

const wrap = (props) =>
  render(
    <ThemeProvider theme={lightTheme}>
      <ChatSearchPanel conversationIdx={1} onClose={jest.fn()} {...props} />
    </ThemeProvider>
  );

const submit = (keyword) => {
  fireEvent.change(screen.getByPlaceholderText('메시지 검색'), {
    target: { value: keyword },
  });
  fireEvent.submit(screen.getByRole('search'));
};

beforeEach(() => {
  searchMessages.mockReset();
});

test('2글자 미만이면 API를 호출하지 않고 안내 문구를 보여준다', () => {
  wrap({});
  submit('산');
  expect(searchMessages).not.toHaveBeenCalled();
  expect(screen.getByText('2글자 이상 입력해주세요.')).toBeInTheDocument();
});

test('검색에 성공하면 건수와 결과를 보여주고 키워드를 mark로 감싼다', async () => {
  searchMessages.mockResolvedValue([
    {
      idx: 10,
      senderIdx: 2,
      senderUsername: '김철수',
      content: '강아지 산책 같이 가실래요',
      createdAt: '2026-07-28T14:03:00',
      messageType: 'TEXT',
    },
  ]);
  wrap({});
  submit('산책');

  expect(await screen.findByText('1건')).toBeInTheDocument();
  expect(searchMessages).toHaveBeenCalledWith(1, '산책');
  expect(screen.getByText('김철수')).toBeInTheDocument();

  const mark = screen.getByText('산책');
  expect(mark.tagName).toBe('MARK');
});

test('결과가 없으면 안내 문구를 보여준다', async () => {
  searchMessages.mockResolvedValue([]);
  wrap({});
  submit('산책');

  expect(await screen.findByText('검색 결과가 없습니다.')).toBeInTheDocument();
});
