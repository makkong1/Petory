package com.linkup.Petory.domain.petRecommendation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PetRecommendTagMatchingPerformanceTest {

    @Test
    @DisplayName("List.contains 기반 태그 교집합과 HashSet 기반 교집합은 결과가 같다")
    void listContainsAndHashSetMatching_returnSameMatchCount() {
        List<String> locationTags = tags("tag-", 1_000);
        List<String> intentTags = List.of("tag-10", "tag-20", "tag-30", "missing-1", "missing-2");

        MatchResult listResult = matchByListContains(locationTags, intentTags);
        MatchResult setResult = matchByHashSet(locationTags, intentTags);

        assertThat(listResult.matched()).isEqualTo(3);
        assertThat(setResult.matched()).isEqualTo(listResult.matched());
    }

    @Test
    @DisplayName("태그 수가 커질수록 HashSet lookup은 List 순차 비교보다 비용 증가가 작다")
    void hashSetLookupScalesBetterThanListContains() {
        List<String> locationTags = tags("tag-", 1_000);
        List<String> intentTags = List.of(
                "tag-100", "tag-200", "tag-300", "tag-400", "tag-500",
                "tag-600", "tag-700", "tag-800", "tag-900", "missing");

        MatchResult listResult = matchByListContains(locationTags, intentTags);
        MatchResult setResult = matchByHashSet(locationTags, intentTags);

        assertThat(setResult.matched()).isEqualTo(listResult.matched());
        assertThat(listResult.cost()).isGreaterThan(setResult.cost() * 100);
    }

    private List<String> tags(String prefix, int count) {
        List<String> tags = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            tags.add(prefix + i);
        }
        return tags;
    }

    private MatchResult matchByListContains(List<String> locationTags, List<String> intentTags) {
        long matched = 0;
        long comparisons = 0;

        for (String intentTag : intentTags) {
            for (String locationTag : locationTags) {
                comparisons++;
                if (locationTag.equals(intentTag)) {
                    matched++;
                    break;
                }
            }
        }

        return new MatchResult(matched, comparisons);
    }

    private MatchResult matchByHashSet(List<String> locationTags, List<String> intentTags) {
        Set<String> tagSet = new HashSet<>(locationTags);
        long matched = 0;
        long lookups = 0;

        for (String intentTag : intentTags) {
            lookups++;
            if (tagSet.contains(intentTag)) {
                matched++;
            }
        }

        return new MatchResult(matched, lookups);
    }

    private record MatchResult(long matched, long cost) {
    }
}
