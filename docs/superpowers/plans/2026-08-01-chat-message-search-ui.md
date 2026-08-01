# 채팅 메시지 검색 UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 채팅방 헤더에 🔍 버튼을 달아, 그 방의 메시지를 키워드로 검색해 결과 목록을 보여준다.

**Architecture:** 검색 로직·UI 전부를 신규 `ChatSearchPanel.js`가 소유한다. `ChatRoom.js`(현재 1,624줄)는 `showSearch` 상태와 헤더 버튼만 추가하고, 켜지면 `<Header>` 자리에 패널을 렌더링한다. 백엔드는 이미 완성돼 있어 변경하지 않는다.

**Tech Stack:** React 19, styled-components, @testing-library/react (jest via react-scripts)

## Global Constraints

- **백엔드 변경 금지.** `V12__chat_location_search_ngram.sql`, `FulltextParserRegressionTest`, `ChatMessageController`, `ChatMessageService`, `SpringDataJpaChatMessageRepository` 모두 손대지 않는다.
- **`dangerouslySetInnerHTML` 금지.** 메시지 본문은 사용자 입력이다. 하이라이트는 문자열을 배열로 쪼개 React가 이스케이프하게 둔다.
- **검색어 2글자 미만이면 API를 호출하지 않는다.** 서버 `ngram_token_size=2`라 1글자는 무조건 0건이다.
- **타이핑 디바운스 자동검색을 넣지 않는다.** 제출(엔터 또는 🔍 클릭)로만 호출한다.
- 스타일은 `theme.colors.*` / `theme.borderRadius.*` 토큰만 쓴다. 하드코딩 색상 금지.
- 프론트 작업 디렉터리는 `frontend/`. 테스트는 `cd frontend && CI=true npx react-scripts test --testPathPattern=<경로>`.

**API 계약 (이미 존재 — 변경 없음)**

```js
// frontend/src/api/chatApi.js:135
searchMessages(conversationIdx, keyword) -> Promise<ChatMessageDTO[]>
```

`ChatMessageDTO` 중 이 기능이 쓰는 필드 (`ChatRoom.js:691-714`에서 확인):

| 필드 | 타입 | 비고 |
|---|---|---|
| `idx` | number | React key |
| `senderIdx` | number | 내 메시지 판별 |
| `senderUsername` | string | 보낸사람 표시 |
| `content` | string | 본문. `messageType==='IMAGE'`면 이미지 URL |
| `createdAt` | string | ISO 날짜 |
| `messageType` | string | `'TEXT'` \| `'IMAGE'` 등 |

백엔드가 `created_at DESC`로 정렬해서 주므로 프론트 정렬은 없다.

---

## File Structure

| 파일 | 책임 |
|---|---|
| `frontend/src/components/Chat/ChatSearchPanel.js` (신규) | 검색 입력줄 + API 호출 + 결과 목록 + 하이라이트. props는 `conversationIdx`, `onClose` 둘뿐 |
| `frontend/src/components/Chat/ChatSearchPanel.test.js` (신규) | 위 컴포넌트 단위 테스트 |
| `frontend/src/components/Chat/ChatRoom.js` (수정) | `showSearch` 상태 · 헤더 🔍 버튼 · 조건부 렌더링. 3군데 |

---

### Task 1: ChatSearchPanel — 검색어 검증과 결과 렌더링

**Files:**
- Create: `frontend/src/components/Chat/ChatSearchPanel.js`
- Test: `frontend/src/components/Chat/ChatSearchPanel.test.js`

**Interfaces:**
- Consumes: `chatApi.searchMessages(conversationIdx, keyword)` (기존)
- Produces: `export default ChatSearchPanel` — props `{ conversationIdx: number, onClose: () => void }`

- [ ] **Step 1: Write the failing test**

`frontend/src/components/Chat/ChatSearchPanel.test.js` 생성:

```jsx
// frontend/src/components/Chat/ChatSearchPanel.test.js
import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
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

  await waitFor(() => expect(screen.getByText('1건')).toBeInTheDocument());
  expect(searchMessages).toHaveBeenCalledWith(1, '산책');
  expect(screen.getByText('김철수')).toBeInTheDocument();

  const mark = document.querySelector('mark');
  expect(mark).not.toBeNull();
  expect(mark.textContent).toBe('산책');
});

test('결과가 없으면 안내 문구를 보여준다', async () => {
  searchMessages.mockResolvedValue([]);
  wrap({});
  submit('산책');

  await waitFor(() =>
    expect(screen.getByText('검색 결과가 없습니다.')).toBeInTheDocument()
  );
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd frontend && CI=true npx react-scripts test --testPathPattern=ChatSearchPanel
```

