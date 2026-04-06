package com.chatassist.gateway.filter;

import com.chatassist.common.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;

import java.util.Optional;


/**
 * Global authentication filter for the gateway.
 *
 * <p>Authentication strategy (checked in order):
 * <ol>
 *   <li><b>JWT Bearer token</b> – if an {@code Authorization: Bearer <token>} header is present
 *       the token is validated via {@link JwtUtil}. A valid token grants access; an invalid /
 *       expired token is immediately rejected with 401 (no session fallback).</li>
 *   <li><b>Server-side session</b> (Redis/Spring Session) – if no Authorization header is present
 *       the request session is resolved by {@link GatewaySessionService}.</li>
 * </ol>
 *
 * <p>Public routes (login, register, assets…) bypass authentication entirely.
 */
@Configuration
public class AuthenticationFilterConfig {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationFilterConfig.class);

    /** Downstream header carrying the authenticated user-id (overwritten by this filter). */
    private static final String AUTH_USER_ID_HEADER = "X-User-Id";

    /** Downstream header carrying the authenticated username (overwritten by this filter). */
    private static final String AUTH_USERNAME_HEADER = "X-Username";

    private final GatewaySessionService sessionService;

    public AuthenticationFilterConfig(GatewaySessionService sessionService) {
        this.sessionService = sessionService;
    }

    // ── Public-route tables ──────────────────────────────────────────────────

    /** Paths that must match exactly to be considered public. */
    private static final String[] EXACT_PUBLIC_ROUTES = {
        "/",
        "/index.html",
        "/ws-chat/info",
        "/api/users/login",
        "/api/users/register",
        "/api/users/assistants",
        "/actuator/health",
        "/default-user.svg",
        "/favicon.ico"
    };

    /** Path prefixes that are considered public (e.g. static assets, Swagger). */
    private static final String[] PREFIX_PUBLIC_ROUTES = {
        "/v3/api-docs",
        "/swagger-ui",
        "/static/",
        "/assets/",
        "/.well-known/"
    };

    // ── Filter bean ──────────────────────────────────────────────────────────

    @Bean
    public GlobalFilter authenticationFilter() {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getURI().getPath();

            // ── 1. Public routes bypass auth entirely ─────────────────────
            if (isPublicRoute(path)) {
                log.debug("Public route accessed: {}", path);
                return chain.filter(exchange);
            }

            // ── 2. JWT Bearer token (primary) ─────────────────────────────
            // If the client sends "Authorization: Bearer <token>" validate the JWT.
            // A valid JWT grants access; an invalid/expired one is rejected immediately
            // (we do NOT fall back to session when a Bearer header is present).
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7).trim();
                Optional<JwtUtil.JwtPrincipal> jwtPrincipal = JwtUtil.extractPrincipal(token);

                if (jwtPrincipal.isPresent()) {
                    log.debug("JWT validated for user: {} (id: {}), path: {}",
                            jwtPrincipal.get().username(), jwtPrincipal.get().userId(), path);
                    return chain.filter(
                            buildAuthenticatedExchange(exchange, jwtPrincipal.get().userId(), jwtPrincipal.get().username()));
                } else {
                    // Bearer token present but invalid/expired – reject immediately
                    log.warn("Invalid or expired JWT token for path: {}", path);
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }
            }

            // ── 3. Server-side session (fallback) ─────────────────────────
            // Used by browser clients that rely on the SESSION cookie set at login.
            return sessionService.resolveAuthenticatedPrincipal(exchange)
                    .defaultIfEmpty(new GatewaySessionService.SessionPrincipal(null, null))
                    .flatMap(principal -> {
                        if (principal.userId() == null
                                || principal.username() == null
                                || principal.username().isBlank()) {
                            log.warn("Missing or expired server session for protected path: {}", path);
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        }

                        log.debug("Session validated for user: {} (id: {}), path: {}",
                                principal.username(), principal.userId(), path);
                        return chain.filter(
                                buildAuthenticatedExchange(exchange, principal.userId(), principal.username()));
                    });
        };
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Builds a new exchange with the identity headers set to {@code userId}/{@code username},
     * stripping any previously set (or attacker-supplied) values.
     */
    private ServerWebExchange buildAuthenticatedExchange(ServerWebExchange exchange,
                                                         Long userId,
                                                         String username) {
        return exchange.mutate()
                .request(r -> r.headers(headers -> {
                    // Remove first to prevent header-injection attacks
                    headers.remove(AUTH_USER_ID_HEADER);
                    headers.remove(AUTH_USERNAME_HEADER);
                    headers.set(AUTH_USER_ID_HEADER, userId.toString());
                    headers.set(AUTH_USERNAME_HEADER, username);
                }))
                .build();
    }

    /**
     * Returns {@code true} if the path should bypass authentication.
     */
    private boolean isPublicRoute(String path) {
        for (String publicRoute : EXACT_PUBLIC_ROUTES) {
            if (publicRoute.equals(path)) return true;
        }
        for (String prefix : PREFIX_PUBLIC_ROUTES) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }
}

