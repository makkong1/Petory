package com.linkup.Petory.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.linkup.Petory.domain.file.entity.AttachmentFile;
import com.linkup.Petory.domain.file.entity.FileTargetType;
import com.linkup.Petory.domain.file.repository.AttachmentFileRepository;
import com.linkup.Petory.domain.file.service.AttachmentFileService;

@ExtendWith(MockitoExtension.class)
class AdminFileFacadeTest {

    @InjectMocks
    private AdminFileFacade facade;

    @Mock
    private AttachmentFileRepository fileRepository;
    @Mock
    private AttachmentFileService attachmentFileService;
    @Mock
    private AdminAuditService auditService;

    @Test
    @DisplayName("정상: 파일 단건 삭제 시 감사 로그를 남긴다")
    void 정상_파일단건삭제_감사로그() {
        AttachmentFile file = AttachmentFile.builder()
                .idx(1L)
                .targetType(FileTargetType.BOARD)
                .targetIdx(10L)
                .filePath("board/test.png")
                .build();
        when(fileRepository.findById(1L)).thenReturn(Optional.of(file));

        facade.deleteFile(1L, 99L);

        verify(fileRepository).deleteById(1L);
        verify(auditService).log(99L, "FILE_DELETE", "FILE", 1L, "targetType=BOARD,targetIdx=10");
    }

    @Test
    @DisplayName("예외: 유효하지 않은 targetType으로 대상별 삭제 시 실패한다")
    void 예외_잘못된TargetType_대상삭제() {
        assertThatThrownBy(() -> facade.deleteFilesByTarget("WRONG", 10L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 targetType 입니다.");
    }
}
