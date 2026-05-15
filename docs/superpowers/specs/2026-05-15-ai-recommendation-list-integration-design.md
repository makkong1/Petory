# AI 추천 시설 목록 통합 디자인

- **일자**: 2026-05-15
- **관련 파일**: `UnifiedPetMapPage.js`, `RecommendCard.js`

## 배경

현재 `RecommendCard`는 pet-data-api(카카오/블로그 데이터)에서 받은 AI 추천 시설을 카드 내부에 독립적으로 렌더링한다. 기존 Petory DB 시설 목록(`LeftPanelResults`)은 별도로 위에 있어, 사용자 눈에 AI 카드가 아래에 붙으면서 기존 목록이 밀려 보이는 문제가 있다. 두 데이터 소스를 하나의 목록에서 배지로 구분하여 표시한다.

## 목표

- AI 추천 시설과 Petory DB 시설을 `LeftPanelResults` 단일 목록에 표시
- AI 항목: 왼쪽 primary 컬러바 + `[AI]` 텍스트 배지로 시각 구분
- Petory DB 항목: 기존 스타일 그대로 유지

## 변경 범위

### 1. `RecommendCard.js`

- 내부 `FacilityItem` 목록 렌더링 제거
- LLM 카피 텍스트(`RecommendQuote`) + "AI 추천" 라벨만 유지
- `onFacilitiesLoaded` 콜백은 유지 — 데이터는 계속 부모에 전달
- 제거 대상 styled-components: `FacilityList`, `FacilityItem`, `FacilityHeader`, `FacilityName`, `FacilityScore`, `FacilityMeta`, `MetaDot`, `SourceBadge`, `MentionInline`, `ReasonRow`, `ReasonChip`
- 제거 대상 helper functions: `reasonLabel`, `isTrendReason`, `sourceLabel`
- 제거 대상 JSX: `<Title>주변 시설</Title>` + `<FacilityList>` 블록 (line 336~385)

### 2. `UnifiedPetMapPage.js`

**데이터 병합**
- `aiRecommendFacilities`(pet-data-api 결과)를 `items`에 병합
- 각 AI 항목에 `isAiRecommend: true` 플래그 추가
- 목록 순서: AI 추천 항목 먼저(거리순) → Petory DB 항목(기존 정렬 그대로)

**스타일**
- `ResultCard`에 `$isAiRecommend` prop 추가
- AI 항목: `border-left: 3px solid ${theme.colors.primary}` + `[AI]` 배지
- Petory 항목: 변경 없음

**렌더링 예시**
```
┃ 니니애견미용실             [AI]
┃ 763m · 추천도 67% · 블로그 6건

  서울 강남구 애견샵
  900m · 강남구 역삼동
```

## 제외 범위

- `RecommendCard`의 LLM 카피(`RecommendQuote`) 스타일 변경 없음
- 지도 마커 색상 변경 없음 (AI 항목 마커 이미 `#FFD700`으로 구분)
- Petory DB 항목 정렬 로직 변경 없음

## 검증 기준

1. AI 카테고리 선택 후 목록에 AI 항목(컬러바+배지)과 Petory 항목이 함께 보임
2. AI 항목 클릭 시 지도 마커 선택 동작
3. `RecommendCard` 내부에 시설 목록이 사라지고 LLM 카피만 표시
4. AI 카테고리 없는 경우 기존 목록 동작 그대로
