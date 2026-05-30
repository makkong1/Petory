package com.linkup.Petory.domain.place.service;

import com.linkup.Petory.domain.place.dto.CandidateApproveRequest;
import com.linkup.Petory.domain.place.dto.CandidateRejectRequest;
import com.linkup.Petory.domain.place.dto.PlaceCandidateDto;
import com.linkup.Petory.domain.place.entity.*;
import com.linkup.Petory.domain.place.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PlaceCandidateAdminService {

    private final PlaceCandidateRepository candidateRepo;
    private final PlaceRepository placeRepo;

    public Page<PlaceCandidateDto> listByStatus(CandidateDecisionStatus status, Pageable pageable) {
        return candidateRepo.findByDecisionStatus(status, pageable).map(PlaceCandidateDto::from);
    }

    @Transactional
    public PlaceCandidateDto approve(Long id, CandidateApproveRequest req, String adminUsername) {
        PlaceCandidate c = candidateRepo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // idempotent: already has a linked place → return as-is
        if (c.getMatchedPlaceId() != null) {
            return PlaceCandidateDto.from(c);
        }

        // state guard
        if (c.getDecisionStatus() != CandidateDecisionStatus.PENDING
            && c.getDecisionStatus() != CandidateDecisionStatus.NEEDS_REVIEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "approve 허용 상태: PENDING, NEEDS_REVIEW. 현재: " + c.getDecisionStatus());
        }

        String name = Optional.ofNullable(req.getOverrideName())
            .filter(StringUtils::hasText).orElse(c.getRawName());
        String address = Optional.ofNullable(req.getOverrideAddress())
            .filter(StringUtils::hasText).orElse(c.getRawAddress());
        String category = Optional.ofNullable(req.getOverrideCategory())
            .filter(StringUtils::hasText).orElse(null);
        Double lat = req.getOverrideLat() != null ? req.getOverrideLat() : c.getLat();
        Double lng = req.getOverrideLng() != null ? req.getOverrideLng() : c.getLng();

        Place place = placeRepo.save(Place.builder()
            .name(name).address(address).lat(lat).lng(lng).category(category)
            .status(PlaceStatus.PENDING)
            .primarySource(c.getCollectedFrom())
            .confidence(c.getConfidenceScore())
            .build());

        c.setDecisionStatus(CandidateDecisionStatus.ADMIN_APPROVED);
        c.setMatchedPlaceId(place.getId());
        c.setReviewedBy(adminUsername);
        c.setReviewedAt(LocalDateTime.now());
        candidateRepo.save(c);
        return PlaceCandidateDto.from(c);
    }

    @Transactional
    public PlaceCandidateDto reject(Long id, CandidateRejectRequest req, String adminUsername) {
        PlaceCandidate c = candidateRepo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (c.getDecisionStatus() != CandidateDecisionStatus.PENDING
            && c.getDecisionStatus() != CandidateDecisionStatus.NEEDS_REVIEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "reject 허용 상태: PENDING, NEEDS_REVIEW. 현재: " + c.getDecisionStatus());
        }

        c.setDecisionStatus(CandidateDecisionStatus.REJECTED);
        c.setRejectionReason(req.getRejectionReason());
        c.setReviewedBy(adminUsername);
        c.setReviewedAt(LocalDateTime.now());
        candidateRepo.save(c);
        return PlaceCandidateDto.from(c);
    }
}
