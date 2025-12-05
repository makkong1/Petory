package com.linkup.Petory.domain.location.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.time.LocalTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 운영시간 문자열을 파싱하여 LocalTime으로 변환하는 유틸리티
 * 예: "월~금 09:00~18:00" -> openingTime: 09:00, closingTime: 18:00
 */
@Slf4j
public class OperatingHoursParser {

    private static final Pattern TIME_RANGE_PATTERN = Pattern.compile(
            "(\\d{1,2}):(\\d{2})\\s*~\\s*(\\d{1,2}):(\\d{2})"
    );
    
    private static final Pattern SINGLE_TIME_PATTERN = Pattern.compile(
            "(\\d{1,2}):(\\d{2})"
    );

    /**
     * 운영시간 문자열에서 시작시간과 종료시간을 추출
     * 
     * @param operatingHours 운영시간 문자열 (예: "월~금 09:00~18:00", "09:00~18:00", "09:00")
     * @return OperatingHoursResult (openingTime, closingTime)
     */
    public static OperatingHoursResult parse(String operatingHours) {
        if (!StringUtils.hasText(operatingHours)) {
            return OperatingHoursResult.empty();
        }

        String cleaned = operatingHours.trim();

        // "09:00~18:00" 형식 찾기
        Matcher rangeMatcher = TIME_RANGE_PATTERN.matcher(cleaned);
        if (rangeMatcher.find()) {
            try {
                int startHour = Integer.parseInt(rangeMatcher.group(1));
                int startMinute = Integer.parseInt(rangeMatcher.group(2));
                int endHour = Integer.parseInt(rangeMatcher.group(3));
                int endMinute = Integer.parseInt(rangeMatcher.group(4));

                LocalTime openingTime = LocalTime.of(startHour, startMinute);
                LocalTime closingTime = LocalTime.of(endHour, endMinute);

                return OperatingHoursResult.of(openingTime, closingTime);
            } catch (Exception e) {
                log.warn("운영시간 파싱 실패: {}", operatingHours, e);
            }
        }

        // 단일 시간 형식 "09:00" 찾기
        Matcher singleMatcher = SINGLE_TIME_PATTERN.matcher(cleaned);
        if (singleMatcher.find()) {
            try {
                int hour = Integer.parseInt(singleMatcher.group(1));
                int minute = Integer.parseInt(singleMatcher.group(2));
                LocalTime time = LocalTime.of(hour, minute);
                return OperatingHoursResult.of(time, null);
            } catch (Exception e) {
                log.warn("운영시간 파싱 실패: {}", operatingHours, e);
            }
        }

        return OperatingHoursResult.empty();
    }

    public static class OperatingHoursResult {
        private final LocalTime openingTime;
        private final LocalTime closingTime;

        private OperatingHoursResult(LocalTime openingTime, LocalTime closingTime) {
            this.openingTime = openingTime;
            this.closingTime = closingTime;
        }

        public static OperatingHoursResult empty() {
            return new OperatingHoursResult(null, null);
        }

        public static OperatingHoursResult of(LocalTime openingTime, LocalTime closingTime) {
            return new OperatingHoursResult(openingTime, closingTime);
        }

        public LocalTime getOpeningTime() {
            return openingTime;
        }

        public LocalTime getClosingTime() {
            return closingTime;
        }
    }
}

