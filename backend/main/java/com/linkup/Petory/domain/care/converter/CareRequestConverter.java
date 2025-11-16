package com.linkup.Petory.domain.care.converter;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.linkup.Petory.domain.care.dto.CareRequestDTO;
import com.linkup.Petory.domain.care.entity.CareRequest;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CareRequestConverter {

    // Entity → DTO
    public CareRequestDTO toDTO(CareRequest request) {
        return CareRequestDTO.builder()
                .idx(request.getIdx())
                .title(request.getTitle())
                .description(request.getDescription())
                .date(request.getDate())
                .status(request.getStatus().name())
                .createdAt(request.getCreatedAt())
                .deleted(request.getIsDeleted())
                .deletedAt(request.getDeletedAt())
                .userId(request.getUser().getIdx())
                .username(request.getUser().getUsername())
                .userLocation(request.getUser().getLocation())
                .applicationCount(request.getApplications() != null ? request.getApplications().size() : 0)
                .build();
    }

    // DTO 리스트 변환
    public List<CareRequestDTO> toDTOList(List<CareRequest> requests) {
        return requests.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
}