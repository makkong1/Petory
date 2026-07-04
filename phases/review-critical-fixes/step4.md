# Step 4 — OAuth2Service private `@Transactional` 제거

## 배경

`domain/user/service/OAuth2Service.java`:

- line 48~49: `@Transactional public TokenResponse processOAuth2Login(...)` — 정상.
- line 161~162: `@Transactional private Users createOrLinkUser(...)` — **문제**.
- line 84: `processOAuth2Login`이 `createOrLinkUser`를 같은 클래스 내부에서 호출 (self-invocation).

Spring AOP 프록시는 private 메서드와 내부 호출을 가로채지 못하므로 line 161의 `@Transactional`은 **조용히 무시된다**. 현재는 호출자 `processOAuth2Login`의 트랜잭션 안에서 실행되므로 동작은 안전하지만, 나중에 호출자의 `@Transactional`이 제거되면 트랜잭션 없이 유저 생성/소셜 연결이 실행되는 함정이 남는다. 죽은 어노테이션은 "여기 트랜잭션이 걸려있다"는 잘못된 신호를 주므로 제거한다.

## 수정 대상 파일

- `backend/main/java/com/linkup/Petory/domain/user/service/OAuth2Service.java`

## 수정 내용

```java
// Before (line 161~162)
@Transactional
private Users createOrLinkUser(OAuth2User oauth2User, Provider provider, String providerId, String email,
        String name) {

// After — 어노테이션 제거, 트랜잭션 경계 소유자를 주석으로 명시
// 트랜잭션 경계는 public 진입점 processOAuth2Login이 소유한다.
// (private 메서드의 @Transactional은 Spring 프록시가 가로채지 못해 무시됨)
private Users createOrLinkUser(OAuth2User oauth2User, Provider provider, String providerId, String email,
        String name) {
```

- `@Transactional` import는 line 48에서 여전히 사용 중이므로 **제거하지 않는다**.
- 다른 private 메서드에 같은 패턴이 있는지 클래스 내 확인: `grep -B1 "private" OAuth2Service.java | grep -A1 "@Transactional"` — 있으면 동일하게 제거.

## 가드레일

- 어노테이션 제거와 주석 추가 외에 어떤 로직도 변경하지 않는다.

## AC (Acceptance Criteria)

```bash
./gradlew compileJava
./gradlew test --tests '*OAuth2*'   # OAuth2ServiceConcurrencyTest 포함, MySQL+Redis 필요
```
