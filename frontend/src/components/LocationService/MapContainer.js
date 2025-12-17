import React, { useCallback, useEffect, useRef, useState } from 'react';
import styled from 'styled-components';
// GeoJSON 관련 import 제거됨 (geojsonUtils 파일 없음)

const DEFAULT_CENTER = { lat: 36.5, lng: 127.5 }; // 대한민국 중심 좌표
// DEFAULT_ZOOM 제거: 각 페이지에서 mapLevel prop으로 줌 레벨을 명시적으로 전달해야 함
const COORD_EPSILON = 0.00001;

// 네이버맵 API 키 (환경변수에서 가져오거나 직접 설정)
// 최신 버전에서는 ncpKeyId를 사용합니다
const NAVER_MAPS_KEY_ID = process.env.REACT_APP_NAVER_MAPS_KEY_ID || process.env.REACT_APP_NAVER_MAPS_CLIENT_ID || '';

/**
 * 범용 지도 컨테이너 컴포넌트
 * 
 * @param {number} mapLevel - 카카오맵 레벨 (1-14, 낮을수록 확대). 필수 prop.
 *                            각 페이지에서 사용 목적에 맞는 레벨을 명시적으로 전달해야 함.
 *                            예: 동 단위(11), 시군구 단위(12), 시도 단위(13), 전국(14)
 * @param {Object} mapCenter - 지도 중심 좌표 {lat, lng}
 * @param {Array} services - 표시할 서비스/마커 목록
 * @param {Function} onServiceClick - 마커 클릭 핸들러
 * @param {Object} userLocation - 사용자 위치 {lat, lng}
 * @param {Function} onMapIdle - 지도 이동/줌 완료 시 호출되는 콜백
 * @param {Function} onMapDragStart - 지도 드래그 시작 시 호출되는 콜백
 * @param {Function} onMapClick - 지도 클릭 핸들러
 * @param {Object} hoverMarker - 호버 중인 마커 정보
 * @param {string} currentMapView - 현재 지도 뷰 ('nation', 'sido', 'sigungu', 'dong')
 * @param {string} selectedSido - 선택된 시도
 * @param {string} selectedSigungu - 선택된 시군구
 * @param {string} selectedEupmyeondong - 선택된 읍면동
 * @param {Function} onRegionClick - 지역 클릭 핸들러
 */
