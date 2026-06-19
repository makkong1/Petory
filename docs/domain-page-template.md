# DomainV2 페이지 템플릿 — 구조 레퍼런스

> `src/pages/projects/petory/domains/` 아래 모든 `*DomainV2.jsx` 파일의 공통 구조를 정리한 문서.
> 새 도메인 페이지를 추가하거나 기존 페이지를 수정할 때 이 템플릿을 기준으로 삼는다.

---

## 1. 파일 목록

| 파일 | 도메인 이름 | 핵심 기능 태그 |
|---|---|---|
| `BoardDomainV2.jsx` | 게시판 도메인 | 목록 N+1 최적화, 반응 토글, 조회수 중복 방지, 인기글 스냅샷, FULLTEXT 검색 |
| `UserDomainV2.jsx` | 유저 도메인 | JWT 인증, OAuth 계정 연결, Pet 소유 검증, 제재 상태 동기화, 보안 트레이드오프 |
| `ChatDomainV2.jsx` | 채팅 도메인 | 채팅 생성 규칙 중앙화, unread count 원자적 갱신, 읽음 처리 최적화, 재참여 메시지 제한, 참여자 N+1 개선 |
| `LocationDomainV2.jsx` | 위치 서비스 도메인 | 위치 우선 검색 분기, sort=stable 추천순, 반경·size=300, 지도 「이 지역」, JSON·CSV 적재, 목록/추천 API 분리 |
| `CareDomainV2.jsx` | Care 도메인 | Race Condition 제어, N+1 최적화, 에스크로 연동, 처리 경로 일원화, 위치 기반 조회 |
| `MeetupDomainV2.jsx` | 모임 도메인 | 참가 동시성 제어, 이벤트 기반 채팅방 분리, 근처 모임 2단계 조회, 히스토리 N+1 제거, 참여 가능 목록 단순화 |
| `MissingPetDomainV2.jsx` | 실종 제보 도메인 | 조인 폭발 방지, 댓글 일괄삭제 최적화, 채팅 연결 경량화, 관리자 DB 필터링, 서비스 레이어 권한 검증 |
| `RecommendationDomainV2.jsx` | 반려동물 추천 도메인 | 비동기 intent signal, Python NLP 분석, 형태소 정밀 매칭, 원문 텍스트 미저장, 추천 카드 /signals, Location 카테고리 연결, NLP 호출·부하 제어, 본 기능 무영향 장애 처리 |

---

## 2. 각 파일의 공통 구조

### 2-1. 상단 import & 로컬 컴포넌트

모든 파일에 동일하게 복사되어 있음 (공유 파일 아님):

```jsx
import { Link } from 'react-router-dom';
import TableOfContents from '../../../../components/Common/TableOfContents';

function Card({ children, style }) { ... }    // 카드 박스
function CodeBlock({ children }) { ... }      // 코드 블럭 pre
```

`Card`와 `CodeBlock`은 8개 파일에 모두 중복 정의되어 있다.
공통 컴포넌트로 추출할 후보. 현재는 각 파일이 독립적으로 보유.

### 2-2. GitHub 링크 상수

파일 최상단에 관련 GitHub 문서 URL을 const로 선언:

```jsx
const PETORY_BOARD_DOMAIN_DOC = 'https://github.com/makkong1/Petory/blob/main/...';
const PETORY_BOARD_PERF_DOC   = 'https://github.com/makkong1/Petory/blob/main/...';
```

### 2-3. 컴포넌트 내부 데이터

```jsx
function XxxDomainV2() {
  // TableOfContents에 넘기는 섹션 목록 — 순서 고정
  const sections = [
    { id: 'pillars', title: '핵심 기능' },
    { id: 'intro',   title: '도메인 개요' },
    { id: 'design',  title: '기술 결정' },
    { id: 'limits',  title: '한계 & 개선' },
    { id: 'docs',    title: '관련 페이지' },
  ];

  // 상단 chip 태그 배열
  const corePillars = ['태그1', '태그2', ...];

  // bullet 아이템 헬퍼
  const li = (text) => <li style={{ marginBottom: '0.35rem' }}>• {text}</li>;
```

---

## 3. JSX 레이아웃 골격

