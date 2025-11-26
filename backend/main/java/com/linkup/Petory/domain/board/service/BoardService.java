package com.linkup.Petory.domain.board.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.board.converter.BoardConverter;
import com.linkup.Petory.domain.common.ContentStatus;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.domain.board.dto.BoardDTO;
import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.repository.BoardRepository;
import com.linkup.Petory.domain.board.repository.BoardReactionRepository;
import com.linkup.Petory.domain.board.repository.BoardViewLogRepository;
import com.linkup.Petory.domain.board.entity.ReactionType;
import com.linkup.Petory.domain.board.entity.BoardViewLog;
import com.linkup.Petory.domain.file.dto.FileDTO;
import com.linkup.Petory.domain.file.entity.FileTargetType;
import com.linkup.Petory.domain.file.service.AttachmentFileService;
import org.springframework.util.StringUtils;

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

    // 단일 게시글 조회 + 조회수 증가
    @Cacheable(value = "boardDetail", key = "#idx")
    @Transactional
    public BoardDTO getBoard(long idx, Long viewerId) {
        Board board = boardRepository.findById(idx)
                .orElseThrow(() -> new RuntimeException("Board not found"));

        if (shouldIncrementView(board, viewerId)) {
            incrementViewCount(board);
        }

        return mapWithReactions(board);
    }

    // 게시글 생성
    @CacheEvict(value = "boardList", allEntries = true) // 전체 리스트 캐시 무효화 (전체/카테고리 모두)
    @Transactional
    public BoardDTO createBoard(BoardDTO dto) {
        Users user = usersRepository.findById(dto.getUserId())
                .orElseThrow(() -> {
                    log.error("❌ [BoardService.createBoard] User not found with userId: {}", dto.getUserId());
                    return new RuntimeException("User not found with userId: " + dto.getUserId());
                });

        Board board = Board.builder()
                .title(dto.getTitle())
                .content(dto.getContent())
                .category(dto.getCategory())
                .user(user)
                .build();

        Board saved = boardRepository.save(board);
        if (dto.getBoardFilePath() != null) {
            attachmentFileService.syncSingleAttachment(FileTargetType.BOARD, saved.getIdx(), dto.getBoardFilePath(),
                    null);
        }
        return mapWithReactions(saved);
    }

    // 게시글 수정
    @Caching(evict = {
            @CacheEvict(value = "boardDetail", key = "#idx"),
            @CacheEvict(value = "boardList", allEntries = true) // 카테고리 변경 가능하므로 안전하게 전체 무효화
    })
    @Transactional
    public BoardDTO updateBoard(long idx, BoardDTO dto) {
        Board board = boardRepository.findById(idx)
                .orElseThrow(() -> new RuntimeException("Board not found"));

        if (dto.getTitle() != null)
            board.setTitle(dto.getTitle());
        if (dto.getContent() != null)
            board.setContent(dto.getContent());
        if (dto.getCategory() != null)
            board.setCategory(dto.getCategory());
        Board updated = boardRepository.save(board);
        if (dto.getBoardFilePath() != null) {
            attachmentFileService.syncSingleAttachment(FileTargetType.BOARD, updated.getIdx(), dto.getBoardFilePath(),
                    null);
        }
        return mapWithReactions(updated);
    }

    // 게시글 삭제
    @Caching(evict = {
            @CacheEvict(value = "boardDetail", key = "#idx"),
            @CacheEvict(value = "boardList", allEntries = true) // 해당 카테고리 캐시 무효화를 위해 전체 무효화
    })
    @Transactional
    public void deleteBoard(long idx) {
        Board board = boardRepository.findById(idx)
                .orElseThrow(() -> new RuntimeException("Board not found"));

        // Soft delete: mark as DELETED and keep related data for audit/hard-delete
        // later
        board.setStatus(ContentStatus.DELETED);
        board.setIsDeleted(true);
        board.setDeletedAt(LocalDateTime.now());
        // Optionally mark child comments as deleted as well
        if (board.getComments() != null) {
            board.getComments().forEach(c -> {
                c.setStatus(ContentStatus.DELETED);
                c.setIsDeleted(true);
                c.setDeletedAt(LocalDateTime.now());
            });
        }
        boardRepository.saveAndFlush(board);
    }

    // 내 게시글 조회
    public List<BoardDTO> getMyBoards(long userId) {
        log.info("🔍 [BoardService.getMyBoards] userId: {}", userId);
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("❌ [BoardService.getMyBoards] User not found with userId: {}", userId);
                    return new RuntimeException("User not found with userId: " + userId);
                });

        List<Board> boards = boardRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user);

        if (boards.isEmpty()) {
            return new ArrayList<>();
        }

        // 배치 조회로 N+1 문제 해결
        return mapBoardsWithReactionsBatch(boards);
    }

    // 게시글 검색
    public List<BoardDTO> searchBoards(String keyword) {
        List<Board> boards = boardRepository.searchByKeyword(keyword);

        if (boards.isEmpty()) {
            return new ArrayList<>();
        }

        // 배치 조회로 N+1 문제 해결
        return mapBoardsWithReactionsBatch(boards);
    }

    /**
     * 단일 게시글에 반응 정보 매핑 (단일 조회용)
     */
    private BoardDTO mapWithReactions(Board board) {
        BoardDTO dto = boardConverter.toDTO(board);
        long likeCount = boardReactionRepository.countByBoardAndReactionType(board, ReactionType.LIKE);
        long dislikeCount = boardReactionRepository.countByBoardAndReactionType(board, ReactionType.DISLIKE);
        dto.setLikes(Math.toIntExact(likeCount));
        dto.setDislikes(Math.toIntExact(dislikeCount));
        List<FileDTO> attachments = attachmentFileService.getAttachments(FileTargetType.BOARD, board.getIdx());
        dto.setAttachments(attachments);
        dto.setBoardFilePath(extractPrimaryFileUrl(attachments));
        return dto;
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

                    // 좋아요/싫어요 카운트 설정
                    Map<ReactionType, Long> counts = reactionCountsMap.getOrDefault(
                            board.getIdx(), new HashMap<>());
                    dto.setLikes(Math.toIntExact(counts.getOrDefault(ReactionType.LIKE, 0L)));
                    dto.setDislikes(Math.toIntExact(counts.getOrDefault(ReactionType.DISLIKE, 0L)));

                    // 첨부파일 설정
                    List<FileDTO> attachments = attachmentsMap.getOrDefault(
                            board.getIdx(), new ArrayList<>());
                    dto.setAttachments(attachments);
                    dto.setBoardFilePath(extractPrimaryFileUrl(attachments));

                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 여러 게시글의 좋아요/싫어요 카운트를 배치로 조회
     * 반환값: Map<BoardId, Map<ReactionType, Count>>
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

            // 결과를 Map으로 변환: Map<BoardId, Map<ReactionType, Count>>
            for (Object[] result : results) {
                Long boardId = ((Number) result[0]).longValue();
                ReactionType reactionType = (ReactionType) result[1];
                Long count = ((Number) result[2]).longValue();

                countsMap.computeIfAbsent(boardId, k -> new HashMap<>())
                        .put(reactionType, count);
            }
        }

        return countsMap;
    }

    private void incrementViewCount(Board board) {
        Integer current = board.getViewCount();
        board.setViewCount((current == null ? 0 : current) + 1);
        boardRepository.save(board);
    }

    private boolean shouldIncrementView(Board board, Long viewerId) {
        if (viewerId == null) {
            return true;
        }

        Users viewer = usersRepository.findById(viewerId).orElse(null);
        if (viewer == null) {
            log.error("❌ [BoardService.shouldIncrementView] User not found with viewerId: {}", viewerId);
        }
        if (viewer == null) {
            return true;
        }

        boolean alreadyViewed = boardViewLogRepository.existsByBoardAndUser(board, viewer);
        if (alreadyViewed) {
            return false;
        }

        BoardViewLog log = BoardViewLog.builder()
                .board(board)
                .user(viewer)
                .build();
        boardViewLogRepository.save(log);
        return true;
    }

    private String extractPrimaryFileUrl(List<? extends FileDTO> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }
        FileDTO primary = attachments.get(0);
        if (primary == null) {
            return null;
        }
        if (StringUtils.hasText(primary.getDownloadUrl())) {
            return primary.getDownloadUrl();
        }
        return attachmentFileService.buildDownloadUrl(primary.getFilePath());
    }

    @Caching(evict = {
            @CacheEvict(value = "boardDetail", key = "#id"),
            @CacheEvict(value = "boardList", allEntries = true)
    })
    @Transactional
    public BoardDTO updateBoardStatus(long id, com.linkup.Petory.domain.common.ContentStatus status) {
        Board board = boardRepository.findById(id).orElseThrow(() -> new RuntimeException("Board not found"));
        // do not change isDeleted here
        board.setStatus(status);
        Board saved = boardRepository.save(board);
        return mapWithReactions(saved);
    }

    @Caching(evict = {
            @CacheEvict(value = "boardDetail", key = "#id"),
            @CacheEvict(value = "boardList", allEntries = true)
    })
    @Transactional
    public BoardDTO restoreBoard(long id) {
        Board board = boardRepository.findById(id).orElseThrow(() -> new RuntimeException("Board not found"));
        board.setIsDeleted(false);
        board.setDeletedAt(null);
        if (board.getStatus() == com.linkup.Petory.domain.common.ContentStatus.DELETED) {
            board.setStatus(com.linkup.Petory.domain.common.ContentStatus.ACTIVE);
        }
        Board saved = boardRepository.save(board);
        return mapWithReactions(saved);
    }
}
