package it.sdc.src.config;

import it.sdc.src.auth.UserPrincipal;
import it.sdc.src.ws.WebSocketSessionRegistry;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.core.Authentication;
import org.springframework.security.messaging.access.intercept.AuthorizationChannelInterceptor;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.security.messaging.context.SecurityContextChannelInterceptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

import java.util.UUID;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final AppCorsProperties appCorsProperties;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/msgQueue");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(
                appCorsProperties.getAllowedOriginPatterns().toArray(new String[0])
        );
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        var authorizationManager = MessageMatcherDelegatingAuthorizationManager.builder()
                .nullDestMatcher().authenticated()
                .simpSubscribeDestMatchers("/user/msgQueue/messages")
                .authenticated()
                .simpTypeMatchers(SimpMessageType.MESSAGE, SimpMessageType.SUBSCRIBE).denyAll()
                .anyMessage().denyAll()
                .build();

        registration.interceptors(
                new SecurityContextChannelInterceptor(),
                new AuthorizationChannelInterceptor(authorizationManager)
        );
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(
                handler -> new WebSocketHandlerDecorator(handler) {

                    @Override
                    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
                        super.afterConnectionEstablished(session);

                        UUID authSessionId = getAuthSessionId(session);
                        if (authSessionId != null)
                            webSocketSessionRegistry.register(authSessionId, session);
                    }

                    @Override
                    public void afterConnectionClosed(
                            @NonNull WebSocketSession session,
                            @NonNull CloseStatus status
                    ) throws Exception {
                        try {
                            super.afterConnectionClosed(session, status);
                        } finally {
                            UUID authSessionId = getAuthSessionId(session);
                            if (authSessionId != null)
                                webSocketSessionRegistry.unregister(authSessionId, session);
                        }
                    }
                }
        );
    }

    private UUID getAuthSessionId(WebSocketSession session) {
        if (
                session.getPrincipal() instanceof Authentication authentication &&
                        authentication.getPrincipal() instanceof UserPrincipal user
        ) return user.getSessionId();
        return null;
    }
}
