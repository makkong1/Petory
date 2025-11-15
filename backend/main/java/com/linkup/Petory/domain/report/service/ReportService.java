package com.linkup.Petory.domain.report.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.linkup.Petory.domain.board.repository.BoardRepository;
import com.linkup.Petory.domain.board.repository.CommentRepository;
import com.linkup.Petory.domain.board.repository.MissingPetBoardRepository;
import com.linkup.Petory.domain.board.repository.MissingPetCommentRepository;
import com.linkup.Petory.domain.report.converter.ReportConverter;
import com.linkup.Petory.domain.report.dto.ReportDTO;
import com.linkup.Petory.domain.report.dto.ReportRequestDTO;
import com.linkup.Petory.domain.report.entity.Report;
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

