# Step 3: UnifiedPetMapPage.js — ContentRow + LeftPanel 통합

## 목표

`frontend/src/components/UnifiedMap/UnifiedPetMapPage.js` 를 수정하여 데스크톱에서 **LeftPanel(검색 컨트롤 + 결과 목록) + MapWrapper(지도)** 수평 2단 레이아웃으로 전환한다. 기존 `ControlsOverlay`(지도 위 float 오버레이)와 `LocationResultSheet`(좌하단 absolute 결과 시트)는 모바일 전용으로 유지한다.

## 변경 대상 파일

- **Modify**: `frontend/src/components/UnifiedMap/UnifiedPetMapPage.js`

## 전제

Step 1, 2 완료 후 실행한다.

---

## 변경 내용

### A. JSX 구조 변경

#### 기존 return JSX (간략):
```jsx
<PageWrapper>
  <DomainTabHeader ... />
  <MapWrapper>
    <MapContainer ... />
    <ControlsOverlay>
      <OverlayRow><RadiusFilter /></OverlayRow>
      {renderLayerControls()}
    </ControlsOverlay>
    <MyLocationFAB />
    {loading && <LoadingBar />}
    {/* CountChip, ErrorBanner, EmptyBanner */}
    {renderInfoPanel()}
    {renderLocationResults()}
  </MapWrapper>
  {activeLayer === 'location' && userLocation && ... && <RecommendCard />}
  {showMeetupCreateModal && <MeetupCreateModal />}
  {showCareCreateModal && <CareCreateModal />}
</PageWrapper>
```

