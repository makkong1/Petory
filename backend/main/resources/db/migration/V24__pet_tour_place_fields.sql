-- TourAPI 반려동물 동반여행 적재에 필요한 두 칸.
--
-- 1) locationservice.pet_zone_type
--    TourAPI `detailPetTour2` 의 `acmpyTypeCd` — 「전구역 동반가능」/「일부구역 동반가능」.
--    기존 데이터에 없는 구분이고, 실측(2026-10-23)에서 상세 필드 중 유일하게 채워져 있었다
--    (표본 25건 중 24건. 같은 응답의 etcAcmpyInfo 는 15건 중 1건뿐이라 믿을 수 없다).
--    「동반가능 여부」(pet_friendly) 와 다른 축이다 — 들어갈 수는 있지만 테라스만 되는 곳을
--    구분하는 값이라, pet_restrictions 자유서술에 섞으면 필터를 만들 수 없다.
--
-- 2) locationservice.tour_content_id
--    TourAPI `contentid`. 적재와 상세 조회를 나눠 돌리므로(일일 한도 1,000회) 나중에 그 장소의
--    상세를 다시 부를 식별자가 행에 남아 있어야 한다. 없으면 이름으로 재검색해야 하고,
--    그러면 행당 호출이 두 배가 된다.
--
-- 3) location_sync_log.source
--    이제 이 테이블에 성격이 다른 두 동기화가 쓴다(odcloud 동반가능 문화시설 / TourAPI).
--    합쳐 두면 이력에서 「inserted=876」이 어느 데이터셋이었는지 말할 수 없다 — V22 에서
--    skip 사유를 쪼갠 것과 같은 이유다.
--    NULL 을 허용하는 이유: 기존 행은 전부 문화시설 동기화지만, 0 이나 특정 값으로 채우면
--    「그때도 source 를 기록하고 있었다」는 거짓이 된다. NULL = 계측 이전.

ALTER TABLE `locationservice`
    ADD COLUMN `pet_zone_type`    VARCHAR(30) NULL AFTER `pet_restrictions`,
    ADD COLUMN `tour_content_id`  VARCHAR(20) NULL AFTER `pet_zone_type`;

ALTER TABLE `location_sync_log`
    ADD COLUMN `source` VARCHAR(30) NULL AFTER `trigger_type`;
