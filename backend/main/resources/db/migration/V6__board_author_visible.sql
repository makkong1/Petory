-- board 목록 깊은 페이지 + 자동 COUNT 비용의 근본 원인은 "조인 건너편 작성자 필터"다.
-- (u.is_deleted=0 AND u.status='ACTIVE' 가 board 인덱스만으로 offset 을 못 세게 만든다)
-- 작성자 보임 여부를 board 컬럼으로 내려 커버링 idx skip + 단일 테이블 COUNT 를 가능케 한다.
-- 의미: author_visible = (미탈퇴 AND status<>BANNED). 정지(SUSPENDED)는 보임(일시적).

ALTER TABLE board ADD COLUMN author_visible TINYINT(1) NOT NULL DEFAULT 1;
-- 새 글은 항상 보임: 밴/탈퇴 회원은 글 생성 경로에서 차단되므로 DEFAULT 1 이 정확하다.

-- 기존 행 백필
UPDATE board b JOIN users u ON u.idx = b.user_idx
SET b.author_visible = IF(u.is_deleted = 0 AND u.status <> 'BANNED', 1, 0);

-- 전체 목록 + COUNT 커버링 (등가 2컬럼 + created_at 정렬)
ALTER TABLE board ADD INDEX idx_board_visible_created (is_deleted, author_visible, created_at DESC);
-- 카테고리 목록 커버링
ALTER TABLE board ADD INDEX idx_board_cat_visible_created (category, is_deleted, author_visible, created_at DESC);

-- 동기화: 회원 상태를 바꾸는 모든 경로(관리자 밴/언밴, 제재, 탈퇴, 재활성화)는 결국 users 를
-- UPDATE 한다. 트리거 하나로 전부 잡는다. is_deleted 또는 BANNED 경계가 바뀔 때만 발동한다
-- (로그인의 last_login_at 갱신 등 흔한 UPDATE 에는 안 걸리고, SUSPENDED<->ACTIVE 도 안 건드림).
-- Flyway 의 SQL 파서는 BEGIN...END 로 감싸지 않은 복합문(IF...END IF)의 내부 세미콜론에서
-- 문장을 잘라 SQL 문법 오류를 낸다(1064). BEGIN/END 로 감싸 하나의 문장으로 인식시킨다.
CREATE TRIGGER trg_board_author_visible AFTER UPDATE ON users
FOR EACH ROW
BEGIN
  IF (OLD.is_deleted <> NEW.is_deleted)
     OR ((OLD.status = 'BANNED') <> (NEW.status = 'BANNED')) THEN
    UPDATE board SET author_visible = IF(NEW.is_deleted = 0 AND NEW.status <> 'BANNED', 1, 0)
    WHERE user_idx = NEW.idx;
  END IF;
END;
