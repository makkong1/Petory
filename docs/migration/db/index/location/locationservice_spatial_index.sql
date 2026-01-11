-- ============================================
-- LocationService 공간 인덱스(Spatial Index) 생성 스크립트
-- 작성일: 2024
-- 목적: 위도/경도 범위 검색 성능 최적화
-- ============================================

USE petory;

-- ============================================
-- 1. POINT 타입 컬럼 추가
-- ============================================
-- 기존 latitude, longitude를 POINT 타입으로 변환하여 공간 인덱스 활용
-- SRID 4326: WGS84 좌표계 (일반적으로 사용하는 GPS 좌표계)

-- 1-1. location 컬럼 추가 (POINT 타입)
ALTER TABLE locationservice 
ADD COLUMN location POINT SRID 4326 NULL 
COMMENT '위치 정보 (latitude, longitude를 POINT로 변환)'
AFTER longitude;

-- ============================================
-- 2. 기존 데이터 변환
-- ============================================
-- latitude, longitude가 있는 경우 POINT로 변환

-- 2-1. 기존 데이터를 POINT로 변환 (SRID 4326 명시)
-- ⚠️ 주의: POINT() 함수는 SRID를 지원하지 않으므로 ST_GeomFromText 사용
-- ⚠️ 주의: MySQL의 ST_GeomFromText는 SRID 4326에서 (latitude, longitude) 순서를 기대함
UPDATE locationservice 
SET location = ST_GeomFromText(
    CONCAT('POINT(', latitude, ' ', longitude, ')'),
    4326
)
WHERE latitude IS NOT NULL 
  AND longitude IS NOT NULL
  AND location IS NULL;

-- ============================================
-- 3. location 컬럼을 NOT NULL로 변경
-- ============================================
-- ⚠️ 주의: SPATIAL INDEX는 NOT NULL 컬럼에만 생성 가능
-- ⚠️ 주의: location이 NULL인 행이 있으면 NOT NULL 변경이 실패합니다
-- latitude 또는 longitude가 NULL인 행은 location이 NULL로 유지되므로,
-- 해당 행들이 있다면 먼저 처리하거나 삭제해야 합니다

-- 3-1. location이 NULL인 행 확인 (선택사항)
-- SELECT COUNT(*) as null_location_count
-- FROM locationservice 
-- WHERE location IS NULL;

-- 3-2. location 컬럼을 NOT NULL로 변경
-- ⚠️ location이 NULL인 행이 있으면 이 명령이 실패합니다
ALTER TABLE locationservice 
MODIFY COLUMN location POINT SRID 4326 NOT NULL 
COMMENT '위치 정보 (latitude, longitude를 POINT로 변환)';

-- ============================================
-- 4. 공간 인덱스 생성
-- ============================================
-- SPATIAL INDEX는 POINT, GEOMETRY 등의 공간 데이터 타입에만 생성 가능
-- ⚠️ 주의: NOT NULL 컬럼에만 생성 가능

-- 4-1. location 컬럼에 공간 인덱스 생성
CREATE SPATIAL INDEX idx_locationservice_location_spatial 
ON locationservice(location);

-- ============================================
-- 5. 트리거 생성 (선택사항)
-- ============================================
-- 새로운 데이터 삽입/업데이트 시 자동으로 location 컬럼 업데이트

-- 5-1. INSERT 트리거 (SRID 4326 명시)
DELIMITER //
CREATE TRIGGER trg_locationservice_location_insert
BEFORE INSERT ON locationservice
FOR EACH ROW
BEGIN
    IF NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL THEN
        SET NEW.location = ST_GeomFromText(
            CONCAT('POINT(', NEW.latitude, ' ', NEW.longitude, ')'),
            4326
        );
    END IF;
END//
DELIMITER ;

-- 5-2. UPDATE 트리거 (SRID 4326 명시)
DELIMITER //
CREATE TRIGGER trg_locationservice_location_update
BEFORE UPDATE ON locationservice
FOR EACH ROW
BEGIN
    IF NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL THEN
        SET NEW.location = ST_GeomFromText(
            CONCAT('POINT(', NEW.latitude, ' ', NEW.longitude, ')'),
            4326
        );
    END IF;
END//
DELIMITER ;

-- ============================================
-- 6. 인덱스 생성 확인
-- ============================================

-- 6-1. 공간 인덱스 확인
SHOW INDEX FROM locationservice 
WHERE Key_name = 'idx_locationservice_location_spatial';

