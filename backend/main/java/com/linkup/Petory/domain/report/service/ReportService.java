package com.linkup.Petory.domain.report.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.linkup.Petory.domain.board.repository.BoardRepository;
import com.linkup.Petory.domain.board.repository.CommentRepository;
import com.linkup.Petory.domain.board.repository.MissingPetBoardRepository;
import com.linkup.Petory.domain.board.repository.MissingPetCommentRepository;
import com.linkup.Petory.domain.report.converter.ReportConverter;
import com.linkup.Petory.domain.report.dto.ReportDTO;
import com.linkup.Petory.domain.report.exception.ReportConflictException;
import com.linkup.Petory.domain.report.exception.ReportForbiddenException;
import com.linkup.Petory.domain.report.exception.ReportNotFoundException;
import com.linkup.Petory.domain.report.exception.ReportTargetNotFoundException;
import com.linkup.Petory.domain.report.exception.ReportValidationException;
import com.linkup.Petory.domain.report.dto.ReportDetailDTO;
import com.linkup.Petory.domain.report.dto.ReportHandleRequest;
import com.linkup.Petory.domain.report.dto.ReportRequestDTO;
import com.linkup.Petory.domain.report.entity.Report;
import com.linkup.Petory.domain.report.entity.ReportActionType;
import com.linkup.Petory.domain.report.entity.ReportStatus;
import com.linkup.Petory.domain.report.entity.ReportTargetType;
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
    private final ReportConverter reportConverter;
    private final UserSanctionService userSanctionService;

    @Transactional
    public ReportDTO createReport(ReportRequestDTO request) {
        if (request.targetType() == null) {
            throw ReportValidationException.targetTypeRequired();
        }
        if (request.targetIdx() == null) {
            throw ReportValidationException.targetIdxRequired();
        }
        if (request.reporterId() == null) {
            throw ReportValidationException.reporterRequired();
        }
        if (!StringUtils.hasText(request.reason())) {
            throw ReportValidationException.reasonRequired();
        }

        Users reporter = usersRepository.findById(request.reporterId())
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
     * 신고 목록 조회 (관리자용)
     * - AdminReportController에서 사용
     * - 신고 횟수가 많은 순서대로 정렬 (신고 횟수 DESC, 생성일시 DESC)
     */
    public List<ReportDTO> getReports(ReportTargetType targetType, ReportStatus status) {
        // 필터 조건에 맞는 신고 목록 조회
        List<Report> reports = reportRepository.findReportsWithFilters(targetType, status);

        // 각 target에 대한 총 신고 횟수 계산 (Map으로 캐싱)
        Map<String, Long> reportCountMap = reports.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        r -> r.getTargetType().name() + "_" + r.getTargetIdx(),
                        java.util.stream.Collectors.counting()));

        // ReportDTO 변환 및 신고 횟수 포함
        List<ReportDTO> dtos = reports.stream()
                .map(report -> {
                    String targetKey = report.getTargetType().name() + "_" + report.getTargetIdx();
                    Integer reportCount = reportCountMap.get(targetKey).intValue();
                    
                    ReportDTO dto = reportConverter.toDTO(report);
                    // ReportDTO는 @Value이므로 새로 빌드해야 함
                    return ReportDTO.builder()
                            .idx(dto.getIdx())
                            .targetType(dto.getTargetType())
                            .targetIdx(dto.getTargetIdx())
                            .reporterId(dto.getReporterId())
                            .reporterName(dto.getReporterName())
                            .reason(dto.getReason())
                            .status(dto.getStatus())
                            .actionTaken(dto.getActionTaken())
                            .handledBy(dto.getHandledBy())
                            .handledByName(dto.getHandledByName())
                            .handledAt(dto.getHandledAt())
                            .adminNote(dto.getAdminNote())
                            .createdAt(dto.getCreatedAt())
                            .updatedAt(dto.getUpdatedAt())
                            .reportCount(reportCount)
                            .build();
                })
                .toList();

        // 신고 횟수 기준으로 정렬 (신고 횟수 DESC, 생성일시 DESC)
        return dtos.stream()
                .sorted((dto1, dto2) -> {
                    // 신고 횟수 비교 (내림차순)
                    int countCompare = Integer.compare(
                            dto2.getReportCount() != null ? dto2.getReportCount() : 0,
                            dto1.getReportCount() != null ? dto1.getReportCount() : 0);
                    if (countCompare != 0) {
                        return countCompare;
                    }
                    // 신고 횟수가 같으면 생성일시 비교 (내림차순)
                    if (dto1.getCreatedAt() != null && dto2.getCreatedAt() != null) {
                        return dto2.getCreatedAt().compareTo(dto1.getCreatedAt());
                    }
                    return 0;
                })
                .toList();
    }

    /**
     * 신고 상세 조회 (관리자용)
     * - AdminReportController에서 사용
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
     * 신고 처리 (관리자용)
     * - AdminReportController에서 사용
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

        report.setStatus(req.getStatus());
        report.setHandledBy(admin);
        report.setHandledAt(LocalDateTime.now());
        report.setAdminNote(req.getAdminNote());
        report.setActionTaken(req.getActionTaken() != null ? req.getActionTaken() : ReportActionType.NONE);

        // 제재 조치가 있으면 자동 적용
        if (req.getActionTaken() != null &&
                (req.getActionTaken() == ReportActionType.WARN_USER ||
                        req.getActionTaken() == ReportActionType.SUSPEND_USER)) {
            String sanctionReason = String.format("신고 #%d 처리: %s", reportId,
                    req.getAdminNote() != null ? req.getAdminNote() : report.getReason());
            userSanctionService.applySanctionFromReport(
                    report.getTargetIdx(),
                    req.getActionTaken(),
                    sanctionReason,
                    admin.getIdx(),
                    reportId);
        }

        return reportConverter.toDTO(report);
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
            default: {
                return ReportDetailDTO.TargetPreview.builder().type(type).id(id).build();
            }
        }
    }

    // 이후 실제 미리보기 구현 시 사용할 유틸
    private String ellipsis(String text, int maxLen) {
        if (text == null)
            return null;
        if (text.length() <= maxLen)
            return text;
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
            default:
                throw ReportValidationException.unsupportedTarget();
        }
    }
}
