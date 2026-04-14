package com.linkup.Petory.domain.care.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.user.entity.Users;

@ExtendWith(MockitoExtension.class)
class JpaCareRequestAdapterTest {

    @Mock
    private SpringDataJpaCareRequestRepository jpaRepository;

    @InjectMocks
    private JpaCareRequestAdapter adapter;

    private Users minimalUser() {
        return Users.builder().idx(1L).id("u1").build();
    }

    private CareRequest requestWithIdx(long idx) {
        CareRequest cr = CareRequest.builder()
                .user(minimalUser())
                .title("t")
                .description("d")
                .status(CareRequestStatus.OPEN)
                .build();
        cr.setIdx(idx);
        cr.setApplications(new ArrayList<>());
        return cr;
    }

    @Test
    @DisplayName("정상: searchWithPaging은 네이티브 페이지 ID 순서를 유지하고 연관 FETCH 재조회")
    void 정상_searchWithPaging_ID순서_유지_재조회() {
        Pageable pageable = PageRequest.of(0, 10);
        CareRequest raw10 = requestWithIdx(10L);
        CareRequest raw20 = requestWithIdx(20L);
        Page<CareRequest> raw = new PageImpl<>(List.of(raw10, raw20), pageable, 2);
        when(jpaRepository.searchWithPaging("산책", pageable)).thenReturn(raw);

        CareRequest hydrated20 = requestWithIdx(20L);
        CareRequest hydrated10 = requestWithIdx(10L);
        when(jpaRepository.findByIdxInWithAssociations(List.of(10L, 20L)))
                .thenReturn(List.of(hydrated20, hydrated10));

        Page<CareRequest> result = adapter.searchWithPaging("산책", pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(CareRequest::getIdx).containsExactly(10L, 20L);
        verify(jpaRepository).findByIdxInWithAssociations(eq(List.of(10L, 20L)));
    }

    @Test
    @DisplayName("경계: searchWithPaging 빈 페이지면 findByIdxInWithAssociations 호출 안 함")
    void 경계_searchWithPaging_빈페이지() {
        Pageable pageable = PageRequest.of(0, 10);
        when(jpaRepository.searchWithPaging("x", pageable)).thenReturn(Page.empty(pageable));

        Page<CareRequest> result = adapter.searchWithPaging("x", pageable);

        assertThat(result).isEmpty();
        verify(jpaRepository, never()).findByIdxInWithAssociations(any());
    }

    @Test
    @DisplayName("경계: 키워드 공백만이면 FULLTEXT 조회 없이 빈 목록")
    void 경계_키워드_공백_FULLTEXT_미호출() {
        List<CareRequest> r = adapter
                .findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCaseAndIsDeletedFalse("   ", "   ");

        assertThat(r).isEmpty();
        verify(jpaRepository, never()).findIdxByFulltextKeyword(any());
    }
}
