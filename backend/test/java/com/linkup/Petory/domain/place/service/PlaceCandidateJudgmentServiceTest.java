package com.linkup.Petory.domain.place.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkup.Petory.domain.place.entity.*;
import com.linkup.Petory.domain.place.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaceCandidateJudgmentServiceTest {

    @Mock PlaceCandidateRepository candidateRepo;
    @Mock PlaceRepository placeRepo;
    @Mock PublicDataMatcher matcher;

    // NameQualityChecker has no dependencies — use real instance
    private final NameQualityChecker nameChecker = new NameQualityChecker();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private PlaceCandidateJudgmentService sut;

    private PlaceCandidate candidate(String name, String address, Double lat, Double lng) {
        return PlaceCandidate.builder()
            .rawName(name).rawAddress(address).lat(lat).lng(lng)
            .collectedFrom("PET_DATA_API").build();
    }

    @BeforeEach void setUp() {
        // Manual construction — NOT @InjectMocks (NameQualityChecker/ObjectMapper have no mocks)
        sut = new PlaceCandidateJudgmentService(
            candidateRepo, placeRepo, nameChecker, matcher, objectMapper);
        // lenient: these are shared defaults; not every test exercises all branches
        lenient().when(matcher.findStrongMatch(any(), any(), any(), any())).thenReturn(Optional.empty());
        lenient().when(matcher.findMediumMatch(any(), any(), any(), any())).thenReturn(Optional.empty());
        lenient().when(candidateRepo.countByRawNameAndRawAddress(any(), any())).thenReturn(0);
        lenient().when(candidateRepo.countDistinctSourcesByRawNameAndAddress(any(), any())).thenReturn(0);
        lenient().when(candidateRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubPlaceSave(long id) {
        when(placeRepo.save(any())).thenAnswer(inv -> {
            Place p = inv.getArgument(0);
            return Place.builder().id(id).name(p.getName())
                .status(PlaceStatus.PENDING).confidence(p.getConfidence()).build();
        });
    }

    @Test void hardBlacklist_isRejected() {
        PlaceCandidate c = candidate("식사", "서울 마포구 어딘가", 37.5, 126.9);
        sut.judge(c);
        assertThat(c.getDecisionStatus()).isEqualTo(CandidateDecisionStatus.REJECTED);
        verify(placeRepo, never()).save(any());
    }

    @Test void noAddressAndNoCoords_isRejected() {
        PlaceCandidate c = candidate("개떼놀이터", null, null, null);
        sut.judge(c);
        assertThat(c.getDecisionStatus()).isEqualTo(CandidateDecisionStatus.REJECTED);
    }

    @Test void softBlacklist_withAddress_isNeedsReview() {
        // 프렌치(soft) + 주소O + medium match 없음 → score=0.0+0.2+0.1=0.3
        // canAutoApprove 불가(risk_flag) → score=0.3 ≥ 0.3 → NEEDS_REVIEW
        PlaceCandidate c = candidate("프렌치", "서울 마포구 어딘가", 37.5, 126.9);
        sut.judge(c);
        assertThat(c.getDecisionStatus()).isEqualTo(CandidateDecisionStatus.NEEDS_REVIEW);
    }

    @Test void softBlacklist_withStrongMatch_isAutoApproved() {
        // soft blacklist여도 strong match → Gate 2 통과
        stubPlaceSave(1L);
        PlaceCandidate c = candidate("프렌치", "서울 마포구 어딘가", 37.5, 126.9);
        when(matcher.findStrongMatch(any(), any(), any(), any()))
            .thenReturn(Optional.of(new PublicDataMatcher.MatchResult(99L, 0.9, 100.0)));
        sut.judge(c);
        assertThat(c.getDecisionStatus()).isEqualTo(CandidateDecisionStatus.AUTO_APPROVED);
        assertThat(c.getMatchedLocationserviceId()).isEqualTo(99L);
    }

    @Test void strongMatchPublicData_isAutoApproved() {
        stubPlaceSave(1L);
        PlaceCandidate c = candidate("38도씨식당", "서울 마포구 와우산로17길 19-17", 37.549, 126.921);
        when(matcher.findStrongMatch(any(), any(), any(), any()))
            .thenReturn(Optional.of(new PublicDataMatcher.MatchResult(33538L, 0.95, 50.0)));
        sut.judge(c);
        assertThat(c.getDecisionStatus()).isEqualTo(CandidateDecisionStatus.AUTO_APPROVED);
        assertThat(c.getConfidenceScore()).isEqualTo(0.9);
        assertThat(c.getMatchedLocationserviceId()).isEqualTo(33538L);
    }

    @Test void gate4_altBusinessDetected_isNeedsReview() {
        // name(0.1) + addr(0.2) + coord(0.1) + medium(0.3) - altbiz(0.3) = 0.4
        // alt_biz blocks canAutoApprove → NEEDS_REVIEW
        PlaceCandidate c = candidate("큰강아지카페",
            "경기도 남양주시 진건읍 사릉로280번길 7 2층 봉맨션", 37.65, 127.19);
        when(matcher.findMediumMatch(any(), any(), any(), any()))
            .thenReturn(Optional.of(new PublicDataMatcher.MatchResult(34136L, 0.7, 200.0)));
        sut.judge(c);
        assertThat(c.getDecisionStatus()).isEqualTo(CandidateDecisionStatus.NEEDS_REVIEW);
        verify(placeRepo, never()).save(any());
    }

    @Test void gate4_highScore_allConditionsMet_isAutoApproved() {
        // name(0.1) + addr(0.2) + coord(0.1) + medium(0.3) = 0.7, all conditions met
        stubPlaceSave(2L);
        PlaceCandidate c = candidate("개떼놀이터",
            "경기도 남양주시 진건읍 사릉로280번길 7", 37.65, 127.19);
        when(matcher.findMediumMatch(any(), any(), any(), any()))
            .thenReturn(Optional.of(new PublicDataMatcher.MatchResult(34136L, 0.7, 200.0)));
        sut.judge(c);
        assertThat(c.getDecisionStatus()).isEqualTo(CandidateDecisionStatus.AUTO_APPROVED);
    }

    @Test void gate1_twoCharsName_isRejected() {
        PlaceCandidate c = candidate("개떼", "경기도 어딘가", 37.65, 127.19);
        sut.judge(c);
        assertThat(c.getDecisionStatus()).isEqualTo(CandidateDecisionStatus.REJECTED);
    }

    @Test void gate4_scoreBelow0p3_isRejected() {
        // name(0.1) + coord(0.1) + no addr + no medium = 0.2 < 0.3 → REJECTED
        PlaceCandidate c = candidate("개떼놀이터", null, 37.65, 127.19);
        sut.judge(c);
        assertThat(c.getDecisionStatus()).isEqualTo(CandidateDecisionStatus.REJECTED);
    }

    @Test void gate2B_dupThreshold_isAutoApproved() {
        stubPlaceSave(3L);
        lenient().when(candidateRepo.countByRawNameAndRawAddress(any(), any())).thenReturn(3);
        PlaceCandidate c = candidate("개떼놀이터", "경기도 남양주시 어딘가", 37.65, 127.19);
        sut.judge(c);
        assertThat(c.getDecisionStatus()).isEqualTo(CandidateDecisionStatus.AUTO_APPROVED);
        assertThat(c.getConfidenceScore()).isEqualTo(0.9);
        assertThat(c.getMatchedLocationserviceId()).isNull();
    }
}
