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
package com.github.jphilippeplante.springcloudgatewayplayground.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ClaimJwtException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.InvalidClaimException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.MissingClaimException;
import io.jsonwebtoken.PrematureJwtException;
import io.jsonwebtoken.RequiredTypeException;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

public class PlaygroundJwtExceptionHandler {

    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {

        JwtError response = JwtError.internalError();

        // TODO put error in the header/body and logging
        exchange.getResponse().getHeaders().add("WWW-Authenticate", "Bearer");

        if (ex instanceof InvalidAudienceException) {
            response = JwtError.invalidRequest().description(ex.getMessage());
        } else if (ex instanceof InvalidIssuerException) {
            response = JwtError.invalidRequest().description(ex.getMessage());
        } else if (ex instanceof InvalidScopeException) {
            response = JwtError.insufficientScope().description(ex.getMessage());
        } else if (ex instanceof ExpiredJwtException) {
            response = JwtError.invalidToken().description(ex.getMessage());
            ;
        } else if (ex instanceof MalformedJwtException) {
            response = JwtError.invalidRequest();
        } else if (ex instanceof MissingClaimException) {
            response = JwtError.invalidRequest();
        } else if (ex instanceof PrematureJwtException) {
            response = JwtError.invalidToken().description(ex.getMessage());
        } else if (ex instanceof RequiredTypeException) {
            response = JwtError.invalidToken().description(ex.getMessage());
        } else if (ex instanceof SignatureException) {
            response = JwtError.invalidToken().status(HttpStatus.INTERNAL_SERVER_ERROR);
        } else if (ex instanceof UnsupportedJwtException) {
            response = JwtError.invalidToken().status(HttpStatus.INTERNAL_SERVER_ERROR);
        } else if (ex instanceof IncorrectClaimException) {
            response = JwtError.invalidRequest();
        } else if (ex instanceof InvalidClaimException) {
            response = JwtError.invalidToken();
        } else if (ex instanceof ClaimJwtException) {
            response = JwtError.invalidToken().status(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        byte[] bytes = response.toJson().getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(response.status);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Flux.just(buffer));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class JwtError {
        @JsonProperty("error")
        String error;
        @JsonProperty("error_description")
        String description;

        transient HttpStatus status;

        public JwtError(String error, HttpStatus status) {
            this.error = error;
            this.status = status;
        }

        public JwtError(String error, String description, HttpStatus status) {
            this.error = error;
            this.description = description;
            this.status = status;
        }

        public static JwtError invalidRequest() {
            return new JwtError("invalid_request", HttpStatus.BAD_REQUEST);
        }

        public static JwtError invalidToken() {
            return new JwtError("invalid_token", HttpStatus.UNAUTHORIZED);
        }

        public static JwtError insufficientScope() {
            return new JwtError("insufficient_scope", HttpStatus.FORBIDDEN);
        }

        public static JwtError internalError() {
            return new JwtError("internal_error", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        public JwtError description(String description) {
            this.description = description;
            return this;
        }

        public JwtError status(HttpStatus status) {
            this.status = status;
            return this;
        }

        public String toJson() {
            try {
                ObjectMapper mapper = new ObjectMapper();
                return mapper.writeValueAsString(this);
            } catch (JsonProcessingException e) {
                return "{ \"error\" : \"" + error + "\"}";
            }
        }
    }
}
