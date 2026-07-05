-- 이미 적용된 경우 생략: tags 컬럼이 없을 때만 실행
ALTER TABLE locationservice
    ADD COLUMN tags JSON NULL COMMENT '장소 태그 배열 (place_tag_batch 생성)';
