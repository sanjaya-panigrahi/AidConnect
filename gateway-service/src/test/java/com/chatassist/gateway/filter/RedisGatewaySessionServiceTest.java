package com.chatassist.gateway.filter;

import com.chatassist.common.security.AuthSessionKeyUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

class RedisGatewaySessionServiceTest {

    @Test
    @DisplayName("should resolve principal when authenticated session age is within absolute timeout")
    void shouldResolvePrincipalWhenSessionIsFresh() {
        RedisGatewaySessionService service = new RedisGatewaySessionService(300_000);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users").build());

        exchange.getSession().block().getAttributes().put(AuthSessionKeyUtil.SESSION_USER_ID_ATTRIBUTE, 42L);
        exchange.getSession().block().getAttributes().put(AuthSessionKeyUtil.SESSION_USERNAME_ATTRIBUTE, "ajit");
        exchange.getSession().block().getAttributes().put(AuthSessionKeyUtil.SESSION_AUTH_CREATED_AT_ATTRIBUTE, System.currentTimeMillis() - 60_000);

        GatewaySessionService.SessionPrincipal principal = service.resolveAuthenticatedPrincipal(exchange).block();

        assertThat(principal).isNotNull();
        assertThat(principal.userId()).isEqualTo(42L);
        assertThat(principal.username()).isEqualTo("ajit");
    }

    @Test
    @DisplayName("should reject principal when auth-created timestamp is missing")
    void shouldRejectWhenCreatedAtMissing() {
        RedisGatewaySessionService service = new RedisGatewaySessionService(300_000);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users").build());

        exchange.getSession().block().getAttributes().put(AuthSessionKeyUtil.SESSION_USER_ID_ATTRIBUTE, 42L);
        exchange.getSession().block().getAttributes().put(AuthSessionKeyUtil.SESSION_USERNAME_ATTRIBUTE, "ajit");

        GatewaySessionService.SessionPrincipal principal = service.resolveAuthenticatedPrincipal(exchange).block();

        assertThat(principal).isNull();
    }

    @Test
    @DisplayName("should allow principal without auth-created timestamp when force relogin is disabled")
    void shouldAllowWhenForceReloginDisabled() {
        RedisGatewaySessionService service = new RedisGatewaySessionService(false, 300_000);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users").build());

        exchange.getSession().block().getAttributes().put(AuthSessionKeyUtil.SESSION_USER_ID_ATTRIBUTE, 42L);
        exchange.getSession().block().getAttributes().put(AuthSessionKeyUtil.SESSION_USERNAME_ATTRIBUTE, "ajit");

        GatewaySessionService.SessionPrincipal principal = service.resolveAuthenticatedPrincipal(exchange).block();

        assertThat(principal).isNotNull();
        assertThat(principal.userId()).isEqualTo(42L);
        assertThat(principal.username()).isEqualTo("ajit");
    }

    @Test
    @DisplayName("should reject principal when authenticated session exceeds absolute timeout")
    void shouldRejectWhenSessionOlderThanAbsoluteTimeout() {
        RedisGatewaySessionService service = new RedisGatewaySessionService(300_000);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users").build());

        exchange.getSession().block().getAttributes().put(AuthSessionKeyUtil.SESSION_USER_ID_ATTRIBUTE, 42L);
        exchange.getSession().block().getAttributes().put(AuthSessionKeyUtil.SESSION_USERNAME_ATTRIBUTE, "ajit");
        exchange.getSession().block().getAttributes().put(AuthSessionKeyUtil.SESSION_AUTH_CREATED_AT_ATTRIBUTE, System.currentTimeMillis() - 301_000);

        GatewaySessionService.SessionPrincipal principal = service.resolveAuthenticatedPrincipal(exchange).block();

        assertThat(principal).isNull();
    }
}

