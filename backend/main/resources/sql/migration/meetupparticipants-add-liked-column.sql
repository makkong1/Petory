-- 모임 히스토리에서 사용자가 다시 보고 싶은 모임을 표시하기 위한 좋아요 컬럼
ALTER TABLE meetupparticipants
  ADD COLUMN liked TINYINT(1) NOT NULL DEFAULT 0 COMMENT '사용자 모임 기록 좋아요 여부';

CREATE INDEX idx_meetupparticipants_user_liked_joined
  ON meetupparticipants (user_idx, liked, joined_at);