#### 교체 후 return JSX (전체):
```jsx
return (
  <PageWrapper>
    <DomainTabHeader activeLayer={activeLayer} onTabChange={handleTabChange} />

    <ContentRow>
      {/* ── 데스크톱 전용 좌측 패널 ── */}
      <LeftPanel>
        <LeftPanelTop>
          <RadiusFilter radius={radius} onRadiusChange={handleRadiusChange} />
          {renderLayerControls()}
        </LeftPanelTop>

        {/* location 탭: 결과 목록 */}
        {activeLayer === 'location' && (
          <LeftPanelResults>
            {loading && <PanelStatusMsg>검색 중...</PanelStatusMsg>}
            {!loading && !error && items.length === 0 && mapViewportCenter && (
              <PanelStatusMsg>반경 {radius}km 내 결과가 없습니다.</PanelStatusMsg>
            )}
            {!loading && !error && items.length > 0 && (
              <>
                <PanelResultHeader>
                  <div>
                    <PanelResultTitle>주변 시설</PanelResultTitle>
                    <PanelResultSubtitle>
                      {searchMode === 'initial' ? '초기 검색' : '현재 검색 기준'} · 반경 {radius}km · {SORT_LABELS[locationSort]}
                    </PanelResultSubtitle>
                  </div>
                  <PanelResultCount>{items.length}개</PanelResultCount>
                </PanelResultHeader>
                <PanelResultList>
                  {items.map((item, index) => {
                    const isSelected = selectedItem?.id === item.id;
                    const isRecommended = recommendedMap?.has(item.idx);
                    return (
                      <ResultCard
                        key={item.id}
                        type="button"
                        $selected={isSelected}
                        onClick={() => handleLocationResultClick(item)}
                        onMouseEnter={() => setHoveredLocationItem(item)}
                        onMouseLeave={() => setHoveredLocationItem(current =>
                          current?.id === item.id ? null : current
                        )}
                      >
                        <ResultCardTop>
                          <ResultCardTitle>
                            {isRecommended && (
                              <ResultRankBadge>TOP {recommendedMap.get(item.idx)}</ResultRankBadge>
                            )}
                            {item.title || item.name || `시설 ${index + 1}`}
                          </ResultCardTitle>
                          {item.raw?.distance != null && (
                            <ResultDistance>{Math.round(item.raw.distance)}m</ResultDistance>
                          )}
                        </ResultCardTop>
                        <ResultCardSubtitle>
                          {item.subtitle || item.raw?.address || '주소 정보 없음'}
                        </ResultCardSubtitle>
                      </ResultCard>
                    );
                  })}
                </PanelResultList>
              </>
            )}
          </LeftPanelResults>
        )}

        {/* AI 추천 카드 — location 탭 + 카테고리 선택 시 */}
        {activeLayer === 'location' && userLocation && CATEGORY_TO_CONTEXT[locationCategory] && (
          <RecommendCard
            lat={userLocation.lat}
            lng={userLocation.lng}
            context={CATEGORY_TO_CONTEXT[locationCategory]}
            onFacilitiesLoaded={setAiRecommendFacilities}
          />
        )}
      </LeftPanel>

      {/* ── 지도 영역 ── */}
      <MapWrapper>
        {mapViewportCenter ? (
          <MapContainer
            services={[
              ...items,
              ...aiRecommendFacilities.map((f, i) => ({
                id: `ai-${i}`,
                type: 'ai_recommend',
                latitude: f.lat,
                longitude: f.lng,
                name: f.name,
                title: f.name,
                subtitle: f.address,
                distanceM: f.distance_m,
                markerColor: '#FFD700',
              })),
            ]}
            onServiceClick={setSelectedItem}
            userLocation={userLocation}
            mapCenter={mapViewportCenter}
            mapLevel={mapLevel}
            selectedService={selectedItem}
            hoveredService={hoveredLocationItem}
            recommendedServiceIdxs={recommendedMap}
            onMapIdle={handleMapIdle}
          />
        ) : (
          <MapInitLoading>🗺️ 위치 정보를 가져오는 중...</MapInitLoading>
        )}

        {/* 모바일 전용 컨트롤 오버레이 (데스크톱은 LeftPanel로 대체) */}
        <ControlsOverlay>
          <OverlayRow>
            <RadiusFilter radius={radius} onRadiusChange={handleRadiusChange} />
          </OverlayRow>
          {renderLayerControls()}
        </ControlsOverlay>

        <MyLocationFAB
          onClick={handleMoveToMyLocation}
          disabled={locating}
          title="내 위치로 이동"
          aria-label="내 위치로 이동"
        >
          <span aria-hidden="true">{locating ? '⏳' : '📍'}</span>
        </MyLocationFAB>

        {loading && <LoadingBar aria-label={isAiMode ? 'AI 추천 중' : '데이터 조회 중'} />}

        {!loading && mapViewportCenter && activeLayer !== 'location' && (
          <CountChip>
            {isAiMode && <AiBadge>✨ AI</AiBadge>}
            반경 <strong>{radius}km</strong> · <strong>{items.length}</strong>개
          </CountChip>
        )}

        {error && !loading && (
          <ErrorBanner onClick={() => setError(null)}>{error} ✕</ErrorBanner>
        )}

        {!loading && !error && items.length === 0 && mapViewportCenter && (
          <EmptyBanner>반경 {radius}km 내 결과가 없습니다.</EmptyBanner>
        )}

        {renderInfoPanel()}
        {renderLocationResults()}
      </MapWrapper>
    </ContentRow>

    {showMeetupCreateModal && (
      <MeetupCreateModal
        onClose={() => setShowMeetupCreateModal(false)}
        onSuccess={handleMeetupCreated}
      />
    )}
    {showCareCreateModal && (
      <CareCreateModal
        onClose={() => setShowCareCreateModal(false)}
        onSuccess={handleCareCreated}
      />
    )}
  </PageWrapper>
);
```

**기존 JSX에서 삭제된 부분:**
- `<RecommendCard>` 가 `<PageWrapper>` 직속 자식이었던 것 → `<LeftPanel>` 안으로 이동
- `<ControlsOverlay>` 는 그대로지만 styled component에 모바일 전용 CSS 추가

---

### B. 기존 Styled Components 수정

#### 1. `ControlsOverlay` — 데스크톱에서 숨김 추가

