package com.linkup.Petory.domain.file.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.file.entity.AttachmentFile;
import com.linkup.Petory.domain.file.entity.FileTargetType;

import lombok.RequiredArgsConstructor;

/**
 * AttachmentFileRepository의 JPA 구현체(어댑터)입니다.
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaAttachmentFileAdapter implements AttachmentFileRepository {

    private final SpringDataJpaAttachmentFileRepository jpaRepository;

    @Override
    public AttachmentFile save(AttachmentFile attachmentFile) {
        return jpaRepository.save(attachmentFile);
    }

    @Override
    public Optional<AttachmentFile> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<AttachmentFile> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }

    @Override
    public void delete(AttachmentFile attachmentFile) {
        jpaRepository.delete(attachmentFile);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public List<AttachmentFile> findByTargetTypeAndTargetIdx(FileTargetType targetType, Long targetIdx) {
        return jpaRepository.findByTargetTypeAndTargetIdx(targetType, targetIdx);
    }

    @Override
    public void deleteByTargetTypeAndTargetIdx(FileTargetType targetType, Long targetIdx) {
        jpaRepository.deleteByTargetTypeAndTargetIdx(targetType, targetIdx);
    }

    @Override
    public List<AttachmentFile> findByTargetTypeAndTargetIdxIn(FileTargetType targetType, List<Long> targetIndices) {
        return jpaRepository.findByTargetTypeAndTargetIdxIn(targetType, targetIndices);
    }
}

