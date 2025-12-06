import React, { useCallback, useEffect, useRef, useState } from 'react';
import styled from 'styled-components';
import {
  loadGeoJSON,
  groupBySido,
  groupBySigungu,
  groupByDong,
  convertCoordinatesToPaths,
  getAllSidoCodes,
  loadSidoGeoJSON,
  loadSigunguGeoJSON,
  loadDongGeoJSON,
  getSidoCode,
  getSidoName,
  getSigunguCodeByName,
  getSigunguCodesBySidoCode,
  getDongCodesBySigungu,
  getBoundingBox,
  calculateZoomFromBoundingBox
} from '../../utils/geojsonUtils';

const DEFAULT_CENTER = { lat: 36.5, lng: 127.5 }; // 대한민국 중심 좌표
const DEFAULT_ZOOM = 8; // 전국이 보이도록 줌 레벨 8로 설정 (카카오맵 레벨 13과 동일)
const COORD_EPSILON = 0.00001;

// 네이버맵 API 키 (환경변수에서 가져오거나 직접 설정)
// 최신 버전에서는 ncpKeyId를 사용합니다
const NAVER_MAPS_KEY_ID = process.env.REACT_APP_NAVER_MAPS_KEY_ID || process.env.REACT_APP_NAVER_MAPS_CLIENT_ID || '';

