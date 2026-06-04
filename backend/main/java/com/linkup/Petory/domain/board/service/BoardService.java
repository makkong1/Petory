package com.linkup.Petory.domain.board.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.board.converter.BoardConverter;
import com.linkup.Petory.domain.board.dto.BoardDTO;
import com.linkup.Petory.domain.board.dto.BoardPageResponseDTO;
import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.entity.ReactionType;
import com.linkup.Petory.domain.board.exception.BoardForbiddenException;
import com.linkup.Petory.domain.board.exception.BoardNotFoundException;
import com.linkup.Petory.domain.board.repository.BoardReactionRepository;
import com.linkup.Petory.domain.board.repository.BoardRepository;
import com.linkup.Petory.domain.board.repository.BoardViewLogRepository;
import com.linkup.Petory.domain.board.repository.CommentRepository;
import com.linkup.Petory.domain.common.ContentStatus;
import com.linkup.Petory.domain.file.dto.FileDTO;
import com.linkup.Petory.domain.file.entity.FileTargetType;
import com.linkup.Petory.domain.file.service.AttachmentFileService;
import com.linkup.Petory.domain.petRecommendation.event.CommunityPostCreatedEvent;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.exception.EmailVerificationRequiredException;
import com.linkup.Petory.domain.user.exception.UserNotFoundException;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.global.security.RoleConstants;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class BoardService {

    private final BoardRepository boardRepository;
    private final UsersRepository usersRepository;
    private final BoardReactionRepository boardReactionRepository;
    private final BoardViewLogRepository boardViewLogRepository;
    private final AttachmentFileService attachmentFileService;
    private final BoardConverter boardConverter;
    private final ApplicationEventPublisher eventPublisher;
    private final CommentRepository commentRepository;

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals(RoleConstants.ROLE_ADMIN) || a.equals(RoleConstants.ROLE_MASTER));
    }

    private void assertBoardOwner(Users boardOwner) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!isAdmin() && (auth == null || !auth.getName().equals(boardOwner.getId()))) {
            throw BoardForbiddenException.boardOwnerOnly();
        }
    }

    // 전체 게시글 조회 (카테고리 필터링 포함)
    // 캐시 임시 비활성화 - 개발 중 데이터 동기화 문제 해결
    // @Cacheable(value = "boardList", key = "#category != null ? #category :
    // 'ALL'")
    public List<BoardDTO> getAllBoards(String category) {
        List<Board> boards;

        if (category != null && !category.equals("ALL")) { // 카테고리 필터링
            boards = boardRepository.findByCategoryAndIsDeletedFalseOrderByCreatedAtDesc(category);
        } else {
            boards = boardRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc(); // 전체
        }

        if (boards.isEmpty()) {
            return new ArrayList<>();
        }

        // 배치 조회로 N+1 문제 해결
        return mapBoardsWithReactionsBatch(boards);
    }

    // 전체 게시글 조회 (페이징 지원)
    public BoardPageResponseDTO getAllBoardsWithPaging(String category, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Board> boardPage;

        if (category != null && !category.equals("ALL")) { // 카테고리 필터링
            boardPage = boardRepository.findByCategoryAndIsDeletedFalseOrderByCreatedAtDesc(category, pageable);
        } else {
            boardPage = boardRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc(pageable); // 전체
        }

        if (boardPage.isEmpty()) {
            return new BoardPageResponseDTO(
                    new ArrayList<>(),
                    0,
                    0,
                    page,
                    size,
                    false,
                    false);
        }

        // 배치 조회로 N+1 문제 해결
        List<BoardDTO> boardDTOs = mapBoardsWithReactionsBatch(boardPage.getContent());

        return new BoardPageResponseDTO(
                boardDTOs,
                boardPage.getTotalElements(),
                boardPage.getTotalPages(),
                page,
                size,
                boardPage.hasNext(),
                boardPage.hasPrevious());
    }

    /**
     * 관리자용 단일 게시글 조회 (조회수 증가 없음) [리팩토링] listBoards 전체 로드 제거 → 단건 조회 API로 대체 -
     * AdminBoardController에서 사용 - 삭제된 게시글도 조회 가능
     */
    /**
     * [리팩토링] Fetch Join - Board + User 1회 쿼리
     */
    @Transactional(readOnly = true)
    public BoardDTO getBoardForAdmin(long idx) {
        Board board = boardRepository.findByIdWithUser(idx)
                .orElseThrow(() -> new BoardNotFoundException());
        return mapBoardWithDetails(board);
    }

    // 단일 게시글 조회 + 조회수 증가
    // [리팩토링] Fetch Join - Board + User 1회 쿼리 / @Cacheable 제거: 조회수 실시간 반영
    @Transactional
    public BoardDTO getBoard(long idx, Long viewerId) {
        Board board = boardRepository.findByIdWithUser(idx)
                .orElseThrow(() -> new BoardNotFoundException());

        boolean incremented = shouldIncrementView(board, viewerId);
        if (incremented) {
            boardRepository.incrementViewCount(board.getIdx());
        }

        BoardDTO dto = mapBoardWithDetails(board);
        if (incremented) {
            dto.setViews((board.getViewCount() != null ? board.getViewCount() : 0) + 1);
        }
        return dto;
    }

    // 게시글 생성
    @CacheEvict(value = "boardList", allEntries = true) // 전체 리스트 캐시 무효화 (전체/카테고리 모두)
    @Transactional
    public BoardDTO createBoard(BoardDTO dto) {
        String loginId = SecurityContextHolder.getContext().getAuthentication().getName();
        Users user = usersRepository.findActiveByIdString(loginId)
                .orElseThrow(() -> new UserNotFoundException());

        Board board = Board.builder()
                .title(dto.getTitle())
                .content(dto.getContent())
                .category(dto.getCategory())
                .user(user)
                .build();

        Board saved = boardRepository.save(board);
        eventPublisher.publishEvent(new CommunityPostCreatedEvent(
                this, user.getIdx(), saved.getIdx(),
                saved.getTitle() + " " + saved.getContent()));
        if (dto.getBoardFilePath() != null) {
            attachmentFileService.syncSingleAttachment(FileTargetType.BOARD, saved.getIdx(), dto.getBoardFilePath(),
                    null);
        }
        return mapBoardWithDetails(saved);
    }

    // 게시글 수정
    @Caching(evict = {
        @CacheEvict(value = "boardDetail", key = "#p0"),
        @CacheEvict(value = "boardList", allEntries = true) // 카테고리 변경 가능하므로 안전하게 전체 무효화
    })
    @Transactional
    public BoardDTO updateBoard(long idx, BoardDTO dto) {
        Board board = boardRepository.findByIdWithUser(idx)
                .orElseThrow(() -> new BoardNotFoundException());

        // 작성자 또는 관리자만 수정 가능
        assertBoardOwner(board.getUser());

        // 이메일 인증 확인
        Users user = board.getUser();
        if (user.getEmailVerified() == null || !user.getEmailVerified()) {
            throw new EmailVerificationRequiredException(
                    "게시글 수정을 위해 이메일 인증이 필요합니다.",
                    com.linkup.Petory.domain.user.entity.EmailVerificationPurpose.BOARD_EDIT);
        }

        if (dto.getTitle() != null) {
            board.setTitle(dto.getTitle());
        }
        if (dto.getContent() != null) {
            board.setContent(dto.getContent());
        }
        if (dto.getCategory() != null) {
            board.setCategory(dto.getCategory());
        }
        Board updated = boardRepository.save(board);
        if (dto.getBoardFilePath() != null) {
            attachmentFileService.syncSingleAttachment(FileTargetType.BOARD, updated.getIdx(), dto.getBoardFilePath(),
                    null);
        }
        return mapBoardWithDetails(updated);
    }

    // 게시글 삭제
    @Caching(evict = {
        @CacheEvict(value = "boardDetail", key = "#p0"),
        @CacheEvict(value = "boardList", allEntries = true) // 해당 카테고리 캐시 무효화를 위해 전체 무효화
    })
    @Transactional
    public void deleteBoard(long idx) {
        Board board = boardRepository.findByIdWithUser(idx)
                .orElseThrow(() -> new BoardNotFoundException());

        // 작성자 또는 관리자만 삭제 가능
        assertBoardOwner(board.getUser());

        // 이메일 인증 확인 (소유권 확인 후이므로 올바른 사용자 기준)
        Users user = board.getUser();
        if (user.getEmailVerified() == null || !user.getEmailVerified()) {
            throw new EmailVerificationRequiredException(
                    "게시글 삭제를 위해 이메일 인증이 필요합니다.",
                    com.linkup.Petory.domain.user.entity.EmailVerificationPurpose.BOARD_EDIT);
        }

        board.softDelete();
        boardRepository.saveAndFlush(board);
        commentRepository.softDeleteByBoardIdx(board.getIdx(), LocalDateTime.now());
    }

    // 내 게시글 조회
    public List<BoardDTO> getMyBoards(long userId) {
        log.info("🔍 [BoardService.getMyBoards] userId: {}", userId);
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("❌ [BoardService.getMyBoards] User not found with userId: {}", userId);
                    return new UserNotFoundException("사용자를 찾을 수 없습니다. userId: " + userId);
                });

        List<Board> boards = boardRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user);

        if (boards.isEmpty()) {
            return new ArrayList<>();
        }

        // 배치 조회로 N+1 문제 해결
        return mapBoardsWithReactionsBatch(boards);
    }

    // 게시글 검색 (페이징 지원)
    public BoardPageResponseDTO searchBoardsWithPaging(String keyword, String searchType, int page, int size) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return new BoardPageResponseDTO(
                    new ArrayList<>(),
                    0,
                    0,
                    page,
                    size,
                    false,
                    false);
        }

        String trimmedKeyword = keyword.trim();
        Pageable pageable = PageRequest.of(page, size);
        Page<Board> boardPage;

        // 검색 타입에 따라 다른 쿼리 실행
        // B: TITLE/CONTENT 개별 검색 제거 → TITLE_CONTENT(FULLTEXT)로 통합
        // C: NICKNAME 검색 최적화 → JOIN 쿼리 1번으로 DB 레벨 페이징
        switch (searchType != null ? searchType.toUpperCase() : "TITLE_CONTENT") {
            case "NICKNAME":
                // 작성자 닉네임으로 검색 - JOIN 쿼리로 최적화 (2 Query → 1 Query)
                log.info("🔍 [BoardService.searchBoardsWithPaging] 닉네임 검색: keyword = {}", trimmedKeyword);
                boardPage = boardRepository.searchByNicknameWithPaging(trimmedKeyword, pageable);
                break;
            case "TITLE_CONTENT":
            default:
                // 제목+내용 통합 검색 (FULLTEXT 인덱스 활용)
                boardPage = boardRepository.searchByKeywordWithPaging(trimmedKeyword, pageable);
                break;
        }

        if (boardPage.isEmpty()) {
            return new BoardPageResponseDTO(
                    new ArrayList<>(),
                    0,
                    0,
                    page,
                    size,
                    false,
                    false);
        }

        // 배치 조회로 N+1 문제 해결
        List<BoardDTO> boardDTOs = mapBoardsWithReactionsBatch(boardPage.getContent());

        return new BoardPageResponseDTO(
                boardDTOs,
                boardPage.getTotalElements(),
                boardPage.getTotalPages(),
                page,
                size,
                boardPage.hasNext(),
                boardPage.hasPrevious());
    }

    /**
     * 단일 게시글에 상세 정보 매핑 (반응 정보, 첨부파일 포함) 배치 조회를 활용하여 최적화
     */
    private BoardDTO mapBoardWithDetails(Board board) {
        List<BoardDTO> results = mapBoardsWithReactionsBatch(List.of(board));
        return results.isEmpty() ? boardConverter.toDTO(board) : results.get(0);
    }

    /**
     * 배치 조회 결과(Object[])를 Map으로 변환
     *
     * @param results Repository에서 반환된 Object[] 리스트 [boardId, reactionType,
     * count]
     * @return Map<BoardId, Map<ReactionType, Count>>
     */
    private Map<Long, Map<ReactionType, Long>> parseBatchReactionCountResults(List<Object[]> results) {
        Map<Long, Map<ReactionType, Long>> countsMap = new HashMap<>();
        for (Object[] result : results) {
            Long boardId = ((Number) result[0]).longValue();
            ReactionType reactionType = (ReactionType) result[1];
            Long count = ((Number) result[2]).longValue();

            countsMap.computeIfAbsent(boardId, k -> new HashMap<>())
                    .put(reactionType, count);
        }
        return countsMap;
    }

    /**
     * BoardDTO에 반응 카운트 적용
     *
     * @param dto 대상 BoardDTO
     * @param counts Map<ReactionType, Count>
     */
    private void applyReactionCounts(BoardDTO dto, Map<ReactionType, Long> counts) {
        dto.setLikes(Math.toIntExact(counts.getOrDefault(ReactionType.LIKE, 0L)));
        dto.setDislikes(Math.toIntExact(counts.getOrDefault(ReactionType.DISLIKE, 0L)));
    }

    /**
     * BoardDTO에 첨부파일 정보 적용 (배치 조회용)
     *
     * @param dto 대상 BoardDTO
     * @param boardId 게시글 ID
     * @param attachmentsMap Map<BoardId, List<FileDTO>>
     */
    private void applyAttachmentInfo(BoardDTO dto, Long boardId, Map<Long, List<FileDTO>> attachmentsMap) {
        List<FileDTO> attachments = attachmentsMap.getOrDefault(boardId, new ArrayList<>());
        dto.setAttachments(attachments);
        dto.setBoardFilePath(attachmentFileService.extractPrimaryFileUrl(attachments));
    }

    /**
     * 여러 게시글에 반응 정보를 배치로 매핑 (목록 조회용 - N+1 문제 해결)
     */
    private List<BoardDTO> mapBoardsWithReactionsBatch(List<Board> boards) {
        if (boards.isEmpty()) {
            return new ArrayList<>();
        }

        // 게시글 ID 목록 추출
        List<Long> boardIds = boards.stream()
                .map(Board::getIdx)
                .collect(Collectors.toList());

        // 1. 좋아요/싫어요 카운트 배치 조회
        Map<Long, Map<ReactionType, Long>> reactionCountsMap = getReactionCountsBatch(boardIds);

        // 2. 첨부파일 배치 조회
        Map<Long, List<FileDTO>> attachmentsMap = attachmentFileService.getAttachmentsBatch(
                FileTargetType.BOARD, boardIds);

        // 3. 게시글 DTO 변환 및 반응 정보 매핑
        return boards.stream()
                .map(board -> {
                    BoardDTO dto = boardConverter.toDTO(board);

                    // 좋아요/싫어요 카운트 설정 (공통 메서드 사용)
                    Map<ReactionType, Long> counts = reactionCountsMap.getOrDefault(
                            board.getIdx(), new HashMap<>());
                    applyReactionCounts(dto, counts);

                    // 첨부파일 설정 (공통 메서드 사용)
                    applyAttachmentInfo(dto, board.getIdx(), attachmentsMap);

                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 여러 게시글의 좋아요/싫어요 카운트를 배치로 조회 반환값: Map<BoardId, Map<ReactionType, Count>>
     * IN 절 크기 제한을 위해 배치 단위로 나누어 조회
     */
    private Map<Long, Map<ReactionType, Long>> getReactionCountsBatch(List<Long> boardIds) {
        if (boardIds.isEmpty()) {
            return new HashMap<>();
        }

        // IN 절 크기 제한 (일반적으로 1000개 이하 권장)
        final int BATCH_SIZE = 500;
        Map<Long, Map<ReactionType, Long>> countsMap = new HashMap<>();

        // boardIds를 배치 단위로 나누어 처리
        for (int i = 0; i < boardIds.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, boardIds.size());
            List<Long> batch = boardIds.subList(i, end);

            List<Object[]> results = boardReactionRepository.countByBoardsGroupByReactionType(batch);

            // 결과를 Map으로 변환: Map<BoardId, Map<ReactionType, Count>> (공통 메서드 사용)
            Map<Long, Map<ReactionType, Long>> batchCounts = parseBatchReactionCountResults(results);
            countsMap.putAll(batchCounts);
        }

        return countsMap;
    }

    private boolean shouldIncrementView(Board board, Long viewerId) {
        if (viewerId == null) {
            return true;
        }
        Users viewer = usersRepository.findById(viewerId).orElse(null);
        if (viewer == null) {
            return true;
        }
        return boardViewLogRepository.insertIgnore(board.getIdx(), viewer.getIdx()) > 0;
    }

    /**
     * 게시글 상태 변경 (관리자용) - AdminBoardController에서 사용
     */
    @Caching(evict = {
        @CacheEvict(value = "boardDetail", key = "#p0"),
        @CacheEvict(value = "boardList", allEntries = true)
    })
    @Transactional
    public BoardDTO updateBoardStatus(long id, ContentStatus status) {
        Board board = boardRepository.findByIdWithUser(id).orElseThrow(() -> new BoardNotFoundException());
        board.changeStatus(status);
        Board saved = boardRepository.save(board);
        return mapBoardWithDetails(saved);
    }

    /**
     * 게시글 복구 (관리자용) - AdminBoardController에서 사용
     */
    @Caching(evict = {
        @CacheEvict(value = "boardDetail", key = "#p0"),
        @CacheEvict(value = "boardList", allEntries = true)
    })
    @Transactional
    public BoardDTO restoreBoard(long id) {
        Board board = boardRepository.findByIdWithUser(id).orElseThrow(() -> new BoardNotFoundException());
        board.restore();
        Board saved = boardRepository.save(board);
        return mapBoardWithDetails(saved);
    }

    /**
     * 관리자용 게시글 조회 (페이징 + 필터링 지원) - DB 레벨 필터링 버전 [리팩토링] getAdminBoardsWithPaging
     * 메모리 필터링 → Specification + DB 페이징
     *
     * 개선사항: - 메모리 필터링 → DB 레벨 필터링 (Specification 패턴 사용) - 불필요한 데이터 조회 제거 - 인덱스
     * 활용 가능 - 성능 대폭 개선
     */
    public BoardPageResponseDTO getAdminBoardsWithPagingOptimized(
            String status, Boolean deleted, String category, String q, int page, int size) {

        // null에서 시작해 파라미터가 있을 때만 조건을 .and()로 붙인다.
        // 파라미터가 전혀 없으면 spec이 null인 채로 findAll에 전달되고,
        // Spring Data JPA는 null spec을 "조건 없음(전체 조회)"으로 처리한다.
        Specification<Board> spec = null;

        // 삭제 여부 필터 — deleted=true면 삭제된 것만, false면 활성 게시글만
        if (deleted != null) {
            Specification<Board> deletedSpec = (root, query, cb) -> cb.equal(root.get("isDeleted"), deleted);
            spec = spec == null ? deletedSpec : spec.and(deletedSpec);
        }

        // 카테고리 필터 — "ALL"이면 조건 미적용
        if (category != null && !category.equals("ALL")) {
            Specification<Board> categorySpec = (root, query, cb) -> cb.equal(root.get("category"), category);
            spec = spec == null ? categorySpec : spec.and(categorySpec);
        }

        // 상태 필터 — 잘못된 status 문자열은 예외 없이 무시하고 전체 조회
        if (status != null && !status.equals("ALL")) {
            try {
                ContentStatus contentStatus = ContentStatus.valueOf(status.toUpperCase());
                Specification<Board> statusSpec = (root, query, cb) -> cb.equal(root.get("status"), contentStatus);
                spec = spec == null ? statusSpec : spec.and(statusSpec);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status value: {}", status);
            }
        }

        // 검색어 필터 — 제목·내용은 FULLTEXT MATCH...AGAINST, 작성자명은 접두사 LIKE
        // - title·content: idx_board_title_content FULLTEXT 인덱스 사용 → 풀스캔 없음
        // - username: users.username unique 인덱스로 접두사 LIKE 'q%' 사용
        //   (앞 와일드카드 없애야 인덱스 range scan 가능)
        if (q != null && !q.isBlank()) {
            final String trimmed = q.trim();
            Specification<Board> searchSpec = (root, query, cb) -> {
                Join<Board, Users> userJoin = root.join("user", JoinType.LEFT);
                return cb.or(
                        cb.gt(
                                cb.function("MATCH", Double.class,
                                        root.get("title"), root.get("content"),
                                        cb.literal(trimmed)),
                                0.0),
                        cb.like(cb.lower(userJoin.get("username")), trimmed.toLowerCase() + "%"));
            };
            spec = spec == null ? searchSpec : spec.and(searchSpec);
        }

        // Board → DTO 변환 시 board.getUser() 접근으로 발생하는 N+1 방지용 FETCH JOIN.
        // JPA 페이징은 내부적으로 데이터 쿼리(SELECT)와 전체 개수 쿼리(COUNT) 두 번을 날리는데,
        // COUNT 쿼리에 fetch를 포함하면 Hibernate가 예외를 던진다.
        // getResultType()이 Long이면 COUNT 쿼리이므로 fetch를 건너뛴다.
        Specification<Board> userFetchSpec = (root, query, cb) -> {
            if (Long.class != query.getResultType()) {
                root.fetch("user", JoinType.LEFT);
            }
            return cb.conjunction(); // 이 spec 자체는 WHERE 조건에 아무것도 추가하지 않음
        };
        spec = spec == null ? userFetchSpec : spec.and(userFetchSpec);

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));

        // 위에서 조립한 spec을 DB에 위임 — 메모리 필터링 없이 DB 레벨에서 필터·페이징 처리
        Page<Board> boardPage = boardRepository.findAll(spec, pageable);

        if (boardPage.isEmpty()) {
            return new BoardPageResponseDTO(
                    new ArrayList<>(),
                    0,
                    0,
                    page,
                    size,
                    false,
                    false);
        }

        // 리액션 카운트(likeCount, dislikeCount) N+1 방지 — 게시글 ID 목록으로 IN 배치 조회 후 Map으로 합산
        List<BoardDTO> boardDTOs = mapBoardsWithReactionsBatch(boardPage.getContent());

        return new BoardPageResponseDTO(
                boardDTOs,
                boardPage.getTotalElements(),
                boardPage.getTotalPages(),
                page,
                size,
                boardPage.hasNext(),
                boardPage.hasPrevious());
    }
}
