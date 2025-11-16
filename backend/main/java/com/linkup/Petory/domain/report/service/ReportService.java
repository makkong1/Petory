package com.linkup.Petory.domain.report.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.linkup.Petory.domain.board.repository.BoardRepository;
import com.linkup.Petory.domain.board.repository.CommentRepository;
import com.linkup.Petory.domain.board.repository.MissingPetBoardRepository;
import com.linkup.Petory.domain.board.repository.MissingPetCommentRepository;
import com.linkup.Petory.domain.report.converter.ReportConverter;
import com.linkup.Petory.domain.report.dto.ReportDTO;
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
import com.linkup.Petory.domain.user.repository.UsersRepository;

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

    @Transactional
    public ReportDTO createReport(ReportRequestDTO request) {
        if (request.getTargetType() == null) {
            throw new IllegalArgumentException("신고 대상 종류를 선택해주세요.");
        }
        if (request.getTargetIdx() == null) {
            throw new IllegalArgumentException("신고 대상 ID가 필요합니다.");
        }
        if (request.getReporterId() == null) {
            throw new IllegalArgumentException("신고자 정보가 필요합니다.");
        }
        if (!StringUtils.hasText(request.getReason())) {
            throw new IllegalArgumentException("신고 사유를 입력해주세요.");
        }

        Users reporter = usersRepository.findById(request.getReporterId())
                .orElseThrow(() -> new IllegalArgumentException("신고자 정보를 찾을 수 없습니다."));

        validateTarget(request.getTargetType(), request.getTargetIdx());

        if (reportRepository.existsByTargetTypeAndTargetIdxAndReporterIdx(
                request.getTargetType(),
                request.getTargetIdx(),
                reporter.getIdx())) {
            throw new IllegalStateException("이미 해당 대상을 신고하셨습니다.");
        }

        Report report = Report.builder()
                .targetType(request.getTargetType())
                .targetIdx(request.getTargetIdx())
                .reporter(reporter)
                .reason(request.getReason().trim())
                .build();

        Report saved = reportRepository.save(report);
        return reportConverter.toDTO(saved);
    }

    public List<ReportDTO> getReports(ReportTargetType targetType, ReportStatus status) {
        List<Report> reports;

        if (targetType != null && status != null) {
            reports = reportRepository.findByTargetTypeAndStatusOrderByCreatedAtDesc(targetType, status);
        } else if (targetType != null) {
            reports = reportRepository.findByTargetTypeOrderByCreatedAtDesc(targetType);
        } else if (status != null) {
            reports = reportRepository.findByStatusOrderByCreatedAtDesc(status);
        } else {
            reports = reportRepository.findAllByOrderByCreatedAtDesc();
        }

        return reports.stream()
                .map(reportConverter::toDTO)
                .toList();
    }

    public ReportDetailDTO getReportDetail(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("신고 정보를 찾을 수 없습니다."));

        ReportDetailDTO.TargetPreview preview = buildTargetPreview(report);

        return ReportDetailDTO.builder()
                .report(reportConverter.toDTO(report))
                .target(preview)
                .build();
    }

    @Transactional
    public ReportDTO handleReport(Long reportId, Long adminUserId, ReportHandleRequest req) {
        if (req.getStatus() == null) {
            throw new IllegalArgumentException("처리 상태를 선택해주세요.");
        }
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("신고 정보를 찾을 수 없습니다."));
        Users admin = usersRepository.findById(adminUserId)
                .orElseThrow(() -> new IllegalArgumentException("관리자 정보를 찾을 수 없습니다."));

        report.setStatus(req.getStatus());
        report.setHandledBy(admin);
        report.setHandledAt(LocalDateTime.now());
        report.setAdminNote(req.getAdminNote());
        report.setActionTaken(req.getActionTaken() != null ? req.getActionTaken() : ReportActionType.NONE);

        return reportConverter.toDTO(report);
    }

    private ReportDetailDTO.TargetPreview buildTargetPreview(Report report) {
        String type = report.getTargetType().name();
        Long id = report.getTargetIdx();

        switch (report.getTargetType()) {
            case BOARD -> {
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
            case COMMENT -> {
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
            case MISSING_PET -> {
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
            case PET_CARE_PROVIDER -> {
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
            default -> {
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
            case BOARD -> {
                exists = boardRepository.existsById(targetIdx);
                if (!exists) {
                    throw new IllegalArgumentException("신고 대상 게시글을 찾을 수 없습니다.");
                }
            }
            case COMMENT -> {
                exists = commentRepository.existsById(targetIdx);
                if (!exists) {
                    exists = missingPetCommentRepository.existsById(targetIdx);
                }
                if (!exists) {
                    throw new IllegalArgumentException("신고 대상 댓글을 찾을 수 없습니다.");
                }
            }
            case MISSING_PET -> {
                exists = missingPetBoardRepository.existsById(targetIdx);
                if (!exists) {
                    throw new IllegalArgumentException("신고 대상 실종 제보를 찾을 수 없습니다.");
                }
            }
            case PET_CARE_PROVIDER -> {
                Users provider = usersRepository.findById(targetIdx)
                        .orElseThrow(() -> new IllegalArgumentException("해당 서비스 제공자를 찾을 수 없습니다."));
                if (provider.getRole() != Role.SERVICE_PROVIDER) {
                    throw new IllegalArgumentException("서비스 제공자만 신고할 수 있습니다.");
                }
            }
            default -> throw new IllegalArgumentException("지원하지 않는 신고 대상입니다.");
        }
    }
}
