package com.linkup.Petory.domain.petRecommendation.event;

public record SignalSavedEvent(
        Long userIdx,
        Long signalId,
        String intentDomain,
        String urgency
) {}