기존 CSS 맨 앞에 아래 라인 추가:
```js
const ControlsOverlay = styled.div`
  /* 데스크톱: LeftPanel이 대체하므로 숨김 */
  @media (min-width: 769px) {
    display: none;
  }

  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 200;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  background: ${props => props.theme.colors.surface + 'E8'};
  border-bottom: 1px solid ${props => props.theme.colors.border};
  box-shadow: 0 2px 12px ${props => props.theme.colors.shadow};
  pointer-events: none;
  > * { pointer-events: auto; }
`;
```

#### 2. `LocationResultSheet` — 데스크톱에서 숨김 추가

기존 CSS 맨 앞에 아래 라인 추가:
```js
const LocationResultSheet = styled.section`
  /* 데스크톱: LeftPanel 내 결과 목록이 대체하므로 숨김 */
  @media (min-width: 769px) {
    display: none;
  }

  position: absolute;
  left: 16px;
  width: 392px;
  bottom: 16px;
  z-index: 230;
  top: 236px;
  /* ... 나머지 기존 스타일 그대로 ... */
`;
```

#### 3. `PageWrapper` — 수정 불필요

현재 `height: 100vh; flex-direction: column`으로 정의되어 있다. `ContentArea`에 `margin-top: 60px`이 적용된 후에도 PageWrapper 자체는 변경 없이 잘 동작한다.

---

### C. 새로 추가할 Styled Components

기존 styled components 블록 끝(파일 맨 아래)에 아래 컴포넌트들을 추가한다:

```js
/* ── 데스크톱 2단 레이아웃 ── */

const ContentRow = styled.div`
  display: flex;
  flex-direction: row;
  flex: 1;
  overflow: hidden;
`;

const LeftPanel = styled.aside`
  width: 320px;
  flex-shrink: 0;
  border-right: 1px solid ${props => props.theme.colors.border};
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: ${props => props.theme.colors.surface};

  /* 모바일: 숨김 (ControlsOverlay + LocationResultSheet 사용) */
  @media (max-width: 768px) {
    display: none;
  }
`;

const LeftPanelTop = styled.div`
  flex-shrink: 0;
  border-bottom: 1px solid ${props => props.theme.colors.border};
`;

const LeftPanelResults = styled.div`
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
`;

const PanelResultHeader = styled.div`
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 14px 16px 10px;
  border-bottom: 1px solid rgba(120, 113, 108, 0.14);
  flex-shrink: 0;
`;

const PanelResultTitle = styled.h3`
  margin: 0;
  font-size: 15px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: ${props => props.theme.colors.text};
`;

const PanelResultSubtitle = styled.p`
  margin: 4px 0 0;
  font-size: 11px;
  color: ${props => props.theme.colors.textSecondary};
`;

const PanelResultCount = styled.span`
  font-size: 12px;
  font-weight: 700;
  color: ${props => props.theme.colors.textSecondary};
  flex-shrink: 0;
`;

const PanelResultList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  overflow-y: auto;
  padding: 12px 14px 16px;
`;

const PanelStatusMsg = styled.div`
  padding: 28px 16px;
  text-align: center;
  color: ${props => props.theme.colors.textSecondary};
  font-size: 13px;
`;
```

---

## 검증

```bash
cd /Users/maknkkong/project/Petory/frontend
npm start
```

브라우저 데스크톱(> 768px)에서 확인:
1. 탐색 탭 진입 시 좌측 320px 패널 + 우측 지도 2단 배치 확인
2. 좌측 패널 상단: 반경 필터 + 검색창 + 카테고리 칩 + 정렬 드롭다운
3. 좌측 패널 하단: 주변 시설 결과 목록 (스크롤 가능)
4. 지도 위에 ControlsOverlay가 더 이상 float하지 않음
5. location 탭에서 시설 클릭 시 지도가 해당 위치로 이동 + 우하단 상세 패널 열림
6. meetup/care 탭에서 지도 위 CountChip(결과 수) 표시 확인
7. 모바일(< 768px): LeftPanel 숨김, 기존 ControlsOverlay + LocationResultSheet 정상 작동 확인
