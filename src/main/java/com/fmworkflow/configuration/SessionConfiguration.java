package com.fmworkflow.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.HeaderHttpSessionStrategy;
import org.springframework.session.web.http.HttpSessionStrategy;

@EnableRedisHttpSession
@Configuration
public class SessionConfiguration {

    @Bean
    public JedisConnectionFactory lettuceConnectionFactory() {
        return new JedisConnectionFactory();
    }

    @Bean
    public HttpSessionStrategy httpSessionStrategy(){
            return new HeaderHttpSessionStrategy();
    }
}
