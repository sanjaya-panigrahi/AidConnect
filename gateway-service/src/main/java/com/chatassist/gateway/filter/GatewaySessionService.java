package com.chatassist.gateway.filter;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public interface GatewaySessionService {
    Mono<SessionPrincipal> resolveAuthenticatedPrincipal(ServerWebExchange exchange);

    record SessionPrincipal(Long userId, String username) {
    }
}

