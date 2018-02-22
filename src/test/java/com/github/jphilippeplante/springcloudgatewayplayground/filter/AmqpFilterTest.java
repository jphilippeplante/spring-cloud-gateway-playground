package com.github.jphilippeplante.springcloudgatewayplayground.filter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.support.CorrelationData;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * @author Jean-Philippe Plante
 */
@RunWith(MockitoJUnitRunner.class)
public class AmqpFilterTest {

    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private RabbitTemplate mockRabbitTemplate;

    @Test
    public void testFilterInvalidRoute() {
        Route value = new Route("1", URI.create("smb://my-smb-server"), 0, swe -> true, Collections.emptyList());
        ServerWebExchange webExchange = testFilter(value);
        assertFalse(ServerWebExchangeUtils.isAlreadyRouted(webExchange));
        assertEquals("NEW", ReflectionTestUtils.getField(webExchange.getResponse(), "state").toString());
    }

    @Test
    public void testFilterWorksDefault() {
        when(applicationContext.getBean(RabbitTemplate.class)).thenReturn(mockRabbitTemplate);

        Route value = new Route("1", URI.create("amqp://default"), 0, swe -> true,
                Collections.emptyList());
        ServerWebExchange webExchange = testFilter(value);
        assertTrue(ServerWebExchangeUtils.isAlreadyRouted(webExchange));
        assertEquals(HttpStatus.ACCEPTED, webExchange.getResponse().getStatusCode());
        verify(mockRabbitTemplate).correlationConvertAndSend(any(Message.class), any(CorrelationData.class));
    }

    @Test
    public void testFilterRabbitTemplateError() {
        when(applicationContext.getBean(RabbitTemplate.class)).thenReturn(mockRabbitTemplate);
        doThrow(new AmqpConnectException("AmqpConnectException", null)).when(mockRabbitTemplate).correlationConvertAndSend(any(Message.class), any(CorrelationData.class));

        Route value = new Route("1", URI.create("amqp://default"), 0, swe -> true,
                Collections.emptyList());
        ServerWebExchange webExchange = testFilter(value);
        assertTrue(ServerWebExchangeUtils.isAlreadyRouted(webExchange));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, webExchange.getResponse().getStatusCode());
    }

    @Test
    public void testFilterWorksDefaultWithRoutingKey() {
        when(applicationContext.getBean(RabbitTemplate.class)).thenReturn(mockRabbitTemplate);

        Route value = new Route("1", URI.create("amqp://default?routingKey=myrouting"), 0, swe -> true,
                Collections.emptyList());
        ServerWebExchange webExchange = testFilter(value);
        assertTrue(ServerWebExchangeUtils.isAlreadyRouted(webExchange));
        assertEquals(HttpStatus.ACCEPTED, webExchange.getResponse().getStatusCode());
        verify(mockRabbitTemplate).convertAndSend(eq("myrouting"), any(Message.class), any(CorrelationData.class));
    }

    @Test
    public void testFilterWorksDefaultWithExchangeAndRoutingKey() {
        when(applicationContext.getBean(RabbitTemplate.class)).thenReturn(mockRabbitTemplate);

        Route value = new Route("1", URI.create("amqp://default?exchange=myexchange&routingKey=myrouting"), 0, swe -> true,
                Collections.emptyList());
        ServerWebExchange webExchange = testFilter(value);
        assertTrue(ServerWebExchangeUtils.isAlreadyRouted(webExchange));
        assertEquals(HttpStatus.ACCEPTED, webExchange.getResponse().getStatusCode());
        verify(mockRabbitTemplate).convertAndSend(eq("myexchange"), eq("myrouting"), any(Message.class), any(CorrelationData.class));
    }

    @Test
    public void testFilterWorksDefaultWithBody() {
        when(applicationContext.getBean(RabbitTemplate.class)).thenReturn(mockRabbitTemplate);

        Route value = new Route("1", URI.create("amqp://default?exchange=myexchange&routingKey=myrouting"), 0, swe -> true,
                Collections.emptyList());
        ServerWebExchange webExchange = testFilter(value, "mybody");
        assertTrue(ServerWebExchangeUtils.isAlreadyRouted(webExchange));
        assertEquals(HttpStatus.ACCEPTED, webExchange.getResponse().getStatusCode());
    }

    @Test
    public void testFilterWorksCustomRabbitTemplateBean() {
        when(applicationContext.getBean("myRabbitTemplate")).thenReturn(mockRabbitTemplate);

        Route value = new Route("1", URI.create("amqp://myRabbitTemplate?exchange=myexchange&routingKey=myrouting"), 0, swe -> true,
                Collections.emptyList());
        ServerWebExchange webExchange = testFilter(value);
        assertTrue(ServerWebExchangeUtils.isAlreadyRouted(webExchange));
        assertEquals(HttpStatus.ACCEPTED, webExchange.getResponse().getStatusCode());
    }

    @Test
    public void testFilterWorksUnknowCustomRabbitTemplateBean() {
        when(applicationContext.getBean(RabbitTemplate.class)).thenReturn(mockRabbitTemplate);
        when(applicationContext.getBean("myRabbitTemplate")).thenThrow(new NoSuchBeanDefinitionException("myRabbitTemplate"));

        Route value = new Route("1", URI.create("amqp://myRabbitTemplate?exchange=myexchange&routingKey=myrouting"), 0, swe -> true,
                Collections.emptyList());
        ServerWebExchange webExchange = testFilter(value);
        assertTrue(ServerWebExchangeUtils.isAlreadyRouted(webExchange));
        assertEquals(HttpStatus.ACCEPTED, webExchange.getResponse().getStatusCode());
    }

    @Test
    public void testFilterInvalidTypeCustomRabbitTemplateBean() {
        when(applicationContext.getBean(RabbitTemplate.class)).thenReturn(mockRabbitTemplate);
        when(applicationContext.getBean("myRabbitTemplate")).thenReturn("invalidType");

        Route value = new Route("1", URI.create("amqp://myRabbitTemplate?exchange=myexchange&routingKey=myrouting"), 0, swe -> true,
                Collections.emptyList());
        ServerWebExchange webExchange = testFilter(value);
        assertTrue(ServerWebExchangeUtils.isAlreadyRouted(webExchange));
        assertEquals(HttpStatus.ACCEPTED, webExchange.getResponse().getStatusCode());
    }

    private ServerWebExchange testFilter(Route route) {
        return testFilter(route, null);
    }

    private ServerWebExchange testFilter(Route route, String body) {
        URI url = URI.create("http://localhost/get");

        MockServerHttpRequest.BodyBuilder builder = MockServerHttpRequest
                .method(HttpMethod.GET, url)
                .remoteAddress(InetSocketAddress.createUnresolved("127.0.0.1", 1234));

        MockServerHttpRequest request = StringUtils.isEmpty(body) ? builder.build() : builder.body(body);

        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route);
        exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, route.getUri());

        GatewayFilterChain filterChain = mock(GatewayFilterChain.class);

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor
                .forClass(ServerWebExchange.class);
        when(filterChain.filter(captor.capture())).thenReturn(Mono.empty());

        AmqpFilter filter = new AmqpFilter(applicationContext);
        filter.filter(exchange, filterChain).block();

        return captor.getValue();
    }

}
