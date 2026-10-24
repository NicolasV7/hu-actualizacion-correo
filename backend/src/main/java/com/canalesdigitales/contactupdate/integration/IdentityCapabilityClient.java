package com.canalesdigitales.contactupdate.integration;

/**
 * Puerto hacia Identity Capability API (doc 06): el proveedor genera, entrega y valida el código y registra el
 * resultado (supuesto S3). Este servicio nunca conoce el código en claro.
 */
public interface IdentityCapabilityClient {

    VerificationChallenge requestCode(String affiliateId, String mobile);

    /** {@code true} si el código es válido para el reto; {@code false} si es incorrecto. Fallos técnicos lanzan IntegrationException. */
    boolean validateCode(String challengeId, String code);

    record VerificationChallenge(String challengeId) {
    }
}
