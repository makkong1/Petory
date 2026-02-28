package com.linkup.Petory.domain.file.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linkup.Petory.domain.file.entity.AttachmentFile;
import com.linkup.Petory.domain.file.entity.FileTargetType;
import com.linkup.Petory.global.annotation.RepositoryMethod;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaAttachmentFileRepository extends JpaRepository<AttachmentFile, Long> {

    @RepositoryMethod("첨부파일: 타겟별 조회")
    List<AttachmentFile> findByTargetTypeAndTargetIdx(FileTargetType targetType, Long targetIdx);

    @RepositoryMethod("첨부파일: 타겟별 삭제")
    void deleteByTargetTypeAndTargetIdx(FileTargetType targetType, Long targetIdx);

    @RepositoryMethod("첨부파일: 타겟 목록별 배치 조회")
    List<AttachmentFile> findByTargetTypeAndTargetIdxIn(FileTargetType targetType, List<Long> targetIndices);
}

