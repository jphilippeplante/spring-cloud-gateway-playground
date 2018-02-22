/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.github.jphilippeplante.springcloudgatewayplayground;

import com.github.jphilippeplante.springcloudgatewayplayground.exception.PlaygroundExceptionHandler;
import com.github.jphilippeplante.springcloudgatewayplayground.filter.AmqpFilter;
import com.github.jphilippeplante.springcloudgatewayplayground.filter.factory.ValidateJwtGatewayFilterFactory;
import com.github.jphilippeplante.springcloudgatewayplayground.filter.jwtvalidation.CustomFilterSigningKeyResolver;
import com.github.jphilippeplante.springcloudgatewayplayground.filter.jwtvalidation.DefaultFilterSigningKeyResolver;
import com.github.jphilippeplante.springcloudgatewayplayground.handler.predicate.CanaryRoutePredicateFactory;
import com.github.jphilippeplante.springcloudgatewayplayground.handler.predicate.support.CanaryRoutePredicateFactorySupport;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.web.server.WebExceptionHandler;

@Configuration
@Import(SecurityConfig.class)
public class PlaygroundConfiguration {

    @Bean
    public CanaryRoutePredicateFactory canaryPredicateFactory() {
        return new CanaryRoutePredicateFactory(new CanaryRoutePredicateFactorySupport());
    }

    @Bean
    @Order(-2)
    public WebExceptionHandler webExceptionHandler() {
        return new PlaygroundExceptionHandler();
    }

    @Bean("customFilterSigningKeyResolver")
    public CustomFilterSigningKeyResolver customFilterSigningKeyResolver() {
        return new CustomFilterSigningKeyResolver();
    }

    @Bean(name = DefaultFilterSigningKeyResolver.BEAN_NAME)
    public DefaultFilterSigningKeyResolver defaultFilterSigningKeyResolver() {
        return new DefaultFilterSigningKeyResolver();
    }

    @Bean
    public ValidateJwtGatewayFilterFactory validateJwtGatewayFilterFactory(ConfigurableApplicationContext context, DefaultFilterSigningKeyResolver defaultFilterSigningKeyResolver) {
        return new ValidateJwtGatewayFilterFactory(context, defaultFilterSigningKeyResolver);
    }

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

}
