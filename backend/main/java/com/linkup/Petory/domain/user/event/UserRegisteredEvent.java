package com.linkup.Petory.domain.user.event;

/**
 * 회원가입 완료 이벤트. createUser 커밋 직후 발행되어, 되돌릴 수 없는 후처리(인증 메일 발송·Redis 인증 상태 삭제)를 가입 트랜잭션 밖으로 뺀다. 
 * 커밋이 실패하면 리스너가 실행되지 않아 메일이 나가거나 인증 상태가 지워지지 않는다.
 */
public record UserRegisteredEvent(String userId, String email, boolean isPreVerified) {

}
