-- Meetup 더미 데이터 생성 스크립트 (1000~5000개)
-- 실행 전: user_idx가 실제로 존재하는지 확인하세요

-- 사용 가능한 user_idx 목록
-- 14~113, 141~559

SET @user_ids = '14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,71,72,73,74,75,76,77,78,79,80,81,82,83,84,85,86,87,88,89,90,91,92,93,94,95,96,97,98,99,100,101,102,103,104,105,106,107,108,109,110,111,112,113,141,142,143,144,145,146,147,148,149,150,151,152,153,154,155,156,157,158,159,160,161,162,163,164,165,166,167,168,169,170,171,172,173,174,175,176,177,178,179,180,181,182,183,184,185,186,187,188,189,190,191,192,193,194,195,196,197,198,199,200,201,202,203,204,205,206,207,208,209,210,211,212,213,214,215,216,217,218,219,220,221,222,223,224,225,226,227,228,229,230,231,232,233,234,235,236,237,238,239,240,268,269,270,271,272,273,274,275,276,277,278,279,280,281,282,283,284,285,286,287,288,289,290,291,292,293,294,295,296,297,298,299,300,301,302,303,304,305,306,307,308,309,310,311,312,313,314,315,316,317,318,319,320,321,322,323,324,325,326,327,328,329,330,331,332,333,334,335,336,337,338,339,340,341,342,343,344,345,346,347,348,349,350,351,352,353,354,355,356,357,358,359,360,361,362,363,364,365,366,367,395,396,397,398,399,400,401,402,403,404,405,406,407,408,409,410,411,412,413,414,415,416,417,418,419,420,421,422,423,424,425,426,427,428,429,430,431,432,433,434,435,436,437,438,439,440,441,442,443,444,445,446,447,448,449,450,451,452,453,454,455,456,457,458,459,460,461,462,463,464,465,466,467,468,469,470,471,472,473,474,475,476,477,478,479,480,481,482,483,484,485,486,487,488,489,490,491,492,493,494,522,523,524,525,526,527,528,529,530,531,532,533,534,535,536,537,538,539,540,541,542,543,544,545,546,547,548,549,550,551,552,553,554,555,556,557,558,559';

-- 제목 템플릿
SET @titles = '강아지 산책 모임,반려동물 놀이터 모임,펫카페 방문 모임,강아지 유치원 체험 모임,고양이 놀이 모임,반려동물 미용 모임,강아지 훈련 모임,펫샵 쇼핑 모임,반려동물 병원 방문 모임,강아지 공원 모임,고양이 카페 모임,반려동물 사진 촬영 모임,강아지 수영 모임,펫 페스티벌 참가 모임,반려동물 입양 설명회 모임,강아지 미용 체험 모임,고양이 놀이터 모임,반려동물 건강 검진 모임,강아지 산책 동호회,펫카페 체험 모임';

-- 설명 템플릿
SET @descriptions = '함께 산책하며 반려동물과 즐거운 시간을 보내요,반려동물 친구들과 함께 즐거운 시간을 보내요,새로운 반려동물 친구들을 만나요,반려동물과 함께하는 즐거운 모임입니다,반려동물 건강과 행복을 위한 모임입니다';

-- 지역 목록 (한국 주요 도시)
SET @locations = '서울특별시 강남구,서울특별시 서초구,서울특별시 송파구,서울특별시 마포구,서울특별시 종로구,경기도 성남시,경기도 수원시,경기도 고양시,경기도 용인시,인천광역시 남동구,부산광역시 해운대구,대구광역시 수성구,대전광역시 유성구,광주광역시 북구,울산광역시 남구';

-- 좌표 범위 (한국)
-- 위도: 33.0 ~ 38.6
-- 경도: 124.0 ~ 132.0

-- 삭제
DROP PROCEDURE IF EXISTS generate_meetup_dummy_data;

DELIMITER $$

