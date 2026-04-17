import React, { useState, useEffect, useCallback, useRef } from 'react';
import styled, { keyframes } from 'styled-components';
import MapContainer from '../LocationService/MapContainer';
import DomainTabHeader from './DomainTabHeader';
import RadiusFilter from './RadiusFilter';
import LocationControls from './controls/LocationControls';
import MeetupControls from './controls/MeetupControls';
import CareControls from './controls/CareControls';
import MeetupCreateModal from './MeetupCreateModal';
import CareCreateModal from './CareCreateModal';
import LocationLayer from './layers/LocationLayer';
import MeetupLayer from './layers/MeetupLayer';
import CareLayer from './layers/CareLayer';
import { fetchActiveMapItems, LAYER_CONFIG } from '../../api/unifiedMapApi';
import { locationServiceApi } from '../../api/locationServiceApi';

const DEFAULT_CENTER = { lat: 37.5665, lng: 126.9780 };
const DEFAULT_RADIUS = 5;

const calculateMapLevelFromRadius = (radiusKm) => {
  if (radiusKm <= 1) return 5;
  if (radiusKm <= 3) return 6;
  if (radiusKm <= 5) return 7;
  if (radiusKm <= 10) return 8;
  return 9;
};

const buildRecommendedMap = (services) => {
  const map = new Map();
  services.forEach((s, i) => { if (s.idx != null) map.set(s.idx, i + 1); });
  return map;
};