Expected: FAIL — `Cannot find module './ChatSearchPanel'`

- [ ] **Step 3: Write minimal implementation**

`frontend/src/components/Chat/ChatSearchPanel.js` 생성:

```jsx
// frontend/src/components/Chat/ChatSearchPanel.js
import React, { useState } from 'react';
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

  const handleSubmit = async (e) => {
    e.preventDefault();
    const trimmed = keyword.trim();

    // ngram_token_size=2 라 1글자는 서버가 무조건 0건을 돌려준다. 왕복을 아낀다.
    if (trimmed.length < MIN_KEYWORD_LENGTH) {
      setResults(null);
      setNotice('2글자 이상 입력해주세요.');
      return;
    }

    setNotice('');
    setLoading(true);
    try {
      const data = await searchMessages(conversationIdx, trimmed);
      setResults(Array.isArray(data) ? data : []);
      setSearchedKeyword(trimmed);
    } catch (error) {
      setResults(null);
      setNotice('검색에 실패했습니다. 다시 시도해주세요.');
    } finally {
      setLoading(false);
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
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd frontend && CI=true npx react-scripts test --testPathPattern=ChatSearchPanel
```

Expected: PASS — `Tests: 3 passed, 3 total`

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/Chat/ChatSearchPanel.js frontend/src/components/Chat/ChatSearchPanel.test.js
git commit -m "feat(chat): 메시지 검색 패널 컴포넌트 추가

2글자 미만은 요청을 보내지 않는다 — ngram_token_size=2 라 서버가
무조건 0건을 돌려준다. 하이라이트는 문자열을 배열로 쪼개 React 가
이스케이프하게 둔다(dangerouslySetInnerHTML 미사용).

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: ChatRoom 진입점 — 헤더 🔍 버튼

**Files:**
- Modify: `frontend/src/components/Chat/ChatRoom.js` (import 추가 / `showSearch` 상태 / `HeaderActions` 버튼 / `return` 최상단 분기)

**Interfaces:**
- Consumes: `ChatSearchPanel` — props `{ conversationIdx, onClose }` (Task 1)
- Produces: 없음 (최종 통합 지점)

- [ ] **Step 1: import 추가**

`ChatRoom.js` 10번째 줄 `import { geocodingApi } from '../../api/geocodingApi';` 바로 아래에 추가:

```jsx
import ChatSearchPanel from './ChatSearchPanel';
```

- [ ] **Step 2: 상태 추가**

`ChatRoom.js:22` `const [showMenu, setShowMenu] = useState(false);` 바로 아래에 추가:

```jsx
  const [showSearch, setShowSearch] = useState(false);
```

- [ ] **Step 3: 헤더에 🔍 버튼 추가**

`ChatRoom.js:670-671`의 `<HeaderActions>` 시작 부분을 아래로 교체한다. `MenuButton` 스타일을 그대로 재사용하므로 새 styled 컴포넌트를 만들지 않는다.

교체 전:

```jsx
        <HeaderActions>
          <MenuButton onClick={() => setShowMenu(!showMenu)}>⋮</MenuButton>
```

교체 후:

```jsx
        <HeaderActions>
          <MenuButton onClick={() => setShowSearch(true)} aria-label="메시지 검색">🔍</MenuButton>
          <MenuButton onClick={() => setShowMenu(!showMenu)}>⋮</MenuButton>
```

- [ ] **Step 4: 패널을 오버레이로 렌더링**

패널이 `position: absolute; inset: 0`이라 헤더를 조건부로 숨길 필요가 없다. `<Header>` 블록은 그대로 두고, 그 닫는 태그(`ChatRoom.js:682`) 바로 뒤에 패널을 추가한다.

교체 전 (682-684행):

```jsx
      </Header>

      <MiddleColumn>
```

교체 후:

```jsx
      </Header>

      {showSearch && (
        <ChatSearchPanel
          conversationIdx={conversationIdx}
          onClose={() => setShowSearch(false)}
        />
      )}

      <MiddleColumn>
```

