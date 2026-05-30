package com.linkup.Petory.domain.place.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.util.Set;

@Component
public class NameQualityChecker {

    public enum NameCheckResult { HARD_REJECT, SOFT_RISK, OK }

    private static final Set<String> HARD_BLACKLIST = Set.of(
        "식사", "기념일", "맛집", "주말", "데이트", "공원", "산책"
    );

    private static final Set<String> SOFT_BLACKLIST = Set.of(
        "프렌치", "벚꽃", "감성", "라운지", "살롱"
    );

    public NameCheckResult check(String name) {
        if (!StringUtils.hasText(name)) return NameCheckResult.HARD_REJECT;
        String t = name.trim();
        if (HARD_BLACKLIST.contains(t)) return NameCheckResult.HARD_REJECT;
        if (SOFT_BLACKLIST.contains(t)) return NameCheckResult.SOFT_RISK;
        if (t.length() <= 2) return NameCheckResult.HARD_REJECT;
        if (t.matches("[^가-힣a-zA-Z0-9]+")) return NameCheckResult.HARD_REJECT;
        return NameCheckResult.OK;
    }

    /** 4글자 이상 + OK 상태. Gate 2 경로B / Gate 3 name_quality 점수에 사용. */
    public boolean isGoodQuality(String name) {
        return check(name) == NameCheckResult.OK
            && name != null && name.trim().length() >= 4;
    }
}
