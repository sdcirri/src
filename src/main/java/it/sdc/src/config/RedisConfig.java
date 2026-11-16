package it.sdc.src.config;

import it.sdc.src.auth.UserPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration(proxyBeanMethods = false)
public class RedisConfig {

    @Bean
    public RedisTemplate<String, UserPrincipal> userPrincipalRedisTemplate(RedisConnectionFactory connectionFactory) {
        var template = new RedisTemplate<String, UserPrincipal>();
        template.setConnectionFactory(connectionFactory);

        var keySerializer = new StringRedisSerializer();
        var valueSerializer = new JacksonJsonRedisSerializer<>(UserPrincipal.class);

        template.setKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashKeySerializer(keySerializer);
        template.setHashValueSerializer(valueSerializer);

        return template;
    }
}
