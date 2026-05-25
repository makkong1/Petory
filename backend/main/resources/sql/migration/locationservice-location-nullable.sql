-- location 컬럼을 NULL 허용으로 변경
-- FacilitySyncService가 lat/lng만 있는 상태로 INSERT한 뒤
-- LocationServiceBatchWriter가 native UPDATE로 POINT를 채우는 구조.
-- 기존 NOT NULL 제약이 INSERT를 막아 PET_DATA_API 동기화가 전혀 동작하지 않던 문제 해결.
ALTER TABLE locationservice MODIFY COLUMN location POINT NULL;