const MapContainer = React.forwardRef(
  ({ services = [], onServiceClick, userLocation, mapCenter, mapLevel, onMapDragStart, onMapIdle, hoverMarker = null, currentMapView = 'nation', selectedSido = null, selectedSigungu = null, selectedEupmyeondong = null, onRegionClick = null, onMapClick = null }, ref) => {
    const mapRef = useRef(null);
    const mapInstanceRef = useRef(null);
    const markersRef = useRef([]);
    const userMarkerRef = useRef(null);
    const hoverMarkerRef = useRef(null);
    const lastProgrammaticCenterRef = useRef(null);
    const mapReadyRef = useRef(false);
    const [mapReady, setMapReady] = useState(false);
    const userZoomedRef = useRef(false); // 사용자가 직접 줌 조정했는지 여부
    // GeoJSON 관련 ref 제거됨

    // 카카오맵 레벨을 네이버맵 줌으로 변환
    const mapLevelToZoom = useCallback((kakaoLevel) => {
      // 카카오맵 레벨 1-14를 네이버맵 줌 1-21로 대략 변환
      // 레벨이 낮을수록 확대 (카카오맵), 줌이 높을수록 확대 (네이버맵)
      const zoomMap = {
        1: 21, 2: 20, 3: 19, 4: 18, 5: 17, 6: 16, 7: 15, 8: 14,
        9: 13, 10: 12, 11: 11, 12: 10, 13: 9, 14: 8
      };
      return zoomMap[kakaoLevel] || 7;
    }, []);

    // 줌을 카카오맵 레벨로 변환
    const zoomToMapLevel = useCallback((zoom) => {
      const levelMap = {
        21: 1, 20: 2, 19: 3, 18: 4, 17: 5, 16: 6, 15: 7, 14: 8,
        13: 9, 12: 10, 11: 11, 10: 12, 9: 13, 8: 14
      };
      return levelMap[zoom] || 3;
    }, []);

    const ensureMap = useCallback(() => {
      if (mapInstanceRef.current || !mapRef.current || !window.naver?.maps) {
        if (!window.naver?.maps) {
          console.error('네이버맵 API가 로드되지 않았습니다.');
        }
        return;
      }

      try {
        const initial = mapCenter || DEFAULT_CENTER;
        // mapLevel은 필수 prop이어야 하며, 각 페이지에서 명시적으로 전달해야 함
        if (!mapLevel) {
          console.warn('MapContainer: mapLevel prop이 제공되지 않았습니다. 기본값(전국 뷰, level 14)을 사용합니다.');
        }
        const initialZoom = mapLevel ? mapLevelToZoom(mapLevel) : mapLevelToZoom(14); // 기본값: 전국 뷰 (level 14)

        const mapOptions = {
          center: new window.naver.maps.LatLng(initial.lat, initial.lng),
          zoom: initialZoom,
          minZoom: 1, // 최소 줌 레벨 (최대 축소)
          maxZoom: 21, // 최대 줌 레벨 (최대 확대)
          zoomControl: false, // 기본 컨트롤 비활성화 (커스텀 버튼 사용)
          scrollWheel: false, // 마우스 휠 확대/축소 비활성화
          disableDoubleClickZoom: false, // 더블클릭 확대 활성화
          disableDoubleClick: false,
        };

        const map = new window.naver.maps.Map(mapRef.current, mapOptions);
        mapInstanceRef.current = map;
        lastProgrammaticCenterRef.current = initial;
        mapReadyRef.current = true;
        setMapReady(true);

        // 지도 이벤트 리스너 등록
        window.naver.maps.Event.addListener(map, 'dragstart', () => {
          lastProgrammaticCenterRef.current = null;
          onMapDragStart?.();
        });

        // 줌 변경 이벤트: 사용자가 직접 마우스 휠로 조정한 경우 감지
        window.naver.maps.Event.addListener(map, 'zoom_changed', () => {
          // 프로그래밍 방식이 아닌 경우 (사용자가 직접 조정)
          if (lastProgrammaticCenterRef.current !== null) {
            // 중심이 변경되지 않았는데 줌만 변경된 경우 = 사용자가 마우스 휠로 조정
            const currentCenter = map.getCenter();
            const planned = lastProgrammaticCenterRef.current;
            if (planned &&
              Math.abs(planned.lat - currentCenter.lat()) < COORD_EPSILON &&
              Math.abs(planned.lng - currentCenter.lng()) < COORD_EPSILON) {
              userZoomedRef.current = true; // 사용자가 직접 줌 조정함
            }
          } else {
            userZoomedRef.current = true; // 사용자가 직접 조정함
          }
        });

        // 지도 클릭 이벤트 (GeoJSON 폴리곤 기능 제거됨)
        if (onMapClick) {
          window.naver.maps.Event.addListener(map, 'click', (e) => {
            onMapClick(e);
          });
        }

        // idle 이벤트 디바운싱 (성능 최적화)
        let idleTimeout = null;
        window.naver.maps.Event.addListener(map, 'idle', () => {
          clearTimeout(idleTimeout);
          idleTimeout = setTimeout(() => {
            const center = map.getCenter();
            const bounds = map.getBounds();
            const planned = lastProgrammaticCenterRef.current;

            if (planned) {
              const isSame =
                Math.abs(planned.lat - center.lat()) < COORD_EPSILON &&
                Math.abs(planned.lng - center.lng()) < COORD_EPSILON;

              if (isSame) {
                // 프로그래밍 방식으로 이동이 완료되었으므로 유지
                // null로 설정하지 않음 (다음 프로그래밍 이동을 위해)
              } else {
                // 목표 위치와 다르면 사용자가 수동으로 이동했을 수 있음
                // 하지만 짧은 시간 내에 다시 목표 위치로 이동할 수 있으므로
                // 조금 더 기다려봐야 함 (줌 변경 중일 수 있음)
                // 일단 null로 설정하지 않고 유지
              }
            }

            // 수동 조작 여부 확인
            const isManualOperation = lastProgrammaticCenterRef.current === null;

            onMapIdle?.({
              lat: center.lat(),
              lng: center.lng(),
              level: zoomToMapLevel(map.getZoom()),
              bounds: {
                sw: { lat: bounds.getMin().lat(), lng: bounds.getMin().lng() },
                ne: { lat: bounds.getMax().lat(), lng: bounds.getMax().lng() },
              },
              isManualOperation, // 수동 조작 여부 전달
            });
          }, 200); // 200ms 디바운싱
        });
      } catch (error) {
        console.error('네이버맵 초기화 실패:', error);
        console.error('에러 상세:', error.message, error.stack);
        console.error('가능한 원인:');
        console.error('1. 네이버 클라우드 플랫폼에서 Maps API가 활성화되지 않았습니다.');
        console.error('2. Key ID가 잘못되었거나 도메인이 등록되지 않았습니다.');
        console.error('3. 네이버 클라우드 플랫폼 > Application > Web Service URL에 현재 URL을 등록하세요.');
        console.error('   현재 URL:', window.location.origin);
      }
    }, [mapCenter, mapLevel, mapLevelToZoom, zoomToMapLevel, onMapDragStart, onMapIdle]);

    // 네이버맵 스크립트 로드
    useEffect(() => {
      if (!NAVER_MAPS_KEY_ID) {
        console.error('네이버맵 Key ID가 설정되지 않았습니다. .env 파일에 REACT_APP_NAVER_MAPS_KEY_ID를 확인하세요.');
        return;
      }

      if (window.naver?.maps) {
        if (!mapInstanceRef.current) {
          ensureMap();
        }
        return;
      }

      // 이미 스크립트가 있는지 확인 (중복 로드 방지)
      const existingScript = document.querySelector(`script[src*="map.naver.com"]`);
      if (existingScript) {
        // 이미 스크립트가 있으면 로드 완료를 기다림
        let retryCount = 0;
        const checkInterval = setInterval(() => {
          if (window.naver?.maps) {
            clearInterval(checkInterval);
            if (!mapInstanceRef.current) {
              ensureMap();
            }
          } else if (retryCount++ > 100) {
            clearInterval(checkInterval);
            console.error('네이버맵 API 로드 타임아웃');
          }
        }, 100);
        return () => clearInterval(checkInterval);
      }

      const script = document.createElement('script');
      // 네이버맵 API v3는 ncpClientId를 사용 (지도 표시만, geocoding은 백엔드에서 처리)
      const scriptUrl = `https://openapi.map.naver.com/openapi/v3/maps.js?ncpKeyId=${NAVER_MAPS_KEY_ID}`;
      script.src = scriptUrl;
      script.async = true;
      script.onload = () => {
        // 스크립트 로드 후 약간의 지연을 두고 지도 초기화
        setTimeout(() => {
          if (window.naver?.maps && !mapInstanceRef.current) {
            ensureMap();
          }
        }, 100);
      };
      script.onerror = (error) => {
        console.error('네이버맵 API 스크립트 로드 실패:', error);
        console.error('가능한 원인:');
        console.error('1. 네이버 클라우드 플랫폼에서 Maps API가 활성화되지 않았습니다.');
        console.error('2. Key ID가 잘못되었거나 불완전합니다.');
        console.error('3. 웹 서비스 URL이 등록되지 않았습니다.');
        console.error('4. 신규 Maps API 클라이언트 ID를 발급받아야 할 수 있습니다.');
        console.error('   - 네이버 클라우드 플랫폼 콘솔 > Services > AI·NAVER API > Application');
        console.error('   - 클라이언트 ID 선택 > Web Service URL에 "http://localhost:3000" 추가');
      };
      document.head.appendChild(script);

      return () => {
        // cleanup은 스크립트를 제거하지 않음 (다른 컴포넌트에서도 사용할 수 있음)
      };
    }, [ensureMap]);

    // 마커 정리
    const clearMarkers = useCallback(() => {
      markersRef.current.forEach((marker) => {
        if (marker.setMap) marker.setMap(null);
      });
      markersRef.current = [];
    }, []);

    // 지역 폴리곤 정리 함수 제거됨 (GeoJSON 미사용)

    // 서비스 마커 표시 - 성능 최적화: 마커 개수 제한 및 배치 처리
    const lastServicesKeyRef = useRef('');

    useEffect(() => {
      if (!mapReadyRef.current || !mapInstanceRef.current || !window.naver?.maps) return;

      // 마커가 변경되지 않았으면 스킵
      const servicesKey = services.map(s => `${s.latitude},${s.longitude}`).join('|');
      if (servicesKey === lastServicesKeyRef.current && markersRef.current.length > 0) {
        return;
      }
      lastServicesKeyRef.current = servicesKey;

      clearMarkers();

      // 마커 개수 제한 (성능 최적화)
      const maxMarkers = 500;
      const servicesToShow = services.slice(0, maxMarkers);

      // 배치 처리로 성능 개선
      const batchSize = 50;
      let batchIndex = 0;

      const createMarkerBatch = () => {
        const start = batchIndex * batchSize;
        const end = Math.min(start + batchSize, servicesToShow.length);

        for (let i = start; i < end; i++) {
          const service = servicesToShow[i];
          if (typeof service.latitude !== 'number' || typeof service.longitude !== 'number') {
            continue;
          }

          const position = new window.naver.maps.LatLng(service.latitude, service.longitude);

          // 실종신고는 다른 색상 마커 사용
          const isMissingPet = service.type === 'missingPet';
          const markerIcon = isMissingPet
            ? {
              content: '<div style="width:20px;height:20px;background:#FF6B6B;border-radius:50%;border:2px solid #fff;box-shadow:0 2px 4px rgba(0,0,0,0.3);"></div>',
              anchor: new window.naver.maps.Point(10, 10),
            }
            : undefined; // 기본 마커 사용

          const marker = new window.naver.maps.Marker({
            position,
            map: mapInstanceRef.current,
            title: service.name || '서비스',
            icon: markerIcon,
          });

          window.naver.maps.Event.addListener(marker, 'click', () => {
            if (mapInstanceRef.current) {
              mapInstanceRef.current.panTo(position);
            }
            onServiceClick?.(service);
          });

          markersRef.current.push(marker);
        }

        batchIndex++;
        if (end < servicesToShow.length) {
          // 다음 배치를 비동기로 처리
          requestAnimationFrame(createMarkerBatch);
        } else {

          // 마커가 하나만 있고 mapCenter가 설정되어 있으면, 마커 위치로 지도 중심 조정
          if (markersRef.current.length === 1 && mapCenter && mapInstanceRef.current) {
            const marker = markersRef.current[0];
            const markerPosition = marker.getPosition();
            const currentCenter = mapInstanceRef.current.getCenter();

            // 마커 위치와 현재 중심이 다르면 마커 위치로 이동
            if (currentCenter && (
              Math.abs(currentCenter.lat() - markerPosition.lat()) > COORD_EPSILON ||
              Math.abs(currentCenter.lng() - markerPosition.lng()) > COORD_EPSILON
            )) {
              setTimeout(() => {
                if (mapInstanceRef.current && marker) {
                  mapInstanceRef.current.setCenter(markerPosition);
                }
              }, 100);
            }
          }
        }
      };

      createMarkerBatch();
    }, [services, onServiceClick, clearMarkers, mapCenter]);

    // 지도 중심 및 줌 변경 (프로그래밍 방식으로만 실행)
    useEffect(() => {
      if (!mapReadyRef.current || !mapInstanceRef.current || !mapCenter || !mapLevel) return;

      const map = mapInstanceRef.current;
      const currentCenter = map.getCenter();
      const currentZoom = map.getZoom();
      const isAlreadyAtCenter =
        currentCenter &&
        Math.abs(currentCenter.lat() - mapCenter.lat) < COORD_EPSILON &&
        Math.abs(currentCenter.lng() - mapCenter.lng) < COORD_EPSILON;
      const targetZoom = mapLevelToZoom(mapLevel);
      const isSameZoom = Math.abs(currentZoom - targetZoom) < 0.5; // 소수점 오차 허용

      console.log('MapContainer 줌 업데이트:', {
        currentZoom,
        targetZoom,
        mapLevel,
        isSameZoom,
        userZoomed: userZoomedRef.current
      });

      // mapLevel prop이 변경되었으면 무조건 userZoomedRef 리셋 (프로그래밍 방식 변경)
      // 사용자가 마우스 휠로 조정했더라도, mapLevel prop이 명시적으로 변경되었으면 줌 변경 허용
      userZoomedRef.current = false;

      // mapLevel이 변경되었고, 실제 줌이 다를 때만 강제로 줌 변경 (레벨 선택 드롭다운 변경 시)
      if (!isSameZoom) {
        map.setZoom(targetZoom);
        lastProgrammaticCenterRef.current = { ...mapCenter };
        if (!isAlreadyAtCenter) {
          setTimeout(() => {
            map.setCenter(new window.naver.maps.LatLng(mapCenter.lat, mapCenter.lng));
            lastProgrammaticCenterRef.current = { ...mapCenter };
            console.log('지도 줌 변경 완료:', mapCenter, '줌:', targetZoom, '레벨:', mapLevel);
          }, 300);
        } else {
          console.log('지도 줌 변경 완료 (중심 동일):', mapCenter, '줌:', targetZoom, '레벨:', mapLevel);
        }
        return;
      }

      // 줌은 같지만 중심이 다르면 중심만 이동
      if (!isAlreadyAtCenter) {
        lastProgrammaticCenterRef.current = { ...mapCenter };
        map.setCenter(new window.naver.maps.LatLng(mapCenter.lat, mapCenter.lng));
      } else {
        lastProgrammaticCenterRef.current = { ...mapCenter };
      }
    }, [mapCenter, mapLevel, mapLevelToZoom]);

    // 사용자 위치 마커
    useEffect(() => {
      if (!mapReadyRef.current || !mapInstanceRef.current || !userLocation || !window.naver?.maps) return;

      const position = new window.naver.maps.LatLng(userLocation.lat, userLocation.lng);

      if (!userMarkerRef.current) {
        userMarkerRef.current = new window.naver.maps.Marker({
          position,
          map: mapInstanceRef.current,
          icon: {
            content: '<div style="width:12px;height:12px;background:#4285F4;border-radius:50%;border:2px solid #fff;"></div>',
            anchor: new window.naver.maps.Point(6, 6),
          },
          title: '내 위치',
        });
      } else {
        userMarkerRef.current.setPosition(position);
      }
    }, [userLocation]);

    // 호버 마커
    useEffect(() => {
      if (!mapReadyRef.current || !mapInstanceRef.current || !window.naver?.maps) return;

      if (hoverMarkerRef.current) {
        hoverMarkerRef.current.setMap(null);
        hoverMarkerRef.current = null;
      }

      if (hoverMarker) {
        const position = new window.naver.maps.LatLng(hoverMarker.lat, hoverMarker.lng);
        hoverMarkerRef.current = new window.naver.maps.Marker({
          position,
          map: mapInstanceRef.current,
          icon: {
            content: '<div style="width:16px;height:16px;background:#FF6B6B;border-radius:50%;border:2px solid #fff;"></div>',
            anchor: new window.naver.maps.Point(8, 8),
          },
          title: hoverMarker.title || '호버된 지역',
        });
      }
    }, [hoverMarker]);

    // GeoJSON 폴리곤 표시 기능 제거됨 (geojsonUtils 파일 없음)

    // 정리
    useEffect(() => {
      return () => {
        clearMarkers();
        // clearRegionPolygons 제거됨 (GeoJSON 미사용)
        if (userMarkerRef.current) {
          userMarkerRef.current.setMap(null);
        }
        if (hoverMarkerRef.current) {
          hoverMarkerRef.current.setMap(null);
        }
      };
    }, [clearMarkers]);

    const handleZoomIn = useCallback(() => {
      if (mapInstanceRef.current) {
        // 수동 조작임을 표시하여 자동 이동 방지
        lastProgrammaticCenterRef.current = null;
        userZoomedRef.current = true; // 사용자가 직접 줌 조정
        const currentZoom = mapInstanceRef.current.getZoom();
        mapInstanceRef.current.setZoom(currentZoom + 1);
      }
    }, []);

    const handleZoomOut = useCallback(() => {
      if (mapInstanceRef.current) {
        // 수동 조작임을 표시하여 자동 이동 방지
        lastProgrammaticCenterRef.current = null;
        userZoomedRef.current = true; // 사용자가 직접 줌 조정
        const currentZoom = mapInstanceRef.current.getZoom();
        mapInstanceRef.current.setZoom(currentZoom - 1);
      }
    }, []);

    if (!NAVER_MAPS_KEY_ID) {
      return (
        <MapDiv ref={mapRef}>
          <MapError>
            네이버맵 Key ID가 설정되지 않았습니다.<br />
            .env 파일에 REACT_APP_NAVER_MAPS_KEY_ID를 확인하세요.
          </MapError>
        </MapDiv>
      );
    }

    if (!mapReady) {
      return (
        <MapDiv ref={mapRef}>
          <MapLoading>🗺️ 지도를 불러오는 중...</MapLoading>
        </MapDiv>
      );
    }

    return (
      <MapDiv ref={mapRef}>
        <ZoomControls>
          <ZoomButton onClick={handleZoomIn} title="확대">
            <ZoomIcon>+</ZoomIcon>
          </ZoomButton>
          <ZoomButton onClick={handleZoomOut} title="축소">
            <ZoomIcon>−</ZoomIcon>
          </ZoomButton>
        </ZoomControls>
      </MapDiv>
    );
  }
);

