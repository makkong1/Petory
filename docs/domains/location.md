# Location 도메인

## 개요

위치 기반 서비스 (병원, 카페, 공원, 펫샵 등) 정보 제공 및 리뷰 관리 도메인입니다.
DB에 저장된 공공데이터를 기반으로 **지역 계층적 탐색** 방식의 서비스 검색을 제공합니다.

**중요**: 현재 구현은 지도를 사용하지 않으며, 지역 선택 UI와 서비스 목록만 제공합니다.

## 핵심 개념

### 지역 계층적 탐색
지역을 계층적으로 선택하여 서비스를 검색합니다:
- **시도**: 전국 17개 시도 선택 (예: 서울특별시, 경기도)
- **시군구**: 선택된 시도의 시군구 선택 (예: 서울특별시 → 강남구, 노원구)
- **읍면동**: 선택된 시군구의 읍면동 선택 (예: 노원구 → 상계동, 하계동)
- **도로명**: 선택된 읍면동의 도로명 선택 (예: 상계동 → 상계로)

### 내 위치 활용
- **거리 계산**: 내 위치에서 각 서비스까지의 거리 표시 (Haversine 공식)
- **길찾기**: 네이버맵 길찾기 연동 (외부 링크)

### UI 구성
- **지역 선택 UI**: 시도 → 시군구 → 읍면동 계층적 선택 버튼
- **서비스 목록**: 선택된 지역의 서비스 목록 표시
- **상세 모달**: 서비스 클릭 시 상세 정보, 길찾기, 네이버맵 링크 제공

## Entity 구조

### 1. LocationService (위치 서비스)

```java
@Entity
@Table(name = "locationservice")
public class LocationService {
    // 기본 필드
    Long idx;                    // PK (bigint, auto_increment)
    String name;                 // 서비스 이름 (varchar(150))
    String address;              // 주소 (varchar(255)) - 도로명주소 우선, 없으면 지번주소
    Double latitude;            // 위도 (double)
    Double longitude;            // 경도 (double)
    Double rating;               // 평균 평점 (double)
    String description;          // 서비스 설명 (TEXT)
    String phone;                // 전화번호 (varchar(255))
    String website;              // 웹사이트 (varchar(255))
    Boolean petFriendly;         // 반려동물 동반 가능 여부 (tinyint(1), default 0)
    
    // 카테고리 계층 구조
    String category1;             // 카테고리1 (대분류, varchar(100))
    String category2;             // 카테고리2 (중분류, varchar(100))
    String category3;             // 카테고리3 (소분류, varchar(100)) - 기본 카테고리로 사용
    
    // 주소 구성 요소 (지역 계층적 탐색에 핵심)
    String sido;                  // 시도 (varchar(50)) - 전국 범위 표시
    String sigungu;              // 시군구 (varchar(50)) - 시도 범위 표시
    String eupmyeondong;         // 읍면동 (varchar(50)) - 시군구 범위 표시
    String roadName;             // 도로명 (varchar(100)) - 읍면동 범위 표시
    String zipCode;              // 우편번호 (varchar(10))
    
    // 운영 정보
    String closedDay;             // 휴무일 (varchar(255))
    String operatingHours;       // 운영시간 (varchar(255)) - "월~금 09:00~18:00" 형식
    Boolean parkingAvailable;    // 주차 가능여부 (tinyint(1), default 0)
    String priceInfo;            // 가격 정보 (varchar(255)) - 입장료, 이용료 등
    
    // 반려동물 상세 정보
    Boolean isPetOnly;           // 반려동물 전용 (tinyint(1))
    String petSize;              // 입장 가능 동물 크기 (varchar(100))
    String petRestrictions;      // 반려동물 제한사항 (varchar(255))
    String petExtraFee;          // 애견 동반 추가 요금 (varchar(255))
    
    // 장소 정보
    Boolean indoor;              // 실내 여부 (tinyint(1))
    Boolean outdoor;             // 실외 여부 (tinyint(1))
    
    // 메타데이터
    LocalDate lastUpdated;       // 최종작성일 (date)
    String dataSource;           // 데이터 출처 (varchar(50), default 'PUBLIC')
                                 // PUBLIC: 공공데이터, KAKAO: 카카오맵
    
    // 연관관계
    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL)
    List<LocationServiceReview> reviews;
}
```

