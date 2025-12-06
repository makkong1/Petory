import React, { useCallback, useEffect, useRef, useState } from 'react';
import styled from 'styled-components';

const DEFAULT_CENTER = { lat: 36.5, lng: 127.5 }; // 대한민국 중심 좌표
const DEFAULT_ZOOM = 7; // 전국이 보이도록 줌 레벨 7로 설정
const COORD_EPSILON = 0.00001;

// 네이버맵 API 키 (환경변수에서 가져오거나 직접 설정)
// 최신 버전에서는 ncpKeyId를 사용합니다
const NAVER_MAPS_KEY_ID = process.env.REACT_APP_NAVER_MAPS_KEY_ID || process.env.REACT_APP_NAVER_MAPS_CLIENT_ID || '';

const MapContainer = React.forwardRef(
  ({ services = [], onServiceClick, userLocation, mapCenter, mapLevel, onMapDragStart, onMapIdle, hoverMarker = null, currentMapView = 'nation', selectedSido = null, selectedSigungu = null, onRegionClick = null }, ref) => {
    const mapRef = useRef(null);
    const mapInstanceRef = useRef(null);
    const markersRef = useRef([]);
    const userMarkerRef = useRef(null);
    const hoverMarkerRef = useRef(null);
    const regionPolygonsRef = useRef([]); // 지역 폴리곤
    const lastProgrammaticCenterRef = useRef(null);
    const mapReadyRef = useRef(false);
    const [mapReady, setMapReady] = useState(false);

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
        const initialZoom = mapLevel ? mapLevelToZoom(mapLevel) : DEFAULT_ZOOM;

        const mapOptions = {
          center: new window.naver.maps.LatLng(initial.lat, initial.lng),
          zoom: initialZoom,
          zoomControl: false, // 기본 컨트롤 비활성화 (커스텀 버튼 사용)
          scrollWheel: true, // 마우스 휠 확대/축소 활성화
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

        // 지도 클릭 이벤트로 폴리곤 클릭 감지 (폴리곤 직접 클릭이 안 될 때 대비)
        const sidoPolygonsMap = new Map(); // 폴리곤 저장용
        window.naver.maps.Event.addListener(map, 'click', (e) => {
          const clickPoint = e.coord;
          // 클릭한 위치가 어떤 폴리곤 안에 있는지 확인
          sidoPolygonsMap.forEach((polygonData, sidoName) => {
            const polygon = polygonData.polygon;
            if (polygon && window.naver.maps.geometry.polygon) {
              const isInside = window.naver.maps.geometry.polygon.containsLocation(clickPoint, polygon);
              if (isInside) {
                console.log('지도 클릭으로 폴리곤 감지:', sidoName);
                if (onRegionClick) {
                  onRegionClick('sido', sidoName);
                }
              }
            }
          });
        });

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

      const script = document.createElement('script');
      // 최신 버전에서는 ncpKeyId를 사용합니다
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
        console.error('Key ID:', NAVER_MAPS_KEY_ID);
        console.error('가능한 원인:');
        console.error('1. 네이버 클라우드 플랫폼에서 Maps API가 활성화되지 않았습니다.');
        console.error('2. Key ID가 잘못되었거나 불완전합니다.');
        console.error('3. 웹 서비스 URL이 등록되지 않았습니다.');
        console.error('   - localhost는 자동 허용되지만, 다른 도메인은 등록이 필요합니다.');
        console.error('   - 네이버 클라우드 플랫폼 > Application > Web Service URL에 등록하세요.');
      };
      document.head.appendChild(script);

      return () => {
        const existingScript = document.querySelector(`script[src*="openapi.map.naver.com"]`);
        if (existingScript) {
          document.head.removeChild(existingScript);
        }
      };
    }, [ensureMap]);

    // 마커 정리
    const clearMarkers = useCallback(() => {
      markersRef.current.forEach((marker) => {
        if (marker.setMap) marker.setMap(null);
      });
      markersRef.current = [];
    }, []);

    // 지역 폴리곤 정리
    const clearRegionPolygons = useCallback(() => {
      regionPolygonsRef.current.forEach((polygon) => {
        if (polygon.setMap) polygon.setMap(null);
      });
      regionPolygonsRef.current = [];
    }, []);

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
          const marker = new window.naver.maps.Marker({
            position,
            map: mapInstanceRef.current,
            title: service.name || '서비스',
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
        }
      };

      createMarkerBatch();
    }, [services, onServiceClick, clearMarkers]);

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
      const isSameZoom = currentZoom === targetZoom;

      // 이미 목표 위치와 줌에 있으면 스킵
      if (isAlreadyAtCenter && isSameZoom) {
        // 프로그래밍 방식으로 이동한 경우 ref 설정
        lastProgrammaticCenterRef.current = { ...mapCenter };
        return;
      }

      // mapCenter/mapLevel이 변경되었으므로 프로그래밍 방식으로 간주
      // (LocationServiceMap에서 setMapCenter/setMapLevel을 호출했을 때)
      // lastProgrammaticCenterRef를 먼저 설정하여 수동 조작이 아님을 표시
      lastProgrammaticCenterRef.current = { ...mapCenter };

      // 줌을 먼저 설정하고 중심 이동
      if (!isSameZoom) {
        map.setZoom(targetZoom);
        // 줌 변경 후 중심 이동 (더 긴 지연으로 안정성 확보)
        setTimeout(() => {
          map.setCenter(new window.naver.maps.LatLng(mapCenter.lat, mapCenter.lng));
          // 중심 이동 후에도 ref 유지
          lastProgrammaticCenterRef.current = { ...mapCenter };
          console.log('지도 확대 완료:', mapCenter, '줌:', targetZoom);
        }, 300); // 200ms -> 300ms로 증가하여 줌 변경 완료 대기
      } else {
        map.setCenter(new window.naver.maps.LatLng(mapCenter.lat, mapCenter.lng));
        console.log('지도 중심 이동 완료:', mapCenter);
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

    // 지역 폴리곤 표시 (계층적 지도 탐색) - 디바운싱 적용
    useEffect(() => {
      if (!mapReadyRef.current || !mapInstanceRef.current || !window.naver?.maps) return;

      // 디바운싱: 줌 변경 시 약간의 지연 후 폴리곤 업데이트
      const timeoutId = setTimeout(() => {
        clearRegionPolygons();

        const map = mapInstanceRef.current;
        const currentZoom = map.getZoom();

        // 전국 뷰: 시/도 중심에 클릭 가능한 폴리곤 영역 표시
        if (currentMapView === 'nation' && currentZoom <= 8) {
          // 각 시/도의 실제 경계 좌표 (실제 모양에 가깝게)
          const SIDO_BOUNDARIES = {
            '강원특별자치도': [
              { lat: 38.6, lng: 127.0 }, { lat: 38.5, lng: 128.0 }, { lat: 38.3, lng: 128.5 },
              { lat: 38.0, lng: 128.8 }, { lat: 37.7, lng: 129.0 }, { lat: 37.5, lng: 129.2 },
              { lat: 37.2, lng: 129.0 }, { lat: 37.0, lng: 128.8 }, { lat: 37.0, lng: 128.2 },
              { lat: 37.2, lng: 127.8 }, { lat: 37.3, lng: 127.5 }, { lat: 37.5, lng: 127.2 },
              { lat: 37.8, lng: 127.0 }, { lat: 38.0, lng: 127.0 }, { lat: 38.3, lng: 127.0 }
            ],
            '서울특별시': [
              { lat: 37.701, lng: 126.734 }, { lat: 37.701, lng: 127.183 },
              { lat: 37.428, lng: 127.183 }, { lat: 37.428, lng: 126.734 }
            ],
            '경기도': [
              { lat: 38.25, lng: 126.4 }, { lat: 38.25, lng: 127.0 }, { lat: 38.2, lng: 127.5 },
              { lat: 38.1, lng: 127.8 }, { lat: 37.8, lng: 127.9 }, { lat: 37.5, lng: 127.8 },
              { lat: 37.2, lng: 127.6 }, { lat: 37.0, lng: 127.4 }, { lat: 37.0, lng: 127.0 },
              { lat: 37.1, lng: 126.9 }, { lat: 37.2, lng: 126.7 }, { lat: 37.3, lng: 126.5 },
              { lat: 37.5, lng: 126.4 }, { lat: 37.8, lng: 126.4 }, { lat: 38.0, lng: 126.4 }
            ],
            '인천광역시': [
              { lat: 37.65, lng: 126.25 }, { lat: 37.65, lng: 126.85 },
              { lat: 37.35, lng: 126.85 }, { lat: 37.35, lng: 126.25 }
            ],
            '충청북도': [
              { lat: 37.6, lng: 127.0 }, { lat: 37.6, lng: 127.5 }, { lat: 37.5, lng: 128.0 },
              { lat: 37.3, lng: 128.3 }, { lat: 37.0, lng: 128.5 }, { lat: 36.7, lng: 128.5 },
              { lat: 36.4, lng: 128.3 }, { lat: 36.2, lng: 128.0 }, { lat: 36.1, lng: 127.5 },
              { lat: 36.2, lng: 127.2 }, { lat: 36.5, lng: 127.0 }, { lat: 36.8, lng: 127.0 },
              { lat: 37.2, lng: 127.0 }
            ],
            '충청남도': [
              { lat: 36.9, lng: 125.9 }, { lat: 36.9, lng: 126.3 }, { lat: 36.8, lng: 126.8 },
              { lat: 36.8, lng: 127.3 }, { lat: 36.6, lng: 127.5 }, { lat: 36.3, lng: 127.5 },
              { lat: 36.0, lng: 127.3 }, { lat: 36.0, lng: 126.8 }, { lat: 36.1, lng: 126.5 },
              { lat: 36.2, lng: 126.2 }, { lat: 36.4, lng: 126.0 }, { lat: 36.6, lng: 125.9 }
            ],
            '전북특별자치도': [
              { lat: 36.3, lng: 126.4 }, { lat: 36.3, lng: 126.8 }, { lat: 36.2, lng: 127.2 },
              { lat: 36.2, lng: 127.6 }, { lat: 36.0, lng: 127.8 }, { lat: 35.7, lng: 127.8 },
              { lat: 35.4, lng: 127.6 }, { lat: 35.2, lng: 127.4 }, { lat: 35.2, lng: 127.0 },
              { lat: 35.3, lng: 126.8 }, { lat: 35.4, lng: 126.6 }, { lat: 35.6, lng: 126.4 },
              { lat: 35.9, lng: 126.4 }
            ],
            '전라남도': [
              { lat: 35.6, lng: 125.9 }, { lat: 35.6, lng: 126.3 }, { lat: 35.5, lng: 126.8 },
              { lat: 35.5, lng: 127.2 }, { lat: 35.3, lng: 127.5 }, { lat: 35.0, lng: 127.5 },
              { lat: 34.7, lng: 127.3 }, { lat: 34.4, lng: 127.0 }, { lat: 34.2, lng: 126.5 },
              { lat: 34.2, lng: 125.8 }, { lat: 34.4, lng: 125.5 }, { lat: 34.7, lng: 125.6 },
              { lat: 35.0, lng: 125.8 }, { lat: 35.3, lng: 125.9 }
            ],
            '광주광역시': [
              { lat: 35.28, lng: 126.62 }, { lat: 35.28, lng: 126.92 },
              { lat: 35.05, lng: 126.92 }, { lat: 35.05, lng: 126.62 }
            ],
            '대전광역시': [
              { lat: 36.52, lng: 127.18 }, { lat: 36.52, lng: 127.48 },
              { lat: 36.18, lng: 127.48 }, { lat: 36.18, lng: 127.18 }
            ],
            '세종특별자치시': [
              { lat: 36.65, lng: 127.08 }, { lat: 36.65, lng: 127.42 },
              { lat: 36.28, lng: 127.42 }, { lat: 36.28, lng: 127.08 }
            ],
            '부산광역시': [
              { lat: 35.32, lng: 128.85 }, { lat: 35.32, lng: 129.25 },
              { lat: 35.05, lng: 129.25 }, { lat: 35.05, lng: 128.85 }
            ],
            '울산광역시': [
              { lat: 35.72, lng: 129.08 }, { lat: 35.72, lng: 129.52 },
              { lat: 35.28, lng: 129.52 }, { lat: 35.28, lng: 129.08 }
            ],
            '대구광역시': [
              { lat: 36.05, lng: 128.28 }, { lat: 36.05, lng: 128.72 },
              { lat: 35.68, lng: 128.72 }, { lat: 35.68, lng: 128.28 }
            ],
            '경상북도': [
              { lat: 37.1, lng: 127.9 }, { lat: 37.1, lng: 128.3 }, { lat: 37.0, lng: 128.7 },
              { lat: 36.8, lng: 129.0 }, { lat: 36.5, lng: 129.3 }, { lat: 36.2, lng: 129.5 },
              { lat: 35.9, lng: 129.5 }, { lat: 35.6, lng: 129.3 }, { lat: 35.5, lng: 129.0 },
              { lat: 35.5, lng: 128.6 }, { lat: 35.7, lng: 128.3 }, { lat: 36.0, lng: 128.0 },
              { lat: 36.3, lng: 127.9 }, { lat: 36.6, lng: 127.9 }, { lat: 36.9, lng: 127.9 }
            ],
            '경상남도': [
              { lat: 35.9, lng: 127.7 }, { lat: 35.9, lng: 128.2 }, { lat: 35.8, lng: 128.6 },
              { lat: 35.6, lng: 129.0 }, { lat: 35.3, lng: 129.2 }, { lat: 35.0, lng: 129.2 },
              { lat: 34.7, lng: 129.0 }, { lat: 34.5, lng: 128.6 }, { lat: 34.5, lng: 128.2 },
              { lat: 34.6, lng: 127.9 }, { lat: 34.8, lng: 127.7 }, { lat: 35.1, lng: 127.7 },
              { lat: 35.4, lng: 127.7 }, { lat: 35.7, lng: 127.7 }
            ],
            '제주특별자치도': [
              { lat: 33.6, lng: 126.15 }, { lat: 33.6, lng: 126.95 },
              { lat: 33.15, lng: 126.95 }, { lat: 33.15, lng: 126.15 }
            ],
          };

          // 각 시/도의 실제 경계를 따라 폴리곤 생성
          const sidoPolygonsMap = new Map(); // 폴리곤 저장용 (지도 클릭 이벤트에서 사용)

          Object.entries(SIDO_BOUNDARIES).forEach(([sidoName, coordinates]) => {
            const paths = coordinates.map(coord =>
              new window.naver.maps.LatLng(coord.lat, coord.lng)
            );

            const polygon = new window.naver.maps.Polygon({
              map: mapInstanceRef.current,
              paths: paths,
              fillColor: '#75B8FA',
              fillOpacity: 0.1, // 옅지만 보이도록 (0.05 -> 0.1)
              strokeColor: '#75B8FA',
              strokeOpacity: 0.5, // 옅지만 보이도록 (0.3 -> 0.5)
              strokeWeight: 1.5, // 얇지만 보이도록 (1 -> 1.5)
              clickable: true, // 클릭 가능하도록 명시
              zIndex: 100, // 다른 레이어 위에 표시
            });

            // 폴리곤을 Map에 저장 (지도 클릭 이벤트에서 사용)
            sidoPolygonsMap.set(sidoName, { polygon, paths });

            // 클릭 이벤트 핸들러
            const handlePolygonClick = (e) => {
              // 네이버맵 이벤트 객체는 stopPropagation이 없을 수 있음
              if (e && typeof e.stopPropagation === 'function') {
                e.stopPropagation(); // 이벤트 전파 방지
              }
              console.log('폴리곤 클릭 감지:', sidoName);
              if (onRegionClick) {
                onRegionClick('sido', sidoName);
              }
            };

            // 네이버맵 폴리곤 클릭 이벤트 (여러 이벤트 타입 시도)
            window.naver.maps.Event.addListener(polygon, 'click', handlePolygonClick);
            window.naver.maps.Event.addListener(polygon, 'mousedown', (e) => {
              if (e && typeof e.stopPropagation === 'function') {
                e.stopPropagation();
              }
              handlePolygonClick(e);
            });

            // 호버 효과 (옅지만 보이도록 조정)
            window.naver.maps.Event.addListener(polygon, 'mouseover', () => {
              polygon.setOptions({
                fillOpacity: 0.25, // 호버 시 약간 진하게 (0.1 -> 0.25)
                strokeWeight: 2, // 호버 시 약간 두껍게 (1.5 -> 2)
                strokeOpacity: 0.7, // 호버 시 약간 진하게 (0.5 -> 0.7)
              });
            });

            window.naver.maps.Event.addListener(polygon, 'mouseout', () => {
              polygon.setOptions({
                fillOpacity: 0.1, // 원래대로
                strokeWeight: 1.5, // 원래대로
                strokeOpacity: 0.5, // 원래대로
              });
            });

            regionPolygonsRef.current.push(polygon);
          });

          // 지도 클릭 이벤트로 폴리곤 감지 (폴리곤 직접 클릭이 안 될 때 대비)
          const mapClickHandler = (e) => {
            const clickPoint = e.coord;
            // 클릭한 위치가 어떤 폴리곤 안에 있는지 확인
            sidoPolygonsMap.forEach((polygonData, sidoName) => {
              const polygon = polygonData.polygon;
              // 네이버맵 geometry API 사용
              if (polygon && window.naver.maps.geometry && window.naver.maps.geometry.polygon) {
                try {
                  const isInside = window.naver.maps.geometry.polygon.containsLocation(clickPoint, polygon);
                  if (isInside) {
                    console.log('지도 클릭으로 폴리곤 감지:', sidoName);
                    if (onRegionClick) {
                      onRegionClick('sido', sidoName);
                    }
                    return; // 첫 번째 매칭되는 폴리곤만 처리
                  }
                } catch (err) {
                  console.warn('폴리곤 위치 확인 실패:', err);
                }
              }
            });
          };

          // 지도 클릭 이벤트 등록
          window.naver.maps.Event.addListener(map, 'click', mapClickHandler);
        }
      }, 300); // 300ms 디바운싱

      return () => {
        clearTimeout(timeoutId);
      };
    }, [currentMapView, mapLevel, selectedSido, selectedSigungu, onRegionClick, clearRegionPolygons]);

    // 정리
    useEffect(() => {
      return () => {
        clearMarkers();
        clearRegionPolygons();
        if (userMarkerRef.current) {
          userMarkerRef.current.setMap(null);
        }
        if (hoverMarkerRef.current) {
          hoverMarkerRef.current.setMap(null);
        }
      };
    }, [clearMarkers, clearRegionPolygons]);

    const handleZoomIn = useCallback(() => {
      if (mapInstanceRef.current) {
        // 수동 조작임을 표시하여 자동 이동 방지
        lastProgrammaticCenterRef.current = null;
        const currentZoom = mapInstanceRef.current.getZoom();
        mapInstanceRef.current.setZoom(currentZoom + 1);
      }
    }, []);

    const handleZoomOut = useCallback(() => {
      if (mapInstanceRef.current) {
        // 수동 조작임을 표시하여 자동 이동 방지
        lastProgrammaticCenterRef.current = null;
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
