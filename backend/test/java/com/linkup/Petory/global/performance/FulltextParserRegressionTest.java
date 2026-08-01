package com.linkup.Petory.global.performance;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * ====================================================================================
 * FULLTEXT 파서 회귀 테스트 (2026-08-01, V12)
 * ====================================================================================
 *
 * <p>
 * 지키는 것: 이 스키마의 <b>모든</b> FULLTEXT 인덱스는 ngram 파서를 쓴다.
 *
 * <p>
 * 기본 InnoDB 파서는 {@code innodb_ft_min_token_size=3} 이라 2글자 한글을 아예 색인하지 않는다.
 * 그래서 파서 하나가 어긋나면 "느려지는" 게 아니라 <b>검색 결과가 사라진다</b>. 실패 유형은 두 가지였다.
 *
 * <ul>
 * <li>chatmessage — 0 건. '시드'가 30,600 행에 들어 있는데 FULLTEXT 결과가 0 건이었다.</li>
 * <li>locationservice — 부분 유실. 쿼리가 {@code AGAINST(CONCAT(:keyword,'*'))} 라
 * 2글자가 긴 토큰의 접두사로 걸리는 것만 살아남아 '카페'가 272 건 중 55 건만 나왔다.
 * 0 건이면 바로 알아채지만 55 건은 정상처럼 보인다 — 이쪽이 더 위험하다.</li>
 * </ul>
 *
 * <p>
 * <b>측정 함정 — performance_schema 로는 이걸 못 잡는다.</b>
 * {@code table_io_waits_summary_by_index_usage} 는 InnoDB FULLTEXT 보조 테이블 접근을
 * 집계하지 않는다. ft_search 를 타는 쿼리로 13,003 행을 받은 직후에도 COUNT_READ 는 0 이었다.
 * 안 써도 0, 써도 0 이라 "이 FULLTEXT 는 안 쓰인다"의 근거가 될 수 없다. 그래서 스키마를 직접 본다.
 *
 * <p>
 * <b>또 하나의 함정 — @Transactional 롤백 테스트로는 FULLTEXT 동작을 검증할 수 없다.</b>
 * InnoDB 는 FULLTEXT 색인을 커밋 시점에 반영하므로, 같은 트랜잭션에서 INSERT 한 행은
 * {@code MATCH...AGAINST} 에 잡히지 않는다(직접 확인: 삽입 직후 같은 트랜잭션에서 0 건).
 * 그래서 채팅 검증은 자기 데이터를 만들어 <b>커밋</b>하고 {@link #cleanup()} 에서 지운다.
 *
 * <p>
 * 근거: V12__chat_location_search_ngram.sql, V9__care_search_ngram.sql
 * ====================================================================================
 */
@SpringBootTest
class FulltextParserRegressionTest {

    /** FULLTEXT 는 커밋해야 색인되므로 JPA 트랜잭션이 아니라 autocommit 으로 쓴다. */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long seededUserIdx;
    private Long seededConversationIdx;

    @AfterEach
    void cleanup() {
        if (seededConversationIdx != null) {
            jdbcTemplate.update("DELETE FROM chatmessage WHERE conversation_idx = ?", seededConversationIdx);
            jdbcTemplate.update("DELETE FROM conversation WHERE idx = ?", seededConversationIdx);
            seededConversationIdx = null;
        }
        if (seededUserIdx != null) {
            jdbcTemplate.update("DELETE FROM users WHERE idx = ?", seededUserIdx);
            seededUserIdx = null;
        }
    }

    @Test
    @DisplayName("스키마의 모든 FULLTEXT 인덱스가 ngram 파서를 쓴다")
    void everyFulltextIndexUsesNgramParser() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT DISTINCT TABLE_NAME FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND INDEX_TYPE = 'FULLTEXT' "
                        + "ORDER BY TABLE_NAME",
                String.class);

        assertThat(tables)
                .as("FULLTEXT 인덱스가 걸린 테이블 — 하나라도 사라졌으면 검색 기능이 통째로 빠진 것이다")
                .containsExactly("board", "carerequest", "chatmessage", "locationservice", "meetup");

        for (String table : tables) {
            String createStatement = jdbcTemplate.queryForObject(
                    "SHOW CREATE TABLE " + table, (rs, rowNum) -> rs.getString(2));

            for (String line : createStatement.split("\n")) {
                if (!line.contains("FULLTEXT KEY")) {
                    continue;
                }
                assertThat(line)
                        .as("%s 의 FULLTEXT 인덱스가 기본 파서다 — 2글자 한글이 색인되지 않아 "
                                + "검색 결과가 조용히 비거나 일부만 나온다: %s", table, line.trim())
                        .contains("WITH PARSER `ngram`");
            }
        }
    }

    @Test
    @DisplayName("장소 검색: 이름에 2글자 키워드가 든 행은 FULLTEXT 로 전부 잡힌다")
    void locationTwoCharKeywordMatchesEveryRowContainingIt() {
        String keyword = "카페";

        Long likeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM locationservice WHERE name LIKE ? AND is_deleted = 0",
                Long.class, "%" + keyword + "%");

        // CI 는 빈 MySQL 에 스키마만 만든다. 데이터가 없으면 이 단언은 0 >= 0 이라 아무것도 안 지킨다.
        assumeTrue(likeCount != null && likeCount > 0,
                "locationservice 에 '" + keyword + "' 를 포함한 행이 없어 건너뛴다 (파서 검증은 위 테스트가 맡는다)");

        Long fulltextCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM locationservice "
                        + "WHERE MATCH(name, description, category1, category2, category3) "
                        + "      AGAINST(CONCAT(?, '*') IN BOOLEAN MODE) "
                        + "  AND is_deleted = 0",
                Long.class, keyword);

        // FULLTEXT 는 name 외 4개 컬럼도 보므로 LIKE(name) 보다 많이 나오는 게 정상이다.
        // 기본 파서에서는 반대로 적게 나왔다 — 272 건 중 55 건.
        assertThat(fulltextCount)
                .as("이름에 '%s' 가 든 행이 %d 건인데 FULLTEXT 는 %d 건만 냈다 — 기본 파서로 되돌아간 것이다",
                        keyword, likeCount, fulltextCount)
                .isGreaterThanOrEqualTo(likeCount);
    }

    @Test
    @DisplayName("채팅 검색: 2글자 한글 메시지가 FULLTEXT 로 검색된다")
    void chatTwoCharKeywordIsSearchable() {
        String marker = UUID.randomUUID().toString();
        seedConversationWithMessages(marker);

        Long twoChar = countMatching("산책");
        Long threeChar = countMatching("강아지");

        assertThat(threeChar)
                .as("3글자는 기본 파서에서도 잡힌다 — 여기서 깨지면 파서가 아니라 시딩·커밋이 잘못된 것이다")
                .isEqualTo(2L);
        assertThat(twoChar)
                .as("2글자 '산책' 이 든 메시지 2건이 FULLTEXT 로 0건이 됐다 — 기본 파서로 되돌아간 것이다")
                .isEqualTo(2L);
    }

    private Long countMatching(String keyword) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chatmessage "
                        + "WHERE conversation_idx = ? "
                        + "  AND MATCH(content) AGAINST(? IN NATURAL LANGUAGE MODE)",
                Long.class, seededConversationIdx, keyword);
    }

    /** 자기 데이터를 만들어 커밋한다 — FULLTEXT 는 커밋 전 행을 보지 못한다. */
    private void seedConversationWithMessages(String marker) {
        jdbcTemplate.update(
                "INSERT INTO users (email, id, password, username) VALUES (?, ?, ?, ?)",
                "ft-" + marker + "@test.local", "ft-" + marker, "x", "ft-test");
        seededUserIdx = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbcTemplate.update("INSERT INTO conversation (conversation_type) VALUES ('DIRECT')");
        seededConversationIdx = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        // '산책'(2글자) 2건 · '강아지'(3글자) 2건 — 한 문장에 둘 다 넣어 건수를 맞춘다.
        jdbcTemplate.update(
                "INSERT INTO chatmessage (conversation_idx, sender_idx, content, is_deleted) VALUES (?, ?, ?, 0)",
                seededConversationIdx, seededUserIdx, "강아지 산책 같이 가실래요");
        jdbcTemplate.update(
                "INSERT INTO chatmessage (conversation_idx, sender_idx, content, is_deleted) VALUES (?, ?, ?, 0)",
                seededConversationIdx, seededUserIdx, "네 강아지 데리고 산책 좋아요");
        jdbcTemplate.update(
                "INSERT INTO chatmessage (conversation_idx, sender_idx, content, is_deleted) VALUES (?, ?, ?, 0)",
                seededConversationIdx, seededUserIdx, "그럼 내일 봬요");
    }
}
