package it.sdc.src.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.messaging.access.intercept.AuthorizationChannelInterceptor;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.security.messaging.context.SecurityContextChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
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
}
