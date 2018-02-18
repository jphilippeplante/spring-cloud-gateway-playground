package com.github.jphilippeplante.springcloudgatewayplayground;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;

@SpringBootApplication
@EnableWebFluxSecurity
public class SpringCloudGatewayPlaygroundApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringCloudGatewayPlaygroundApplication.class, args);
	}
}
