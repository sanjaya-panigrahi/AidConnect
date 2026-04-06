package com.chatassist.gateway.filter;

import com.chatassist.common.security.AuthSessionKeyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Service
public class RedisGatewaySessionService implements GatewaySessionService {
    private final boolean forceReloginEnabled;
    private final long absoluteTimeoutMs;

    // Primary constructor with Spring dependency injection for @Value annotations
    @Autowired
    public RedisGatewaySessionService(
            @Value("${chatassist.auth.force-relogin-enabled:true}") boolean forceReloginEnabled,
            @Value("${chatassist.auth.absolute-timeout-ms:300000}") long absoluteTimeoutMs) {
        this.forceReloginEnabled = forceReloginEnabled;
        this.absoluteTimeoutMs = absoluteTimeoutMs;
    }

    // Secondary constructor for testing/manual instantiation
    RedisGatewaySessionService(long absoluteTimeoutMs) {
        this(true, absoluteTimeoutMs);
    }

    @Override
    public Mono<SessionPrincipal> resolveAuthenticatedPrincipal(ServerWebExchange exchange) {
        return exchange.getSession()
                .flatMap(session -> {
                    Object userId = session.getAttribute(AuthSessionKeyUtil.SESSION_USER_ID_ATTRIBUTE);
                    Object username = session.getAttribute(AuthSessionKeyUtil.SESSION_USERNAME_ATTRIBUTE);
                    Object createdAtEpochMs = session.getAttribute(AuthSessionKeyUtil.SESSION_AUTH_CREATED_AT_ATTRIBUTE);

                    if (!(userId instanceof Number numericUserId)
                            || !(username instanceof String sessionUsername)
                            || sessionUsername.isBlank()) {
                        return Mono.empty();
                    }

                    if (forceReloginEnabled) {
                        if (!(createdAtEpochMs instanceof Number createdAt)) {
                            return Mono.empty();
                        }

                        long ageMs = System.currentTimeMillis() - createdAt.longValue();
                        if (ageMs < 0 || ageMs > absoluteTimeoutMs) {
                            return Mono.empty();
                        }
                    }

                    if (numericUserId.longValue() <= 0) {
                        return Mono.empty();
                    }

                    return Mono.just(new SessionPrincipal(numericUserId.longValue(), sessionUsername));
                });
    }
}
