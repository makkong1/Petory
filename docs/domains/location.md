# Location 도메인

## 개요

위치 기반 서비스 (병원, 카페, 공원, 펫샵 등) 정보 제공 및 리뷰 관리 도메인입니다.
DB에 저장된 공공데이터를 기반으로 **지역 계층적 탐색** 방식의 지도 서비스를 제공합니다.

## 핵심 개념

### 지역 계층적 탐색
지도를 확대/축소할수록 더 상세한 지역 단위로 서비스를 표시합니다:
- **전국 범위 (축소)**: 시도 단위 표시
- **시도 범위**: 시군구 단위 표시 (예: 서울특별시 → 강남구, 노원구 등)
- **시군구 범위**: 읍면동 단위 표시 (예: 노원구 → 상계동, 하계동 등)
- **읍면동 범위**: 도로명 단위 표시 (예: 상계동 → 상계로, 노원로 등)
- **도로명 범위**: 건물명/상세 주소 표시 (카카오맵 연동)

### 내 위치 활용
- **길찾기**: 선택한 서비스까지의 경로 안내
- **거리 계산**: 내 위치에서 각 서비스까지의 거리 표시
- **초기 필터링**: 내 위치의 구/동 기준으로 초기 서비스 목록 표시

### UI 구성
- **지도 영역**: 지역 계층에 따라 서비스 표시
- **서비스 목록**: 하단 또는 오른쪽 사이드바에 목록 표시
- **상세 모달**: 서비스 클릭 시 상세 정보, 길찾기, 카카오맵 링크 제공

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
                                 // PUBLIC: 공공데이터
    
    // 연관관계
    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL)
    List<LocationServiceReview> reviews;
}
```

**주소 필드 활용:**
- `sido`: 전국 범위에서 시도별 서비스 그룹화
- `sigungu`: 시도 범위에서 시군구별 서비스 그룹화 (서울: 구, 경기: 시/군)
- `eupmyeondong`: 시군구 범위에서 읍면동별 서비스 그룹화
- `roadName`: 읍면동 범위에서 도로명별 서비스 그룹화
- `address`: 상세 주소 (카카오맵 연동, 길찾기)

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

## Service 주요 기능

### LocationServiceService

#### 1. 지역 계층별 서비스 조회
```java
// 지역 계층에 따라 서비스 조회
List<LocationServiceDTO> searchLocationServicesByRegion(
    String sido,                 // 시도 (선택)
    String sigungu,             // 시군구 (선택)
    String eupmyeondong,         // 읍면동 (선택)
    String roadName,             // 도로명 (선택)
    String category,             // 카테고리 (선택)
    Integer maxResults           // 최대 결과 수
)
```

**지역 계층 우선순위:**
1. `roadName`이 있으면 도로명 기준 조회
2. `eupmyeondong`이 있으면 읍면동 기준 조회
3. `sigungu`가 있으면 시군구 기준 조회
4. `sido`가 있으면 시도 기준 조회
5. 모두 없으면 전체 조회

#### 2. 내 위치 기반 서비스 조회 (길찾기/거리 계산용)
```java
// 내 위치의 구/동 기준으로 서비스 조회
List<LocationServiceDTO> searchServicesByUserLocation(
    Double userLatitude,         // 사용자 위도
    Double userLongitude,        // 사용자 경도
    String category,             // 카테고리 (선택)
    Integer maxResults           // 최대 결과 수
)
```

**주요 기능:**
- 사용자 위치의 `sigungu`, `eupmyeondong` 추출
- 해당 지역의 서비스 조회
- 각 서비스까지의 거리 계산 (Haversine 공식)
- 거리 순 정렬

#### 3. 거리 계산
```java
// 두 좌표 간 거리 계산 (미터 단위)
Double calculateDistance(
    Double lat1,
    Double lng1,
    Double lat2,
    Double lng2
)
```

**사용 목적:**
- 내 위치에서 각 서비스까지의 거리 표시
- 길찾기 기능에서 경로 거리 계산

#### 4. 카테고리별 인기 서비스 조회
```java
// 카테고리별 상위 10개 평점순 서비스 조회
List<LocationServiceDTO> getPopularLocationServices(String category)
```

### LocationServiceConverter

#### 1. 엔티티 ↔ DTO 변환
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
- **데이터 소스**: `dataSource` 필드로 구분 (PUBLIC: 공공데이터)

## Repository 주요 쿼리

### LocationServiceRepository

#### 1. 지역 계층별 검색
```java
// 시도별 조회
@Query("SELECT ls FROM LocationService ls WHERE " +
       "ls.sido = :sido " +
       "ORDER BY ls.rating DESC")
