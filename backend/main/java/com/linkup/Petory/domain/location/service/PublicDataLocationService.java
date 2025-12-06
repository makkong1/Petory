package com.linkup.Petory.domain.location.service;

import com.linkup.Petory.domain.location.dto.PublicDataLocationDTO;
import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.web.multipart.MultipartFile;

/**
 * 공공데이터 CSV 파일을 읽어서 LocationService 엔티티로 변환하여 배치 저장하는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicDataLocationService {

    private final LocationServiceRepository locationServiceRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private static final int BATCH_SIZE = 1000; // 한 번에 저장할 개수
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 업로드된 CSV 파일을 받아서 데이터를 파싱하고 배치로 저장
     * 각 배치는 별도 트랜잭션으로 처리되므로 메인 메서드는 트랜잭션 불필요
     * 
     * @param file 업로드된 CSV 파일
     * @return 저장 결과 통계
     */
    public BatchImportResult importFromCsv(MultipartFile file) {
        log.info("공공데이터 CSV 파일 업로드 임포트 시작: {} ({} bytes)",
                file.getOriginalFilename(), file.getSize());

        int totalRead = 0;
        int saved = 0;
        int skipped = 0;
        int duplicate = 0;
        int error = 0;

        Set<String> deduplicationKeys = new HashSet<>();
        List<LocationService> batch = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            // 헤더 라인 읽기
            String headerLine = reader.readLine();
            if (headerLine == null) {
                log.warn("CSV 파일이 비어있습니다.");
                return BatchImportResult.empty();
            }

            String line;
            while ((line = reader.readLine()) != null) {
                totalRead++;

                try {
                    PublicDataLocationDTO dto = parseCsvLine(line);
                    if (dto == null || !isValid(dto)) {
                        skipped++;
                        continue;
                    }

                    // 중복 체크
                    String dedupKey = buildDedupKey(dto);
                    if (deduplicationKeys.contains(dedupKey)) {
                        duplicate++;
                        continue;
                    }

                    // DB 중복 체크
                    if (isDuplicateInDb(dto)) {
                        duplicate++;
                        continue;
                    }

                    // 엔티티 변환 (예외 발생 시 스킵 및 세션 정리)
                    LocationService entity;
                    try {
                        entity = convertToEntity(dto);
                        if (entity == null) {
                            skipped++;
                            continue;
                        }
                        // 엔티티 유효성 검증 (idx는 null이어야 함 - 새 엔티티)
                        if (entity.getIdx() != null) {
                            log.warn("라인 {} 엔티티에 이미 ID가 설정됨: {}", totalRead, entity.getIdx());
                            skipped++;
                            continue;
                        }
                    } catch (Exception e) {
                        error++;
                        log.warn("라인 {} 엔티티 변환 실패: {}", totalRead, e.getMessage());
                        // 세션 정리 (오염 방지)
                        entityManager.clear();
                        continue;
                    }

                    batch.add(entity);
                    deduplicationKeys.add(dedupKey);

                    // 배치 사이즈에 도달하면 저장
                    if (batch.size() >= BATCH_SIZE) {
                        int batchSaved = saveBatch(batch);
                        saved += batchSaved;
                        if (batchSaved < batch.size()) {
                            error += (batch.size() - batchSaved);
                        }
                        log.info("배치 저장 완료: {}개 (총 저장: {}개)", batchSaved, saved);
                        batch.clear();
                        // 세션 정리 (메모리 관리 및 오염 방지)
                        entityManager.clear();
                    }

                } catch (Exception e) {
                    error++;
                    log.warn("라인 {} 파싱 실패: {}", totalRead, e.getMessage());
                    // 예외 발생 시 세션 정리
                    entityManager.clear();
                }
            }

            // 남은 배치 저장
            if (!batch.isEmpty()) {
                int batchSaved = saveBatch(batch);
                saved += batchSaved;
                if (batchSaved < batch.size()) {
                    error += (batch.size() - batchSaved);
                }
                log.info("최종 배치 저장 완료: {}개 (총 저장: {}개)", batchSaved, saved);
                entityManager.clear();
            }

        } catch (IOException e) {
            log.error("CSV 파일 읽기 실패: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("CSV 파일 읽기 실패: " + e.getMessage(), e);
        }

        log.info("공공데이터 임포트 완료 - 총 읽음: {}, 저장: {}, 중복: {}, 스킵: {}, 에러: {}",
                totalRead, saved, duplicate, skipped, error);

        return BatchImportResult.builder()
                .totalRead(totalRead)
                .saved(saved)
                .duplicate(duplicate)
                .skipped(skipped)
                .error(error)
                .build();
    }

    /**
     * CSV 파일 경로를 받아서 데이터를 파싱하고 배치로 저장
     * 각 배치는 별도 트랜잭션으로 처리되므로 메인 메서드는 트랜잭션 불필요
     * 
     * @param csvFilePath CSV 파일 경로
     * @return 저장 결과 통계
     */
    public BatchImportResult importFromCsv(String csvFilePath) {
        log.info("공공데이터 CSV 파일 임포트 시작: {}", csvFilePath);

        int totalRead = 0;
        int saved = 0;
        int skipped = 0;
        int duplicate = 0;
        int error = 0;

        Set<String> deduplicationKeys = new HashSet<>();
        List<LocationService> batch = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(Path.of(csvFilePath))) {
            // 헤더 라인 읽기
            String headerLine = reader.readLine();
            if (headerLine == null) {
                log.warn("CSV 파일이 비어있습니다.");
                return BatchImportResult.empty();
            }

            String line;
            while ((line = reader.readLine()) != null) {
                totalRead++;

                try {
                    PublicDataLocationDTO dto = parseCsvLine(line);
                    if (dto == null || !isValid(dto)) {
                        skipped++;
                        continue;
                    }

                    // 중복 체크
                    String dedupKey = buildDedupKey(dto);
                    if (deduplicationKeys.contains(dedupKey)) {
                        duplicate++;
                        continue;
                    }

                    // DB 중복 체크
                    if (isDuplicateInDb(dto)) {
                        duplicate++;
                        continue;
                    }

                    // 엔티티 변환 (예외 발생 시 스킵 및 세션 정리)
                    LocationService entity;
                    try {
                        entity = convertToEntity(dto);
                        if (entity == null) {
                            skipped++;
                            continue;
                        }
                        // 엔티티 유효성 검증 (idx는 null이어야 함 - 새 엔티티)
                        if (entity.getIdx() != null) {
                            log.warn("라인 {} 엔티티에 이미 ID가 설정됨: {}", totalRead, entity.getIdx());
                            skipped++;
                            continue;
                        }
                    } catch (Exception e) {
                        error++;
                        log.warn("라인 {} 엔티티 변환 실패: {}", totalRead, e.getMessage());
                        // 세션 정리 (오염 방지)
                        entityManager.clear();
                        continue;
                    }

                    batch.add(entity);
                    deduplicationKeys.add(dedupKey);

                    // 배치 사이즈에 도달하면 저장
                    if (batch.size() >= BATCH_SIZE) {
                        int batchSaved = saveBatch(batch);
                        saved += batchSaved;
                        if (batchSaved < batch.size()) {
                            error += (batch.size() - batchSaved);
                        }
                        log.info("배치 저장 완료: {}개 (총 저장: {}개)", batchSaved, saved);
                        batch.clear();
                        // 세션 정리 (메모리 관리 및 오염 방지)
                        entityManager.clear();
                    }

                } catch (Exception e) {
                    error++;
                    log.warn("라인 {} 파싱 실패: {}", totalRead, e.getMessage());
                    // 예외 발생 시 세션 정리
                    entityManager.clear();
                }
            }

            // 남은 배치 저장
            if (!batch.isEmpty()) {
                int batchSaved = saveBatch(batch);
                saved += batchSaved;
                if (batchSaved < batch.size()) {
                    error += (batch.size() - batchSaved);
                }
                log.info("최종 배치 저장 완료: {}개 (총 저장: {}개)", batchSaved, saved);
                entityManager.clear();
            }

        } catch (IOException e) {
            log.error("CSV 파일 읽기 실패: {}", csvFilePath, e);
            throw new RuntimeException("CSV 파일 읽기 실패: " + e.getMessage(), e);
        }

        log.info("공공데이터 임포트 완료 - 총 읽음: {}, 저장: {}, 중복: {}, 스킵: {}, 에러: {}",
                totalRead, saved, duplicate, skipped, error);

        return BatchImportResult.builder()
                .totalRead(totalRead)
                .saved(saved)
                .duplicate(duplicate)
                .skipped(skipped)
                .error(error)
                .build();
    }

    /**
     * CSV 한 라인을 파싱하여 DTO로 변환
     * CSV 형식:
     * 시설명,카테고리1,카테고리2,카테고리3,시도명칭,시군구명칭,법정읍면동명칭,리명칭,번지,도로명이름,건물번호,위도,경도,우편번호,도로명주소,지번주소,전화번호,홈페이지,휴무일,운영시간,주차가능여부,입장가격정보,반려동물동반가능정보,반려동물전용정보,입장가능동물크기,반려동물제한사항,장소실내여부,장소실외여부,기본정보장소설명,애견동반추가요금,최종작성일
     */
    private PublicDataLocationDTO parseCsvLine(String line) {
        if (!StringUtils.hasText(line)) {
            return null;
        }

        // CSV 파싱 (쉼표로 구분, 따옴표 처리)
        List<String> fields = parseCsvFields(line);
        if (fields.size() < 31) {
            log.warn("필드 개수가 부족합니다: {}", fields.size());
            return null;
        }

        try {
            return PublicDataLocationDTO.builder()
                    .facilityName(getField(fields, 0))
                    .category1(getField(fields, 1))
                    .category2(getField(fields, 2))
                    .category3(getField(fields, 3))
                    .sidoName(getField(fields, 4))
                    .sigunguName(getField(fields, 5))
                    .eupmyeondongName(getField(fields, 6))
                    .riName(getField(fields, 7))
                    .bunji(getField(fields, 8))
                    .roadName(getField(fields, 9))
                    .buildingNumber(getField(fields, 10))
                    .latitude(getField(fields, 11))
                    .longitude(getField(fields, 12))
                    .postalCode(getField(fields, 13))
                    .roadAddress(getField(fields, 14))
                    .jibunAddress(getField(fields, 15))
                    .phone(getField(fields, 16))
                    .website(getField(fields, 17))
                    .closedDays(getField(fields, 18))
                    .operatingHours(getField(fields, 19))
                    .parkingAvailable(getField(fields, 20))
                    .entranceFee(getField(fields, 21))
                    .petFriendly(getField(fields, 22))
                    .petOnly(getField(fields, 23))
                    .petSizeLimit(getField(fields, 24))
                    .petRestrictions(getField(fields, 25))
                    .indoor(getField(fields, 26))
                    .outdoor(getField(fields, 27))
                    .description(getField(fields, 28))
                    .petAdditionalFee(getField(fields, 29))
                    .lastUpdatedDate(getField(fields, 30))
                    .build();
        } catch (Exception e) {
            log.warn("DTO 변환 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * CSV 필드를 파싱 (쉼표로 구분, 따옴표 처리)
     */
    private List<String> parseCsvFields(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString().trim()); // 마지막 필드

        return fields;
    }

    private String getField(List<String> fields, int index) {
        if (index >= fields.size()) {
            return null;
        }
        String value = fields.get(index);
        return (value == null || value.isEmpty() || "정보없음".equals(value)) ? null : value;
    }

    /**
     * DTO 유효성 검증
     */
    private boolean isValid(PublicDataLocationDTO dto) {
        // 최소한 시설명과 주소 중 하나는 있어야 함
        if (!StringUtils.hasText(dto.getFacilityName())) {
            return false;
        }
        if (!StringUtils.hasText(dto.getRoadAddress()) && !StringUtils.hasText(dto.getJibunAddress())) {
            return false;
        }
        return true;
    }

    /**
     * 중복 체크용 키 생성
     */
    private String buildDedupKey(PublicDataLocationDTO dto) {
        String name = dto.getFacilityName() != null ? dto.getFacilityName() : "";
        String address = dto.getRoadAddress() != null ? dto.getRoadAddress()
                : (dto.getJibunAddress() != null ? dto.getJibunAddress() : "");
        return name + "|" + address;
    }

    /**
     * DB에 이미 존재하는지 확인
     */
    private boolean isDuplicateInDb(PublicDataLocationDTO dto) {
        // 도로명주소 우선, 없으면 지번주소로 중복 체크
        String address = StringUtils.hasText(dto.getRoadAddress()) ? dto.getRoadAddress() : dto.getJibunAddress();
        if (StringUtils.hasText(address)) {
            return locationServiceRepository.existsByNameAndAddress(
                    dto.getFacilityName(), address);
        }
        return false;
    }

    /**
     * DTO를 엔티티로 변환
     * 구조: 1) 모든 값 검증 및 파싱 먼저 수행, 2) 마지막에 엔티티 생성
     * 이렇게 하면 파싱 실패 시 영속성 컨텍스트에 엔티티가 들어가지 않음
     */
    private LocationService convertToEntity(PublicDataLocationDTO dto) {
        // ============================================
        // 1단계: 모든 값 검증 및 파싱 (엔티티 생성 전)
        // ============================================

        // 위도/경도 파싱
        Double latitude = parseDouble(dto.getLatitude());
        Double longitude = parseDouble(dto.getLongitude());

        // 최종작성일 파싱
        LocalDate lastUpdated = parseDate(dto.getLastUpdatedDate());

        // 반려동물 동반 가능 여부
        Boolean petFriendly = parseBoolean(dto.getPetFriendly());

        // 카테고리 통합 (우선순위: category3 > category2 > category1)
        String category = StringUtils.hasText(dto.getCategory3()) ? dto.getCategory3()
                : StringUtils.hasText(dto.getCategory2()) ? dto.getCategory2() : dto.getCategory1();

        // 주소 통합 (도로명주소 우선, 없으면 지번주소)
        String address = StringUtils.hasText(dto.getRoadAddress()) ? dto.getRoadAddress() : dto.getJibunAddress();

        // Boolean 필드 파싱
        Boolean parkingAvailable = parseBoolean(dto.getParkingAvailable());
        Boolean isPetOnly = parseBoolean(dto.getPetOnly());
        Boolean indoor = parseBoolean(dto.getIndoor());
        Boolean outdoor = parseBoolean(dto.getOutdoor());

        // ============================================
        // 2단계: 엔티티 생성 (모든 검증/파싱 완료 후)
        // ============================================
        // 이 시점에서 예외가 발생해도 영속성 컨텍스트에 들어가지 않음
        return LocationService.builder()
                .name(dto.getFacilityName())
                // category 필드 제거됨
                .category1(dto.getCategory1())
                .category2(dto.getCategory2())
                .category3(dto.getCategory3() != null ? dto.getCategory3() : category) // 기본 카테고리로 사용
                .sido(dto.getSidoName())
                .sigungu(dto.getSigunguName())
                .eupmyeondong(dto.getEupmyeondongName())
                .roadName(dto.getRoadName())
                .address(address) // 도로명주소 우선, 없으면 지번주소
                // detailAddress 필드 제거됨
                .zipCode(cleanZipCode(dto.getPostalCode())) // 소수점 제거
                .latitude(latitude)
                .longitude(longitude)
                .phone(dto.getPhone())
                .website(dto.getWebsite())
                .closedDay(dto.getClosedDays())
                .operatingHours(dto.getOperatingHours()) // 운영시간 문자열로 저장
                .parkingAvailable(parkingAvailable)
                .priceInfo(dto.getEntranceFee())
                .petFriendly(petFriendly)
                .isPetOnly(isPetOnly)
                .petSize(dto.getPetSizeLimit())
                .petRestrictions(dto.getPetRestrictions())
                .petExtraFee(dto.getPetAdditionalFee())
                .indoor(indoor)
                .outdoor(outdoor)
                .description(cleanDescription(dto.getDescription(), category)) // category와 중복 제거
                .lastUpdated(lastUpdated)
                .dataSource("PUBLIC")
                .build();
    }

    private Double parseDouble(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private Boolean parseBoolean(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String upper = value.trim().toUpperCase();
        return "Y".equals(upper) || "YES".equals(upper) || "TRUE".equals(upper);
    }

    /**
     * 우편번호 정리 (소수점 제거)
     */
    private String cleanZipCode(String zipCode) {
        if (!StringUtils.hasText(zipCode)) {
            return null;
        }
        // 소수점 제거 (예: "47596.0" -> "47596")
        String cleaned = zipCode.trim().replace(".0", "").replace(".", "");
        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * description 정리 (category와 중복 제거)
     */
    private String cleanDescription(String description, String category) {
        if (!StringUtils.hasText(description)) {
            return null;
        }
        String desc = description.trim();
        // category와 같거나 간단한 값이면 null
        if (category != null && desc.equals(category)) {
            return null;
        }
        // 너무 짧은 설명도 제거 (2글자 이하)
        if (desc.length() <= 2) {
            return null;
        }
        return desc;
    }

    /**
     * 배치 임포트 결과
     */
    @lombok.Data
    @lombok.Builder
    public static class BatchImportResult {
        private int totalRead;
        private int saved;
        private int duplicate;
        private int skipped;
        private int error;

        public static BatchImportResult empty() {
            return BatchImportResult.builder()
                    .totalRead(0)
                    .saved(0)
                    .duplicate(0)
                    .skipped(0)
                    .error(0)
                    .build();
        }
    }

    /**
     * 배치 저장 (예외 처리 포함)
     * 각 배치는 별도 트랜잭션으로 처리하여 일부 실패해도 다른 배치는 저장됨
     * 
     * @param batch 저장할 엔티티 목록
     * @return 실제 저장된 개수
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    private int saveBatch(List<LocationService> batch) {
        if (batch.isEmpty()) {
            return 0;
        }

        try {
            locationServiceRepository.saveAll(batch);
            return batch.size();
        } catch (Exception e) {
            log.error("배치 저장 실패: {}개 중 일부 저장 실패 - {}", batch.size(), e.getMessage(), e);
            // 세션 정리 (오염 방지)
            entityManager.clear();

            // 개별 저장 시도 (새로운 세션에서)
            int saved = 0;
            List<LocationService> failedEntities = new ArrayList<>();

            for (LocationService entity : batch) {
                try {
                    // 엔티티가 세션에 연결되어 있으면 분리
                    if (entityManager.contains(entity)) {
                        entityManager.detach(entity);
                    }
                    locationServiceRepository.save(entity);
                    saved++;
                } catch (Exception ex) {
                    log.warn("개별 저장 실패: {}", ex.getMessage());
                    failedEntities.add(entity);
                    // 개별 저장 실패 시에도 세션 정리
                    entityManager.clear();
                }
            }

            // 실패한 엔티티가 있으면 한 번 더 정리
            if (!failedEntities.isEmpty()) {
                entityManager.clear();
            }

            return saved;
        }
    }
}
