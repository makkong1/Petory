import React, { useEffect, useRef, useState } from 'react';
import styled from 'styled-components';

const AddressMapSelector = ({ onAddressSelect, initialAddress, initialLat, initialLng }) => {
  const mapRef = useRef(null);
  const mapInstanceRef = useRef(null);
  const markerRef = useRef(null);
  const [selectedAddress, setSelectedAddress] = useState(initialAddress || '');
  const [selectedLat, setSelectedLat] = useState(initialLat || null);
  const [selectedLng, setSelectedLng] = useState(initialLng || null);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    let isMounted = true;
    let retryCount = 0;
    const maxRetries = 100; // 최대 10초 대기

    const initMap = () => {
      if (!isMounted) return;

      // mapRef 확인
      if (!mapRef.current) {
        if (retryCount < maxRetries) {
          retryCount++;
          setTimeout(initMap, 100);
          return;
        } else {
          console.error('[지도] 컨테이너를 찾을 수 없습니다.');
          return;
        }
      }

      // 이미 초기화되어 있으면 리턴
      if (mapInstanceRef.current) {
        console.log('[지도] 이미 초기화되어 있습니다.');
        return;
      }

      // 카카오맵 API 확인
      if (!window.kakao) {
        if (retryCount < maxRetries) {
          retryCount++;
          setTimeout(initMap, 100);
          return;
        } else {
          console.error('[지도] 카카오맵 API를 찾을 수 없습니다.');
          return;
        }
      }

      // 카카오맵 maps 확인
      if (!window.kakao.maps) {
        if (retryCount < maxRetries) {
          retryCount++;
          setTimeout(initMap, 100);
          return;
        } else {
          console.error('[지도] 카카오맵 maps를 찾을 수 없습니다.');
          return;
        }
      }

      // 지도 로드 - 반드시 load() 콜백 안에서 초기화해야 함
      try {
        console.log('[지도] 카카오맵 load() 호출 중...');

        window.kakao.maps.load(() => {
          if (!isMounted || !mapRef.current) {
            console.warn('[지도] 컴포넌트가 언마운트되었거나 컨테이너가 없습니다.');
            return;
          }

          // LatLng가 사용 가능한지 확인
          if (typeof window.kakao.maps.LatLng !== 'function') {
            console.error('[지도] LatLng가 사용 가능하지 않습니다.');
            return;
          }

          console.log('[지도] 카카오맵 로드 완료! 지도 초기화 시작...');
          initializeMap();

          // services는 명시적으로 로드 필요
          if (!window.kakao.maps.services) {
            console.log('[지도] services 로드 시작...');

            // services를 명시적으로 로드하기 위해 다시 load 호출
            // 또는 services 스크립트를 동적으로 로드
            const loadServices = () => {
              if (window.kakao.maps.services) {
                console.log('[지도] services 로드 완료!');
                return;
              }

              // services가 없으면 스크립트 동적 로드 시도
              if (!document.querySelector('script[src*="services"]')) {
                const script = document.createElement('script');
                script.src = '//dapi.kakao.com/v2/maps/sdk.js?appkey=6a65c4b3a2b34e9916b1d9d6ae0d0ab0&libraries=services&autoload=false';
                script.onload = () => {
                  window.kakao.maps.load(() => {
                    if (window.kakao.maps.services) {
                      console.log('[지도] services 로드 완료!');
                    } else {
                      console.warn('[지도] services 로드 실패');
                    }
                  });
                };
                document.head.appendChild(script);
              } else {
                // 스크립트가 이미 있으면 재시도
                let serviceRetryCount = 0;
                const tryLoadServices = () => {
                  if (window.kakao.maps.services) {
                    console.log('[지도] services 로드 완료!');
                  } else {
                    serviceRetryCount++;
                    if (serviceRetryCount < 50) {
                      setTimeout(tryLoadServices, 200);
                    } else {
                      console.warn('[지도] services 로드 실패. 주소 검색 기능이 제한될 수 있습니다.');
                    }
                  }
                };
                setTimeout(tryLoadServices, 500);
              }
            };

            loadServices();
          } else {
            console.log('[지도] services가 이미 로드되어 있습니다.');
          }
        });
      } catch (error) {
        console.error('[지도] 로드 중 오류:', error);
      }
    };

    // 약간의 지연 후 초기화 시도 (컴포넌트가 완전히 마운트된 후)
    const timer = setTimeout(() => {
      initMap();
    }, 100);

    // cleanup
    return () => {
      isMounted = false;
      clearTimeout(timer);
      if (markerRef.current) {
        markerRef.current.setMap(null);
        markerRef.current = null;
      }
    };
  }, []);

  // initialLat, initialLng가 변경되면 지도 업데이트
  useEffect(() => {
    if (mapInstanceRef.current && initialLat && initialLng && window.kakao && window.kakao.maps) {
      const position = new window.kakao.maps.LatLng(initialLat, initialLng);
      mapInstanceRef.current.setCenter(position);
      mapInstanceRef.current.setLevel(3);
      addMarker(position);
    }
  }, [initialLat, initialLng]);

  // 지도가 표시될 때 크기 조정 (조건부 렌더링 대응)
  useEffect(() => {
    if (!mapInstanceRef.current || !mapRef.current) return;

    const checkAndRelayout = () => {
      if (mapInstanceRef.current && mapRef.current) {
        const rect = mapRef.current.getBoundingClientRect();
        if (rect.width > 0 && rect.height > 0) {
          console.log('[지도] relayout 호출', { width: rect.width, height: rect.height });
          mapInstanceRef.current.relayout();
        }
      }
    };

    // IntersectionObserver로 지도가 보일 때 감지
    const observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting && mapInstanceRef.current) {
          console.log('[지도] 지도가 보임 - relayout 호출');
          setTimeout(() => {
            if (mapInstanceRef.current) {
              mapInstanceRef.current.relayout();
            }
          }, 300);
        }
      });
    }, { threshold: 0.1 });

    if (mapRef.current) {
      observer.observe(mapRef.current);
    }

    // 주기적으로 체크 (조건부 렌더링 대응)
    const interval = setInterval(() => {
      checkAndRelayout();
    }, 1000);

    return () => {
      observer.disconnect();
      clearInterval(interval);
    };
  }, []);

  const initializeMap = () => {
    // 이미 초기화되어 있으면 리턴
    if (mapInstanceRef.current) {
      console.log('[지도] 이미 초기화되어 있습니다.');
      return;
    }

    // mapRef 확인
    if (!mapRef.current) {
      console.error('[지도] 컨테이너가 없습니다.');
      return;
    }

    // 카카오맵 API 확인
    if (!window.kakao || !window.kakao.maps) {
      console.error('[지도] 카카오맵 API가 없습니다.');
      return;
    }

    const defaultLat = initialLat || 37.5665;
    const defaultLng = initialLng || 126.9780;

    try {
      const defaultPosition = new window.kakao.maps.LatLng(defaultLat, defaultLng);
      const mapOption = {
        center: defaultPosition,
        level: 3,
      };

      console.log('[지도] 지도 생성 중...', { lat: defaultLat, lng: defaultLng });
      const map = new window.kakao.maps.Map(mapRef.current, mapOption);
      mapInstanceRef.current = map;
      console.log('[지도] 지도 생성 완료!');

      // 지도 크기 조정 (여러 번 시도)
      const relayoutMap = () => {
        if (mapInstanceRef.current && mapRef.current) {
          const rect = mapRef.current.getBoundingClientRect();
          if (rect.width > 0 && rect.height > 0) {
            mapInstanceRef.current.relayout();
            console.log('[지도] 크기 조정 완료');
          } else {
            setTimeout(relayoutMap, 200);
          }
        }
      };

      // 초기 relayout
      setTimeout(relayoutMap, 300);
      setTimeout(relayoutMap, 800);
      setTimeout(relayoutMap, 1500);

      // 초기 위치에 마커 표시
      if (initialLat && initialLng) {
        addMarker(defaultPosition);
      }

      // 지도 클릭 이벤트
      window.kakao.maps.event.addListener(map, 'click', (mouseEvent) => {
        const latlng = mouseEvent.latLng;
        addMarker(latlng);
        reverseGeocode(latlng.getLat(), latlng.getLng());
      });

      console.log('[지도] 초기화 완료!');
    } catch (error) {
      console.error('[지도] 초기화 실패:', error);
    }
  };

  const addMarker = (position) => {
    if (!mapInstanceRef.current) {
      return;
    }

    // 기존 마커 제거
    if (markerRef.current) {
      markerRef.current.setMap(null);
    }

    try {
      // 새 마커 추가
      const marker = new window.kakao.maps.Marker({
        position: position,
        map: mapInstanceRef.current,
      });

      markerRef.current = marker;
      mapInstanceRef.current.panTo(position);
    } catch (error) {
      console.error('마커 추가 실패:', error);
    }
  };

  const reverseGeocode = async (lat, lng) => {
    setIsLoading(true);
    try {
      // 카카오맵 API 로드 확인
      if (!window.kakao || !window.kakao.maps) {
        console.error('[지도] 카카오맵 API가 로드되지 않았습니다.');
        setIsLoading(false);
        return;
      }

      // services가 없으면 로드 시도
      if (!window.kakao.maps.services) {
        console.warn('[지도] services가 없습니다. 로드 시도...');

        let retryCount = 0;
        const waitForServices = () => {
          if (window.kakao.maps.services) {
            console.log('[지도] services 로드 완료! 주소 변환 시작...');
            reverseGeocode(lat, lng);
          } else {
            retryCount++;
            if (retryCount < 50) {
              setTimeout(waitForServices, 200);
            } else {
              setIsLoading(false);
              console.error('[지도] services를 로드할 수 없습니다.');
            }
          }
        };

        window.kakao.maps.load(() => {
          waitForServices();
        });
        return;
      }

      // 카카오맵 Geocoder 사용
      const geocoder = new window.kakao.maps.services.Geocoder();
      const coord = new window.kakao.maps.LatLng(lat, lng);

      geocoder.coord2Address(coord.getLng(), coord.getLat(), (result, status) => {
        setIsLoading(false);
        if (status === window.kakao.maps.services.Status.OK) {
          const address = result[0].road_address
            ? result[0].road_address.address_name
            : result[0].address.address_name;

          setSelectedAddress(address);
          setSelectedLat(lat);
          setSelectedLng(lng);

          if (onAddressSelect) {
            onAddressSelect({
              address,
              latitude: lat,
              longitude: lng,
            });
          }
        } else {
          // 주소 변환 실패 시 좌표만 저장
          setSelectedAddress(`위도: ${lat.toFixed(6)}, 경도: ${lng.toFixed(6)}`);
          setSelectedLat(lat);
          setSelectedLng(lng);

          if (onAddressSelect) {
            onAddressSelect({
              address: `위도: ${lat.toFixed(6)}, 경도: ${lng.toFixed(6)}`,
              latitude: lat,
              longitude: lng,
            });
          }
        }
      });
    } catch (error) {
      setIsLoading(false);
      console.error('주소 변환 실패:', error);
    }
  };

  const handleAddressSearch = async () => {
    if (!selectedAddress.trim()) return;

    // 카카오맵 API 로드 확인
    if (!window.kakao || !window.kakao.maps) {
      alert('지도가 아직 로드되지 않았습니다. 잠시 후 다시 시도해주세요.');
      return;
    }

    // services가 없으면 로드 시도
    if (!window.kakao.maps.services) {
      console.warn('[지도] services가 없습니다. 로드 시도...');
      setIsLoading(true);

      // services 로드를 기다림
      let retryCount = 0;
      const waitForServices = () => {
        if (window.kakao.maps.services) {
          console.log('[지도] services 로드 완료! 검색 시작...');
          setIsLoading(false);
          handleAddressSearch();
        } else {
          retryCount++;
          if (retryCount < 50) {
            setTimeout(waitForServices, 200);
          } else {
            setIsLoading(false);
            alert('지도 서비스를 로드할 수 없습니다. 페이지를 새로고침해주세요.');
          }
        }
      };

      // load 호출 후 대기
      window.kakao.maps.load(() => {
        waitForServices();
      });
      return;
    }

    setIsLoading(true);
    try {
      const searchQuery = selectedAddress.trim();
      const geocoder = new window.kakao.maps.services.Geocoder();

      // 주소 검색 함수
      const searchAddress = (query, isRetry = false) => {
        geocoder.addressSearch(query, (result, status) => {
          if (status === window.kakao.maps.services.Status.OK) {
            // 첫 번째 결과 사용
            const firstResult = result[0];
            const lat = parseFloat(firstResult.y);
            const lng = parseFloat(firstResult.x);
            const address = firstResult.address_name || query;

            const position = new window.kakao.maps.LatLng(lat, lng);

            // 지도 중심 이동 및 마커 추가
            if (mapInstanceRef.current) {
              mapInstanceRef.current.setCenter(position);
              mapInstanceRef.current.setLevel(3); // 적절한 줌 레벨 설정
              addMarker(position);
            }

            setSelectedAddress(address);
            setSelectedLat(lat);
            setSelectedLng(lng);
            setIsLoading(false);

            if (onAddressSelect) {
              onAddressSelect({
                address: address,
                latitude: lat,
                longitude: lng,
              });
            }
          } else {
            // 검색 실패 시 재시도 로직
            if (!isRetry) {
              // "서울특별시" 또는 "서울"이 없으면 자동으로 추가
              if (!query.includes('서울') && !query.includes('경기') && !query.includes('인천') &&
                !query.includes('부산') && !query.includes('대구') && !query.includes('광주') &&
                !query.includes('대전') && !query.includes('울산') && !query.includes('세종')) {
                // 구나 동으로 끝나는 경우 서울특별시 추가
                if (query.endsWith('구') || query.endsWith('동') || query.endsWith('시') || query.endsWith('군')) {
                  searchAddress(`서울특별시 ${query}`, true);
                  return;
                }
              }
            }

            // 재시도도 실패하거나 이미 재시도한 경우 Places API로 키워드 검색 시도
            tryKeywordSearch();
          }
        });
      };

      // Places API 키워드 검색 (구 단위 검색용)
      const tryKeywordSearch = () => {
        if (!window.kakao || !window.kakao.maps || !window.kakao.maps.services) {
          setIsLoading(false);
          alert('지도가 아직 로드되지 않았습니다. 잠시 후 다시 시도해주세요.');
          return;
        }

        const places = new window.kakao.maps.services.Places();
        places.keywordSearch(searchQuery, (data, status) => {
          setIsLoading(false);

          if (status === window.kakao.maps.services.Status.OK && data.length > 0) {
            // 첫 번째 결과 사용
            const firstResult = data[0];
            const lat = parseFloat(firstResult.y);
            const lng = parseFloat(firstResult.x);
            const address = firstResult.address_name || firstResult.place_name || searchQuery;

            const position = new window.kakao.maps.LatLng(lat, lng);

            // 지도 중심 이동 및 마커 추가
            if (mapInstanceRef.current) {
              mapInstanceRef.current.setCenter(position);
              mapInstanceRef.current.setLevel(3);
              addMarker(position);
            }

            setSelectedAddress(address);
            setSelectedLat(lat);
            setSelectedLng(lng);

            if (onAddressSelect) {
              onAddressSelect({
                address: address,
                latitude: lat,
                longitude: lng,
              });
            }
          } else {
            console.error('주소 검색 실패:', status);
            alert('주소를 찾을 수 없습니다. 더 구체적인 주소로 검색해주세요. (예: 서울특별시 노원구)');
          }
        });
      };

      // 먼저 Geocoder로 주소 검색 시도
      searchAddress(searchQuery);
    } catch (error) {
      setIsLoading(false);
      console.error('주소 검색 실패:', error);
      alert('주소를 찾을 수 없습니다.');
    }
  };

  return (
    <Container>
      <AddressInputRow>
        <AddressInput
          type="text"
          value={selectedAddress}
          onChange={(e) => setSelectedAddress(e.target.value)}
          placeholder="주소를 입력하거나 지도를 클릭하세요"
          onKeyPress={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              handleAddressSearch();
            }
          }}
        />
        <SearchButton type="button" onClick={handleAddressSearch} disabled={isLoading}>
          {isLoading ? '검색 중...' : '검색'}
        </SearchButton>
      </AddressInputRow>
      {isLoading && <LoadingText>주소를 검색하는 중...</LoadingText>}
      {selectedAddress && !isLoading && (
        <SelectedAddress>
          <AddressLabel>선택된 위치:</AddressLabel>
          <AddressValue>{selectedAddress}</AddressValue>
        </SelectedAddress>
      )}
      <MapDiv ref={mapRef} />
      <HelperText>지도를 클릭하여 위치를 선택하거나 주소를 검색하세요</HelperText>
    </Container>
  );
};

