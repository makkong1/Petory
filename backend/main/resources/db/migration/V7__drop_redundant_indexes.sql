-- 중복/죽은 인덱스 정리 (쓰기 비용만 깎아먹는 인덱스 제거).
-- 근거: docker DB(petory-mysql)에 V1~V6 적용 후 실제 인덱스를 감사해서 발견.

-- ── pet_coin_escrow ──────────────────────────────────────────────────────────
-- care_request_idx 하나에 UNIQUE 인덱스가 uk_care_request / uk_escrow_care_request
-- 두 개가 걸려 있다(V1 baseline 시점부터 존재, 레거시 스크립트 중복 적용 흔적).
-- PetCoinEscrow.careRequest 는 @JoinColumn(unique = true) 하나만 요구하므로
-- 둘 중 하나만 있으면 충분하다. uk_care_request 를 남기고 uk_escrow_care_request 를 제거한다.
ALTER TABLE pet_coin_escrow
    DROP INDEX uk_escrow_care_request;

-- ── pets ─────────────────────────────────────────────────────────────────────
-- V3 에서 idx_pets_type_deleted (pet_type, is_deleted) 복합 인덱스를 추가했지만
-- 기존 단일 컬럼 인덱스 idx_pets_type / idx_pets_deleted 를 지우지 않았다.
-- 코드베이스 전체에서 Pet 조회는 petType 단독 또는 petType+isDeleted 조합뿐이고
-- (findByPetTypeAndIsDeletedFalse), isDeleted 단독 조회는 없다.
-- 즉 두 인덱스 모두 idx_pets_type_deleted 의 리딩 컬럼(pet_type)으로 완전히 대체되어
-- 이제는 쓰기 비용만 발생시키는 죽은 인덱스다.
ALTER TABLE pets
    DROP INDEX idx_pets_type,
    DROP INDEX idx_pets_deleted;
