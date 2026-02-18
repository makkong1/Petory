package com.linkup.Petory.domain.board.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.linkup.Petory.domain.board.converter.MissingPetConverter;
import com.linkup.Petory.domain.board.dto.MissingPetBoardDTO;
import com.linkup.Petory.domain.board.dto.MissingPetBoardPageResponseDTO;
import com.linkup.Petory.domain.board.dto.MissingPetCommentDTO;
import com.linkup.Petory.domain.board.dto.MissingPetCommentPageResponseDTO;
import com.linkup.Petory.domain.board.entity.MissingPetBoard;
import com.linkup.Petory.domain.board.entity.MissingPetStatus;
import com.linkup.Petory.domain.board.repository.MissingPetBoardRepository;
import com.linkup.Petory.domain.file.dto.FileDTO;
import com.linkup.Petory.domain.file.entity.FileTargetType;
import com.linkup.Petory.domain.file.service.AttachmentFileService;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.RequiredArgsConstructor;

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
     * 실종 제보 목록 조회 (페이징 지원)
     * 엔드포인트: GET /api/missing-pets?page={page}&size={size}&status={status}
     * - 상태별 필터링 지원 (status 파라미터)
     * - 페이징 지원 (page, size 파라미터)
     * - 최신순 정렬
     * - 게시글 파일 배치 조회 (N+1 문제 해결)
     * - 댓글 수 배치 조회 (N+1 문제 해결)
     * - 댓글은 포함하지 않음 (조인 폭발 방지)
     */
    public MissingPetBoardPageResponseDTO getBoardsWithPaging(MissingPetStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        // 게시글 + 작성자 페이징 조회 (댓글 제외 - 조인 폭발 방지)
        Page<MissingPetBoard> boardPage = status == null
                ? boardRepository.findAllByOrderByCreatedAtDesc(pageable)
                : boardRepository.findByStatusOrderByCreatedAtDesc(status, pageable);

        if (boardPage.isEmpty()) {
            return new MissingPetBoardPageResponseDTO(
                    new ArrayList<>(),
                    0,
                    0,
                    page,
                    size,
                    false,
                    false);
        }

        List<MissingPetBoard> boards = boardPage.getContent();

        // 게시글 ID 목록 추출
        List<Long> boardIds = boards.stream()
                .map(MissingPetBoard::getIdx)
                .collect(Collectors.toList());

        // 파일 배치 조회 (N+1 문제 해결)
        Map<Long, List<FileDTO>> filesByBoardId = attachmentFileService.getAttachmentsBatch(
                FileTargetType.MISSING_PET, boardIds);

        // 댓글 수 배치 조회 (N+1 문제 해결)
        Map<Long, Integer> commentCountsByBoardId = commentService.getCommentCountsBatch(boardIds);

        // DTO 변환 (파일 정보 포함, 댓글은 빈 리스트)
        // toBoardDTOWithoutComments 사용으로 N+1 문제 방지 (댓글 lazy loading 트리거 안함)
        List<MissingPetBoardDTO> boardDTOs = boards.stream()
                .map(board -> {
                    MissingPetBoardDTO dto = missingPetConverter.toBoardDTOWithoutComments(board);
                    // 파일 정보 추가
                    List<FileDTO> attachments = filesByBoardId.getOrDefault(board.getIdx(), List.of());
                    dto.setAttachments(attachments);
                    dto.setImageUrl(attachmentFileService.extractPrimaryFileUrl(attachments));
                    // 댓글 수 추가
                    int commentCount = commentCountsByBoardId.getOrDefault(board.getIdx(), 0);
                    dto.setCommentCount(commentCount);
                    return dto;
                })
                .collect(Collectors.toList());

        return new MissingPetBoardPageResponseDTO(
                boardDTOs,
                boardPage.getTotalElements(),
                boardPage.getTotalPages(),
                page,
                size,
                boardPage.hasNext(),
                boardPage.hasPrevious());
    }

    /**
     * 실종 제보 목록 조회 (페이징 없음 - 하위 호환성)
     * 엔드포인트: GET /api/missing-pets
     * - 상태별 필터링 지원 (status 파라미터)
     * - 최신순 정렬
     * - 게시글 파일 배치 조회 (N+1 문제 해결)
     * - 댓글 수 배치 조회 (N+1 문제 해결)
     * - 댓글은 포함하지 않음 (조인 폭발 방지)
     */
    public List<MissingPetBoardDTO> getBoards(MissingPetStatus status) {
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

        // 댓글 수 배치 조회 (N+1 문제 해결)
        Map<Long, Integer> commentCountsByBoardId = commentService.getCommentCountsBatch(boardIds);

        // DTO 변환 (파일 정보 포함, 댓글은 빈 리스트)
        // toBoardDTOWithoutComments 사용으로 N+1 문제 방지 (댓글 lazy loading 트리거 안함)
        List<MissingPetBoardDTO> result = boards.stream()
                .map(board -> {
                    MissingPetBoardDTO dto = missingPetConverter.toBoardDTOWithoutComments(board);
                    // 파일 정보 추가
                    List<FileDTO> attachments = filesByBoardId.getOrDefault(board.getIdx(), List.of());
                    dto.setAttachments(attachments);
                    dto.setImageUrl(attachmentFileService.extractPrimaryFileUrl(attachments));
                    // 댓글 수 추가
                    int commentCount = commentCountsByBoardId.getOrDefault(board.getIdx(), 0);
                    dto.setCommentCount(commentCount);
                    return dto;
                })
                .collect(Collectors.toList());

        return result;
    }

    /**
     * 실종 제보 상세 조회 (댓글 페이징 지원)
     * 엔드포인트: GET
     * /api/missing-pets/{id}?commentPage={commentPage}&commentSize={commentSize}
     * - 게시글, 작성자 정보 조회
     * - 게시글 파일 조회
     * - 댓글 페이징 처리 (commentPage, commentSize 파라미터)
     * - commentPage, commentSize가 모두 제공되면 댓글 페이징 조회
     * - 제공되지 않으면 댓글 제외 (빈 리스트, 댓글 수만 포함)
     */
    public MissingPetBoardDTO getBoard(Long id, Integer commentPage, Integer commentSize) {
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

        // 댓글 수 조회
        int commentCount = commentService.getCommentCount(board);

        // 댓글 페이징 조회 (파라미터가 제공된 경우)
        List<MissingPetCommentDTO> comments = List.of();
        if (commentPage != null && commentSize != null && commentSize > 0) {
            MissingPetCommentPageResponseDTO commentPageResponse = commentService.getCommentsWithPaging(id, commentPage,
                    commentSize);
            comments = commentPageResponse.comments();
        }

        // DTO 변환
        MissingPetBoardDTO dto = missingPetConverter.toBoardDTOWithoutComments(board);
        dto.setAttachments(boardAttachments);
        dto.setImageUrl(attachmentFileService.extractPrimaryFileUrl(boardAttachments));
        // 댓글 목록과 댓글 수 설정
        dto.setComments(comments);
        dto.setCommentCount(commentCount);

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
        dto.setImageUrl(attachmentFileService.extractPrimaryFileUrl(attachments));
        return dto;
    }

}
