import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import MapContainer from './MapContainer';
import { locationServiceApi } from '../../api/locationServiceApi';

const LocationServiceMap = () => {
  const [services, setServices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedCategory, setSelectedCategory] = useState('');
  const [selectedService, setSelectedService] = useState(null);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [viewMode, setViewMode] = useState('map'); // 'map' or 'list'

  // 서비스 카테고리 목록
  const categories = [
    { value: '', label: '전체' },
    { value: '병원', label: '🏥 병원' },
    { value: '용품점', label: '🛒 용품점' },
    { value: '유치원', label: '🏫 유치원' },
    { value: '카페', label: '☕ 카페' },
    { value: '호텔', label: '🏨 호텔' },
    { value: '미용실', label: '✂️ 미용실' },
  ];

  // 서비스 데이터 가져오기
  useEffect(() => {
    loadServices();
  }, [selectedCategory, searchKeyword]);

  // 카카오맵 API 로드 확인 (index.html에서 이미 로드됨)
  useEffect(() => {
    if (window.kakao && window.kakao.maps) {
      window.kakao.maps.load(() => {
        console.log('카카오맵 API 로드 완료');
      });
    }
  }, []);

  const loadServices = async () => {
    try {
      setLoading(true);
      setError(null);

      let response;
      if (searchKeyword) {
        response = await locationServiceApi.searchServicesByKeyword(searchKeyword);
      } else if (selectedCategory) {
        response = await locationServiceApi.getServicesByCategory(selectedCategory);
      } else {
        response = await locationServiceApi.getAllServices();
      }

      setServices(response.data.services || []);
    } catch (error) {
      console.error('서비스 데이터 로딩 실패:', error);
      setError('데이터를 불러오는데 실패했습니다.');
      setServices([]);
    } finally {
      setLoading(false);
    }
  };

  const handleServiceClick = (service) => {
    setSelectedService(service);
  };

  const handleSearch = (e) => {
    e.preventDefault();
    loadServices();
  };

  const filteredServices = services.filter(service => {
    if (selectedCategory && service.category !== selectedCategory) return false;
    if (searchKeyword && !service.name.includes(searchKeyword)) return false;
    return true;
  });

  return (
    <Container>
      <Header>
        <Title>📍 주변 서비스 찾기</Title>
        <ViewModeToggle>
          <ModeButton 
            active={viewMode === 'map'} 
            onClick={() => setViewMode('map')}
          >
            🗺️ 지도
          </ModeButton>
          <ModeButton 
            active={viewMode === 'list'} 
            onClick={() => setViewMode('list')}
          >
            📋 리스트
          </ModeButton>
        </ViewModeToggle>
      </Header>

      <FilterSection>
        <SearchForm onSubmit={handleSearch}>
          <SearchInput
            type="text"
            placeholder="서비스 이름으로 검색..."
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
          />
          <SearchButton type="submit">검색</SearchButton>
        </SearchForm>

        <CategoryFilter>
          {categories.map(category => (
            <CategoryButton
              key={category.value}
              active={selectedCategory === category.value}
              onClick={() => setSelectedCategory(category.value)}
            >
              {category.label}
            </CategoryButton>
          ))}
        </CategoryFilter>
      </FilterSection>

      {loading && <LoadingMessage>데이터 로딩 중...</LoadingMessage>}
      {error && <ErrorMessage>{error}</ErrorMessage>}

      <ContentArea>
        {viewMode === 'map' ? (
          <MapView>
            <MapContainer
              services={services}
              selectedCategory={selectedCategory}
              onServiceClick={handleServiceClick}
            />
            {selectedService && (
              <ServiceDetailPanel>
                <CloseButton onClick={() => setSelectedService(null)}>✕</CloseButton>
                <ServiceTitle>{selectedService.name}</ServiceTitle>
                <ServiceInfo>
                  <InfoItem>📍 {selectedService.address}</InfoItem>
                  <InfoItem>🏷️ {selectedService.category}</InfoItem>
                  {selectedService.rating && (
                    <InfoItem>⭐ {selectedService.rating.toFixed(1)}</InfoItem>
                  )}
                  {selectedService.phoneNumber && (
                    <InfoItem>📞 {selectedService.phoneNumber}</InfoItem>
                  )}
                  {selectedService.operatingHours && (
                    <InfoItem>⏰ {selectedService.operatingHours}</InfoItem>
                  )}
                  {selectedService.description && (
                    <InfoItem>📝 {selectedService.description}</InfoItem>
                  )}
                </ServiceInfo>
              </ServiceDetailPanel>
            )}
          </MapView>
        ) : (
          <ListView>
            {filteredServices.length === 0 ? (
              <EmptyMessage>
                <div className="icon">📍</div>
                <h3>등록된 서비스가 없습니다</h3>
              </EmptyMessage>
            ) : (
              <ServiceGrid>
                {filteredServices.map(service => (
                  <ServiceCard 
                    key={service.idx}
                    onClick={() => handleServiceClick(service)}
                  >
                    <CardHeader>
                      <CardTitle>{service.name}</CardTitle>
                      {service.rating && (
                        <RatingBadge>
                          ⭐ {service.rating.toFixed(1)}
                        </RatingBadge>
                      )}
                    </CardHeader>
                    <CardInfo>
                      <InfoText>🏷️ {service.category}</InfoText>
                      <InfoText>📍 {service.address}</InfoText>
                      {service.phoneNumber && (
                        <InfoText>📞 {service.phoneNumber}</InfoText>
                      )}
                      {service.reviewCount > 0 && (
                        <InfoText>📝 리뷰 {service.reviewCount}개</InfoText>
                      )}
                    </CardInfo>
                  </ServiceCard>
                ))}
              </ServiceGrid>
            )}
          </ListView>
        )}
      </ContentArea>
    </Container>
  );
};

