package com.canalesdigitales.contactupdate.config;

import com.canalesdigitales.contactupdate.config.AppProperties.Endpoint;
import com.canalesdigitales.contactupdate.config.AppProperties.Integration;
import com.canalesdigitales.contactupdate.integration.CustomerDataClient;
import com.canalesdigitales.contactupdate.integration.IdentityCapabilityClient;
import com.canalesdigitales.contactupdate.integration.IntegrationException;
import com.canalesdigitales.contactupdate.integration.MessagingClient;
import com.canalesdigitales.contactupdate.integration.http.HttpCustomerDataClient;
import com.canalesdigitales.contactupdate.integration.http.HttpIdentityCapabilityClient;
import com.canalesdigitales.contactupdate.integration.http.HttpMessagingClient;
import com.canalesdigitales.contactupdate.observability.CorrelationIdFilter;
import java.time.Duration;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Clientes HTTP hacia las APIs corporativas (modo {@code app.integration.mode=http}).
 * Cada cliente lleva URL base, credencial, timeouts (doc 04, INC-002) y propaga el correlation ID (doc 11).
 * Si falta una URL, el arranque falla con el nombre de la variable esperada (INC-004).
 */
@Configuration
@ConditionalOnProperty(name = "app.integration.mode", havingValue = "http")
public class HttpIntegrationConfig {

    private static final String API_KEY_HEADER = "X-Api-Key";
    private static final Duration RETRY_DELAY = Duration.ofMillis(200);

    @Bean
    public CustomerDataClient customerDataClient(RestClient.Builder builder, AppProperties properties) {
        Integration integration = properties.integration();
        return new HttpCustomerDataClient(
                restClient(builder, integration, integration.customerData(), "CUSTOMER_DATA_BASE_URL"),
                readRetryTemplate());
    }

    @Bean
    public IdentityCapabilityClient identityCapabilityClient(RestClient.Builder builder, AppProperties properties) {
        Integration integration = properties.integration();
        return new HttpIdentityCapabilityClient(
                restClient(builder, integration, integration.identity(), "IDENTITY_BASE_URL"));
    }

    @Bean
    public MessagingClient messagingClient(RestClient.Builder builder, AppProperties properties) {
        Integration integration = properties.integration();
        return new HttpMessagingClient(
                restClient(builder, integration, integration.messaging(), "MESSAGING_BASE_URL"));
    }

    static RestClient restClient(RestClient.Builder builder, Integration integration, Endpoint endpoint,
                                 String baseUrlVariable) {
        if (!StringUtils.hasText(endpoint.baseUrl())) {
            throw new IllegalStateException("Falta la variable de ambiente " + baseUrlVariable
                    + " (obligatoria con INTEGRATION_MODE=http)");
        }
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(integration.connectTimeout())
                .withReadTimeout(integration.readTimeout());
        RestClient.Builder client = builder.clone()
                .baseUrl(endpoint.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .requestInterceptor(correlationIdPropagation());
        if (StringUtils.hasText(endpoint.apiKey())) {
            client.defaultHeader(API_KEY_HEADER, endpoint.apiKey());
        }
        return client.build();
    }

    /** Un reintento para lecturas idempotentes ante timeout o indisponibilidad; nunca ante un rechazo del contrato. */
    public static RetryTemplate readRetryTemplate() {
        RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(1)
                .delay(RETRY_DELAY)
                .includes(IntegrationException.class)
                .predicate(exception -> exception instanceof IntegrationException integration
                        && integration.category() != IntegrationException.Category.REJECTED)
                .build();
        return new RetryTemplate(policy);
    }

    public static ClientHttpRequestInterceptor correlationIdPropagation() {
        return (request, body, execution) -> {
            String correlationId = MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID);
            if (correlationId != null) {
                request.getHeaders().set(CorrelationIdFilter.HEADER, correlationId);
            }
            return execution.execute(request, body);
        };
    }
}
