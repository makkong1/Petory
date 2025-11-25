-- Database Indexes for Petory Application
-- Based on repository query patterns

-- ============================================
-- USERS TABLE INDEXES
-- ============================================
-- username으로 조회 (findByUsername)
CREATE INDEX idx_users_username ON users(username);

-- id로 조회는 unique constraint로 이미 인덱스가 생성됨
-- email로 조회도 unique constraint로 이미 인덱스가 생성됨

-- refreshToken으로 조회 (findByRefreshToken)
-- 이거는 좀 고민해보자
CREATE INDEX idx_users_refresh_token ON users(refresh_token);

-- location으로 조회 (CareRequest에서 사용)
CREATE INDEX idx_users_location ON users(location);


-- ============================================
-- BOARD TABLE INDEXES
-- ============================================
-- 전체 게시글 최신순 조회 (findAllByOrderByCreatedAtDesc)
CREATE INDEX idx_board_created_at_desc ON board(created_at);

-- 카테고리별 게시글 조회 (findByCategoryOrderByCreatedAtDesc)
CREATE INDEX idx_board_category_created_at ON board(category, created_at);

-- 사용자별 게시글 조회 (findByUserOrderByCreatedAtDesc)
CREATE INDEX idx_board_user_idx_created_at ON board(user_idx, created_at);

-- 제목/내용 검색용 (findByTitleContainingOrContentContainingOrderByCreatedAtDesc)
-- MySQL의 경우 FULLTEXT 인덱스 사용 가능
-- MySQL 8.0에서는 InnoDB FULLTEXT 인덱스가 UTF-8 환경에서 한글 형태소 분석이 약함.
-- "반려동물" → "반려", "동물" 식으로 분리되지 않아 검색 누락 가능.
-- 따라서 ngram 파서를 사용하여 검색 성능을 향상가능
CREATE FULLTEXT INDEX idx_board_title_content ON board(title, content) WITH PARSER ngram;
-- 추가로 정렬을 위한 인덱스
CREATE INDEX idx_board_created_at ON board(created_at);


-- ============================================
-- LOCATIONSERVICE TABLE INDEXES
-- ============================================
-- 지역별 서비스 조회 (findByLocationRange) - 위도/경도 범위 검색
-- 1. 일반 복합 인덱스
CREATE INDEX idx_locationservice_latitude_longitude ON locationservice(latitude, longitude);

-- 2. 공간 인덱스 (Spatial Index)
-- MySQL에서 공간 검색을 쓸 계획이라면 SPATIAL INDEX가 가장 빠름.
-- 단, latitude/longitude 컬럼 타입이 반드시 FLOAT/DOUBLE이 아닌, POINT 타입(geometry)이 이상적으로 추천됨.
-- 만약 latitude, longitude가 별도 컬럼으로 double이고, 지리적 거리 연산이 아니라 단순 범위(BETWEEN) 검색 위주면,
-- 일반 복합 인덱스만으로도 성능에 도움을 줄 수 있지만, 
-- latitude 기준으로 먼저 걸러지고 longitude는 그 다음에만 쓰이므로, 
-- index selectivity가 낮으면 효율이 떨어질 수 있음.

-- SPATIAL INDEX를 쓰려면 (MySQL InnoDB 5.7+, 컬럼 타입이 POINT 등 geometry 계열로 변경 필요)
-- 만약 지금 컬럼이 double 이라면 아래 alter는 에러날 수 있음.
-- ALTER TABLE locationservice ADD SPATIAL INDEX idx_locationservice_coords (latitude, longitude);