export default AddressMapSelector;

const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${(props) => props.theme.spacing.md};
`;

const AddressInputRow = styled.div`
  display: flex;
  gap: ${(props) => props.theme.spacing.sm};
`;

const AddressInput = styled.input`
  flex: 1;
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.md};
  border-radius: ${(props) => props.theme.borderRadius.md};
  border: 1px solid ${(props) => props.theme.colors.border};
  background: ${(props) => props.theme.colors.surfaceElevated};
  font-size: 0.95rem;

  &:focus {
    outline: none;
    border-color: ${(props) => props.theme.colors.primary};
    box-shadow: 0 0 0 3px rgba(255, 126, 54, 0.2);
  }
`;

const SearchButton = styled.button`
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.lg};
  border-radius: ${(props) => props.theme.borderRadius.md};
  border: 1px solid ${(props) => props.theme.colors.primary};
  background: ${(props) => props.theme.colors.primary};
  color: #ffffff;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover:not(:disabled) {
    background: ${(props) => props.theme.colors.primaryDark};
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
`;

const LoadingText = styled.div`
  color: ${(props) => props.theme.colors.textSecondary};
  font-size: 0.9rem;
`;

const SelectedAddress = styled.div`
  padding: ${(props) => props.theme.spacing.sm} ${(props) => props.theme.spacing.md};
  background: ${(props) => props.theme.colors.surfaceElevated};
  border-radius: ${(props) => props.theme.borderRadius.md};
  border: 1px solid ${(props) => props.theme.colors.border};
  display: flex;
  flex-direction: column;
  gap: 4px;
`;

const AddressLabel = styled.span`
  font-size: 0.8rem;
  color: ${(props) => props.theme.colors.textSecondary};
  font-weight: 600;
`;

const AddressValue = styled.span`
  font-size: 0.95rem;
  color: ${(props) => props.theme.colors.text};
  font-weight: 500;
`;

const MapDiv = styled.div`
  width: 100%;
  height: 400px;
  border-radius: ${(props) => props.theme.borderRadius.lg};
  overflow: hidden;
  border: 1px solid ${(props) => props.theme.colors.border};
`;

const HelperText = styled.span`
  font-size: 0.85rem;
  color: ${(props) => props.theme.colors.textSecondary};
`;

