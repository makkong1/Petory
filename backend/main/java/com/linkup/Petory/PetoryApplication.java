package com.linkup.Petory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;

import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.web.filter.HiddenHttpMethodFilter;

@SpringBootApplication
@ConfigurationProperties(prefix = "app")
@ServletComponentScan // 이 어노테이션을 추가하여 Servlet 리스너와 필터 자동 등록
@EnableScheduling // 주기적 작업을 활성화
@EnableAsync
@EnableCaching
@org.springframework.data.jpa.repository.config.EnableJpaAuditing // JPA Auditing 활성화
@EnableMethodSecurity(prePostEnabled = true) // @PreAuthorize 작동 활성화
public class PetoryApplication {

	public static void main(String[] args) {
		SpringApplication.run(PetoryApplication.class, args);
	}

	@Bean
	public HiddenHttpMethodFilter hiddenHttpMethodFilter() {

		return new HiddenHttpMethodFilter();
	}

}
