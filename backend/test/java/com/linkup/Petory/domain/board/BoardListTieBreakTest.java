package com.linkup.Petory.domain.board;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.board.dto.BoardListItemDTO;
import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.repository.BoardRepository;
import com.linkup.Petory.domain.board.repository.SpringDataJpaBoardRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.UserStatus;
import com.linkup.Petory.domain.user.entity.Users;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ====================================================================================
 * 게시글 목록 정렬의 동점 처리(tie-break) 검증 (2026-07-30)
 * ====================================================================================
 *
 * <p>
 * <b>배경</b>: 목록은 2단계 지연조인이다. 1단계가 커버링 인덱스로 {@code idx} 만 뽑고, 2단계가 그
 * {@code idx} 들로 본문을 조립하면서 정렬을 다시 재현한다. 그런데 두 단계 모두 정렬 키가
 * {@code created_at} 하나뿐이었고, {@code board.created_at} 컬럼 타입은 <b>{@code datetime}(초
 * 단위, 밀리초 없음)</b> 이다.
 *
 * <p>
 * 같은 초에 글이 여러 개 올라오면 {@code created_at} 이 완전히 같아져 <b>DB 가 순서를 보장하지
 * 않는다.</b> 그러면 두 가지가 깨질 수 있다:
 * <ul>
 * <li>1단계가 고른 순서와 2단계가 재현한 순서가 <b>어긋난다</b></li>
 * <li>OFFSET 페이징이라 매 요청마다 정렬을 새로 하므로, 동점 순서가 흔들리면
 * <b>페이지 경계에서 같은 글이 중복되거나 빠진다</b></li>
 * </ul>
 *
 * <p>
 * 이 테스트는 <b>동일 초에 몰린 글</b>을 만들어 그 두 성질을 고정한다. 해결은 두 단계의
 * {@code ORDER BY} 에 고유값인 {@code idx} 를 보조 키로 붙이는 것이다(주변서비스 반경검색은 이미
 * {@code rating DESC, idx ASC} 로 동점을 처리하고 있다).
 *
 * <p>
 * ⚠️ 전체 목록이 아니라 <b>전용 카테고리</b>로 범위를 좁힌다 — {@code petory_test} 에 더미 시드가
 * 들어와 있어 전체 목록으로는 격리가 안 된다.
 * ====================================================================================
 */
@SpringBootTest
@Transactional
class BoardListTieBreakTest {

    @Autowired
    EntityManager em;
    @Autowired
    BoardRepository boardRepository;
    @Autowired
    SpringDataJpaBoardRepository jpaBoardRepository;

    /** 같은 created_at 을 공유할 글 수. 페이지 크기의 배수가 아니게 잡아 경계를 물게 한다. */
    private static final int SAME_SECOND_BOARDS = 7;
    private static final int PAGE_SIZE = 2;

    private String category;
    private List<Long> createdIdxs;

    @BeforeEach
    void seed() {
        String marker = "tie_" + UUID.randomUUID().toString().substring(0, 8);
        category = "cat_" + marker;

        Users author = Users.builder()
                .id(marker + "_author")
                .username(marker + "_author")
                .email(marker + "@test.petory")
                .password("x")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .isDeleted(false)
                .build();
        em.persist(author);

        createdIdxs = new ArrayList<>();
        for (int i = 0; i < SAME_SECOND_BOARDS; i++) {
            Board board = Board.builder()
                    .user(author)
                    .title("동점 정렬 테스트 " + i)
                    .content("본문 " + i)
                    .category(category)
                    .authorVisible(true)
                    .build();
            em.persist(board);
            em.flush();
            createdIdxs.add(board.getIdx());
        }

        // 전부 같은 '초'로 맞춘다 — datetime 이라 밀리초는 어차피 버려진다.
        LocalDateTime sameSecond = LocalDateTime.now().withNano(0).minusMinutes(1);
        em.createNativeQuery("UPDATE board SET created_at = :ts WHERE category = :category")
                .setParameter("ts", sameSecond)
                .setParameter("category", category)
                .executeUpdate();

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("동일 created_at 이어도 페이지를 넘기며 훑으면 중복·누락이 없다")
    void 동점_정렬에서_페이지_경계에_중복이나_누락이_없다() {
        List<Long> collected = new ArrayList<>();
        int pages = (int) Math.ceil((double) SAME_SECOND_BOARDS / PAGE_SIZE);

        for (int page = 0; page < pages; page++) {
            List<BoardListItemDTO> content = boardRepository
                    .findBoardListItemsByCategory(category, PageRequest.of(page, PAGE_SIZE))
                    .getContent();
            content.forEach(dto -> collected.add(dto.getIdx()));
        }

        assertThat(collected)
                .as("페이지를 모두 훑었는데 중복이 있다. created_at 동점 순서가 요청마다 흔들리면 "
                        + "OFFSET 페이징에서 같은 글이 두 페이지에 걸쳐 나온다. 수집=%s", collected)
                .doesNotHaveDuplicates();

        assertThat(collected)
                .as("페이지를 모두 훑었는데 빠진 글이 있다. 동점 순서가 흔들리면 어떤 글은 어느 페이지에도 안 나온다.")
                .containsExactlyInAnyOrderElementsOf(createdIdxs);
    }

    @Test
    @DisplayName("1단계(idx 조회)와 2단계(본문 조립)의 순서가 정확히 일치한다")
    void 지연조인_두_단계의_정렬_순서가_같다() {
        for (int page = 0; page < 3; page++) {
            List<Long> stage1 = jpaBoardRepository
                    .findVisibleBoardIdsByCategory(category, (long) page * PAGE_SIZE, PAGE_SIZE);
            List<Long> stage2 = jpaBoardRepository.findBoardListItemsByIdIn(stage1).stream()
                    .map(BoardListItemDTO::getIdx)
                    .toList();

            assertThat(stage2)
                    .as("page %d — 1단계가 고른 순서와 2단계가 재현한 순서가 다르다. "
                            + "두 쿼리의 ORDER BY 에 고유 보조 키(idx)가 없어 동점 순서가 서로 다르게 결정된 것이다. "
                            + "1단계=%s / 2단계=%s", page, stage1, stage2)
                    .containsExactlyElementsOf(stage1);
        }
    }

    @Test
    @DisplayName("같은 페이지를 반복 조회해도 순서가 동일하다 (결정적 정렬)")
    void 같은_페이지를_반복_조회해도_순서가_같다() {
        List<Long> first = pageIdxs(0);
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(pageIdxs(0))
                    .as("같은 요청인데 순서가 달라졌다 — 정렬이 결정적이지 않다")
                    .containsExactlyElementsOf(first);
        }
    }

    private List<Long> pageIdxs(int page) {
        return boardRepository.findBoardListItemsByCategory(category, PageRequest.of(page, PAGE_SIZE))
                .getContent().stream().map(BoardListItemDTO::getIdx).toList();
    }
}
