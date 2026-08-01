// frontend/src/components/Chat/ChatSearchPanel.js
import React, { useEffect, useRef, useState } from 'react';
import styled from 'styled-components';
import { searchMessages } from '../../api/chatApi';

const MIN_KEYWORD_LENGTH = 2;

// 검색 결과는 대화 전 기간에 걸쳐 있어 시각만으로는 구분이 안 된다.
// ChatRoom 의 formatTime 은 HH:MM 만 내므로 여기서 날짜까지 붙여 따로 만든다.
const formatResultTime = (dateString) => {
  if (!dateString) return '';
  const date = new Date(dateString);
  const hh = date.getHours().toString().padStart(2, '0');
  const mm = date.getMinutes().toString().padStart(2, '0');
  return `${date.getMonth() + 1}월 ${date.getDate()}일 ${hh}:${mm}`;
};

/**
 * 본문에서 키워드를 대소문자 무시로 쪼개 <mark> 로 감싼다.
 * dangerouslySetInnerHTML 을 쓰지 않는다 — 메시지 본문은 사용자 입력이다.
 * 배열로 돌려주면 React 가 각 조각을 이스케이프한다.
 */
const highlight = (text, keyword) => {
  if (!keyword) return text;
  const lowerText = text.toLowerCase();
  const lowerKeyword = keyword.toLowerCase();
  const parts = [];
  let cursor = 0;

  for (;;) {
    const hit = lowerText.indexOf(lowerKeyword, cursor);
    if (hit === -1) break;
    if (hit > cursor) parts.push(text.slice(cursor, hit));
    parts.push(<mark key={hit}>{text.slice(hit, hit + keyword.length)}</mark>);
    cursor = hit + keyword.length;
  }
  if (cursor < text.length) parts.push(text.slice(cursor));
  return parts;
};

