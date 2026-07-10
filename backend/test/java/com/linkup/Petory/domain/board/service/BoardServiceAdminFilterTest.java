package com.linkup.Petory.domain.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.board.dto.BoardDTO;
import com.linkup.Petory.domain.board.dto.BoardPageResponseDTO;
import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.repository.BoardRepository;
import com.linkup.Petory.domain.common.ContentStatus;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

/**
 * BoardService.getAdminBoardsWithPagingOptimized() 검증.
 *
 * Specification으로 조합되는 4개 필터(deleted/category/status/q)를 각각 검증한다.
 * FULLTEXT MATCH...AGAINST(idx_board_title_content ngram 인덱스)를 사용하므로
 * H2가 아닌 실제 MySQL(로컬 개발 DB)에 대해 실행되어야 한다.
 *
 * 개발 DB에 이미 존재할 수 있는 다른 게시글과 섞이지 않도록,
 * 테스트마다 UUID 기반 고유 태그를 category/username에 심어 결과를 식별한다.
 */
@SpringBootTest
@Transactional
class BoardServiceAdminFilterTest {

    @Autowired
    private BoardService boardService;
    @Autowired
    private BoardRepository boardRepository;
    @Autowired
    private UsersRepository usersRepository;

    private String tag;
    private Board active1;
    private Board active2;
    private Board notice;
    private Board deleted;
    private Board byOtherWriter;
    private Users writer;
    private Users finder;

    @BeforeEach
    void setUp() {
        tag = "tag" + UUID.randomUUID().toString().substring(0, 8);

        writer = usersRepository.save(Users.builder()
                .id(tag + "-writer")
                .username(tag + "-writer")
                .email(tag + "-writer@test.com")
                .nickname(tag + "-작성자")
                .password("password")
                .role(Role.USER)
                .build());

        finder = usersRepository.save(Users.builder()
                .id(tag + "-finder")
                .username(tag + "-finder")
                .email(tag + "-finder@test.com")
                .nickname(tag + "-검색대상")
                .password("password")
                .role(Role.USER)
                .build());

        active1 = boardRepository.save(Board.builder()
                .title("강아지 산책 후기 고유테스트키워드사용됨")
                .content("오늘 강아지랑 신나게 산책했어요")
                .category(tag)
                .status(ContentStatus.ACTIVE)
                .isDeleted(false)
                .user(writer)
                .build());

        active2 = boardRepository.save(Board.builder()
                .title("고양이 사료 추천")
                .content("우리 고양이가 좋아하는 사료")
                .category(tag)
                .status(ContentStatus.ACTIVE)
                .isDeleted(false)
                .user(writer)
                .build());

        notice = boardRepository.save(Board.builder()
                .title("공지사항 안내")
                .content("시스템 점검 안내입니다")
                .category(tag + "-notice")
                .status(ContentStatus.BLINDED)
                .isDeleted(false)
                .user(writer)
                .build());

        deleted = boardRepository.save(Board.builder()
                .title("삭제된 게시글")
                .content("삭제된 내용")
                .category(tag)
                .status(ContentStatus.ACTIVE)
                .isDeleted(true)
                .user(writer)
                .build());

        byOtherWriter = boardRepository.save(Board.builder()
                .title("기타 게시글")
                .content("검색 대상 작성자 글")
                .category(tag)
                .status(ContentStatus.ACTIVE)
                .isDeleted(false)
                .user(finder)
                .build());
    }

    @AfterEach
    void tearDown() {
        // FULLTEXT 검색 테스트는 TestTransaction으로 실제 커밋되어 개발 DB에 남으므로 직접 정리한다.
        // 나머지 테스트는 기존 트랜잭션(#1) 안에서 삭제 후 함께 롤백되어 결과적으로 no-op이다.
        // 아래 delete들이 실제로 반영되도록 커밋까지 명시적으로 수행한다 — FULLTEXT 테스트는
        // 이미 두 번째 트랜잭션(#2)이 활성 상태라 삭제분만 커밋되고, 나머지 테스트는
        // "삽입 후 삭제"가 같은 트랜잭션에서 커밋되어도 최종 DB 상태는 비어 있어 안전하다.
        for (Board board : List.of(active1, active2, notice, deleted, byOtherWriter)) {
            if (boardRepository.existsById(board.getIdx())) {
                boardRepository.deleteById(board.getIdx());
            }
        }
        for (Users user : List.of(writer, finder)) {
            if (usersRepository.findById(user.getIdx()).isPresent()) {
                usersRepository.deleteById(user.getIdx());
            }
        }
        TestTransaction.flagForCommit();
        TestTransaction.end();
    }

    private List<Long> resultIdxs(String status, Boolean deletedFilter, String category, String q) {
        BoardPageResponseDTO page = boardService.getAdminBoardsWithPagingOptimized(
                status, deletedFilter, category, q, 0, 1000);
        return page.boards().stream().map(BoardDTO::getIdx).collect(Collectors.toList());
    }

