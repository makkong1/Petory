package com.linkup.Petory.domain.care.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.care.converter.CareRequestConverter;
import com.linkup.Petory.domain.care.dto.CareRequestDTO;
import com.linkup.Petory.domain.care.entity.CareApplication;
import com.linkup.Petory.domain.care.entity.CareApplicationStatus;
import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.repository.CareApplicationRepository;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.file.entity.AttachmentFile;
import com.linkup.Petory.domain.file.entity.FileTargetType;
import com.linkup.Petory.domain.file.repository.AttachmentFileRepository;
import com.linkup.Petory.domain.file.service.AttachmentFileService;
import com.linkup.Petory.domain.user.entity.Pet;
import com.linkup.Petory.domain.user.entity.PetType;
import com.linkup.Petory.domain.user.entity.PetVaccination;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.PetRepository;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ====================================================================================
 * Care 요청 목록 N+1 재검증 (2026-07-12)
 * ====================================================================================
 *
 * troubleshooting/care/care-request-n-plus-one-analysis.md (~2,400 쿼리 → 4~5개)의
 * 수치를 다시 실행해 재현성을 확인한다.
 *
 * - Before: 문서가 기술한 "해결 전 코드"를 그대로 재현 (JOIN FETCH 없이 조회 →
 *   applications/vaccinations lazy 개별 접근, File 개별 조회)
 * - After: 실제 프로덕션 경로 그대로 호출
 *   (CareRequestRepository.findAllActiveRequests() + CareRequestConverter.toDTOList())
 *
 * ====================================================================================
 */
@SpringBootTest
@Transactional
class CareRequestNPlusOneReverifyTest {

    @Autowired
    private CareRequestRepository careRequestRepository;

    @Autowired
    private CareApplicationRepository careApplicationRepository;

    @Autowired
    private AttachmentFileRepository attachmentFileRepository;

    @Autowired
    private AttachmentFileService attachmentFileService;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private PetRepository petRepository;

    @Autowired
    private CareRequestConverter careRequestConverter;

    @PersistenceContext
    private EntityManager entityManager;

    private Users requester;
    private List<Users> providers;
    private List<CareRequest> testRequests;

    private static final int REQUEST_COUNT = 100;

    @BeforeEach
    void setUp() {
        requester = usersRepository.save(Users.builder()
                .id("care_requester")
                .username("care_requester")
                .email("care_requester@test.com")
                .nickname("케어요청자")
                .password("password")
                .role(Role.USER)
                .build());

        providers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            providers.add(usersRepository.save(Users.builder()
                    .id("care_provider" + i)
                    .username("care_provider" + i)
                    .email("care_provider" + i + "@test.com")
                    .nickname("케어제공자" + i)
                    .password("password")
                    .role(Role.SERVICE_PROVIDER)
                    .build()));
        }

