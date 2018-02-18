# spring-cloud-gateway-playground
Playing with spring-cloud-gateway


# Global Filters
## AMQP Routing Filter

The `AmqpFilter` runs if the url located in the `ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR` exchange attribute has a `amqp` scheme (ie `amqp://default`, `amqp://default?routingKey=myrouting`, `amqp://default?exchange=myExchange&routingKey=myrouting`). It uses the RabbitMQ messaging infrastructure to send the request into the specified exchange name and routing key of a queue.

It requires the use of the `spring-boot-starter-amqp` Spring Boot starter.

If the hostname part of the uri is different of `default`, it will look for a `RabbitTemplate` bean by that name (ie `amqp://myCustomRabbitTemplate`). If not found or is not a `RabbitTemplate` bean, it will use the default `RabbitTemplate` bean.

The following informations from the request is in the message (json format): remote address, cookies, headers, method, uri, queryParams and body (string only).

## Null Route Filter

The `NullRouteFilter` looks for a URI in the exchange attribute `ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR`. If the url has a `nullroute` scheme (ie `nullroute://200`), it will return a response with the status code specified without calling any backend. The host part of the uri is used as the http status the filter will put in the response. It makes possible to use predicates and filters on a route without any backend to do simple operations.

# Route Predicate Factories

## Canary Predicate Factory

The Canary Predicate Factory will use these parameters to matches requests that happen on the route.

- beta: beta Id, the value will be use in the cookie's value
- cookie: name of the cookie
- expiration: cookie's expiration in seconds (s), minutes (m), hours (h) or days (d) (ie `15m`)
- ratio: ratio of enrollment between `0` and `1`
- by (optional): increment ratio by `time` or `enrollment` hit
- every (optional): if `time` is used for the `by` parameter, specify seconds (s), minutes (m), hours (h) or days (d) between each auto-increment

The predicate also works with the header `X-Beta-` + cookie name.

```yaml
spring:
  cloud:
    gateway:
      routes:
      - id: canary_myroute
        uri: http://new.example.org
        predicates:
        - Path=/canary
        - name: Canary
          args:
            beta: 0dbe73c-6761-460e-ac27-8d4237497cac/1.0.0
            cookie: mybeta
            expiration: 10m
            ratio: 0.10
      - id: myroute
        uri: http://www.example.org
        predicates:
        - Path=/canary
```

The canary route will match if the user is enroll or he is already enrolled (cookie or header). Otherwise, the route without canary predicate will be used.