    @Test
    @DisplayName("정상: deleted=false면 삭제되지 않은 게시글만 반환한다")
    void 정상_삭제여부_필터링() {
        List<Long> idxs = resultIdxs(null, false, tag, null);

        assertThat(idxs).contains(active1.getIdx(), active2.getIdx(), byOtherWriter.getIdx());
        assertThat(idxs).doesNotContain(deleted.getIdx());
    }

    @Test
    @DisplayName("정상: deleted=true면 삭제된 게시글만 반환한다")
    void 정상_삭제된게시글만_필터링() {
        List<Long> idxs = resultIdxs(null, true, tag, null);

        assertThat(idxs).contains(deleted.getIdx());
        assertThat(idxs).doesNotContain(active1.getIdx(), active2.getIdx(), byOtherWriter.getIdx());
    }

    @Test
    @DisplayName("정상: category로 필터링하면 해당 카테고리만 반환한다")
    void 정상_카테고리_필터링() {
        List<Long> idxs = resultIdxs(null, null, tag + "-notice", null);

        assertThat(idxs).containsExactly(notice.getIdx());
    }

    @Test
    @DisplayName("정상: status=BLINDED면 블라인드된 게시글만 반환한다")
    void 정상_상태_필터링() {
        List<Long> idxs = resultIdxs("BLINDED", null, null, null);

        assertThat(idxs).contains(notice.getIdx());
        assertThat(idxs).doesNotContain(active1.getIdx(), active2.getIdx(), deleted.getIdx(), byOtherWriter.getIdx());
    }

    @Test
    @DisplayName("정상: 검색어가 제목/내용에 FULLTEXT로 매칭되면 반환한다")
    void 정상_검색어_제목내용_매칭() {
        // InnoDB FULLTEXT 인덱스는 커밋된 데이터만 검색 대상으로 삼는다(같은 트랜잭션 내 삽입은 미반영).
        // setUp()에서 삽입한 데이터를 실제로 커밋한 뒤 새 트랜잭션에서 검색해야
        // MATCH...AGAINST가 정상 동작한다. 커밋된 데이터는 tearDown()에서 직접 정리한다.
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        // FULLTEXT 관련도는 전체 테이블(개발 DB에 이미 1만 건 이상 존재) 기준으로 계산되므로
        // "강아지"처럼 흔한 단어는 관련도가 낮게 나올 수 있다. 이 테스트 케이스에만 존재하는
        // 고유 한글 키워드로 매칭 여부만 검증한다.
        List<Long> idxs = resultIdxs(null, false, tag, "고유테스트키워드사용됨");

        assertThat(idxs).contains(active1.getIdx());
        assertThat(idxs).doesNotContain(active2.getIdx());
    }

    @Test
    @DisplayName("정상: 검색어가 작성자명 접두사와 매칭되면 반환한다")
    void 정상_검색어_작성자명_접두사매칭() {
        List<Long> idxs = resultIdxs(null, false, tag, finder.getUsername());

        assertThat(idxs).contains(byOtherWriter.getIdx());
        assertThat(idxs).doesNotContain(active1.getIdx(), active2.getIdx());
    }

    @Test
    @DisplayName("예외: 잘못된 status 문자열은 예외 없이 무시되고 전체 조회된다")
    void 예외_잘못된_상태값은_무시된다() {
        assertThatCode(() -> resultIdxs("NOT_A_REAL_STATUS", false, tag, null))
                .doesNotThrowAnyException();

        List<Long> idxs = resultIdxs("NOT_A_REAL_STATUS", false, tag, null);
        assertThat(idxs).contains(active1.getIdx(), active2.getIdx(), byOtherWriter.getIdx());
    }

    @Test
    @DisplayName("경계: category=ALL이면 카테고리 필터가 적용되지 않는다")
    void 경계_카테고리_ALL이면_필터미적용() {
        List<Long> idxs = resultIdxs(null, false, "ALL", finder.getUsername());

        assertThat(idxs).contains(byOtherWriter.getIdx());
    }

    @Test
    @DisplayName("경계: 검색어가 공백뿐이면 검색 필터가 적용되지 않는다")
    void 경계_검색어_공백이면_필터미적용() {
        List<Long> idxs = resultIdxs(null, false, tag, "   ");

        assertThat(idxs).contains(active1.getIdx(), active2.getIdx(), byOtherWriter.getIdx());
    }

    @Test
    @DisplayName("경계: 마지막 페이지를 넘어가면 빈 목록을 반환한다")
    void 경계_존재하지않는_페이지는_빈결과() {
        BoardPageResponseDTO page = boardService.getAdminBoardsWithPagingOptimized(
                null, false, tag, null, 999, 10);

        assertThat(page.boards()).isEmpty();
    }
}
