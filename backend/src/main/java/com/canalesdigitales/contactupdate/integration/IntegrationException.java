package com.canalesdigitales.contactupdate.integration;

import com.canalesdigitales.contactupdate.domain.Provider;

/**
 * Fallo técnico de una API corporativa, reducido a tres categorías (docs/02 sección 6):
 * TIMEOUT (504), UNAVAILABLE (503) y REJECTED (contrato roto; se trata como 503).
 */
public class IntegrationException extends RuntimeException {

    public enum Category { TIMEOUT, UNAVAILABLE, REJECTED }

    private final Provider provider;
    private final Category category;

    public IntegrationException(Provider provider, Category category, String message) {
        this(provider, category, message, null);
    }

    public IntegrationException(Provider provider, Category category, String message, Throwable cause) {
        super(provider + " " + category + ": " + message, cause);
        this.provider = provider;
        this.category = category;
    }

    public Provider provider() {
        return provider;
    }

    public Category category() {
        return category;
    }
}
