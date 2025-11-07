import React, { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import styled from 'styled-components';

const DEFAULT_CENTER = { lat: 37.5665, lng: 126.9780 };
const DEFAULT_LEVEL = 4;

const MapContainer = React.forwardRef(({ services = [], onServiceClick, userLocation, mapCenter, onMapDragStart, onMapIdle }, ref) => {
  const mapRef = useRef(null);
  const mapInstanceRef = useRef(null);
  const markersRef = useRef([]);
  const userMarkerRef = useRef(null);
  const lastCenterRef = useRef(null);
  const [mapReady, setMapReady] = useState(false);

  const initializeMap = useCallback(() => {
    if (!mapRef.current || !window.kakao || !window.kakao.maps) {
      return;
    }

    const initial = mapCenter || userLocation || DEFAULT_CENTER;
    const options = {
      center: new window.kakao.maps.LatLng(initial.lat, initial.lng),
      level: DEFAULT_LEVEL,
    };

    const map = new window.kakao.maps.Map(mapRef.current, options);
    mapInstanceRef.current = map;
    lastCenterRef.current = initial;
    setMapReady(true);
  }, [mapCenter, userLocation]);

  useLayoutEffect(() => {
    const load = () => {
      if (window.kakao && window.kakao.maps) {
        window.kakao.maps.load(() => {
          initializeMap();
        });
      } else {
        setTimeout(load, 100);
      }
    };
    load();
  }, [initializeMap]);

  const clearMarkers = useCallback(() => {
    markersRef.current.forEach((marker) => marker.setMap(null));
    markersRef.current = [];
  }, []);

  const updateMarkers = useCallback(() => {
    if (!mapInstanceRef.current) return;

    clearMarkers();

    services.forEach((service) => {
      if (!service.latitude || !service.longitude) return;

      const position = new window.kakao.maps.LatLng(service.latitude, service.longitude);
      const marker = new window.kakao.maps.Marker({
        position,
        map: mapInstanceRef.current,
      });

      window.kakao.maps.event.addListener(marker, 'click', () => {
        mapInstanceRef.current.panTo(position);
        if (onServiceClick) {
          onServiceClick(service);
        }
      });

      markersRef.current.push(marker);
    });
  }, [services, onServiceClick, clearMarkers]);

  useEffect(() => {
    if (mapReady) {
      updateMarkers();
    }
  }, [mapReady, updateMarkers]);

  useEffect(() => {
    if (!mapReady || !mapInstanceRef.current) return;
    if (!mapCenter) return;

    const previous = lastCenterRef.current;
    if (!previous || previous.lat !== mapCenter.lat || previous.lng !== mapCenter.lng) {
      const position = new window.kakao.maps.LatLng(mapCenter.lat, mapCenter.lng);
      mapInstanceRef.current.panTo(position);
      lastCenterRef.current = mapCenter;
    }
  }, [mapCenter, mapReady]);

  useEffect(() => {
    if (!mapReady || !mapInstanceRef.current || !userLocation) return;

    const position = new window.kakao.maps.LatLng(userLocation.lat, userLocation.lng);

    if (!userMarkerRef.current) {
      const marker = new window.kakao.maps.Marker({
        position,
        map: mapInstanceRef.current,
      });
      userMarkerRef.current = marker;
    } else {
      userMarkerRef.current.setMap(mapInstanceRef.current);
      userMarkerRef.current.setPosition(position);
    }
  }, [userLocation, mapReady]);

  useEffect(() => {
    if (!mapReady || !mapInstanceRef.current || !window.kakao) return;

    const map = mapInstanceRef.current;

    const handleDragStart = () => {
      if (onMapDragStart) {
        onMapDragStart();
      }
    };

    const emitCenterUpdate = () => {
      if (!onMapIdle) return;
      const center = map.getCenter();
      onMapIdle({
        lat: center.getLat(),
        lng: center.getLng(),
        level: map.getLevel(),
      });
    };

    window.kakao.maps.event.addListener(map, 'dragstart', handleDragStart);
    window.kakao.maps.event.addListener(map, 'dragend', emitCenterUpdate);
    window.kakao.maps.event.addListener(map, 'zoom_changed', emitCenterUpdate);
    window.kakao.maps.event.addListener(map, 'idle', emitCenterUpdate);

    return () => {
      window.kakao.maps.event.removeListener(map, 'dragstart', handleDragStart);
      window.kakao.maps.event.removeListener(map, 'dragend', emitCenterUpdate);
      window.kakao.maps.event.removeListener(map, 'zoom_changed', emitCenterUpdate);
      window.kakao.maps.event.removeListener(map, 'idle', emitCenterUpdate);
    };
  }, [mapReady, onMapDragStart, onMapIdle]);

  useEffect(() => () => clearMarkers(), [clearMarkers]);

  return (
    <MapDiv ref={mapRef}>
      {!mapReady && (
        <MapLoading>🗺️ 지도를 불러오는 중...</MapLoading>
      )}
    </MapDiv>
  );
});

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