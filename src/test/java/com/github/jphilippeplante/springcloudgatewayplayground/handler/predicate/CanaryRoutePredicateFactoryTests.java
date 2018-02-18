package com.github.jphilippeplante.springcloudgatewayplayground.handler.predicate;

import static com.github.jphilippeplante.springcloudgatewayplayground.test.TestUtils.assertStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.github.jphilippeplante.springcloudgatewayplayground.PlaygroundConfiguration;
import com.github.jphilippeplante.springcloudgatewayplayground.test.BaseWebClientTests;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.reactive.function.client.ClientResponse;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
@ActiveProfiles("canary")
public class CanaryRoutePredicateFactoryTests extends BaseWebClientTests {

	@Test
	public void headerRouteWorks() {
		Mono<ClientResponse> result = webClient.get().uri("/get?beta=beta1").exchange();
		StepVerifier.create(result).consumeNextWith(response -> {
			assertRightRoute(response, "canary_test_beta1", "mybeta1", "beta1/1.0.0");
		}).expectComplete().verify(DURATION);
	}

	@Test
	public void headerRouteWorksAlreadyEnrolled() {
		Mono<ClientResponse> result = webClient.get().uri("/get?beta=beta1")
				.cookie("mybeta1", "beta1/1.0.0").exchange();
		StepVerifier.create(result).consumeNextWith(response -> {
			assertRightRoute(response, "canary_test_beta1", "mybeta1", "beta1/1.0.0");
		}).expectComplete().verify(DURATION);
	}

	@Test
	public void headerRouteWorksTwoRoutes() {
		Mono<ClientResponse> result = webClient.get().uri("/get?beta=beta2").exchange();
		StepVerifier.create(result).consumeNextWith(response -> {
			assertRightRoute(response, "canary_test_beta2_2", "mybeta2", "beta2/1.0.1");
		}).expectComplete().verify(DURATION);
	}

	@Test
	public void headerRouteWorksBetaNotActive() {
		Mono<ClientResponse> result = webClient.get().uri("/get?beta=beta3").exchange();
		StepVerifier.create(result).consumeNextWith(response -> {
			assertRightRoute(response, "canary_test_beta3", "mybeta3", "beta3/1.0.0");
		}).expectComplete().verify(DURATION);
	}

	@Test
	public void headerRouteWorksAlreadyEnrolledInAnotherBeta() {
		Mono<ClientResponse> result = webClient.get().uri("/get?beta=beta4")
				.cookie("mybeta4", "beta4/1.0.1").exchange();
		StepVerifier.create(result).consumeNextWith(response -> {
			assertRightRoute(response, "canary_test_beta4_2", "mybeta4", "beta4/1.0.1");
		}).expectComplete().verify(DURATION);
	}

	private void assertRightRoute(ClientResponse response, String expectedRoute,
			String cookieName, String expectedCookieValue) {
		assertStatus(response, HttpStatus.OK);
		HttpHeaders httpHeaders = response.headers().asHttpHeaders();
		assertThat(httpHeaders.getFirst(HANDLER_MAPPER_HEADER)).isEqualTo(
				RoutePredicateHandlerMapping.class.getSimpleName());
		assertThat(httpHeaders.getFirst(ROUTE_ID_HEADER)).isEqualTo(expectedRoute);
		assertThat(response.cookies().getFirst(cookieName)).isNotNull();
		assertThat(response.cookies().getFirst(cookieName).getValue()).isEqualTo(
				expectedCookieValue);
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import({DefaultTestConfig.class, PlaygroundConfiguration.class})
	public static class TestConfig {
	}
}
