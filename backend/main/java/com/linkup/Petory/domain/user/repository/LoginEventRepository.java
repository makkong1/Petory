package com.linkup.Petory.domain.user.repository;

import java.time.LocalDateTime;

import com.linkup.Petory.domain.user.entity.LoginEvent;

public interface LoginEventRepository {
    LoginEvent save(LoginEvent loginEvent);
    long countDistinctUsersBetween(LocalDateTime start, LocalDateTime end);
}
