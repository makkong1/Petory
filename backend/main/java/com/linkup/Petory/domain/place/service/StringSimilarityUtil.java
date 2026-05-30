package com.linkup.Petory.domain.place.service;

public final class StringSimilarityUtil {

    private StringSimilarityUtil() {}

    /**
     * Levenshtein 거리 기반 정규화 유사도 [0.0, 1.0].
     * 1.0 = 동일, 0.0 = 완전히 다름.
     */
    public static double normalized(String a, String b) {
        if (a == null && b == null) return 1.0;
        if (a == null || b == null) return 0.0;
        String s1 = a.trim().toLowerCase();
        String s2 = b.trim().toLowerCase();
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;
        return 1.0 - (double) levenshtein(s1, s2) / maxLen;
    }

    private static int levenshtein(String s, String t) {
        int m = s.length(), n = t.length();
        int[] prev = new int[n + 1], curr = new int[n + 1];
        for (int j = 0; j <= n; j++) prev[j] = j;
        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            for (int j = 1; j <= n; j++) {
                curr[j] = s.charAt(i - 1) == t.charAt(j - 1)
                    ? prev[j - 1]
                    : 1 + Math.min(prev[j - 1], Math.min(prev[j], curr[j - 1]));
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[n];
    }
}