const ChatSearchPanel = ({ conversationIdx, onClose }) => {
  const [keyword, setKeyword] = useState('');
  const [results, setResults] = useState(null);
  const [searchedKeyword, setSearchedKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState('');

  // 연타하면 응답이 보낸 순서대로 오지 않는다. 마지막 요청의 응답만 반영한다.
  // (없으면 먼저 보낸 검색의 늦은 응답이 나중 검색 결과를 덮어쓰고,
  //  하이라이트용 searchedKeyword 가 표시된 행과 어긋난다.)
  const latestRequestRef = useRef(0);

  // 언마운트 뒤 setState 하지 않기 위한 것이기도 하다.
  useEffect(() => () => {
    latestRequestRef.current += 1;
  }, []);

  const handleSubmit = async (e) => {
    e.preventDefault();
    const trimmed = keyword.trim();

    // ngram_token_size=2 라 1글자는 서버가 무조건 0건을 돌려준다. 왕복을 아낀다.
    if (trimmed.length < MIN_KEYWORD_LENGTH) {
      setResults(null);
      setNotice('2글자 이상 입력해주세요.');
      return;
    }

    const requestId = latestRequestRef.current + 1;
    latestRequestRef.current = requestId;

    setNotice('');
    setLoading(true);
    try {
      const data = await searchMessages(conversationIdx, trimmed);
      if (latestRequestRef.current !== requestId) return;
      setResults(Array.isArray(data) ? data : []);
      setSearchedKeyword(trimmed);
    } catch (error) {
      if (latestRequestRef.current !== requestId) return;
      console.error('메시지 검색 실패:', error);
      setResults(null);
      setNotice('검색에 실패했습니다. 다시 시도해주세요.');
    } finally {
      if (latestRequestRef.current === requestId) setLoading(false);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Escape') onClose();
  };

  return (
    <Panel>
      <SearchBar role="search" onSubmit={handleSubmit}>
        <SearchIcon type="submit" aria-label="검색">🔍</SearchIcon>
        <SearchInput
          autoFocus
          value={keyword}
          placeholder="메시지 검색"
          onChange={(e) => setKeyword(e.target.value)}
          onKeyDown={handleKeyDown}
        />
        <CloseButton type="button" onClick={onClose} aria-label="검색 닫기">✕</CloseButton>
      </SearchBar>

      <ResultArea>
        {loading && <Notice>검색 중...</Notice>}
        {!loading && notice && <Notice>{notice}</Notice>}
        {!loading && !notice && results !== null && results.length === 0 && (
          <Notice>검색 결과가 없습니다.</Notice>
        )}
        {!loading && !notice && results !== null && results.length > 0 && (
          <>
            <ResultCount>{results.length}건</ResultCount>
            {results.map((message) => (
              <ResultItem key={message.idx}>
                <ResultMeta>
                  <ResultSender>{message.senderUsername || '알 수 없음'}</ResultSender>
                  <ResultTime>{formatResultTime(message.createdAt)}</ResultTime>
                </ResultMeta>
                <ResultContent>
                  {message.messageType === 'IMAGE'
                    ? '(이미지)'
                    : highlight(message.content || '', searchedKeyword)}
                </ResultContent>
              </ResultItem>
            ))}
          </>
        )}
      </ResultArea>
    </Panel>
  );
};

export default ChatSearchPanel;

// 채팅방 전체를 덮는 오버레이다. Container 가 이미 position: relative 라
// (ChatRoom.js:884) inset: 0 이 채팅방 영역에 딱 맞는다.
// 이렇게 해야 메시지 목록이 언마운트되지 않고 뒤에 그대로 남는다 —
// 형제 flex 아이템으로 넣으면 둘 다 flex:1 이라 화면을 반씩 나눠 갖는다.
const Panel = styled.div`
  position: absolute;
  inset: 0;
  z-index: 5;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: ${({ theme }) => theme.colors.background};
`;

const SearchBar = styled.form`
  display: flex;
  align-items: center;
  height: 56px;
  padding: 0 16px;
  gap: 8px;
  flex-shrink: 0;
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
  background: ${({ theme }) => theme.colors.surface};
`;

const SearchIcon = styled.button`
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  font-size: 16px;
  cursor: pointer;
  flex-shrink: 0;
`;

const SearchInput = styled.input`
  flex: 1;
  min-width: 0;
  height: 36px;
  padding: 0 12px;
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.borderRadius.md};
  background: ${({ theme }) => theme.colors.surfaceElevated};
  color: ${({ theme }) => theme.colors.text};
  font-size: 14px;

  &:focus {
    outline: none;
    border-color: ${({ theme }) => theme.colors.borderFocus};
  }
`;

const CloseButton = styled.button`
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: ${({ theme }) => theme.colors.text};
  font-size: 16px;
  cursor: pointer;
  flex-shrink: 0;
`;

const ResultArea = styled.div`
  flex: 1 1 0;
  min-height: 0;
  overflow-y: auto;
  padding: 8px 0;
`;

const Notice = styled.div`
  padding: 24px 16px;
  text-align: center;
  color: ${({ theme }) => theme.colors.textSecondary};
  font-size: 14px;
`;

const ResultCount = styled.div`
  padding: 4px 16px 8px;
  color: ${({ theme }) => theme.colors.textSecondary};
  font-size: 12px;
`;

const ResultItem = styled.div`
  padding: 10px 16px;
  border-bottom: 1px solid ${({ theme }) => theme.colors.borderLight};
`;

const ResultMeta = styled.div`
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
`;

const ResultSender = styled.span`
  font-size: 13px;
  font-weight: 600;
  color: ${({ theme }) => theme.colors.text};
`;

const ResultTime = styled.span`
  font-size: 12px;
  color: ${({ theme }) => theme.colors.textLight};
  flex-shrink: 0;
`;

const ResultContent = styled.div`
  font-size: 14px;
  color: ${({ theme }) => theme.colors.text};
  word-break: break-word;

  mark {
    background: ${({ theme }) => theme.colors.primarySoft};
    color: ${({ theme }) => theme.colors.primaryDark};
    border-radius: ${({ theme }) => theme.borderRadius.xs};
  }
`;
