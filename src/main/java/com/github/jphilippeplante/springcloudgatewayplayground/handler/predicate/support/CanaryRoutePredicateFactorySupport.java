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
package com.github.jphilippeplante.springcloudgatewayplayground.handler.predicate.support;

import org.springframework.tuple.Tuple;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static com.github.jphilippeplante.springcloudgatewayplayground.handler.predicate.CanaryRoutePredicateFactory.ARG_BETA_ID;
import static com.github.jphilippeplante.springcloudgatewayplayground.handler.predicate.CanaryRoutePredicateFactory.ARG_COOKIE;
import static com.github.jphilippeplante.springcloudgatewayplayground.handler.predicate.CanaryRoutePredicateFactory.ARG_EVERY;
import static com.github.jphilippeplante.springcloudgatewayplayground.handler.predicate.CanaryRoutePredicateFactory.ARG_EXPIRATION;
import static com.github.jphilippeplante.springcloudgatewayplayground.handler.predicate.CanaryRoutePredicateFactory.ARG_INCREMENT;
import static com.github.jphilippeplante.springcloudgatewayplayground.handler.predicate.CanaryRoutePredicateFactory.ARG_INCREMENT_BY;
import static com.github.jphilippeplante.springcloudgatewayplayground.handler.predicate.CanaryRoutePredicateFactory.ARG_RATIO;
import static com.github.jphilippeplante.springcloudgatewayplayground.handler.predicate.CanaryRoutePredicateFactory.BY_ENROLLMENT;
import static com.github.jphilippeplante.springcloudgatewayplayground.handler.predicate.CanaryRoutePredicateFactory.BY_TIME;

/**
 * Class CanaryRoutePredicateFactorySupport is a support to the
 * CanaryRoutePredicateFactory.
 *
 * @author Jean-Philippe Plante
 */
public class CanaryRoutePredicateFactorySupport {

    private static final String INVALID_CONFIG = "Invalid Canary configuration of %s";

    private Map<String, CanaryBetaConfiguration> configurations = new ConcurrentReferenceHashMap<>();
    private Map<String, LocalDateTime> lastUpdatedIncrements = new ConcurrentReferenceHashMap<>();

    /**
     * Increase ratio for the the configuration provided.
     *
     * @param configuration a configuration for a beta
     */
    public void increaseRatioFor(CanaryBetaConfiguration configuration) {
        if (configuration.getRatio() < 1) {
            // increment ratio if by enrollment
            if (BY_ENROLLMENT.equals(configuration.getBy())) {
                configuration.increaseRatioByIncrement(1);
            } else if (BY_TIME.equals(configuration.getBy())) {
                // how many increments we need to make (between then and now)
                String betaId = configuration.getBetaId();

                // get number of increment we can do
                long numberOfIncrement = getNumberOfIncrements(configuration, betaId);

                configuration.increaseRatioByIncrement(numberOfIncrement);
                lastUpdatedIncrements.put(betaId, LocalDateTime.now());
            }
        }
    }

    /**
     * Load and save the configuration use in the predicate.
     *
     * @param args from the predicate in the yml
     * @return a configuration for a beta
     */
    public CanaryBetaConfiguration getConfiguration(Tuple args) {
        String betaId = args.getString(ARG_BETA_ID);
        if (configurations.containsKey(betaId)) {
            return configurations.get(betaId);
        }

        CanaryBetaConfiguration configuration = new CanaryBetaConfiguration();
        configuration.setBetaId(betaId);
        if (args.hasFieldName(ARG_COOKIE)) {
            configuration.setCookie(args.getString(ARG_COOKIE));
        }
        if (args.hasFieldName(ARG_EXPIRATION)) {
            String expiration = args.getString(ARG_EXPIRATION);
            Duration duration = Duration.parse("PT" + expiration.trim().toUpperCase());
            configuration.setExpiration(duration);
        }
        if (args.hasFieldName(ARG_RATIO)) {
            double ratio = args.getDouble(ARG_RATIO);
            ratio = ratio < 0 ? 0.01 : ratio;
            ratio = ratio > 1 ? 1 : ratio;
            configuration.setRatio(ratio);
        }

        // validate fields
        if (StringUtils.isEmpty(betaId) || StringUtils.isEmpty(configuration.getCookie())
                || StringUtils.isEmpty(configuration.getExpiration())
                || StringUtils.isEmpty(configuration.getRatio())) {
            throw new IllegalStateException(String.format(INVALID_CONFIG, betaId));
        }

        configuration.setBetaId(betaId);

        // optional fields
        if (args.hasFieldName(ARG_INCREMENT_BY)) {
            // time or enrollment
            String by = args.getString(ARG_INCREMENT_BY);
            if (BY_ENROLLMENT.equalsIgnoreCase(by) || BY_TIME.equalsIgnoreCase(by)) {
                configuration.setBy(by);
                // conf for the auto-increase of the ratio enrollment, by time or
                // enrollment hits
                if (args.hasFieldName(ARG_INCREMENT)) {
                    double increment = args.getDouble(ARG_INCREMENT);
                    increment = increment < 0 ? 0.01 : increment;
                    increment = increment > 1 ? 1 : increment;
                    configuration.setIncrement(increment);
                }
                if (BY_TIME.equalsIgnoreCase(by) && args.hasFieldName(ARG_EVERY)) {
                    String every = args.getString(ARG_EVERY);
                    Duration duration = Duration.parse("PT" + every.trim().toUpperCase());
                    configuration.setEvery(duration);
                }
            }
        }

        configurations.put(betaId, configuration);
        lastUpdatedIncrements.put(betaId, LocalDateTime.now());

        return configuration;
    }

    /**
     * Get configuration by Id.
     *
     * @param betaId is the Id use for the configuration
     * @return a configuration for a beta
     */
    public CanaryBetaConfiguration getConfiguration(String betaId) {
        return configurations.get(betaId);
    }

    /**
     * Calculate the number of increments possible.
     *
     * @param configuration configuration for a beta
     * @param betaId        is the Id use for the configuration
     * @return number of increments possible
     */
    private long getNumberOfIncrements(CanaryBetaConfiguration configuration,
                                       String betaId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastUpdatedIncrements = this.lastUpdatedIncrements.get(betaId);

        if (lastUpdatedIncrements == null) {
            lastUpdatedIncrements = now;
        }

        long fromLastUpdate = lastUpdatedIncrements.until(now, ChronoUnit.SECONDS);
        fromLastUpdate = fromLastUpdate < 1 ? 1 : fromLastUpdate; // to avoid 0

        long nbOfIncrements = fromLastUpdate
                / configuration.getEvery().get(ChronoUnit.SECONDS);
        nbOfIncrements = nbOfIncrements < 1 ? 1 : nbOfIncrements; // to avoid 0

        double maxOfIncrements = (1 - configuration.getRatio())
                / configuration.getIncrement();
        maxOfIncrements = maxOfIncrements < 1 ? 1 : maxOfIncrements; // to avoid 0

        nbOfIncrements = Long.min(nbOfIncrements, (long) maxOfIncrements);

        return nbOfIncrements;
    }

}
