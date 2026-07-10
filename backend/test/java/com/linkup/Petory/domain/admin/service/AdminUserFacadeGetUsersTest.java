package com.linkup.Petory.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.user.dto.UserPageResponseDTO;
import com.linkup.Petory.domain.user.dto.UsersDTO;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.UserStatus;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

/**
 * AdminUserFacade.getUsers() 검증.
 *
 * 실제 필터 로직은 Facade가 아니라 SpringDataJpaUsersRepository.findAllForAdmin()의
 * "(:param IS NULL OR ...)" JPQL에 있다. Mock으로는 이 조건식이 실제로 옳게 동작하는지
 * 검증할 수 없으므로 다른 AdminXFacadeTest들과 달리 실제 DB를 사용하는 통합 테스트로 작성한다.
 *
 * 개발 DB에 이미 존재할 수 있는 다른 사용자와 섞이지 않도록 username에 UUID 태그를 심고,
 * 총 건수 대신 특정 사용자의 포함/제외 여부로 검증한다.
 */
@SpringBootTest
@Transactional
class AdminUserFacadeGetUsersTest {

    @Autowired
    private AdminUserFacade adminUserFacade;
    @Autowired
    private UsersRepository usersRepository;

    private String tag;
    private Users admin;
    private Users activeUser;
    private Users suspendedUser;
    private Users bannedUser;
    private Users searchTarget;

    @BeforeEach
    void setUp() {
        tag = "tag" + UUID.randomUUID().toString().substring(0, 8);

        admin = usersRepository.save(Users.builder()
                .id(tag + "-admin")
                .username(tag + "-admin")
                .email(tag + "-admin@test.com")
                .nickname(tag + "관리자")
                .password("password")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build());

        activeUser = usersRepository.save(Users.builder()
                .id(tag + "-active")
                .username(tag + "-active")
                .email(tag + "-active@test.com")
                .nickname(tag + "활성유저")
                .password("password")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build());

        suspendedUser = usersRepository.save(Users.builder()
                .id(tag + "-suspended")
                .username(tag + "-suspended")
                .email(tag + "-suspended@test.com")
                .nickname(tag + "정지유저")
                .password("password")
                .role(Role.USER)
                .status(UserStatus.SUSPENDED)
                .build());

        bannedUser = usersRepository.save(Users.builder()
                .id(tag + "-banned")
                .username(tag + "-banned")
                .email(tag + "-banned@test.com")
                .nickname(tag + "밴유저")
                .password("password")
                .role(Role.USER)
                .status(UserStatus.BANNED)
                .build());

        searchTarget = usersRepository.save(Users.builder()
                .id(tag + "-findme")
                .username(tag + "-findme")
                .email(tag + "-findme@test.com")
                .nickname(tag + "찾아줘")
                .password("password")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build());
    }

    private List<String> resultUsernames(String role, String status, String keyword) {
        UserPageResponseDTO page = adminUserFacade.getUsers(role, status, keyword, 0, 1000);
        return page.getUsers().stream().map(UsersDTO::getUsername).collect(Collectors.toList());
    }

    @Test
    @DisplayName("정상: role로 필터링하면 해당 역할만 반환한다")
    void 정상_role_필터링() {
        List<String> usernames = resultUsernames("ADMIN", null, tag);

        assertThat(usernames).containsExactly(admin.getUsername());
    }

    @Test
    @DisplayName("정상: status로 필터링하면 해당 상태만 반환한다")
    void 정상_status_필터링() {
        List<String> usernames = resultUsernames(null, "SUSPENDED", tag);

        assertThat(usernames).containsExactly(suspendedUser.getUsername());
    }

    @Test
    @DisplayName("정상: keyword가 username에 매칭되면 반환한다")
    void 정상_keyword_username_매칭() {
        List<String> usernames = resultUsernames(null, null, searchTarget.getUsername());

        assertThat(usernames).containsExactly(searchTarget.getUsername());
    }

    @Test
    @DisplayName("정상: keyword가 nickname에 매칭되면 반환한다")
    void 정상_keyword_nickname_매칭() {
        List<String> usernames = resultUsernames(null, null, searchTarget.getNickname());

        assertThat(usernames).containsExactly(searchTarget.getUsername());
    }

    @Test
    @DisplayName("경계: 필터가 전부 없으면 전체 조회된다")
    void 경계_필터없으면_전체조회() {
        List<String> usernames = resultUsernames(null, null, tag);

        assertThat(usernames).containsExactlyInAnyOrder(
                admin.getUsername(), activeUser.getUsername(), suspendedUser.getUsername(),
                bannedUser.getUsername(), searchTarget.getUsername());
    }

    @Test
    @DisplayName("경계: 존재하지 않는 role 문자열은 예외 없이 빈 결과를 반환한다")
    void 경계_존재하지않는_role값은_빈결과() {
        List<String> usernames = resultUsernames("NOT_A_REAL_ROLE", null, tag);

        assertThat(usernames).isEmpty();
    }
}
