package com.docflow.config;

import com.docflow.collaboration.CollaborationWebSocketHandler;
import com.docflow.collaboration.WebSocketAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final CollaborationWebSocketHandler handler;
    private final WebSocketAuthInterceptor authInterceptor;

    public WebSocketConfig(CollaborationWebSocketHandler handler,
                           WebSocketAuthInterceptor authInterceptor) {
        this.handler = handler;
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws")
                .addInterceptors(authInterceptor)
                .setAllowedOrigins("*");
    }
}
