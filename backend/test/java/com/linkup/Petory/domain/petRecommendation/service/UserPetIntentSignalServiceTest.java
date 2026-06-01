package com.linkup.Petory.domain.petRecommendation.service;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkup.Petory.domain.petRecommendation.dto.PetIntentAnalyzeResponse;
import com.linkup.Petory.domain.petRecommendation.dto.UserPetIntentSignalResponse;
import com.linkup.Petory.domain.petRecommendation.entity.UserPetIntentSignal;
import com.linkup.Petory.domain.petRecommendation.repository.UserPetIntentSignalRepository;

@ExtendWith(MockitoExtension.class)
class UserPetIntentSignalServiceTest {

    @InjectMocks
    private UserPetIntentSignalService signalService;

    @Mock
    private UserPetIntentSignalRepository signalRepository;

    @Spy
    private ObjectMapper objectMapper;

    // ===== saveIfConfident =====
    @Test
    @DisplayName("confidence < 0.6 이면 signal 저장하지 않는다")
    void saveIfConfident_belowThreshold_doesNotSave() {
        PetIntentAnalyzeResponse analysis = analysisOf("MEDICAL", 0.55);

        signalService.saveIfConfident(1L, "COMMUNITY", 10L, analysis);

        verify(signalRepository, never()).save(any());
        verify(signalRepository, never()).existsByUserIdxAndIntentDomainAndExpiresAtAfter(any(), any(), any());
    }

    @Test
    @DisplayName("analysis == null 이면 signal 저장하지 않는다")
    void saveIfConfident_nullAnalysis_doesNotSave() {
        signalService.saveIfConfident(1L, "COMMUNITY", 10L, null);

        verify(signalRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 userIdx + intentDomain 유효 signal이 존재하면 저장 스킵한다 (R3)")
    void saveIfConfident_duplicateDomain_skipsInsert() {
        PetIntentAnalyzeResponse analysis = analysisOf("MEDICAL", 0.87);
        when(signalRepository.existsByUserIdxAndIntentDomainAndExpiresAtAfter(
                eq(1L), eq("MEDICAL"), any(LocalDateTime.class)))
                .thenReturn(true);

        signalService.saveIfConfident(1L, "COMMUNITY", 10L, analysis);

        verify(signalRepository, never()).save(any());
    }

    @Test
    @DisplayName("새 도메인 signal이고 confidence >= 0.6 이면 저장한다")
    void saveIfConfident_newDomainAboveThreshold_saves() throws Exception {
        PetIntentAnalyzeResponse analysis = analysisOf("GROOMING", 0.91);
        when(signalRepository.existsByUserIdxAndIntentDomainAndExpiresAtAfter(
                eq(1L), eq("GROOMING"), any(LocalDateTime.class)))
                .thenReturn(false);

        signalService.saveIfConfident(1L, "CARE", 99L, analysis);

        ArgumentCaptor<UserPetIntentSignal> captor = ArgumentCaptor.forClass(UserPetIntentSignal.class);
        verify(signalRepository).save(captor.capture());

        UserPetIntentSignal saved = captor.getValue();
        assertThat(saved.getUserIdx()).isEqualTo(1L);
        assertThat(saved.getIntentDomain()).isEqualTo("GROOMING");
        assertThat(saved.getConfidence()).isEqualTo(0.91);
        assertThat(saved.getSourceType()).isEqualTo("CARE");
        assertThat(saved.getSourceId()).isEqualTo(99L);
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("sourceId가 null인 LOCATION_SEARCH signal도 저장된다")
    void saveIfConfident_locationSearch_nullSourceId_saves() {
        PetIntentAnalyzeResponse analysis = analysisOf("MEDICAL", 0.75);
        when(signalRepository.existsByUserIdxAndIntentDomainAndExpiresAtAfter(any(), any(), any()))
                .thenReturn(false);

        signalService.saveIfConfident(2L, "LOCATION_SEARCH", null, analysis);

        ArgumentCaptor<UserPetIntentSignal> captor = ArgumentCaptor.forClass(UserPetIntentSignal.class);
        verify(signalRepository).save(captor.capture());
        assertThat(captor.getValue().getSourceId()).isNull();
    }

    // ===== getActiveSignals =====
    @Test
    @DisplayName("getActiveSignals 는 PageRequest.of(0, 10) 으로 호출된다 (R2)")
    void getActiveSignals_passesPageableLimitOf10() {
        when(signalRepository.findActiveByUser(eq(5L), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of());

        signalService.getActiveSignals(5L);

        ArgumentCaptor<Pageable> pageCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(signalRepository).findActiveByUser(eq(5L), any(LocalDateTime.class), pageCaptor.capture());

        Pageable captured = pageCaptor.getValue();
        assertThat(captured.getPageNumber()).isZero();
        assertThat(captured.getPageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("getActiveSignals 는 signal 목록을 UserPetIntentSignalResponse 로 변환한다")
    void getActiveSignals_returnsResponseList() {
        UserPetIntentSignal signal = signalEntity("MEDICAL", "MEDICAL_CONCERN", 0.88);
        when(signalRepository.findActiveByUser(any(), any(), any()))
                .thenReturn(List.of(signal));

        List<UserPetIntentSignalResponse> result = signalService.getActiveSignals(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIntentDomain()).isEqualTo("MEDICAL");
        assertThat(result.get(0).getCardMessage()).contains("건강");
    }

    // ===== helpers =====
    private PetIntentAnalyzeResponse analysisOf(String domain, double confidence) {
        try {
            var mapper = new ObjectMapper();
            String json = String.format(
                    "{\"intentDomain\":\"%s\",\"intent\":\"%s_NEED\",\"confidence\":%f,"
                    + "\"recommendedCategories\":[],\"keywords\":[],\"intentTags\":[],\"urgency\":\"NORMAL\",\"message\":\"test\"}",
                    domain, domain, confidence);
            return mapper.readValue(json, PetIntentAnalyzeResponse.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private UserPetIntentSignal signalEntity(String domain, String intent, double confidence) {
        return UserPetIntentSignal.builder()
                .userIdx(1L)
                .sourceType("COMMUNITY")
                .sourceId(1L)
                .intentDomain(domain)
                .intent(intent)
                .recommendedCategories("[]")
                .confidence(confidence)
                .intentTags("[]")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
    }
}
