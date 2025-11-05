package com.linkup.Petory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.entity.CareRequest;
import com.linkup.Petory.entity.CareRequestStatus;
import com.linkup.Petory.entity.Users;

@Repository
public interface CareRequestRepository extends JpaRepository<CareRequest, Long> {

    // 사용자별 케어 요청 조회 (최신순)
    List<CareRequest> findByUserOrderByCreatedAtDesc(Users user);

    // 상태별 케어 요청 조회
    List<CareRequest> findByStatus(CareRequestStatus status);

    // 위치별 케어 요청 조회 (사용자 위치 기반)
    List<CareRequest> findByUser_LocationContaining(String location);

    // 제목이나 설명에 키워드 포함된 케어 요청 검색
    List<CareRequest> findByTitleContainingOrDescriptionContaining(String titleKeyword, String descKeyword);
}
