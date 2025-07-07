/*package com.practice1.backend.web.filter;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {
    private final RedisTemplate<String, String> redis;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws IOException, ServletException {
        if ("POST".equals(req.getMethod()) && req.getRequestURI().equals("/transfer")) {
            String key = req.getHeader("Idempotency-Key");
            if (key == null) {
                res.sendError(400, "Missing idempotency key");
                return;
            }
            Boolean ok = redis.opsForValue()
                    .setIfAbsent("idem:" + key, "1", Duration.ofMinutes(5));
            if (Boolean.FALSE.equals(ok)) {
                res.sendError(409, "Duplicate request");
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
*/
