package com.catalogue.verg.core.cache;


import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CacheService {
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${spring.redis.cacheTtl}")
    private long cacheTtl;


    public void putCache(String key, Object object) {
        try {
            String data = objectMapper.writeValueAsString(object);
            redisTemplate.opsForValue().set(key, data, cacheTtl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Error while putting data in Redis cache: {} ", e.getMessage());
        }
    }

    public String getCache(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("Error while getting data from Redis cache: {} ", e.getMessage());
            return null;
        }
    }

    public Long deleteCache(String key) {
        boolean result = redisTemplate.delete(key);
        if (result) {
            log.info("Field deleted successfully from key {}.", key);
        } else {
            log.warn("Field not found in key {}.", key);
        }
        return null;
    }
}
