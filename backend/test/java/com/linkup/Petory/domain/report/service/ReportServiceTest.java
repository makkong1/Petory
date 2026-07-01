package com.linkup.Petory.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.entity.Comment;
import com.linkup.Petory.domain.board.repository.BoardRepository;
import com.linkup.Petory.domain.board.repository.CommentRepository;
import com.linkup.Petory.domain.board.repository.MissingPetBoardRepository;
import com.linkup.Petory.domain.board.repository.MissingPetCommentRepository;
import com.linkup.Petory.domain.care.repository.CareReviewRepository;
import com.linkup.Petory.domain.report.converter.ReportConverter;
import com.linkup.Petory.domain.report.dto.ReportDTO;
import com.linkup.Petory.domain.report.dto.ReportHandleRequest;
import com.linkup.Petory.domain.report.dto.ReportRequestDTO;
import com.linkup.Petory.domain.report.entity.Report;
import com.linkup.Petory.domain.report.entity.ReportActionType;
import com.linkup.Petory.domain.report.entity.ReportStatus;
import com.linkup.Petory.domain.report.entity.ReportTargetType;
import com.linkup.Petory.domain.report.repository.ReportRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.UserStatus;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.domain.user.service.UserSanctionService;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @InjectMocks
    private ReportService reportService;

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private UsersRepository usersRepository;
    @Mock
    private BoardRepository boardRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private MissingPetBoardRepository missingPetBoardRepository;
    @Mock
    private MissingPetCommentRepository missingPetCommentRepository;
    @Mock
    private CareReviewRepository careReviewRepository;
    @Mock
    private ReportConverter reportConverter;
    @Mock
    private UserSanctionService userSanctionService;

    @Test
    @DisplayName("신고 생성: 요청 바디 reporterId가 아닌 인증 주체 idx를 신고자로 사용한다")
    void 신고생성_인증주체를_신고자로_사용() {
        Users reporter = user(5L, Role.USER);
        ReportRequestDTO request = new ReportRequestDTO(ReportTargetType.BOARD, 123L, 999L, "bad board");

        when(usersRepository.findById(5L)).thenReturn(Optional.of(reporter));
        when(boardRepository.existsById(123L)).thenReturn(true);
        when(reportRepository.existsByTargetTypeAndTargetIdxAndReporterIdx(
                ReportTargetType.BOARD, 123L, 5L)).thenReturn(false);
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reportConverter.toDTO(any(Report.class))).thenReturn(ReportDTO.builder().idx(1L).build());

        reportService.createReport(request, 5L);

        ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(reportCaptor.capture());
        assertThat(reportCaptor.getValue().getReporter().getIdx()).isEqualTo(5L);
        verify(usersRepository, never()).findById(999L);
        verify(reportRepository, never()).existsByTargetTypeAndTargetIdxAndReporterIdx(
                eq(ReportTargetType.BOARD), eq(123L), eq(999L));
    }

    @Test
    @DisplayName("신고 처리: 게시글 제재는 report.targetIdx가 아니라 게시글 작성자 idx에 적용된다")
    void 게시글신고_작성자에게_제재적용() {
        Users admin = user(1L, Role.ADMIN);
        Users author = user(99L, Role.USER);
        Report report = Report.builder()
                .idx(10L)
                .targetType(ReportTargetType.BOARD)
                .targetIdx(123L)
                .reason("bad board")
                .build();
        Board board = Board.builder().idx(123L).user(author).build();
        ReportHandleRequest request = new ReportHandleRequest();
        request.setStatus(ReportStatus.RESOLVED);
        request.setActionTaken(ReportActionType.WARN_USER);
        request.setAdminNote("warning");
        when(reportRepository.findById(10L)).thenReturn(Optional.of(report));
        when(usersRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(boardRepository.findById(123L)).thenReturn(Optional.of(board));
        when(reportConverter.toDTO(any(Report.class))).thenReturn(ReportDTO.builder().idx(10L).build());

        reportService.handleReport(10L, 1L, request);

        verify(userSanctionService).applySanctionFromReport(
                99L,
                ReportActionType.WARN_USER,
                "신고 #10 처리: warning",
                1L,
                10L);
    }

    @Test
    @DisplayName("신고 처리: 댓글 제재는 report.targetIdx가 아니라 댓글 작성자 idx에 적용된다")
    void 댓글신고_작성자에게_제재적용() {
        Users admin = user(1L, Role.ADMIN);
        Users author = user(77L, Role.USER);
        Report report = Report.builder()
                .idx(11L)
                .targetType(ReportTargetType.COMMENT)
                .targetIdx(456L)
                .reason("bad comment")
                .build();
        Comment comment = Comment.builder().idx(456L).user(author).build();
        ReportHandleRequest request = new ReportHandleRequest();
        request.setStatus(ReportStatus.RESOLVED);
        request.setActionTaken(ReportActionType.SUSPEND_USER);
        when(reportRepository.findById(11L)).thenReturn(Optional.of(report));
        when(usersRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(commentRepository.findById(456L)).thenReturn(Optional.of(comment));
        when(reportConverter.toDTO(any(Report.class))).thenReturn(ReportDTO.builder().idx(11L).build());

        reportService.handleReport(11L, 1L, request);

        verify(userSanctionService).applySanctionFromReport(
                77L,
                ReportActionType.SUSPEND_USER,
                "신고 #11 처리: bad comment",
                1L,
                11L);
    }

    private Users user(Long idx, Role role) {
        return Users.builder()
                .idx(idx)
                .id("user-" + idx)
                .username("user-" + idx)
                .email("user-" + idx + "@example.com")
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();
    }
}
