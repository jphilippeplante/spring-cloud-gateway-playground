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

package com.github.jphilippeplante.springcloudgatewayplayground.filter.factory;

import com.github.jphilippeplante.springcloudgatewayplayground.exception.InvalidAudienceException;
import com.github.jphilippeplante.springcloudgatewayplayground.exception.InvalidIssuerException;
import com.github.jphilippeplante.springcloudgatewayplayground.exception.InvalidScopeException;
import com.github.jphilippeplante.springcloudgatewayplayground.filter.jwtvalidation.FilterSigningKeyResolver;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.PrematureJwtException;
import io.jsonwebtoken.SigningKeyResolver;
import io.jsonwebtoken.impl.DefaultJwt;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

public class ValidateJwtGatewayFilterFactory extends AbstractGatewayFilterFactory<ValidateJwtConfig> {

    private static final String BEARER_TYPE = "Bearer";
    private static final String HEADER_AUTHORIZATION = "Authorization";

    private final FilterSigningKeyResolver defaultFilterSigningKeyResolver;
    private final ConfigurableApplicationContext context;

    public ValidateJwtGatewayFilterFactory(ConfigurableApplicationContext context, FilterSigningKeyResolver filterSigningKeyResolver) {
        this.context = context;
        this.defaultFilterSigningKeyResolver = filterSigningKeyResolver;
    }

    @Override
    public GatewayFilter apply(ValidateJwtConfig config) {
        return (exchange, chain) -> {

            extractJwt(exchange).ifPresent(jwt -> {
                Jwt<Header, Claims> claims = retreiveClaims(getFilterSigningResolver(config), jwt);
                // validate jwt expiration, not before, audience and scopes (any or all)
                isExpired(claims);
                isNotBefore(claims);
                validateIssuer(config, claims);
                validateAudience(config, claims);
                validateScopes(config, claims);
            });

            return chain.filter(exchange);
        };
    }

    private FilterSigningKeyResolver getFilterSigningResolver(ValidateJwtConfig config) {
        FilterSigningKeyResolver filterSigningKeyResolver = defaultFilterSigningKeyResolver;
        if (!StringUtils.isEmpty(config.getSigningKeyResolver())) {
            try {
                filterSigningKeyResolver = context.getBean(config.getSigningKeyResolver(), FilterSigningKeyResolver.class);
            } catch (NoSuchBeanDefinitionException e) {
                e.printStackTrace();
                //TODO LOG
            }
        }
        return filterSigningKeyResolver;
    }

    private void isNotBefore(Jwt<Header, Claims> claims) {
        Date notBefore = claims.getBody().getNotBefore();
        if (notBefore != null) {
            LocalDateTime ldt = LocalDateTime.ofInstant(notBefore.toInstant(), ZoneId.systemDefault());
            if (LocalDateTime.now().isBefore(ldt)) {
                throw new PrematureJwtException(claims.getHeader(), claims.getBody(), "premature_jwt");
            }
        }
    }

    private void isExpired(Jwt<Header, Claims> claims) {
        Date expirationTime = claims.getBody().getExpiration();
        LocalDateTime ldt = LocalDateTime.ofInstant(expirationTime.toInstant(), ZoneId.systemDefault());

        if (LocalDateTime.now().isAfter(ldt)) {
            throw new ExpiredJwtException(claims.getHeader(), claims.getBody(), "expired_jwt");
        }
    }

    private void validateIssuer(ValidateJwtConfig config, Jwt<Header, Claims> claims) {
        // if issuer is specified in the configuration, validate it
        String issuer = config.getIss();
        String jwtIssuer = claims.getBody().getIssuer();
        if (StringUtils.isEmpty(issuer) || !issuer.equalsIgnoreCase(jwtIssuer)) {
            throw new InvalidIssuerException(claims.getHeader(), claims.getBody(), "Invalid issuer, expected " + issuer);
        }
    }