-- Q: 어떤 것이 더 나은가?
-- A: 
-- - 범위(BETWEEN)로 latitude/longitude를 단순 조건식으로 자주 조회한다면 일반 인덱스가 실제로 더 효율적일 수 있음. 
--   왜냐하면 SPATIAL INDEX는 geometry 타입 컬럼이어야 하며, 두 개의 double 타입에는 적용이 제한됨.
-- - "내 주변 서비스"처럼 반경(distance) 기반 검색이 필수라면, POINT로 geometry 컬럼을 재설계하고, SPATIAL INDEX를 붙이는게 최적임. 
-- - 실제 쿼리가 `where latitude between x1 and x2 and longitude between y1 and y2` 형식이면, 
--   복합(BTREE) 인덱스가 적용되고, MySQL은 latitude 범위를 먼저 필터링 후, 남은 행에서 longitude 범위로 추가 필터링. 
--   두 값의 분포도와 쿼리 패턴에 따라 성능차가 있음.

-- 결론: 
-- - 현재 테이블 설계가 double이라면, 복합 인덱스가 가장 쉽고 무난하다.
-- - 추후 더 정밀한 공간(geo) 기반 검색이 필요하거나, 반경 기반 r-tree 탐색이 필요하다면 POINT/GEOMETRY로 컬럼 변경 후 SPATIAL INDEX를 고민.
-- -- End of Selection

-- 평점순 조회 (findByOrderByRatingDesc)
CREATE INDEX idx_locationservice_rating_desc ON locationservice(rating DESC);

-- 카테고리별 평점순 조회 (findByCategoryOrderByRatingDesc)
CREATE INDEX idx_locationservice_category_rating ON locationservice(category, rating DESC);

-- 평점 이상 조회 (findByRatingGreaterThanEqualOrderByRatingDesc)
-- rating DESC 인덱스로 커버 가능

-- 이름으로 검색 (findByNameContaining)
CREATE FULLTEXT INDEX idx_locationservice_name_description ON locationservice(name, description);

-- 이름과 주소로 중복 체크 (findByNameAndAddress)
CREATE INDEX idx_locationservice_name_address ON locationservice(name, address);

-- 주소로 중복 체크 (findByAddress)
CREATE INDEX idx_locationservice_address ON locationservice(address);

-- 주소와 상세주소로 중복 체크 (findByAddressAndDetailAddress)
CREATE INDEX idx_locationservice_address_detail ON locationservice(address, detail_address);

-- 주소로 서비스 검색 (findByAddressContaining)
-- address 인덱스로 커버 가능


-- ============================================
-- LOCATIONSERVICEREVIEW TABLE INDEXES
-- ============================================
-- 특정 서비스의 리뷰 조회 (findByServiceIdxOrderByCreatedAtDesc)
CREATE INDEX idx_locationservicereview_service_idx_created_at ON locationservicereview(service_idx, created_at);

-- 특정 사용자의 리뷰 조회 (findByUserIdxOrderByCreatedAtDesc)
CREATE INDEX idx_locationservicereview_user_idx_created_at ON locationservicereview(user_idx, created_at);

-- 특정 서비스의 평균 평점 계산 (findAverageRatingByServiceIdx)
-- service_idx 인덱스로 커버 가능

-- 특정 서비스의 리뷰 개수 (countByServiceIdx)
-- service_idx 인덱스로 커버 가능

-- 특정 사용자가 특정 서비스에 리뷰 작성 여부 확인 (existsByServiceIdxAndUserIdx)
CREATE INDEX idx_locationservicereview_service_user ON locationservicereview(service_idx, user_idx);


-- ============================================
-- MEETUP TABLE INDEXES
-- ============================================
-- 주최자별 모임 조회 (findByOrganizerIdxOrderByCreatedAtDesc)
CREATE INDEX idx_meetup_organizer_idx_created_at ON meetup(organizer_idx, created_at);

-- 지역별 모임 조회 (findByLocationRange) - 위도/경도 범위 검색
CREATE INDEX idx_meetup_latitude_longitude ON meetup(latitude, longitude);

-- 날짜별 모임 조회 (findByDateBetweenOrderByDateAsc)
CREATE INDEX idx_meetup_date_asc ON meetup(date ASC);

-- 키워드 검색 (findByKeyword)
CREATE FULLTEXT INDEX idx_meetup_title_description ON meetup(title, description);