-- 6-2. location 컬럼 정보 확인
SHOW COLUMNS FROM locationservice LIKE 'location';

-- 6-3. 테이블 통계 업데이트 (인덱스 선택 개선을 위해)
-- ⚠️ 참고: 통계가 오래되었으면 옵티마이저가 잘못된 인덱스를 선택할 수 있음
ANALYZE TABLE locationservice;

-- ============================================
-- 7. 사용 예시 쿼리 (프로젝트 실제 쿼리 기반)
-- ============================================

-- 7-1. findByLocationRange 개선 버전 (기존: latitude/longitude BETWEEN)
-- 기존: SELECT * FROM locationservice WHERE latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ? AND is_deleted = 0 ORDER BY rating DESC
-- 개선: ST_Within 사용하여 공간 인덱스 활용
-- 사용 예시: 위도 37.5~37.7, 경도 126.9~127.2
SELECT * FROM locationservice 
WHERE ST_Within(
    location,
    ST_GeomFromText('POLYGON((37.5 126.9, 37.5 127.2, 37.7 127.2, 37.7 126.9, 37.5 126.9))', 4326)
)
AND is_deleted = 0
ORDER BY rating DESC;

-- 7-2. findByRadius 개선 버전 (기존: ST_Distance_Sphere만 사용)
-- 기존: SELECT * FROM locationservice WHERE latitude IS NOT NULL AND longitude IS NOT NULL AND ST_Distance_Sphere(POINT(longitude, latitude), POINT(?2, ?1)) <= ?3 AND is_deleted = 0 ORDER BY rating DESC
-- ⚠️ 주의: ST_Distance_Sphere는 인덱스를 사용하지 않으므로, 먼저 공간 범위로 필터링 후 거리 계산
-- 개선안: MBR 기반 1차 필터링 + ST_Distance_Sphere 2차 필터링
-- 사용 예시: 위도 37.5, 경도 127.0 기준 반경 5000미터 (5km)
SELECT * FROM locationservice 
WHERE ST_Within(
    location,
    ST_GeomFromText('POLYGON((37.45 126.95, 37.45 127.05, 37.55 127.05, 37.55 126.95, 37.45 126.95))', 4326)
)
AND ST_Distance_Sphere(
    location,
    ST_GeomFromText('POINT(37.5 127.0)', 4326)
) <= 5000
AND is_deleted = 0
ORDER BY rating DESC;

-- 7-3. findByRadiusOrderByDistance 개선 버전 (거리순 정렬)
-- 기존: SELECT * FROM locationservice WHERE latitude IS NOT NULL AND longitude IS NOT NULL AND ST_Distance_Sphere(POINT(longitude, latitude), POINT(?2, ?1)) <= ?3 AND is_deleted = 0 ORDER BY ST_Distance_Sphere(POINT(longitude, latitude), POINT(?2, ?1)) ASC
-- 개선안: MBR 기반 1차 필터링 + ST_Distance_Sphere 2차 필터링 및 정렬
SELECT * FROM locationservice 
WHERE ST_Within(
    location,
    ST_GeomFromText('POLYGON((37.45 126.95, 37.45 127.05, 37.55 127.05, 37.55 126.95, 37.45 126.95))', 4326)
)
AND ST_Distance_Sphere(
    location,
    ST_GeomFromText('POINT(37.5 127.0)', 4326)
) <= 5000
AND is_deleted = 0
ORDER BY ST_Distance_Sphere(location, ST_GeomFromText('POINT(37.5 127.0)', 4326)) ASC;

-- ============================================
-- 8. 성능 비교 (프로젝트 실제 쿼리 기반)
-- ============================================

-- 8-1. 기존 방식: findByLocationRange (BETWEEN 사용)
-- Repository: findByLocationRange(minLat, maxLat, minLng, maxLng)
EXPLAIN 
SELECT * FROM locationservice 
WHERE latitude BETWEEN 37.5 AND 37.7 
  AND longitude BETWEEN 126.9 AND 127.2 
  AND is_deleted = 0 
ORDER BY rating DESC;

-- 8-2. 개선 방식: 공간 인덱스 사용 (ST_Within)
-- Repository 쿼리를 ST_Within으로 대체 가능
EXPLAIN 
SELECT * FROM locationservice 
WHERE ST_Within(
    location,
    ST_GeomFromText('POLYGON((37.5 126.9, 37.5 127.2, 37.7 127.2, 37.7 126.9, 37.5 126.9))', 4326)
)
AND is_deleted = 0
ORDER BY rating DESC;

