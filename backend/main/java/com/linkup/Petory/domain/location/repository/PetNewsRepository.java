package com.linkup.Petory.domain.location.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linkup.Petory.domain.location.entity.PetNews;

public interface PetNewsRepository extends JpaRepository<PetNews, Long> {

    /** 멱등의 판정. {@code (source, source_no)} 가 UNIQUE 라 이 검사와 제약이 같은 축을 본다. */
    boolean existsBySourceAndSourceNo(String source, String sourceNo);
}