-- 참여 가능한 모임 조회 (findAvailableMeetups)
-- date 인덱스로 커버 가능


-- ============================================
-- MEETUPPARTICIPANTS TABLE INDEXES
-- ============================================
-- 특정 모임의 참여자 목록 (findByMeetupIdxOrderByJoinedAtAsc)
CREATE INDEX idx_meetupparticipants_meetup_idx_joined_at ON meetupparticipants(meetup_idx, joined_at ASC);

-- 특정 사용자가 참여한 모임 목록 (findByUserIdxOrderByJoinedAtDesc)
CREATE INDEX idx_meetupparticipants_user_idx_joined_at ON meetupparticipants(user_idx, joined_at DESC);

-- 특정 모임의 참여자 수 (countByMeetupIdx)
-- meetup_idx 인덱스로 커버 가능

-- 특정 사용자가 특정 모임에 참여했는지 확인 (existsByMeetupIdxAndUserIdx)
-- 복합 기본키로 이미 인덱스가 생성됨, 하지만 명시적으로 생성
-- 이건 일단 생각중
CREATE INDEX idx_meetupparticipants_meetup_user ON meetupparticipants(meetup_idx, user_idx);

-- 특정 사용자가 참여한 모임 중 진행 예정인 모임들 (findUpcomingMeetupsByUser)
-- user_idx 인덱스로 커버 가능


-- ============================================
-- CAREREQUEST TABLE INDEXES
-- ============================================
-- 사용자별 케어 요청 조회 (findByUserOrderByCreatedAtDesc)
CREATE INDEX idx_carerequest_user_idx_created_at ON carerequest(user_idx, created_at);

-- 상태별 케어 요청 조회 (findByStatus)
CREATE INDEX idx_carerequest_status ON carerequest(status);

-- 위치별 케어 요청 조회 (findByUser_LocationContaining)
-- users 테이블의 location 인덱스로 커버 가능

-- 제목이나 설명에 키워드 포함된 케어 요청 검색 (findByTitleContainingOrDescriptionContaining)
CREATE FULLTEXT INDEX idx_carerequest_title_description ON carerequest(title, description);


-- ============================================
-- BOARDPOPULARITYSNAPSHOT TABLE INDEXES
-- ============================================
-- 기본 조회 쿼리 최적화 (정확한 날짜 매칭)
-- 쿼리: findByPeriodTypeAndPeriodStartDateAndPeriodEndDateOrderByRankingAsc
-- WHERE: period_type = ? AND period_start_date = ? AND period_end_date = ?
-- ORDER BY: ranking ASC
CREATE INDEX idx_snapshot_period_ranking 
ON board_popularity_snapshot(period_type, period_start_date, period_end_date, ranking);

-- 범위 조회 쿼리 최적화 (기간 겹치는 경우)
-- 쿼리: findByPeriodTypeAndPeriodStartDateLessThanEqualAndPeriodEndDateGreaterThanEqualOrderByRankingAsc
-- WHERE: period_type = ? AND period_start_date <= ? AND period_end_date >= ?
-- ORDER BY: ranking ASC
CREATE INDEX idx_snapshot_period_range_ranking 
ON board_popularity_snapshot(period_type, period_start_date, period_end_date, ranking);

-- 최근 스냅샷 조회 최적화
-- 쿼리: findTop30ByPeriodTypeOrderByPeriodEndDateDescRankingAsc
-- WHERE: period_type = ?
-- ORDER BY: period_end_date DESC, ranking ASC
-- LIMIT: 30
CREATE INDEX idx_snapshot_recent 
ON board_popularity_snapshot(period_type, period_end_date DESC, ranking ASC);

-- board_id 조회 최적화 (FK이지만 명시적 인덱스 생성)
-- 특정 게시글의 스냅샷 이력 조회 시 사용
CREATE INDEX idx_snapshot_board_id 
ON board_popularity_snapshot(board_id);
