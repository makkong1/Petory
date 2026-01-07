package com.linkup.Petory.domain.file.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linkup.Petory.domain.file.entity.AttachmentFile;
import com.linkup.Petory.domain.file.entity.FileTargetType;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaAttachmentFileRepository extends JpaRepository<AttachmentFile, Long> {

    List<AttachmentFile> findByTargetTypeAndTargetIdx(FileTargetType targetType, Long targetIdx);

    void deleteByTargetTypeAndTargetIdx(FileTargetType targetType, Long targetIdx);

    /**
     * 여러 타겟의 첨부파일을 한 번에 조회 (배치 조회)
     */
    List<AttachmentFile> findByTargetTypeAndTargetIdxIn(FileTargetType targetType, List<Long> targetIndices);
}