const UnifiedPetMapPage = () => {
  const [activeLayer, setActiveLayer] = useState('location');
  const [mapCenter, setMapCenter] = useState(null);
  const [userLocation, setUserLocation] = useState(null);
  const [radius, setRadius] = useState(DEFAULT_RADIUS);
  const [mapLevel, setMapLevel] = useState(calculateMapLevelFromRadius(DEFAULT_RADIUS));
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [selectedItem, setSelectedItem] = useState(null);

  // location 탭 전용
  const [locationKeyword, setLocationKeyword] = useState('');
  const [locationCategory, setLocationCategory] = useState('');
  const [isAiMode, setIsAiMode] = useState(false);
  const [recommendedMap, setRecommendedMap] = useState(null);

  // meetup 탭 전용
  const [showMeetupCreateModal, setShowMeetupCreateModal] = useState(false);

  // care 탭 전용
  const [showCareCreateModal, setShowCareCreateModal] = useState(false);

  // 내 위치 버튼 로딩 상태
  const [locating, setLocating] = useState(false);

  const cacheRef = useRef({});
  const fetchTimerRef = useRef(null);

  // 위치 취득
  useEffect(() => {
    if (!navigator.geolocation) { setMapCenter(DEFAULT_CENTER); return; }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const loc = { lat: pos.coords.latitude, lng: pos.coords.longitude };
        setUserLocation(loc);
        setMapCenter(loc);
      },
      () => setMapCenter(prev => prev || DEFAULT_CENTER),
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 60000 }
    );
  }, []);

  // 데이터 조회 (디바운스 300ms)
  const fetchItems = useCallback((type, center, r, keyword, category, level = 7) => {
    if (!center?.lat || !center?.lng) return;
    clearTimeout(fetchTimerRef.current);
    fetchTimerRef.current = setTimeout(async () => {
      const cacheKey = `${type}-${center.lat.toFixed(4)}-${center.lng.toFixed(4)}-${r}-${keyword}-${category}-${level}`;
      if (cacheRef.current[cacheKey]) {
        setItems(cacheRef.current[cacheKey]);
        setRecommendedMap(null);
        return;
      }
      setLoading(true);
      setError(null);
      setSelectedItem(null);
      setRecommendedMap(null);
      try {
        const result = await fetchActiveMapItems({
          type, lat: center.lat, lng: center.lng, radius: r,
          keyword: type === 'location' ? keyword : undefined,
          category: type === 'location' ? category : undefined,
          mapLevel: level,
        });
        cacheRef.current[cacheKey] = result;
        setItems(result);
      } catch (err) {
        console.error('[UnifiedPetMap] 조회 실패:', err);
        setError('데이터를 불러오지 못했습니다.');
        setItems([]);
      } finally {
        setLoading(false);
      }
    }, 300);
  }, []);

  useEffect(() => {
    if (mapCenter) {
      setIsAiMode(false);
      fetchItems(activeLayer, mapCenter, radius, locationKeyword, locationCategory, mapLevel);
    }
  }, [activeLayer, mapCenter, radius, locationKeyword, locationCategory, mapLevel, fetchItems]);

  // AI 추천
  const handleAiToggle = useCallback(async () => {
    if (isAiMode) {
      setIsAiMode(false);
      setRecommendedMap(null);
      fetchItems('location', mapCenter, radius, locationKeyword, locationCategory);
      return;
    }
    if (!mapCenter) return;
    setLoading(true);
    setError(null);
    setIsAiMode(true);
    try {
      const res = await locationServiceApi.recommendPlaces({
        latitude: mapCenter.lat, longitude: mapCenter.lng, radius: radius * 1000,
        keyword: locationKeyword || undefined, category: locationCategory || undefined,
      });
      const services = res?.data?.services ?? [];
      const aiItems = services.map(s => ({
        idx: s.idx, name: s.name || '', latitude: s.latitude, longitude: s.longitude,
        markerColor: LAYER_CONFIG.location.color,
        id: `location-${s.idx}`, type: 'location',
        title: s.name || '', subtitle: s.category || s.address || '', raw: s,
      }));
      setRecommendedMap(buildRecommendedMap(services));
      setItems(aiItems);
    } catch (err) {
      console.error('[UnifiedPetMap] AI 추천 실패:', err);
      setError('AI 추천을 불러오지 못했습니다.');
      setIsAiMode(false);
    } finally {
      setLoading(false);
    }
  }, [isAiMode, mapCenter, radius, locationKeyword, locationCategory, fetchItems]);

  const handleTabChange = (layer) => {
    setActiveLayer(layer);
    setSelectedItem(null);
    setIsAiMode(false);
    setRecommendedMap(null);
    if (layer !== 'location') { setLocationKeyword(''); setLocationCategory(''); }
  };

  const handleRadiusChange = (r) => {
    setRadius(r);
    setMapLevel(calculateMapLevelFromRadius(r));
    setSelectedItem(null);
    cacheRef.current = {};
  };

  const handleMoveToMyLocation = () => {
    // 이미 위치를 알고 있으면 바로 이동
    if (userLocation) {
      setMapCenter({ ...userLocation });
      setSelectedItem(null);
      return;
    }

    // 위치 권한이 없거나 아직 취득 전이면 재시도
    if (!navigator.geolocation) {
      setError('이 브라우저는 위치 서비스를 지원하지 않습니다.');
      return;
    }

    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const loc = { lat: pos.coords.latitude, lng: pos.coords.longitude };
        setUserLocation(loc);
        setMapCenter(loc);
        setSelectedItem(null);
        setLocating(false);
      },
      (err) => {
        setLocating(false);
        if (err.code === err.PERMISSION_DENIED) {
          setError('위치 권한이 거부되었습니다. 브라우저 설정에서 위치를 허용해주세요.');
        } else {
          setError('현재 위치를 가져올 수 없습니다. 잠시 후 다시 시도해주세요.');
        }
      },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 }
    );
  };

  const handleMapIdle = useCallback(({ lat, lng, level }) => {
    setMapCenter({ lat, lng });
    if (level) setMapLevel(level);
  }, []);

  // 모임 생성 성공 시 목록 갱신
  const handleMeetupCreated = () => {
    cacheRef.current = {};
    fetchItems('meetup', mapCenter, radius, '', '', mapLevel);
  };

  // 케어 요청 생성 성공 시 목록 갱신
  const handleCareCreated = () => {
    cacheRef.current = {};
    fetchItems('care', mapCenter, radius, '', '', mapLevel);
  };

  const renderLayerControls = () => {
    if (activeLayer === 'location') {
      return (
        <LocationControls
          keyword={locationKeyword}
          category={locationCategory}
          isAiMode={isAiMode}
          onSearch={(kw) => { setLocationKeyword(kw); cacheRef.current = {}; }}
          onCategoryChange={(cat) => { setLocationCategory(cat); cacheRef.current = {}; }}
          onAiToggle={handleAiToggle}
        />
      );
    }
    if (activeLayer === 'meetup') {
      return <MeetupControls onCreateClick={() => setShowMeetupCreateModal(true)} />;
    }
    if (activeLayer === 'care') {
      return <CareControls onCreateClick={() => setShowCareCreateModal(true)} />;
    }
    return null;
  };

  const renderInfoPanel = () => {
    if (!selectedItem) return null;
    const props = { selectedItem, onClose: () => setSelectedItem(null) };
    if (selectedItem.type === 'location') return <LocationLayer {...props} />;
    if (selectedItem.type === 'meetup') return <MeetupLayer {...props} onRefresh={handleMeetupCreated} />;
    if (selectedItem.type === 'care') return <CareLayer {...props} />;
    return null;
  };

  return (
    <PageWrapper>
      <DomainTabHeader activeLayer={activeLayer} onTabChange={handleTabChange} />

      <MapWrapper>
        {mapCenter ? (
          <MapContainer
            services={items}
            onServiceClick={setSelectedItem}
            userLocation={userLocation}
            mapCenter={mapCenter}
            mapLevel={mapLevel}
            recommendedServiceIdxs={recommendedMap}
            onMapIdle={handleMapIdle}
          />
        ) : (
          <MapInitLoading>🗺️ 위치 정보를 가져오는 중...</MapInitLoading>
        )}

        {/* 컨트롤 오버레이 — 지도 위에 float, backdrop blur */}
        <ControlsOverlay>
          <OverlayRow>
            <RadiusFilter radius={radius} onRadiusChange={handleRadiusChange} />
          </OverlayRow>
          {renderLayerControls()}
        </ControlsOverlay>

        {/* 내 위치 FAB — 지도 우하단 독립 부동 버튼 */}
        <MyLocationFAB
          onClick={handleMoveToMyLocation}
          disabled={locating}
          title="내 위치로 이동"
          aria-label="내 위치로 이동"
        >
          {locating ? '⏳' : '📍'}
        </MyLocationFAB>

        {/* 로딩 — 얇은 프로그레스 바 */}
        {loading && <LoadingBar aria-label={isAiMode ? 'AI 추천 중' : '데이터 조회 중'} />}

        {/* 결과 수 칩 */}
        {!loading && mapCenter && (
          <CountChip>
            {isAiMode && <AiBadge>✨ AI</AiBadge>}
            반경 <strong>{radius}km</strong> · <strong>{items.length}</strong>개
          </CountChip>
        )}

        {error && !loading && <ErrorBanner onClick={() => setError(null)}>{error} ✕</ErrorBanner>}

        {!loading && !error && items.length === 0 && mapCenter && (
          <EmptyBanner>반경 {radius}km 내 결과가 없습니다.</EmptyBanner>
        )}

        {renderInfoPanel()}
      </MapWrapper>

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
  background: ${props => props.theme.colors.background};

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
  background: ${props => props.theme.colors.background};
  color: ${props => props.theme.colors.textSecondary};
  font-size: 15px;
  z-index: 10;
`;

/* 컨트롤을 지도 위에 float — backdrop blur로 map이 살짝 비침 */
const ControlsOverlay = styled.div`
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

const OverlayRow = styled.div`
  display: flex;
  align-items: center;
`;

/* 지도 우하단 독립 FAB */
const MyLocationFAB = styled.button`
  position: absolute;
  right: 16px;
  bottom: 80px;
  z-index: 300;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: none;
  background: ${props => props.theme.colors.surface};
  box-shadow: 0 2px 10px rgba(28, 25, 23, 0.2);
  font-size: 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.15s, box-shadow 0.15s;

  &:hover:not(:disabled) {
    transform: scale(1.08);
    box-shadow: 0 4px 14px rgba(28, 25, 23, 0.28);
  }
  &:disabled { opacity: 0.6; cursor: not-allowed; }
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
    content: '';
    position: absolute;
    top: 0;
    height: 100%;
    background: ${props => props.theme.colors.primary};
    animation: ${loadingSlide} 1.2s ease-in-out infinite;
  }
`;

const CountChip = styled.div`
  position: absolute;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(28, 25, 23, 0.72);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  color: rgba(255, 255, 255, 0.92);
  padding: 5px 16px;
  border-radius: 999px;
  font-size: 12px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.2);
  z-index: 200;
  white-space: nowrap;
  pointer-events: none;
  strong { color: white; font-weight: 600; }
`;

const AiBadge = styled.span`
  margin-right: 6px;
  background: ${props => props.theme.colors.ai.bg};
  color: ${props => props.theme.colors.ai.text};
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 8px;
  font-size: 11px;
`;

const ErrorBanner = styled.div`
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  background: ${props => props.theme.colors.error};
  color: white;
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
  background: ${props => props.theme.colors.surface};
  color: ${props => props.theme.colors.textSecondary};
  border: 1px solid ${props => props.theme.colors.border};
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 13px;
  z-index: 400;
  white-space: nowrap;
`;
