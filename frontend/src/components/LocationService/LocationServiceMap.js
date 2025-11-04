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
  const [currentLocation, setCurrentLocation] = useState(null); // 현재 위치 (Geolocation API)
  const [showForm, setShowForm] = useState(false);
  const [searchMode, setSearchMode] = useState('service'); // 'service', 'location', 'region', 'radius'
  const [shouldFocusOnResults, setShouldFocusOnResults] = useState(false);
  const [selectedSido, setSelectedSido] = useState('');
  const [selectedSigungu, setSelectedSigungu] = useState('');
  const [selectedDong, setSelectedDong] = useState('');
  const [sortBy, setSortBy] = useState('rating'); // 'rating', 'name', 'createdAt'
  const mapContainerRef = useRef(null);

  // 전국 시/도 목록
  const sidos = [
    '서울특별시', '부산광역시', '대구광역시', '인천광역시', '광주광역시', '대전광역시', '울산광역시',
    '세종특별자치시', '경기도', '강원특별자치도', '충청북도', '충청남도', '전북특별자치도', 
    '전라남도', '경상북도', '경상남도', '제주특별자치도'
  ];

  // 시/도별 시/군/구 목록 (주요 지역만)
  const sigungus = {
    '서울특별시': [
      '강남구', '강동구', '강북구', '강서구', '관악구', '광진구', '구로구', '금천구',
      '노원구', '도봉구', '동대문구', '동작구', '마포구', '서대문구', '서초구', '성동구',
      '성북구', '송파구', '양천구', '영등포구', '용산구', '은평구', '종로구', '중구', '중랑구'
    ],
    '부산광역시': ['중구', '서구', '동구', '영도구', '부산진구', '동래구', '남구', '북구', '해운대구', '사하구', '금정구', '강서구', '연제구', '수영구', '사상구', '기장군'],
    '대구광역시': ['중구', '동구', '서구', '남구', '북구', '수성구', '달서구', '달성군'],
    '인천광역시': ['중구', '동구', '미추홀구', '연수구', '남동구', '부평구', '계양구', '서구', '강화군', '옹진군'],
    '광주광역시': ['동구', '서구', '남구', '북구', '광산구'],
    '대전광역시': ['동구', '중구', '서구', '유성구', '대덕구'],
    '울산광역시': ['중구', '남구', '동구', '북구', '울주군'],
    '세종특별자치시': ['세종시'],
    '경기도': ['수원시', '성남시', '고양시', '용인시', '부천시', '안산시', '안양시', '남양주시', '화성시', '평택시', '의정부시', '시흥시', '김포시', '광명시', '광주시', '이천시', '양주시', '오산시', '구리시', '안성시', '포천시', '의왕시', '하남시', '여주시', '양평군', '동두천시', '과천시', '가평군', '연천군'],
    '강원특별자치도': ['춘천시', '원주시', '강릉시', '동해시', '태백시', '속초시', '삼척시', '홍천군', '횡성군', '영월군', '평창군', '정선군', '철원군', '화천군', '양구군', '인제군', '고성군', '양양군'],
    '충청북도': ['청주시', '충주시', '제천시', '보은군', '옥천군', '영동군', '증평군', '진천군', '괴산군', '음성군', '단양군'],
    '충청남도': ['천안시', '공주시', '보령시', '아산시', '서산시', '논산시', '계룡시', '당진시', '금산군', '부여군', '서천군', '청양군', '홍성군', '예산군', '태안군'],
    '전북특별자치도': ['전주시', '군산시', '익산시', '정읍시', '남원시', '김제시', '완주군', '진안군', '무주군', '장수군', '임실군', '순천시', '순창군', '고창군', '부안군'],
    '전라남도': ['목포시', '여수시', '순천시', '나주시', '광양시', '담양군', '곡성군', '구례군', '고흥군', '보성군', '화순군', '장흥군', '강진군', '해남군', '영암군', '무안군', '함평군', '영광군', '장성군', '완도군', '진도군', '신안군'],
    '경상북도': ['포항시', '경주시', '김천시', '안동시', '구미시', '영주시', '영천시', '상주시', '문경시', '경산시', '군위군', '의성군', '청송군', '영양군', '영덕군', '청도군', '고령군', '성주군', '칠곡군', '예천군', '봉화군', '울진군', '울릉군'],
    '경상남도': ['창원시', '진주시', '통영시', '사천시', '김해시', '밀양시', '거제시', '양산시', '의령군', '함안군', '창녕군', '고성군', '남해군', '하동군', '산청군', '함양군', '거창군', '합천군'],
    '제주특별자치도': ['제주시', '서귀포시']
  };

  // 시/군/구별 동/면/리 목록 (주요 지역만)
  const dongs = {
    // 서울특별시
    '강남구': ['역삼동', '개포동', '논현동', '대치동', '도곡동', '삼성동', '세곡동', '수서동', '신사동', '압구정동', '일원동', '청담동'],
    '강동구': ['강일동', '고덕동', '길동', '둔촌동', '명일동', '상일동', '성내동', '암사동', '천호동'],
    '강북구': ['미아동', '번동', '수유동', '우이동'],
    '강서구': ['가양동', '공항동', '등촌동', '방화동', '염창동', '화곡동'],
    '관악구': ['남현동', '봉천동', '신림동', '은천동', '인헌동', '청림동', '청룡동', '행운동'],
    '광진구': ['광장동', '구의동', '군자동', '능동', '자양동', '중곡동', '화양동'],
    '구로구': ['가리봉동', '개봉동', '고척동', '구로동', '궁동', '신도림동', '오류동', '온수동', '천왕동', '항동'],
    '금천구': ['가산동', '독산동', '시흥동'],
    '노원구': ['공릉동', '상계동', '월계동', '중계동', '하계동'],
    '도봉구': ['도봉동', '방학동', '쌍문동', '창동'],
    '동대문구': ['답십리동', '용신동', '이문동', '장안동', '전농동', '제기동', '청량리동', '회기동', '휘경동'],
    '동작구': ['노량진동', '대방동', '사당동', '상도동', '신대방동', '흑석동'],
    '마포구': ['공덕동', '구수동', '노고산동', '당인동', '대흥동', '도화동', '동교동', '망원동', '상암동', '서강동', '서교동', '성산동', '신수동', '아현동', '연남동', '염리동', '용강동', '합정동'],
    '서대문구': ['경의동', '교남동', '냉천동', '대신동', '대현동', '미근동', '봉원동', '북아현동', '신촌동', '연희동', '영천동', '옥천동', '충현동', '천연동', '홍은동', '홍제동'],
    '서초구': ['내곡동', '반포동', '방배동', '서초동', '신원동', '양재동', '염곡동', '우면동', '원지동', '잠원동'],
    '성동구': ['금호동', '도선동', '마장동', '사근동', '상왕십리동', '성수동', '송정동', '옥수동', '용답동', '응봉동', '하왕십리동', '행당동'],
    '성북구': ['길음동', '돈암동', '동선동', '동소문동', '보문동', '삼선동', '상월곡동', '석관동', '성북동', '안암동', '월곡동', '장위동', '정릉동', '종암동', '하월곡동'],
    '송파구': ['가락동', '거여동', '마천동', '문정동', '방이동', '삼전동', '석촌동', '송파동', '신천동', '잠실동', '장지동', '천호동', '풍납동', '오금동', '위례동'],
    '양천구': ['목동', '신월동', '신정동'],
    '영등포구': ['당산동', '대림동', '도림동', '문래동', '신길동', '양평동', '여의도동', '영등포동', '여의도'],
    '용산구': ['갈월동', '남영동', '도원동', '동빙고동', '동자동', '문배동', '보광동', '산천동', '서빙고동', '서계동', '신계동', '신창동', '용산동', '용산동2가', '원효로동', '이촌동', '이태원동', '한강로동', '한남동', '효창동', '후암동'],
    '은평구': ['갈현동', '구산동', '녹번동', '대조동', '불광동', '수색동', '신사동', '역촌동', '응암동', '증산동', '진관동'],
    '종로구': ['가회동', '견지동', '경운동', '계동', '공평동', '관수동', '관철동', '교남동', '교북동', '구기동', '궁정동', '권농동', '낙원동', '내수동', '내자동', '누상동', '누하동', '당주동', '도렴동', '돈의동', '동숭동', '명륜동', '무악동', '봉익동', '부암동', '사간동', '사직동', '삼청동', '서린동', '세종로', '소격동', '송월동', '송현동', '수송동', '숭인동', '신교동', '신문로동', '신영동', '안국동', '연건동', '연지동', '예지동', '옥인동', '와룡동', '운니동', '원남동', '원서동', '이화동', '익선동', '인사동', '인의동', '장사동', '재동', '적선동', '종로동', '종로1가', '종로2가', '종로3가', '종로4가', '종로5가', '종로6가', '중학동', '창신동', '청와대로', '청진동', '체부동', '충신동', '통의동', '통인동', '팔판동', '평동', '평창동', '필운동', '행촌동', '혜화동', '홍지동', '홍파동', '화동', '효자동', '효제동'],
    '중구': ['광희동', '다동', '동호동', '명동', '무교동', '무학동', '묵정동', '방산동', '북창동', '산림동', '을지로동', '을지로1가', '을지로2가', '을지로3가', '을지로4가', '을지로5가', '을지로6가', '을지로7가', '장교동', '장충동', '저동', '정동', '주교동', '주자동', '중림동', '초동', '충무로', '충무로1가', '충무로2가', '충무로3가', '충무로4가', '충무로5가', '태평로', '태평로1가', '태평로2가', '필동', '황학동', '회현동'],
    '중랑구': ['면목동', '망우동', '묵동', '상봉동', '신내동', '중화동', '철암동']
  };

  // 사용자 위치 로드 (DB 저장 위치)
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

  // 전국 지역 검색 처리
  const handleRegionSearch = async () => {
    if (!selectedSido) {
      alert('시/도를 선택해주세요.');
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const response = await locationServiceApi.getServicesByRegion(
        selectedSido, 
        selectedSigungu || null, 
        selectedDong || null
      );
      const searchResults = response.data?.services || [];
      setServices(searchResults);
      setSearchMode('region');
      
      if (searchResults.length > 0) {
        setShouldFocusOnResults(true);
      } else {
        alert('검색 결과가 없습니다.');
      }
    } catch (error) {
      setError('지역 검색에 실패했습니다: ' + (error.response?.data?.error || error.message));
    } finally {
      setLoading(false);
    }
  };

  // 내 위치 기준 반경 3km 검색
  const handleRadiusSearch = () => {
    if (!currentLocation) {
      // 현재 위치가 없으면 먼저 가져오기
      if (navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(
          (position) => {
            const location = {
              lat: position.coords.latitude,
              lng: position.coords.longitude
            };
            setCurrentLocation(location);
            setUserLocation(location); // 지도 표시용으로도 사용
            // 위치 가져온 후 검색 수행
            performRadiusSearch(location);
          },
          (error) => {
            console.error('위치 정보 가져오기 실패:', error);
            alert('위치 정보를 가져올 수 없습니다. 위치 권한을 확인해주세요.');
          }
        );
      } else {
        alert('이 브라우저는 위치 정보를 지원하지 않습니다.');
      }
      return;
    }
    performRadiusSearch(currentLocation);
  };

  const performRadiusSearch = async (location) => {
    if (!location || !location.lat || !location.lng) {
      alert('위치 정보가 없습니다.');
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const response = await locationServiceApi.getServicesByRadius(location.lat, location.lng, 3000);
      const searchResults = response.data?.services || [];
      setServices(searchResults);
      setSearchMode('radius');
      
      if (searchResults.length > 0) {
        setShouldFocusOnResults(true);
      } else {
        alert('반경 3km 내에 서비스가 없습니다.');
      }
    } catch (error) {
      setError('반경 검색에 실패했습니다: ' + (error.response?.data?.error || error.message));
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
      setSelectedSido('');
      setSelectedSigungu('');
      setSelectedDong('');
      setCurrentLocation(null);
      const response = await locationServiceApi.getAllServices();
      setServices(response.data?.services || []);
      setSearchMode('service');
    } catch (error) {
      setError('서비스 데이터를 불러오는데 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  // 필터링 및 정렬된 서비스 목록 - useMemo로 메모이제이션
  const filteredServices = useMemo(() => {
    let filtered = services.filter(service => {
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
    
    // 정렬 적용
    const sorted = [...filtered].sort((a, b) => {
      if (sortBy === 'rating') {
        // 평점순 (높은 순)
        const ratingA = a.rating || 0;
        const ratingB = b.rating || 0;
        return ratingB - ratingA;
      } else if (sortBy === 'name') {
        // 이름순 (가나다순)
        return (a.name || '').localeCompare(b.name || '');
      } else if (sortBy === 'createdAt') {
        // 최신순 (최신이 먼저)
        // createdAt이 없으면 기본값으로 처리
        return 0; // createdAt 필드가 없으면 순서 유지
      }
      return 0;
    });
    
    return sorted;
  }, [services, selectedCategory, sortBy]);

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
            <SearchTab 
              active={searchMode === 'region'} 
              onClick={() => setSearchMode('region')}
            >
              지역 선택
            </SearchTab>
            <SearchTab 
              active={searchMode === 'radius'} 
              onClick={() => setSearchMode('radius')}
            >
              내 주변 3km
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
          ) : searchMode === 'location' ? (
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
          ) : searchMode === 'region' ? (
            <LocationSearchBox>
              <SeoulSelect
                value={selectedSido}
                onChange={(e) => {
                  setSelectedSido(e.target.value);
                  setSelectedSigungu(''); // 시/도 변경 시 시/군/구 초기화
                  setSelectedDong(''); // 동도 초기화
                }}
              >
                <option value="">시/도 선택</option>
                {sidos.map(sido => (
                  <option key={sido} value={sido}>{sido}</option>
                ))}
              </SeoulSelect>
              <SeoulSelect
                value={selectedSigungu}
                onChange={(e) => {
                  setSelectedSigungu(e.target.value);
                  setSelectedDong(''); // 시/군/구 변경 시 동 초기화
                }}
                disabled={!selectedSido}
              >
                <option value="">시/군/구 선택 (선택사항)</option>
                {selectedSido && sigungus[selectedSido]?.map(sigungu => (
                  <option key={sigungu} value={sigungu}>{sigungu}</option>
                ))}
              </SeoulSelect>
              <SeoulSelect
                value={selectedDong}
                onChange={(e) => setSelectedDong(e.target.value)}
                disabled={!selectedSigungu}
              >
                <option value="">동/면/리 선택 (선택사항)</option>
                {selectedSigungu && dongs[selectedSigungu]?.map(dong => (
                  <option key={dong} value={dong}>{dong}</option>
                ))}
              </SeoulSelect>
              <SearchButton onClick={handleRegionSearch} disabled={!selectedSido}>
                검색
              </SearchButton>
            </LocationSearchBox>
          ) : (
            <LocationSearchBox>
              <SearchButton 
                onClick={handleRadiusSearch}
                style={{ background: '#28a745' }}
              >
                {currentLocation ? '내 주변 3km 검색' : '📍 위치 가져오기 & 검색'}
              </SearchButton>
            </LocationSearchBox>
          )}
          {(searchTerm || locationSearch || selectedSido || searchMode === 'radius') && (
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
        <SortSelect value={sortBy} onChange={(e) => setSortBy(e.target.value)}>
          <option value="rating">⭐ 평점순</option>
          <option value="name">🔤 이름순</option>
        </SortSelect>
      </FilterSection>

      <MapArea>
        <MapWrapper>
          <MapContainer
            ref={mapContainerRef}
            services={filteredServices}
            selectedCategory={selectedCategory}
            onServiceClick={handleServiceClick}
            userLocation={userLocation}
            shouldFocusOnResults={shouldFocusOnResults}
            onFocusComplete={() => setShouldFocusOnResults(false)}
          />
        </MapWrapper>
        
        <ServiceListPanel>
          <ServiceListHeader>
            <ServiceListTitle>서비스 목록 ({filteredServices.length})</ServiceListTitle>
          </ServiceListHeader>
          <ServiceListContent>
            {filteredServices.length === 0 ? (
              <EmptyMessage>표시할 서비스가 없습니다.</EmptyMessage>
            ) : (
              filteredServices.map((service) => (
                <ServiceListItem
                  key={service.idx}
                  onClick={() => handleServiceClick(service)}
                  active={selectedService?.idx === service.idx}
                >
                  <ServiceListItemHeader>
                    <ServiceListItemName>{service.name}</ServiceListItemName>
                    {service.rating && (
                      <ServiceListItemRating>⭐ {service.rating.toFixed(1)}</ServiceListItemRating>
                    )}
                  </ServiceListItemHeader>
                  {service.category && (
                    <ServiceListItemCategory>{service.category}</ServiceListItemCategory>
                  )}
                  {service.address && (
                    <ServiceListItemAddress>📍 {service.address}</ServiceListItemAddress>
                  )}
                  {service.phone && (
                    <ServiceListItemPhone>📞 {service.phone}</ServiceListItemPhone>
                  )}
                </ServiceListItem>
              ))
            )}
          </ServiceListContent>
        </ServiceListPanel>
        
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
  align-items: center;
`;

const SortSelect = styled.select`
  padding: 0.5rem 1rem;
  border: 1px solid #ddd;
  border-radius: 20px;
  font-size: 0.9rem;
  cursor: pointer;
  background: white;
  margin-left: auto;
  
  &:focus {
    outline: none;
    border-color: #007bff;
  }
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
  display: flex;
  overflow: hidden;
`;

const MapWrapper = styled.div`
  flex: 1;
  position: relative;
  min-width: 0;
`;

const ServiceListPanel = styled.div`
  width: 350px;
  background: white;
  border-left: 1px solid #e9ecef;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  z-index: 100;
`;

const ServiceListHeader = styled.div`
  padding: 1rem;
  border-bottom: 1px solid #e9ecef;
  background: #f8f9fa;
`;

const ServiceListTitle = styled.h3`
  margin: 0;
  font-size: 1rem;
  color: #333;
  font-weight: 600;
`;

const ServiceListContent = styled.div`
  flex: 1;
  overflow-y: auto;
  padding: 0.5rem;
`;

const ServiceListItem = styled.div.withConfig({
  shouldForwardProp: (prop) => prop !== 'active',
})`
  padding: 1rem;
  margin-bottom: 0.5rem;
  border: 1px solid ${props => props.active ? '#007bff' : '#e9ecef'};
  border-radius: 8px;
  background: ${props => props.active ? '#f0f7ff' : 'white'};
  cursor: pointer;
  transition: all 0.2s;
  
  &:hover {
    border-color: #007bff;
    box-shadow: 0 2px 8px rgba(0, 123, 255, 0.15);
    transform: translateY(-2px);
  }
`;

const ServiceListItemHeader = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
`;

const ServiceListItemName = styled.div`
  font-weight: 600;
  font-size: 1rem;
  color: #333;
  flex: 1;
`;

const ServiceListItemRating = styled.div`
  font-size: 0.9rem;
  color: #ff9800;
  font-weight: 600;
`;

const ServiceListItemCategory = styled.div`
  font-size: 0.85rem;
  color: #666;
  margin-bottom: 0.25rem;
`;

const ServiceListItemAddress = styled.div`
  font-size: 0.85rem;
  color: #666;
  margin-bottom: 0.25rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

const ServiceListItemPhone = styled.div`
  font-size: 0.85rem;
  color: #666;
`;

const EmptyMessage = styled.div`
  padding: 2rem;
  text-align: center;
  color: #999;
  font-size: 0.9rem;
`;

const ServiceDetailPanel = styled.div`
  position: absolute;
  top: 1rem;
  left: 1rem;
  width: 300px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  padding: 1rem;
  z-index: 1000;
  max-height: calc(100vh - 200px);
  overflow-y: auto;
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

const SeoulSelect = styled.select`
  padding: 0.5rem 1rem;
  border: 1px solid #ddd;
  border-radius: 20px;
  font-size: 0.9rem;
  cursor: pointer;
  background: white;
  
  &:focus {
    outline: none;
    border-color: #007bff;
  }
  
  &:disabled {
    background: #f5f5f5;
    cursor: not-allowed;
    opacity: 0.6;
  }
`;
