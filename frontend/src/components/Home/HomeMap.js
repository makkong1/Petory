import React, { useEffect, useRef, useState } from 'react';
import styled from 'styled-components';

// 홈 벤토 "내 주변" 타일용 경량 정적 지도.
// 네이버 지도 SDK를 재사용(동적 로드)하고, 상호작용은 끈 채
// 내 위치 마커 + 주변 서비스 마커(핀+이름)를 미리보기로 표시한다.
// 클릭은 부모 타일(onClick)이 처리하도록 pointer-events를 막는다.

const NAVER_KEY_ID =
  process.env.REACT_APP_NAVER_MAPS_KEY_ID ||
  process.env.REACT_APP_NAVER_MAPS_CLIENT_ID ||
  '';
const DEFAULT_CENTER = { lat: 37.5665, lng: 126.978 };

// 주변 서비스용 핀 아이콘 (핀 + 짧은 이름). 상호작용은 없다.
const serviceIcon = (name) => {
  const short = name ? (name.length > 6 ? name.slice(0, 6) + '…' : name) : '';
  const label = short
    ? `<div style="background:rgba(255,255,255,0.95);padding:1px 5px;border-radius:4px;font-size:10px;font-weight:600;white-space:nowrap;box-shadow:0 1px 2px rgba(0,0,0,0.2);color:#2d3b39;margin-top:1px;line-height:1.3;">${short}</div>`
    : '';
  return {
    content:
      `<div style="display:flex;flex-direction:column;align-items:center;">` +
      `<svg width="20" height="28" viewBox="0 0 24 34" xmlns="http://www.w3.org/2000/svg" style="filter:drop-shadow(0 1px 2px rgba(0,0,0,0.3));">` +
      `<path fill="#14b8a6" d="M12 0C5.37 0 0 5.37 0 12c0 7 12 22 12 22s12-15 12-22C24 5.37 18.63 0 12 0Z"/>` +
      `<circle cx="12" cy="12" r="4.5" fill="#fff"/>` +
      `</svg>${label}</div>`,
    anchor: new window.naver.maps.Point(10, 28),
  };
};

const HomeMap = ({ coords, services = [] }) => {
  const mapRef = useRef(null);
  const instRef = useRef(null);
  const markerRef = useRef(null); // 내 위치
  const svcMarkersRef = useRef([]); // 주변 서비스
  const coordsRef = useRef(coords);
  const servicesRef = useRef(services);
  const [failed, setFailed] = useState(false);

  coordsRef.current = coords;
  servicesRef.current = services;

  // 주변 서비스 마커를 다시 그리고, 내 위치+서비스가 모두 보이도록 뷰를 맞춘다.
  const renderServices = () => {
    const map = instRef.current;
    if (!map || !window.naver?.maps) return;

    svcMarkersRef.current.forEach((m) => m.setMap(null));
    svcMarkersRef.current = [];

    const list = (servicesRef.current || []).filter(
      (s) => s && s.latitude != null && s.longitude != null
    );
    list.forEach((s) => {
      const marker = new window.naver.maps.Marker({
        position: new window.naver.maps.LatLng(s.latitude, s.longitude),
        map,
        icon: serviceIcon(s.name),
      });
      svcMarkersRef.current.push(marker);
    });

    const c = coordsRef.current;
    if (c && list.length) {
      const origin = new window.naver.maps.LatLng(c.lat, c.lng);
      const bounds = new window.naver.maps.LatLngBounds(origin, origin);
      list.forEach((s) =>
        bounds.extend(new window.naver.maps.LatLng(s.latitude, s.longitude))
      );
      map.fitBounds(bounds);
      if (map.getZoom() > 16) map.setZoom(16);
    }
  };

  // 최초 1회 지도 초기화 (SDK 로드 포함)
  useEffect(() => {
    let cancelled = false;
    const center = coordsRef.current || DEFAULT_CENTER;

    const init = () => {
      if (cancelled || !mapRef.current || instRef.current || !window.naver?.maps) return;
      const c = new window.naver.maps.LatLng(center.lat, center.lng);
      instRef.current = new window.naver.maps.Map(mapRef.current, {
        center: c,
        zoom: 15,
        draggable: false,
        pinchZoom: false,
        scrollWheel: false,
        keyboardShortcuts: false,
        disableDoubleClickZoom: true,
        disableDoubleTapZoom: true,
        zoomControl: false,
        logoControl: false,
        mapDataControl: false,
        scaleControl: false,
      });
      markerRef.current = new window.naver.maps.Marker({ position: c, map: instRef.current });
      renderServices();
    };

    if (window.naver?.maps) {
      init();
      return () => { cancelled = true; };
    }

    const existing = document.querySelector('script[src*="map.naver.com"]');
    if (existing) {
      const interval = setInterval(() => {
        if (window.naver?.maps) { clearInterval(interval); init(); }
      }, 100);
      return () => { cancelled = true; clearInterval(interval); };
    }

    if (!NAVER_KEY_ID) {
      setFailed(true);
      return () => { cancelled = true; };
    }

    const script = document.createElement('script');
    script.src = `https://openapi.map.naver.com/openapi/v3/maps.js?ncpKeyId=${NAVER_KEY_ID}`;
    script.async = true;
    script.onload = init;
    script.onerror = () => { if (!cancelled) setFailed(true); };
    document.head.appendChild(script);

    return () => { cancelled = true; };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 위치가 뒤늦게 잡히면 지도 중심/마커 갱신
  useEffect(() => {
    if (!instRef.current || !coords || !window.naver?.maps) return;
    const c = new window.naver.maps.LatLng(coords.lat, coords.lng);
    if (markerRef.current) markerRef.current.setPosition(c);
    else markerRef.current = new window.naver.maps.Marker({ position: c, map: instRef.current });
    // 서비스가 있으면 fitBounds가 중심을 잡고, 없으면 내 위치로 센터링
    if (servicesRef.current?.length) renderServices();
    else instRef.current.setCenter(c);
  }, [coords]);

  // 주변 서비스 목록이 갱신되면 마커 다시 렌더
  useEffect(() => {
    renderServices();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [services]);

  // SDK 키가 없거나 로드 실패 시: 타일 배경 그라데이션이 그대로 보이도록 아무것도 렌더 안 함
  if (failed) return null;

  return <Canvas ref={mapRef} />;
};

export default HomeMap;

const Canvas = styled.div`
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
`;
