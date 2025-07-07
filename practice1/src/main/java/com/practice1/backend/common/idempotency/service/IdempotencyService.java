package com.practice1.backend.common.idempotency.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public Optional<ResponseEntity<Object>> getCachedResponse(String key) {
        String value = redisTemplate.opsForValue().get("idem:" + key);
        if (value == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(
                    value,
                    new TypeReference<>() {
                    }
            ));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    public void cacheResponse(String key, ResponseEntity<?> response, long ttlSeconds) {
        try {
            String serialized = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set("idem:" + key, serialized, Duration.ofSeconds(ttlSeconds));
        } catch (JsonProcessingException ignored) {
        }
    }

    public boolean tryProcessing(String key, long ttlSeconds) {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(
                "idem:processing:" + key, "1", Duration.ofSeconds(ttlSeconds));
        return Boolean.TRUE.equals(ok);
    }

    public void clearProcessingKey(String key) {
        redisTemplate.delete("idem:processing:" + key);
    }

}