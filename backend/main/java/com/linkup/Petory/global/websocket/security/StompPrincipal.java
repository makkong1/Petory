package com.linkup.Petory.global.websocket.security;

import java.security.Principal;

/**
 * STOMP Principal 구현
 * WebSocket 인증된 사용자를 나타내는 Principal
 */
public class StompPrincipal implements Principal {
    private final String name;

    public StompPrincipal(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
}

