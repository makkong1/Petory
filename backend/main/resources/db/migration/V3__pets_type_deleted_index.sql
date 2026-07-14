-- /api/pets/type/{petType} 에 페이징을 붙이면서 생긴 Page<> COUNT 쿼리를 고친다.
--
-- 증상: COUNT(p.idx) FROM pets WHERE pet_type='DOG' AND is_deleted=0 이
--       pets 가 12,000행인데 19,667행을 검사한다.
--
-- 원인: (pet_type, is_deleted) 복합 인덱스가 없어서 MySQL 이 인덱스 머지를 한다.
--         idx_pets_deleted (is_deleted=0)  → 12,000행
--       ∩ idx_pets_type    (pet_type=DOG)  →  7,667행
--       = 19,667행을 읽고 교집합(Intersect)을 구한다.
--
-- 복합 인덱스가 있으면 단일 레인지 스캔 7,667행으로 끝난다.
-- 근거: docs/analysis/query-audit/fixes-2026-07-14.md §3

ALTER TABLE pets
    ADD INDEX idx_pets_type_deleted (pet_type, is_deleted);
