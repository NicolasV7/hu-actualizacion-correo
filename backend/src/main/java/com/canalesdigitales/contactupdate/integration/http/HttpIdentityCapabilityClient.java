package com.canalesdigitales.contactupdate.integration.http;

import com.canalesdigitales.contactupdate.domain.Provider;
import com.canalesdigitales.contactupdate.integration.IdentityCapabilityClient;
import com.canalesdigitales.contactupdate.integration.IntegrationException;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Adaptador HTTP de Identity Capability API (docs/02 sección 6). Ninguna operación se reintenta: generar o validar dos veces consume intentos. */
public class HttpIdentityCapabilityClient implements IdentityCapabilityClient {

    private static final String CHANNEL = "SMS";

    private final RestClient restClient;

    public HttpIdentityCapabilityClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public VerificationChallenge requestCode(String affiliateId, String mobile) {
        try {
            ChallengeResponse response = restClient.post()
                    .uri("/verification-codes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ChallengeRequest(affiliateId, CHANNEL, mobile))
                    .retrieve()
                    .body(ChallengeResponse.class);
            if (response == null || response.challengeId() == null) {
                throw new IntegrationException(Provider.IDENTITY, IntegrationException.Category.REJECTED,
                        "respuesta sin challengeId");
            }
            return new VerificationChallenge(response.challengeId());
        } catch (RestClientException exception) {
            throw IntegrationErrors.map(Provider.IDENTITY, exception);
        }
    }

    @Override
    public boolean validateCode(String challengeId, String code) {
        try {
            ValidationResponse response = restClient.post()
                    .uri("/verification-codes/{challengeId}/validate", challengeId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ValidationRequest(code))
                    .retrieve()
                    .body(ValidationResponse.class);
            return response != null && response.valid();
        } catch (RestClientException exception) {
            throw IntegrationErrors.map(Provider.IDENTITY, exception);
        }
    }

    record ChallengeRequest(String affiliateId, String channel, String destination) {
    }

    record ChallengeResponse(String challengeId) {
    }

    record ValidationRequest(String code) {
    }

    record ValidationResponse(boolean valid) {
    }
}
