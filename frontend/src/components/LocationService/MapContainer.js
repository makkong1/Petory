import React, { useEffect, useRef, useState } from 'react';
import styled from 'styled-components';
import { locationServiceApi } from '../../api/locationServiceApi';

const MapContainer = ({ services = [], onServiceClick, selectedCategory = null }) => {
  const mapRef = useRef(null);
  const mapInstanceRef = useRef(null);
  const markersRef = useRef([]);
  const infoWindowsRef = useRef([]);

  // 카카오맵 초기화
  useEffect(() => {
    const loadKakaoMap = () => {
      if (window.kakao && window.kakao.maps) {
        initializeMap();
      } else {
        // 카카오맵이 아직 로드되지 않았으면 로드 시도
        if (window.kakao && window.kakao.maps && window.kakao.maps.load) {
          window.kakao.maps.load(() => {
            initializeMap();
          });
        } else {
          // 잠시 후 다시 시도
          setTimeout(() => {
            if (window.kakao && window.kakao.maps) {
              initializeMap();
            }
          }, 100);
        }
      }
    };

    loadKakaoMap();

    return () => {
      // 컴포넌트 언마운트 시 마커와 인포윈도우 정리
      markersRef.current.forEach(marker => marker.setMap(null));
      infoWindowsRef.current.forEach(infoWindow => infoWindow.close());
    };
  }, []);

  // 서비스 변경 시 마커 업데이트
  useEffect(() => {
    if (mapInstanceRef.current) {
      updateMarkers();
    }
  }, [services, selectedCategory]);

  const initializeMap = () => {
    // 기본 위치: 서울시청
    const defaultPosition = new window.kakao.maps.LatLng(37.5665, 126.9780);
    
    const mapOption = {
      center: defaultPosition,
      level: 5 // 확대 레벨 (1-14, 숫자가 작을수록 확대)
    };

    const map = new window.kakao.maps.Map(mapRef.current, mapOption);
    mapInstanceRef.current = map;

    // 지도 로드 후 마커 표시
    updateMarkers();
  };

  const updateMarkers = () => {
    // 기존 마커와 인포윈도우 제거
    markersRef.current.forEach(marker => marker.setMap(null));
    infoWindowsRef.current.forEach(infoWindow => infoWindow.close());
    markersRef.current = [];
    infoWindowsRef.current = [];

    if (!mapInstanceRef.current || !services || services.length === 0) return;

    const bounds = new window.kakao.maps.LatLngBounds();

    services.forEach((service) => {
      // 카테고리 필터링
      if (selectedCategory && service.category !== selectedCategory) return;

      if (!service.latitude || !service.longitude) return;

      const position = new window.kakao.maps.LatLng(
        service.latitude,
        service.longitude
      );

      // 마커 생성
      const marker = new window.kakao.maps.Marker({
        position: position,
        map: mapInstanceRef.current,
      });

      // 인포윈도우 생성
      const infoWindowContent = `
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
              ${service.reviewCount ? `(${service.reviewCount}개 리뷰)` : ''}
            </p>
          ` : ''}
          <button 
            onclick="window.openServiceDetail && window.openServiceDetail(${service.idx})"
            style="margin-top: 5px; padding: 5px 10px; background: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer;">
            상세보기
          </button>
        </div>
      `;

      const infoWindow = new window.kakao.maps.InfoWindow({
        content: infoWindowContent,
      });

      // 마커 클릭 이벤트
      window.kakao.maps.event.addListener(marker, 'click', () => {
        // 다른 인포윈도우 닫기
        infoWindowsRef.current.forEach(iw => iw.close());
        // 현재 인포윈도우 열기
        infoWindow.open(mapInstanceRef.current, marker);
        // 부모 컴포넌트에 서비스 클릭 이벤트 전달
        if (onServiceClick) {
          onServiceClick(service);
        }
      });

      markersRef.current.push(marker);
      infoWindowsRef.current.push(infoWindow);
      bounds.extend(position);
    });

    // 모든 마커가 보이도록 지도 범위 조정
    if (markersRef.current.length > 0) {
      mapInstanceRef.current.setBounds(bounds);
    }

    // 전역 함수로 상세보기 클릭 핸들러 등록
    window.openServiceDetail = (serviceIdx) => {
      if (onServiceClick) {
        const service = services.find(s => s.idx === serviceIdx);
        if (service) {
          onServiceClick(service);
        }
      }
    };
  };

  return <MapDiv ref={mapRef} />;
};

export default MapContainer;

const MapDiv = styled.div`
  width: 100%;
  height: 100%;
  min-height: 500px;
`;
