# 행정구역 GeoJSON 데이터

이 폴더에 시도별 GeoJSON 파일을 추가하세요.

## 파일명 형식
- 서울특별시: `11.json`
- 부산광역시: `26.json`
- 대구광역시: `27.json`
- 인천광역시: `28.json`
- 광주광역시: `29.json`
- 대전광역시: `30.json`
- 울산광역시: `31.json`
- 세종특별자치시: `36.json`
- 경기도: `41.json`
- 강원특별자치도: `42.json`
- 충청북도: `43.json`
- 충청남도: `44.json`
- 전북특별자치도: `45.json`
- 전라남도: `46.json`
- 경상북도: `47.json`
- 경상남도: `48.json`
- 제주특별자치도: `50.json`

## GeoJSON 파일 다운로드 방법

### 방법 1: 네이버맵 예제 사용 (권장 - 가장 간단)
- **현재 설정**: 네이버맵 예제의 GeoJSON을 CDN에서 자동 로드
- 파일 추가 불필요, 바로 사용 가능
- URL: `https://navermaps.github.io/maps.js.ncp/data/region/11.json`

### 방법 2: 공공데이터포털 SHP → GeoJSON 변환
공공데이터포털에서 제공하는 SHP 파일을 GeoJSON으로 변환:

1. **데이터 다운로드**
   - https://www.data.go.kr 에서 "행정구역 경계" 검색
   - "LARD_ADM_SECT_SGG_시도명" 형식의 SHP 파일 다운로드
   - 좌표계: GRS80(EPSG:5186)

2. **SHP → GeoJSON 변환**
   - **온라인 도구**: https://mapshaper.org/
     - SHP 파일 업로드 → Export → GeoJSON 선택
   - **Python (GDAL)**: 
     ```bash
     ogr2ogr -f GeoJSON output.json input.shp -t_srs EPSG:4326
     ```
   - **Node.js (shapefile)**: 
     ```bash
     npm install -g shapefile
     shp2json input.shp > output.json
     ```

3. **좌표계 변환**
   - 공공데이터는 GRS80(EPSG:5186) 사용
   - 웹 지도는 WGS84(EPSG:4326) 필요
   - 변환 시 좌표계를 4326으로 변환해야 함

### 방법 3: GitHub에서 이미 변환된 GeoJSON 사용
- https://github.com/vuski/admdongkor (한국 행정구역 GeoJSON)
- https://github.com/southkorea/southkorea-maps (한국 지도 GeoJSON)

## 현재 설정
현재는 **네이버맵 예제의 GeoJSON을 CDN에서 사용**하도록 설정되어 있습니다.
- 파일 추가 불필요
- 바로 작동

로컬 파일을 사용하려면:
1. 이 폴더에 GeoJSON 파일 추가
2. `MapContainer.js`에서 `USE_NAVER_EXAMPLE = false`로 변경

