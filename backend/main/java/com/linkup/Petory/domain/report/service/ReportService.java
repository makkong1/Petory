package com.linkup.Petory.domain.report.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.linkup.Petory.domain.board.repository.BoardRepository;
import com.linkup.Petory.domain.board.repository.CommentRepository;
import com.linkup.Petory.domain.board.repository.MissingPetBoardRepository;
import com.linkup.Petory.domain.board.repository.MissingPetCommentRepository;
import com.linkup.Petory.domain.care.repository.CareReviewRepository;
import com.linkup.Petory.domain.report.converter.ReportConverter;
import com.linkup.Petory.domain.report.dto.AdminReportPageResponseDTO;
import com.linkup.Petory.domain.report.dto.ReportDTO;
import com.linkup.Petory.domain.report.dto.ReportDetailDTO;
import com.linkup.Petory.domain.report.dto.ReportHandleRequest;
import com.linkup.Petory.domain.report.dto.ReportRequestDTO;
import com.linkup.Petory.domain.report.entity.Report;
import com.linkup.Petory.domain.common.ReportActionType;
import com.linkup.Petory.domain.report.entity.ReportStatus;
import com.linkup.Petory.domain.report.entity.ReportTargetType;
import com.linkup.Petory.domain.report.exception.ReportConflictException;
import com.linkup.Petory.domain.report.exception.ReportForbiddenException;
import com.linkup.Petory.domain.report.exception.ReportNotFoundException;
import com.linkup.Petory.domain.report.exception.ReportTargetNotFoundException;
import com.linkup.Petory.domain.report.exception.ReportValidationException;
import com.linkup.Petory.domain.report.repository.ReportRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.exception.UserNotFoundException;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.domain.user.service.UserSanctionService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final ReportRepository reportRepository;
    private final UsersRepository usersRepository;
    private final BoardRepository boardRepository;
    private final CommentRepository commentRepository;
    private final MissingPetBoardRepository missingPetBoardRepository;
    private final MissingPetCommentRepository missingPetCommentRepository;
    private final CareReviewRepository careReviewRepository;
    private final ReportConverter reportConverter;
    private final UserSanctionService userSanctionService;

    @Transactional
    public ReportDTO createReport(ReportRequestDTO request, Long reporterId) {
        if (request.targetType() == null) {
            throw ReportValidationException.targetTypeRequired();
        }
        if (request.targetIdx() == null) {
            throw ReportValidationException.targetIdxRequired();
        }
        if (reporterId == null) {
            throw ReportValidationException.reporterRequired();
        }
        if (!StringUtils.hasText(request.reason())) {
            throw ReportValidationException.reasonRequired();
        }

        Users reporter = usersRepository.findById(reporterId)
                .orElseThrow(UserNotFoundException::new);

        validateTarget(request.targetType(), request.targetIdx());

        if (reportRepository.existsByTargetTypeAndTargetIdxAndReporterIdx(
                request.targetType(),
                request.targetIdx(),
                reporter.getIdx())) {
            throw ReportConflictException.alreadyReported();
        }

        Report report = Report.builder()
                .targetType(request.targetType())
                .targetIdx(request.targetIdx())
                .reporter(reporter)
                .reason(request.reason().trim())
                .build();

        Report saved = reportRepository.save(report);
        return reportConverter.toDTO(saved);
    }

    /**
     * 신고 목록 조회 (관리자용) - AdminReportController에서 사용.
     *
     * <p>
     * 기존엔 전건을 인메모리로 읽어 target별 신고횟수를 집계하고 그 횟수순으로 정렬했으나, 데이터가 쌓일수록 조회량이 비례해 커지는
     * 구조였다. reporter/handledBy를 통째로 로딩하던 컬럼 오버페칭까지 겹쳐 있어, DB projection 페이징으로
     * 전환한다(정렬은 최신순 {@code createdAt DESC}). 인메모리 집계하던 {@code reportCount}는 소비처가
     * 없어 제거했다.
     */
    @Transactional(readOnly = true)
    public AdminReportPageResponseDTO getReports(ReportTargetType targetType, ReportStatus status, Pageable pageable) {
        Page<ReportDTO> page = reportRepository.findReportListItems(targetType, status, pageable);
        return AdminReportPageResponseDTO.builder()
                .reports(page.getContent())
                .totalCount(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .currentPage(page.getNumber())
                .pageSize(page.getSize())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }

    /**
     * 신고 상세 조회 (관리자용) - AdminReportController에서 사용
     */
    public ReportDetailDTO getReportDetail(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(ReportNotFoundException::new);

        ReportDetailDTO.TargetPreview preview = buildTargetPreview(report);

        return ReportDetailDTO.builder()
                .report(reportConverter.toDTO(report))
                .target(preview)
                .build();
    }

    /**
     * 신고 처리 (관리자용) - AdminReportController에서 사용
     */
    @Transactional
    public ReportDTO handleReport(Long reportId, Long adminUserId, ReportHandleRequest req) {
        if (req.getStatus() == null) {
            throw ReportValidationException.statusRequired();
        }
        Report report = reportRepository.findById(reportId)
                .orElseThrow(ReportNotFoundException::new);
        Users admin = usersRepository.findById(adminUserId)
                .orElseThrow(UserNotFoundException::new);

        report.handle(admin, req.getStatus(), req.getActionTaken(), req.getAdminNote());

        // 제재 조치가 있으면 자동 적용
        if (req.getActionTaken() != null
                && (req.getActionTaken() == ReportActionType.WARN_USER
                || req.getActionTaken() == ReportActionType.SUSPEND_USER
                || req.getActionTaken() == ReportActionType.BAN_USER)) {
            String sanctionReason = String.format("신고 #%d 처리: %s", reportId,
                    req.getAdminNote() != null ? req.getAdminNote() : report.getReason());
            userSanctionService.applySanctionFromReport(
                    resolveSanctionUserId(report),
                    req.getActionTaken(),
                    sanctionReason,
                    admin.getIdx(),
                    reportId);
        }

        return reportConverter.toDTO(report);
    }

    private Long resolveSanctionUserId(Report report) {
        return switch (report.getTargetType()) {
            case BOARD ->
                boardRepository.findById(report.getTargetIdx())
                .map(board -> board.getUser().getIdx())
                .orElseThrow(ReportTargetNotFoundException::board);
            case COMMENT ->
                commentRepository.findById(report.getTargetIdx())
                .map(comment -> comment.getUser().getIdx())
                .or(() -> missingPetCommentRepository.findById(report.getTargetIdx())
                .map(comment -> comment.getUser().getIdx()))
                .orElseThrow(ReportTargetNotFoundException::comment);
            case MISSING_PET ->
                missingPetBoardRepository.findById(report.getTargetIdx())
                .map(board -> board.getUser().getIdx())
                .orElseThrow(ReportTargetNotFoundException::missingPet);
            case PET_CARE_PROVIDER ->
                usersRepository.findById(report.getTargetIdx())
                .filter(provider -> provider.getRole() == Role.SERVICE_PROVIDER)
                .map(Users::getIdx)
                .orElseThrow(ReportTargetNotFoundException::provider);
            case CARE_REVIEW ->
                careReviewRepository.findById(report.getTargetIdx())
                .map(review -> review.getReviewer().getIdx())
                .orElseThrow(ReportTargetNotFoundException::careReview);
        };
    }

    private ReportDetailDTO.TargetPreview buildTargetPreview(Report report) {
        String type = report.getTargetType().name();
        Long id = report.getTargetIdx();

        switch (report.getTargetType()) {
            case BOARD: {
                return boardRepository.findById(id)
                        .map(b -> ReportDetailDTO.TargetPreview.builder()
                        .type(type)
                        .id(id)
                        .title(b.getTitle())
                        .summary(ellipsis(b.getContent(), 300))
                        .authorName(b.getUser() != null ? b.getUser().getUsername() : null)
                        .build())
                        .orElse(ReportDetailDTO.TargetPreview.builder().type(type).id(id).title("(삭제됨)").build());
            }
            case COMMENT: {
                return commentRepository.findById(id)
                        .map(c -> ReportDetailDTO.TargetPreview.builder()
                        .type(type)
                        .id(id)
                        .title(null)
                        .summary(ellipsis(c.getContent(), 300))
                        .authorName(c.getUser() != null ? c.getUser().getUsername() : null)
                        .build())
                        .orElse(ReportDetailDTO.TargetPreview.builder().type(type).id(id).summary("(삭제됨)").build());
            }
            case MISSING_PET: {
                return missingPetBoardRepository.findById(id)
                        .map(m -> ReportDetailDTO.TargetPreview.builder()
                        .type(type)
                        .id(id)
                        .title(m.getTitle())
                        .summary(ellipsis(m.getContent(), 300))
                        .authorName(m.getUser() != null ? m.getUser().getUsername() : null)
                        .build())
                        .orElse(ReportDetailDTO.TargetPreview.builder().type(type).id(id).title("(삭제됨)").build());
            }
            case PET_CARE_PROVIDER: {
                return usersRepository.findById(id)
                        .map(u -> ReportDetailDTO.TargetPreview.builder()
                        .type(type)
                        .id(id)
                        .title("서비스 제공자")
                        .summary(null)
                        .authorName(u.getUsername())
                        .build())
                        .orElse(ReportDetailDTO.TargetPreview.builder().type(type).id(id).title("(탈퇴/없음)").build());
            }
            case CARE_REVIEW: {
                return careReviewRepository.findById(id)
                        .map(r -> ReportDetailDTO.TargetPreview.builder()
                        .type(type)
                        .id(id)
                        .title("케어 리뷰")
                        .summary(ellipsis(r.getComment(), 300))
                        .authorName(r.getReviewer() != null ? r.getReviewer().getUsername() : null)
                        .build())
                        .orElse(ReportDetailDTO.TargetPreview.builder().type(type).id(id).title("(삭제됨)").build());
            }
            default: {
                return ReportDetailDTO.TargetPreview.builder().type(type).id(id).build();
            }
        }
    }

    // 이후 실제 미리보기 구현 시 사용할 유틸
    private String ellipsis(String text, int maxLen) {
        if (text == null) {
            return null;
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLen - 1)) + "…";
    }

    private void validateTarget(ReportTargetType targetType, Long targetIdx) {
        boolean exists;
        switch (targetType) {
            case BOARD: {
                exists = boardRepository.existsById(targetIdx);
                if (!exists) {
                    throw ReportTargetNotFoundException.board();
                }
                break;
            }
            case COMMENT: {
                exists = commentRepository.existsById(targetIdx);
                if (!exists) {
                    exists = missingPetCommentRepository.existsById(targetIdx);
                }
                if (!exists) {
                    throw ReportTargetNotFoundException.comment();
                }
                break;
            }
            case MISSING_PET: {
                exists = missingPetBoardRepository.existsById(targetIdx);
                if (!exists) {
                    throw ReportTargetNotFoundException.missingPet();
                }
                break;
            }
            case PET_CARE_PROVIDER: {
                Users provider = usersRepository.findById(targetIdx)
                        .orElseThrow(ReportTargetNotFoundException::provider);
                if (provider.getRole() != Role.SERVICE_PROVIDER) {
                    throw ReportForbiddenException.providerOnly();
                }
                break;
            }
            case CARE_REVIEW: {
                exists = careReviewRepository.existsById(targetIdx);
                if (!exists) {
                    throw ReportTargetNotFoundException.careReview();
                }
                break;
            }
            default:
                throw ReportValidationException.unsupportedTarget();
        }
    }
}