-- 8-3. 기존 방식: findByRadius (ST_Distance_Sphere만 사용)
-- Repository: findByRadius(latitude, longitude, radiusInMeters)
-- ⚠️ 문제: ST_Distance_Sphere는 인덱스를 사용하지 않아 전체 스캔 발생
EXPLAIN 
SELECT * FROM locationservice 
WHERE latitude IS NOT NULL AND longitude IS NOT NULL AND 
  ST_Distance_Sphere(POINT(longitude, latitude), POINT(127.0, 37.5)) <= 5000 AND 
  is_deleted = 0 
ORDER BY rating DESC;

-- 8-4. 개선 방식: 공간 인덱스 1차 필터링 + ST_Distance_Sphere 2차 필터링
-- 먼저 MBR 범위로 필터링 (공간 인덱스 사용) 후 정확한 거리 계산
EXPLAIN 
SELECT * FROM locationservice 
WHERE ST_Within(
    location,
    ST_GeomFromText('POLYGON((37.45 126.95, 37.45 127.05, 37.55 127.05, 37.55 126.95, 37.45 126.95))', 4326)
)
AND ST_Distance_Sphere(location, ST_GeomFromText('POINT(37.5 127.0)', 4326)) <= 5000
AND is_deleted = 0
ORDER BY rating DESC;

-- EXPLAIN 결과 분석:
-- ⚠️ 참고: MySQL 옵티마이저가 idx_locationservice_deleted_rating을 선택할 수 있음
-- 이유:
-- 1. is_deleted = 0 필터가 매우 선택적일 경우 (많은 행이 삭제되지 않은 경우)
-- 2. ORDER BY rating DESC가 idx_locationservice_deleted_rating과 잘 맞는 경우
-- 3. MySQL 옵티마이저가 공간 인덱스보다 일반 인덱스를 선호하는 경우
-- 
-- 해결 방법:
-- 1. ANALYZE TABLE locationservice; 실행하여 테이블 통계 업데이트
-- 2. 공간 범위를 더 작게 하면 공간 인덱스 선택 확률 증가
-- 3. Repository에서 쿼리 수정 시 공간 인덱스 활용 고려
-- 
-- 실제 성능은 데이터 분포에 따라 다르므로, 여러 방식 모두 테스트 권장


-- ============================================
-- 9. 주의사항
-- ============================================
-- 1. POINT 타입은 (latitude, longitude) 순서로 저장 (ST_GeomFromText 사용 시)
--    ⚠️ 주의: MySQL의 POINT() 함수는 (longitude, latitude) 순서를 사용하지만,
--            ST_GeomFromText의 WKT 포맷은 (latitude, longitude) 순서를 사용
-- 2. SRID 4326은 WGS84 좌표계 (일반적인 GPS 좌표계)
-- 3. 공간 인덱스는 NOT NULL 컬럼에만 생성 가능 (중요!)
-- 4. 공간 인덱스는 MySQL 5.7.5+에서 InnoDB 지원
-- 5. 공간 인덱스는 R-Tree 구조로 저장되어 범위 검색에 최적화
-- 6. ST_Contains, ST_Within 등 공간 함수 사용 시 인덱스 활용 가능
-- 7. ST_Distance_Sphere는 함수이므로 인덱스 활용 어려움 (1차 필터링 후 사용 권장)
-- 8. 공간 인덱스가 사용되지 않는 경우:
--    - 서브쿼리로 공간 조건을 먼저 필터링하거나
--    - MBRWithin 같은 MBR 기반 함수 사용을 고려
--    - ANALYZE TABLE로 테이블 통계 업데이트
--    - 공간 범위를 더 작게 하면 공간 인덱스 선택 확률 증가

-- ============================================
-- 10. 롤백 스크립트 (필요 시)
-- ============================================

-- 10-1. 트리거 삭제
-- DROP TRIGGER IF EXISTS trg_locationservice_location_insert;
-- DROP TRIGGER IF EXISTS trg_locationservice_location_update;

-- 10-2. 공간 인덱스 삭제
-- DROP INDEX idx_locationservice_location_spatial ON locationservice;

-- 10-3. location 컬럼 삭제
-- ALTER TABLE locationservice DROP COLUMN location;
