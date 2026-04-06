package com.chatassist.gateway.filter;

import com.chatassist.common.security.AuthSessionKeyUtil;
import com.chatassist.common.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AuthenticationFilterConfig}.
 *
 * <p>The filter uses a two-step strategy:
 * <ol>
 *   <li>JWT Bearer token (primary) – validated via {@link JwtUtil}.</li>
 *   <li>Server-side session (fallback) – resolved by {@link GatewaySessionService}.</li>
 * </ol>
 */
class AuthenticationFilterConfigTest {

    private GlobalFilter filter;

    /** Minimal mock session service: resolves userId/username from session attributes. */
    @BeforeEach
    void setUp() {
        GatewaySessionService sessionService = exchange -> exchange.getSession()
                .flatMap(session -> {
                    Object userId   = session.getAttribute(AuthSessionKeyUtil.SESSION_USER_ID_ATTRIBUTE);
                    Object username = session.getAttribute(AuthSessionKeyUtil.SESSION_USERNAME_ATTRIBUTE);
                    if (!(userId instanceof Number numericUserId)
                            || !(username instanceof String sessionUsername)
                            || sessionUsername.isBlank()) {
                        return Mono.empty();
                    }
                    return Mono.just(
                            new GatewaySessionService.SessionPrincipal(numericUserId.longValue(), sessionUsername));
                });
        filter = new AuthenticationFilterConfig(sessionService).authenticationFilter();
    }

    // ── Public-route tests ───────────────────────────────────────────────────

    @Test
    @DisplayName("public routes should bypass authentication")
    void shouldBypassPublicRoutes() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users/login").build());

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        filter.filter(exchange, chainCapture(chainInvoked)).block();

        assertThat(chainInvoked).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("default avatar image should bypass authentication")
    void shouldBypassDefaultAvatar() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/default-user.svg").build());

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        filter.filter(exchange, chainCapture(chainInvoked)).block();

        assertThat(chainInvoked).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("WebSocket info endpoint should bypass authentication")
    void shouldBypassWebSocketInfoEndpoint() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/ws-chat/info").build());

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        filter.filter(exchange, chainCapture(chainInvoked)).block();

        assertThat(chainInvoked).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("well-known probe paths should bypass authentication")
    void shouldBypassWellKnownProbeRoute() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/.well-known/appspecific/com.chrome.devtools.json").build());

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        filter.filter(exchange, chainCapture(chainInvoked)).block();

        assertThat(chainInvoked).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    // ── JWT tests ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("valid JWT Bearer token should allow access and override identity headers")
    void shouldAcceptValidJwtToken() {
        // Generate a real token for user 42 / "alice"
        String token = JwtUtil.generateToken(42L, "alice");

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/chats/conversation")
                        .header("Authorization", "Bearer " + token)
                        .build());

        AtomicReference<ServerWebExchange> forwardedExchange = new AtomicReference<>();
        filter.filter(exchange, req -> { forwardedExchange.set(req); return Mono.empty(); }).block();

        assertThat(forwardedExchange.get()).isNotNull();
        assertThat(forwardedExchange.get().getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("42");
        assertThat(forwardedExchange.get().getRequest().getHeaders().getFirst("X-Username")).isEqualTo("alice");
        assertThat(exchange.getResponse().getStatusCode()).isNull(); // no error
    }

    @Test
    @DisplayName("invalid JWT Bearer token should be rejected with 401")
    void shouldRejectInvalidToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/chats/conversation")
                        .header("Authorization", "Bearer this.is.not.a.valid.jwt")
                        .build());

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        filter.filter(exchange, chainCapture(chainInvoked)).block();

        assertThat(chainInvoked).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("valid JWT should override attacker-supplied identity headers")
    void shouldOverrideAttackerHeadersWithJwtClaims() {
        String token = JwtUtil.generateToken(99L, "sanjaya");

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/chats/conversation")
                        .header("Authorization", "Bearer " + token)
                        .header("X-User-Id", "attacker-value")
                        .header("X-Username", "attacker")
                        .build());

        AtomicReference<ServerWebExchange> forwardedExchange = new AtomicReference<>();
        filter.filter(exchange, req -> { forwardedExchange.set(req); return Mono.empty(); }).block();

        assertThat(forwardedExchange.get()).isNotNull();
        // Attacker headers must be replaced by JWT-derived values
        assertThat(forwardedExchange.get().getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("99");
        assertThat(forwardedExchange.get().getRequest().getHeaders().getFirst("X-Username")).isEqualTo("sanjaya");
    }

    // ── Session fallback tests ────────────────────────────────────────────────

    @Test
    @DisplayName("valid server session (no JWT) should allow access")
    void shouldForwardAuthenticatedHeaders() {
        // No Authorization header → falls back to session
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/chats/conversation")
                        .header("X-User-Id", "attacker-value")
                        .header("X-Username", "attacker")
                        .build());
        exchange.getSession().block().getAttributes()
                .put(AuthSessionKeyUtil.SESSION_USER_ID_ATTRIBUTE, 99L);
        exchange.getSession().block().getAttributes()
                .put(AuthSessionKeyUtil.SESSION_USERNAME_ATTRIBUTE, "sanjaya");

        AtomicReference<ServerWebExchange> forwardedExchange = new AtomicReference<>();
        filter.filter(exchange, req -> { forwardedExchange.set(req); return Mono.empty(); }).block();

        assertThat(forwardedExchange.get()).isNotNull();
        assertThat(forwardedExchange.get().getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("99");
        assertThat(forwardedExchange.get().getRequest().getHeaders().getFirst("X-Username")).isEqualTo("sanjaya");
    }

    @Test
    @DisplayName("protected routes should reject requests with no JWT and no session")
    void shouldRejectMissingToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/chats/conversation").build());

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        filter.filter(exchange, chainCapture(chainInvoked)).block();

        assertThat(chainInvoked).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("logout endpoint should require authentication")
    void logoutShouldRemainProtected() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/users/logout").build());

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        filter.filter(exchange, chainCapture(chainInvoked)).block();

        assertThat(chainInvoked).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("login-like paths should not be treated as public (prefix-only match)")
    void shouldNotBypassLoginLikePaths() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users/login-extra").build());

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        filter.filter(exchange, chainCapture(chainInvoked)).block();

        assertThat(chainInvoked).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("malformed session service (empty) should yield 401")
    void shouldRejectMalformedServerSession() {
        filter = new AuthenticationFilterConfig(exchange -> exchange.getSession().then(Mono.empty()))
                .authenticationFilter();

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/chats/conversation").build());

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        filter.filter(exchange, chainCapture(chainInvoked)).block();

        assertThat(chainInvoked).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private GatewayFilterChain chainCapture(AtomicBoolean invoked) {
        return req -> { invoked.set(true); return Mono.empty(); };
    }
}
