package com.practice1.backend.common.idempotency.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.practice1.backend.common.idempotency.annotation.Idempotent;
import com.practice1.backend.common.idempotency.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotencyAspect {
    private final HttpServletRequest request;
    private final IdempotencyService idempotencyService;

    @Around("@annotation(com.practice1.backend.common.idempotency.annotation.Idempotent)")
    public Object checkIdempotency(ProceedingJoinPoint joinPoint) throws Throwable {
        String idempotencyKey = request.getHeader("Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            return ResponseEntity.badRequest().body("Missing Idempotency-Key header");
        }

        Optional<ResponseEntity<Object>> cached = idempotencyService.getCachedResponse(idempotencyKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        long ttl = ((MethodSignature) joinPoint.getSignature()).getMethod()
                .getAnnotation(Idempotent.class).ttl();
        boolean processing = idempotencyService.tryProcessing(idempotencyKey, ttl);
        if (!processing) {
            return ResponseEntity.status(409).body("Idempotent request already processing");
        }

        Object result;
        try {
            result = joinPoint.proceed();
        } finally {
            idempotencyService.clearProcessingKey(idempotencyKey);
        }

        if (result instanceof ResponseEntity<?> response) {
            idempotencyService.cacheResponse(idempotencyKey, response, ttl);
        }
        return result;
    }

}
