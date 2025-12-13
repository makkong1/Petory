-- socialuser 테이블의 provider 컬럼 크기 수정
-- Provider enum 값: KAKAO(5자), GOOGLE(6자), NAVER(5자)
-- 최소 10자 이상으로 설정 (여유 있게 20자)

ALTER TABLE socialuser 
MODIFY COLUMN provider VARCHAR(20) NOT NULL;

