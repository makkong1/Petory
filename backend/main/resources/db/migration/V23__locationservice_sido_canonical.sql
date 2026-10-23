-- 시도명을 현행 법정명으로 통일한다.
--
-- findBySido 가 WHERE sido = :sido 정확일치이고, 프론트(UnifiedPetMapPage.js)가 사용자가
-- 입력한 지역명을 그대로 그 파라미터로 보낸다. 그래서 현행 법정명으로 검색하면 거의 아무것도
-- 나오지 않았다 (2026-10-23 개발 DB 측정):
--
--   「강원특별자치도」(2023-06-11 현행명) 검색 →   2건  (실제 886)
--   「전북특별자치도」(2024-01-18 현행명) 검색 →   0건  (실제 902)
--   「강원도」      (구명)              검색 → 884건
--
-- 그리고 약식 표기가 섞여 있었다 — PET_DATA_API 출처가 「서울」34·「부산」9·「인천」7·
-- 「울산」1·「대구」1·「광주」1 로 넣어 뒀다. 합 53행(0.2%)이라 눈에 안 띄었을 뿐이다.
--
-- 🔴 이 파일만으로는 반쪽이다. SidoNormalizer 가 검색 입력도 같은 목록으로 통과시켜야
--    「강원도」로 치는 사용자가 886건을 본다. 두 곳을 함께 고칠 것.
--
-- 왜 지금인가: 다음 작업이 TourAPI 적재인데 그쪽이 「강원특별자치도」·「전남광주통합특별시」·
-- 「경기」 같은 비표준·약식을 섞어 준다. 선행하지 않으면 불일치가 100건 넘게 커진다.

-- 약식 → 정식
UPDATE `locationservice` SET `sido` = '서울특별시'     WHERE `sido` IN ('서울', '서울시');
UPDATE `locationservice` SET `sido` = '부산광역시'     WHERE `sido` IN ('부산', '부산시');
UPDATE `locationservice` SET `sido` = '대구광역시'     WHERE `sido` IN ('대구', '대구시');
UPDATE `locationservice` SET `sido` = '인천광역시'     WHERE `sido` IN ('인천', '인천시');
UPDATE `locationservice` SET `sido` = '광주광역시'     WHERE `sido` IN ('광주', '광주시');
UPDATE `locationservice` SET `sido` = '대전광역시'     WHERE `sido` IN ('대전', '대전시');
UPDATE `locationservice` SET `sido` = '울산광역시'     WHERE `sido` IN ('울산', '울산시');
UPDATE `locationservice` SET `sido` = '세종특별자치시' WHERE `sido` IN ('세종', '세종시');
UPDATE `locationservice` SET `sido` = '경기도'         WHERE `sido` = '경기';
UPDATE `locationservice` SET `sido` = '충청북도'       WHERE `sido` = '충북';
UPDATE `locationservice` SET `sido` = '충청남도'       WHERE `sido` = '충남';
UPDATE `locationservice` SET `sido` = '전라남도'       WHERE `sido` = '전남';
UPDATE `locationservice` SET `sido` = '경상북도'       WHERE `sido` = '경북';
UPDATE `locationservice` SET `sido` = '경상남도'       WHERE `sido` = '경남';

-- 구 법정명 → 현행명. 886·902행을 놓치게 만든 축이다.
UPDATE `locationservice` SET `sido` = '강원특별자치도' WHERE `sido` IN ('강원', '강원도');
UPDATE `locationservice` SET `sido` = '전북특별자치도' WHERE `sido` IN ('전북', '전라북도');
UPDATE `locationservice` SET `sido` = '제주특별자치도' WHERE `sido` IN ('제주', '제주도');