**주소 필드 활용:**
- `sido`: 시도별 서비스 필터링
- `sigungu`: 시군구별 서비스 필터링 (서울: 구, 경기: 시/군)
- `eupmyeondong`: 읍면동별 서비스 필터링
- `roadName`: 도로명별 서비스 필터링
- `address`: 상세 주소 (네이버맵 연동, 길찾기)

### 2. LocationServiceReview (위치 서비스 리뷰)

```java
@Entity
@Table(name = "locationservicereview")
public class LocationServiceReview {
    Long idx;                    // PK
    LocationService service;     // 서비스 (ManyToOne)
    Users user;                  // 작성자 (ManyToOne)
    Integer rating;              // 평점 (1-5)
    String comment;              // 리뷰 내용 (TEXT)
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
```

**연관관계:**
- `ManyToOne` → LocationService
- `ManyToOne` → Users (리뷰 작성자)

## 백엔드 구조

### LocationServiceController

**엔드포인트**: `/api/location-services`

#### GET /search
지역 계층별 서비스 검색

```java
@GetMapping("/search")
public ResponseEntity<Map<String, Object>> searchLocationServices(
    @RequestParam(required = false) String sido,
    @RequestParam(required = false) String sigungu,
    @RequestParam(required = false) String eupmyeondong,
    @RequestParam(required = false) String roadName,
    @RequestParam(required = false) String category,
    @RequestParam(required = false) Integer size
)
```

**파라미터:**
- `sido` (선택): 시도 (예: "서울특별시", "경기도")
- `sigungu` (선택): 시군구 (예: "노원구", "고양시 덕양구")
- `eupmyeondong` (선택): 읍면동 (예: "상계동", "동산동")
- `roadName` (선택): 도로명 (예: "상계로", "동세로")
- `category` (선택): 카테고리 (예: "동물약국", "미술관")
- `size` (선택): 최대 결과 수 (기본값: 500)

**반환:**
```json
{
  "services": [
    {
      "idx": 33310,
      "name": "1004 약국",
      "address": "경기도 고양시 덕양구 동세로 19",
      "latitude": 37.64454276,
      "longitude": 126.886336,
      "category1": "반려동물업",
      "category2": "반려의료",
      "category3": "동물약국",
      "category": "동물약국",
      "sido": "경기도",
      "sigungu": "고양시 덕양구",
      "eupmyeondong": "동산동",
      "roadName": "동세로",
      "zipCode": "10598",
      "phone": "02-381-5052",
      "closedDay": "매주 토, 일, 법정공휴일",
      "operatingHours": "월~금 09:00~18:00",
      "parkingAvailable": true,
      "petFriendly": true,
      "indoor": true,
      "outdoor": false,
      "dataSource": "PUBLIC"
    }
  ],
  "count": 1
}
```

### LocationServiceService

#### 1. 지역 계층별 서비스 조회
```java
public List<LocationServiceDTO> searchLocationServicesByRegion(
    String sido,
    String sigungu,
    String eupmyeondong,
    String roadName,
    String category,
    Integer maxResults
)
```

**지역 계층 우선순위:**
1. `roadName`이 있으면 도로명 기준 조회
2. `eupmyeondong`이 있으면 읍면동 기준 조회
3. `sigungu`가 있으면 시군구 기준 조회
4. `sido`가 있으면 시도 기준 조회
5. 모두 없으면 전체 조회

**카테고리 필터링:**
- `category3` → `category2` → `category1` 순서로 검색
- 대소문자 구분 없이 검색

#### 2. 거리 계산
```java
public Double calculateDistance(Double lat1, Double lng1, Double lat2, Double lng2)
```

