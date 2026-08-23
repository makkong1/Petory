package com.linkup.Petory.domain.care.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.linkup.Petory.domain.care.converter.CareRequestCommentConverter;
import com.linkup.Petory.domain.care.dto.CareRequestCommentDTO;
import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestComment;
import com.linkup.Petory.domain.care.exception.CareForbiddenException;
import com.linkup.Petory.domain.care.repository.CareRequestCommentRepository;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.file.service.AttachmentFileService;
import com.linkup.Petory.domain.notification.service.NotificationService;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.UserStatus;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

@ExtendWith(MockitoExtension.class)
class CareRequestCommentServiceTest {

    @Mock
    private CareRequestCommentRepository commentRepository;
    @Mock
    private CareRequestRepository careRequestRepository;
    @Mock
    private UsersRepository usersRepository;
    @Mock
    private CareRequestCommentConverter commentConverter;
    @Mock
    private AttachmentFileService attachmentFileService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private CareRequestCommentService careRequestCommentService;

    @Test
    @DisplayName("정상: 댓글 작성자는 자신의 댓글을 삭제할 수 있다")
    void 정상_deleteComment_작성자() {
        Long careRequestId = 1L;
        Long commentId = 2L;
        Long userId = 10L;

        Users owner = Users.builder().idx(userId).id("provider").role(Role.SERVICE_PROVIDER).build();
        CareRequest careRequest = CareRequest.builder().idx(careRequestId).build();
        CareRequestComment comment = CareRequestComment.builder()
                .idx(commentId)
                .careRequest(careRequest)
                .user(owner)
                .isDeleted(false)
                .build();

        when(careRequestRepository.findById(careRequestId)).thenReturn(Optional.of(careRequest));
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(usersRepository.findById(userId)).thenReturn(Optional.of(owner));
        when(commentRepository.save(any(CareRequestComment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        careRequestCommentService.deleteComment(careRequestId, commentId, userId);

        assertThat(comment.getIsDeleted()).isTrue();
        assertThat(comment.getDeletedAt()).isNotNull();
        verify(commentRepository).save(comment);
    }

    @Test
    @DisplayName("정상: ADMIN은 다른 사람 댓글도 삭제할 수 있다")
    void 정상_deleteComment_ADMIN() {
        Long careRequestId = 1L;
        Long commentId = 2L;
        Long adminId = 99L;

        Users owner = Users.builder().idx(10L).id("provider").role(Role.SERVICE_PROVIDER).build();
        Users admin = Users.builder().idx(adminId).id("admin").role(Role.ADMIN).build();
        CareRequest careRequest = CareRequest.builder().idx(careRequestId).build();
        CareRequestComment comment = CareRequestComment.builder()
                .idx(commentId)
                .careRequest(careRequest)
                .user(owner)
                .isDeleted(false)
                .build();

        when(careRequestRepository.findById(careRequestId)).thenReturn(Optional.of(careRequest));
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(usersRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(commentRepository.save(any(CareRequestComment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        careRequestCommentService.deleteComment(careRequestId, commentId, adminId);

        assertThat(comment.getIsDeleted()).isTrue();
        assertThat(comment.getDeletedAt()).isNotNull();
        verify(commentRepository).save(comment);
    }

    @Test
    @DisplayName("예외: 댓글 작성자도 관리자도 아니면 삭제할 수 없다")
    void 예외_deleteComment_권한없음() {
        Long careRequestId = 1L;
        Long commentId = 2L;
        Long otherUserId = 20L;

        Users owner = Users.builder().idx(10L).id("provider").role(Role.SERVICE_PROVIDER).build();
        Users otherUser = Users.builder().idx(otherUserId).id("other-user").role(Role.USER).build();
        CareRequest careRequest = CareRequest.builder().idx(careRequestId).build();
        CareRequestComment comment = CareRequestComment.builder()
                .idx(commentId)
                .careRequest(careRequest)
                .user(owner)
                .isDeleted(false)
                .build();

        when(careRequestRepository.findById(careRequestId)).thenReturn(Optional.of(careRequest));
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(usersRepository.findById(otherUserId)).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> careRequestCommentService.deleteComment(careRequestId, commentId, otherUserId))
                .isInstanceOf(CareForbiddenException.class)
                .hasMessageContaining("댓글 작성자 또는 관리자");

        verify(commentRepository, never()).save(any(CareRequestComment.class));
    }

    @Test
    @DisplayName("정상: SERVICE_PROVIDER는 인증된 사용자 PK로 댓글을 작성할 수 있다")
    void 정상_addComment() {
        Long careRequestId = 1L;
        Long userId = 10L;
        CareRequestCommentDTO dto = CareRequestCommentDTO.builder().content("문의드립니다").build();

        Users provider = Users.builder().idx(userId).id("provider").role(Role.SERVICE_PROVIDER)
                .status(UserStatus.ACTIVE).build();
        Users requester = Users.builder().idx(20L).id("requester").build();
        CareRequest careRequest = CareRequest.builder().idx(careRequestId).user(requester).build();

        when(careRequestRepository.findById(careRequestId)).thenReturn(Optional.of(careRequest));
        when(usersRepository.findById(userId)).thenReturn(Optional.of(provider));
        when(commentRepository.save(any(CareRequestComment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(commentConverter.toDTO(any(CareRequestComment.class))).thenReturn(dto);

        careRequestCommentService.addComment(careRequestId, dto, userId);

        verify(commentRepository).save(any(CareRequestComment.class));
    }

    @Test
    @DisplayName("예외: 제재된 사용자는 댓글을 작성할 수 없다")
    void 예외_addComment_제재유저() {
        Long careRequestId = 1L;
        Long userId = 10L;
        CareRequestCommentDTO dto = CareRequestCommentDTO.builder().content("문의드립니다").build();

        Users sanctioned = Users.builder().idx(userId).id("banned-provider").role(Role.SERVICE_PROVIDER)
                .status(UserStatus.BANNED).build();
        CareRequest careRequest = CareRequest.builder().idx(careRequestId).build();

        when(careRequestRepository.findById(careRequestId)).thenReturn(Optional.of(careRequest));
        when(usersRepository.findById(userId)).thenReturn(Optional.of(sanctioned));

        assertThatThrownBy(() -> careRequestCommentService.addComment(careRequestId, dto, userId))
                .isInstanceOf(CareForbiddenException.class);

        verify(commentRepository, never()).save(any(CareRequestComment.class));
    }
}
