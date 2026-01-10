package com.linkup.Petory.domain.board.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.linkup.Petory.domain.board.converter.MissingPetConverter;
import com.linkup.Petory.domain.board.dto.MissingPetBoardDTO;
import com.linkup.Petory.domain.board.entity.MissingPetBoard;
import com.linkup.Petory.domain.board.entity.MissingPetStatus;
import com.linkup.Petory.domain.board.repository.MissingPetBoardRepository;
import com.linkup.Petory.domain.file.dto.FileDTO;
import com.linkup.Petory.domain.file.entity.FileTargetType;
import com.linkup.Petory.domain.file.service.AttachmentFileService;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MissingPetBoardService {

    private final MissingPetBoardRepository boardRepository;
    private final MissingPetCommentService commentService;
    private final UsersRepository usersRepository;
    private final MissingPetConverter missingPetConverter;
    private final AttachmentFileService attachmentFileService;

    /**
     * 실종 제보 목록 조회
     * 엔드포인트: GET /api/missing-pets
     * - 상태별 필터링 지원 (status 파라미터)
     * - 최신순 정렬
     * - 게시글 파일 배치 조회 (N+1 문제 해결)
     * - 댓글은 포함하지 않음 (조인 폭발 방지)
     */
    public List<MissingPetBoardDTO> getBoards(MissingPetStatus status) {
        // 성능 측정 시작
        long startTime = System.currentTimeMillis();
        Runtime runtime = Runtime.getRuntime();
        long startMemory = runtime.totalMemory() - runtime.freeMemory();

        log.info("=== [성능 측정] 게시글 목록 조회 시작 ===");
        log.info("  - 상태 필터: {}", status != null ? status : "전체");

        // 게시글 + 작성자만 조회 (댓글 제외 - 조인 폭발 방지)
        List<MissingPetBoard> boards = status == null
                ? boardRepository.findAllByOrderByCreatedAtDesc()
                : boardRepository.findByStatusOrderByCreatedAtDesc(status);

        // 게시글 ID 목록 추출
        List<Long> boardIds = boards.stream()
                .map(MissingPetBoard::getIdx)
                .collect(Collectors.toList());

        // 파일 배치 조회 (N+1 문제 해결)
        Map<Long, List<FileDTO>> filesByBoardId = attachmentFileService.getAttachmentsBatch(
                FileTargetType.MISSING_PET, boardIds);

        // DTO 변환 (파일 정보 포함, 댓글은 빈 리스트)
        List<MissingPetBoardDTO> result = boards.stream()
                .map(board -> {
                    MissingPetBoardDTO dto = mapBoardWithAttachmentsFromBatch(board, filesByBoardId);
                    // 댓글은 빈 리스트로 설정 (목록에서는 댓글 불필요)
                    dto.setComments(List.of());
                    return dto;
                })
                .collect(Collectors.toList());

        // 성능 측정 종료
        long endTime = System.currentTimeMillis();
        long endMemory = runtime.totalMemory() - runtime.freeMemory();
        long executionTime = endTime - startTime;
        long memoryUsed = endMemory - startMemory;
        long currentMemoryMB = endMemory / 1024 / 1024;
        long maxMemoryMB = runtime.maxMemory() / 1024 / 1024;
        double avgTimePerBoard = boards.isEmpty() ? 0 : (double) executionTime / boards.size();

        // 성능 측정 로그 출력
        log.info("=== [성능 측정] 게시글 목록 조회 완료 ===");
        log.info("  - 조회된 게시글 수: {}개", boards.size());
        log.info("  - 실행 시간: {}ms", executionTime);
        log.info("  - 평균 게시글당 시간: {}ms", String.format("%.2f", avgTimePerBoard));
        log.info("  - 상태: {}", status != null ? status : "전체");
        log.info("  - 메모리 사용량: {}MB (증가: {}MB)", currentMemoryMB, memoryUsed / 1024 / 1024);
        log.info("  - 최대 메모리: {}MB", maxMemoryMB);

        return result;
    }

    /**
     * 실종 제보 상세 조회
     * 엔드포인트: GET /api/missing-pets/{id}
     * - 게시글, 작성자 정보만 조회 (댓글은 별도 API로 조회)
     * - 게시글 파일 조회
     * 
     * 참고: 댓글은 GET /api/missing-pets/{id}/comments 로 별도 조회
     * 이유: 댓글 조인 시 조인 폭발 방지 및 페이징 가능
     */
    public MissingPetBoardDTO getBoard(Long id) {
        // 성능 측정 시작
        long startTime = System.currentTimeMillis();
        Runtime runtime = Runtime.getRuntime();
        long startMemory = runtime.totalMemory() - runtime.freeMemory();

        // 게시글 + 작성자만 조회 (댓글 제외)
        MissingPetBoard board = boardRepository.findByIdWithUser(id)
                .orElseThrow(() -> new IllegalArgumentException("Missing pet board not found"));

        // 삭제된 게시글인지 확인
        if (board.getIsDeleted()) {
            throw new IllegalArgumentException("Missing pet board not found");
        }

        // 게시글 파일 조회
        List<FileDTO> boardAttachments = attachmentFileService.getAttachments(
                FileTargetType.MISSING_PET, board.getIdx());

        // 댓글 수만 조회 (댓글 목록은 별도 API로)
        int commentCount = commentService.getCommentCount(board);

        // DTO 변환 (댓글은 포함하지 않음)
        MissingPetBoardDTO dto = missingPetConverter.toBoardDTO(board);
        dto.setAttachments(boardAttachments);
        dto.setImageUrl(extractPrimaryFileUrl(boardAttachments));
        // 댓글은 빈 리스트로 설정, 댓글 수는 실제 카운트 사용
        dto.setComments(List.of());
        dto.setCommentCount(commentCount);

        // 성능 측정 종료
        long endTime = System.currentTimeMillis();
        long endMemory = runtime.totalMemory() - runtime.freeMemory();
        long executionTime = endTime - startTime;
        long memoryUsed = endMemory - startMemory;
        long currentMemoryMB = endMemory / 1024 / 1024;
        long maxMemoryMB = runtime.maxMemory() / 1024 / 1024;

        // 성능 측정 로그 출력
        log.info("=== [성능 측정] 게시글 상세 조회 완료 ===");
        log.info("  - 게시글 ID: {}", id);
        log.info("  - 실행 시간: {}ms", executionTime);
        if (commentCount > 0) {
            double avgTimePerComment = (double) executionTime / commentCount;
            log.info("  - 평균 댓글당 시간: {}ms", String.format("%.2f", avgTimePerComment));
        }
        log.info("  - 조회된 댓글 수: {}개", commentCount);
        log.info("  - 메모리 사용량: {}MB (증가: {}MB)", currentMemoryMB, memoryUsed / 1024 / 1024);
        log.info("  - 최대 메모리: {}MB", maxMemoryMB);

        return dto;
    }

    /**
     * 실종 제보 작성
     * 엔드포인트: POST /api/missing-pets
     * - 이메일 인증 필수
     * - 게시글 이미지 업로드 지원
     */
    @Transactional
    public MissingPetBoardDTO createBoard(MissingPetBoardDTO dto) {
        Users user = usersRepository.findById(dto.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // 이메일 인증 확인
        if (user.getEmailVerified() == null || !user.getEmailVerified()) {
            throw new com.linkup.Petory.domain.user.exception.EmailVerificationRequiredException(
                    "실종 제보 작성을 위해 이메일 인증이 필요합니다.",
                    com.linkup.Petory.domain.user.entity.EmailVerificationPurpose.MISSING_PET);
        }

        MissingPetBoard board = MissingPetBoard.builder()
                .user(user)
                .title(dto.getTitle())
                .content(dto.getContent())
                .petName(dto.getPetName())
                .species(dto.getSpecies())
                .breed(dto.getBreed())
                .gender(dto.getGender())
                .age(dto.getAge())
                .color(dto.getColor())
                .lostDate(dto.getLostDate())
                .lostLocation(dto.getLostLocation())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .status(dto.getStatus())
                .build();

        MissingPetBoard saved = boardRepository.save(board);
        if (dto.getImageUrl() != null) {
            attachmentFileService.syncSingleAttachment(FileTargetType.MISSING_PET, saved.getIdx(), dto.getImageUrl(),
                    null);
        }
        return mapBoardWithAttachments(saved);
    }

    /**
     * 실종 제보 수정
     * 엔드포인트: PUT /api/missing-pets/{id}
     * - 이메일 인증 필수
     * - 게시글 이미지 수정 지원
     */
    @Transactional
    public MissingPetBoardDTO updateBoard(Long id, MissingPetBoardDTO dto) {
        MissingPetBoard board = boardRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Missing pet board not found"));

        // 이메일 인증 확인
        Users user = board.getUser();
        if (user.getEmailVerified() == null || !user.getEmailVerified()) {
            throw new com.linkup.Petory.domain.user.exception.EmailVerificationRequiredException(
                    "실종 제보 수정을 위해 이메일 인증이 필요합니다.",
                    com.linkup.Petory.domain.user.entity.EmailVerificationPurpose.MISSING_PET);
        }

        if (StringUtils.hasText(dto.getTitle())) {
            board.setTitle(dto.getTitle());
        }
        if (dto.getContent() != null) {
            board.setContent(dto.getContent());
        }
        if (dto.getPetName() != null) {
            board.setPetName(dto.getPetName());
        }
        if (dto.getSpecies() != null) {
            board.setSpecies(dto.getSpecies());
        }
        if (dto.getBreed() != null) {
            board.setBreed(dto.getBreed());
        }
        if (dto.getGender() != null) {
            board.setGender(dto.getGender());
        }
        if (dto.getAge() != null) {
            board.setAge(dto.getAge());
        }
        if (dto.getColor() != null) {
            board.setColor(dto.getColor());
        }
        if (dto.getLostDate() != null) {
            board.setLostDate(dto.getLostDate());
        }
        if (dto.getLostLocation() != null) {
            board.setLostLocation(dto.getLostLocation());
        }
        if (dto.getLatitude() != null) {
            board.setLatitude(dto.getLatitude());
        }
        if (dto.getLongitude() != null) {
            board.setLongitude(dto.getLongitude());
        }
        if (dto.getStatus() != null) {
            board.setStatus(dto.getStatus());
        }
        if (dto.getImageUrl() != null) {
            attachmentFileService.syncSingleAttachment(FileTargetType.MISSING_PET, board.getIdx(), dto.getImageUrl(),
                    null);
        }

        return mapBoardWithAttachments(board);
    }

    /**
     * 실종 제보 상태 변경
     * 엔드포인트: PATCH /api/missing-pets/{id}/status
     * - 상태: MISSING(실종), FOUND(발견), CLOSED(종료)
     */
    @Transactional
    public MissingPetBoardDTO updateStatus(Long id, MissingPetStatus status) {
        MissingPetBoard board = boardRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Missing pet board not found"));
        board.setStatus(status);
        return mapBoardWithAttachments(board);
    }

    /**
     * 실종 제보 삭제 (소프트 삭제)
     * 엔드포인트: DELETE /api/missing-pets/{id}
     * - 이메일 인증 필수
     * - 게시글과 관련 댓글 모두 소프트 삭제
     */
    @Transactional
    public void deleteBoard(Long id) {
        MissingPetBoard board = boardRepository.findByIdWithUser(id)
                .orElseThrow(() -> new IllegalArgumentException("Missing pet board not found"));

        // 이메일 인증 확인
        Users user = board.getUser();
        if (user.getEmailVerified() == null || !user.getEmailVerified()) {
            throw new com.linkup.Petory.domain.user.exception.EmailVerificationRequiredException(
                    "실종 제보 삭제를 위해 이메일 인증이 필요합니다.",
                    com.linkup.Petory.domain.user.entity.EmailVerificationPurpose.MISSING_PET);
        }

        // 게시글 소프트 삭제
        board.setIsDeleted(true);
        board.setDeletedAt(java.time.LocalDateTime.now());

        // 관련 댓글 모두 소프트 삭제
        commentService.deleteAllCommentsByBoard(board);

        boardRepository.saveAndFlush(board);
    }

    /**
     * 게시글 DTO 매핑 (파일 정보 포함)
     * 단일 게시글 조회 시 사용
     */
    private MissingPetBoardDTO mapBoardWithAttachments(MissingPetBoard board) {
        MissingPetBoardDTO dto = missingPetConverter.toBoardDTO(board);
        List<FileDTO> attachments = attachmentFileService.getAttachments(FileTargetType.MISSING_PET, board.getIdx());
        dto.setAttachments(attachments);
        dto.setImageUrl(extractPrimaryFileUrl(attachments));
        return dto;
    }

    /**
     * 배치 조회된 파일 정보를 사용하여 게시글 DTO 매핑 (N+1 문제 해결)
     */
    private MissingPetBoardDTO mapBoardWithAttachmentsFromBatch(MissingPetBoard board,
            Map<Long, List<FileDTO>> filesByBoardId) {
        MissingPetBoardDTO dto = missingPetConverter.toBoardDTO(board);
        List<FileDTO> attachments = filesByBoardId.getOrDefault(board.getIdx(), List.of());
        dto.setAttachments(attachments);
        dto.setImageUrl(extractPrimaryFileUrl(attachments));
        return dto;
    }

    /**
     * 첨부파일 목록에서 첫 번째 파일의 다운로드 URL 추출
     * 게시글의 대표 이미지 URL로 사용
     */
    private String extractPrimaryFileUrl(List<FileDTO> attachments) {
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

}
