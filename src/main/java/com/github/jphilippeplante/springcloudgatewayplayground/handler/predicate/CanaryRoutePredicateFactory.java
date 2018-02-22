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
package com.github.jphilippeplante.springcloudgatewayplayground.handler.predicate;

import com.github.jphilippeplante.springcloudgatewayplayground.handler.predicate.support.CanaryBetaConfiguration;
import com.github.jphilippeplante.springcloudgatewayplayground.handler.predicate.support.CanaryRoutePredicateFactorySupport;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.tuple.Tuple;
import org.springframework.web.server.ServerWebExchange;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * CanaryRoutePredicateFactory is use to enroll in a beta a user who hits the route where
 * the predicate is used. It also possible to auto-increments the ratio of enrollment by
 * time or number of enrollment.
 *
 * @author Jean-Philippe Plante
 */
public class CanaryRoutePredicateFactory implements RoutePredicateFactory {

    public static final String ARG_BETA_ID = "beta";
    public static final String ARG_COOKIE = "cookie";
    public static final String ARG_EXPIRATION = "expiration";
    public static final String ARG_RATIO = "ratio";
    public static final String ARG_INCREMENT_BY = "by";
    public static final String ARG_INCREMENT = "increment";
    public static final String ARG_EVERY = "every";
    public static final String HEADER_X_BETA = "X-Beta-";
    public static final String BY_ENROLLMENT = "enrollment";
    public static final String BY_TIME = "time";
    public static final String SP_ID_AND_VERSION = "/";

    private CanaryRoutePredicateFactorySupport support;

    public CanaryRoutePredicateFactory(CanaryRoutePredicateFactorySupport support) {
        this.support = support;
    }

    @Override
    public List<String> argNames() {
        return Arrays.asList(ARG_BETA_ID);
    }

    @Override
    public boolean validateArgs() {
        return false;
    }

    @Override
    public Predicate<ServerWebExchange> apply(Tuple args) {
        CanaryBetaConfiguration configuration = support.getConfiguration(args);
        return exchange -> ifBetaActivePredicate(configuration).or(
                randomBetaEnroll(configuration)).test(exchange);
    }

    private Predicate<ServerWebExchange> randomBetaEnroll(
            CanaryBetaConfiguration configuration) {
        return exchange -> {

            if (configuration.getRatio() == 0) {
                return false;
            }

            // dont enroll in a different version of the same beta if the cookie exist
            if (isAlreadyInAnotherBeta(configuration, exchange)) {
                refreshBetaCookie(exchange, configuration.getCookie());
                return false;
            }

            // by time, we have to increase before the enroll
            if (BY_TIME.equalsIgnoreCase(configuration.getBy())) {
                support.increaseRatioFor(configuration);
            }

            return doRandomBetaEnroll(configuration).test(exchange);
        };
    }

    private boolean isAlreadyInAnotherBeta(CanaryBetaConfiguration configuration,
                                           ServerWebExchange exchange) {
        HttpCookie requestCookie = exchange.getRequest().getCookies()
                .getFirst(configuration.getCookie());
        if (requestCookie != null
                && !Objects.equals(requestCookie.getValue(), configuration.getBetaId())) {
            // check if it the same beta but different version
            if (configuration.getBetaId().contains(SP_ID_AND_VERSION)
                    && requestCookie.getValue().contains(SP_ID_AND_VERSION)) {
                String[] betaId = configuration.getBetaId().split(SP_ID_AND_VERSION, 2);
                String[] cBetaId = requestCookie.getValue().split(SP_ID_AND_VERSION, 2);
                return Objects.equals(betaId[0], cBetaId[0])
                        && support.getConfiguration(requestCookie.getValue()) != null;
            }
        }
        return false;
    }

    private Predicate<ServerWebExchange> doRandomBetaEnroll(
            CanaryBetaConfiguration configuration) {
        return exchange -> {
            boolean enroll = Math.random() < configuration.getRatio();
            if (enroll) {
                // by enrollment, increase when someone enroll
                if (BY_ENROLLMENT.equalsIgnoreCase(configuration.getBy())) {
                    support.increaseRatioFor(configuration);
                }
                ResponseCookie responseCookie = createBetaCookie(configuration);
                exchange.getResponse().addCookie(responseCookie);
            }
            return enroll;
        };
    }

    private Predicate<ServerWebExchange> ifBetaActivePredicate(
            CanaryBetaConfiguration configuration) {
        return exchange -> {
            ServerHttpRequest request = exchange.getRequest();
            HttpCookie cookie = request.getCookies().getFirst(configuration.getCookie());
            if (cookie != null
                    && Objects.equals(cookie.getValue(), configuration.getBetaId())) {
                refreshBetaCookie(exchange, cookie.getName()); // refresh cookie
                return true;
            }
            List<String> values = request.getHeaders().get(
                    HEADER_X_BETA + configuration.getCookie());
            return values != null && values.contains(configuration.getBetaId());
        };
    }

    private ResponseCookie createBetaCookie(CanaryBetaConfiguration configuration) {
        return ResponseCookie.from(configuration.getCookie(), configuration.getBetaId())
                .maxAge(Duration.ofMinutes(configuration.getExpiration().toMinutes()))
                .httpOnly(true).build();
    }

    private void refreshBetaCookie(ServerWebExchange exchange, String cookie) {
        HttpCookie requestCookie = exchange.getRequest().getCookies().getFirst(cookie);
        if (requestCookie != null) {
            CanaryBetaConfiguration configuration = support
                    .getConfiguration(requestCookie.getValue());
            if (configuration != null) {
                ResponseCookie responseCookie = ResponseCookie
                        .from(requestCookie.getName(), requestCookie.getValue())
                        .maxAge(Duration.ofMinutes(configuration.getExpiration()
                                .toMinutes())).httpOnly(true).build();
                exchange.getResponse().addCookie(responseCookie);
            }
        }
    }

}
