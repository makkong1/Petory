import React, { useEffect, useRef, useState } from 'react';
import styled from 'styled-components';
import { geocodingApi } from '../../api/geocodingApi';

const DEFAULT_CENTER = { lat: 37.5665, lng: 126.9780 };
const DEFAULT_ZOOM = 15;
const NAVER_KEY_ID =
  process.env.REACT_APP_NAVER_MAPS_KEY_ID ||
  process.env.REACT_APP_NAVER_MAPS_CLIENT_ID ||
  '';

/**
 * 모달 내 미니 지도 장소 선택 컴포넌트
 *
 * Props:
 *   lat, lng        - 현재 선택된 좌표 (null이면 핀 없음)
 *   onSelect(lat, lng, address) - 지도 클릭 시 역지오코딩 결과 반환
 */
const MiniMapPicker = ({ lat, lng, onSelect }) => {
  const mapRef = useRef(null);
  const mapInstanceRef = useRef(null);
  const markerRef = useRef(null);
  const [loading, setLoading] = useState(false);
  const [hint, setHint] = useState('지도를 클릭하면 장소가 설정됩니다');

  // 지도 초기화
  useEffect(() => {
    const initMap = () => {
      if (!mapRef.current || mapInstanceRef.current) return;
      if (!window.naver?.maps) return;

      const center = lat && lng
        ? new window.naver.maps.LatLng(lat, lng)
        : new window.naver.maps.LatLng(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng);

      const map = new window.naver.maps.Map(mapRef.current, {
        center,
        zoom: DEFAULT_ZOOM,
        zoomControl: false,
        logoControl: false,
        mapDataControl: false,
        scaleControl: false,
      });
      mapInstanceRef.current = map;

      // 초기 핀
      if (lat && lng) {
        placeMarker(new window.naver.maps.LatLng(lat, lng));
      }

      // 클릭 이벤트
      window.naver.maps.Event.addListener(map, 'click', async (e) => {
        const coord = e.coord || e.latlng;
        if (!coord) return;

        placeMarker(coord);
        setLoading(true);
        setHint('주소 가져오는 중...');
        try {
          const result = await geocodingApi.coordinatesToAddress(coord.lat(), coord.lng());
          const address = result?.address || result?.roadAddress || '';
          onSelect?.(coord.lat(), coord.lng(), address);
          setHint(address || '선택된 위치');
        } catch {
          onSelect?.(coord.lat(), coord.lng(), '');
          setHint('선택된 위치');
        } finally {
          setLoading(false);
        }
      });
    };

    if (window.naver?.maps) {
      initMap();
      return;
    }

    const existing = document.querySelector('script[src*="map.naver.com"]');
    if (existing) {
      const interval = setInterval(() => {
        if (window.naver?.maps) { clearInterval(interval); initMap(); }
      }, 100);
      return () => clearInterval(interval);
    }

    if (!NAVER_KEY_ID) return;
    const script = document.createElement('script');
    script.src = `https://openapi.map.naver.com/openapi/v3/maps.js?ncpKeyId=${NAVER_KEY_ID}`;
    script.async = true;
    script.onload = initMap;
    document.head.appendChild(script);

    return () => {
      if (mapInstanceRef.current) {
        mapInstanceRef.current = null;
      }
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 외부에서 lat/lng 변경 시 지도 이동 + 핀 업데이트
  useEffect(() => {
    if (!mapInstanceRef.current || !window.naver?.maps) return;
    if (!lat || !lng) return;

    const coord = new window.naver.maps.LatLng(lat, lng);
    mapInstanceRef.current.setCenter(coord);
    mapInstanceRef.current.setZoom(DEFAULT_ZOOM);
    placeMarker(coord);
    setHint(`${lat.toFixed(5)}, ${lng.toFixed(5)}`);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lat, lng]);

  const placeMarker = (coord) => {
    if (!mapInstanceRef.current || !window.naver?.maps) return;
    if (markerRef.current) markerRef.current.setMap(null);
    markerRef.current = new window.naver.maps.Marker({
      position: coord,
      map: mapInstanceRef.current,
    });
  };

  return (
    <Wrapper>
      <MapDiv ref={mapRef} />
      <Hint $loading={loading}>{loading ? '📍 주소 조회 중...' : `📍 ${hint}`}</Hint>
    </Wrapper>
  );
};

export default MiniMapPicker;

const Wrapper = styled.div`
  position: relative;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid ${p => p.theme.colors.border};
`;

const MapDiv = styled.div`
  width: 100%;
  height: 220px;
  background: ${p => p.theme.colors.background};
`;

const Hint = styled.div`
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 11px;
  padding: 5px 10px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  opacity: ${p => p.$loading ? 0.7 : 1};
`;
