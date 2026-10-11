package com.becho.saas.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.*;
import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(GenericJacksonJsonRedisSerializer.builder()
                                .enableUnsafeDefaultTyping()
                                .build()))

                .disableCachingNullValues();

        // Per-cache TTLs: catalog/theme/settings change rarely; storefront-domain often.
        Map<String, RedisCacheConfiguration> configs = Map.of(
                "storefront-domain", base.entryTtl(Duration.ofMinutes(30)),
                "product", base.entryTtl(Duration.ofMinutes(10)),
                "theme-settings", base.entryTtl(Duration.ofMinutes(30)),
                "store-settings", base.entryTtl(Duration.ofMinutes(30)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(5)))
                .withInitialCacheConfigurations(configs)
                .build();
    }

    /**
     * Redis-down fallback: Redis বন্ধ থাকলে বা Upstash-এর daily limit শেষ
     * হলে cache error যেন API ভেঙে না ফেলে। Log করে চুপচাপ ছেড়ে দিলে
     * Spring method-টা database থেকে চালায় — ধীর হবে, কিন্তু চলবে।
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Redis GET failed (cache={}, key={}) — falling back to DB: {}",
                        cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
                log.warn("Redis PUT failed (cache={}, key={}) — continuing without cache: {}",
                        cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Redis EVICT failed (cache={}, key={}) — TTL will clean up: {}",
                        cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException ex, Cache cache) {
                log.warn("Redis CLEAR failed (cache={}) — TTL will clean up: {}",
                        cache.getName(), ex.getMessage());
            }
        };
    }
}
