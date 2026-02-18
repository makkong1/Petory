# 트랜잭션 어노테이션 위치 가이드

## 📋 개요

**작성 일시**: 2026-02-10  
**목적**: Service와 Repository에서 `@Transactional` 어노테이션을 어디에 둘지 결정하는 가이드

---

## 🎯 일반 원칙

### 1. Service 계층에서 트랜잭션 관리 (권장) ⭐

**원칙**: 비즈니스 로직의 단위로 트랜잭션을 관리

```java
@Service
@Transactional(readOnly = true)  // 클래스 레벨: 기본값
public class MeetupService {
    
    @Transactional  // 메서드 레벨: 쓰기 작업
    public MeetupParticipantsDTO joinMeetup(Long meetupIdx, String userId) {
        // 여러 Repository 호출을 하나의 트랜잭션으로 처리
        Meetup meetup = meetupRepository.findById(meetupIdx);
        int updated = meetupRepository.incrementParticipantsIfAvailable(meetupIdx);
        MeetupParticipants participant = meetupParticipantsRepository.save(...);
        return converter.toDTO(participant);
    }
}
```

**장점**:
- ✅ 비즈니스 로직 단위로 트랜잭션 관리
- ✅ 여러 Repository 호출을 하나의 트랜잭션으로 묶을 수 있음
- ✅ 트랜잭션 범위가 명확함
- ✅ 롤백 정책을 비즈니스 로직에 맞게 설정 가능

**단점**:
- ❌ Service 메서드가 길어질 수 있음

---

### 2. Repository 계층에서 트랜잭션 관리 (비권장)

**원칙**: 각 Repository 메서드마다 트랜잭션 관리

```java
public interface SpringDataJpaMeetupRepository extends JpaRepository<Meetup, Long> {
    
    @Modifying
    @Transactional  // Repository에 트랜잭션 선언
    @Query("UPDATE Meetup m SET m.currentParticipants = m.currentParticipants + 1 ...")
    int incrementParticipantsIfAvailable(@Param("meetupIdx") Long meetupIdx);
}
```

**장점**:
- ✅ 각 메서드가 독립적으로 트랜잭션 관리

**단점**:
- ❌ 여러 Repository 호출을 하나의 트랜잭션으로 묶기 어려움
- ❌ 비즈니스 로직 단위로 트랜잭션 관리 불가
- ❌ 트랜잭션 전파(propagation) 제어 어려움
- ❌ 코드 중복 가능성

---

## 🔍 현재 코드 분석

### 현재 구조

```java
// Service 계층
@Service
@Transactional(readOnly = true)  // 클래스 레벨 기본값
public class MeetupService {
    
    @Transactional  // 메서드 레벨: 쓰기 작업
    public MeetupParticipantsDTO joinMeetup(Long meetupIdx, String userId) {
        Meetup meetup = meetupRepository.findById(meetupIdx);
        
        if (!meetup.getOrganizer().getIdx().equals(userIdx)) {
            // @Modifying 쿼리 호출
            int updated = meetupRepository.incrementParticipantsIfAvailable(meetupIdx);
            entityManager.refresh(meetup);
        }
        
        MeetupParticipants participant = meetupParticipantsRepository.save(...);
        return converter.toDTO(participant);
    }
}

// Repository 계층
public interface SpringDataJpaMeetupRepository extends JpaRepository<Meetup, Long> {
    
    @Modifying  // @Transactional 없음
    @Query("UPDATE Meetup m SET m.currentParticipants = m.currentParticipants + 1 ...")
    int incrementParticipantsIfAvailable(@Param("meetupIdx") Long meetupIdx);
}
```

### 분석 결과

✅ **현재 구조가 올바름**

**이유**:
1. Service에 `@Transactional`이 있으므로 `incrementParticipantsIfAvailable()` 호출도 같은 트랜잭션 내에서 실행됨
2. `@Modifying` 쿼리는 트랜잭션이 필요하지만, 호출하는 메서드에 트랜잭션이 있으면 그 트랜잭션을 사용함
3. 여러 Repository 호출(`findById`, `incrementParticipantsIfAvailable`, `save`)이 하나의 트랜잭션으로 묶임

---

## 📊 상황별 가이드

### 상황 1: Service에서 여러 Repository 호출 (현재 상황) ✅

**패턴**: Service에 `@Transactional`, Repository에는 없음

```java
@Service
@Transactional(readOnly = true)
public class MeetupService {
    
    @Transactional
    public void complexBusinessLogic() {
        // 여러 Repository 호출을 하나의 트랜잭션으로
        repository1.save(...);
        repository2.update(...);
        repository3.delete(...);
        // 모두 성공하거나 모두 롤백
    }
}
```

