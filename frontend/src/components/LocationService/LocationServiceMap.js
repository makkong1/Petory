import React, { useState, useEffect, useMemo, useRef } from 'react';
import styled from 'styled-components';
import MapContainer from './MapContainer';
import { locationServiceApi } from '../../api/locationServiceApi';
import { geocodingApi } from '../../api/geocodingApi';
import { useAuth } from '../../contexts/AuthContext';
import LocationServiceForm from './LocationServiceForm';

const LocationServiceMap = () => {
  const { user } = useAuth();
  const [services, setServices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedCategory, setSelectedCategory] = useState('');
  const [selectedService, setSelectedService] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [locationSearch, setLocationSearch] = useState('');
  const [userLocation, setUserLocation] = useState(null); // 사용자 위치 {lat, lng}
  const [showForm, setShowForm] = useState(false);
  const [searchMode, setSearchMode] = useState('service'); // 'service' 또는 'location'
  const [shouldFocusOnResults, setShouldFocusOnResults] = useState(false);
  const mapContainerRef = useRef(null);

  // 사용자 위치 로드
  useEffect(() => {
    const loadUserLocation = async () => {
      if (user && user.location) {
        try {
          const response = await geocodingApi.addressToCoordinates(user.location);
          if (response.success && response.latitude && response.longitude) {
            setUserLocation({
              lat: response.latitude,
              lng: response.longitude
            });
          }
        } catch (error) {
          console.error('사용자 위치 변환 실패:', error);
          // 401 에러는 인터셉터에서 처리되므로 여기서는 조용히 실패
        }
      }
    };
    if (user) {
      loadUserLocation();
    }
  }, [user]);

  // 서비스 데이터 로드
  useEffect(() => {
    const loadServices = async () => {
      try {
        setLoading(true);
        setError(null);
        const response = await locationServiceApi.getAllServices();
        setServices(response.data?.services || []);
      } catch (error) {
        console.error('서비스 데이터 로드 실패:', error);
        // 401 에러는 인터셉터에서 처리되므로 여기서는 에러만 표시
        if (error.response?.status !== 401) {
          setError('서비스 데이터를 불러오는데 실패했습니다: ' + (error.response?.data?.error || error.message));
        }
      } finally {
        setLoading(false);
      }
    };
    loadServices();
  }, []);

  // 지역 검색 처리
  const handleLocationSearch = async () => {
    if (!locationSearch.trim()) {
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const response = await locationServiceApi.searchServicesByAddress(locationSearch);
      const searchResults = response.data?.services || [];
      setServices(searchResults);
      setSearchMode('location');
      
      // 검색 결과가 있으면 지도에 포커스
      if (searchResults.length > 0) {
        setShouldFocusOnResults(true);
      }
    } catch (error) {
      setError('지역 검색에 실패했습니다: ' + (error.response?.data?.error || error.message));
    } finally {
      setLoading(false);
    }
  };

  // 서비스 이름 검색 처리
  const handleServiceSearch = async () => {
    if (!searchTerm.trim()) {
      // 검색어가 없으면 전체 목록 다시 로드
      try {
        setLoading(true);
        const response = await locationServiceApi.getAllServices();
        setServices(response.data?.services || []);
        setSearchMode('service');
        setShouldFocusOnResults(false);
      } catch (error) {
        setError('서비스 데이터를 불러오는데 실패했습니다.');
      } finally {
        setLoading(false);
      }
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const response = await locationServiceApi.searchServicesByKeyword(searchTerm);
      const searchResults = response.data?.services || [];
      setServices(searchResults);
      setSearchMode('service');
      
      // 검색 결과가 있으면 지도에 포커스
      if (searchResults.length > 0) {
        setShouldFocusOnResults(true);
      }
    } catch (error) {
      setError('서비스 검색에 실패했습니다: ' + (error.response?.data?.error || error.message));
    } finally {
      setLoading(false);
    }
  };

  // 전체 목록 다시 로드
  const handleResetSearch = async () => {
    try {
      setLoading(true);
      setError(null);
      setSearchTerm('');
      setLocationSearch('');
      const response = await locationServiceApi.getAllServices();
      setServices(response.data?.services || []);
      setSearchMode('service');
    } catch (error) {
      setError('서비스 데이터를 불러오는데 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  // 필터링된 서비스 목록 (카테고리 필터만 적용) - useMemo로 메모이제이션
  const filteredServices = useMemo(() => {
    const filtered = services.filter(service => {
      if (!selectedCategory) return true;
      
      // 기본 카테고리 매칭
      if (service.category === selectedCategory) return true;
      
      // 특수 케이스: "샵" 선택 시 "기타" 카테고리이면서 description에 "용품" 포함된 경우도 포함
      if (selectedCategory === '샵') {
        const description = service.description || '';
        const categoryName = service.category || '';
        if (categoryName === '기타' && (
          description.includes('용품') || 
          description.includes('반려동물용품') ||
          description.includes('펫샵')
        )) {
          return true;
        }
      }
      
      // 특수 케이스: "유치원" 선택 시 "기타" 카테고리이면서 description에 "유치원" 포함된 경우도 포함
      if (selectedCategory === '유치원') {
        const description = service.description || '';
        const categoryName = service.category || '';
        if (categoryName === '기타' && (
          description.includes('유치원') || 
          description.includes('애견유치원') ||
          description.includes('펫유치원') ||
          description.includes('반려동물유치원') ||
          description.includes('강아지유치원') ||
          description.includes('견주유치원')
        )) {
          return true;
        }
      }
      
      return false;
    });
    
    // 디버깅: 선택된 카테고리와 실제 서비스 카테고리 확인
    if (selectedCategory) {
      console.log('카테고리 필터링:', {
        selectedCategory,
        totalServices: services.length,
        filteredCount: filtered.length,
        categoriesInData: [...new Set(services.map(s => s.category))]
      });
    }
    
    return filtered;
  }, [services, selectedCategory]);

  const handleServiceClick = (service) => {
    setSelectedService(service);
  };

  const categories = [
    { value: '', label: '전체' },
    { value: '병원', label: '🏥 병원' },
    { value: '샵', label: '🛒 반려동물용품' },
    { value: '유치원', label: '🏫 유치원' },
    { value: '카페', label: '☕ 카페' },
    { value: '호텔', label: '🏨 호텔' },
    { value: '미용', label: '✂️ 미용실' },
  ];

  if (loading) {
    return (
      <Container>
        <LoadingMessage>
          <div>🗺️ 지도 로딩 중...</div>
        </LoadingMessage>
      </Container>
    );
  }

  if (error) {
    return (
      <Container>
        <ErrorMessage>
          <div>❌ {error}</div>
          <button onClick={() => window.location.reload()}>다시 시도</button>
        </ErrorMessage>
      </Container>
    );
  }

  return (
    <Container>
      <Header>
        <Title>지역 서비스 정보</Title>
        <SearchSection>
          <SearchTabs>
            <SearchTab 
              active={searchMode === 'service'} 
              onClick={() => setSearchMode('service')}
            >
              서비스 검색
            </SearchTab>
            <SearchTab 
              active={searchMode === 'location'} 
              onClick={() => setSearchMode('location')}
            >
              지역 검색
            </SearchTab>
          </SearchTabs>
          {searchMode === 'service' ? (
            <LocationSearchBox>
              <SearchBox
                type="text"
                placeholder="서비스 이름으로 검색..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                onKeyPress={(e) => e.key === 'Enter' && handleServiceSearch()}
              />
              <SearchButton onClick={handleServiceSearch}>검색</SearchButton>
            </LocationSearchBox>
          ) : (
            <LocationSearchBox>
              <SearchBox
                type="text"
                placeholder="지역명으로 검색 (예: 서울시 강남구)"
                value={locationSearch}
                onChange={(e) => setLocationSearch(e.target.value)}
                onKeyPress={(e) => e.key === 'Enter' && handleLocationSearch()}
              />
              <SearchButton onClick={handleLocationSearch}>검색</SearchButton>
            </LocationSearchBox>
          )}
          {(searchTerm || locationSearch) && (
            <ResetButton onClick={handleResetSearch}>전체보기</ResetButton>
          )}
        </SearchSection>
        <button onClick={()=>setShowForm(true)} style={{marginLeft:'1rem',padding:'0.5rem 1rem',borderRadius:'1rem',background:'#28a745',color:'#fff',fontWeight:'bold',border:'none',cursor:'pointer'}}>+ 서비스 등록</button>
      </Header>

      <FilterSection>
        {categories.map(category => (
          <FilterButton
            key={category.value}
            active={selectedCategory === category.value}
            onClick={() => setSelectedCategory(category.value)}
          >
            {category.label}
          </FilterButton>
        ))}
      </FilterSection>

      <MapArea>
        <MapContainer
          ref={mapContainerRef}
          services={filteredServices}
          selectedCategory={selectedCategory}
          onServiceClick={handleServiceClick}
          userLocation={userLocation}
          shouldFocusOnResults={shouldFocusOnResults}
          onFocusComplete={() => setShouldFocusOnResults(false)}
        />
        
        {selectedService && (
          <ServiceDetailPanel>
            <CloseButton onClick={() => setSelectedService(null)}>✕</CloseButton>
            <ServiceTitle>{selectedService.name}</ServiceTitle>
            <ServiceInfo>
              <div>
                <strong>📍 주소</strong>
                <div style={{ marginTop: '0.25rem', marginLeft: '0.5rem' }}>
                  {selectedService.address || '주소 정보 없음'}
                  {selectedService.detailAddress && (
                    <div style={{ marginTop: '0.25rem', fontSize: '0.85rem', color: '#666' }}>
                      {selectedService.detailAddress}
                    </div>
                  )}
                </div>
              </div>
              {selectedService.imageUrl && (
                <div style={{ marginTop: '0.5rem' }}>
                  <img 
                    src={selectedService.imageUrl} 
                    alt={selectedService.name}
                    style={{ width: '100%', maxHeight: '200px', objectFit: 'cover', borderRadius: '8px' }}
                    onError={(e) => { e.target.style.display = 'none'; }}
                  />
                </div>
              )}
              <div>📞 {selectedService.phone || '전화번호 없음'}</div>
              <div>🕒 {selectedService.openingTime && selectedService.closingTime ? 
                `오전: ${selectedService.openingTime.substring(0,5)} 
                ~ 오후: ${selectedService.closingTime.substring(0,5)}` : '운영시간 정보 없음'}</div>
              {selectedService.rating && <div>⭐ {selectedService.rating.toFixed(1)}</div>}
              {selectedService.category && (
                <div style={{ fontSize: '0.85rem', color: '#666', marginTop: '0.5rem' }}>
                  카테고리: {selectedService.category}
                </div>
              )}
            </ServiceInfo>
            {selectedService.description && (
              <ServiceDescription>{selectedService.description}</ServiceDescription>
            )}
          </ServiceDetailPanel>
        )}
      </MapArea>
      <LocationServiceForm show={showForm} onClose={()=>setShowForm(false)} onSuccess={()=>setShowForm(false)} />
    </Container>
  );
};

export default LocationServiceMap;

const Container = styled.div`
  width: 100%;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f8f9fa;
`;

const Header = styled.div`
  padding: 1rem;
  background: white;
  border-bottom: 1px solid #e9ecef;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  flex-wrap: wrap;
`;

const Title = styled.h1`
  margin: 0;
  color: #333;
  font-size: 1.5rem;
`;

const SearchSection = styled.div`
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex: 1;
`;

const SearchTabs = styled.div`
  display: flex;
  gap: 0.25rem;
  border: 1px solid #ddd;
  border-radius: 20px;
  padding: 0.25rem;
  background: #f8f9fa;
`;

const SearchTab = styled.button.withConfig({
  shouldForwardProp: (prop) => prop !== 'active',
})`
  padding: 0.4rem 0.8rem;
  border: none;
  border-radius: 16px;
  font-size: 0.85rem;
  cursor: pointer;
  background: ${props => props.active ? '#007bff' : 'transparent'};
  color: ${props => props.active ? 'white' : '#666'};
  transition: all 0.2s;
  
  &:hover {
    background: ${props => props.active ? '#0056b3' : '#e9ecef'};
  }
`;

const SearchBox = styled.input`
  padding: 0.5rem 1rem;
  border: 1px solid #ddd;
  border-radius: 20px;
  width: 300px;
  font-size: 0.9rem;
  
  &:focus {
    outline: none;
    border-color: #007bff;
  }
`;

const LocationSearchBox = styled.div`
  display: flex;
  gap: 0.5rem;
  align-items: center;
`;

const SearchButton = styled.button`
  padding: 0.5rem 1rem;
  background: #007bff;
  color: white;
  border: none;
  border-radius: 20px;
  font-size: 0.9rem;
  cursor: pointer;
  transition: background 0.2s;
  
  &:hover {
    background: #0056b3;
  }
`;

const ResetButton = styled.button`
  padding: 0.5rem 1rem;
  background: #6c757d;
  color: white;
  border: none;
  border-radius: 20px;
  font-size: 0.9rem;
  cursor: pointer;
  transition: background 0.2s;
  
  &:hover {
    background: #5a6268;
  }
`;

const FilterSection = styled.div`
  padding: 1rem;
  background: white;
  border-bottom: 1px solid #e9ecef;
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
`;

const FilterButton = styled.button.withConfig({
  shouldForwardProp: (prop) => prop !== 'active',
})`
  padding: 0.5rem 1rem;
  border: 1px solid #ddd;
  border-radius: 20px;
  background: ${props => props.active ? '#007bff' : 'white'};
  color: ${props => props.active ? 'white' : '#333'};
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.2s;
  
  &:hover {
    background: ${props => props.active ? '#0056b3' : '#f8f9fa'};
  }
`;

const MapArea = styled.div`
  flex: 1;
  position: relative;
  background: #f0f0f0;
`;

const ServiceDetailPanel = styled.div`
  position: absolute;
  top: 1rem;
  right: 1rem;
  width: 300px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  padding: 1rem;
  z-index: 1000;
`;

const CloseButton = styled.button`
  position: absolute;
  top: 0.5rem;
  right: 0.5rem;
  background: none;
  border: none;
  font-size: 1.2rem;
  cursor: pointer;
  color: #666;
  
  &:hover {
    color: #333;
  }
`;

const ServiceTitle = styled.h3`
  margin: 0 0 1rem 0;
  color: #333;
  font-size: 1.2rem;
`;

const ServiceInfo = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  font-size: 0.9rem;
  color: #666;
  margin-bottom: 1rem;
`;

const ServiceDescription = styled.div`
  font-size: 0.9rem;
  color: #555;
  line-height: 1.4;
`;

const LoadingMessage = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  font-size: 1.2rem;
  color: #666;
`;

const ErrorMessage = styled.div`
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  height: 100%;
  font-size: 1.2rem;
  color: #dc3545;
  gap: 1rem;
  
  button {
    padding: 0.5rem 1rem;
    background: #007bff;
    color: white;
    border: none;
    border-radius: 4px;
    cursor: pointer;
    
    &:hover {
      background: #0056b3;
    }
  }
`;