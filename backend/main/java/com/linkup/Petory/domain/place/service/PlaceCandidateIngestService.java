package com.linkup.Petory.domain.place.service;

import com.linkup.Petory.domain.place.dto.BatchIngestRequest;
import com.linkup.Petory.domain.place.entity.CandidateDecisionStatus;
import com.linkup.Petory.domain.place.entity.PlaceCandidate;
import com.linkup.Petory.domain.place.repository.PlaceCandidateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceCandidateIngestService {

    private final PlaceCandidateRepository candidateRepo;

    @Transactional
    public int ingest(BatchIngestRequest request) {
        List<PlaceCandidate> toSave = new ArrayList<>();
        for (BatchIngestRequest.CandidateItem item : request.getCandidates()) {
            if (!StringUtils.hasText(item.getName())) continue;
            // same name+address already pending/needs_review → skip to prevent duplicate ingestion
            if (StringUtils.hasText(item.getAddress())
                && candidateRepo.countByRawNameAndRawAddress(item.getName(), item.getAddress()) > 0) {
                continue;
            }
            toSave.add(PlaceCandidate.builder()
                .rawName(item.getName())
                .rawAddress(item.getAddress())
                .lat(item.getLat())
                .lng(item.getLng())
                .collectedFrom(item.getCollectedFrom() != null ? item.getCollectedFrom() : "PET_DATA_API")
                .evidenceText(item.getEvidenceText())
                .decisionStatus(CandidateDecisionStatus.PENDING)
                .build());
        }
        candidateRepo.saveAll(toSave);
        log.info("[PlaceCandidateIngest] 적재 완료 requested={} saved={}", request.getCandidates().size(), toSave.size());
        return toSave.size();
    }
}
