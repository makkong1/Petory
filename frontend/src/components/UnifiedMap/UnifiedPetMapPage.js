import React, { useState, useEffect, useCallback, useRef } from "react";
import styled, { keyframes } from "styled-components";
import MapContainer from "../LocationService/MapContainer";
import DomainTabHeader from "./DomainTabHeader";
import RadiusFilter from "./RadiusFilter";
import LocationControls from "./controls/LocationControls";
import MeetupControls from "./controls/MeetupControls";
import CareControls from "./controls/CareControls";
import MeetupCreateModal from "./MeetupCreateModal";
import CareCreateModal from "./CareCreateModal";
import LocationLayer from "./layers/LocationLayer";
import MeetupLayer from "./layers/MeetupLayer";
import CareLayer from "./layers/CareLayer";
import { fetchActiveMapItems } from "../../api/unifiedMapApi";
import { petRecommendationApi } from "../../api/petRecommendationApi";
import { geocodingApi } from "../../api/geocodingApi";

const SORT_LABELS = {
  stable: "추천순",
  distance: "거리순",
  rating: "평점순",
  reviews: "리뷰순",
};

const DEFAULT_CENTER = { lat: 37.5665, lng: 126.978 };
const DEFAULT_RADIUS = 5;
const EARTH_RADIUS_METERS = 6371000;
const MIN_SEARCH_AREA_THRESHOLD_METERS = 300;

const calculateMapLevelFromRadius = (radiusKm) => {
  if (radiusKm <= 1) return 5;
  if (radiusKm <= 3) return 6;
  if (radiusKm <= 5) return 7;
  if (radiusKm <= 10) return 8;
  return 9;
};

const hasValidCenter = (center) =>
  Number.isFinite(center?.lat) && Number.isFinite(center?.lng);

const haversineDistanceMeters = (a, b) => {
  const dLat = ((b.lat - a.lat) * Math.PI) / 180;
  const dLng = ((b.lng - a.lng) * Math.PI) / 180;
  const lat1 = (a.lat * Math.PI) / 180;
  const lat2 = (b.lat * Math.PI) / 180;
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;

  return EARTH_RADIUS_METERS * 2 * Math.asin(Math.sqrt(h));
};

const isSameCenter = (a, b, radiusKm) => {
  if (!hasValidCenter(a) || !hasValidCenter(b)) return false;
  const radiusMeters =
    typeof radiusKm === "number" && Number.isFinite(radiusKm)
      ? radiusKm * 1000
      : DEFAULT_RADIUS * 1000;
  const threshold = Math.max(
    MIN_SEARCH_AREA_THRESHOLD_METERS,
    radiusMeters * 0.1
  );

  return haversineDistanceMeters(a, b) < threshold;
};

const hasValidItemCoordinates = (item) =>
  Number.isFinite(item?.latitude) && Number.isFinite(item?.longitude);

