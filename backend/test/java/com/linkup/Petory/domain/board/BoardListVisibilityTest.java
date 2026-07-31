package com.linkup.Petory.domain.board;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.board.dto.BoardListItemDTO;
import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.repository.BoardRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.UserStatus;
import com.linkup.Petory.domain.user.entity.Users;

/**
 * 목록 쿼리(2단계 지연 조인 + author_visible 단일 테이블 COUNT) 정합성 테스트.
 *
 * <p>
 * 기존 쿼리는 {@code u.status = 'ACTIVE'} 로 걸러 정지(SUSPENDED) 작성자 글까지 숨겼다. author_visible
 * 은 {@code (미탈퇴 AND status<>BANNED)} 이므로 SUSPENDED 는 보여야 한다 — 이 테스트의 핵심 단언은 "SUSPENDED
 * 작성자 글이 목록에 포함된다"이며, 지연조인 전환 전 코드에서는 반드시 실패해야 한다(TDD RED).
 *
 * <p>
 * 각 테스트마다 전용 user+board 행을 직접 만든다 — {@link AuthorVisibleTriggerTest} 와 동일한 패턴.
 * (petory_test 에도 더미 시드가 들어와 있으므로 전체 건수를 절대값으로 단언하지 않는다.) board.created_at
 * 컬럼은 초 단위(datetime, 밀리초 없음)라 JPA auditing 이 부여한 값만으로는 순서를 결정적으로 만들 수 없어,
 * 네이티브 UPDATE 로 명시적으로 벌린다(1단계 native 쿼리가 실제 DB 값을 읽으므로 flush 필수).
 */
@SpringBootTest
@Transactional
class BoardListVisibilityTest {

    @Autowired
    EntityManager em;
    @Autowired
    BoardRepository boardRepository;

    private String category;
    private Long activeBoardIdx;
    private Long suspendedBoardIdx;
    private Long bannedBoardIdx;
    private Long withdrawnBoardIdx;

    @BeforeEach
    void seed() {
        String marker = "blv_" + UUID.randomUUID().toString().substring(0, 8);
        category = "cat_" + marker;

        Users activeUser = persistUser(marker + "_active", UserStatus.ACTIVE, false);
        Users suspendedUser = persistUser(marker + "_susp", UserStatus.SUSPENDED, false);
        Users bannedUser = persistUser(marker + "_ban", UserStatus.BANNED, false);
        Users withdrawnUser = persistUser(marker + "_wd", UserStatus.ACTIVE, true);

        // author_visible = (미탈퇴 AND status<>BANNED): ACTIVE·SUSPENDED → true, BANNED·탈퇴 → false
        activeBoardIdx = persistBoard(activeUser, true);
        suspendedBoardIdx = persistBoard(suspendedUser, true);
        bannedBoardIdx = persistBoard(bannedUser, false);
        withdrawnBoardIdx = persistBoard(withdrawnUser, false);

        // created_at 을 서로 겹치지 않게 명시적으로 벌려 DESC 정렬을 결정적으로 만든다.
        // 최신 → 과거 순으로: withdrawn(-1m) > banned(-2m) > suspended(-3m) > active(-4m)
        setCreatedAt(activeBoardIdx, LocalDateTime.now().minusMinutes(4));
        setCreatedAt(suspendedBoardIdx, LocalDateTime.now().minusMinutes(3));
        setCreatedAt(bannedBoardIdx, LocalDateTime.now().minusMinutes(2));
        setCreatedAt(withdrawnBoardIdx, LocalDateTime.now().minusMinutes(1));

        em.flush();
        em.clear(); // 이후 리포지토리 호출이 영속성 컨텍스트 캐시가 아니라 DB 값을 읽도록
    }

    private Users persistUser(String id, UserStatus status, boolean deleted) {
        Users user = Users.builder()
                .id(id)
                .username(id)
                .email(id + "@test.petory")
                .password("x")
                .role(Role.USER)
                .status(status)
                .isDeleted(deleted)
                .build();
        em.persist(user);
        return user;
    }

    private Long persistBoard(Users user, boolean authorVisible) {
        Board board = Board.builder()
                .user(user)
                .title("목록 지연조인 테스트")
                .content("본문")
                .category(category)
                .authorVisible(authorVisible)
                .build();
        em.persist(board);
        em.flush();
        return board.getIdx();
    }

    private void setCreatedAt(Long boardIdx, LocalDateTime createdAt) {
        em.createNativeQuery("UPDATE board SET created_at = :createdAt WHERE idx = :idx")
                .setParameter("createdAt", createdAt)
                .setParameter("idx", boardIdx)
                .executeUpdate();
    }

    @Test
    void 전체_목록은_ACTIVE와_SUSPENDED_작성자_글만_보이고_생성일_역순이다() {
        Page<BoardListItemDTO> page = boardRepository.findBoardListItems(PageRequest.of(0, 4));

        List<Long> idxs = page.getContent().stream().map(BoardListItemDTO::getIdx).toList();

        assertThat(idxs)
                .as("ACTIVE 작성자 글 포함")
                .contains(activeBoardIdx);
        assertThat(idxs)
                .as("SUSPENDED 작성자 글도 포함되어야 한다 — 기존 u.status='ACTIVE' 필터를 걷어낸 핵심 단언")
                .contains(suspendedBoardIdx);
        assertThat(idxs)
                .as("BANNED 작성자 글은 제외")
                .doesNotContain(bannedBoardIdx);
        assertThat(idxs)
                .as("탈퇴 작성자 글은 제외")
                .doesNotContain(withdrawnBoardIdx);

        assertThat(page.getContent())
                .as("created_at DESC 순서")
                .isSortedAccordingTo((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

        // petory_test 에도 더미 시드(board 5만)가 들어와 절대값으로 고정할 수 없다.
        // 검증 의도는 "COUNT 가 users 조인 없이 author_visible 만으로 세어진다"이므로 그 정의와 대조한다.
        long visibleInDb = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM board WHERE is_deleted = 0 AND author_visible = 1")
                .getSingleResult()).longValue();

        assertThat(page.getTotalElements())
                .as("단일 테이블 COUNT: 보이는(author_visible=true) 글 수와 일치")
                .isEqualTo(visibleInDb);
    }

    @Test
    void 카테고리_목록도_같은_기준으로_필터링된다() {
        Page<BoardListItemDTO> page = boardRepository.findBoardListItemsByCategory(category, PageRequest.of(0, 4));

        List<Long> idxs = page.getContent().stream().map(BoardListItemDTO::getIdx).toList();

        assertThat(idxs).contains(activeBoardIdx, suspendedBoardIdx);
        assertThat(idxs).doesNotContain(bannedBoardIdx, withdrawnBoardIdx);
        assertThat(page.getContent())
                .isSortedAccordingTo((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        assertThat(page.getTotalElements()).isEqualTo(2L);
    }
}
