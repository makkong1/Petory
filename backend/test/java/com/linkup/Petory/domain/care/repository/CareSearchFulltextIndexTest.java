package com.linkup.Petory.domain.care.repository;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * ====================================================================================
 * care 검색 FULLTEXT 인덱스 회귀 테스트 (2026-07-14)
 * ====================================================================================
 *
 * 배경: /api/care-requests/search 와 /api/admin/care-requests?q= 가 항상 HTTP 500 이었다.
 *       두 쿼리 모두 MATCH(cr.title, cr.description) AGAINST(...) 를 쓰는데
 *       carerequest 에 FULLTEXT 인덱스가 없었기 때문이다.
 *
 *       MySQL 은 FULLTEXT 인덱스 없이 MATCH...AGAINST 를 실행하지 못한다.
 *       느린 게 아니라 에러다("Can't find FULLTEXT index matching the column list").
 *       즉 데이터가 몇 건이든 항상 실패한다.
 *
 * 이 테스트가 지키는 것: V2__care_search_fulltext_index.sql 을 되돌리면 두 검증이 모두 깨진다.
 *
 * 근거: docs/analysis/query-audit/care-2026-07-14.md §2
 *       docs/analysis/query-audit/fixes-2026-07-14.md §1
 * ====================================================================================
 */
@SpringBootTest
@Transactional
class CareSearchFulltextIndexTest {

    @Autowired
    private SpringDataJpaCareRequestRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("carerequest(title, description) 에 FULLTEXT 인덱스가 있어야 한다")
    void fulltextIndexExists() {
        @SuppressWarnings("unchecked")
        List<String> columns = entityManager.createNativeQuery(
                "SELECT COLUMN_NAME FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'carerequest' "
                        + "AND INDEX_TYPE = 'FULLTEXT' ORDER BY SEQ_IN_INDEX")
                .getResultList();

        assertThat(columns)
                .as("FULLTEXT 인덱스가 없으면 MATCH...AGAINST 가 실행 자체를 못 해 검색이 HTTP 500 이 된다")
                .containsExactly("title", "description");
    }

    @Test
    @DisplayName("키워드 검색이 예외 없이 실행된다 (인덱스가 없으면 SQLException 으로 터진다)")
    void searchDoesNotThrow() {
        assertThatCode(() -> repository.searchWithPaging("산책", PageRequest.of(0, 20)).getContent())
                .as("Can't find FULLTEXT index matching the column list 가 나면 인덱스가 사라진 것이다")
                .doesNotThrowAnyException();
    }
}
