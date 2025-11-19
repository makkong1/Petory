package com.linkup.Petory.global.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisConfig {

        @Value("${spring.redis.host:localhost}")
        private String redisHost;

        @Value("${spring.redis.port:6379}")
        private int redisPort;

        @Value("${spring.redis.password:}")
        private String redisPassword;

        @Value("${spring.redis.database:0}")
        private int redisDatabase;

        /**
         * Jackson ObjectMapper 설정 (Java 8 날짜/시간 타입 지원)
         * 타입 정보를 포함하여 역직렬화 시 정확한 타입으로 변환 가능하도록 설정
         */
        private ObjectMapper createObjectMapper() {
                ObjectMapper mapper = new ObjectMapper();
                mapper.registerModule(new JavaTimeModule());
                mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                // 타입 정보 활성화 (역직렬화 시 정확한 타입으로 변환)
                PolymorphicTypeValidator ptv = LaissezFaireSubTypeValidator.instance;
                mapper.activateDefaultTyping(
                                ptv,
                                com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping.NON_FINAL,
                                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY);
                return mapper;
        }

        /**
         * Java 8 날짜/시간 타입을 지원하는 GenericJackson2JsonRedisSerializer 생성
         */
        private GenericJackson2JsonRedisSerializer createJsonRedisSerializer() {
                return new GenericJackson2JsonRedisSerializer(createObjectMapper());
        }

        /**
         * Redis 연결 설정
         */
        @Bean
        public RedisConnectionFactory redisConnectionFactory() {
                RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
                config.setHostName(redisHost);
                config.setPort(redisPort);
                config.setDatabase(redisDatabase);

                if (redisPassword != null && !redisPassword.isEmpty()) {
                        config.setPassword(redisPassword);
                }

                return new LettuceConnectionFactory(config);
        }

        /**
         * 일반용 RedisTemplate (문자열 기반)
         * - Refresh Token, 블랙리스트 등에 사용
         */
        @Bean
        public RedisTemplate<String, String> customStringRedisTemplate(RedisConnectionFactory connectionFactory) {
                RedisTemplate<String, String> template = new RedisTemplate<>();
                template.setConnectionFactory(connectionFactory);
                template.setKeySerializer(new StringRedisSerializer());
                template.setValueSerializer(new StringRedisSerializer());
                template.setHashKeySerializer(new StringRedisSerializer());
                template.setHashValueSerializer(new StringRedisSerializer());
                template.afterPropertiesSet();
                return template;
        }

        /**
         * 객체 저장용 RedisTemplate (JSON 직렬화)
         * - 게시글 캐싱, 사용자 정보 캐싱 등에 사용
         */
        @Bean
        public RedisTemplate<String, Object> objectRedisTemplate(RedisConnectionFactory connectionFactory) {
                RedisTemplate<String, Object> template = new RedisTemplate<>();
                template.setConnectionFactory(connectionFactory);
                template.setKeySerializer(new StringRedisSerializer());
                template.setValueSerializer(createJsonRedisSerializer());
                template.setHashKeySerializer(new StringRedisSerializer());
                template.setHashValueSerializer(createJsonRedisSerializer());
                template.afterPropertiesSet();
                return template;
        }

        /**
         * 알림용 RedisTemplate
         * - 사용자별 알림 리스트 저장
         * - Key: "notification:{userId}", Value: 알림 리스트
         */
        @Bean
        public RedisTemplate<String, Object> notificationRedisTemplate(RedisConnectionFactory connectionFactory) {
                RedisTemplate<String, Object> template = new RedisTemplate<>();
                template.setConnectionFactory(connectionFactory);
                template.setKeySerializer(new StringRedisSerializer());
                template.setValueSerializer(createJsonRedisSerializer());
                template.setHashKeySerializer(new StringRedisSerializer());
                template.setHashValueSerializer(createJsonRedisSerializer());
                template.afterPropertiesSet();
                return template;
        }

        /**
         * 좋아요/싫어요 배치 동기화용 RedisTemplate
         * - Key: "reaction:board:{boardId}" 또는 "reaction:comment:{commentId}"
         * - Value: 좋아요/싫어요 카운트 (임시 저장)
         */
        @Bean
        public RedisTemplate<String, Long> reactionCountRedisTemplate(RedisConnectionFactory connectionFactory) {
                RedisTemplate<String, Long> template = new RedisTemplate<>();
                template.setConnectionFactory(connectionFactory);
                template.setKeySerializer(new StringRedisSerializer());
                template.setValueSerializer(createJsonRedisSerializer());
                template.afterPropertiesSet();
                return template;
        }

        /**
         * Spring Cache Manager 설정
         * - @Cacheable 어노테이션 사용 시 적용
         */
        @Bean
        public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
                GenericJackson2JsonRedisSerializer jsonSerializer = createJsonRedisSerializer();
                
                RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofMinutes(30)) // 기본 TTL: 30분
                                .serializeKeysWith(
                                                RedisSerializationContext.SerializationPair
                                                                .fromSerializer(new StringRedisSerializer()))
                                .serializeValuesWith(RedisSerializationContext.SerializationPair
                                                .fromSerializer(jsonSerializer))
                                .disableCachingNullValues(); // null 값 캐싱 방지

                // 게시글 목록 캐시: 10분
                RedisCacheConfiguration boardListConfig = RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofMinutes(10))
                                .serializeKeysWith(
                                                RedisSerializationContext.SerializationPair
                                                                .fromSerializer(new StringRedisSerializer()))
                                .serializeValuesWith(RedisSerializationContext.SerializationPair
                                                .fromSerializer(jsonSerializer))
                                .disableCachingNullValues();

                // 게시글 상세 캐시: 1시간
                RedisCacheConfiguration boardDetailConfig = RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofHours(1))
                                .serializeKeysWith(
                                                RedisSerializationContext.SerializationPair
                                                                .fromSerializer(new StringRedisSerializer()))
                                .serializeValuesWith(RedisSerializationContext.SerializationPair
                                                .fromSerializer(jsonSerializer))
                                .disableCachingNullValues();

                // 사용자 정보 캐시: 1시간
                RedisCacheConfiguration userConfig = RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofHours(1))
                                .serializeKeysWith(
                                                RedisSerializationContext.SerializationPair
                                                                .fromSerializer(new StringRedisSerializer()))
                                .serializeValuesWith(RedisSerializationContext.SerializationPair
                                                .fromSerializer(jsonSerializer))
                                .disableCachingNullValues();

                return RedisCacheManager.builder(connectionFactory)
                                .cacheDefaults(defaultConfig)
                                .withCacheConfiguration("boardList", boardListConfig)
                                .withCacheConfiguration("boardDetail", boardDetailConfig)
                                .withCacheConfiguration("user", userConfig)
                                .build();
        }
}
