package com.linkup.Petory.domain.care.converter;

import org.springframework.stereotype.Component;

import com.linkup.Petory.domain.care.dto.CareApplicationDTO;
import com.linkup.Petory.domain.care.entity.CareApplication;

import lombok.RequiredArgsConstructor;

/** CareApplication 엔티티 → CareApplicationDTO 변환기. */
@Component
@RequiredArgsConstructor
public class CareApplicationConverter {

    // private final CareReviewConverter careReviewConverter;

    public CareApplicationDTO toDTO(CareApplication app) {
        return CareApplicationDTO.builder()
                .idx(app.getIdx())
                .careRequestId(app.getCareRequest().getIdx())
                .providerId(app.getProvider().getIdx())
                .providerName(app.getProvider().getUsername())
                .status(app.getStatus().name())
                .offeredCoins(app.getOfferedCoins())
                .createdAt(app.getCreatedAt())
                .careRequestTitle(app.getCareRequest().getTitle())
                .careRequestStatus(app.getCareRequest().getStatus().name())
                .requesterId(app.getCareRequest().getUser().getIdx())
                .requesterCompletedAt(app.getCareRequest().getRequesterCompletedAt())
                .providerCompletedAt(app.getCareRequest().getProviderCompletedAt())
                .message(app.getMessage())
                .message(app.getMessage())
                .build();
    }
}
