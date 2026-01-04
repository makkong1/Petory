-- ============================================
-- 펫케어 요청 더미 데이터 생성 스크립트
-- 1000개의 COMPLETED 상태 펫케어 요청 생성
-- ============================================

USE petory_test;  -- 또는 실제 사용하는 데이터베이스 이름으로 변경

SET FOREIGN_KEY_CHECKS = 0;

-- ============================================
-- 변수 설정
-- ============================================
SET @care_request_seq = 0;
SET @max_user = (SELECT MAX(idx) FROM users WHERE is_deleted = 0 AND status = 'ACTIVE');
SET @max_pet = (SELECT MAX(idx) FROM pets WHERE is_deleted = 0);

-- 펫이 없는 경우를 대비한 체크
SET @has_pets = IF(@max_pet IS NULL OR @max_pet = 0, 0, 1);

-- ============================================
-- CareRequest 1000개 더미 데이터 생성
-- ============================================

INSERT INTO carerequest (
    user_idx,
    pet_idx,
    title,
    description,
    date,
    status,
    is_deleted,
    deleted_at,
    created_at,
    updated_at
)
SELECT
    -- 활성 사용자 중 랜덤 선택
    u.idx AS user_idx,
    
    -- 펫 정보 (30% 확률로 NULL, 70% 확률로 해당 사용자의 펫 중 랜덤 선택)
    CASE 
        WHEN RAND() < 0.3 OR @has_pets = 0 THEN NULL
        ELSE (
            SELECT p.idx 
            FROM pets p 
            WHERE p.user_idx = u.idx 
              AND p.is_deleted = 0 
            ORDER BY RAND() 
            LIMIT 1
        )
    END AS pet_idx,
    
    -- 랜덤 제목 생성
    CONCAT(
        ELT(1 + (@care_request_seq % 10),
            '강아지 산책 도와주세요',
            '고양이 돌봄 부탁드립니다',
            '반려동물 펜션 맡아주실 분',
            '출장 중 강아지 케어 요청',
            '병원 가는 동안 펫 돌봄',
            '여행 중 반려동물 케어',
            '강아지 산책 서비스 필요',
            '고양이 급식 및 청소 부탁',
            '반려동물 목욕 서비스',
            '펫 호텔 대신 홈케어'
        ),
        ' - ',
        @care_request_seq := @care_request_seq + 1
    ) AS title,
    
    -- 랜덤 설명 생성
    CONCAT(
        '펫케어 요청 번호: ', @care_request_seq, '. ',
        ELT(1 + (@care_request_seq % 8),
            '반려동물을 안전하게 돌봐주실 분을 찾고 있습니다. 산책과 급식이 필요합니다.',
            '출장으로 인해 며칠간 반려동물을 맡아주실 분이 필요합니다. 친절하고 책임감 있는 분을 원합니다.',
            '병원 입원으로 인해 반려동물 케어가 어려워 도움을 요청합니다.',
            '여행 중 반려동물을 안전하게 돌봐주실 분을 찾습니다. 일일 2회 방문이 필요합니다.',
            '반려동물 산책과 급식 서비스를 필요로 합니다. 경험이 있는 분을 우대합니다.',
            '고양이 돌봄 서비스를 요청합니다. 급식, 물 갈아주기, 화장실 청소가 필요합니다.',
            '강아지 목욕과 산책 서비스를 원합니다. 대형견이라 체력이 좋은 분이면 좋겠습니다.',
            '반려동물 펜션 대신 집에서 돌봐주실 분을 찾고 있습니다. 안전한 환경이 중요합니다.'
        )
    ) AS description,
    
    -- 과거 날짜 랜덤 생성 (1년 전 ~ 1일 전)
    DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY) + INTERVAL FLOOR(RAND() * 24) HOUR AS date,
    
    -- 상태는 모두 COMPLETED
    'COMPLETED' AS status,
    
    -- 삭제되지 않음
    FALSE AS is_deleted,
    NULL AS deleted_at,
    
    -- 생성일시 (date보다 과거, 1년 전 ~ 1일 전)
    DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY) + INTERVAL FLOOR(RAND() * 24) HOUR AS created_at,
    
    -- 수정일시 (생성일시 이후 ~ 현재)
    DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 180) DAY) + INTERVAL FLOOR(RAND() * 24) HOUR AS updated_at

FROM
    -- 1000개 생성하기 위한 CROSS JOIN
    (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 
     UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS t1
    CROSS JOIN (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 
     UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS t2
    CROSS JOIN (SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 
     UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) AS t3
    CROSS JOIN (
        -- 활성 사용자 중 랜덤 선택 (각 행마다 다른 사용자 선택)
        SELECT idx 
        FROM users 
        WHERE is_deleted = 0 AND status = 'ACTIVE'
        ORDER BY RAND()
    ) AS u
LIMIT 1000;

-- ============================================
-- 외래키 체크 재활성화
-- ============================================
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================
-- 생성 결과 확인
-- ============================================
SELECT 
    COUNT(*) AS total_care_requests,
    COUNT(DISTINCT user_idx) AS unique_users,
    COUNT(pet_idx) AS requests_with_pet,
    COUNT(*) - COUNT(pet_idx) AS requests_without_pet,
    MIN(created_at) AS earliest_created,
    MAX(created_at) AS latest_created
FROM carerequest
WHERE status = 'COMPLETED' AND is_deleted = 0;

-- 상태별 통계
SELECT 
    status,
    COUNT(*) AS count
FROM carerequest
WHERE is_deleted = 0
GROUP BY status;

