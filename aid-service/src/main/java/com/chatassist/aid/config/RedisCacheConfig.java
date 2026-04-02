package com.chatassist.aid.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Redis-backed cache configuration for aid-service.
 *
 * Cache names and TTLs
 * ─────────────────────────────────────────────
 * aid:doctors          – 1 h   (active doctor registry — rarely changes)
 * aid:doctor:slots     – 5 min (available slots per doctorId)
 * aid:doctor:by-id     – 1 h   (single doctor entity by id)
 * ─────────────────────────────────────────────
 * All keys are prefixed with "aid-svc::" to avoid collisions.
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    // ── Cache name constants (also used in service annotations) ──────────────
    public static final String AID_DOCTORS      = "aid:doctors";
    public static final String AID_DOCTOR_SLOTS = "aid:doctor:slots";
    public static final String AID_DOCTOR_BY_ID = "aid:doctor:by-id";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory cf) {
        RedisCacheConfiguration base = defaultConfig();

        Map<String, RedisCacheConfiguration> perCache = Map.of(
            AID_DOCTORS,      base.entryTtl(Duration.ofHours(1)),
            AID_DOCTOR_SLOTS, base.entryTtl(Duration.ofMinutes(5)),
            AID_DOCTOR_BY_ID, base.entryTtl(Duration.ofHours(1))
        );

        return RedisCacheManager.builder(cf)
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(10)))
                .withInitialCacheConfigurations(perCache)
                .build();
    }

    private RedisCacheConfiguration defaultConfig() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.activateDefaultTypingAsProperty(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                "@class");

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        return RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("aid-svc:v2::")
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .disableCachingNullValues();
    }
}

