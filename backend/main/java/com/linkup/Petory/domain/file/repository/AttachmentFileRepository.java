package com.linkup.Petory.domain.file.repository;

import java.util.List;
import java.util.Optional;

import com.linkup.Petory.domain.file.entity.AttachmentFile;
import com.linkup.Petory.domain.file.entity.FileTargetType;

/**
 * AttachmentFile 도메인 Repository 인터페이스입니다.
 */
public interface AttachmentFileRepository {

    // 기본 CRUD 메서드
    AttachmentFile save(AttachmentFile attachmentFile);

    Optional<AttachmentFile> findById(Long id);

    List<AttachmentFile> findAll();

    long count();

    void delete(AttachmentFile attachmentFile);

    void deleteById(Long id);

    /**
     * 타겟 타입과 ID로 첨부파일 조회
     */
    List<AttachmentFile> findByTargetTypeAndTargetIdx(FileTargetType targetType, Long targetIdx);

    /**
     * 타겟 타입과 ID로 첨부파일 삭제
     */
    void deleteByTargetTypeAndTargetIdx(FileTargetType targetType, Long targetIdx);

    /**
     * 여러 타겟의 첨부파일을 한 번에 조회 (배치 조회)
     */
    List<AttachmentFile> findByTargetTypeAndTargetIdxIn(FileTargetType targetType, List<Long> targetIndices);
}

