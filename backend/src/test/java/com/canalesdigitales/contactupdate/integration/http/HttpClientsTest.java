package com.canalesdigitales.contactupdate.integration.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.canalesdigitales.contactupdate.config.HttpIntegrationConfig;
import com.canalesdigitales.contactupdate.domain.Provider;
import com.canalesdigitales.contactupdate.integration.CustomerDataClient.AffiliateProfile;
import com.canalesdigitales.contactupdate.integration.IntegrationException;
import com.canalesdigitales.contactupdate.integration.IntegrationException.Category;
import com.canalesdigitales.contactupdate.integration.MessagingClient.EmailNotification;
import com.canalesdigitales.contactupdate.integration.MessagingClient.Template;
import com.canalesdigitales.contactupdate.observability.CorrelationIdFilter;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Adaptadores HTTP contra un servidor simulado: contrato asumido y mapeo de fallos a categorías. */
class HttpClientsTest {

    private static final String BASE = "https://integrationhub.test";

    private MockRestServiceServer server;
    private HttpCustomerDataClient customerData;
    private HttpIdentityCapabilityClient identity;
    private HttpMessagingClient messaging;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE)
                .requestInterceptor(HttpIntegrationConfig.correlationIdPropagation());
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        customerData = new HttpCustomerDataClient(restClient, HttpIntegrationConfig.readRetryTemplate());
        identity = new HttpIdentityCapabilityClient(restClient);
        messaging = new HttpMessagingClient(restClient);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void should_mapProfileAndPropagateCorrelationId_when_customerDataAnswers() {
        MDC.put(CorrelationIdFilter.MDC_CORRELATION_ID, "corr-42");
        server.expect(requestTo(BASE + "/affiliates/AFF-001/profile"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(CorrelationIdFilter.HEADER, "corr-42"))
                .andRespond(withSuccess("""
                        {"affiliateId":"AFF-001","email":"ana.perez@example.com","mobile":"3001234567","status":"ACTIVE"}
                        """, MediaType.APPLICATION_JSON));

        Optional<AffiliateProfile> profile = customerData.findProfile("AFF-001");

        assertThat(profile).contains(new AffiliateProfile("AFF-001", "ana.perez@example.com", "3001234567", true));
        server.verify();
    }

    @Test
    void should_returnEmpty_when_affiliateDoesNotExist() {
        server.expect(requestTo(BASE + "/affiliates/AFF-999/profile")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(customerData.findProfile("AFF-999")).isEmpty();
    }

    @Test
    void should_retryOnce_when_profileReadFailsTransiently() {
        server.expect(requestTo(BASE + "/affiliates/AFF-001/profile"))
                .andRespond(withException(new ConnectException("connection refused")));
        server.expect(requestTo(BASE + "/affiliates/AFF-001/profile"))
                .andRespond(withSuccess("""
                        {"affiliateId":"AFF-001","email":"a@example.com","mobile":"3001234567","status":"INACTIVE"}
                        """, MediaType.APPLICATION_JSON));

        Optional<AffiliateProfile> profile = customerData.findProfile("AFF-001");

        assertThat(profile).isPresent();
        assertThat(profile.get().active()).isFalse();
        server.verify();
    }

    @Test
    void should_reportTimeout_when_providerExceedsReadTimeoutTwice() {
        server.expect(requestTo(BASE + "/affiliates/AFF-001/profile"))
                .andRespond(withException(new HttpTimeoutException("request timed out")));
        server.expect(requestTo(BASE + "/affiliates/AFF-001/profile"))
                .andRespond(withException(new HttpTimeoutException("request timed out")));

        assertThatThrownBy(() -> customerData.findProfile("AFF-001"))
                .isInstanceOfSatisfying(IntegrationException.class, ex -> {
                    assertThat(ex.provider()).isEqualTo(Provider.CUSTOMER_DATA);
                    assertThat(ex.category()).isEqualTo(Category.TIMEOUT);
                });
        server.verify();
    }

    @Test
    void should_notRetry_when_providerRejectsTheContract() {
        server.expect(requestTo(BASE + "/affiliates/AFF-001/profile")).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> customerData.findProfile("AFF-001"))
                .isInstanceOfSatisfying(IntegrationException.class,
                        ex -> assertThat(ex.category()).isEqualTo(Category.REJECTED));
        server.verify();
    }

    @Test
    void should_sendUpdateWithValidationReference_andNeverRetry() {
        server.expect(requestTo(BASE + "/affiliates/AFF-001/contact/email"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.email").value("nueva@example.com"))
                .andExpect(jsonPath("$.validationReference").value("tx-1"))
                .andRespond(withSuccess("{\"updatedAt\":\"2026-10-24T15:05:33Z\"}", MediaType.APPLICATION_JSON));

        assertThat(customerData.updateEmail("AFF-001", "nueva@example.com", "tx-1").updatedAt())
                .isEqualTo(Instant.parse("2026-10-24T15:05:33Z"));

        server.reset();
        server.expect(requestTo(BASE + "/affiliates/AFF-001/contact/email"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> customerData.updateEmail("AFF-001", "nueva@example.com", "tx-1"))
                .isInstanceOfSatisfying(IntegrationException.class,
                        ex -> assertThat(ex.category()).isEqualTo(Category.UNAVAILABLE));
        server.verify();
    }

    @Test
    void should_requestAndValidateCode_when_identityProviderAnswers() {
        server.expect(requestTo(BASE + "/verification-codes"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.affiliateId").value("AFF-001"))
                .andExpect(jsonPath("$.channel").value("SMS"))
                .andExpect(jsonPath("$.destination").value("3001234567"))
                .andRespond(withSuccess("{\"challengeId\":\"ch-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/verification-codes/ch-1/validate"))
                .andExpect(jsonPath("$.code").value("123456"))
                .andRespond(withSuccess("{\"valid\":false}", MediaType.APPLICATION_JSON));

        assertThat(identity.requestCode("AFF-001", "3001234567").challengeId()).isEqualTo("ch-1");
        assertThat(identity.validateCode("ch-1", "123456")).isFalse();
        server.verify();
    }

    @Test
    void should_reportUnavailable_when_identityProviderFails() {
        server.expect(requestTo(BASE + "/verification-codes")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> identity.requestCode("AFF-001", "3001234567"))
                .isInstanceOfSatisfying(IntegrationException.class, ex -> {
                    assertThat(ex.provider()).isEqualTo(Provider.IDENTITY);
                    assertThat(ex.category()).isEqualTo(Category.UNAVAILABLE);
                });
    }

    @Test
    void should_postEmailNotification_andMapFailures() {
        server.expect(requestTo(BASE + "/messages/email"))
                .andExpect(jsonPath("$.to").value("nueva@example.com"))
                .andExpect(jsonPath("$.template").value("EMAIL_UPDATED"))
                .andExpect(jsonPath("$.params.maskedEmail").value("n***@example.com"))
                .andRespond(withStatus(HttpStatus.ACCEPTED));
        EmailNotification notification = new EmailNotification("nueva@example.com", Template.EMAIL_UPDATED,
                Map.of("maskedEmail", "n***@example.com"));

        messaging.sendEmail(notification);
        server.verify();

        server.reset();
        server.expect(requestTo(BASE + "/messages/email"))
                .andRespond(withException(new HttpTimeoutException("timed out")));
        assertThatThrownBy(() -> messaging.sendEmail(notification))
                .isInstanceOfSatisfying(IntegrationException.class, ex -> {
                    assertThat(ex.provider()).isEqualTo(Provider.MESSAGING);
                    assertThat(ex.category()).isEqualTo(Category.TIMEOUT);
                });
    }
}
