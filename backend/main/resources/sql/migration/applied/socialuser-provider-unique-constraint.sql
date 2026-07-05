-- 동일 (provider, provider_id) 로 SocialUser 가 중복 생성되는 동시성 레이스 방지용 유니크 제약.
-- 기존 계정에 소셜 연동 시 check-then-insert 가 겹치면 중복 행이 생겼다(락/제약 부재).

-- 1) 제약 추가 전, 이미 쌓인 중복 행 정리 (그룹별 최소 idx 만 남김)
DELETE s1 FROM socialuser s1
    JOIN socialuser s2
      ON s1.provider = s2.provider
     AND s1.provider_id = s2.provider_id
     AND s1.idx > s2.idx;

-- 2) 유니크 제약 추가 — 이후 동시 INSERT 는 DB 가 거부하고, 앱은 재조회로 복구한다
ALTER TABLE socialuser
    ADD CONSTRAINT uk_socialuser_provider_providerid UNIQUE (provider, provider_id);
