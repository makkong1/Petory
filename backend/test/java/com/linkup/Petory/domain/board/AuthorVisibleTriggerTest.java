package com.linkup.Petory.domain.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.UserStatus;
import com.linkup.Petory.domain.user.entity.Users;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * V6 트리거 trg_board_author_visible 회귀 테스트.
 *
 * users 상태 변경(밴/탈퇴/정지)이 board.author_visible 에 올바르게 반영되는지 검증한다.
 * 트리거는 is_deleted 또는 BANNED 경계가 바뀔 때만 발동한다 (SUSPENDED<->ACTIVE 는 안 건드림).
 *
 * petory_test 는 board 시드 데이터가 없으므로(더미 데이터는 dev DB petory 전용), 각 테스트마다
 * 전용 user+board 행을 직접 만든다 — BoardListQueryPlanMaintainerTest 와 동일한 패턴.
 * @Transactional 롤백으로 격리되므로 별도 cleanup 은 불필요하다.
 *
 * 근거: backend/main/resources/db/migration/V6__board_author_visible.sql
 */
@SpringBootTest
@Transactional
class AuthorVisibleTriggerTest {

    @Autowired EntityManager em;

    private Long testUserIdx;

    @BeforeEach
    void seedUserAndBoard() {
        String marker = "avt_" + UUID.randomUUID().toString().substring(0, 8);
        Users user = Users.builder()
                .id(marker)
                .username(marker)
                .email(marker + "@test.petory")
                .password("x")
                .role(Role.USER)
                .build();
        em.persist(user);

        Board board = Board.builder()
                .user(user)
                .title("author_visible 트리거 테스트")
                .content("본문")
                .category("FREE")
                .build();
        em.persist(board);
        em.flush();

        testUserIdx = user.getIdx();
    }

    // MySQL TINYINT(1) 은 드라이버가 Boolean 으로 매핑한다(Number 아님) — 둘 다 받아준다.
    private boolean visibleFlag(Long userIdx) {
        Object result = em.createNativeQuery(
                "SELECT author_visible FROM board WHERE user_idx = :u LIMIT 1")
                .setParameter("u", userIdx).getSingleResult();
        return (result instanceof Boolean b) ? b : ((Number) result).intValue() != 0;
    }

    private void setStatus(Long userIdx, UserStatus status, boolean deleted) {
        Users u = em.find(Users.class, userIdx);
        u.setStatus(status);
        u.setIsDeleted(deleted);
        em.flush(); // users UPDATE → 트리거 발동
    }

    @Test
    void ban_hides_posts_and_unban_restores() {
        Long u = testUserIdx;
        setStatus(u, UserStatus.ACTIVE, false);
        assertThat(visibleFlag(u)).isTrue();

        setStatus(u, UserStatus.BANNED, false);
        assertThat(visibleFlag(u)).as("BANNED → 숨김").isFalse();

        setStatus(u, UserStatus.ACTIVE, false);
        assertThat(visibleFlag(u)).as("언밴 → 복원").isTrue();
    }

    @Test
    void withdraw_hides_and_reactivate_restores() {
        Long u = testUserIdx;
        setStatus(u, UserStatus.ACTIVE, true);
        assertThat(visibleFlag(u)).as("탈퇴 → 숨김").isFalse();

        setStatus(u, UserStatus.ACTIVE, false);
        assertThat(visibleFlag(u)).as("복구 → 복원").isTrue();
    }

    @Test
    void suspend_does_not_touch_posts() {
        Long u = testUserIdx;
        setStatus(u, UserStatus.ACTIVE, false);
        assertThat(visibleFlag(u)).isTrue();

        setStatus(u, UserStatus.SUSPENDED, false);
        assertThat(visibleFlag(u)).as("정지 → 그대로 보임").isTrue();
    }
}