        testRequests = new ArrayList<>();
        int providerIndex = 0;
        for (int i = 0; i < REQUEST_COUNT; i++) {
            Pet pet = petRepository.save(Pet.builder()
                    .user(requester)
                    .petName("펫" + i)
                    .petType(PetType.DOG)
                    .isNeutered(false)
                    .build());

            // Pet당 예방접종 기록 2개 (vaccinations lazy N+1 재현용)
            entityManager.persist(PetVaccination.builder()
                    .pet(pet)
                    .vaccineName("종합백신")
                    .vaccinatedAt(LocalDate.now().minusMonths(6))
                    .isDeleted(false)
                    .build());
            entityManager.persist(PetVaccination.builder()
                    .pet(pet)
                    .vaccineName("광견병")
                    .vaccinatedAt(LocalDate.now().minusMonths(3))
                    .isDeleted(false)
                    .build());

            // Pet당 첨부파일 1개 (File N+1 재현용)
            entityManager.persist(AttachmentFile.builder()
                    .targetType(FileTargetType.PET)
                    .targetIdx(pet.getIdx())
                    .filePath("pet_" + i + ".jpg")
                    .fileType("image/jpeg")
                    .build());

            CareRequest request = CareRequest.builder()
                    .user(requester)
                    .pet(pet)
                    .title("케어 요청 " + i)
                    .description("설명 " + i)
                    .date(LocalDateTime.now().plusDays(1))
                    .status(CareRequestStatus.OPEN)
                    .isDeleted(false)
                    .build();
            request = careRequestRepository.save(request);
            testRequests.add(request);

            // 요청당 지원 2개 (applications lazy N+1 재현용)
            for (int a = 0; a < 2; a++) {
                CareApplication application = CareApplication.builder()
                        .careRequest(request)
                        .provider(providers.get(providerIndex % providers.size()))
                        .status(CareApplicationStatus.PENDING)
                        .message("지원합니다")
                        .build();
                careApplicationRepository.saveAndFlush(application);
                providerIndex++;
            }
        }

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("Care 요청 목록: N+1(해결 전 재현) vs 실제 프로덕션 경로 비교")
    void reproduceNPlusOneVsProductionPath() {
        Statistics stats = getStatistics();
        stats.clear();

        // ===== Before: 문서가 기술한 해결 전 코드 재현 =====
        // JOIN FETCH 없이 CareRequest 조회 → applications/vaccinations lazy 개별 접근, File 개별 조회
        entityManager.flush();
        entityManager.clear();
        System.gc();

        long beforeStart = System.currentTimeMillis();
        List<CareRequestDTO> beforeResult = getRequestsWithIndividualQueries();
        long beforeElapsed = System.currentTimeMillis() - beforeStart;
        long beforeQueryCount = stats.getQueryExecutionCount();

        System.out.println("\n[Before] JOIN FETCH 없음 + lazy 개별 접근");
        System.out.println("  쿼리 수: " + beforeQueryCount);
        System.out.println("  실행 시간: " + beforeElapsed + " ms");

        stats.clear();
        entityManager.flush();
        entityManager.clear();
        System.gc();

        // ===== After: 실제 프로덕션 경로 그대로 =====
        long afterStart = System.currentTimeMillis();
        List<CareRequest> requests = careRequestRepository.findAllActiveRequests().stream()
                .filter(r -> r.getUser().getIdx().equals(requester.getIdx()))
                .collect(Collectors.toList());
        List<CareRequestDTO> afterResult = careRequestConverter.toDTOList(requests);
        long afterElapsed = System.currentTimeMillis() - afterStart;
        long afterQueryCount = stats.getQueryExecutionCount();

        System.out.println("\n[After] 실제 프로덕션 경로 (findAllActiveRequests + toDTOList)");
        System.out.println("  쿼리 수: " + afterQueryCount);
        System.out.println("  실행 시간: " + afterElapsed + " ms");

        System.out.println("\n=== 결과 ===");
        System.out.println("쿼리 수: " + beforeQueryCount + " → " + afterQueryCount
                + " (" + String.format("%.1f", (1 - (double) afterQueryCount / beforeQueryCount) * 100) + "% 감소)");
        System.out.println("실행 시간: " + beforeElapsed + "ms → " + afterElapsed + "ms");

        assertThat(beforeResult).hasSize(REQUEST_COUNT);
        assertThat(afterResult).hasSize(REQUEST_COUNT);
        assertThat(afterQueryCount).isLessThan(beforeQueryCount);
    }

    /**
     * 해결 전 코드 재현: JOIN FETCH 없이 조회 → applications/vaccinations lazy 개별
     * 접근, File 개별 조회
     */
    private List<CareRequestDTO> getRequestsWithIndividualQueries() {
        Statistics stats = getStatistics();

        TypedQuery<CareRequest> query = entityManager.createQuery(
                "SELECT cr FROM CareRequest cr WHERE cr.isDeleted = false AND cr.user.idx = :userId ORDER BY cr.createdAt DESC",
                CareRequest.class);
        query.setParameter("userId", requester.getIdx());
        List<CareRequest> requests = query.getResultList();
        long afterMain = stats.getQueryExecutionCount();

        List<CareRequestDTO> results = new ArrayList<>();
        for (CareRequest request : requests) {
            // applications lazy 접근 → 요청마다 개별 쿼리 (N+1)
            int applicationCount = request.getApplications() != null ? request.getApplications().size() : 0;

            Pet pet = request.getPet();
            List<com.linkup.Petory.domain.file.dto.FileDTO> files = List.of();
            int vaccinationCount = 0;
            if (pet != null) {
                // File 개별 조회 (N+1)
                files = attachmentFileService.getAttachments(FileTargetType.PET, pet.getIdx());
                // vaccinations lazy 접근 → Pet마다 개별 쿼리 (N+1)
                vaccinationCount = pet.getVaccinations() != null ? pet.getVaccinations().size() : 0;
            }

            results.add(CareRequestDTO.builder()
                    .idx(request.getIdx())
                    .title(request.getTitle())
                    .applicationCount(applicationCount)
                    .build());

            // vaccinationCount/files는 N+1 재현이 목적이므로 결과에는 카운트만 반영해도 충분
            if (vaccinationCount < 0 || files.size() < 0) {
                throw new IllegalStateException("unreachable");
            }
        }
        long afterLoop = stats.getQueryExecutionCount();
        System.out.println("  [세부] 메인쿼리 이후: " + afterMain + ", 루프(applications+file+vaccinations) 이후: "
                + afterLoop + " (루프에서 발생: " + (afterLoop - afterMain) + "개)");
        return results;
    }

    private Statistics getStatistics() {
        SessionFactory sessionFactory = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class);
        Statistics stats = sessionFactory.getStatistics();
        stats.setStatisticsEnabled(true);
        return stats;
    }
}
