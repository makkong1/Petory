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

      const initial = mapCenter || userLocation || DEFAULT_CENTER;
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
          window.kakao.maps.load(() => ensureMap());
        } else {
          setTimeout(waitForKakao, 100);
        }
      };

      waitForKakao();
    }, [ensureMap]);

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
      if (!mapReadyRef.current || !mapInstanceRef.current || !mapCenter) return;

      const map = mapInstanceRef.current;
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
    }, [userLocation]);

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
            return;
          }

          lastProgrammaticCenterRef.current = null;
        }

        onMapIdle?.({
          lat: center.getLat(),
          lng: center.getLng(),
          level: map.getLevel(),
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
