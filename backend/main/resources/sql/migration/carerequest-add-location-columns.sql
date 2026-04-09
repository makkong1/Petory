-- Phase 2: 펫케어 요청 좌표 컬럼 추가
-- 목적: UnifiedPetMap 지도 표출을 위한 위치 정보 저장
-- 테이블: carerequest (JPA @Table(name = "carerequest") 기준)
ALTER TABLE carerequest
  ADD COLUMN latitude  DOUBLE       NULL COMMENT '위도 (케어 요청 장소)',
  ADD COLUMN longitude DOUBLE       NULL COMMENT '경도 (케어 요청 장소)',
  ADD COLUMN address   VARCHAR(255) NULL COMMENT '주소 (클라이언트 geocoding 후 저장)';

-- 기존 데이터는 좌표 NULL 상태로 유지 (백필 별도 진행)
-- 지도 조회 쿼리에서 latitude IS NOT NULL 조건으로 NULL 데이터 자동 제외됨