List<LocationService> findBySido(@Param("sido") String sido);

// 시군구별 조회
@Query("SELECT ls FROM LocationService ls WHERE " +
       "ls.sigungu = :sigungu " +
       "ORDER BY ls.rating DESC")
List<LocationService> findBySigungu(@Param("sigungu") String sigungu);

// 읍면동별 조회
@Query("SELECT ls FROM LocationService ls WHERE " +
       "ls.eupmyeondong = :eupmyeondong " +
       "ORDER BY ls.rating DESC")
List<LocationService> findByEupmyeondong(@Param("eupmyeondong") String eupmyeondong);

// 도로명별 조회
@Query("SELECT ls FROM LocationService ls WHERE " +
       "ls.roadName = :roadName " +
       "ORDER BY ls.rating DESC")
List<LocationService> findByRoadName(@Param("roadName") String roadName);
```

#### 2. 내 위치 기반 검색
```java
// 사용자 위치의 시군구/읍면동 기준 조회
@Query("SELECT ls FROM LocationService ls WHERE " +
       "(:sigungu IS NULL OR ls.sigungu = :sigungu) AND " +
       "(:eupmyeondong IS NULL OR ls.eupmyeondong = :eupmyeondong) " +
       "ORDER BY ls.rating DESC")
List<LocationService> findByUserLocation(
    @Param("sigungu") String sigungu,
    @Param("eupmyeondong") String eupmyeondong
);
```

#### 3. 거리 계산용 반경 검색
```java
// MySQL Spatial 함수를 사용한 반경 검색 (길찾기용)
@Query(value = 
    "SELECT * FROM locationservice WHERE " +
    "latitude IS NOT NULL AND longitude IS NOT NULL AND " +
    "ST_Distance_Sphere(POINT(longitude, latitude), POINT(?2, ?1)) <= ?3 " +
    "ORDER BY ST_Distance_Sphere(POINT(longitude, latitude), POINT(?2, ?1)) ASC", 
    nativeQuery = true)
List<LocationService> findByRadiusOrderByDistance(
    @Param("latitude") Double latitude,
    @Param("longitude") Double longitude,
    @Param("radiusInMeters") Double radiusInMeters
);
```

#### 4. 카테고리별 검색
```java
// category3, category2, category1 순서로 검색
@Query("SELECT ls FROM LocationService ls WHERE " +
       "(:category IS NULL OR ls.category3 = :category OR " +
       "ls.category2 = :category OR ls.category1 = :category) " +
       "ORDER BY ls.rating DESC")
List<LocationService> findByCategoryOrderByRatingDesc(@Param("category") String category);
```

## API 엔드포인트

### 위치 서비스 (/api/location-services)

#### GET /search
지역 계층별 또는 내 위치 기반 서비스 검색

**파라미터:**
- `sido` (선택): 시도 (예: "서울특별시", "경기도")
- `sigungu` (선택): 시군구 (예: "노원구", "고양시 덕양구")
- `eupmyeondong` (선택): 읍면동 (예: "상계동", "동산동")
- `roadName` (선택): 도로명 (예: "상계로", "동세로")
- `userLatitude` (선택): 사용자 위도 (내 위치 기반 검색용)
- `userLongitude` (선택): 사용자 경도 (내 위치 기반 검색용)
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
      "distance": 1250.5,
      "dataSource": "PUBLIC"
    }
  ],
  "count": 1
}
```

**기능:**
- 지역 계층에 따라 서비스 조회 (시도 → 시군구 → 읍면동 → 도로명)
- 내 위치 기반 검색 시 거리 계산 포함
- 카테고리 필터링 지원
- 평점 순 정렬

#### GET /detail/{idx}
서비스 상세 정보 조회 (상세 모달용)

