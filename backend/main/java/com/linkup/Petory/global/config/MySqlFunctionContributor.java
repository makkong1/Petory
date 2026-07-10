package com.linkup.Petory.global.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.BasicType;
import org.hibernate.type.StandardBasicTypes;

/**
 * MySQL FULLTEXT {@code MATCH(...) AGAINST(...)} 구문을 JPA Criteria API({@code cb.function})에서
 * 쓸 수 있도록 패턴 함수로 등록한다.
 * <p>
 * {@code cb.function()}은 "이름(인자, 인자, ...)" 형태만 만들 수 있어 괄호가 두 개로 분리된
 * {@code MATCH(cols) AGAINST(expr)} 구문을 표현하지 못한다. 이 함수를 등록해두면
 * {@code cb.function("matchAgainst", Double.class, title, content, keyword)} 호출이
 * {@code match(title,content) against (keyword)}로 올바르게 렌더링된다.
 * <p>
 * {@code META-INF/services/org.hibernate.boot.model.FunctionContributor}에 SPI로 등록되어 있다.
 */
public class MySqlFunctionContributor implements FunctionContributor {

    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        BasicType<Double> doubleType = functionContributions.getTypeConfiguration()
                .getBasicTypeRegistry()
                .resolve(StandardBasicTypes.DOUBLE);

        functionContributions.getFunctionRegistry()
                .registerPattern("matchAgainst", "match(?1,?2) against (?3)", doubleType);
    }
}
