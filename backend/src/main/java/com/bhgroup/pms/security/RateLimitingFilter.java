package com.bhgroup.pms.security;

import com.bhgroup.pms.common.response.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lightweight in-memory rate limiter for unauthenticated, spam/abuse-prone
 * endpoints (public forms, login, MFA code guessing). Not a substitute for a
 * CDN/WAF-level limiter in a multi-instance deployment, but blocks naive
 * scripted abuse without requiring an external service.
 */
@Slf4j
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private record RateLimitRule(String method, String pathPrefix, int maxRequests, long windowMillis) {
    }

    private record Window(AtomicInteger count, long windowStart) {
    }

    private static final List<RateLimitRule> RULES = List.of(
            new RateLimitRule("POST", "/api/v1/public/leads", 5, 10 * 60 * 1000L),
            new RateLimitRule("POST", "/api/v1/public/reservations", 10, 10 * 60 * 1000L),
            new RateLimitRule("POST", "/api/v1/auth/login", 15, 5 * 60 * 1000L),
            // TOTP codes are 6 digits (1M combinations) and recovery codes
            // are single-use but still guessable without throttling - both
            // endpoints sit behind a valid challenge token but are otherwise
            // unauthenticated, so they need their own limit independent of
            // the plain /login rule above.
            new RateLimitRule("POST", "/api/v1/auth/mfa/verify-login", 10, 5 * 60 * 1000L),
            new RateLimitRule("POST", "/api/v1/auth/mfa/verify-recovery", 10, 5 * 60 * 1000L),
            // Public and costs money per call (Anthropic API) - capped tighter
            // than a normal form submit, generous enough for one real chat
            // session's back-and-forth.
            new RateLimitRule("POST", "/api/v1/assistant/chat", 20, 10 * 60 * 1000L),
            new RateLimitRule("POST", "/api/v1/assistant/handoff", 10, 10 * 60 * 1000L),
            // Trailing slash disambiguates from the POST /api/v1/assistant/chat
            // rule above (different method anyway, but different prefix too) -
            // this covers the visitor's polling GET .../chat/{token}/messages.
            // No GET rule existed anywhere before this; polling is by design
            // far more frequent than a form submit, so the window is wider.
            new RateLimitRule("GET", "/api/v1/assistant/chat/", 60, 5 * 60 * 1000L),
            // Payment webhooks: public by necessity (the gateway calls it with
            // no session of ours), so it needs a ceiling against flooding - but
            // a generous one. All of Stripe's deliveries share one source IP and
            // therefore one bucket, and throttling a real "payment succeeded"
            // event would leave a paid booking unconfirmed. Well above any
            // realistic delivery rate here; a throttled delivery is retried by
            // Stripe anyway, and unsigned payloads are rejected regardless.
            new RateLimitRule("POST", "/api/v1/public/payments/webhook", 120, 60 * 1000L)
    );

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    // Must be the Spring-managed bean (with JavaTimeModule registered), not
    // `new ObjectMapper()` - ApiError carries an Instant field, and the
    // default mapper throws trying to serialize it, turning every 429 this
    // filter emits into an unhandled 500 instead.
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        RateLimitRule rule = matchRule(request);
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = rule.pathPrefix() + "|" + clientIp(request);
        long now = System.currentTimeMillis();

        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStart() > rule.windowMillis()) {
                return new Window(new AtomicInteger(1), now);
            }
            existing.count().incrementAndGet();
            return existing;
        });

        if (window.count().get() > rule.maxRequests()) {
            log.warn("Rate limit exceeded for {} on {}", clientIp(request), rule.pathPrefix());
            writeTooManyRequests(response, request.getRequestURI());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private RateLimitRule matchRule(HttpServletRequest request) {
        for (RateLimitRule rule : RULES) {
            if (rule.method().equalsIgnoreCase(request.getMethod())
                    && request.getRequestURI().startsWith(rule.pathPrefix())) {
                return rule;
            }
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response, String path) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = ApiError.of("RATE_LIMIT_EXCEEDED",
                "Prea multe cereri. Te rugăm să încerci din nou peste câteva minute.", path);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
