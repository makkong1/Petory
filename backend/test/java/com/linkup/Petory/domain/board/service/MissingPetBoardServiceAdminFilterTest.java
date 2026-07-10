package com.linkup.Petory.domain.board.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.board.dto.MissingPetBoardDTO;
import com.linkup.Petory.domain.board.dto.MissingPetBoardPageResponseDTO;
import com.linkup.Petory.domain.board.entity.MissingPetBoard;
import com.linkup.Petory.domain.board.entity.MissingPetStatus;
import com.linkup.Petory.domain.board.repository.MissingPetBoardRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

/**
 * MissingPetBoardService.getAdminBoardsWithPaging() 검증.
 *
 * Specification으로 조합되는 3개 필터(deleted/status/q)를 각각 검증한다.
 * 검색어(q)는 게시글 쪽 FULLTEXT와 달리 title/content/petName/username 전부 LIKE라
 * H2로도 재현 가능하지만, 프로젝트 컨벤션(@SpringBootTest, 실 DB)을 그대로 따른다.
 *
 * status는 MissingPetStatus enum 타입 파라미터라 애초에 잘못된 문자열이 들어올 수 없어
 * (컴파일 타임에 막힘) BoardService 쪽과 달리 "잘못된 값 무시" 예외 케이스가 성립하지 않는다.
 */
@SpringBootTest
@Transactional
class MissingPetBoardServiceAdminFilterTest {

    @Autowired
    private MissingPetBoardService missingPetBoardService;
    @Autowired
    private MissingPetBoardRepository missingPetBoardRepository;
    @Autowired
    private UsersRepository usersRepository;

    private String tag;
    private MissingPetBoard missing1;
    private MissingPetBoard found1;
    private MissingPetBoard resolved1;
    private MissingPetBoard deleted;
    private MissingPetBoard byOtherWriter;
    private Users finder;

    @BeforeEach
    void setUp() {
        tag = "tag" + UUID.randomUUID().toString().substring(0, 8);

        Users writer = usersRepository.save(Users.builder()
                .id(tag + "-writer")
                .username(tag + "-writer")
                .email(tag + "-writer@test.com")
                .nickname("작성자")
                .password("password")
                .role(Role.USER)
                .build());

        finder = usersRepository.save(Users.builder()
                .id(tag + "-finder")
                .username(tag + "-finder")
                .email(tag + "-finder@test.com")
                .nickname("검색대상")
                .password("password")
                .role(Role.USER)
                .build());

        missing1 = missingPetBoardRepository.save(MissingPetBoard.builder()
                .title(tag + " 실종된 강아지 찾아요")
                .content("흰색 강아지입니다")
                .petName(tag + "-choco")
                .status(MissingPetStatus.MISSING)
                .isDeleted(false)
                .user(writer)
                .build());

        found1 = missingPetBoardRepository.save(MissingPetBoard.builder()
                .title(tag + " 고양이 발견했어요")
                .content("검은 고양이")
                .petName(tag + "-nabi")
                .status(MissingPetStatus.FOUND)
                .isDeleted(false)
                .user(writer)
                .build());

        resolved1 = missingPetBoardRepository.save(MissingPetBoard.builder()
                .title(tag + " 완료된 사례")
                .content("주인 찾았습니다")
                .petName(tag + "-gureum")
                .status(MissingPetStatus.RESOLVED)
                .isDeleted(false)
                .user(writer)
                .build());

        deleted = missingPetBoardRepository.save(MissingPetBoard.builder()
                .title(tag + " 삭제된 제보")
                .content("삭제됨")
                .petName(tag + "-sakjae")
                .status(MissingPetStatus.MISSING)
                .isDeleted(true)
                .user(writer)
                .build());

        byOtherWriter = missingPetBoardRepository.save(MissingPetBoard.builder()
                .title(tag + " 검색용 제보")
                .content("아무 내용")
                .petName(tag + "-byeol")
                .status(MissingPetStatus.MISSING)
                .isDeleted(false)
                .user(finder)
                .build());
    }

    private List<Long> resultIdxs(MissingPetStatus status, Boolean deletedFilter, String q) {
        MissingPetBoardPageResponseDTO page = missingPetBoardService.getAdminBoardsWithPaging(
                status, deletedFilter, q, 0, 1000);
        return page.boards().stream().map(MissingPetBoardDTO::getIdx).collect(Collectors.toList());
    }

    @Test
    @DisplayName("정상: status로 필터링하면 해당 상태만 반환한다")
    void 정상_상태별_필터링() {
        List<Long> idxs = resultIdxs(MissingPetStatus.FOUND, null, tag);

        assertThat(idxs).containsExactly(found1.getIdx());
    }

    @Test
    @DisplayName("정상: deleted=false면 삭제되지 않은 제보만 반환한다")
    void 정상_삭제여부_필터링() {
        List<Long> idxs = resultIdxs(null, false, tag);

        assertThat(idxs).contains(missing1.getIdx(), found1.getIdx(), resolved1.getIdx(), byOtherWriter.getIdx());
        assertThat(idxs).doesNotContain(deleted.getIdx());
    }

    @Test
    @DisplayName("정상: 검색어가 반려동물 이름에 매칭되면 반환한다")
    void 정상_검색어_반려동물이름_매칭() {
        List<Long> idxs = resultIdxs(null, false, missing1.getPetName());

        assertThat(idxs).containsExactly(missing1.getIdx());
    }

    @Test
    @DisplayName("정상: 검색어가 작성자명에 매칭되면 반환한다")
    void 정상_검색어_작성자명_매칭() {
        List<Long> idxs = resultIdxs(null, false, finder.getUsername());

        assertThat(idxs).containsExactly(byOtherWriter.getIdx());
    }

    @Test
    @DisplayName("경계: 검색어가 공백뿐이면 검색 필터가 적용되지 않는다")
    void 경계_검색어_공백이면_필터미적용() {
        List<Long> idxs = resultIdxs(null, false, "   ");

        assertThat(idxs).contains(missing1.getIdx(), found1.getIdx(), resolved1.getIdx(), byOtherWriter.getIdx());
    }

    @Test
    @DisplayName("경계: 마지막 페이지를 넘어가면 빈 목록을 반환한다")
    void 경계_존재하지않는_페이지는_빈결과() {
        MissingPetBoardPageResponseDTO page = missingPetBoardService.getAdminBoardsWithPaging(
                null, false, tag, 999, 10);

        assertThat(page.boards()).isEmpty();
    }
}
