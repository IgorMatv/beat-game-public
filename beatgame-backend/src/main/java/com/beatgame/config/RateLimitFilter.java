package com.beatgame.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentHashMap<String, Bucket> createBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> joinBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if ("POST".equals(request.getMethod())) {
            String path = request.getRequestURI();
            String ip = clientIp(request);
            Bucket bucket = null;

            if (path.equals("/api/rooms") || path.equals("/api/rooms/solo")) {
                if (createBuckets.size() < 100_000) {
                    bucket = createBuckets.computeIfAbsent(ip, k -> newCreateBucket());
                }
            } else if (path.matches("/api/rooms/[^/]+/join")) {
                if (joinBuckets.size() < 100_000) {
                    bucket = joinBuckets.computeIfAbsent(ip, k -> newJoinBucket());
                }
            }

            if (bucket != null && !bucket.tryConsume(1)) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Too many requests\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private Bucket newCreateBucket() {
        return Bucket.builder()
            .addLimit(Bandwidth.builder()
                .capacity(5)
                .refillGreedy(5, Duration.ofMinutes(1))
                .build())
            .build();
    }

    private Bucket newJoinBucket() {
        return Bucket.builder()
            .addLimit(Bandwidth.builder()
                .capacity(10)
                .refillGreedy(10, Duration.ofMinutes(1))
                .build())
            .build();
    }
}
