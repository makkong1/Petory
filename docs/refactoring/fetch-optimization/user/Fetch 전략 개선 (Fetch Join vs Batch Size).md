# User 도메인 Fetch 전략 개선

> **규칙**: 단건 상세 → Fetch Join / 페이징 목록 → Batch Size

---

## 요약

| 구분 | 대상 | 전략 | 상태 |
|------|------|------|------|
| 단건 상세 | `getMyProfile`, `getUserWithPets` | Fetch Join | 적용 필요 |
| 페이징 목록 | `getAllUsersWithPaging` | Batch Size | ✅ 이미 적용됨 (socialUsers) |
| Pet 하위 | `getPetsByUserIdx` 등 | Batch Size | ✅ 이미 적용됨 (vaccinations) |

---

## 1. 단건 상세 조회 (Fetch Join)

### 1.1 대상

- `getMyProfile(userId)` — 내 프로필 (`GET /api/users/me`)
- `getUserWithPets(userIdx)` — 사용자 상세 (`GET /api/admin/users/{id}`)
- `getUser(idx)` — `getUserWithPets` 호출 (`GET /api/users/{userId}/profile`)

### 1.2 현재 문제

```
User 1회 + toDTO(socialUsers) 1회 + PetService 조회 1회 = 최소 3회 쿼리
```

```java
// UsersService.getMyProfile (293행)
Users user = usersRepository.findByIdString(userId).orElseThrow(...);
UsersDTO userDTO = usersConverter.toDTO(user);           // socialUsers Lazy Load
List<PetDTO> pets = petService.getPetsByUserId(userId); // 별도 Pet 조회
userDTO.setPets(pets);

// UsersService.getUserWithPets (316행)
Users user = usersRepository.findById(userIdx).orElseThrow(...);
UsersDTO userDTO = usersConverter.toDTO(user);
List<PetDTO> pets = petService.getPetsByUserIdx(userIdx);
userDTO.setPets(pets);
```

### 1.3 적용 위치

| 파일 | 작업 |
|------|------|
| `SpringDataJpaUsersRepository` | `findByIdWithPets(Long)`, `findByIdStringWithPets(String)` 추가 |
| `UsersRepository` | 위 메서드 시그니처 추가 |
| `JpaUsersAdapter` | 위 메서드 구현 |
| `UsersService.getMyProfile` | `findByIdStringWithPets` 사용, PetService 호출 제거 |
| `UsersService.getUserWithPets` | `findByIdWithPets` 사용, PetService 호출 제거 |

### 1.4 Fetch Join 선택 근거

- 단건이라 Fetch Join 부담 적음
- Page와 달리 단건은 `JOIN FETCH` + `DISTINCT` 제약 없음
- 3회 → 1~2회로 쿼리 감소

### 1.5 Fetch Join 미적용 (연관 불필요)

- `getUserById`, `getUserByUsername` — socialUsers만 필요, pets 불필요
- `updateUser`, `restoreUser`, `updateUserStatus` — pets 미사용

---

## 2. 페이징 목록 조회 (Batch Size)

### 2.1 대상

- `getAllUsersWithPaging(page, size)` — 관리자 사용자 목록 (`GET /api/admin/users/paging`)

### 2.2 현재 상태

```java
// UsersService (48행)
Page<Users> userPage = usersRepository.findAll(pageable);
List<UsersDTO> userDTOs = usersConverter.toDTOList(userPage.getContent());
// → toDTO() 내 user.getSocialUsers() 접근 시 Lazy Load
```

**Users.socialUsers**에 `@BatchSize(size=50)` 이미 적용됨 → N+1 해결됨 (100명 기준 3 쿼리)

### 2.3 Batch Size 선택 근거

- Page + OneToMany Fetch Join은 row 중복, count 쿼리 비효율
- `@BatchSize`로 N+1만 제거하는 방식이 더 안전

### 2.4 pets, sanctions

- 페이징 목록에서 `pets`, `sanctions` 미접근 → @BatchSize 불필요

---

## 3. Pet 도메인 (User 하위)

- **Pet.vaccinations** — `@BatchSize(size=50)` 이미 적용됨
- `PetConverter.toDTO()`에서 `pet.getVaccinations()` 접근 시 배치 조회

---

## 4. 적용 불필요 구간

| 구간 | 이유 |
|------|------|
| `UsersDetailsServiceImpl.loadUserByUsername` | UserDetails만 반환, 연관 미접근 |
| `getRoleById` | role 프로젝션만 조회 |
| `createUser`, `updateMyProfile`, `changePassword` 등 | 단건 1회, socialUsers만 접근 |
| `checkNicknameAvailability`, `checkIdAvailability` | isEmpty만, 엔티티 미로드 |

---

## 5. 참고 문서

- [user-backend-performance-optimization.md](../../user/user-backend-performance-optimization.md)
- [social-users-query/troubleshooting.md](../../user/social-users-query/troubleshooting.md)
