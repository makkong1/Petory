package com.linkup.Petory.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.querydsl.jpa.impl.JPAQueryFactory;

import jakarta.persistence.EntityManager;

/**
 * QueryDSL 설정. JPAQueryFactory를 빈으로 등록해 어댑터 계층에서 타입 안전 동적 쿼리를 작성할 수 있게 한다.
 */
@Configuration
public class QuerydslConfig {

    @Bean
    public JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
        return new JPAQueryFactory(entityManager);
    }
}