**Haversine 공식 사용:**
- 지구 반경: 6371000m
- 미터 단위로 반환

#### 3. 인기 서비스 조회
```java
@Cacheable(value = "popularLocationServices", key = "#category")
public List<LocationServiceDTO> getPopularLocationServices(String category)
```

### LocationServiceRepository

#### 주요 쿼리 메서드

```java
// 시도별 조회
List<LocationService> findBySido(@Param("sido") String sido);

// 시군구별 조회
List<LocationService> findBySigungu(@Param("sigungu") String sigungu);

// 읍면동별 조회
List<LocationService> findByEupmyeondong(@Param("eupmyeondong") String eupmyeondong);

// 도로명별 조회
List<LocationService> findByRoadName(@Param("roadName") String roadName);

// 전체 조회 (평점순)
List<LocationService> findByOrderByRatingDesc();

// 카테고리별 조회
List<LocationService> findByCategoryOrderByRatingDesc(@Param("category") String category);
```

### LocationServiceConverter

#### 엔티티 ↔ DTO 변환
```java
// LocationService → LocationServiceDTO
LocationServiceDTO toDTO(LocationService service)

// LocationServiceDTO → LocationService
LocationService fromDTO(LocationServiceDTO dto)
```

**필드 매핑 전략:**
- **카테고리**: category3 → category2 → category1 순서로 사용
- **운영시간**: `operatingHours` 문자열로 저장 (예: "월~금 09:00~18:00")
- **반려동물 정책**: `petRestrictions` 사용
- **주소**: address 필드 사용 (도로명주소 우선, 없으면 지번주소)
- **지역 계층**: sido, sigungu, eupmyeondong, roadName 모두 매핑
- **데이터 소스**: `dataSource` 필드로 구분 (PUBLIC: 공공데이터, KAKAO: 카카오맵)

## 프론트엔드 구조

### LocationServiceMap 컴포넌트

**위치**: `frontend/src/components/LocationService/LocationServiceMap.js`

#### 주요 기능

1. **지역 선택 UI**
   - 시도 선택: 전국 17개 시도 버튼 그리드
   - 시군구 선택: 선택된 시도의 시군구 목록 (서비스 데이터에서 추출)
   - 읍면동 선택: 선택된 시군구의 읍면동 목록 (서비스 데이터에서 추출)
   - 전국 보기: 선택 초기화

2. **검색 모드**
   - 키워드 검색: 카테고리 선택 또는 직접 입력
   - 지역 선택: 시도 → 시군구 → 읍면동 계층적 선택

3. **서비스 목록 표시**
   - 하단 패널: 현재 지역의 서비스 목록
   - 거리 표시: 내 위치가 있으면 각 서비스까지의 거리 표시
   - 카테고리 필터: 카테고리별 필터링 지원
   - 정렬: 평점순 (서버에서 정렬)

4. **상세 모달**
   - 기본 정보: 이름, 주소, 전화번호, 운영시간 등
   - 반려동물 정보: 동반 가능 여부, 제한사항, 추가 요금
   - 길찾기 버튼: 네이버맵 길찾기 연동
   - 네이버맵 링크: 상세 정보 보기
   - 편의 기능: 전화하기, 주소 복사, 링크 공유

#### 데이터 로딩 전략

**하이브리드 방식:**
1. **초기 로드**: 전국 데이터 가져오기 (최대 5000개)
2. **클라이언트 필터링**: 선택된 지역에 따라 클라이언트에서 필터링
3. **지역 검색**: 특정 지역 선택 시 서버에서 해당 지역 데이터만 가져오기

```javascript
// 초기 로드
const response = await locationServiceApi.searchPlaces({
  size: 5000
});
setAllServices(response.data.services);

// 클라이언트 필터링
const filterServicesByRegion = (allServicesData, sido, sigungu, eupmyeondong, category) => {
  // 시도, 시군구, 읍면동, 카테고리로 필터링
  // 동시에 시군구/읍면동 목록 추출
};
```

#### 지역 목록 추출

