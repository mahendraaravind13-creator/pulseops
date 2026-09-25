package com.pulseops.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulseops.tenant.TenantAuth;
import com.pulseops.tenant.TenantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisCacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheConfig.class);

    /**
     * API-key cache: JSON values (readable with redis-cli, unlike Java serialization), 15-minute TTL,
     * null results never cached.
     */
    @Bean
    RedisCacheManagerBuilderCustomizer apiKeyCache(ObjectMapper mapper) {
        var serializer = new Jackson2JsonRedisSerializer<>(mapper, TenantAuth.class);
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(15))
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(serializer));
        return builder -> builder.withCacheConfiguration(TenantService.API_KEY_CACHE, config);
    }

    /**
     * If Redis is down, cache reads and writes fail. Instead of failing the request, log and fall through to the
     * database: the cache is an optimisation, so its outage should cost latency, not availability.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache get failed ({}), using database: {}", cache.getName(), e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Cache put failed ({}): {}", cache.getName(), e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.error("Cache evict failed ({}): {}", cache.getName(), e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.error("Cache clear failed ({}): {}", cache.getName(), e.getMessage());
            }
        };
    }
}
