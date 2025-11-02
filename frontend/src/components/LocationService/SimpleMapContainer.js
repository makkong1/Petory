import React, { useEffect, useRef, useState } from 'react';
import styled from 'styled-components';

const MapContainer = ({ services = [], onServiceClick, selectedCategory = null }) => {
  const mapRef = useRef(null);
  const mapInstanceRef = useRef(null);
  const markersRef = useRef([]);
  const infoWindowsRef = useRef([]);

  // 카카오맵 초기화
  useEffect(() => {
    const initMap = () => {
      if (window.kakao && window.kakao.maps) {
        window.kakao.maps.load(() => {
          initializeMap();
        });
      } else {
        setTimeout(initMap, 100);
      }
    };

    initMap();
  }, []);

  const initializeMap = () => {
    const defaultPosition = new window.kakao.maps.LatLng(37.5665, 126.9780);
    
    const mapOption = {
      center: defaultPosition,
      level: 5
    };

    const map = new window.kakao.maps.Map(mapRef.current, mapOption);
    mapInstanceRef.current = map;

    updateMarkers();
  };

  const updateMarkers = () => {
    if (!mapInstanceRef.current) return;

    // 기존 마커와 인포윈도우 제거
    markersRef.current.forEach(marker => marker.setMap(null));
    infoWindowsRef.current.forEach(infoWindow => infoWindow.close());
    markersRef.current = [];
    infoWindowsRef.current = [];

    if (!services || services.length === 0) return;

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
          <div style="padding: 10px; min-width: 200px; background: white !important; color: #000000 !important; color-scheme: light !important;">
            <h3 style="margin: 0 0 5px 0; font-size: 14px; font-weight: bold; color: #000000 !important;">
              ${service.name}
            </h3>
            <p style="margin: 5px 0; font-size: 12px; color: #333333 !important;">
              ${service.category || '카테고리 없음'}
            </p>
            <p style="margin: 5px 0; font-size: 12px; color: #333333 !important;">
              ${service.address || '주소 없음'}
            </p>
            ${service.rating ? `
              <p style="margin: 5px 0; font-size: 12px; color: #000000 !important;">
                ⭐ ${service.rating.toFixed(1)}
              </p>
            ` : ''}
          </div>
        `
      });

      window.kakao.maps.event.addListener(marker, 'click', () => {
        infoWindowsRef.current.forEach(iw => iw.close());
        infoWindow.open(mapInstanceRef.current, marker);
        if (onServiceClick) {
          onServiceClick(service);
        }
      });

      markersRef.current.push(marker);
      infoWindowsRef.current.push(infoWindow);
      bounds.extend(position);
    });

    if (markersRef.current.length > 0) {
      mapInstanceRef.current.setBounds(bounds);
    }
  };

  // 서비스 변경 시 마커 업데이트
  useEffect(() => {
    if (mapInstanceRef.current) {
      updateMarkers();
    }
  }, [services, selectedCategory]);

  return <MapDiv ref={mapRef} />;
};

export default MapContainer;

const MapDiv = styled.div`
  width: 100%;
  height: 100%;
  min-height: 500px;
`;
