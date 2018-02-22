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
package com.github.jphilippeplante.springcloudgatewayplayground.filter;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.support.CorrelationData;
import org.springframework.beans.BeansException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.UUID;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.isAlreadyRouted;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.setAlreadyRouted;

public class AmqpFilter implements GlobalFilter, Ordered {

    private static final Log log = LogFactory.getLog(NettyWriteResponseFilter.class);

    private static final String ROUTING_KEY = "routingKey";
    private static final String EXCHANGE = "exchange";

    private final ApplicationContext context;

    public AmqpFilter(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        URI requestUrl = exchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);

        String scheme = requestUrl.getScheme();
        if (isAlreadyRouted(exchange) || (!"amqp".equals(scheme))) {
            return chain.filter(exchange);
        }
        setAlreadyRouted(exchange);

        Flux<DataBuffer> fluxBody = exchange.getRequest().getBody();

        Mono<String> requestBody = fluxBody.collect(InputStreamCollector::new,
                (t, dataBuffer) -> t.collectInputStream(dataBuffer.asInputStream())).map(
                InputStreamCollector::convertStreamToString);

        return requestBody
                .map(body -> sendMessage(body, exchange, requestUrl))
                .then(chain.filter(exchange));
    }

    private Mono<Void> sendMessage(String bodyContent, ServerWebExchange exchange, URI requestUrl) {
        // parameters in a map
        MultiValueMap<String, String> parameters = UriComponentsBuilder
                .fromUri(requestUrl).build().getQueryParams();

        // obtains the bean in the url or default one
        RabbitTemplate rabbitTemplate = getRabbitTemplate(requestUrl);

        String exchangeQueue = parameters.getFirst(EXCHANGE);
        String routingKey = parameters.getFirst(ROUTING_KEY);

        // prepare message with headers from the parameters
        Map<String, String> headers = parameters.toSingleValueMap();
        headers.remove(ROUTING_KEY); // used in code only
        headers.remove(EXCHANGE); // used in code only

        // building message with parameters of uri as headers
        Message<AmqpRequest> message = MessageBuilder
                .withPayload(new AmqpRequest(bodyContent, exchange.getRequest()))
                .copyHeaders(headers).build();

        // send message in the queue
        sendMessage(rabbitTemplate, exchange, exchangeQueue, routingKey, message);

        return Mono.empty();
    }

    private void sendMessage(RabbitTemplate rabbitTemplate, ServerWebExchange exchange,
                             String exchangeQueue, String routingKey, Message<AmqpRequest> message) {
        try {
            CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
            if (!StringUtils.isEmpty(exchangeQueue) && !StringUtils.isEmpty(routingKey)) {
                // send it to a specific exchange with a specific routing key.
                rabbitTemplate.convertAndSend(exchangeQueue, routingKey, message, correlationData);
            } else if (StringUtils.isEmpty(exchangeQueue) && !StringUtils.isEmpty(routingKey)) {
                // send it to a default exchange with a specific routing key.
                rabbitTemplate.convertAndSend(routingKey, message, correlationData);
            } else {
                // send it to a default exchange with a default routing key.
                rabbitTemplate.correlationConvertAndSend(message, correlationData);
            }

            exchange.getResponse().setStatusCode(HttpStatus.ACCEPTED);
        } catch (AmqpException e) {
            log.error(String.format("Error while sending message to exchangeQueue=%s routingKey=%s", exchangeQueue, routingKey), e);
            exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get a RabbitTemplate by name or the default one.
     *
     * @param uri in the route's configuration
     * @return a RabbitTemplate
     */
    private RabbitTemplate getRabbitTemplate(URI uri) {
        RabbitTemplate bean = null;
        String beanName = uri.getHost();
        try {
            if (!"default".equals(beanName)) {
                bean = this.context.getBean(beanName, RabbitTemplate.class);
            }
        } catch (BeansException e) {
            log.error(String.format("Error while getting bean %s", beanName), e);
        } catch (ClassCastException e) {
            log.error(String.format("%s is not a RabbitTemplate", beanName), e);
        }
        return Optional.ofNullable(bean).orElse(this.context.getBean(RabbitTemplate.class));
    }

    class AmqpRequest {
        @JsonProperty
        private String remoteAddress;
        @JsonProperty
        private MultiValueMap<String, HttpCookie> cookies;
        @JsonProperty
        private Map<String, String> headers;
        @JsonProperty
        private String method;
        @JsonProperty
        private String uri;
        @JsonProperty
        private MultiValueMap<String, String> queryParams;
        @JsonProperty
        private String body;

        public AmqpRequest(String body, ServerHttpRequest request) {
            this.remoteAddress = request.getRemoteAddress().toString();
            this.cookies = request.getCookies();
            this.headers = request.getHeaders().toSingleValueMap();
            this.method = request.getMethodValue();
            this.uri = request.getURI().toString();
            this.queryParams = request.getQueryParams();
            if (!StringUtils.isEmpty(body)) {
                this.body = body;
            }
        }
    }

    class InputStreamCollector {
        private InputStream is;

        public void collectInputStream(InputStream is) {
            // TODO maximum size for the request body?
            if (this.is == null) {
                this.is = is;
            }
            this.is = new SequenceInputStream(this.is, is);
        }

        public String convertStreamToString() {
            if (is == null) {
                return "";
            }
            Scanner s = new Scanner(is).useDelimiter("\\A");
            return s.hasNext() ? s.next() : "";
        }
    }
}
