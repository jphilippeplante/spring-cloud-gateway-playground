package com.github.jphilippeplante.springcloudgatewayplayground.filter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * @author Jean-Philippe Plante
 */
@RunWith(MockitoJUnitRunner.class)
public class NullRouteFilterTest {

    @Test
    public void testFilterInvalidRoute() {
        Route value = new Route("1", URI.create("smb://my-smb-server"), 0, swe -> true,
                Collections.emptyList());
        ServerWebExchange webExchange = testFilter(value);
        assertFalse(ServerWebExchangeUtils.isAlreadyRouted(webExchange));
        assertEquals("NEW",
                ReflectionTestUtils.getField(webExchange.getResponse(), "state")
                        .toString());
    }

    @Test
    public void testFilterForNullRouteWithHttpStatusCode() {
        Route value = new Route("1", URI.create("nullroute://418"), 0, swe -> true,
                Collections.emptyList());
        ServerWebExchange webExchange = testFilter(value);
        assertTrue(ServerWebExchangeUtils.isAlreadyRouted(webExchange));
        assertEquals(HttpStatus.I_AM_A_TEAPOT, webExchange.getResponse().getStatusCode());
    }

    @Test
    public void testFilterForNullRouteWithHttpStatusCodeNonNumeric() {
        Route value = new Route("1", URI.create("nullroute://mystatus"), 0, swe -> true,
                Collections.emptyList());
        ServerWebExchange webExchange = testFilter(value);
        assertTrue(ServerWebExchangeUtils.isAlreadyRouted(webExchange));
        assertEquals(HttpStatus.OK, webExchange.getResponse().getStatusCode());
    }

    private ServerWebExchange testFilter(Route value) {
        URI url = URI.create("http://localhost/get");
        MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.GET, url)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, value);
        exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, value.getUri());

        GatewayFilterChain filterChain = mock(GatewayFilterChain.class);

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor
                .forClass(ServerWebExchange.class);
        when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());

        NullRouteFilter filter = new NullRouteFilter();
        filter.filter(exchange, filterChain);

        return captor.getValue();
    }
}