서비스 데이터에서 지역 목록을 동적으로 추출:
- **시군구 목록**: 선택된 시도의 서비스 데이터에서 `sigungu` 필드 추출
- **읍면동 목록**: 선택된 시군구의 서비스 데이터에서 `eupmyeondong` 필드 추출

### locationServiceApi

**위치**: `frontend/src/api/locationServiceApi.js`

```javascript
export const locationServiceApi = {
  searchPlaces: ({ 
    sido, 
    sigungu, 
    eupmyeondong, 
    roadName, 
    category, 
    size 
  } = {}) =>
    api.get('/search', {
      params: {
        ...(sido && { sido }),
        ...(sigungu && { sigungu }),
        ...(eupmyeondong && { eupmyeondong }),
        ...(roadName && { roadName }),
        ...(category && { category }),
        ...(typeof size === 'number' && { size }),
      },
    }),
};
```

## 비즈니스 로직

### 지역 계층적 탐색

#### 지역 단위 결정 로직
```java
// 지역 계층 우선순위에 따라 조회
if (StringUtils.hasText(roadName)) {
    services = locationServiceRepository.findByRoadName(roadName);
} else if (StringUtils.hasText(eupmyeondong)) {
    services = locationServiceRepository.findByEupmyeondong(eupmyeondong);
} else if (StringUtils.hasText(sigungu)) {
    services = locationServiceRepository.findBySigungu(sigungu);
} else if (StringUtils.hasText(sido)) {
    services = locationServiceRepository.findBySido(sido);
} else {
    services = locationServiceRepository.findByOrderByRatingDesc();
}
```

### 거리 계산 (Haversine 공식)

```java
public Double calculateDistance(Double lat1, Double lng1, Double lat2, Double lng2) {
    final int R = 6371000; // 지구 반경 (미터)
    
    double dLat = Math.toRadians(lat2 - lat1);
    double dLng = Math.toRadians(lng2 - lng1);
    
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
               Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
               Math.sin(dLng / 2) * Math.sin(dLng / 2);
    
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    
    return R * c; // 미터 단위
}
```

**사용 목적:**
- 내 위치에서 각 서비스까지의 거리 표시
- 거리 순 정렬
- 길찾기 기능에서 경로 거리 계산

### 네이버맵 연동

#### 길찾기 URL 생성
```javascript
// 네이버맵 길찾기 URL 생성
const generateNaverMapRouteUrl = (serviceName) => {
  return `https://map.naver.com/p/search/${encodeURIComponent(serviceName)}`;
};
```

#### 상세 정보 URL 생성
```javascript
// 네이버맵 상세 정보 URL 생성
const generateNaverMapPlaceUrl = (serviceName) => {
  return `https://map.naver.com/v5/search/${encodeURIComponent(serviceName)}`;
};
```

## 성능 최적화

### 1. 인덱싱

```sql
-- 지역 계층별 검색 인덱스
CREATE INDEX idx_sido ON locationservice(sido);
CREATE INDEX idx_sigungu ON locationservice(sigungu);
CREATE INDEX idx_eupmyeondong ON locationservice(eupmyeondong);
CREATE INDEX idx_road_name ON locationservice(road_name);

-- 복합 인덱스 (지역 계층 검색 최적화)
CREATE INDEX idx_region_hierarchy ON locationservice(sido, sigungu, eupmyeondong);
CREATE INDEX idx_sigungu_eupmyeondong ON locationservice(sigungu, eupmyeondong);

-- 위치 기반 검색 (위도/경도)
CREATE INDEX idx_lat_lng ON locationservice(latitude, longitude);

-- 카테고리별 조회
CREATE INDEX idx_category ON locationservice(category1, category2, category3);
CREATE INDEX idx_category3_pet ON locationservice(category3, pet_friendly);

