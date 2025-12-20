package com.linkup.Petory.domain.board.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.linkup.Petory.domain.board.converter.BoardConverter;
import com.linkup.Petory.domain.board.dto.BoardDTO;
import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.entity.ReactionType;
import com.linkup.Petory.domain.board.repository.BoardReactionRepository;
import com.linkup.Petory.domain.board.repository.BoardRepository;
import com.linkup.Petory.domain.board.repository.BoardViewLogRepository;
import com.linkup.Petory.domain.common.ContentStatus;
import com.linkup.Petory.domain.file.dto.FileDTO;
import com.linkup.Petory.domain.file.entity.FileTargetType;
import com.linkup.Petory.domain.file.service.AttachmentFileService;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

/**
 * BoardService의 코드 중복 및 일관성 문제를 재현하는 테스트
 * 
 * 이 테스트는 code-duplication-mapping.md 문서에서 언급된 문제들을 검증합니다:
 * 1. Object[] 파싱 로직 중복
 * 2. 반응 카운트 설정 로직 중복
 * 3. 첨부파일 설정 로직 중복
 * 4. 단일/배치 조회 분리
 * 5. 관리자 조회에서 메모리 필터링 성능 문제
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BoardService 코드 중복 및 일관성 문제 재현 테스트")
class BoardServiceCodeDuplicationTest {

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private UsersRepository usersRepository;

    @Mock
    private BoardReactionRepository boardReactionRepository;

    @Mock
    private BoardViewLogRepository boardViewLogRepository;

    @Mock
    private AttachmentFileService attachmentFileService;

    @Mock
    private BoardConverter boardConverter;

    @InjectMocks
    private BoardService boardService;

    private Users testUser;
    private Board testBoard1;
    private Board testBoard2;
    private BoardDTO testBoardDTO1;
    private BoardDTO testBoardDTO2;

    @BeforeEach
    void setUp() {
        // 테스트 사용자 생성
        testUser = Users.builder()
                .idx(1L)
                .id("testuser")
                .username("테스트유저")
                .email("test@example.com")
                .password("password")
                .emailVerified(true)
                .role(com.linkup.Petory.domain.user.entity.Role.USER)
                .build();

        // 테스트 게시글 생성
        testBoard1 = Board.builder()
                .idx(1L)
                .title("테스트 게시글 1")
                .content("테스트 내용 1")
                .category("FREE")
                .status(ContentStatus.ACTIVE)
                .isDeleted(false)
                .user(testUser)
                .viewCount(10)
                .build();

        testBoard2 = Board.builder()
                .idx(2L)
                .title("테스트 게시글 2")
                .content("테스트 내용 2")
                .category("NOTICE")
                .status(ContentStatus.ACTIVE)
                .isDeleted(false)
                .user(testUser)
                .viewCount(20)
                .build();

        // 테스트 DTO 생성
        testBoardDTO1 = BoardDTO.builder()
                .idx(1L)
                .title("테스트 게시글 1")
                .content("테스트 내용 1")
                .category("FREE")
                .status("ACTIVE")
                .userId(1L)
                .username("테스트유저")
                .views(10)
                .build();

        testBoardDTO2 = BoardDTO.builder()
                .idx(2L)
                .title("테스트 게시글 2")
                .content("테스트 내용 2")
                .category("NOTICE")
                .status("ACTIVE")
                .userId(1L)
                .username("테스트유저")
                .views(20)
                .build();
    }

    @Test
    @DisplayName("문제 1: Object[] 파싱 로직 중복 - mapReactionCounts와 getReactionCountsBatch에서 동일한 로직 사용")
    void testObjectArrayParsingDuplication() {
        // Given: 단일 게시글 조회 시 반응 카운트 결과
        Long boardId = 1L;
        List<Object[]> singleResults = Arrays.asList(
                new Object[] { boardId, ReactionType.LIKE, 5L },
                new Object[] { boardId, ReactionType.DISLIKE, 2L });

        // 배치 조회 시 반응 카운트 결과
        List<Long> boardIds = Arrays.asList(1L, 2L);
        List<Object[]> batchReactionResults = Arrays.asList(
                new Object[] { 1L, ReactionType.LIKE, 5L },
                new Object[] { 1L, ReactionType.DISLIKE, 2L },
                new Object[] { 2L, ReactionType.LIKE, 3L },
                new Object[] { 2L, ReactionType.DISLIKE, 1L });

        when(boardReactionRepository.countByBoardGroupByReactionType(boardId))
                .thenReturn(singleResults);
        when(boardReactionRepository.countByBoardsGroupByReactionType(boardIds))
                .thenReturn(batchReactionResults);
        when(boardConverter.toDTO(any(Board.class))).thenReturn(testBoardDTO1);
        when(attachmentFileService.getAttachments(FileTargetType.BOARD, boardId))
                .thenReturn(Collections.emptyList());
        when(attachmentFileService.getAttachmentsBatch(eq(FileTargetType.BOARD), anyList()))
                .thenReturn(Collections.emptyMap());

        // When: 단일 조회 (mapReactionCounts 사용)
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(testBoard1));
        BoardDTO singleResult = boardService.getBoard(boardId, null);

        // When: 배치 조회 (getReactionCountsBatch 사용)
        when(boardRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc())
                .thenReturn(Arrays.asList(testBoard1, testBoard2));
        when(boardConverter.toDTO(testBoard1)).thenReturn(testBoardDTO1);
        when(boardConverter.toDTO(testBoard2)).thenReturn(testBoardDTO2);
        List<BoardDTO> batchBoardResults = boardService.getAllBoards(null);

        // Then: 두 메서드 모두 동일한 방식으로 Object[]를 파싱하는지 확인
        // 문제점: result[1], result[2] 같은 인덱스 접근이 두 곳에 중복됨
        assertNotNull(singleResult);
        assertNotNull(batchBoardResults);
        verify(boardReactionRepository).countByBoardGroupByReactionType(boardId);

        // 배치 조회도 동일한 파싱 로직 사용
        verify(boardReactionRepository).countByBoardsGroupByReactionType(anyList());
    }

    @Test
    @DisplayName("문제 2: 반응 카운트 설정 로직 중복 - mapReactionCounts와 mapBoardsWithReactionsBatch에서 동일한 로직")
    void testReactionCountSettingDuplication() {
        // Given
        Long boardId = 1L;
        List<Object[]> singleResults = Arrays.asList(
                new Object[] { boardId, ReactionType.LIKE, 5L },
                new Object[] { boardId, ReactionType.DISLIKE, 2L });

        List<Object[]> batchResults = Arrays.asList(
                new Object[] { 1L, ReactionType.LIKE, 5L },
                new Object[] { 1L, ReactionType.DISLIKE, 2L });

        when(boardReactionRepository.countByBoardGroupByReactionType(boardId))
                .thenReturn(singleResults);
        when(boardReactionRepository.countByBoardsGroupByReactionType(anyList()))
                .thenReturn(batchResults);
        when(boardConverter.toDTO(any(Board.class))).thenReturn(testBoardDTO1);
        when(attachmentFileService.getAttachments(FileTargetType.BOARD, boardId))
                .thenReturn(Collections.emptyList());
        when(attachmentFileService.getAttachmentsBatch(eq(FileTargetType.BOARD), anyList()))
                .thenReturn(Collections.emptyMap());
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(testBoard1));

        // When: 단일 조회
        BoardDTO singleResult = boardService.getBoard(boardId, null);

        // When: 배치 조회
        when(boardRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc())
                .thenReturn(Collections.singletonList(testBoard1));
        List<BoardDTO> batchResult = boardService.getAllBoards(null);

        // Then: 두 메서드 모두 Math.toIntExact를 사용하여 동일한 방식으로 설정
        // 문제점: dto.setLikes(Math.toIntExact(...)) 로직이 두 곳에 중복됨
        assertNotNull(singleResult);
        assertNotNull(batchResult);
        assertFalse(batchResult.isEmpty());

        // 두 방식 모두 동일한 결과를 반환해야 함
        // 하지만 코드 중복으로 인해 한 곳만 수정 시 불일치 발생 가능
    }

    @Test
    @DisplayName("문제 3: 첨부파일 설정 로직 중복 - mapAttachmentInfo와 mapBoardsWithReactionsBatch에서 동일한 로직")
    void testAttachmentInfoSettingDuplication() {
        // Given
        Long boardId = 1L;
        FileDTO fileDTO = FileDTO.builder()
                .filePath("/test/file.jpg")
                .downloadUrl("http://example.com/file.jpg")
                .build();
        List<FileDTO> attachments = Collections.singletonList(fileDTO);

        when(boardReactionRepository.countByBoardGroupByReactionType(boardId))
                .thenReturn(Collections.emptyList());
        when(boardReactionRepository.countByBoardsGroupByReactionType(anyList()))
                .thenReturn(Collections.emptyList());
        when(boardConverter.toDTO(any(Board.class))).thenReturn(testBoardDTO1);
        when(attachmentFileService.getAttachments(FileTargetType.BOARD, boardId))
                .thenReturn(attachments);
        when(attachmentFileService.getAttachmentsBatch(eq(FileTargetType.BOARD), anyList()))
                .thenReturn(Collections.singletonMap(boardId, attachments));
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(testBoard1));

        // When: 단일 조회 (mapAttachmentInfo 사용)
        BoardDTO singleResult = boardService.getBoard(boardId, null);

        // When: 배치 조회 (mapBoardsWithReactionsBatch 내부에서 직접 처리)
        when(boardRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc())
                .thenReturn(Collections.singletonList(testBoard1));
        List<BoardDTO> batchResult = boardService.getAllBoards(null);

        // Then: 두 메서드 모두 동일한 방식으로 첨부파일 설정
        // 문제점: dto.setAttachments(...), dto.setBoardFilePath(...) 로직이 두 곳에 중복됨
        assertNotNull(singleResult);
        assertNotNull(batchResult);
        assertFalse(batchResult.isEmpty());

        verify(attachmentFileService).getAttachments(FileTargetType.BOARD, boardId);
        verify(attachmentFileService).getAttachmentsBatch(eq(FileTargetType.BOARD), anyList());
    }

    @Test
    @DisplayName("문제 4: 단일/배치 조회 분리 - mapBoardWithDetails와 mapBoardsWithReactionsBatch가 다른 방식으로 구현")
    void testSingleBatchQuerySeparation() {
        // Given
        Long boardId = 1L;
        when(boardReactionRepository.countByBoardGroupByReactionType(boardId))
                .thenReturn(Collections.emptyList());
        when(boardReactionRepository.countByBoardsGroupByReactionType(anyList()))
                .thenReturn(Collections.emptyList());
        when(boardConverter.toDTO(any(Board.class))).thenReturn(testBoardDTO1);
        when(attachmentFileService.getAttachments(FileTargetType.BOARD, boardId))
                .thenReturn(Collections.emptyList());
        when(attachmentFileService.getAttachmentsBatch(eq(FileTargetType.BOARD), anyList()))
                .thenReturn(Collections.emptyMap());
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(testBoard1));

        // When: 단일 조회 (mapBoardWithDetails -> mapReactionCounts, mapAttachmentInfo 사용)
        BoardDTO singleResult = boardService.getBoard(boardId, null);

        // When: 배치 조회 (mapBoardsWithReactionsBatch -> getReactionCountsBatch 사용)
        when(boardRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc())
                .thenReturn(Collections.singletonList(testBoard1));
        List<BoardDTO> batchResult = boardService.getAllBoards(null);

        // Then: 단일 조회는 별도 메서드 호출, 배치 조회는 배치 메서드 호출
        // 문제점: 단일 조회도 배치 조회를 활용할 수 있음 (List.of(boardId))
        // 하지만 현재는 완전히 다른 방식으로 구현되어 있음

        // 단일 조회: 개별 메서드 호출
        verify(boardReactionRepository).countByBoardGroupByReactionType(boardId);
        verify(attachmentFileService).getAttachments(FileTargetType.BOARD, boardId);

        // 배치 조회: 배치 메서드 호출
        verify(boardReactionRepository).countByBoardsGroupByReactionType(anyList());
        verify(attachmentFileService).getAttachmentsBatch(eq(FileTargetType.BOARD), anyList());

        // 문제: 단일 조회가 배치 조회의 최적화 혜택을 받지 못함
        assertNotNull(singleResult);
        assertNotNull(batchResult);
    }

    @Test
    @DisplayName("문제 5: 관리자 조회에서 메모리 필터링 구조적 문제 - 1000개 조회 요청 후 메모리에서 필터링")
    void testAdminQueryMemoryFilteringPerformanceIssue() {
        // 주의: 이 테스트는 Mock을 사용하므로 실제 DB 조회 시간이 측정되지 않습니다.
        // 목적: 메모리 필터링의 구조적 문제를 검증 (1000개 조회 요청 vs 실제 필요한 데이터)
        // 실제 성능 측정은 통합 테스트(@SpringBootTest)에서 실제 DB를 사용하여 수행해야 합니다.

        // Given: 다양한 조건의 게시글 1000개 생성
        List<Board> allBoards = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            String category = i % 3 == 0 ? "FREE" : (i % 3 == 1 ? "NOTICE" : "QNA");
            ContentStatus status = i % 2 == 0 ? ContentStatus.ACTIVE : ContentStatus.BLINDED;
            boolean isDeleted = i % 10 == 0; // 10% 삭제됨

            Board board = Board.builder()
                    .idx((long) i)
                    .title("게시글 " + i)
                    .content("내용 " + i)
                    .category(category)
                    .status(status)
                    .isDeleted(isDeleted)
                    .user(testUser)
                    .build();
            allBoards.add(board);
        }

        // 필터링 조건: category=FREE, status=ACTIVE, deleted=false
        // 예상 결과: 약 300개 정도 (1000개 중 FREE가 약 333개, 그 중 ACTIVE가 약 166개, 삭제되지 않은 것)

        Pageable largePageable = PageRequest.of(0, 1000);
        Page<Board> largePage = new PageImpl<>(allBoards, largePageable, 1000);

        when(boardRepository.findAllByIsDeletedFalseForAdmin(largePageable))
                .thenReturn(largePage);
        when(boardReactionRepository.countByBoardsGroupByReactionType(anyList()))
                .thenReturn(Collections.emptyList());
        when(attachmentFileService.getAttachmentsBatch(eq(FileTargetType.BOARD), anyList()))
                .thenReturn(Collections.emptyMap());

        // BoardConverter mock 설정 - Answer를 사용하여 동적으로 DTO 생성
        // 이렇게 하면 실제로 사용되는 Board에 대해서만 DTO가 생성됨 (UnnecessaryStubbingException 방지)
        when(boardConverter.toDTO(any(Board.class))).thenAnswer(new Answer<BoardDTO>() {
            @Override
            public BoardDTO answer(InvocationOnMock invocation) throws Throwable {
                Board board = invocation.getArgument(0);
                return BoardDTO.builder()
                        .idx(board.getIdx())
                        .title(board.getTitle())
                        .content(board.getContent())
                        .category(board.getCategory())
                        .status(board.getStatus().name())
                        .userId(board.getUser().getIdx())
                        .username(board.getUser().getUsername())
                        .build();
            }
        });

        // When: 관리자 조회 (메모리 필터링)
        // 주의: 이 테스트는 Mock을 사용하므로 실제 DB 조회 시간이 측정되지 않습니다.
        // 실제 성능 측정은 통합 테스트(@SpringBootTest)에서 수행해야 합니다.
        var result = boardService.getAdminBoardsWithPaging(
                "ACTIVE", false, "FREE", null, 0, 20);

        // Then: 구조적 문제점 검증
        // 1. Repository에서 1000개를 모두 조회하는 쿼리가 호출됨
        verify(boardRepository).findAllByIsDeletedFalseForAdmin(largePageable);

        // 2. 메모리에서 필터링이 수행됨 (스트림 필터링)
        // 3. 페이징도 메모리에서 처리됨 (subList)

        assertNotNull(result);
        assertNotNull(result.getBoards());

        // 필터링된 결과가 20개 이하인지 확인 (페이징)
        assertTrue(result.getBoards().size() <= 20);

        // 구조적 문제점 검증:
        // - Repository에서 1000개를 모두 조회하는 쿼리가 호출됨 (실제 DB에서는 비용 발생)
        // - 메모리에서 필터링 수행 (DB 인덱스 활용 불가)
        // - 필터링 결과가 적을수록 더 비효율적 (예: 1000개 조회 → 5개만 필요)
        // - 실제 환경에서는 네트워크 전송, DB 쿼리 실행 시간 등 추가 비용 발생

        // 실제 성능 측정 예상:
        // - DB 레벨 필터링: 필요한 20개만 조회 → 빠름
        // - 메모리 필터링: 1000개 조회 → 느림 (특히 데이터가 많을수록)
        //
        // 개선 방안: getAdminBoardsWithPagingOptimized() 메서드처럼
        // DB 레벨에서 필터링 및 페이징 처리 (Specification 패턴 사용)

        System.out.println("=== 구조적 문제 검증 (실제 성능 측정 아님 - Mock 사용) ===");
        System.out.println("Repository 호출: findAllByIsDeletedFalseForAdmin(1000개)");
        System.out.println("필터링 조건: category=FREE, status=ACTIVE, deleted=false");
        System.out.println("조회 요청된 게시글 수: 1000개 (Repository에서)");
        System.out.println("실제 필요한 게시글 수: " + result.getBoards().size() + "개 (필터링 후)");
        System.out.println("불필요한 데이터 조회: " + (1000 - result.getBoards().size()) + "개");
        System.out.println("→ 실제 DB 환경에서는 이 차이만큼 네트워크 전송 및 쿼리 비용이 발생");
        System.out.println("→ 개선: DB 레벨 필터링으로 필요한 데이터만 조회");
    }

    @Test
    @DisplayName("문제 5-2: 관리자 조회 메모리 필터링 - 검색어 필터링 시 더 심각한 구조적 비효율성")
    void testAdminQueryMemoryFilteringWithSearchKeyword() {
        // 주의: 이 테스트는 Mock을 사용하므로 실제 DB 조회 시간이 측정되지 않습니다.
        // 목적: 검색어 필터링 시 메모리 필터링의 극단적인 비효율성 검증
        // 실제 성능 측정은 통합 테스트(@SpringBootTest)에서 실제 DB를 사용하여 수행해야 합니다.

        // Given: 1000개의 게시글 중 검색어와 일치하는 것은 5개만 존재
        List<Board> allBoards = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            String title = i < 5 ? "특정검색어 게시글 " + i : "일반 게시글 " + i;
            Board board = Board.builder()
                    .idx((long) i)
                    .title(title)
                    .content("내용 " + i)
                    .category("FREE")
                    .status(ContentStatus.ACTIVE)
                    .isDeleted(false)
                    .user(testUser)
                    .build();
            allBoards.add(board);
        }

        Pageable largePageable = PageRequest.of(0, 1000);
        Page<Board> largePage = new PageImpl<>(allBoards, largePageable, 1000);

        when(boardRepository.findAllByIsDeletedFalseForAdmin(largePageable))
                .thenReturn(largePage);
        when(boardReactionRepository.countByBoardsGroupByReactionType(anyList()))
                .thenReturn(Collections.emptyList());
        when(attachmentFileService.getAttachmentsBatch(eq(FileTargetType.BOARD), anyList()))
                .thenReturn(Collections.emptyMap());

        // BoardConverter mock 설정 - Answer를 사용하여 동적으로 DTO 생성
        // 이렇게 하면 실제로 사용되는 Board에 대해서만 DTO가 생성됨 (UnnecessaryStubbingException 방지)
        when(boardConverter.toDTO(any(Board.class))).thenAnswer(new Answer<BoardDTO>() {
            @Override
            public BoardDTO answer(InvocationOnMock invocation) throws Throwable {
                Board board = invocation.getArgument(0);
                return BoardDTO.builder()
                        .idx(board.getIdx())
                        .title(board.getTitle())
                        .content(board.getContent())
                        .category(board.getCategory())
                        .status(board.getStatus().name())
                        .userId(board.getUser().getIdx())
                        .username(board.getUser().getUsername())
                        .build();
            }
        });

        // When: 검색어로 필터링 (메모리에서)
        var result = boardService.getAdminBoardsWithPaging(
                "ACTIVE", false, "FREE", "특정검색어", 0, 20);

        // Then: 심각한 구조적 비효율성 검증
        // - Repository에서 1000개를 모두 조회하는 쿼리 호출
        // - 메모리에서 5개만 필터링
        // - 995개의 불필요한 데이터 조회 요청

        assertNotNull(result);
        verify(boardRepository).findAllByIsDeletedFalseForAdmin(largePageable);

        // 실제 필요한 데이터는 5개뿐이지만 1000개를 모두 조회하는 쿼리가 호출됨
        // 실제 DB 환경에서는 이 차이만큼 네트워크 전송 및 쿼리 비용 발생

        System.out.println("=== 구조적 비효율성 검증 (검색어 필터링) ===");
        System.out.println("Repository 호출: findAllByIsDeletedFalseForAdmin(1000개)");
        System.out.println("검색어: '특정검색어'");
        System.out.println("조회 요청된 게시글 수: 1000개 (Repository에서)");
        System.out.println("실제 필요한 게시글 수: " + result.getTotalCount() + "개 (필터링 후)");
        System.out.println("불필요한 데이터: " + (1000 - result.getTotalCount()) + "개");
        System.out.println("비효율성 비율: " + ((1000.0 - result.getTotalCount()) / 1000.0 * 100) + "%");
        System.out.println("→ 실제 DB 환경에서는 검색어 조건을 DB 쿼리에 포함하여 필요한 5개만 조회해야 함");
    }

    @Test
    @DisplayName("코드 중복으로 인한 유지보수 문제 시나리오 - 한 곳만 수정 시 불일치 발생")
    void testMaintenanceIssueDueToCodeDuplication() {
        // Given: 새로운 ReactionType이 추가되었다고 가정
        // 문제: mapReactionCounts()와 getReactionCountsBatch() 두 곳 모두 수정해야 함
        // 만약 한 곳만 수정하면 불일치 발생

        Long boardId = 1L;

        // 단일 조회용 결과 (LIKE, DISLIKE만 처리)
        List<Object[]> singleResults = Arrays.asList(
                new Object[] { boardId, ReactionType.LIKE, 5L },
                new Object[] { boardId, ReactionType.DISLIKE, 2L });

        // 배치 조회용 결과 (동일한 데이터)
        List<Object[]> batchResults = Arrays.asList(
                new Object[] { 1L, ReactionType.LIKE, 5L },
                new Object[] { 1L, ReactionType.DISLIKE, 2L });

        when(boardReactionRepository.countByBoardGroupByReactionType(boardId))
                .thenReturn(singleResults);
        when(boardReactionRepository.countByBoardsGroupByReactionType(anyList()))
                .thenReturn(batchResults);
        when(boardConverter.toDTO(any(Board.class))).thenReturn(testBoardDTO1);
        when(attachmentFileService.getAttachments(FileTargetType.BOARD, boardId))
                .thenReturn(Collections.emptyList());
        when(attachmentFileService.getAttachmentsBatch(eq(FileTargetType.BOARD), anyList()))
                .thenReturn(Collections.emptyMap());
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(testBoard1));
        when(boardRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc())
                .thenReturn(Collections.singletonList(testBoard1));

        // When: 단일 조회와 배치 조회 실행
        BoardDTO singleResult = boardService.getBoard(boardId, null);
        List<BoardDTO> batchResult = boardService.getAllBoards(null);

        // Then: 문제점
        // 만약 새로운 ReactionType이 추가되고, 한 곳만 수정하면:
        // - 단일 조회와 배치 조회의 결과가 달라질 수 있음
        // - 버그 발생 가능성 증가
        // - 테스트도 두 곳 모두 해야 함

        assertNotNull(singleResult);
        assertNotNull(batchResult);
        assertFalse(batchResult.isEmpty());

        // 현재는 동일한 결과를 반환하지만, 코드 중복으로 인해
        // 향후 수정 시 불일치 발생 가능성이 높음
    }
}
