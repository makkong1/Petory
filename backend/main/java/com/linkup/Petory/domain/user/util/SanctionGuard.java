package com.linkup.Petory.domain.user.util;

import java.util.function.Supplier;

import com.linkup.Petory.domain.user.entity.Users;

/** 여러 도메인에 흩어진 "현재 유저 제재 상태면 차단" 검사를 한 줄로 묶는다. */
public final class SanctionGuard {

    private SanctionGuard() {
    }

    public static void check(Users user, Supplier<? extends RuntimeException> exceptionSupplier) {
        if (user.isSanctioned()) {
            throw exceptionSupplier.get();
        }
    }
}
