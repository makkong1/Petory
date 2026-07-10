package com.linkup.Petory.domain.board.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
 * [오버페칭 제거] 목록 projection 경로 검증.
 *
 * getAllBoardsWithPaging / searchBoardsWithPaging(NICKNAME)이 JOIN FETCH(작성자 27컬럼) 대신
 * BoardListItemDTO 생성자 표현식 projection(작성자 3컬럼)으로 전환된 뒤에도
 * (1) JPQL 생성자 표현식 + enum(status) 매핑이 런타임에 유효하고,
 * (2) 화면이 쓰는 필드(작성자 idx/username/location, content, 통계)가 정확히 채워지는지 확인한다.
 *
 * 개발 DB의 기존 데이터와 섞이지 않도록 category/nickname에 UUID 태그를 심어 식별한다.
 */
@SpringBootTest
@Transactional
class BoardServiceListProjectionTest {

    @Autowired
    private BoardService boardService;
    @Autowired
    private BoardRepository boardRepository;
    @Autowired
    private UsersRepository usersRepository;

    private String tag;
    private Users writer;
    private Board active1;
    private Board active2;
    private Board deleted;

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
                .location("서울시 강남구")
                .build());

        active1 = boardRepository.save(Board.builder()
                .title("강아지 산책 후기")
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

        deleted = boardRepository.save(Board.builder()
                .title("삭제된 게시글")
                .content("삭제된 내용")
                .category(tag)
                .status(ContentStatus.ACTIVE)
                .isDeleted(true)
                .user(writer)
                .build());
    }

    @Test
    @DisplayName("정상: 카테고리 목록 projection이 작성자 3컬럼·본문·통계를 정확히 채운다")
    void 정상_카테고리_projection_필드매핑() {
        BoardPageResponseDTO page = boardService.getAllBoardsWithPaging(tag, 0, 20);

        List<BoardDTO> boards = page.boards();
        assertThat(boards).extracting(BoardDTO::getIdx)
                .contains(active1.getIdx(), active2.getIdx())
                .doesNotContain(deleted.getIdx()); // isDeleted=true 제외

        BoardDTO dto = boards.stream()
                .filter(b -> b.getIdx().equals(active1.getIdx()))
                .findFirst().orElseThrow();
        // 작성자 3컬럼 projection
        assertThat(dto.getUserId()).isEqualTo(writer.getIdx());
        assertThat(dto.getUsername()).isEqualTo(writer.getUsername());
        assertThat(dto.getUserLocation()).isEqualTo("서울시 강남구");
        // 본문/enum status 매핑
        assertThat(dto.getContent()).isEqualTo("오늘 강아지랑 신나게 산책했어요");
        assertThat(dto.getTitle()).isEqualTo("강아지 산책 후기");
        assertThat(dto.getStatus()).isEqualTo(ContentStatus.ACTIVE.name());
        // 통계 필드는 null이 아니라 0으로 채워진다(배치 enrichment)
        assertThat(dto.getLikes()).isNotNull();
        assertThat(dto.getDislikes()).isNotNull();
        assertThat(dto.getViews()).isNotNull();
    }

    @Test
    @DisplayName("정상: 닉네임 검색 projection이 작성자 접두사로 매칭된다")
    void 정상_닉네임검색_projection() {
        BoardPageResponseDTO page = boardService.searchBoardsWithPaging(tag, "NICKNAME", 0, 20);

        assertThat(page.boards()).extracting(BoardDTO::getIdx)
                .contains(active1.getIdx(), active2.getIdx())
                .doesNotContain(deleted.getIdx());
        assertThat(page.boards()).allSatisfy(b ->
                assertThat(b.getUsername()).isEqualTo(writer.getUsername()));
    }

    @Test
    @DisplayName("경계: 마지막 페이지를 넘어가면 빈 목록을 반환한다")
    void 경계_존재하지않는_페이지는_빈결과() {
        BoardPageResponseDTO page = boardService.getAllBoardsWithPaging(tag, 999, 10);

        assertThat(page.boards()).isEmpty();
    }
}
