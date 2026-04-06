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
 * aid:doctors          – 5 min (active doctor registry)
 * aid:doctor:slots     – 5 min (available slots per doctorId)
 * aid:doctor:by-id     – 5 min (single doctor entity by id)
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
            AID_DOCTORS,      base.entryTtl(Duration.ofMinutes(5)),
            AID_DOCTOR_SLOTS, base.entryTtl(Duration.ofMinutes(5)),
            AID_DOCTOR_BY_ID, base.entryTtl(Duration.ofMinutes(5))
        );

        return RedisCacheManager.builder(cf)
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(5)))
                .withInitialCacheConfigurations(perCache)
                .build();
    }

    private RedisCacheConfiguration defaultConfig() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Use EVERYTHING to include type info for all types including Lists, ensuring
        // proper deserialization of both single objects and collections from Redis cache
        objectMapper.activateDefaultTypingAsProperty(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.EVERYTHING,
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