이러면 메시지 목록·WebSocket 구독이 언마운트되지 않고 패널 뒤에 그대로 남는다(스펙 §3-8). 패널을 형제 flex 아이템으로 넣으면 `MiddleColumn`과 둘 다 `flex: 1`이라 화면을 반씩 나눠 갖는다 — 그래서 오버레이로 간다.

- [ ] **Step 5: 빌드가 깨지지 않는지 확인**

```bash
cd frontend && CI=true npx react-scripts build
```

Expected: `Compiled successfully.` (경고는 무방, 에러 0)

- [ ] **Step 6: 프론트 테스트 전체 실행**

```bash
cd frontend && CI=true npx react-scripts test
```

Expected: 기존 `LocationControls` 테스트 + 신규 `ChatSearchPanel` 3건 전부 PASS, 실패 0

- [ ] **Step 7: 실제 앱에서 동작 확인**

MySQL·Redis가 떠 있어야 한다. 백엔드 `./gradlew bootRun`, 프론트 `cd frontend && npm start` 후 브라우저에서:

1. 로그인 → 채팅방 진입 → 헤더에 🔍가 보인다
2. 🔍 클릭 → 헤더가 검색 입력줄로 바뀌고 커서가 잡힌다
3. 1글자 입력 후 엔터 → "2글자 이상 입력해주세요." 표시, 네트워크 탭에 요청 **없음**
4. 2글자 한글(예: 방에 있는 단어) 입력 후 엔터 → 건수와 결과가 뜨고 키워드가 강조된다
   - **이게 V12의 실증이다.** V12 이전이면 2글자는 0건이었다.
5. Esc 또는 ✕ → 평상 헤더로 복귀, 메시지 목록 그대로

- [ ] **Step 8: Commit**

```bash
git add frontend/src/components/Chat/ChatRoom.js
git commit -m "feat(chat): 채팅방 헤더에 메시지 검색 진입점 추가

ChatRoom 은 showSearch 상태와 버튼만 갖고 검색 동작은 모른다.
1,624줄짜리 파일이라 검색 로직은 ChatSearchPanel 로 분리했다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 3: 문서 현행화

**Files:**
- Modify: `docs/domains/chat.md` (§5.6 메시지 검색)

**Interfaces:**
- Consumes: Task 1·2의 완성된 기능
- Produces: 없음

- [ ] **Step 1: 현재 서술 확인**

```bash
sed -n '195,215p' docs/domains/chat.md
```

- [ ] **Step 2: §5.6에 프론트 진입점과 파서 전제를 명시**

`### 5.6 메시지 검색` 섹션의 마지막 줄 뒤, **다음 `###` 헤딩 앞**에 아래 문단을 삽입한다. 기존 서술은 지우지 않는다 — API 설명은 그대로 유효하다.

```markdown
**프론트 진입점** — 채팅방 헤더 🔍 버튼 → `ChatSearchPanel`
(`frontend/src/components/Chat/ChatSearchPanel.js`). 결과는 목록으로만 보여주고
해당 메시지로 이동하지 않는다. 메시지 목록이 최신 100건만 로드하는 구조라
(`ChatRoom.js:108`) 이동하려면 특정 메시지 앞뒤 구간을 불러오는 API가 따로 필요하다.

**2글자 검색 전제** — `chatmessage` FULLTEXT 는 `V12` 에서 ngram 파서로 전환됐다.
그 전(기본 파서)에는 `innodb_ft_min_token_size=3` 이라 2글자 한글이 색인되지 않아
검색이 항상 0건이었다. 클라이언트는 1글자 요청을 보내지 않는다 —
`ngram_token_size=2` 라 서버가 무조건 0건을 돌려주기 때문이다.
```

- [ ] **Step 3: Commit**

```bash
git add docs/domains/chat.md
git commit -m "docs(chat): 메시지 검색 프론트 진입점과 ngram 전제 반영

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## 완료 기준

- [ ] `cd frontend && CI=true npx react-scripts test` — 실패 0
- [ ] `cd frontend && CI=true npx react-scripts build` — 에러 0
- [ ] 실제 앱에서 2글자 한글 검색이 결과를 낸다 (Task 2 Step 7)
- [ ] 이 계획의 세 커밋이 `backend/` 를 건드리지 않는다 — `git show --stat` 으로 확인 (V12·회귀 테스트는 이 계획 **이전**에 이미 있던 변경분이다)
