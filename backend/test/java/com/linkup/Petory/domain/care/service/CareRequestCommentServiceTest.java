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
import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestComment;
import com.linkup.Petory.domain.care.exception.CareForbiddenException;
import com.linkup.Petory.domain.care.repository.CareRequestCommentRepository;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.file.service.AttachmentFileService;
import com.linkup.Petory.domain.notification.service.NotificationService;
import com.linkup.Petory.domain.user.entity.Role;
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
        String username = "provider";

        Users owner = Users.builder().idx(10L).username(username).role(Role.SERVICE_PROVIDER).build();
        CareRequest careRequest = CareRequest.builder().idx(careRequestId).build();
        CareRequestComment comment = CareRequestComment.builder()
                .idx(commentId)
                .careRequest(careRequest)
                .user(owner)
                .isDeleted(false)
                .build();

        when(careRequestRepository.findById(careRequestId)).thenReturn(Optional.of(careRequest));
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(usersRepository.findByUsername(username)).thenReturn(Optional.of(owner));
        when(commentRepository.save(any(CareRequestComment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        careRequestCommentService.deleteComment(careRequestId, commentId, username);

        assertThat(comment.getIsDeleted()).isTrue();
        assertThat(comment.getDeletedAt()).isNotNull();
        verify(commentRepository).save(comment);
    }

    @Test
    @DisplayName("정상: ADMIN은 다른 사람 댓글도 삭제할 수 있다")
    void 정상_deleteComment_ADMIN() {
        Long careRequestId = 1L;
        Long commentId = 2L;
        String adminUsername = "admin";

        Users owner = Users.builder().idx(10L).username("provider").role(Role.SERVICE_PROVIDER).build();
        Users admin = Users.builder().idx(99L).username(adminUsername).role(Role.ADMIN).build();
        CareRequest careRequest = CareRequest.builder().idx(careRequestId).build();
        CareRequestComment comment = CareRequestComment.builder()
                .idx(commentId)
                .careRequest(careRequest)
                .user(owner)
                .isDeleted(false)
                .build();

        when(careRequestRepository.findById(careRequestId)).thenReturn(Optional.of(careRequest));
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(usersRepository.findByUsername(adminUsername)).thenReturn(Optional.of(admin));
        when(commentRepository.save(any(CareRequestComment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        careRequestCommentService.deleteComment(careRequestId, commentId, adminUsername);

        assertThat(comment.getIsDeleted()).isTrue();
        assertThat(comment.getDeletedAt()).isNotNull();
        verify(commentRepository).save(comment);
    }

    @Test
    @DisplayName("예외: 댓글 작성자도 관리자도 아니면 삭제할 수 없다")
    void 예외_deleteComment_권한없음() {
        Long careRequestId = 1L;
        Long commentId = 2L;
        String username = "other-user";

        Users owner = Users.builder().idx(10L).username("provider").role(Role.SERVICE_PROVIDER).build();
        Users otherUser = Users.builder().idx(20L).username(username).role(Role.USER).build();
        CareRequest careRequest = CareRequest.builder().idx(careRequestId).build();
        CareRequestComment comment = CareRequestComment.builder()
                .idx(commentId)
                .careRequest(careRequest)
                .user(owner)
                .isDeleted(false)
                .build();

        when(careRequestRepository.findById(careRequestId)).thenReturn(Optional.of(careRequest));
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(usersRepository.findByUsername(username)).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> careRequestCommentService.deleteComment(careRequestId, commentId, username))
                .isInstanceOf(CareForbiddenException.class)
                .hasMessageContaining("댓글 작성자 또는 관리자");

        verify(commentRepository, never()).save(any(CareRequestComment.class));
    }
}
