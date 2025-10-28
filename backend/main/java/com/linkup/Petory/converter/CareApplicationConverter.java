package com.linkup.Petory.converter;

import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.linkup.Petory.dto.CareApplicationDTO;
import com.linkup.Petory.entity.CareApplication;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CareApplicationConverter {

    private final CareReviewConverter careReviewConverter;

    public CareApplicationDTO toDTO(CareApplication app) {
        return CareApplicationDTO.builder()
                .idx(app.getIdx())
                .careRequestId(app.getCareRequest().getIdx())
                .providerId(app.getProvider().getIdx())
                .providerName(app.getProvider().getUsername())
                .status(app.getStatus().name())
                .message(app.getMessage())
                .reviews(app.getReviews() != null ? app.getReviews().stream()
                        .map(careReviewConverter::toDTO)
                        .collect(Collectors.toList())
                        : null)
                .build();
    }
}
