import React, { useEffect, useLayoutEffect, useRef, useState } from 'react';
import styled from 'styled-components';

const MapContainer = ({ services = [], onServiceClick, selectedCategory = null }) => {
  const mapRef = useRef(null);
  const mapInstanceRef = useRef(null);
  const markersRef = useRef([]);
  const infoWindowsRef = useRef([]);
  const [mapLoaded, setMapLoaded] = useState(false);
  const isInitialBoundsSetRef = useRef(false);
  const prevSelectedCategoryRef = useRef(null);

  console.log('MapContainer 렌더링, services:', services);

  // 카카오맵 초기화 - useLayoutEffect 사용
  useLayoutEffect(() => {
    const initMap = () => {
      console.log('카카오맵 초기화 시도...');
      console.log('window.kakao:', window.kakao);
      console.log('mapRef.current:', mapRef.current);
      
      if (window.kakao && window.kakao.maps && mapRef.current) {
        console.log('카카오맵 API 로드됨, 지도 초기화 시작');
        try {
          window.kakao.maps.load(() => {
            console.log('카카오맵 load 콜백 실행');
            initializeMap();
          });
        } catch (error) {
          console.error('카카오맵 로드 실패:', error);
          setTimeout(initMap, 1000);
        }
      } else {
        console.log('카카오맵 API 또는 DOM 요소 준비되지 않음, 100ms 후 재시도');
        setTimeout(initMap, 100);
      }
    };

    // DOM이 준비된 후 실행
    const timer = setTimeout(initMap, 100);
    return () => clearTimeout(timer);
  }, []);

  const initializeMap = () => {
    console.log('initializeMap 호출됨');
    console.log('mapRef.current 재확인:', mapRef.current);
    
    if (!mapRef.current) {
      console.log('mapRef.current가 없음, 100ms 후 재시도');
      setTimeout(() => {
        if (mapRef.current) {
          initializeMap();
        }
      }, 100);
      return;
    }
    
    const defaultPosition = new window.kakao.maps.LatLng(37.5665, 126.9780);
    
    const mapOption = {
      center: defaultPosition,
      level: 5
    };

    try {
      console.log('지도 생성 시도...');
      const map = new window.kakao.maps.Map(mapRef.current, mapOption);
      console.log('지도 생성 성공!');
      mapInstanceRef.current = map;
      setMapLoaded(true);
      updateMarkers();
    } catch (error) {
      console.error('지도 초기화 실패:', error);
    }
  };

  const updateMarkers = () => {
    if (!mapInstanceRef.current) return;

    console.log('updateMarkers 호출됨, services:', services);

    // 기존 마커와 인포윈도우 제거
    markersRef.current.forEach(marker => marker.setMap(null));
    infoWindowsRef.current.forEach(infoWindow => infoWindow.close());
    markersRef.current = [];
    infoWindowsRef.current = [];

    if (!services || services.length === 0) {
      console.log('서비스 데이터가 없음');
      return;
    }

    console.log('마커 생성 시작, 서비스 개수:', services.length);

    const bounds = new window.kakao.maps.LatLngBounds();

    services.forEach((service) => {
      if (selectedCategory && service.category !== selectedCategory) return;
      if (!service.latitude || !service.longitude) return;

      const position = new window.kakao.maps.LatLng(
        service.latitude,
        service.longitude
      );

      const marker = new window.kakao.maps.Marker({
        position: position,
        map: mapInstanceRef.current,
      });

      const infoWindow = new window.kakao.maps.InfoWindow({
        content: `
          <div style="padding: 10px; min-width: 200px;">
            <h3 style="margin: 0 0 5px 0; font-size: 14px; font-weight: bold;">
              ${service.name}
            </h3>
            <p style="margin: 5px 0; font-size: 12px; color: #666;">
              ${service.category || '카테고리 없음'}
            </p>
            <p style="margin: 5px 0; font-size: 12px; color: #666;">
              ${service.address || '주소 없음'}
            </p>
            ${service.rating ? `
              <p style="margin: 5px 0; font-size: 12px;">
                ⭐ ${service.rating.toFixed(1)}
              </p>
            ` : ''}
          </div>
        `
      });

      window.kakao.maps.event.addListener(marker, 'click', () => {
        infoWindowsRef.current.forEach(iw => iw.close());
        infoWindow.open(mapInstanceRef.current, marker);
        // 마커 클릭 시 해당 위치로 부드럽게 이동 (확대는 하지 않음)
        mapInstanceRef.current.panTo(position);
        if (onServiceClick) {
          onServiceClick(service);
        }
      });

      markersRef.current.push(marker);
      infoWindowsRef.current.push(infoWindow);
      bounds.extend(position);
    });

    // 처음 한 번만 bounds 설정 (리로딩 방지)
    if (markersRef.current.length > 0 && !isInitialBoundsSetRef.current) {
      mapInstanceRef.current.setBounds(bounds);
      isInitialBoundsSetRef.current = true;
    }
  };

  // 서비스 변경 시 마커 업데이트 (필터 변경 시에만 bounds 재설정)
  useEffect(() => {
    if (mapInstanceRef.current && mapLoaded) {
      // 필터가 변경된 경우에만 bounds 재설정
      if (prevSelectedCategoryRef.current !== selectedCategory) {
        isInitialBoundsSetRef.current = false;
        prevSelectedCategoryRef.current = selectedCategory;
      }
      updateMarkers();
    }
  }, [services, selectedCategory, mapLoaded]);

  return (
    <MapDiv ref={mapRef}>
      {!mapLoaded && (
        <LoadingMessage>
          <div>🗺️ 지도 로딩 중...</div>
        </LoadingMessage>
      )}
    </MapDiv>
  );
};

export default MapContainer;

const MapDiv = styled.div`
  width: 100%;
  height: 100%;
  min-height: 500px;
  position: relative;
`;

const LoadingMessage = styled.div`
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
  color: #666;
  font-size: 1.1rem;
`;