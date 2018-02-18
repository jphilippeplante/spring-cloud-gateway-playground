package com.github.jphilippeplante.springcloudgatewayplayground.handler.predicate.support;

import java.time.Duration;

/**
 * Configuration for a canary beta.
 *
 * @author Jean-Philippe Plante
 */
// TODO @lombok.Setter lombok is supposed to be include in spring cloud project
// TODO @lombok.Getter
public class CanaryBetaConfiguration {

	private String betaId;
	private String cookie;
	private Duration expiration;
	private Double ratio;
	private Double increment;
	private String by;
	private Duration every;

	public CanaryBetaConfiguration() {
	}

	public void increaseRatioByIncrement(double numberOfTime) {
		if (ratio < 1) {
			ratio += (numberOfTime * increment);
			ratio = ratio > 1 ? 1 : ratio; // max is 1
		}
	}

	public String getBetaId() {
		return betaId;
	}

	public void setBetaId(String betaId) {
		this.betaId = betaId;
	}

	public String getCookie() {
		return cookie;
	}

	public void setCookie(String cookie) {
		this.cookie = cookie;
	}

	public Duration getExpiration() {
		return expiration;
	}

	public void setExpiration(Duration expiration) {
		this.expiration = expiration;
	}

	public Double getRatio() {
		return ratio;
	}

	public void setRatio(Double ratio) {
		this.ratio = ratio;
	}

	public Double getIncrement() {
		return increment;
	}

	public void setIncrement(Double increment) {
		this.increment = increment;
	}

	public String getBy() {
		return by;
	}

	public void setBy(String by) {
		this.by = by;
	}

	public Duration getEvery() {
		return every;
	}

	public void setEvery(Duration every) {
		this.every = every;
	}
}
