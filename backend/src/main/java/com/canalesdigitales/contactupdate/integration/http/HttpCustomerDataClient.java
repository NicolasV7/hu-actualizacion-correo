package com.canalesdigitales.contactupdate.integration.http;

import com.canalesdigitales.contactupdate.domain.Provider;
import com.canalesdigitales.contactupdate.integration.CustomerDataClient;
import com.canalesdigitales.contactupdate.integration.IntegrationException;
import java.time.Instant;
import java.util.Optional;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Adaptador HTTP de Customer Data API según el contrato asumido en docs/02 sección 6 (supuesto S5). */
public class HttpCustomerDataClient implements CustomerDataClient {

    private final RestClient restClient;
    private final RetryTemplate retryTemplate;

    public HttpCustomerDataClient(RestClient restClient, RetryTemplate retryTemplate) {
        this.restClient = restClient;
        this.retryTemplate = retryTemplate;
    }

    /** Lectura idempotente: se reintenta una vez ante timeout o indisponibilidad (docs/02 sección 7). */
    @Override
    public Optional<AffiliateProfile> findProfile(String affiliateId) {
        try {
            return retryTemplate.execute(() -> fetchProfile(affiliateId));
        } catch (RetryException exhausted) {
            Throwable last = exhausted.getLastException();
            if (last instanceof IntegrationException integration) {
                throw integration;
            }
            throw new IntegrationException(Provider.CUSTOMER_DATA, IntegrationException.Category.UNAVAILABLE,
                    exhausted.getMessage(), exhausted);
        }
    }

    private Optional<AffiliateProfile> fetchProfile(String affiliateId) {
        try {
            ProfileResponse response = restClient.get()
                    .uri("/affiliates/{affiliateId}/profile", affiliateId)
                    .retrieve()
                    .body(ProfileResponse.class);
            if (response == null) {
                throw new IntegrationException(Provider.CUSTOMER_DATA, IntegrationException.Category.REJECTED,
                        "respuesta vacía al consultar el perfil");
            }
            return Optional.of(new AffiliateProfile(response.affiliateId(), response.email(), response.mobile(),
                    "ACTIVE".equalsIgnoreCase(response.status())));
        } catch (HttpClientErrorException.NotFound notFound) {
            return Optional.empty();
        } catch (RestClientException exception) {
            throw IntegrationErrors.map(Provider.CUSTOMER_DATA, exception);
        }
    }

    /** Escritura no idempotente: nunca se reintenta automáticamente (docs/02 D-09). */
    @Override
    public ContactUpdateConfirmation updateEmail(String affiliateId, String newEmail, String validationReference) {
        try {
            UpdateResponse response = restClient.put()
                    .uri("/affiliates/{affiliateId}/contact/email", affiliateId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new UpdateRequest(newEmail, validationReference))
                    .retrieve()
                    .body(UpdateResponse.class);
            Instant updatedAt = response != null && response.updatedAt() != null ? response.updatedAt() : Instant.now();
            return new ContactUpdateConfirmation(updatedAt);
        } catch (RestClientException exception) {
            throw IntegrationErrors.map(Provider.CUSTOMER_DATA, exception);
        }
    }

    record ProfileResponse(String affiliateId, String email, String mobile, String status) {
    }

    record UpdateRequest(String email, String validationReference) {
    }

    record UpdateResponse(Instant updatedAt) {
    }
}