**이유**: 비즈니스 로직 단위로 트랜잭션 관리

---

### 상황 2: Repository 메서드가 독립적으로 사용될 때

**패턴**: Repository에 `@Transactional` 추가

```java
public interface SpringDataJpaMeetupRepository extends JpaRepository<Meetup, Long> {
    
    @Modifying
    @Transactional  // 독립적으로 사용될 수 있으므로 Repository에 선언
    @Query("UPDATE Meetup m SET m.currentParticipants = m.currentParticipants + 1 ...")
    int incrementParticipantsIfAvailable(@Param("meetupIdx") Long meetupIdx);
}
```

**사용 예시**:
```java
// Service에 트랜잭션이 없어도 동작
public void someMethod() {
    // 트랜잭션이 없어도 Repository의 @Transactional이 트랜잭션 생성
    meetupRepository.incrementParticipantsIfAvailable(meetupIdx);
}
```

**주의**: Service에도 `@Transactional`이 있으면 기존 트랜잭션을 사용함 (전파)

---

### 상황 3: @Modifying 쿼리만 있는 경우

**패턴**: Repository에 `@Transactional` 추가 (방어적 코딩)

```java
public interface SpringDataJpaMeetupRepository extends JpaRepository<Meetup, Long> {
    
    @Modifying
    @Transactional  // @Modifying은 트랜잭션이 필수이므로 방어적으로 추가
    @Query("UPDATE Meetup m SET m.currentParticipants = m.currentParticipants + 1 ...")
    int incrementParticipantsIfAvailable(@Param("meetupIdx") Long meetupIdx);
}
```

**이유**:
- `@Modifying` 쿼리는 트랜잭션이 필수
- 호출하는 메서드에 트랜잭션이 없으면 예외 발생
- Repository에 `@Transactional`을 추가하면 방어적으로 동작

---

## ✅ 권장 사항

### 현재 코드에 대한 권장 사항

**옵션 1: 현재 구조 유지 (권장)** ⭐

```java
// Service에만 @Transactional
@Service
@Transactional(readOnly = true)
public class MeetupService {
    
    @Transactional
    public MeetupParticipantsDTO joinMeetup(...) {
        // 여러 Repository 호출
    }
}

// Repository에는 @Transactional 없음
public interface SpringDataJpaMeetupRepository {
    @Modifying
    @Query("...")
    int incrementParticipantsIfAvailable(...);
}
```

**장점**:
- 비즈니스 로직 단위로 트랜잭션 관리
- 여러 Repository 호출을 하나의 트랜잭션으로 묶을 수 있음
- 트랜잭션 범위가 명확함

---

**옵션 2: 방어적 코딩 (선택사항)**

```java
// Repository에 @Transactional 추가 (방어적)
public interface SpringDataJpaMeetupRepository {
    @Modifying
    @Transactional  // 방어적으로 추가
    @Query("...")
    int incrementParticipantsIfAvailable(...);
}
```

**장점**:
- Repository 메서드가 독립적으로 사용되어도 동작
- `@Modifying` 쿼리의 트랜잭션 요구사항 명시

**단점**:
- Service에 `@Transactional`이 있으면 중복 (하지만 문제 없음, 기존 트랜잭션 사용)

---

## 🔄 트랜잭션 전파 (Propagation)

### 기본 동작

```java
@Service
@Transactional  // 트랜잭션 시작
public class MeetupService {
    
    public void method1() {
        // 트랜잭션 1 시작
        repository.methodA();  // 트랜잭션 1 사용
        
        if (someCondition) {
            repository.methodB();  // 트랜잭션 1 사용 (같은 트랜잭션)
        }
    }
}
```

### REQUIRES_NEW 사용 예시

```java
@Service
@Transactional
public class MeetupService {
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void independentOperation() {
        // 새로운 트랜잭션 시작 (독립적)
        // 실패해도 호출한 트랜잭션에 영향 없음
    }
}
```

---

## 📝 결론

### 현재 코드 평가

✅ **현재 구조가 올바름**

**이유**:
1. Service에 `@Transactional`이 있어서 비즈니스 로직 단위로 트랜잭션 관리
2. 여러 Repository 호출이 하나의 트랜잭션으로 묶임
3. `@Modifying` 쿼리는 호출하는 메서드의 트랜잭션을 사용

### 선택적 개선 사항

**방어적 코딩을 원한다면**:
- Repository에 `@Transactional` 추가 가능
- 하지만 현재 구조로도 충분히 동작함

---

## 🔗 관련 문서

- [중복 쿼리 제거 리팩토링](./duplicate-query-removal.md)
- [백엔드 성능 최적화 문서](./backend-performance-optimization.md)
- [Spring Data JPA @Modifying 문서](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#jpa.modifying-queries)
