package com.linkup.Petory.global.security;

/** @PreAuthorize 등에서 사용하는 역할 상수. ROLE_ 접두사 포함 버전과 미포함 버전을 제공한다. */
public final class RoleConstants {

    private RoleConstants() {}

    public static final String ROLE_USER = "ROLE_USER";
    public static final String ROLE_SERVICE_PROVIDER = "ROLE_SERVICE_PROVIDER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    public static final String ROLE_MASTER = "ROLE_MASTER";

    public static final String ADMIN = "ADMIN";
    public static final String MASTER = "MASTER";
}
