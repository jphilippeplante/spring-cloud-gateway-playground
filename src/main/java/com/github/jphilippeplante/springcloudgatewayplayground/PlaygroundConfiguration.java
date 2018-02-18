package com.github.jphilippeplante.springcloudgatewayplayground;

import com.github.jphilippeplante.springcloudgatewayplayground.filter.AmqpFilter;
import com.github.jphilippeplante.springcloudgatewayplayground.handler.predicate.CanaryRoutePredicateFactory;
import com.github.jphilippeplante.springcloudgatewayplayground.handler.predicate.support.CanaryRoutePredicateFactorySupport;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(SecurityConfig.class)
public class PlaygroundConfiguration {

    @Configuration
    @ConditionalOnClass(RabbitTemplate.class)
    public class AmqpGatewayAutoConfiguration {

        @Bean
        public MessageConverter amqpJackson2MessageConverter() {
            return new Jackson2JsonMessageConverter();
        }

        @Bean
        public AmqpFilter ampqFilter(ApplicationContext applicationContext) {
            return new AmqpFilter(applicationContext);
        }

    }

    @Bean
    public CanaryRoutePredicateFactory canaryPredicateFactory() {
        return new CanaryRoutePredicateFactory(new CanaryRoutePredicateFactorySupport());
    }

}