const MapContainer = React.forwardRef(
  ({ services = [], onServiceClick, userLocation, mapCenter, mapLevel, onMapDragStart, onMapIdle, hoverMarker = null, currentMapView = 'nation', selectedSido = null, selectedSigungu = null, selectedEupmyeondong = null, onRegionClick = null, onMapClick = null }, ref) => {
    const mapRef = useRef(null);
    const mapInstanceRef = useRef(null);
    const markersRef = useRef([]);
    const userMarkerRef = useRef(null);
    const hoverMarkerRef = useRef(null);
    const regionPolygonsRef = useRef([]); // 지역 폴리곤
    const lastProgrammaticCenterRef = useRef(null);
    const mapReadyRef = useRef(false);
    const [mapReady, setMapReady] = useState(false);
    const geoJsonDataRef = useRef(null); // GeoJSON 데이터 캐시
    const loadingSggCodesRef = useRef(new Set()); // 로드 중인 sgg 코드 추적
    const loadedSggCodesBySidoRef = useRef(new Map()); // 시도별 로드된 sgg 코드 캐시
    const polygonLoadingAbortRef = useRef(null); // 진행 중인 폴리곤 로드 취소용
    const onRegionClickRef = useRef(onRegionClick);
    const lastPolygonStateRef = useRef(''); // 폴리곤 상태 추적용 (중복 실행 방지)

    // onRegionClick이 변경될 때마다 ref 업데이트
    useEffect(() => {
      onRegionClickRef.current = onRegionClick;
    }, [onRegionClick]);

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
          let clickedOnPolygon = false;

          // 클릭한 위치가 어떤 폴리곤 안에 있는지 확인
          sidoPolygonsMap.forEach((polygonData, sidoName) => {
            const polygon = polygonData.polygon;
            if (polygon && window.naver.maps.geometry.polygon) {
              const isInside = window.naver.maps.geometry.polygon.containsLocation(clickPoint, polygon);
              if (isInside) {
                clickedOnPolygon = true;
                console.log('지도 클릭으로 폴리곤 감지:', sidoName);
                if (onRegionClickRef.current) {
                  onRegionClickRef.current('sido', sidoName);
                }
              }
            }
          });

          // 폴리곤이 아닌 빈 공간을 클릭한 경우 전국 뷰로 리셋
          if (!clickedOnPolygon && (selectedSido || selectedSigungu || selectedEupmyeondong)) {
            // 전국 뷰로 리셋하는 콜백이 있으면 호출
            if (onRegionClickRef.current) {
              // 전국 뷰로 리셋하는 특별한 이벤트
              onRegionClickRef.current('reset', '전국');
            }
          }
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

      console.log('마커 useEffect 실행:', {
        servicesCount: services.length,
        mapReady: mapReadyRef.current,
        mapInstance: !!mapInstanceRef.current
      });

      // 마커가 변경되지 않았으면 스킵
      const servicesKey = services.map(s => `${s.latitude},${s.longitude}`).join('|');
      if (servicesKey === lastServicesKeyRef.current && markersRef.current.length > 0) {
        console.log('마커 변경 없음, 스킵');
        return;
      }
      lastServicesKeyRef.current = servicesKey;

      console.log('마커 생성 시작:', services.length, '개');
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
        } else {
          console.log('마커 생성 완료:', markersRef.current.length, '개');
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

    // GeoJSON 데이터 로드 (더 이상 전체 파일을 로드하지 않음, 필요시 동적 로드)
    // 이제 각 레벨별로 필요한 파일만 로드

    // 지역 폴리곤 표시 (계층적 지도 탐색) - 분리된 GeoJSON 파일 기반
    useEffect(() => {
      // 지도가 준비될 때까지 기다림
      if (!mapReady || !mapInstanceRef.current || !window.naver?.maps) {
        return;
      }

      // 상태가 변경되지 않았으면 스킵 (중복 실행 방지)
      const currentState = `${currentMapView || 'nation'}-${selectedSido || ''}-${selectedSigungu || ''}-${selectedEupmyeondong || ''}`;
      const isInitialLoad = lastPolygonStateRef.current === '';

      if (!isInitialLoad && currentState === lastPolygonStateRef.current) {
        console.log('⏭️ 상태 변경 없음, 스킵');
        return;
      }

      console.log('✅ 폴리곤 로드 시작:', currentState);
      lastPolygonStateRef.current = currentState;

      // 이전 로드 취소
      if (polygonLoadingAbortRef.current) {
        polygonLoadingAbortRef.current.aborted = true;
      }
      const abortController = { aborted: false };
      polygonLoadingAbortRef.current = abortController;

      // 디바운싱 제거 - 즉시 실행
      (async () => {
        if (!mapInstanceRef.current) {
          console.warn('⚠️ 지도 없음');
          return;
        }

        if (abortController.aborted) {
          console.log('❌ 취소됨');
          return;
        }

        console.log('🧹 기존 폴리곤 정리');
        clearRegionPolygons();

        const map = mapInstanceRef.current;
        const naverMaps = window.naver.maps;
        const polygonsMap = new Map();

        // 선택 상태에 따라 다른 단위의 폴리곤 표시
        // 1단계: 전국 뷰 → 시도 폴리곤만 표시
        if (!selectedSido || currentMapView === 'nation') {
          console.log('📍 시도 폴리곤 표시 시작');
          // 모든 시도 파일 로드
          const sidoCodes = getAllSidoCodes();
          console.log('시도 파일 수:', sidoCodes.length);

          // 모든 시도 파일을 병렬로 로드 (실패한 파일은 무시)
          Promise.allSettled(sidoCodes.map(code => loadSidoGeoJSON(code)))
            .then(results => {
              // 취소되었는지 다시 확인
              if (abortController.aborted) {
                console.log('❌ 폴리곤 로드 취소됨 (시도 로드 중)');
                return;
              }

              // 성공한 결과만 필터링
              const sidoDataList = results
                .filter(result => result.status === 'fulfilled' && result.value)
                .map(result => result.value);

              console.log(`시도 파일 로드 완료: ${sidoDataList.length}/${sidoCodes.length}`);

              if (sidoDataList.length === 0) {
                console.error('⚠️ 시도 파일이 하나도 로드되지 않았습니다!');
                return;
              }

              // 성능 최적화: 배치 처리로 폴리곤 생성
              const batchSize = 5; // 한 번에 5개씩 처리
              let batchIndex = 0;
              const sidoEntries = sidoDataList
                .filter(data => {
                  // 데이터 유효성 검사
                  if (!data || !data.features || data.features.length === 0) return false;
                  // sido 파일은 sidonm이 없고 sido 코드만 있음
                  const sidoCode = data.features[0]?.properties?.sido;
                  if (!sidoCode) {
                    console.warn('시도 코드가 없는 데이터:', data);
                    return false;
                  }
                  // 시도 코드로 시도명 가져오기
                  const sidoName = getSidoName(sidoCode);
                  if (!sidoName) {
                    console.warn('시도명을 찾을 수 없음:', sidoCode);
                    return false;
                  }
                  return true;
                })
                .map(data => {
                  const sidoCode = data.features[0].properties.sido;
                  const sidoName = getSidoName(sidoCode);
                  return [sidoName, data.features];
                });

              const createSidoPolygonBatch = () => {
                const start = batchIndex * batchSize;
                const end = Math.min(start + batchSize, sidoEntries.length);

                for (let i = start; i < end; i++) {
                  const [sidoName, sidoFeatures] = sidoEntries[i];

                  // 각 시도의 모든 동 폴리곤을 하나의 MultiPolygon으로 표시
                  const allPaths = [];

                  sidoFeatures.forEach(feature => {
                    const paths = convertCoordinatesToPaths(feature.geometry.coordinates, naverMaps);
                    allPaths.push(...paths);
                  });

                  if (allPaths.length === 0) {
                    console.warn(`시도 ${sidoName}의 경로가 없습니다`);
                    continue;
                  }

                  // MultiPolygon으로 폴리곤 생성
                  const polygon = new naverMaps.Polygon({
                    map: mapInstanceRef.current,
                    paths: allPaths,
                    fillColor: '#75B8FA',
                    fillOpacity: 0.1,
                    strokeColor: '#75B8FA',
                    strokeOpacity: 0.5,
                    strokeWeight: 1.5,
                    clickable: true,
                    zIndex: 100,
                  });

                  polygonsMap.set(sidoName, { polygon, paths: allPaths });

                  // 클릭 이벤트
                  const handlePolygonClick = (e) => {
                    if (e && typeof e.stopPropagation === 'function') {
                      e.stopPropagation();
                    }
                    console.log('시도 폴리곤 클릭:', sidoName);
                    if (onRegionClickRef.current) {
                      onRegionClickRef.current('sido', sidoName);
                    }
                  };

                  naverMaps.Event.addListener(polygon, 'click', handlePolygonClick);
                  naverMaps.Event.addListener(polygon, 'mousedown', (e) => {
                    if (e && typeof e.stopPropagation === 'function') {
                      e.stopPropagation();
                    }
                    handlePolygonClick(e);
                  });

                  // 호버 효과
                  naverMaps.Event.addListener(polygon, 'mouseover', () => {
                    polygon.setOptions({
                      fillOpacity: 0.25,
                      strokeWeight: 2,
                      strokeOpacity: 0.7,
                    });
                  });

                  naverMaps.Event.addListener(polygon, 'mouseout', () => {
                    polygon.setOptions({
                      fillOpacity: 0.1,
                      strokeWeight: 1.5,
                      strokeOpacity: 0.5,
                    });
                  });

                  regionPolygonsRef.current.push(polygon);
                }

                batchIndex++;
                if (end < sidoEntries.length) {
                  // 다음 배치를 비동기로 처리
                  requestAnimationFrame(createSidoPolygonBatch);
                } else {
                  console.log('시도 폴리곤 렌더링 완료:', regionPolygonsRef.current.length);
                }
              };

              createSidoPolygonBatch();
            })
            .catch(error => {
              console.error('시도 폴리곤 로드 실패:', error);
            });
        }
        // 2단계: 시도 뷰 → 선택된 시도의 시군구 폴리곤만 표시
        else if (selectedSido && !selectedSigungu) {
          console.log('📍 시군구 폴리곤 표시 시작:', selectedSido);
          // 선택된 시도의 sido 파일을 로드하여 시군구 코드 추출
          const sidoCode = getSidoCode(selectedSido);
          if (!sidoCode) {
            console.error('시도 코드를 찾을 수 없음:', selectedSido);
            return;
          }

          // sido 파일에는 sgg 정보가 없으므로, sgg 파일을 직접 로드해서 시도 코드 확인
          // sgg 파일명은 시도 코드로 시작함 (예: 11110.json은 서울특별시의 종로구)
          // 하지만 브라우저에서는 파일 목록을 가져올 수 없으므로
          // sgg 파일을 하나씩 로드해서 시도 코드를 확인해야 함

          // 해결책: sgg 파일을 로드해서 시도 코드 확인
          // sgg 파일명 패턴: 시도 코드(2자리) + 시군구 코드(3자리) = 5자리
          // 예: 서울특별시(11) -> 11110, 11140, 11170 등

          // 모든 sgg 파일을 로드하는 것은 비효율적이므로
          // 대신 sgg 파일을 로드해서 시도 코드를 확인
          // 실제로는 서버에서 시도별 sgg 목록을 제공하거나 미리 정의된 매핑 사용 권장

          // 임시 해결책: sgg 파일을 로드해서 시도 코드 확인
          // sgg 파일명 패턴을 사용하여 가능한 sgg 코드 생성 후 확인
          // 하지만 모든 조합을 시도하는 것은 비효율적

          // 더 나은 방법: sgg 파일을 하나씩 로드해서 시도 코드 확인
          // 하지만 252개 파일을 모두 로드하는 것은 비효율적

          // 캐시 확인: 이미 로드된 sgg 코드 목록이 있으면 재사용
          const cacheKey = sidoCode;
          let sggCodesPromise;

          if (loadedSggCodesBySidoRef.current.has(cacheKey)) {
            const cachedSggCodes = loadedSggCodesBySidoRef.current.get(cacheKey);
            console.log('캐시된 시군구 코드 사용:', cachedSggCodes.length);
            sggCodesPromise = Promise.resolve(cachedSggCodes);
          } else {
            // 캐시에 없으면 새로 로드
            sggCodesPromise = getSigunguCodesBySidoCode(sidoCode)
              .then(sggCodes => {
                if (abortController.aborted) return [];
                // 캐시에 저장
                loadedSggCodesBySidoRef.current.set(cacheKey, sggCodes);
                return sggCodes;
              });
          }

          sggCodesPromise
            .then(sggCodes => {
              if (abortController.aborted) {
                console.log('[MapContainer] 폴리곤 로드 취소됨 (aborted)');
                return [];
              }

              console.log('[MapContainer] 선택된 시도의 시군구 수:', sggCodes.length, 'sggCodes:', sggCodes);

              if (!sggCodes || sggCodes.length === 0) {
                console.warn('[MapContainer] 시군구 코드를 찾을 수 없습니다. sido 파일에서 sgg 코드 추출 실패 또는 sido 파일에 sgg 정보가 없을 수 있습니다.');
                return Promise.resolve([]);
              }

              // 모든 시군구 파일을 병렬로 로드 (중복 로드 방지)
              console.log('[MapContainer] 시군구 파일 로드 시작:', sggCodes);
              const loadPromises = sggCodes.map((code, index) => {
                // 이미 로드 중이면 스킵
                if (loadingSggCodesRef.current.has(code)) {
                  console.log(`[MapContainer] sgg 파일 ${code} 이미 로드 중, 스킵`);
                  return Promise.resolve({ status: 'fulfilled', value: null, skipped: true, code, index });
                }
                loadingSggCodesRef.current.add(code);
                console.log(`[MapContainer] sgg 파일 로드 시작: ${code}`);
                return loadSigunguGeoJSON(code)
                  .then(data => {
                    loadingSggCodesRef.current.delete(code);
                    console.log(`[MapContainer] sgg 파일 로드 성공: ${code}`, data ? `features: ${data.features?.length || 0}` : 'data 없음');
                    return { status: 'fulfilled', value: data, code, index };
                  })
                  .catch(error => {
                    loadingSggCodesRef.current.delete(code);
                    console.error(`[MapContainer] sgg 파일 로드 실패: ${code}`, error);
                    return { status: 'rejected', reason: error, code, index };
                  });
              });

              return Promise.allSettled(loadPromises);
            })
            .then(results => {
              if (abortController.aborted) {
                console.log('폴리곤 로드 취소됨');
                return;
              }
              if (!results) return;

              // 성공한 결과만 필터링 (skipped 제외)
              const sigunguDataList = results
                .filter(result => result.status === 'fulfilled' && result.value && result.value.value && !result.value.skipped)
                .map(result => result.value.value);

              const failedCount = results.filter(r => r.status === 'rejected').length;
              console.log(`[MapContainer] 시군구 파일 로드 완료: 성공 ${sigunguDataList.length}/${results.length}, 실패 ${failedCount}개`);

              if (failedCount > 0) {
                const failedCodes = results
                  .filter(r => {
                    // Promise.allSettled의 rejected 결과는 reason에, fulfilled 결과는 value에 우리 객체가 있음
                    const innerResult = r.status === 'rejected' ? r.reason : r.value;
                    return innerResult && innerResult.status === 'rejected' && innerResult.code;
                  })
                  .map(r => {
                    const innerResult = r.status === 'rejected' ? r.reason : r.value;
                    return innerResult?.code;
                  })
                  .filter(Boolean);
                console.warn(`[MapContainer] 로드 실패한 sgg 코드:`, failedCodes);
              }

              // 시군구별로 그룹화
              const sigunguGroups = new Map();
              sigunguDataList.forEach((data) => {
                if (!data || !data.features || data.features.length === 0) return;
                const sggName = data.features[0].properties.sggnm;
                if (sggName) {
                  sigunguGroups.set(`${selectedSido}_${sggName}`, {
                    sido: selectedSido,
                    sigungu: sggName,
                    features: data.features
                  });
                }
              });

              const filteredSigunguGroups = Array.from(sigunguGroups.entries());
              console.log('필터링된 시군구 수:', filteredSigunguGroups.length);

              // 배치 처리
              const batchSize = 3;
              let batchIndex = 0;

              const createSigunguPolygonBatch = () => {
                const start = batchIndex * batchSize;
                const end = Math.min(start + batchSize, filteredSigunguGroups.length);

                for (let i = start; i < end; i++) {
                  const [key, group] = filteredSigunguGroups[i];

                  const allPaths = [];
                  group.features.forEach(feature => {
                    const paths = convertCoordinatesToPaths(feature.geometry.coordinates, naverMaps);
                    allPaths.push(...paths);
                  });

                  if (allPaths.length === 0) continue;

                  const polygon = new naverMaps.Polygon({
                    map: mapInstanceRef.current,
                    paths: allPaths,
                    fillColor: '#75B8FA',
                    fillOpacity: 0.15,
                    strokeColor: '#75B8FA',
                    strokeOpacity: 0.6,
                    strokeWeight: 2,
                    clickable: true,
                    zIndex: 100,
                  });

                  polygonsMap.set(key, { polygon, paths: allPaths });

                  const handlePolygonClick = (e) => {
                    if (e && typeof e.stopPropagation === 'function') {
                      e.stopPropagation();
                    }
                    console.log('시군구 폴리곤 클릭:', group.sigungu);
                    if (onRegionClickRef.current) {
                      onRegionClickRef.current('sigungu', group.sigungu);
                    }
                  };

                  naverMaps.Event.addListener(polygon, 'click', handlePolygonClick);
                  naverMaps.Event.addListener(polygon, 'mousedown', (e) => {
                    if (e && typeof e.stopPropagation === 'function') {
                      e.stopPropagation();
                    }
                    handlePolygonClick(e);
                  });

                  naverMaps.Event.addListener(polygon, 'mouseover', () => {
                    polygon.setOptions({
                      fillOpacity: 0.3,
                      strokeWeight: 2.5,
                      strokeOpacity: 0.8,
                    });
                  });

                  naverMaps.Event.addListener(polygon, 'mouseout', () => {
                    polygon.setOptions({
                      fillOpacity: 0.15,
                      strokeWeight: 2,
                      strokeOpacity: 0.6,
                    });
                  });

                  regionPolygonsRef.current.push(polygon);
                }

                batchIndex++;
                if (end < filteredSigunguGroups.length) {
                  requestAnimationFrame(createSigunguPolygonBatch);
                } else {
                  console.log('시군구 폴리곤 렌더링 완료:', regionPolygonsRef.current.length);
                }
              };

              createSigunguPolygonBatch();
            })
            .catch(error => {
              console.error('시군구 폴리곤 로드 실패:', error);
            });
        }
        // 3단계: 시군구 뷰 → 선택된 시군구의 동 폴리곤만 표시 (동이 선택되어도 표시)
        else if (selectedSido && selectedSigungu) {
          console.log('📍 동 폴리곤 표시 시작:', selectedSido, selectedSigungu);
          // 선택된 시군구의 sgg 코드 가져오기
          getSigunguCodeByName(selectedSido, selectedSigungu)
            .then(sggCode => {
              if (!sggCode) {
                console.error('시군구 코드를 찾을 수 없음:', selectedSido, selectedSigungu);
                return;
              }

              // 시군구 파일을 로드하여 동 코드 추출
              return loadSigunguGeoJSON(sggCode);
            })
            .then(sigunguData => {
              if (!sigunguData || !sigunguData.features) {
                console.error('시군구 데이터 로드 실패:', selectedSido, selectedSigungu, '파일이 존재하지 않을 수 있습니다.');
                return;
              }

              // 동 코드 추출
              const dongCodes = new Set();
              sigunguData.features.forEach(feature => {
                const dongCode = feature.properties.adm_cd2;
                if (dongCode) {
                  dongCodes.add(dongCode);
                }
              });

              console.log('선택된 시군구의 동 수:', dongCodes.size);

              // 모든 동 파일을 병렬로 로드 (일부 실패해도 계속 진행)
              return Promise.allSettled(Array.from(dongCodes).map(code => loadDongGeoJSON(code)));
            })
            .then(results => {
              if (!results) return;

              // 성공한 결과만 필터링
              const dongDataList = results
                .filter(result => result.status === 'fulfilled' && result.value)
                .map(result => result.value);

              console.log(`동 파일 로드 완료: ${dongDataList.length}/${results.length}`);

              // 동 데이터를 필터링하여 정리
              const filteredDongGroups = dongDataList
                .filter(data => data && data.features && data.features.length > 0)
                .map(data => {
                  const feature = data.features[0];
                  const key = feature.properties.adm_nm || feature.properties.adm_cd2;
                  return { key, feature };
                });

              console.log('필터링된 동 수:', filteredDongGroups.length);

              const batchSize = 10;
              let batchIndex = 0;

              const createDongPolygonBatch = () => {
                const start = batchIndex * batchSize;
                const end = Math.min(start + batchSize, filteredDongGroups.length);

                for (let i = start; i < end; i++) {
                  const { key, feature } = filteredDongGroups[i];
                  const props = feature.properties;

                  const paths = convertCoordinatesToPaths(feature.geometry.coordinates, naverMaps);
                  if (paths.length === 0) continue;

                  const polygon = new naverMaps.Polygon({
                    map: mapInstanceRef.current,
                    paths: paths,
                    fillColor: '#75B8FA',
                    fillOpacity: 0.2,
                    strokeColor: '#75B8FA',
                    strokeOpacity: 0.7,
                    strokeWeight: 2.5,
                    clickable: true,
                    zIndex: 100,
                  });

                  polygonsMap.set(key, { polygon, paths });

                  const handlePolygonClick = (e) => {
                    if (e && typeof e.stopPropagation === 'function') {
                      e.stopPropagation();
                    }
                    console.log('동 폴리곤 클릭:', props.adm_nm);
                    if (onRegionClickRef.current) {
                      onRegionClickRef.current('dong', props.adm_nm);
                    }
                  };

                  naverMaps.Event.addListener(polygon, 'click', handlePolygonClick);
                  naverMaps.Event.addListener(polygon, 'mousedown', (e) => {
                    if (e && typeof e.stopPropagation === 'function') {
                      e.stopPropagation();
                    }
                    handlePolygonClick(e);
                  });

                  naverMaps.Event.addListener(polygon, 'mouseover', () => {
                    polygon.setOptions({
                      fillOpacity: 0.35,
                      strokeWeight: 3,
                      strokeOpacity: 0.9,
                    });
                  });

                  naverMaps.Event.addListener(polygon, 'mouseout', () => {
                    polygon.setOptions({
                      fillOpacity: 0.2,
                      strokeWeight: 2.5,
                      strokeOpacity: 0.7,
                    });
                  });

                  regionPolygonsRef.current.push(polygon);
                }

                batchIndex++;
                if (end < filteredDongGroups.length) {
                  requestAnimationFrame(createDongPolygonBatch);
                } else {
                  console.log('동 폴리곤 렌더링 완료:', regionPolygonsRef.current.length);
                }
              };

              createDongPolygonBatch();
            })
            .catch(error => {
              console.error('동 폴리곤 로드 실패:', error);
            });
        }
        // 조건 불일치 시에도 조용히 처리 (경고 제거)

        // 지도 클릭 이벤트로 폴리곤 감지 (폴리곤 직접 클릭이 안 될 때 대비)
        // 비동기 로딩이 완료된 후에 등록되도록 각 폴리곤 렌더링 로직 내에서 처리
        // 여기서는 기본 핸들러만 등록 (실제 핸들링은 각 폴리곤 렌더링 로직에서 처리)
      })(); // 즉시 실행

      return () => {
        // 진행 중인 로드 취소
        if (polygonLoadingAbortRef.current) {
          polygonLoadingAbortRef.current.aborted = true;
        }
      };
    }, [mapReady, currentMapView, selectedSido, selectedSigungu, selectedEupmyeondong]);

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