MapContainer.displayName = 'MapContainer';
export default MapContainer;

const MapDiv = styled.div`
  width: 100%;
  height: 100%;
  min-height: 500px;
  position: relative;
  background: #ffffff;
`;

const MapLoading = styled.div`
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  padding: 1rem 1.5rem;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 10px 25px rgba(15, 23, 42, 0.15);
  font-weight: 600;
  color: #2563eb;
`;

const MapError = styled.div`
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  padding: 1rem 1.5rem;
  border-radius: 8px;
  background: rgba(239, 68, 68, 0.1);
  color: #dc2626;
  font-weight: 600;
  text-align: center;
  max-width: 400px;
`;

const ZoomControls = styled.div`
  position: absolute;
  top: 20px;
  right: 20px;
  z-index: 1000;
  display: flex;
  flex-direction: column;
  gap: 4px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  overflow: hidden;
`;

const ZoomButton = styled.button`
  width: 48px;
  height: 48px;
  border: none;
  background: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  padding: 0;

  &:hover {
    background: #f3f4f6;
  }

  &:active {
    background: #e5e7eb;
  }

  &:first-child {
    border-bottom: 1px solid #e5e7eb;
  }
`;

const ZoomIcon = styled.span`
  font-size: 28px;
  font-weight: 300;
  color: #374151;
  line-height: 1;
  user-select: none;
`;
