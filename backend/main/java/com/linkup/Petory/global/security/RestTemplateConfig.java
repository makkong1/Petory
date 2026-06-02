package com.linkup.Petory.global.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/** RestTemplate 빈 등록 설정. 외부 HTTP 호출(네이버 OAuth2 등)에 사용된다. */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