-- 평점 정렬
CREATE INDEX idx_rating ON locationservice(rating DESC);
```

### 2. 캐싱

```java
// 인기 위치 서비스 캐싱
@Cacheable(value = "popularLocationServices", key = "#category")
public List<LocationServiceDTO> getPopularLocationServices(String category) {
    return locationServiceRepository.findTop10ByCategoryOrderByRatingDesc(category)
        .stream()
        .map(locationServiceConverter::toDTO)
        .collect(Collectors.toList());
}
```

### 3. 프론트엔드 최적화

#### 하이브리드 데이터 로딩
- **초기 로드**: 전국 데이터 한 번만 가져오기
- **클라이언트 필터링**: 지역 선택 시 서버 요청 없이 클라이언트에서 필터링
- **지역 검색**: 특정 지역 선택 시에만 서버에서 해당 지역 데이터 가져오기

#### 메모이제이션
- **거리 계산**: useMemo로 최적화
- **서비스 필터링**: useCallback으로 최적화

## 사용자 시나리오

### 1. 초기 진입
1. 전국 데이터 로드 (최대 5000개)
2. 시도 선택 UI 표시
3. 사용자 위치 확인 (GPS 또는 수동 입력)
4. 사용자 위치는 거리 계산용으로만 사용

### 2. 지역 선택
1. **시도 선택**: 시도 버튼 클릭 → 해당 시도의 시군구 목록 표시
2. **시군구 선택**: 시군구 버튼 클릭 → 해당 시군구의 읍면동 목록 표시
3. **읍면동 선택**: 읍면동 버튼 클릭 → 해당 읍면동의 서비스 목록 표시
4. **전국 보기**: 선택 초기화 및 전국 뷰로 복귀

### 3. 서비스 상세 보기
1. 서비스 목록에서 서비스 클릭
2. 상세 모달 표시
3. 길찾기 버튼 클릭 → 네이버맵 길찾기 연동
4. 네이버맵 링크 클릭 → 네이버맵에서 상세 정보 확인

### 4. 길찾기
1. 서비스 상세 모달에서 "길찾기" 버튼 클릭
2. 네이버맵 길찾기 페이지로 이동
3. 출발지: 사용자 위치, 도착지: 서비스 위치
4. 경로 정보 표시 (예상 시간, 거리)

## 주요 특징

### 1. 지역 계층적 탐색
- 시도 → 시군구 → 읍면동 계층적 선택
- 직관적인 탐색 경험
- 지역별 서비스 그룹화로 정보 구조화

### 2. 내 위치 활용
- 거리 정보 표시 (Haversine 공식)
- 길찾기 기능 제공 (네이버맵 연동)

### 3. 네이버맵 연동
- 길찾기 기능 연동
- 상세 정보 확인
- 건물명 등 상세 정보는 네이버맵에서 확인

### 4. 상세한 정보 제공
- 3단계 카테고리 분류
- 상세 주소 구성 요소 (시도, 시군구, 읍면동, 도로명)
- 운영 정보 (휴무일, 운영시간, 주차 가능 여부)
- 반려동물 정책 (동반 가능, 전용, 크기 제한, 추가 요금)
- 장소 특성 (실내/실외)

### 5. 성능 최적화
- 지역 계층별 인덱스 활용
- 하이브리드 데이터 로딩 (초기 로드 + 클라이언트 필터링)
- 캐싱 지원 (인기 서비스)

### 6. 사용자 경험
- 직관적인 지역 선택 UI
- 키워드 검색 및 지역 선택 모드 지원
- 실시간 거리 계산
- 반응형 디자인

## 참고사항

### 현재 미사용 기능
- **지도 표시**: MapContainer 컴포넌트는 import되지만 실제로 사용하지 않음
- **GeoJSON 폴리곤**: GeoJSON 관련 함수들은 import되지만 실제로 사용하지 않음
- **지도 이벤트**: 지도 관련 이벤트 핸들러는 미사용

### 향후 개선 가능 사항
- 지도 통합 (네이버맵 또는 카카오맵)
- GeoJSON 폴리곤 표시
- 지도 기반 서비스 마커 표시
- 지도 확대/축소에 따른 자동 필터링
