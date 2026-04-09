import React, { useState, useEffect, useCallback, useRef } from 'react';
import styled from 'styled-components';
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
  const [controlsCollapsed, setControlsCollapsed] = useState(true);
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
  const fetchItems = useCallback((type, center, r, keyword, category) => {
    if (!center?.lat || !center?.lng) return;
    clearTimeout(fetchTimerRef.current);
    fetchTimerRef.current = setTimeout(async () => {
      const cacheKey = `${type}-${center.lat.toFixed(4)}-${center.lng.toFixed(4)}-${r}-${keyword}-${category}`;
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
      fetchItems(activeLayer, mapCenter, radius, locationKeyword, locationCategory);
    }
  }, [activeLayer, mapCenter, radius, locationKeyword, locationCategory, fetchItems]);

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
    if (userLocation) { setMapCenter({ ...userLocation }); setSelectedItem(null); }
  };

  const handleMapIdle = useCallback(({ lat, lng }) => {
    setMapCenter({ lat, lng });
  }, []);

  // 모임 생성 성공 시 목록 갱신
  const handleMeetupCreated = () => {
    cacheRef.current = {};
    fetchItems('meetup', mapCenter, radius, '', '');
  };

  // 케어 요청 생성 성공 시 목록 갱신
  const handleCareCreated = () => {
    cacheRef.current = {};
    fetchItems('care', mapCenter, radius, '', '');
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
      <DomainTabHeader
        activeLayer={activeLayer}
        onTabChange={handleTabChange}
        controlsCollapsed={controlsCollapsed}
        onToggleControls={() => setControlsCollapsed(prev => !prev)}
      />

      {!controlsCollapsed && (
        <>
          <ControlBar>
            <RadiusFilter radius={radius} onRadiusChange={handleRadiusChange} />
            <MyLocationButton onClick={handleMoveToMyLocation} title="내 위치로 이동">
              📍 내 위치
            </MyLocationButton>
          </ControlBar>
          {renderLayerControls()}
        </>
      )}

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
          <LoadingOverlay>위치 정보를 가져오는 중...</LoadingOverlay>
        )}

        {loading && (
          <LoadingOverlay $transparent>
            <LoadingSpinner>⏳ {isAiMode ? 'AI 추천 중...' : '데이터 조회 중...'}</LoadingSpinner>
          </LoadingOverlay>
        )}

        {error && !loading && <ErrorBanner>{error}</ErrorBanner>}

        {!loading && !error && items.length === 0 && mapCenter && (
          <EmptyBanner>반경 {radius}km 내 결과가 없습니다.</EmptyBanner>
        )}

        {renderInfoPanel()}
      </MapWrapper>

      <CountBar>
        {!loading && (
          <span>
            {isAiMode && <AiBadge>✨ AI 추천</AiBadge>}
            반경 <strong>{radius}km</strong> 내 <strong>{items.length}</strong>개
          </span>
        )}
      </CountBar>

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

const PageWrapper = styled.div`
  display: flex;
  flex-direction: column;
  height: calc(100vh - 80px);
  background: ${props => props.theme.colors.background};
`;

const ControlBar = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: ${props => props.theme.colors.surface};
  border-bottom: 1px solid ${props => props.theme.colors.border};
`;

const MyLocationButton = styled.button`
  margin-right: 16px;
  padding: 6px 12px;
  border-radius: 14px;
  border: 1px solid ${props => props.theme.colors.border};
  background: ${props => props.theme.colors.background};
  color: ${props => props.theme.colors.text};
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s ease;
  &:hover { border-color: ${props => props.theme.colors.primary}; color: ${props => props.theme.colors.primary}; }
`;

const MapWrapper = styled.div`
  flex: 1;
  position: relative;
  overflow: hidden;
`;

const LoadingOverlay = styled.div`
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: ${props => props.$transparent ? 'rgba(0,0,0,0.25)' : props.theme.colors.background};
  z-index: 400;
  color: ${props => props.theme.colors.text};
  font-size: 15px;
`;

const LoadingSpinner = styled.div`
  background: ${props => props.theme.colors.surface};
  padding: 12px 20px;
  border-radius: 20px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.15);
`;

const ErrorBanner = styled.div`
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  background: ${props => props.theme.colors.error || '#ef4444'};
  color: white;
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 13px;
  z-index: 400;
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
`;

const CountBar = styled.div`
  padding: 8px 16px;
  font-size: 13px;
  color: ${props => props.theme.colors.textSecondary};
  background: ${props => props.theme.colors.surface};
  border-top: 1px solid ${props => props.theme.colors.border};
  text-align: center;
  strong { color: ${props => props.theme.colors.text}; }
`;

const AiBadge = styled.span`
  margin-right: 8px;
  background: #FFF8EC;
  color: #c47d00;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
`;