    private void validateAudience(ValidateJwtConfig config, Jwt<Header, Claims> claims) {
        // if audience is specified in the configuration, validate it
        String audience = config.getAud();
        String jwtAudience = claims.getBody().getAudience();
        if (StringUtils.isEmpty(audience) || !audience.equalsIgnoreCase(jwtAudience)) {
            throw new InvalidAudienceException(claims.getHeader(), claims.getBody(), "Invalid audience, expected " + audience);
        }
    }

    private void validateScopes(ValidateJwtConfig config, Jwt<Header, Claims> claims) {
        // configuration for the filter
        List<String> scopes = Collections.emptyList();
        if (!StringUtils.isEmpty(config.getScope())) {
            scopes = getListFromSeparatedCommaValue(config.getScope());
        }
        String scopeValidationType = "any";
        if (!StringUtils.isEmpty(config.getScopeValidation())) {
            scopeValidationType = config.getScopeValidation();
        }

        // get scopes from jwt
        String jwtScopesStr = (String) claims.getBody().get("scope");
        List<String> jwtScopes = getListFromSeparatedCommaValue(jwtScopesStr);

        // validate scopes
        List<String> finalScopes = scopes;
        if ("all".equalsIgnoreCase(scopeValidationType)) {
            if (!jwtScopes.stream().allMatch(finalScopes::contains)) {
                throw new InvalidScopeException(claims.getHeader(), claims.getBody(), "Insufficient scope, expected all of: " + finalScopes.toString());
            }
        } else {
            if (jwtScopes.stream().noneMatch(finalScopes::contains)) {
                throw new InvalidScopeException(claims.getHeader(), claims.getBody(), "Insufficient scope, expected any of: " + finalScopes.toString());
            }
        }
    }

    private Jwt<Header, Claims> retreiveClaims(FilterSigningKeyResolver filterSigningKeyResolver, String jwt) {
        Jwt<Header, Claims> claims = null;
        // validate signature if signed and signinkeyresolver is available
        if (Jwts.parser().isSigned(jwt)) {
            if (filterSigningKeyResolver.resolve() != null) {
                claims = retreiveClaims(filterSigningKeyResolver.resolve(), jwt);
            } else {
                // retreive claims without checkin the signature
                int i = jwt.lastIndexOf('.');
                String withoutSignature = jwt.substring(0, i + 1);

                claims = Jwts.parser().parseClaimsJwt(withoutSignature);
            }
        } else {
            claims = Jwts.parser().parseClaimsJwt(jwt);
        }
        return claims;
    }

    private Jwt<Header, Claims> retreiveClaims(SigningKeyResolver signingKeyResolver, String jwt) {
        Jws<Claims> claims = Jwts.parser()
                .setSigningKeyResolver(signingKeyResolver)
                .parseClaimsJws(jwt);
        return new DefaultJwt<>(claims.getHeader(), claims.getBody());
    }

    private Optional<String> extractJwt(ServerWebExchange exchange) {
        String jwt = null;
        String authorizationHeader = exchange.getRequest().getHeaders().getFirst(HEADER_AUTHORIZATION);
        if (!StringUtils.isEmpty(authorizationHeader)
                && authorizationHeader.toLowerCase().startsWith(BEARER_TYPE.toLowerCase())) {
            jwt = authorizationHeader.substring(BEARER_TYPE.length()).trim();
            int commaIndex = jwt.indexOf(',');
            if (commaIndex > 0) {
                jwt = jwt.substring(0, commaIndex);
            }
        }
        return Optional.ofNullable(jwt);
    }

    public List<String> getListFromSeparatedCommaValue(String str) {
        if (StringUtils.isEmpty(str)) {
            return Collections.emptyList();
        }
        return Collections.list(new StringTokenizer(str.trim(), ","))
                .stream()
                .map(token -> (String) token).collect(Collectors.toList());
    }
}