**반환:**
```json
{
  "idx": 33310,
  "name": "1004 약국",
  "address": "경기도 고양시 덕양구 동세로 19",
  "latitude": 37.64454276,
  "longitude": 126.886336,
  "category1": "반려동물업",
  "category2": "반려의료",
  "category3": "동물약국",
  "sido": "경기도",
  "sigungu": "고양시 덕양구",
  "eupmyeondong": "동산동",
  "roadName": "동세로",
  "zipCode": "10598",
  "phone": "02-381-5052",
  "website": null,
  "closedDay": "매주 토, 일, 법정공휴일",
  "operatingHours": "월~금 09:00~18:00",
  "parkingAvailable": true,
  "priceInfo": "변동",
  "petFriendly": true,
  "isPetOnly": false,
  "petSize": "모두 가능",
  "petRestrictions": "제한사항 없음",
  "petExtraFee": "없음",
  "indoor": true,
  "outdoor": false,
  "rating": 4.5,
  "description": "동물약국",
  "kakaoMapUrl": "https://place.map.kakao.com/...",
  "reviews": []
}
```

**기능:**
- 서비스 상세 정보 제공
- 카카오맵 URL 포함 (길찾기 연동용)
- 리뷰 목록 포함

## 프론트엔드 구조

### 지도 컴포넌트 (LocationServiceMap)

#### 1. 지역 계층별 표시 로직
```javascript
// 지도 레벨에 따라 표시할 지역 단위 결정
const getRegionLevel = (mapLevel) => {
  if (mapLevel >= 7) return 'sido';        // 전국 범위: 시도
  if (mapLevel >= 5) return 'sigungu';   // 시도 범위: 시군구
  if (mapLevel >= 3) return 'eupmyeondong'; // 시군구 범위: 읍면동
  return 'roadName';                      // 읍면동 범위: 도로명
};

// 지도 확대/축소 시 해당 지역 단위의 서비스 조회
const fetchServicesByRegion = async (regionLevel, regionValue) => {
  const params = {
    [regionLevel]: regionValue,
    size: getSizeByLevel(mapLevel)
  };
  const response = await locationServiceApi.searchPlaces(params);
  return response.data.services;
};
```

#### 2. 내 위치 기반 초기 로드
```javascript
// 사용자 위치의 구/동 추출 후 서비스 조회
const loadServicesByUserLocation = async (userLat, userLng) => {
  // 역지오코딩으로 구/동 추출 (카카오맵 API 또는 백엔드)
  const region = await geocodingApi.reverseGeocode(userLat, userLng);
  
  const response = await locationServiceApi.searchPlaces({
    sigungu: region.sigungu,
    eupmyeondong: region.eupmyeondong,
    userLatitude: userLat,
    userLongitude: userLng,
    size: 100
  });
  
  return response.data.services; // 거리 정보 포함
};
```

#### 3. 서비스 목록 표시
- **하단 또는 오른쪽 사이드바**: 현재 지역의 서비스 목록
- **거리 표시**: 내 위치가 있으면 각 서비스까지의 거리 표시
- **카테고리 필터**: 카테고리별 필터링 지원
- **정렬**: 거리순, 평점순, 이름순

#### 4. 상세 모달
- **기본 정보**: 이름, 주소, 전화번호, 운영시간 등
- **반려동물 정보**: 동반 가능 여부, 제한사항, 추가 요금
- **길찾기 버튼**: 카카오맵 길찾기 연동
- **카카오맵 링크**: 상세 정보 보기
- **리뷰 목록**: 해당 서비스의 리뷰 표시

## 비즈니스 로직

### 지역 계층적 탐색

#### 지역 단위 결정 로직
```java
// 지도 레벨에 따라 조회할 지역 단위 결정
public String determineRegionLevel(Integer mapLevel) {
    if (mapLevel >= 7) return "sido";        // 전국: 시도
    if (mapLevel >= 5) return "sigungu";    // 시도: 시군구
    if (mapLevel >= 3) return "eupmyeondong"; // 시군구: 읍면동
    return "roadName";                       // 읍면동: 도로명
}
```

#### 지역별 서비스 그룹화
```java
// 지역 단위별로 서비스 그룹화
public Map<String, List<LocationServiceDTO>> groupServicesByRegion(
    List<LocationServiceDTO> services,
    String regionLevel
) {
    return services.stream()
        .collect(Collectors.groupingBy(service -> {
            switch (regionLevel) {
                case "sido": return service.getSido();
                case "sigungu": return service.getSigungu();
                case "eupmyeondong": return service.getEupmyeondong();
                case "roadName": return service.getRoadName();
                default: return "전체";
            }
        }));
}
```

