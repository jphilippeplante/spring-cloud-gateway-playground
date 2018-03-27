package com.github.jphilippeplante.springcloudgatewayplayground.filter.factory;

import org.springframework.validation.annotation.Validated;

@Validated
public class ValidateJwtConfig {

    private String iss;
    private String scope;
    private String aud;
    private String signingKeyResolver;
    private String scopeValidation;
    private String all;
    private String any;

    public String getIss() {
        return iss;
    }

    public void setIss(String iss) {
        this.iss = iss;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getAud() {
        return aud;
    }

    public void setAud(String aud) {
        this.aud = aud;
    }

    public String getSigningKeyResolver() {
        return signingKeyResolver;
    }

    public void setSigningKeyResolver(String signingKeyResolver) {
        this.signingKeyResolver = signingKeyResolver;
    }

    public String getScopeValidation() {
        return scopeValidation;
    }

    public void setScopeValidation(String scopeValidation) {
        this.scopeValidation = scopeValidation;
    }

    public String getAll() {
        return all;
    }

    public void setAll(String all) {
        this.all = all;
    }

    public String getAny() {
        return any;
    }

    public void setAny(String any) {
        this.any = any;
    }
}
