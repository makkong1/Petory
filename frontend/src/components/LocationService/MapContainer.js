import React, { useEffect, useLayoutEffect, useRef, useState } from 'react';
import styled from 'styled-components';

const MapContainer = ({ services = [], onServiceClick, selectedCategory = null, userLocation = null }) => {
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
    
    if (!mapRef.current) {
      console.log('mapRef.current가 없음, 100ms 후 재시도');
      setTimeout(() => {
        if (mapRef.current) {
          initializeMap();
        }
      }, 100);
      return;
    }
    
    // 사용자 위치가 있으면 사용자 위치로, 없으면 서울 시청으로 설정
    let initialPosition;
    let initialLevel = 5; // 기본 확대 레벨
    
    if (userLocation && userLocation.lat && userLocation.lng) {
      initialPosition = new window.kakao.maps.LatLng(userLocation.lat, userLocation.lng);
      initialLevel = 4; // 사용자 위치는 구 단위 지역이 잘 보이도록 level 4로 설정
      console.log('사용자 위치로 지도 초기화:', userLocation);
    } else {
      initialPosition = new window.kakao.maps.LatLng(37.5665, 126.9780); // 서울 시청
      initialLevel = 5; // 서울 시청 근처가 잘 보이도록 level 5로 설정
      console.log('기본 위치로 지도 초기화 (사용자 위치 대기 중)');
    }
    
    const mapOption = {
      center: initialPosition,
      level: initialLevel
    };

    try {
      console.log('지도 생성 시도...');
      const map = new window.kakao.maps.Map(mapRef.current, mapOption);
      console.log('지도 생성 성공!');
      mapInstanceRef.current = map;
      setMapLoaded(true);
      
      // 사용자 위치가 이미 있으면 즉시 bounds 설정, 없으면 사용자 위치 로드까지 대기
      if (userLocation && userLocation.lat && userLocation.lng) {
        // 마커 업데이트 및 bounds 설정 (초기 로드이므로 shouldSetBounds = true)
        updateMarkers(true);
      } else {
        // 사용자 위치가 없으면 마커만 업데이트하고 bounds는 설정하지 않음
        // (bounds는 사용자 위치 로드 후에만 설정)
        updateMarkers(false);
        // 사용자 위치가 없으면 bounds 설정 플래그를 true로 유지 (나중에 사용자 위치 로드 시 설정할 수 있도록)
      }
    } catch (error) {
      console.error('지도 초기화 실패:', error);
    }
  };

  const updateMarkers = (shouldSetBounds = false) => {
    if (!mapInstanceRef.current) return;

    // 기존 마커와 인포윈도우 제거
    markersRef.current.forEach(marker => marker.setMap(null));
    infoWindowsRef.current.forEach(infoWindow => infoWindow.close());
    markersRef.current = [];
    infoWindowsRef.current = [];

    const bounds = new window.kakao.maps.LatLngBounds();

    // 사용자 위치를 bounds에 포함 (있을 경우)
    if (userLocation && userLocation.lat && userLocation.lng) {
      const userPosition = new window.kakao.maps.LatLng(userLocation.lat, userLocation.lng);
      bounds.extend(userPosition);
      
      // 사용자 위치 마커도 추가할 수 있음 (선택사항)
      // const userMarker = new window.kakao.maps.Marker({
      //   position: userPosition,
      //   map: mapInstanceRef.current,
      //   image: ... // 커스텀 아이콘
      // });
    }

    // 서비스 마커 추가
    if (services && services.length > 0) {
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
    }

    // bounds 설정 (shouldSetBounds가 true이고 사용자 위치가 있을 때만 설정)
    // 사용자 위치가 없으면 bounds를 설정하지 않음 (사용자 위치 로드를 기다림)
    if (shouldSetBounds && userLocation && userLocation.lat && userLocation.lng) {
        // 사용자 위치를 중심으로 하는 bounds 생성
        const centerLat = userLocation.lat;
        const centerLng = userLocation.lng;
        
        // 최소 범위 보장 (구 단위가 보이도록 약 4-5km 범위)
        const minLatDiff = 0.04; // 약 4.4km
        const minLngDiff = 0.04; // 약 3.5km (서울 기준)
        
        // 최대 범위 제한 (너무 넓어지지 않도록, 최대 구 단위 정도)
        const maxLatDiff = 0.06; // 약 6.6km (최대)
        const maxLngDiff = 0.06; // 약 5.3km (최대)
        
        // 서비스 마커가 있는 경우 bounds에 포함시키고, 없으면 사용자 위치 기준으로만 설정
        let finalLatDiff = minLatDiff;
        let finalLngDiff = minLngDiff;
        
        // bounds가 비어있는지 확인
        let boundsHasPoints = false;
        if (bounds) {
          try {
            // bounds에 포인트가 있는지 확인 (getSouthWest와 getNorthEast가 유효한지)
            const sw = bounds.getSouthWest();
            const ne = bounds.getNorthEast();
            if (sw && ne) {
              boundsHasPoints = true;
              const latDiff = ne.getLat() - sw.getLat();
              const lngDiff = ne.getLng() - sw.getLng();
              
              // 서비스 마커가 있으면 그 범위를 포함하되, 최소/최대 범위는 보장
              if (latDiff > 0 && lngDiff > 0) {
                // 사용자 위치 중심으로부터 bounds의 대각선 거리 계산
                const centerToSW = Math.sqrt(
                  Math.pow(centerLat - sw.getLat(), 2) + 
                  Math.pow(centerLng - sw.getLng(), 2)
                );
                
                // 사용자 위치에서 너무 멀리 떨어진 마커가 있으면 범위를 제한
                // (대략 0.05도 ≈ 5.5km)
                if (centerToSW > 0.05) {
                  // 사용자 위치 주변 5km 범위만 포함하도록 제한
                  finalLatDiff = minLatDiff;
                  finalLngDiff = minLngDiff;
                  console.log('멀리 떨어진 마커 감지, 사용자 위치 중심으로만 bounds 설정');
                } else {
                  // 가까운 마커들만 포함하여 범위 계산 (최대값 제한)
                  finalLatDiff = Math.min(Math.max(latDiff * 1.2, minLatDiff), maxLatDiff);
                  finalLngDiff = Math.min(Math.max(lngDiff * 1.2, minLngDiff), maxLngDiff);
                }
              }
            }
          } catch (e) {
            // bounds가 비어있거나 유효하지 않으면 기본값 사용
            console.log('bounds 계산 실패, 기본값 사용:', e);
          }
        }
        
        // 최종 bounds 생성 (사용자 위치 중심, 최소 범위 보장)
        const finalBounds = new window.kakao.maps.LatLngBounds();
        finalBounds.extend(new window.kakao.maps.LatLng(
          centerLat - finalLatDiff / 2,
          centerLng - finalLngDiff / 2
        ));
        finalBounds.extend(new window.kakao.maps.LatLng(
          centerLat + finalLatDiff / 2,
          centerLng + finalLngDiff / 2
        ));
        
        // bounds 설정
        try {
          mapInstanceRef.current.setBounds(finalBounds);
          // bounds 설정 후 level도 확인하여 너무 넓으면 재조정
          setTimeout(() => {
            const currentLevel = mapInstanceRef.current.getLevel();
            if (currentLevel > 5) {
              // level이 5보다 크면 (너무 축소되면) level 4로 강제 설정
              mapInstanceRef.current.setLevel(4);
              mapInstanceRef.current.setCenter(new window.kakao.maps.LatLng(centerLat, centerLng));
              console.log('bounds가 너무 넓어서 level 4로 재조정');
            }
          }, 100);
          
          isInitialBoundsSetRef.current = true;
          console.log('✅ 사용자 위치 중심으로 bounds 설정 완료', {
            center: { lat: centerLat, lng: centerLng },
            range: { lat: finalLatDiff, lng: finalLngDiff },
            hasServiceMarkers: boundsHasPoints
          });
        } catch (e) {
          console.error('bounds 설정 실패:', e);
          // setBounds 실패 시 setCenter와 setLevel로 대체
          mapInstanceRef.current.setCenter(new window.kakao.maps.LatLng(centerLat, centerLng));
          mapInstanceRef.current.setLevel(4);
          isInitialBoundsSetRef.current = true;
          console.log('setBounds 실패, setCenter/setLevel로 대체');
        }
      } else {
        // 사용자 위치가 없으면 bounds를 설정하지 않음 (사용자 위치 로드를 기다림)
        console.log('사용자 위치 없음 - bounds 설정 건너뜀');
      }
  };

  // 사용자 위치 변경 시 마커 및 bounds 업데이트
  useEffect(() => {
    if (mapInstanceRef.current && mapLoaded) {
      if (userLocation && userLocation.lat && userLocation.lng) {
        // 사용자 위치가 로드되면 한 번만 bounds를 설정
        if (!isInitialBoundsSetRef.current) {
          console.log('사용자 위치 로드됨, 마커 및 bounds 업데이트:', userLocation);
          // 마커를 다시 그리고 bounds를 재설정
          updateMarkers(true);
        }
      }
    }
  }, [userLocation, mapLoaded]);

  // 서비스 변경 시 마커 업데이트 (필터 변경 시에는 bounds 재설정하지 않음)
  useEffect(() => {
    if (mapInstanceRef.current && mapLoaded) {
      // 필터가 변경된 경우 마커만 업데이트 (bounds는 사용자 위치 기준으로 유지)
      const categoryChanged = prevSelectedCategoryRef.current !== selectedCategory;
      if (categoryChanged) {
        prevSelectedCategoryRef.current = selectedCategory;
      }
      // 사용자 위치가 있을 때만 bounds 재설정 (필터 변경 시에는 재설정하지 않음)
      const shouldSetBounds = false; // 필터 변경 시에는 bounds 재설정 안 함
      updateMarkers(shouldSetBounds);
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