### 거리 계산 (Haversine 공식)

```java
// 두 좌표 간 거리 계산 (미터 단위)
public Double calculateDistance(
    Double lat1, Double lng1,
    Double lat2, Double lng2
) {
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

### 카카오맵 연동

#### 길찾기 URL 생성
```java
// 카카오맵 길찾기 URL 생성
public String generateKakaoMapRouteUrl(
    Double startLat, Double startLng,  // 출발지 (사용자 위치)
    Double endLat, Double endLng      // 도착지 (서비스 위치)
) {
    return String.format(
        "https://map.kakao.com/link/route/%f,%f/%f,%f",
        startLng, startLat,
        endLng, endLat
    );
}
```

#### 상세 정보 URL 생성
```java
// 카카오맵 상세 정보 URL 생성
public String generateKakaoMapPlaceUrl(
    String name,
    Double latitude,
    Double longitude
) {
    // 카카오맵 검색 API로 place_id 조회 후 URL 생성
    // 또는 좌표 기반 URL 생성
    return String.format(
        "https://map.kakao.com/link/map/%s,%f,%f",
        URLEncoder.encode(name, StandardCharsets.UTF_8),
        latitude,
        longitude
    );
}
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
// 지역별 서비스 캐싱
@Cacheable(value = "locationServicesByRegion", key = "#sido + '_' + #sigungu + '_' + #eupmyeondong")
public List<LocationServiceDTO> searchLocationServicesByRegion(
    String sido, String sigungu, String eupmyeondong
) {
    // ...
}

// 인기 위치 서비스 캐싱
@Cacheable(value = "popularLocationServices", key = "#category")
public List<LocationServiceDTO> getPopularLocationServices(String category) {
    return locationServiceRepository.findTop10ByCategoryOrderByRatingDesc(category);
}
```

## 사용자 시나리오

### 1. 초기 진입
1. 사용자 위치 확인 (GPS 또는 수동 입력)
2. 사용자 위치의 구/동 추출
3. 해당 지역의 서비스 목록 표시 (거리 정보 포함)
4. 지도에 해당 지역 표시

### 2. 지도 확대/축소
1. **전국 범위 (축소)**: 시도별 서비스 그룹 표시
2. **시도 범위**: 시군구별 서비스 그룹 표시
3. **시군구 범위**: 읍면동별 서비스 그룹 표시
4. **읍면동 범위**: 도로명별 서비스 그룹 표시
5. **도로명 범위**: 개별 서비스 표시

### 3. 서비스 상세 보기
1. 서비스 목록에서 서비스 클릭
2. 상세 모달 표시
3. 길찾기 버튼 클릭 → 카카오맵 길찾기 연동
4. 카카오맵 링크 클릭 → 카카오맵에서 상세 정보 확인

### 4. 길찾기
1. 서비스 상세 모달에서 "길찾기" 버튼 클릭
2. 카카오맵 길찾기 페이지로 이동
3. 출발지: 사용자 위치, 도착지: 서비스 위치

## 주요 특징

### 1. 지역 계층적 탐색
- 지도 확대/축소에 따라 자동으로 지역 단위 변경
- 직관적인 탐색 경험
- 지역별 서비스 그룹화로 정보 구조화

### 2. 내 위치 활용
- 길찾기 기능 제공
- 거리 정보 표시
- 초기 필터링 기준

### 3. 카카오맵 연동
- 길찾기 기능 연동
- 상세 정보 확인
- 건물명 등 상세 정보는 카카오맵에서 확인

### 4. 상세한 정보 제공
- 3단계 카테고리 분류
- 상세 주소 구성 요소 (시도, 시군구, 읍면동, 도로명)
- 운영 정보 (휴무일, 운영시간, 주차 가능 여부)
- 반려동물 정책 (동반 가능, 전용, 크기 제한, 추가 요금)
- 장소 특성 (실내/실외)

### 5. 성능 최적화
- 지역 계층별 인덱스 활용
- MySQL Spatial 함수 활용 (거리 계산)
- 캐싱 지원 (지역별, 인기 서비스)