```
<div className="domain-page-wrapper">           ← 외부 패딩 래퍼
  <div className="domain-page-container">        ← flex row
    <div className="domain-page-content">        ← 좌측 본문 (flex: 1)

      <h1>도메인 이름</h1>
      <p>한 문단 도메인 소개</p>

      <section id="pillars">   ← 핵심 기능 chip 태그들
      <section id="intro">     ← 도메인 개요
      <section id="design">    ← 기술 결정
      <section id="limits">    ← 한계 & 다음 개선
      <section id="docs">      ← 관련 페이지 링크

    </div>
    <TableOfContents sections={sections} />      ← 우측 TOC
  </div>
</div>
```

---

## 4. 섹션별 내용 규칙

### section#pillars — 핵심 기능

`corePillars` 배열을 `span` chip으로 렌더링. 스타일 고정.

### section#intro — 도메인 개요

3개 카드로 구성:
1. **구조 테이블** — `항목 | 현재 구조` 2열 (도메인마다 내용 다름)
2. **성능 테이블** — `지표 | Before | After` 3열, 측정 조건 주석 포함
3. **데이터 흐름** — 시퀀스 다이어그램 링크 (`/domains/flows?tab=xxx`)

> 일부 도메인은 구조 테이블 대신 설명 문단을 사용한다.

### section#design — 기술 결정

카드를 A, B, C, D, E ... 알파벳 순서로 나열.  
각 카드 구조:

```jsx
<Card>
  <h3>A. 결정 제목</h3>
  <ul>
    {li('결정 근거 또는 구현 방법')}
    {li('추가 설명')}
  </ul>
  <CodeBlock>{`// 실제 코드 스니펫`}</CodeBlock>   {/* 선택 */}
</Card>
```

### section#limits — 한계 & 다음 개선

단일 카드, `li()` 목록으로 현재 한계점 나열.  
일부 도메인은 서두 설명 `<p>` 포함.

### section#docs — 관련 페이지

단일 카드, `<li>` 목록:
- 내부 링크: `<Link to="/domains/xxx">` (React Router)
- 외부 링크: `<a href={CONST} target="_blank">` (GitHub)

---

## 5. Optimization / Refactoring 파일

`*DomainV2.jsx` 옆에 나란히 존재:

| 파일 패턴 | 용도 |
|---|---|
| `*DomainOptimization.jsx` | N+1·인덱스 등 성능 최적화 상세 Before/After |
| `*DomainRefactoring.jsx` | 중복 제거, 코드 구조 변경 기록 |

구조는 V2와 동일한 Card + CodeBlock + TableOfContents 패턴 사용.

---

## 6. 라우팅 규칙 (참고)

| 도메인 | V2 경로 | Optimization | Refactoring |
|---|---|---|---|
| board | `/domains/board` | `/domains/board/optimization` | `/domains/board/refactoring` |
| user | `/domains/user` | `/domains/user/optimization` | `/domains/user/refactoring` |
| chat | `/domains/chat` | `/domains/chat/optimization` | `/domains/chat/refactoring` |
| location | `/domains/location` | `/domains/location/optimization` | `/domains/location/refactoring` |
| care | `/domains/care` | `/domains/care/optimization` | `/domains/care/refactoring` |
| meetup | `/domains/meetup` | `/domains/meetup/optimization` | `/domains/meetup/refactoring` |
| missing-pet | `/domains/missing-pet` | `/domains/missing-pet/optimization` | `/domains/missing-pet/refactoring` |
| recommendation | `/domains/recommendation` | `/domains/recommendation/optimization` | `/domains/recommendation/refactoring` |

시퀀스 다이어그램은 `/domains/flows?tab=<도메인명>` 통합 페이지.

---

## 7. 새 도메인 페이지 추가 체크리스트

1. `*DomainV2.jsx` 파일 생성 — 위 골격 복사 후 내용 채우기
2. `sections`, `corePillars`, GitHub 링크 상수 정의
3. `#pillars` → chip 태그
4. `#intro` → 구조 테이블 + 성능 테이블 + 흐름 링크
5. `#design` → A~E 결정 카드 + 코드 스니펫
6. `#limits` → 한계점 목록
7. `#docs` → 내·외부 링크
8. `App.jsx` (또는 라우터 파일)에 경로 등록
9. Navigation에 메뉴 항목 추가
