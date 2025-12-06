import React, { useCallback, useEffect, useRef, useState } from 'react';
import styled from 'styled-components';

const DEFAULT_CENTER = { lat: 37.5665, lng: 126.978 };
const DEFAULT_LEVEL = 4;
const COORD_EPSILON = 0.00001;

const MapContainer = React.forwardRef(
  ({ services = [], onServiceClick, userLocation, mapCenter, onMapDragStart, onMapIdle }, ref) => {
    const mapRef = useRef(null);
    const mapInstanceRef = useRef(null);
    const markersRef = useRef([]);
    const userMarkerRef = useRef(null);
    const lastProgrammaticCenterRef = useRef(null);
    const mapReadyRef = useRef(false);
    const [mapReady, setMapReady] = useState(false);

    const ensureMap = useCallback(() => {
      if (mapInstanceRef.current || !mapRef.current || !window.kakao?.maps) {
        return;
      }

      // userLocation을 우선적으로 사용 (내 위치가 있으면 먼저 사용)
      const initial = userLocation || mapCenter || DEFAULT_CENTER;
      const map = new window.kakao.maps.Map(mapRef.current, {
        center: new window.kakao.maps.LatLng(initial.lat, initial.lng),
        level: DEFAULT_LEVEL,
      });

      mapInstanceRef.current = map;
      lastProgrammaticCenterRef.current = initial;
      mapReadyRef.current = true;
      setMapReady(true);
    }, [mapCenter, userLocation]);

    useEffect(() => {
      const waitForKakao = () => {
        if (window.kakao?.maps) {
          window.kakao.maps.load(() => {
            // userLocation이 있으면 즉시 초기화, 없으면 약간 대기 후 초기화
            if (userLocation) {
              ensureMap();
            } else {
              // geolocation이 완료될 시간을 주기 위해 약간 지연
              setTimeout(() => {
                if (!mapInstanceRef.current) {
                  ensureMap();
                }
              }, 500);
            }
          });
        } else {
          setTimeout(waitForKakao, 100);
        }
      };

      waitForKakao();
    }, [ensureMap, userLocation]);

    const clearMarkers = useCallback(() => {
      markersRef.current.forEach((marker) => marker.setMap(null));
      markersRef.current = [];
    }, []);

    useEffect(() => {
      if (!mapReadyRef.current || !mapInstanceRef.current) return;

      clearMarkers();

      services.forEach((service) => {
        if (typeof service.latitude !== 'number' || typeof service.longitude !== 'number') {
          return;
        }

        const position = new window.kakao.maps.LatLng(service.latitude, service.longitude);
        const marker = new window.kakao.maps.Marker({
          position,
          map: mapInstanceRef.current,
        });

        window.kakao.maps.event.addListener(marker, 'click', () => {
          if (mapInstanceRef.current) {
            lastProgrammaticCenterRef.current = {
              lat: service.latitude,
              lng: service.longitude,
            };
            mapInstanceRef.current.panTo(position);
          }
          onServiceClick?.(service);
        });

        markersRef.current.push(marker);
      });
    }, [services, onServiceClick, clearMarkers]);

    useEffect(() => {
      if (!mapReadyRef.current || !mapInstanceRef.current) return;

      const map = mapInstanceRef.current;
      
      // mapCenter 업데이트 (userLocation은 초기 로드 시에만 사용)
      if (!mapCenter) return;

      const currentCenter = map.getCenter();
      const isAlreadyAtCenter =
        Math.abs(currentCenter.getLat() - mapCenter.lat) < COORD_EPSILON &&
        Math.abs(currentCenter.getLng() - mapCenter.lng) < COORD_EPSILON;

      if (isAlreadyAtCenter) {
        lastProgrammaticCenterRef.current = null;
        return;
      }

      lastProgrammaticCenterRef.current = { ...mapCenter };
      map.panTo(new window.kakao.maps.LatLng(mapCenter.lat, mapCenter.lng));
    }, [mapCenter]);

    useEffect(() => {
      // userLocation이 설정되면 지도가 아직 초기화되지 않았으면 초기화
      if (userLocation && !mapInstanceRef.current && window.kakao?.maps) {
        ensureMap();
        return;
      }

      if (!mapReadyRef.current || !mapInstanceRef.current || !userLocation) return;

      const position = new window.kakao.maps.LatLng(userLocation.lat, userLocation.lng);

      if (!userMarkerRef.current) {
        userMarkerRef.current = new window.kakao.maps.Marker({
          position,
          map: mapInstanceRef.current,
        });
      } else {
        userMarkerRef.current.setPosition(position);
      }
    }, [userLocation, ensureMap]);

    useEffect(() => {
      if (!mapReadyRef.current || !mapInstanceRef.current || !window.kakao?.maps) return;

      const map = mapInstanceRef.current;

      const handleDragStart = () => {
        lastProgrammaticCenterRef.current = null;
        onMapDragStart?.();
      };

      const handleIdle = () => {
        const center = map.getCenter();
        const planned = lastProgrammaticCenterRef.current;

        if (planned) {
          const isSame =
            Math.abs(planned.lat - center.getLat()) < COORD_EPSILON &&
            Math.abs(planned.lng - center.getLng()) < COORD_EPSILON;

          if (isSame) {
            lastProgrammaticCenterRef.current = null;
            // bounds 정보는 계속 전달
            const bounds = map.getBounds();
            onMapIdle?.({
              lat: center.getLat(),
              lng: center.getLng(),
              level: map.getLevel(),
              bounds: {
                sw: { lat: bounds.getSouthWest().getLat(), lng: bounds.getSouthWest().getLng() },
                ne: { lat: bounds.getNorthEast().getLat(), lng: bounds.getNorthEast().getLng() },
              },
            });
            return;
          }

          lastProgrammaticCenterRef.current = null;
        }

        // bounds 정보 추가 (하이브리드용)
        const bounds = map.getBounds();
        onMapIdle?.({
          lat: center.getLat(),
          lng: center.getLng(),
          level: map.getLevel(),
          bounds: {
            sw: { lat: bounds.getSouthWest().getLat(), lng: bounds.getSouthWest().getLng() },
            ne: { lat: bounds.getNorthEast().getLat(), lng: bounds.getNorthEast().getLng() },
          },
        });
      };

      window.kakao.maps.event.addListener(map, 'dragstart', handleDragStart);
      window.kakao.maps.event.addListener(map, 'idle', handleIdle);

      return () => {
        window.kakao.maps.event.removeListener(map, 'dragstart', handleDragStart);
        window.kakao.maps.event.removeListener(map, 'idle', handleIdle);
      };
    }, [onMapDragStart, onMapIdle]);

    useEffect(() => {
      return () => {
        clearMarkers();
        if (userMarkerRef.current) {
          userMarkerRef.current.setMap(null);
        }
      };
    }, [clearMarkers]);

    return (
      <MapDiv ref={mapRef}>
        {!mapReady && <MapLoading>🗺️ 지도를 불러오는 중...</MapLoading>}
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