const UnifiedPetMapPage = () => {
  const [activeLayer, setActiveLayer] = useState("location");
  const [mapViewportCenter, setMapViewportCenter] = useState(null);
  const [searchCenter, setSearchCenter] = useState(null);
  const [userLocation, setUserLocation] = useState(null);
  const [radius, setRadius] = useState(DEFAULT_RADIUS);
  const [mapLevel, setMapLevel] = useState(
    calculateMapLevelFromRadius(DEFAULT_RADIUS)
  );
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [selectedItem, setSelectedItem] = useState(null);
  const [hoveredLocationItem, setHoveredLocationItem] = useState(null);

  // location 탭 전용
  const [locationKeyword, setLocationKeyword] = useState("");
  const [locationCategory, setLocationCategory] = useState("");
  /** 소분류(카페·미술관 등)가 여러 중분류에 있을 때 선택한 중분류 id */
  const [locationCategoryGroupId, setLocationCategoryGroupId] = useState(null);
  const [locationSort, setLocationSort] = useState("stable");
  const [hasPendingAreaChange, setHasPendingAreaChange] = useState(false);
  const [searchMode, setSearchMode] = useState("initial");
  const [petIntentSignals, setPetIntentSignals] = useState([]);

  // meetup 탭 전용
  const [showMeetupCreateModal, setShowMeetupCreateModal] = useState(false);

  // care 탭 전용
  const [showCareCreateModal, setShowCareCreateModal] = useState(false);

  // 내 위치 버튼 로딩 상태
  const [locating, setLocating] = useState(false);

  const cacheRef = useRef({});
  const fetchTimerRef = useRef(null);
  const signalRefreshTimerRef = useRef(null);

  const commitLocationSearch = useCallback(
    (center, mode = "user-triggered") => {
      if (!hasValidCenter(center)) return;
      setSearchCenter({ ...center });
      setHasPendingAreaChange(false);
      setSearchMode(mode);
    },
    []
  );

  const refreshPetIntentSignals = useCallback(() => {
    petRecommendationApi.getSignals()
      .then(signals => setPetIntentSignals(signals))
      .catch(() => setPetIntentSignals([]));
  }, []);

  // 위치 취득 (iOS WebView·시뮬에서 콜백이 안 오는 경우 방지: 시간 지나면 서울 중심으로 진행)
  useEffect(() => {
    let cancelled = false;
    const applyDefaultCenter = () => {
      if (cancelled) return;
      setMapViewportCenter((prev) =>
        hasValidCenter(prev) ? prev : DEFAULT_CENTER
      );
      setSearchCenter((prev) => (hasValidCenter(prev) ? prev : DEFAULT_CENTER));
    };

    const fallbackMs = 10000;
    const fallbackTimer = window.setTimeout(applyDefaultCenter, fallbackMs);

    if (!navigator.geolocation) {
      window.clearTimeout(fallbackTimer);
      applyDefaultCenter();
      return () => {
        cancelled = true;
        window.clearTimeout(fallbackTimer);
      };
    }

    navigator.geolocation.getCurrentPosition(
      (pos) => {
        window.clearTimeout(fallbackTimer);
        if (cancelled) return;
        const loc = { lat: pos.coords.latitude, lng: pos.coords.longitude };
        setUserLocation(loc);
        setMapViewportCenter(loc);
        setSearchCenter(loc);
      },
      () => {
        window.clearTimeout(fallbackTimer);
        applyDefaultCenter();
      },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 60000 }
    );

    return () => {
      cancelled = true;
      window.clearTimeout(fallbackTimer);
    };
  }, []);

  // 데이터 조회 (디바운스 300ms)
  const fetchItems = useCallback(
    (type, center, r, keyword, category, sort, level = 7) => {
      if (!hasValidCenter(center)) return;
      clearTimeout(fetchTimerRef.current);
      fetchTimerRef.current = setTimeout(async () => {
        const cacheKeyParts = [
          type,
          center.lat.toFixed(4),
          center.lng.toFixed(4),
          r,
          keyword,
          category,
          sort,
        ];
        if (type !== "location") {
          cacheKeyParts.push(level);
        }
        const cacheKey = cacheKeyParts.join("-");
        if (cacheRef.current[cacheKey]) {
          setItems(cacheRef.current[cacheKey]);
          return;
        }
        setLoading(true);
        setError(null);
        setSelectedItem(null);
        try {
          const result = await fetchActiveMapItems({
            type,
            lat: center.lat,
            lng: center.lng,
            radius: r,
            keyword: type === "location" ? keyword : undefined,
            category: type === "location" ? category : undefined,
            sort: type === "location" ? sort : undefined,
            mapLevel: level,
          });
          cacheRef.current[cacheKey] = result;
          setItems(result);
        } catch (err) {
          console.error("[UnifiedPetMap] 조회 실패:", err);
          setError("데이터를 불러오지 못했습니다.");
          setItems([]);
        } finally {
          setLoading(false);
        }
      }, 300);
    },
    []
  );

  const effectiveFetchCenter =
    activeLayer === "location" ? searchCenter : mapViewportCenter;
  const effectiveMapLevel = activeLayer === "location" ? null : mapLevel;

  useEffect(() => {
    if (effectiveFetchCenter) {
      fetchItems(
        activeLayer,
        effectiveFetchCenter,
        radius,
        locationKeyword,
        locationCategory,
        locationSort,
        effectiveMapLevel
      );
    }
  }, [
    activeLayer,
    effectiveFetchCenter,
    radius,
    locationKeyword,
    locationCategory,
    locationSort,
    effectiveMapLevel,
    fetchItems,
  ]);

  useEffect(() => {
    if (activeLayer !== "location") return;
    let cancelled = false;
    petRecommendationApi.getSignals()
      .then(signals => {
        if (!cancelled) setPetIntentSignals(signals);
      })
      .catch(() => {
        if (!cancelled) setPetIntentSignals([]);
      });
    return () => {
      cancelled = true;
    };
  }, [activeLayer, locationKeyword, locationCategory]);

  useEffect(() => {
    if (activeLayer !== "location" || !locationKeyword.trim()) return undefined;
    window.clearTimeout(signalRefreshTimerRef.current);
    signalRefreshTimerRef.current = window.setTimeout(refreshPetIntentSignals, 1500);
    return () => window.clearTimeout(signalRefreshTimerRef.current);
  }, [activeLayer, locationKeyword, refreshPetIntentSignals]);

  useEffect(() => {
    const handler = (e) => {
      const { category, groupId } = e.detail || {};
      setActiveLayer('location');
      setLocationCategory(category || '동물병원');
      setLocationCategoryGroupId(groupId || 'medical');
    };
    window.addEventListener('navigateToHealthAlert', handler);
    return () => window.removeEventListener('navigateToHealthAlert', handler);
  }, []);

  const handleTabChange = (layer) => {
    setActiveLayer(layer);
    setSelectedItem(null);
    setHoveredLocationItem(null);
    if (layer !== "location") {
      setLocationKeyword("");
      setLocationCategory("");
      setLocationCategoryGroupId(null);
      setLocationSort("stable");
    }
    if (
      layer === "location" &&
      hasValidCenter(mapViewportCenter) &&
      !hasValidCenter(searchCenter)
    ) {
      setSearchCenter({ ...mapViewportCenter });
    }
    if (
      layer === "location" &&
      hasValidCenter(mapViewportCenter) &&
      hasValidCenter(searchCenter)
    ) {
      setHasPendingAreaChange(
        !isSameCenter(mapViewportCenter, searchCenter, radius)
      );
    }
    if (layer !== "location") {
      setHasPendingAreaChange(false);
    }
  };

  const handleRadiusChange = (r) => {
    setRadius(r);
    setMapLevel(calculateMapLevelFromRadius(r));
    setSelectedItem(null);
    cacheRef.current = {};
  };

  const handleSearchThisArea = useCallback(() => {
    commitLocationSearch(mapViewportCenter, "user-triggered");
  }, [commitLocationSearch, mapViewportCenter]);

  const handleMoveToMyLocation = () => {
    // 이미 위치를 알고 있으면 바로 이동
    if (userLocation) {
      setMapViewportCenter({ ...userLocation });
      commitLocationSearch(userLocation, "user-triggered");
      setSelectedItem(null);
      return;
    }

    // 위치 권한이 없거나 아직 취득 전이면 재시도
    if (!navigator.geolocation) {
      setError("이 브라우저는 위치 서비스를 지원하지 않습니다.");
      return;
    }

    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const loc = { lat: pos.coords.latitude, lng: pos.coords.longitude };
        setUserLocation(loc);
        setMapViewportCenter(loc);
        commitLocationSearch(loc, "user-triggered");
        setSelectedItem(null);
        setLocating(false);
      },
      (err) => {
        setLocating(false);
        if (err.code === err.PERMISSION_DENIED) {
          setError(
            "위치 권한이 거부되었습니다. 브라우저 설정에서 위치를 허용해주세요."
          );
        } else {
          setError(
            "현재 위치를 가져올 수 없습니다. 잠시 후 다시 시도해주세요."
          );
        }
      },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 }
    );
  };

  const handleMapIdle = useCallback(
    ({ lat, lng, level, isManualOperation }) => {
      const nextCenter = { lat, lng };
      setMapViewportCenter(nextCenter);
      if (level) setMapLevel(level);
      if (
        activeLayer === "location" &&
        isManualOperation &&
        hasValidCenter(searchCenter)
      ) {
        setHasPendingAreaChange(!isSameCenter(nextCenter, searchCenter, radius));
      }
    },
    [activeLayer, radius, searchCenter]
  );

  // 모임 생성 성공 시 목록 갱신
  const handleMeetupCreated = () => {
    cacheRef.current = {};
    fetchItems(
      "meetup",
      mapViewportCenter,
      radius,
      "",
      "",
      undefined,
      mapLevel
    );
  };

  // 케어 요청 생성 성공 시 목록 갱신
  const handleCareCreated = () => {
    cacheRef.current = {};
    fetchItems("care", mapViewportCenter, radius, "", "", undefined, mapLevel);
  };

  const displayItems = items;
  const mapServices = items;

  const handleLocationResultClick = useCallback((item) => {
    setSelectedItem(item);
    setHoveredLocationItem(item);
    if (hasValidItemCoordinates(item)) {
      setMapViewportCenter({ lat: item.latitude, lng: item.longitude });
    }
  }, []);

  const renderMobileBottomSheet = () => {
    if (activeLayer !== "location" && activeLayer !== "meetup" && activeLayer !== "care") {
      return null;
    }
    const sheetItems = activeLayer === "location" ? displayItems : items;
    const locationSubtitle =
      `${searchMode === "initial" ? "초기 검색" : "현재 검색 기준"} · 반경 ${radius}km · ${SORT_LABELS[locationSort]}`;
    const domainTitle = activeLayer === "meetup" ? "주변 모임" : "주변 펫케어";
    const domainSubtitle = `반경 ${radius}km · 지도 보는 위치 기준`;

    if (loading || error || sheetItems.length === 0) {
      return null;
    }

    return (
      <LocationResultSheet>
        <ResultSheetHandle aria-hidden="true" />
        <ResultSheetHeader>
          <div>
            <ResultSheetTitle>
              {activeLayer === "location" ? "주변 시설" : domainTitle}
            </ResultSheetTitle>
            <ResultSheetSubtitle>
              {activeLayer === "location" ? locationSubtitle : domainSubtitle}
            </ResultSheetSubtitle>
          </div>
          <ResultSheetMeta>{sheetItems.length}개</ResultSheetMeta>
        </ResultSheetHeader>
        <ResultList>
          {sheetItems.map((item, index) => {
            const isSelected = selectedItem?.id === item.id;
            return (
              <ResultCard
                key={item.id}
                type="button"
                $selected={isSelected}
                onClick={() => {
                  handleLocationResultClick(item);
                }}
                onMouseEnter={() => setHoveredLocationItem(item)}
                onMouseLeave={() =>
                  setHoveredLocationItem((current) =>
                    current?.id === item.id ? null : current
                  )
                }
              >
                <ResultCardTop>
                  <ResultCardTitle>
                    {item.title || item.name || `${activeLayer === "meetup" ? "모임" : activeLayer === "care" ? "케어" : "항목"} ${index + 1}`}
                  </ResultCardTitle>
                  {item.distanceM != null ? (
                    <ResultDistance>{item.distanceM}m</ResultDistance>
                  ) : (
                    item.raw?.distance != null && (
                      <ResultDistance>
                        {Math.round(item.raw.distance)}m
                      </ResultDistance>
                    )
                  )}
                </ResultCardTop>
                <ResultCardSubtitle>
                  {activeLayer === "location"
                    ? item.subtitle || item.raw?.address || "주소 정보 없음"
                    : item.subtitle ||
                      item.raw?.address ||
                      (item.raw?.description
                        ? String(item.raw.description).slice(0, 100).trim()
                        : "상세는 항목을 눌러 확인")}
                </ResultCardSubtitle>
              </ResultCard>
            );
          })}
        </ResultList>
      </LocationResultSheet>
    );
  };

  const renderLocationResults = () => renderMobileBottomSheet();

  const renderLayerControls = (showRadius = false) => {
    if (activeLayer === "location") {
      return (
        <LocationControls
          keyword={locationKeyword}
          category={locationCategory}
          intentSignals={petIntentSignals}
          activeGroupId={locationCategoryGroupId}
          sort={locationSort}
          hasPendingAreaChange={hasPendingAreaChange}
          radius={showRadius ? radius : undefined}
          onSearch={async (kw) => {
            if (!kw) {
              setLocationKeyword("");
              cacheRef.current = {};
              commitLocationSearch(mapViewportCenter, "user-triggered");
              return;
            }
            // geocoding 먼저 시도 — 주소·지역명(강남구, 묵동 등)이면 좌표로 변환해 지도 이동
            try {
              const geoResult = await geocodingApi.searchPlaces(kw);
              const first = geoResult?.results?.[0];
              if (first?.latitude && first?.longitude) {
                const loc = { lat: first.latitude, lng: first.longitude };
                setMapViewportCenter(loc);
                setLocationKeyword("");
                cacheRef.current = {};
                commitLocationSearch(loc, "geocoding");
                return;
              }
            } catch (_) {
              // geocoding 실패 시 keyword 검색으로 fallback
            }
            // fallback: 시설명 keyword 검색
            setLocationKeyword(kw);
            cacheRef.current = {};
            commitLocationSearch(mapViewportCenter, "keyword");
            window.clearTimeout(signalRefreshTimerRef.current);
            signalRefreshTimerRef.current = window.setTimeout(refreshPetIntentSignals, 1500);
          }}
          onCategoryPick={({ category: cat, groupId }) => {
            setLocationCategory(cat || "");
            setLocationCategoryGroupId(cat ? groupId ?? null : null);
            cacheRef.current = {};
            commitLocationSearch(
              mapViewportCenter,
              cat ? "category" : "user-triggered"
            );
          }}
          onSignalPick={(cat) => {
            setLocationKeyword("");
            setLocationCategory(cat || "");
            setLocationCategoryGroupId(null);
            cacheRef.current = {};
            commitLocationSearch(mapViewportCenter, "intent-signal");
          }}
          onSortChange={(sort) => {
            setLocationSort(sort);
            cacheRef.current = {};
            commitLocationSearch(mapViewportCenter, "user-triggered");
          }}
          onSearchThisArea={handleSearchThisArea}
          onRadiusChange={showRadius ? handleRadiusChange : undefined}
        />
      );
    }
    if (activeLayer === "meetup") {
      return (
        <MeetupControls onCreateClick={() => setShowMeetupCreateModal(true)} />
      );
    }
    if (activeLayer === "care") {
      return (
        <CareControls onCreateClick={() => setShowCareCreateModal(true)} />
      );
    }
    return null;
  };

  const renderInfoPanel = () => {
    if (!selectedItem) return null;
    const props = { selectedItem, onClose: () => setSelectedItem(null) };
    if (selectedItem.type === "location") return <LocationLayer {...props} />;
    if (selectedItem.type === "meetup")
      return <MeetupLayer {...props} onRefresh={handleMeetupCreated} />;
    if (selectedItem.type === "care") return <CareLayer {...props} />;
    return null;
  };

  return (
    <PageWrapper>
      <DomainTabHeader
        activeLayer={activeLayer}
        onTabChange={handleTabChange}
      />

      <ContentRow>
        {/* ── 데스크톱 전용 좌측 패널 ── */}
        <LeftPanel>
          <LeftPanelTop>
            {/* location 탭: 반경 필터가 LocationControls 내부 CompactFilterRow에 통합 */}
            {activeLayer !== "location" && (
              <RadiusFilter
                radius={radius}
                onRadiusChange={handleRadiusChange}
              />
            )}
            {renderLayerControls(activeLayer === "location")}
          </LeftPanelTop>

          {/* location 탭: 결과 목록 */}
          {activeLayer === "location" && (
            <LeftPanelResults>
              {loading && <PanelStatusMsg>검색 중...</PanelStatusMsg>}
              {!loading &&
                !error &&
                displayItems.length === 0 &&
                mapViewportCenter && (
                  <PanelStatusMsg>
                    반경 {radius}km 내 결과가 없습니다.
                  </PanelStatusMsg>
                )}
              {!loading && !error && displayItems.length > 0 && (
                <>
                  <PanelResultHeader>
                    <div>
                      <PanelResultTitle>주변 시설</PanelResultTitle>
                      <PanelResultSubtitle>
                        {searchMode === "initial"
                          ? "초기 검색"
                          : "현재 검색 기준"}{" "}
                        · 반경 {radius}km · {SORT_LABELS[locationSort]}
                      </PanelResultSubtitle>
                    </div>
                    <PanelResultCount>{displayItems.length}개</PanelResultCount>
                  </PanelResultHeader>
                  <PanelResultList>
                    {displayItems.map((item, index) => {
                      const isSelected = selectedItem?.id === item.id;
                      return (
                        <ResultCard
                          key={item.id}
                          type="button"
                          $selected={isSelected}
                          onClick={() => {
                            handleLocationResultClick(item);
                          }}
                          onMouseEnter={() => setHoveredLocationItem(item)}
                          onMouseLeave={() =>
                            setHoveredLocationItem((current) =>
                              current?.id === item.id ? null : current
                            )
                          }
                        >
                          <ResultCardTop>
                            <ResultCardTitle>
                              {item.title || item.name || `시설 ${index + 1}`}
                            </ResultCardTitle>
                            {item.distanceM != null ? (
                              <ResultDistance>{item.distanceM}m</ResultDistance>
                            ) : (
                              item.raw?.distance != null && (
                                <ResultDistance>
                                  {Math.round(item.raw.distance)}m
                                </ResultDistance>
                              )
                            )}
                          </ResultCardTop>
                          <ResultCardSubtitle>
                            {item.subtitle ||
                              item.raw?.address ||
                              "주소 정보 없음"}
                          </ResultCardSubtitle>
                        </ResultCard>
                      );
                    })}
                  </PanelResultList>
                </>
              )}
            </LeftPanelResults>
          )}

          {(activeLayer === "meetup" || activeLayer === "care") && (
            <LeftPanelResults>
              {loading && <PanelStatusMsg>검색 중...</PanelStatusMsg>}
              {!loading &&
                !error &&
                items.length === 0 &&
                mapViewportCenter && (
                  <PanelStatusMsg>
                    반경 {radius}km 내 항목이 없습니다.
                    펫케어·모임은 지도 반경 검색 시{" "}
                    <strong>위도·경도가 저장된</strong> 것만 목록에 나옵니다.
                  </PanelStatusMsg>
                )}
              {!loading && !error && items.length > 0 && (
                <>
                  <PanelResultHeader>
                    <div>
                      <PanelResultTitle>
                        {activeLayer === "meetup" ? "주변 모임" : "주변 펫케어"}
                      </PanelResultTitle>
                      <PanelResultSubtitle>
                        반경 {radius}km · 지도 중심 기준
                      </PanelResultSubtitle>
                    </div>
                    <PanelResultCount>{items.length}개</PanelResultCount>
                  </PanelResultHeader>
                  <PanelResultList>
                    {items.map((item, index) => {
                      const isSelected = selectedItem?.id === item.id;
                      const sub =
                        item.subtitle ||
                        item.raw?.address ||
                        (item.raw?.description
                          ? String(item.raw.description).slice(0, 100).trim()
                          : "상세는 항목을 눌러 확인");
                      return (
                        <ResultCard
                          key={item.id}
                          type="button"
                          $selected={isSelected}
                          onClick={() => handleLocationResultClick(item)}
                          onMouseEnter={() => setHoveredLocationItem(item)}
                          onMouseLeave={() =>
                            setHoveredLocationItem((current) =>
                              current?.id === item.id ? null : current
                            )
                          }
                        >
                          <ResultCardTop>
                            <ResultCardTitle>
                              {item.title ||
                                item.name ||
                                `${activeLayer === "meetup" ? "모임" : "케어"} ${index + 1}`}
                            </ResultCardTitle>
                            {item.raw?.distance != null && (
                              <ResultDistance>
                                {Math.round(item.raw.distance)}m
                              </ResultDistance>
                            )}
                          </ResultCardTop>
                          <ResultCardSubtitle>{sub}</ResultCardSubtitle>
                        </ResultCard>
                      );
                    })}
                  </PanelResultList>
                </>
              )}
            </LeftPanelResults>
          )}
        </LeftPanel>

        {/* ── 지도 영역 ── */}
        <MapWrapper>
          {mapViewportCenter ? (
            <MapContainer
              services={mapServices}
              onServiceClick={(item) =>
                handleLocationResultClick(item)
              }
              userLocation={userLocation}
              mapCenter={mapViewportCenter}
              mapLevel={mapLevel}
              selectedService={selectedItem}
              hoveredService={hoveredLocationItem}
              onMapIdle={handleMapIdle}
            />
          ) : (
            <MapInitLoading>🗺️ 위치 정보를 가져오는 중...</MapInitLoading>
          )}

          {/* 모바일 전용 컨트롤 오버레이 */}
          <ControlsOverlay>
            {renderLayerControls(true)}
          </ControlsOverlay>

          <MyLocationFAB
            onClick={handleMoveToMyLocation}
            disabled={locating}
            title="내 위치로 이동"
            aria-label="내 위치로 이동"
          >
            <span aria-hidden="true">{locating ? "⏳" : "📍"}</span>
          </MyLocationFAB>

          {loading && <LoadingBar aria-label="데이터 조회 중" />}

          {!loading && mapViewportCenter && activeLayer !== "location" && (
            <CountChip>
              반경 <strong>{radius}km</strong> · <strong>{items.length}</strong>
              개
            </CountChip>
          )}

          {error && !loading && (
            <ErrorBanner onClick={() => setError(null)}>{error} ✕</ErrorBanner>
          )}

          {!loading &&
            !error &&
            displayItems.length === 0 &&
            mapViewportCenter && (
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
};

export default UnifiedPetMapPage;

const loadingSlide = keyframes`
  0%   { left: -40%; width: 40%; }
  50%  { left: 30%;  width: 60%; }
  100% { left: 100%; width: 40%; }
`;

const PageWrapper = styled.div`
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: ${(props) => props.theme.colors.background};

  @media (max-width: 768px) {
    height: calc(100vh - 60px);
  }
`;

const MapWrapper = styled.div`
  flex: 1;
  position: relative;
  overflow: hidden;
`;

const MapInitLoading = styled.div`
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: ${(props) => props.theme.colors.background};
  color: ${(props) => props.theme.colors.textSecondary};
  font-size: 15px;
  z-index: 10;
`;

/* 모바일 전용 컨트롤 오버레이 — 데스크톱은 LeftPanel이 대체 */
const ControlsOverlay = styled.div`
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
  background: ${(props) => props.theme.colors.surface + "E8"};
  border-bottom: 1px solid ${(props) => props.theme.colors.border};
  box-shadow: 0 2px 12px ${(props) => props.theme.colors.shadow};
  pointer-events: none;
  > * {
    pointer-events: auto;
  }
`;

/* 지도 우하단 독립 FAB */
const MyLocationFAB = styled.button`
  position: absolute;
  right: 20px;
  bottom: 110px;
  z-index: 300;
  width: 52px;
  height: 52px;
  border-radius: 18px;
  border: none;
  background: ${(props) => props.theme.colors.surface};
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow: ${(props) => props.theme.shadows.lg};
  font-size: 18px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.15s, box-shadow 0.15s, background 0.15s;

  &:hover:not(:disabled) {
    transform: translateY(-1px);
    box-shadow: ${(props) => props.theme.shadows.xl};
  }
  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  @media (max-width: 768px) {
    right: 12px;
    bottom: calc(60px + 84px + env(safe-area-inset-bottom, 0px));
    height: 48px;
    width: 48px;
    border-radius: 16px;
  }
`;

const LoadingBar = styled.div`
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  overflow: hidden;
  z-index: 600;
  background: transparent;

  &::after {
    content: "";
    position: absolute;
    top: 0;
    height: 100%;
    background: ${(props) => props.theme.colors.primary};
    animation: ${loadingSlide} 1.2s ease-in-out infinite;
  }
`;

const CountChip = styled.div`
  position: absolute;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  background: ${(props) => props.theme.colors.overlay};
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  color: ${(props) => props.theme.colors.textInverse};
  padding: 5px 16px;
  border-radius: 999px;
  font-size: 12px;
  box-shadow: ${(props) => props.theme.shadows.md};
  z-index: 200;
  white-space: nowrap;
  pointer-events: none;
  strong {
    color: ${(props) => props.theme.colors.textInverse};
    font-weight: 600;
  }

  @media (max-width: 768px) {
    bottom: calc(72px + env(safe-area-inset-bottom, 0px));
    font-size: 11px;
    padding: 5px 14px;
  }
`;

const ErrorBanner = styled.div`
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  background: ${(props) => props.theme.colors.error};
  color: ${(props) => props.theme.colors.textInverse};
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 13px;
  z-index: 400;
  cursor: pointer;
  white-space: nowrap;
`;

const EmptyBanner = styled.div`
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  background: ${(props) => props.theme.colors.surface};
  color: ${(props) => props.theme.colors.textSecondary};
  border: 1px solid ${(props) => props.theme.colors.border};
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 13px;
  z-index: 400;
  white-space: nowrap;
`;

const LocationResultSheet = styled.section`
  /* 모바일 전용 — 데스크톱은 LeftPanel 내 결과 목록이 대체 */
  @media (min-width: 769px) {
    display: none !important;
  }

  position: absolute;
  left: 16px;
  width: 392px;
  bottom: 16px;
  z-index: 230;
  top: 236px;
  border-radius: 24px;
  background: ${(props) => props.theme.colors.surface + "F2"};
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid ${(props) => props.theme.colors.border};
  box-shadow: ${(props) => props.theme.shadows.xl};
  overflow: hidden;
  display: flex;
  flex-direction: column;

  @media (min-width: 1024px) {
    width: 392px;
    bottom: 16px;
  }

  @media (max-width: 768px) {
    left: 12px;
    right: 12px;
    width: auto;
    top: auto;
    bottom: calc(72px + env(safe-area-inset-bottom, 0px));
    min-height: 272px;
    max-height: calc(100dvh - 220px);
    border-radius: 24px 24px 18px 18px;
  }
`;

const ResultSheetHeader = styled.div`
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 18px 18px 12px;
  border-bottom: 1px solid ${(props) => props.theme.colors.borderLight};
`;

const ResultSheetHandle = styled.div`
  width: 52px;
  height: 5px;
  border-radius: 999px;
  background: ${(props) => props.theme.colors.border};
  margin: 10px auto 2px;

  @media (min-width: 769px) {
    display: none;
  }
`;

const ResultSheetTitle = styled.h3`
  margin: 0;
  font-size: 16px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: ${(props) => props.theme.colors.text};
`;

const ResultSheetSubtitle = styled.p`
  margin: 6px 0 0;
  font-size: 12px;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const ResultSheetMeta = styled.span`
  font-size: 12px;
  font-weight: 700;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const ResultList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1;
  overflow-y: auto;
  padding: 14px 16px 18px;

  @media (max-width: 768px) {
    padding-bottom: 22px;
  }
`;

const ResultCard = styled.button`
  width: 100%;
  text-align: left;
  border: 1px solid
    ${(props) =>
      props.$selected
        ? props.theme.colors.domain.location
        : props.theme.colors.border};
  background: ${(props) =>
    props.$selected
      ? props.theme.colors.domain.location + "1A"
      : props.theme.colors.background};
  border-radius: 18px;
  padding: 14px 14px 13px;
  cursor: pointer;
  transition: border-color 0.15s ease, transform 0.15s ease,
    box-shadow 0.15s ease, background 0.15s ease;

  &:hover {
    border-color: ${(props) => props.theme.colors.domain.location};
    transform: translateY(-1px);
    box-shadow: ${(props) => props.theme.shadows.lg};
  }
`;

const ResultCardTop = styled.div`
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
`;

const ResultCardTitle = styled.div`
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 15px;
  font-weight: 800;
  letter-spacing: -0.01em;
  color: ${(props) => props.theme.colors.text};
`;

const ResultCardSubtitle = styled.div`
  margin-top: 7px;
  font-size: 12px;
  line-height: 1.45;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const ResultDistance = styled.span`
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 700;
  color: ${(props) => props.theme.colors.domain.location};
  background: ${(props) => props.theme.colors.domain.location + "1A"};
  padding: 4px 8px;
  border-radius: 999px;
`;

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
  border-right: 1px solid ${(props) => props.theme.colors.border};
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: ${(props) => props.theme.colors.surface};

  @media (max-width: 768px) {
    display: none;
  }
`;

const LeftPanelTop = styled.div`
  flex-shrink: 0;
  border-bottom: 1px solid ${(props) => props.theme.colors.border};
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
  border-bottom: 1px solid ${(props) => props.theme.colors.borderLight};
  flex-shrink: 0;
`;

const PanelResultTitle = styled.h3`
  margin: 0;
  font-size: 15px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: ${(props) => props.theme.colors.text};
`;

const PanelResultSubtitle = styled.p`
  margin: 4px 0 0;
  font-size: 11px;
  color: ${(props) => props.theme.colors.textSecondary};
`;

const PanelResultCount = styled.span`
  font-size: 12px;
  font-weight: 700;
  color: ${(props) => props.theme.colors.textSecondary};
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
  color: ${(props) => props.theme.colors.textSecondary};
  font-size: 13px;
`;