CREATE PROCEDURE generate_meetup_dummy_data(IN count INT)
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE v_title VARCHAR(200);
    DECLARE v_description TEXT;
    DECLARE v_location VARCHAR(255);
    DECLARE v_latitude DOUBLE;
    DECLARE v_longitude DOUBLE;
    DECLARE v_date DATETIME;
    DECLARE v_organizer_idx BIGINT;
    DECLARE v_max_participants INT;
    DECLARE v_current_participants INT;
    DECLARE v_status VARCHAR(20);
    DECLARE v_is_deleted TINYINT(1) DEFAULT 0;
    DECLARE v_deleted_at DATETIME DEFAULT NULL;
    DECLARE v_rand_status INT;
    DECLARE v_days_offset INT;
    
    -- 사용 가능한 user_idx 배열 (간단한 방법으로 랜덤 선택)
    DECLARE user_count INT DEFAULT 400; -- 대략적인 user 수
    
    WHILE i < count DO
        -- 랜덤 제목 선택
        SET v_title = CONCAT(
            ELT(1 + FLOOR(RAND() * 20), 
                '강아지 산책 모임', '반려동물 놀이터 모임', '펫카페 방문 모임', 
                '강아지 유치원 체험 모임', '고양이 놀이 모임', '반려동물 미용 모임',
                '강아지 훈련 모임', '펫샵 쇼핑 모임', '반려동물 병원 방문 모임',
                '강아지 공원 모임', '고양이 카페 모임', '반려동물 사진 촬영 모임',
                '강아지 수영 모임', '펫 페스티벌 참가 모임', '반려동물 입양 설명회 모임',
                '강아지 미용 체험 모임', '고양이 놀이터 모임', '반려동물 건강 검진 모임',
                '강아지 산책 동호회', '펫카페 체험 모임'
            ),
            ' #', i + 1
        );
        
        -- 랜덤 설명
        SET v_description = ELT(1 + FLOOR(RAND() * 5),
            '함께 산책하며 반려동물과 즐거운 시간을 보내요',
            '반려동물 친구들과 함께 즐거운 시간을 보내요',
            '새로운 반려동물 친구들을 만나요',
            '반려동물과 함께하는 즐거운 모임입니다',
            '반려동물 건강과 행복을 위한 모임입니다'
        );
        
        -- 랜덤 지역
        SET v_location = ELT(1 + FLOOR(RAND() * 15),
            '서울특별시 강남구', '서울특별시 서초구', '서울특별시 송파구',
            '서울특별시 마포구', '서울특별시 종로구', '경기도 성남시',
            '경기도 수원시', '경기도 고양시', '경기도 용인시',
            '인천광역시 남동구', '부산광역시 해운대구', '대구광역시 수성구',
            '대전광역시 유성구', '광주광역시 북구', '울산광역시 남구'
        );
        
        -- 랜덤 좌표 (한국 범위)
        SET v_latitude = 33.0 + (RAND() * 5.6);  -- 33.0 ~ 38.6
        SET v_longitude = 124.0 + (RAND() * 8.0); -- 124.0 ~ 132.0
        
        -- 랜덤 날짜 (현재 시간 + 1일 ~ +90일)
        SET v_days_offset = 1 + FLOOR(RAND() * 90);
        SET v_date = DATE_ADD(NOW(), INTERVAL v_days_offset DAY);
        -- 시간도 랜덤하게 (09:00 ~ 20:00)
        SET v_date = DATE_ADD(v_date, INTERVAL (9 + FLOOR(RAND() * 12)) HOUR);
        SET v_date = DATE_ADD(v_date, INTERVAL FLOOR(RAND() * 60) MINUTE);
        
        -- 랜덤 주최자 선택 (14~113, 141~559 범위)
        -- 간단하게 14 + (0~99) 또는 141 + (0~418) 중 선택
        SELECT idx
            INTO v_organizer_idx
            FROM users
            ORDER BY RAND()
            LIMIT 1;
        
        -- 최대 참여 인원 (5~20)
        SET v_max_participants = 5 + FLOOR(RAND() * 16);
        
        -- 현재 참여 인원 (0 ~ max_participants)
        SET v_current_participants = FLOOR(RAND() * (v_max_participants + 1));
        
        -- 상태 분포: RECRUITING(70%), CLOSED(20%), COMPLETED(10%)
        SET v_rand_status = FLOOR(RAND() * 100);
        IF v_rand_status < 70 THEN
            SET v_status = 'RECRUITING';
        ELSEIF v_rand_status < 90 THEN
            SET v_status = 'CLOSED';
        ELSE
            SET v_status = 'COMPLETED';
        END IF;
        
        -- 삭제된 모임은 5% 확률
        IF RAND() < 0.05 THEN
            SET v_is_deleted = 1;
            SET v_deleted_at = DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 30) DAY);
        END IF;
        
        -- INSERT 실행
        INSERT INTO meetup (
            title, description, location, latitude, longitude, date,
            organizer_idx, max_participants, current_participants, status,
            is_deleted, deleted_at, created_at, updated_at
        ) VALUES (
            v_title, v_description, v_location, v_latitude, v_longitude, v_date,
            v_organizer_idx, v_max_participants, v_current_participants, v_status,
            v_is_deleted, v_deleted_at, NOW(), NOW()
        );
        
        SET i = i + 1;
        
        -- 진행 상황 출력 (100개마다)
        IF i % 100 = 0 THEN
            SELECT CONCAT('생성 완료: ', i, ' / ', count) AS progress;
        END IF;
    END WHILE;
    
    SELECT CONCAT('총 ', count, '개의 Meetup 더미 데이터 생성 완료!') AS result;
END$$

DELIMITER ;

-- 사용법:
-- CALL generate_meetup_dummy_data(3000);  -- 3000개 생성
-- CALL generate_meetup_dummy_data(1000);  -- 1000개 생성
-- CALL generate_meetup_dummy_data(5000);  -- 5000개 생성

-- 생성 확인
-- SELECT COUNT(*) as total_meetups FROM meetup;
-- SELECT COUNT(*) as active_meetups FROM meetup WHERE is_deleted = false OR is_deleted IS NULL;
-- SELECT status, COUNT(*) as count FROM meetup WHERE is_deleted = false OR is_deleted IS NULL GROUP BY status;

