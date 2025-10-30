import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import MapContainer from './MapContainer';
import { locationServiceApi } from '../../api/locationServiceApi';
import LocationServiceForm from './LocationServiceForm';

const LocationServiceMap = () => {
  const [services, setServices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedCategory, setSelectedCategory] = useState('');
  const [selectedService, setSelectedService] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [showForm, setShowForm] = useState(false);

  // 서비스 데이터 로드
  useEffect(() => {
    const loadServices = async () => {
      console.log('loadServices 시작');
      try {
        setLoading(true);
        setError(null);
        console.log('loading 상태를 true로 설정');
        
        // 임시 더미 데이터 (백엔드 API가 준비될 때까지)
        const dummyServices = [
          {
            idx: 1,
            name: '강남 펫병원',
            category: '병원',
            address: '서울시 강남구 테헤란로 123',
            latitude: 37.5665,
            longitude: 126.9780,
            rating: 4.5,
            phoneNumber: '02-1234-5678',
            operatingHours: '09:00-18:00',
            description: '24시간 응급실 운영'
          },
          {
            idx: 2,
            name: '펫샵 강남점',
            category: '용품점',
            address: '서울시 강남구 역삼동 456',
            latitude: 37.5666,
            longitude: 126.9781,
            rating: 4.2,
            phoneNumber: '02-2345-6789',
            operatingHours: '10:00-20:00',
            description: '다양한 펫용품 판매'
          },
          {
            idx: 3,
            name: '펫카페 강남',
            category: '카페',
            address: '서울시 강남구 논현동 789',
            latitude: 37.5667,
            longitude: 126.9782,
            rating: 4.8,
            phoneNumber: '02-3456-7890',
            operatingHours: '08:00-22:00',
            description: '반려동물과 함께하는 카페'
          }
        ];
        
        console.log('더미 데이터 설정:', dummyServices);
        setServices(dummyServices);
        
        // 실제 API 호출 (주석 처리)
        // const response = await locationServiceApi.getAllLocationServices();
        // setServices(response.data || []);
      } catch (error) {
        console.error('데이터 로딩 에러:', error);
        setError('서비스 데이터를 불러오는데 실패했습니다.');
      } finally {
        console.log('loading 상태를 false로 설정');
        setLoading(false);
      }
    };

    loadServices();
  }, []);

  // 필터링된 서비스 목록
  const filteredServices = services.filter(service => {
    const matchesCategory = !selectedCategory || service.category === selectedCategory;
    const matchesSearch = !searchTerm || 
      service.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      service.address.toLowerCase().includes(searchTerm.toLowerCase());
    return matchesCategory && matchesSearch;
  });

  const handleServiceClick = (service) => {
    setSelectedService(service);
  };

  const categories = [
    { value: '', label: '전체' },
    { value: '병원', label: '🏥 병원' },
    { value: '용품점', label: '🛒 용품점' },
    { value: '유치원', label: '🏫 유치원' },
    { value: '카페', label: '☕ 카페' },
    { value: '호텔', label: '🏨 호텔' },
    { value: '미용실', label: '✂️ 미용실' },
  ];

  console.log('LocationServiceMap 렌더링, loading:', loading, 'services:', services);

  if (loading) {
    console.log('loading이 true이므로 로딩 화면 표시');
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
        <SearchBox
          type="text"
          placeholder="서비스 검색..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
        />
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
          services={filteredServices}
          selectedCategory={selectedCategory}
          onServiceClick={handleServiceClick}
        />
        
        {selectedService && (
          <ServiceDetailPanel>
            <CloseButton onClick={() => setSelectedService(null)}>✕</CloseButton>
            <ServiceTitle>{selectedService.name}</ServiceTitle>
            <ServiceInfo>
              <div>📍 {selectedService.address}</div>
              <div>📞 {selectedService.phoneNumber || '전화번호 없음'}</div>
              <div>🕒 {selectedService.operatingHours || '운영시간 정보 없음'}</div>
              {selectedService.rating && <div>⭐ {selectedService.rating.toFixed(1)}</div>}
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
`;

const Title = styled.h1`
  margin: 0;
  color: #333;
  font-size: 1.5rem;
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