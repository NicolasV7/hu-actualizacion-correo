package com.canalesdigitales.contactupdate.integration.stub;

import com.canalesdigitales.contactupdate.config.AppProperties;
import com.canalesdigitales.contactupdate.domain.Provider;
import com.canalesdigitales.contactupdate.integration.IdentityCapabilityClient;
import com.canalesdigitales.contactupdate.integration.IntegrationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Simulación de Identity Capability API (docs/02 sección 10). Acepta únicamente el código configurado
 * ({@code app.stub.verification-code}); así ningún código real se genera, registra ni muestra.
 */
@Component
@ConditionalOnProperty(name = "app.integration.mode", havingValue = "stub")
public class StubIdentityCapabilityClient implements IdentityCapabilityClient {

    static final String TIMEOUT_AFFILIATE = "AFF-IDP-TIMEOUT";
    static final String UNAVAILABLE_AFFILIATE = "AFF-IDP-DOWN";

    private final byte[] validCode;
    private final Map<String, String> openChallenges = new ConcurrentHashMap<>();

    public StubIdentityCapabilityClient(AppProperties properties) {
        this.validCode = properties.stub().verificationCode().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public VerificationChallenge requestCode(String affiliateId, String mobile) {
        switch (affiliateId) {
            case TIMEOUT_AFFILIATE -> throw new IntegrationException(Provider.IDENTITY,
                    IntegrationException.Category.TIMEOUT, "stub: el proveedor excedió el tiempo de espera");
            case UNAVAILABLE_AFFILIATE -> throw new IntegrationException(Provider.IDENTITY,
                    IntegrationException.Category.UNAVAILABLE, "stub: proveedor de identidad no disponible");
            default -> {
                String challengeId = UUID.randomUUID().toString();
                openChallenges.put(challengeId, affiliateId);
                return new VerificationChallenge(challengeId);
            }
        }
    }

    @Override
    public boolean validateCode(String challengeId, String code) {
        if (!openChallenges.containsKey(challengeId)) {
            return false;
        }
        // Comparación en tiempo constante: no filtra información por tiempo de respuesta.
        boolean valid = MessageDigest.isEqual(validCode, code.getBytes(StandardCharsets.UTF_8));
        if (valid) {
            openChallenges.remove(challengeId); // el código se consume con la validación exitosa
        }
        return valid;
    }
}
