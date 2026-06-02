package com.linkup.Petory.domain.user.entity;

/** 사용자 권한 계층. USER < SERVICE_PROVIDER < ADMIN < MASTER. */
public enum Role {
    USER,
    SERVICE_PROVIDER,
    ADMIN,
    MASTER
}
