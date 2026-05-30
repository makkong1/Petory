-- place_facts_from_public_data_v1.sql
-- place_candidates strong match(AUTO_APPROVED) 기준으로
-- 매칭된 locationservice(공공데이터) 필드를 place_facts에 복사.
-- 이미 실행됨: 2026-05-30

INSERT INTO place_facts (place_id, fact_type, value_text, source, confidence, observed_at)
SELECT p.id, 'PHONE', ls.phone, 'PUBLIC_DATA', 1.0, ls.last_updated
FROM place_candidates pc
JOIN places p ON pc.matched_place_id = p.id
JOIN locationservice ls ON pc.matched_locationservice_id = ls.idx
WHERE pc.decision_status = 'AUTO_APPROVED'
  AND ls.phone IS NOT NULL AND ls.phone != '';

INSERT INTO place_facts (place_id, fact_type, value_text, source, confidence, observed_at)
SELECT p.id, 'OPERATING_HOURS', ls.operating_hours, 'PUBLIC_DATA', 1.0, ls.last_updated
FROM place_candidates pc
JOIN places p ON pc.matched_place_id = p.id
JOIN locationservice ls ON pc.matched_locationservice_id = ls.idx
WHERE pc.decision_status = 'AUTO_APPROVED'
  AND ls.operating_hours IS NOT NULL AND ls.operating_hours != '';

INSERT INTO place_facts (place_id, fact_type, value_text, source, confidence, observed_at)
SELECT p.id, 'CLOSED_DAY', ls.closed_day, 'PUBLIC_DATA', 1.0, ls.last_updated
FROM place_candidates pc
JOIN places p ON pc.matched_place_id = p.id
JOIN locationservice ls ON pc.matched_locationservice_id = ls.idx
WHERE pc.decision_status = 'AUTO_APPROVED'
  AND ls.closed_day IS NOT NULL AND ls.closed_day != '';

INSERT INTO place_facts (place_id, fact_type, value_text, source, confidence, observed_at)
SELECT p.id, 'PET_SIZE', ls.pet_size, 'PUBLIC_DATA', 1.0, ls.last_updated
FROM place_candidates pc
JOIN places p ON pc.matched_place_id = p.id
JOIN locationservice ls ON pc.matched_locationservice_id = ls.idx
WHERE pc.decision_status = 'AUTO_APPROVED'
  AND ls.pet_size IS NOT NULL AND ls.pet_size != '';

INSERT INTO place_facts (place_id, fact_type, value_text, source, confidence, observed_at)
SELECT p.id, 'PET_RESTRICTIONS', ls.pet_restrictions, 'PUBLIC_DATA', 1.0, ls.last_updated
FROM place_candidates pc
JOIN places p ON pc.matched_place_id = p.id
JOIN locationservice ls ON pc.matched_locationservice_id = ls.idx
WHERE pc.decision_status = 'AUTO_APPROVED'
  AND ls.pet_restrictions IS NOT NULL AND ls.pet_restrictions != '';

INSERT INTO place_facts (place_id, fact_type, value_text, source, confidence, observed_at)
SELECT p.id, 'PET_EXTRA_FEE', ls.pet_extra_fee, 'PUBLIC_DATA', 1.0, ls.last_updated
FROM place_candidates pc
JOIN places p ON pc.matched_place_id = p.id
JOIN locationservice ls ON pc.matched_locationservice_id = ls.idx
WHERE pc.decision_status = 'AUTO_APPROVED'
  AND ls.pet_extra_fee IS NOT NULL AND ls.pet_extra_fee != '';

INSERT INTO place_facts (place_id, fact_type, value_text, source, confidence, observed_at)
SELECT p.id, 'PRICE_INFO', ls.price_info, 'PUBLIC_DATA', 1.0, ls.last_updated
FROM place_candidates pc
JOIN places p ON pc.matched_place_id = p.id
JOIN locationservice ls ON pc.matched_locationservice_id = ls.idx
WHERE pc.decision_status = 'AUTO_APPROVED'
  AND ls.price_info IS NOT NULL AND ls.price_info != '';

INSERT INTO place_facts (place_id, fact_type, value_text, source, confidence, observed_at)
SELECT p.id, 'WEBSITE', ls.website, 'PUBLIC_DATA', 1.0, ls.last_updated
FROM place_candidates pc
JOIN places p ON pc.matched_place_id = p.id
JOIN locationservice ls ON pc.matched_locationservice_id = ls.idx
WHERE pc.decision_status = 'AUTO_APPROVED'
  AND ls.website IS NOT NULL AND ls.website != '';
