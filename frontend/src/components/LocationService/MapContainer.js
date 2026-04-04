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
  ({ services = [], onServiceClick, userLocation, mapCenter, mapLevel, onMapDragStart, onMapIdle, hoverMarker = null, currentMapView = 'nation', selectedSido = null, selectedSigungu = null, selectedEupmyeondong = null, onRegionClick = null, onMapClick = null, selectedService = null, hoveredService = null, recommendedServiceIdxs = null }, ref) => {
    const mapRef = useRef(null);
    const mapInstanceRef = useRef(null);
    const markersRef = useRef([]);
    const markerClusterRef = useRef(null); // MarkerClusterer 인스턴스
    const userMarkerRef = useRef(null);
    const hoverMarkerRef = useRef(null);
    const lastProgrammaticCenterRef = useRef(null);
    const mapReadyRef = useRef(false);
    const [mapReady, setMapReady] = useState(false);
    const userZoomedRef = useRef(false); // 사용자가 직접 줌 조정했는지 여부
    const clustererReadyRef = useRef(false); // MarkerClusterer 로드 여부
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
          logoControl: false, // 네이버맵 로고 숨기기
          mapDataControl: false, // 지도 데이터 컨트롤 숨기기
          scaleControl: false, // 스케일 컨트롤 숨기기
          scrollWheel: false, // 마우스 휠 확대/축소 비활성화
          disableDoubleClickZoom: false, // 더블클릭 확대 활성화
          disableDoubleClick: false,
        };

        const map = new window.naver.maps.Map(mapRef.current, mapOptions);
        mapInstanceRef.current = map;
        lastProgrammaticCenterRef.current = initial;
        mapReadyRef.current = true;
        setMapReady(true);

        // 네이버맵 로고 및 저작권 표시 숨기기 (지도 로드 후)
        setTimeout(() => {
          const copyrightElements = mapRef.current?.querySelectorAll('.nmap_copyright, .nmap_logo, [class*="nmap"][class*="copyright"], [class*="nmap"][class*="logo"]');
          if (copyrightElements) {
            copyrightElements.forEach((el) => {
              if (el instanceof HTMLElement) {
                el.style.display = 'none';
              }
            });
          }

          // 네이버맵 인증 관련 요소 숨기기
          const authElements = document.querySelectorAll('iframe[src*="oapi.map.naver.com"], iframe[src*="auth"], a[href*="oapi.map.naver.com"], a[href*="auth"]');
          authElements.forEach((el) => {
            if (el instanceof HTMLElement) {
              el.style.display = 'none';
              el.style.visibility = 'hidden';
              el.style.opacity = '0';
              el.style.width = '0';
              el.style.height = '0';
              el.style.position = 'absolute';
              el.style.left = '-9999px';
            }
          });
        }, 500);

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
            // 네이버맵 API에서 e.coord 또는 e.latlng로 좌표 전달
            // 호환성을 위해 coord와 latlng 둘 다 제공
            const coord = e.coord || e.latlng;
            if (coord) {
              onMapClick({
                coord: coord,
                latlng: coord, // 호환성을 위해 둘 다 제공
              });
            } else {
              onMapClick(e);
            }
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
      // 클러스터 마커 제거
      if (markerClusterRef.current) {
        if (markerClusterRef.current.markers) {
          // 직접 구현한 클러스터 마커
          markerClusterRef.current.markers.forEach((marker) => {
            if (marker.setMap) marker.setMap(null);
          });
        } else if (markerClusterRef.current.clear) {
          // MarkerClusterer 라이브러리 사용 시
          markerClusterRef.current.clear();
        }
        markerClusterRef.current = null;
      }
      // 개별 마커도 정리
      markersRef.current.forEach((marker) => {
        if (marker.setMap) marker.setMap(null);
      });
      markersRef.current = [];
    }, []);

    // 지역 폴리곤 정리 함수 제거됨 (GeoJSON 미사용)



    // 서비스 마커 표시 - 클러스터링 사용
    const lastServicesKeyRef = useRef('');
    const lastZoomRef = useRef(null);
    const lastBoundsRef = useRef(null);
    const servicesRef = useRef(services);
    const selectedServiceRef = useRef(selectedService);

    // services와 selectedService를 ref에 동기화
    useEffect(() => {
      servicesRef.current = services;
      selectedServiceRef.current = selectedService;
    }, [services, selectedService]);

    // 마커 업데이트 함수
    // 마커 업데이트 함수
    const updateMarkers = useCallback(() => {
      if (!mapReadyRef.current || !mapInstanceRef.current || !window.naver?.maps) return;

      try {
        const currentServices = servicesRef.current;
        const currentSelectedService = selectedServiceRef.current;
        const currentHoveredService = hoverMarkerRef.current?.service || hoveredService; // props.hoveredService -> hoveredService 수정
        const currentZoom = mapInstanceRef.current.getZoom();
        const currentBounds = mapInstanceRef.current.getBounds();

        // 서비스가 변경되지 않고, 줌과 bounds도 변경되지 않았으면 스킵 (단, 호버 상태 변경 시에는 업데이트 필요)
        // 호버 상태 변경 감지를 위해 의존성 배열에 hoveredService 추가 필요하지만,
        // 여기서는 useEffect에서 호출되므로 괜찮음.
        const servicesKey = currentServices.map(s => `${s.latitude},${s.longitude}`).join('|');
        // const zoomChanged = lastZoomRef.current !== currentZoom;
        // const boundsChanged = ...

        // 강제 업데이트가 필요할 수 있으므로 최적화 로직 잠시 완화 또는 호버 ID 포함
        // (실제로는 useEffect에서 호출되므로 로직 단순화)
        
        lastServicesKeyRef.current = servicesKey;
        lastZoomRef.current = currentZoom;
        lastBoundsRef.current = currentBounds;

        clearMarkers();

        // 유효한 좌표를 가진 서비스만 필터링
        const validServices = currentServices.filter(
          service => typeof service.latitude === 'number' && typeof service.longitude === 'number'
        );

        if (validServices.length === 0) return;

        // 통합 핀 아이콘 생성 함수 (SVG)
        // type: 'normal' | 'selected' | 'hovered' | 'missing' | 'top'
        // rank: 'top' 타입일 때 순위 숫자 (1~10)
        const createPinIcon = (type, rank = null) => {
          let color = '#03C75A'; // 기본 녹색
          let scale = 1;
          let zIndex = 100;

          if (type === 'missing') {
            color = '#FF6B6B'; // 실종: 빨강
            zIndex = 150;
          } else if (type === 'top') {
            color = '#F5A623'; // AI 추천: 금색
            scale = 1.15;
            zIndex = 500;
          }

          if (type === 'selected') {
            color = '#028A48'; // 선택: 진한 녹색
            scale = 1.25;
            zIndex = 1000;
          } else if (type === 'hovered') {
            scale = 1.25; // 호버 시 확대
            zIndex = 900;
          }

          // SVG 핀 아이콘 (더 날렵한 비율: 26x36)
          const width = 26 * scale;
          const height = 36 * scale;

          // 내부 컨텐츠: 순위 숫자(top) 또는 흰 점
          const innerContent = (type === 'top' && rank != null)
            ? `<text x="12" y="16" text-anchor="middle" dominant-baseline="middle" font-size="10" font-weight="bold" fill="white">${rank}</text>`
            : `<circle cx="12" cy="12" r="5" fill="white"/>`;

          // SVG Path: 날렵한 핀 모양
           const svgContent = `
            <svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 24 34" fill="none">
              <path fill="${color}" d="M12 0C5.37258 0 0 5.37258 0 12C0 19 12 34 12 34C12 34 24 19 24 12C24 5.37258 18.6274 0 12 0Z"/>
              ${innerContent}
            </svg>
          `;

          return {
            content: `<div style="cursor:pointer; filter: drop-shadow(0px 2px 4px rgba(0,0,0,0.25));">${svgContent}</div>`,
            anchor: new window.naver.maps.Point(width / 2, height),
            zIndex: zIndex
          };
        };

        // recommendedServiceIdxs: Map<idx, rank> (AI 추천 결과, rank는 1-based)
        const recommendedMap = recommendedServiceIdxs instanceof Map ? recommendedServiceIdxs : null;

        // 모든 서비스에 대해 개별 마커 생성 (클러스터링 비활성화 - 사용자 요청)
        const individualMarkersList = validServices.map((service) => {
          const position = new window.naver.maps.LatLng(service.latitude, service.longitude);

          const isSelected = currentSelectedService && (
            (currentSelectedService.idx && service.idx === currentSelectedService.idx) ||
            (currentSelectedService.externalId && service.idx === currentSelectedService.externalId) ||
            (currentSelectedService.latitude && currentSelectedService.latitude === service.latitude &&
              currentSelectedService.longitude && currentSelectedService.longitude === service.longitude)
          );

          const isHovered = hoveredService && (
             (hoveredService.idx && service.idx === hoveredService.idx) ||
             (hoveredService.externalId && service.idx === hoveredService.externalId) ||
             (hoveredService.key && service.key === hoveredService.key)
          );

          const recommendedRank = recommendedMap?.get(service.idx) ?? null;

          let type = 'normal';
          if (service.type === 'missingPet') type = 'missing';
          else if (recommendedRank != null) type = 'top';
          if (isSelected) type = 'selected';
          else if (isHovered && type !== 'top') type = 'hovered';

          // 개별 마커용 핀 아이콘
          const markerIcon = createPinIcon(type, recommendedRank);

          const marker = new window.naver.maps.Marker({
            position,
            map: mapInstanceRef.current,
            title: service.name || '서비스',
            icon: markerIcon,
            zIndex: markerIcon.zIndex,
          });

          window.naver.maps.Event.addListener(marker, 'click', () => {
            if (mapInstanceRef.current) {
               // 클릭 시 부드럽게 이동
              mapInstanceRef.current.panTo(position, { duration: 300 });
            }
            onServiceClick?.(service);
          });

          // 마커 호버 이벤트 (필요 시)
          // window.naver.maps.Event.addListener(marker, 'mouseover', () => { ... });

          return marker;
        });

        markersRef.current = individualMarkersList;
        markerClusterRef.current = null; // 클러스터 없음

        // 1개 마커 중심 이동 로직 (기존 유지)
        if (validServices.length === 1 && mapCenter && mapInstanceRef.current && markersRef.current.length > 0) {
          const marker = markersRef.current[0];
          const markerPosition = marker.getPosition();
          const currentCenter = mapInstanceRef.current.getCenter();

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
      } catch (error) {
        console.error('마커 업데이트 중 오류:', error);
      }
    }, [onServiceClick, clearMarkers, mapCenter, hoveredService]); // props.hoveredService -> hoveredService 수정

    // 마커 업데이트 실행 (서비스 변경 시)
    useEffect(() => {
      if (mapReadyRef.current && mapInstanceRef.current) {
        updateMarkers();
      }
    }, [services, selectedService, updateMarkers]);

    // 지도 줌/이동 시 마커 재계산 (한 번만 등록)
    useEffect(() => {
      if (!mapReadyRef.current || !mapInstanceRef.current || !window.naver?.maps) return;

      const map = mapInstanceRef.current;
      let timeoutId = null;

      const handleMapChange = () => {
        if (timeoutId) clearTimeout(timeoutId);
        timeoutId = setTimeout(() => {
          if (mapReadyRef.current && mapInstanceRef.current) {
            updateMarkers();
          }
        }, 300); // 디바운스
      };

      window.naver.maps.Event.addListener(map, 'zoom_changed', handleMapChange);
      window.naver.maps.Event.addListener(map, 'bounds_changed', handleMapChange);

      return () => {
        if (timeoutId) clearTimeout(timeoutId);
        try {
          window.naver.maps.Event.removeListener(map, 'zoom_changed', handleMapChange);
          window.naver.maps.Event.removeListener(map, 'bounds_changed', handleMapChange);
        } catch (error) {
          // 무시 (이미 제거되었을 수 있음)
        }
      };
    }, [mapReady, updateMarkers]);

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

    // 네이버맵 인증 관련 요소 주기적으로 숨기기 (동적으로 생성될 수 있음)
    useEffect(() => {
      if (!mapReadyRef.current) return;

      const hideAuthElements = () => {
        const authElements = document.querySelectorAll('iframe[src*="oapi.map.naver.com"], iframe[src*="auth"], a[href*="oapi.map.naver.com"], a[href*="auth"]');
        authElements.forEach((el) => {
          if (el instanceof HTMLElement) {
            el.style.display = 'none';
            el.style.visibility = 'hidden';
            el.style.opacity = '0';
            el.style.width = '0';
            el.style.height = '0';
            el.style.position = 'absolute';
            el.style.left = '-9999px';
          }
        });
      };

      // 주기적으로 인증 관련 요소 숨기기
      const hideAuthInterval = setInterval(hideAuthElements, 1000);

      // 초기 실행
      hideAuthElements();

      return () => {
        clearInterval(hideAuthInterval);
      };
    }, [mapReady]);

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
  overflow: hidden; /* 인증 URL 등이 밖으로 나오지 않도록 */

  /* 네이버맵 저작권 표시 숨기기 */
  .nmap_copyright,
  .nmap_logo,
  .nmap_control {
    display: none !important;
  }

  /* 네이버맵 로고 및 저작권 영역 숨기기 */
  div[class*="nmap"],
  div[class*="naver"] {
    &[class*="copyright"],
    &[class*="logo"],
    &[class*="control"] {
      display: none !important;
    }
  }

  /* 네이버맵 인증 관련 요소 숨기기 */
  iframe[src*="oapi.map.naver.com"],
  iframe[src*="auth"],
  script[src*="oapi.map.naver.com"],
  a[href*="oapi.map.naver.com"],
  a[href*="auth"] {
    display: none !important;
    visibility: hidden !important;
    opacity: 0 !important;
    width: 0 !important;
    height: 0 !important;
    position: absolute !important;
    left: -9999px !important;
  }
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