export default LocationServiceMap;

const Container = styled.div`
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 2rem;
  background: #f5f5f5;
`;

const Header = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
`;

const Title = styled.h1`
  font-size: 2rem;
  color: #333;
  margin: 0;
`;

const ViewModeToggle = styled.div`
  display: flex;
  gap: 0.5rem;
`;

const ModeButton = styled.button`
  padding: 0.5rem 1rem;
  border: 2px solid ${props => props.active ? '#007bff' : '#ddd'};
  background: ${props => props.active ? '#007bff' : 'white'};
  color: ${props => props.active ? 'white' : '#333'};
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.2s ease;

  &:hover {
    border-color: #007bff;
  }
`;

const FilterSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin-bottom: 1.5rem;
`;

const SearchForm = styled.form`
  display: flex;
  gap: 0.5rem;
`;

const SearchInput = styled.input`
  flex: 1;
  padding: 0.75rem;
  border: 2px solid #ddd;
  border-radius: 6px;
  font-size: 1rem;
`;

const SearchButton = styled.button`
  padding: 0.75rem 1.5rem;
  background: #007bff;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 600;

  &:hover {
    background: #0056b3;
  }
`;

const CategoryFilter = styled.div`
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
`;

const CategoryButton = styled.button`
  padding: 0.5rem 1rem;
  border: 2px solid ${props => props.active ? '#28a745' : '#ddd'};
  background: ${props => props.active ? '#28a745' : 'white'};
  color: ${props => props.active ? 'white' : '#333'};
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.2s ease;

  &:hover {
    border-color: #28a745;
  }
`;

const ContentArea = styled.div`
  flex: 1;
  display: flex;
  flex-direction: column;
`;

const MapView = styled.div`
  flex: 1;
  position: relative;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
`;

const ListView = styled.div`
  flex: 1;
  overflow-y: auto;
`;

const ServiceGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 1.5rem;
`;

const ServiceCard = styled.div`
  background: white;
  border-radius: 8px;
  padding: 1.5rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  }
`;

const CardHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
`;

const CardTitle = styled.h3`
  margin: 0;
  font-size: 1.2rem;
  color: #333;
`;

const RatingBadge = styled.span`
  background: #ffc107;
  color: #333;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  font-size: 0.9rem;
  font-weight: 600;
`;

const CardInfo = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
`;

const InfoText = styled.div`
  font-size: 0.9rem;
  color: #666;
`;

const ServiceDetailPanel = styled.div`
  position: absolute;
  top: 20px;
  right: 20px;
  background: white;
  padding: 1.5rem;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
  max-width: 350px;
  z-index: 1000;
`;

const CloseButton = styled.button`
  position: absolute;
  top: 10px;
  right: 10px;
  background: none;
  border: none;
  font-size: 1.5rem;
  cursor: pointer;
  color: #666;

  &:hover {
    color: #333;
  }
`;

const ServiceTitle = styled.h2`
  margin: 0 0 1rem 0;
  font-size: 1.5rem;
  color: #333;
`;

const ServiceInfo = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
`;

const InfoItem = styled.div`
  font-size: 0.95rem;
  color: #666;
`;

const LoadingMessage = styled.div`
  text-align: center;
  padding: 2rem;
  color: #666;
`;

const ErrorMessage = styled.div`
  text-align: center;
  padding: 2rem;
  color: #dc3545;
  background: #f8d7da;
  border-radius: 6px;
`;

const EmptyMessage = styled.div`
  text-align: center;
  padding: 4rem 2rem;
  color: #666;

  .icon {
    font-size: 4rem;
    margin-bottom: 1rem;
  }

  h3 {
    margin: 0;
    font-size: 1.5rem;
  }
`;
