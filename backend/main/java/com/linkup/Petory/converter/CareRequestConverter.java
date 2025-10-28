package com.linkup.Petory.converter;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.linkup.Petory.dto.CareRequestDTO;
import com.linkup.Petory.entity.CareRequest;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CareRequestConverter {

    private final CareApplicationConverter careApplicationConverter;

    public CareRequestDTO toDTO(CareRequest request) {
        return CareRequestDTO.builder()
                .idx(request.getIdx())
                .userId(request.getUser().getIdx())
                .username(request.getUser().getUsername())
                .title(request.getTitle())
                .description(request.getDescription())
                .date(request.getDate())
                .status(request.getStatus().name())
                .applications(request.getApplications() != null ? request.getApplications().stream()
                        .map(careApplicationConverter::toDTO)
                        .collect(Collectors.toList())
                        : null)
                .build();
    }

    // 리스트 변환
    public List<CareRequestDTO> toDTOList(List<CareRequest> requests) {
        return requests.stream().map(this::toDTO).collect(Collectors.toList());
    }
}
