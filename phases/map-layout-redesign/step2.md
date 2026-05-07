# Step 2: App.js — ContentArea margin 수정

## 목표

`frontend/src/App.js` 의 `ContentArea` styled component에서 `margin-left: 240px` (사이드바 폭 회피)를 제거하고 `margin-top: 60px` (TopBar 높이 회피)로 교체한다.

## 변경 대상 파일

- **Modify**: `frontend/src/App.js`

## 전제

Step 1 완료 후 실행한다. Navigation이 TopBar (height: 60px, position: fixed)로 변경되어 있어야 한다.

---

## 변경 내용

### `ContentArea` styled component

기존:
```js
const ContentArea = styled.main`
  margin-left: 240px;
  flex: 1;
  min-height: 100vh;
  background: ${props => props.theme.colors.background};

  @media (max-width: 768px) {
    margin-left: 0;
    padding-bottom: 60px;
  }
`;
```

교체:
```js
const ContentArea = styled.main`
  margin-left: 0;
  margin-top: 60px;
  flex: 1;
  min-height: calc(100vh - 60px);
  background: ${props => props.theme.colors.background};

  @media (max-width: 768px) {
    margin-top: 0;
    padding-bottom: 60px;
    min-height: calc(100vh - 60px);
  }
`;
```

변경 이유:
- 데스크톱: TopBar(60px) 아래부터 콘텐츠 시작 (`margin-top: 60px`, `min-height: calc(100vh - 60px)`)
- 모바일: TopBar가 `display: none`이므로 `margin-top: 0`. BottomNav(60px)를 위해 `padding-bottom: 60px` 유지.

---

## 검증

```bash
cd /Users/maknkkong/project/Petory/frontend
npm start
```

브라우저에서 확인:
- 모든 페이지(홈, 커뮤니티, 탐색 등)에서 콘텐츠가 TopBar에 가리지 않고 60px 아래부터 시작
- 모바일에서 콘텐츠가 BottomNav에 가리지 않음
- `UnifiedPetMapPage`의 `height: 100vh`가 이제 `100vh - 60px` 영역에서 렌더링됨을 확인
  - `UnifiedPetMapPage`의 `PageWrapper`가 `height: 100vh`인데, ContentArea의 `margin-top: 60px` + `min-height: calc(100vh - 60px)` 덕분에 페이지가 스크롤 없이 꽉 참

> **참고**: `UnifiedPetMapPage.js`의 `PageWrapper`는 `height: 100vh`로 정의되어 있다. ContentArea에 `margin-top: 60px`이 붙으면 TopBar와 PageWrapper가 겹치지 않게 된다. PageWrapper 자체는 수정하지 않아도 된다.
