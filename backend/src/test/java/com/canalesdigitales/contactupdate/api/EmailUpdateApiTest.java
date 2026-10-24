package com.canalesdigitales.contactupdate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.canalesdigitales.contactupdate.integration.MessagingClient.EmailNotification;
import com.canalesdigitales.contactupdate.integration.stub.StubMessagingClient;
import com.canalesdigitales.contactupdate.observability.TraceLog;
import com.canalesdigitales.contactupdate.observability.TraceLog.Event;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Pruebas de integración de la API con los adaptadores stub (perfil test). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailUpdateApiTest {

    private static final String BASE = "/api/v1/contact/email-updates";
    private static final String VALID_CODE = "123456";
    private static final String UUID_PATTERN = "[0-9a-f-]{36}";
    private static final String ISO_INSTANT_PATTERN = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private TraceLog trace;
    @Autowired
    private StubMessagingClient messaging;

    @Test
    void should_completeTheWholeFlow_when_codeIsValid() throws Exception {
        String newEmail = uniqueEmail("example.com");
        String correlationId = "qa-" + UUID.randomUUID();

        MvcResult started = mvc.perform(start("AFF-001", newEmail).header("X-Correlation-Id", correlationId))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-Id", correlationId))
                .andExpect(jsonPath("$.transactionId").value(matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.status").value("PENDING_CODE"))
                .andExpect(jsonPath("$.channel").value("SMS"))
                .andExpect(jsonPath("$.maskedDestination").value("***4567"))
                .andExpect(jsonPath("$.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.resendAvailableInSeconds").value(60))
                .andExpect(jsonPath("$.attemptsAllowed").value(3))
                .andReturn();
        String transactionId = transactionId(started);

        mvc.perform(confirm("AFF-001", transactionId, VALID_CODE).header("X-Correlation-Id", correlationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(transactionId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.maskedEmail").value("n***@example.com"))
                .andExpect(jsonPath("$.updatedAt").value(matchesPattern(ISO_INSTANT_PATTERN)))
                .andExpect(jsonPath("$.notificationSent").value(true));

        assertThat(trace.eventsOf(transactionId)).extracting(TraceLog.TraceEvent::event)
                .containsExactly(Event.STARTED, Event.CONTACT_UPDATED, Event.NOTIFICATION_SENT, Event.NOTIFICATION_SENT);
        assertThat(trace.eventsOf(transactionId)).extracting(TraceLog.TraceEvent::correlationId)
                .containsOnly(correlationId);
        assertThat(trace.eventsOf(transactionId)).extracting(TraceLog.TraceEvent::detail)
                .noneMatch(detail -> detail.contains(newEmail) || detail.contains("3001234567"));
        assertThat(messaging.sent()).extracting(EmailNotification::to).contains(newEmail);
    }

    @Test
    void should_generateCorrelationId_when_requestHasNone() throws Exception {
        mvc.perform(start("AFF-002", uniqueEmail("example.com")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(header().string("X-Correlation-Id", matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.code").value("IDENTITY_CHANNEL_UNAVAILABLE"))
                .andExpect(jsonPath("$.correlationId").value(matchesPattern(UUID_PATTERN)));
    }

    @Test
    void should_return400WithFieldErrors_when_emailIsInvalid() throws Exception {
        mvc.perform(start("AFF-001", "no-es-un-correo"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("newEmail"));
    }

    @Test
    void should_return400_when_emailEqualsCurrentOne() throws Exception {
        mvc.perform(start("AFF-003", "maria.ruiz@example.com"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("AFFILIATE_NOT_ELIGIBLE"));
        mvc.perform(start("AFF-002", "LUIS.GOMEZ@example.com"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("newEmail"));
    }

    @Test
    void should_return400_when_bodyIsNotJson() throws Exception {
        mvc.perform(post(BASE).header("X-Affiliate-Id", "AFF-001")
                        .contentType(MediaType.APPLICATION_JSON).content("{esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void should_return401_when_affiliateHeaderIsMissing() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(json("newEmail", uniqueEmail("example.com"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void should_return404_when_affiliateDoesNotExist() throws Exception {
        mvc.perform(start("AFF-999", uniqueEmail("example.com")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AFFILIATE_NOT_FOUND"));
    }

    @Test
    void should_return504WithRetryAfter_when_identityProviderTimesOut() throws Exception {
        mvc.perform(start("AFF-IDP-TIMEOUT", uniqueEmail("example.com")))
                .andExpect(status().isGatewayTimeout())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.code").value("PROVIDER_TIMEOUT"))
                .andExpect(jsonPath("$.provider").value("IDENTITY"));
    }

    @Test
    void should_return503_when_identityProviderIsDown() throws Exception {
        mvc.perform(start("AFF-IDP-DOWN", uniqueEmail("example.com")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.provider").value("IDENTITY"));
    }

    @Test
    void should_return422WithRemainingAttempts_when_codeIsWrong() throws Exception {
        String transactionId = transactionId(mvc.perform(start("AFF-004", uniqueEmail("example.com"))).andReturn());

        mvc.perform(confirm("AFF-004", transactionId, "000000"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_CODE"))
                .andExpect(jsonPath("$.remainingAttempts").value(2));
    }

    @Test
    void should_return400_when_codeIsMalformed() throws Exception {
        String transactionId = transactionId(mvc.perform(start("AFF-004", uniqueEmail("example.com"))).andReturn());

        mvc.perform(confirm("AFF-004", transactionId, "12AB"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("code"));
    }

    @Test
    void should_return404_when_transactionBelongsToAnotherAffiliate() throws Exception {
        String transactionId = transactionId(mvc.perform(start("AFF-001", uniqueEmail("example.com"))).andReturn());

        mvc.perform(confirm("AFF-004", transactionId, VALID_CODE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));
    }

    @Test
    void should_return409WithResult_when_transactionIsAlreadyCompleted() throws Exception {
        String transactionId = transactionId(mvc.perform(start("AFF-001", uniqueEmail("example.com"))).andReturn());
        mvc.perform(confirm("AFF-001", transactionId, VALID_CODE)).andExpect(status().isOk());

        mvc.perform(confirm("AFF-001", transactionId, VALID_CODE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_COMPLETED"))
                .andExpect(jsonPath("$.result.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.transactionId").value(transactionId));
    }

    @Test
    void should_return429WithRetryAfter_when_resendingTooSoon() throws Exception {
        String transactionId = transactionId(mvc.perform(start("AFF-004", uniqueEmail("example.com"))).andReturn());

        mvc.perform(post(BASE + "/" + transactionId + "/resend").header("X-Affiliate-Id", "AFF-004"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("RESEND_TOO_SOON"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(60));
    }

    @Test
    void should_return503AndFailTransaction_when_updateFailsAfterValidCode() throws Exception {
        String transactionId = transactionId(mvc.perform(start("AFF-CDS-DOWN", uniqueEmail("example.com"))).andReturn());

        mvc.perform(confirm("AFF-CDS-DOWN", transactionId, VALID_CODE))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.provider").value("CUSTOMER_DATA"));
        mvc.perform(confirm("AFF-CDS-DOWN", transactionId, VALID_CODE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSACTION_FAILED"));
        assertThat(trace.eventsOf(transactionId)).extracting(TraceLog.TraceEvent::event)
                .containsExactly(Event.STARTED, Event.UPDATE_FAILED);
    }

    @Test
    void should_completeWithoutNotification_when_messagingIsDown() throws Exception {
        String transactionId = transactionId(mvc.perform(start("AFF-001", uniqueEmail("down.test"))).andReturn());

        mvc.perform(confirm("AFF-001", transactionId, VALID_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.notificationSent").value(false));
        assertThat(trace.eventsOf(transactionId)).extracting(TraceLog.TraceEvent::event)
                .contains(Event.CONTACT_UPDATED, Event.NOTIFICATION_FAILED);
    }

    @Test
    void should_returnProblemDetail_when_pathDoesNotExist() throws Exception {
        mvc.perform(get("/api/v1/no-existe").header("X-Affiliate-Id", "AFF-001"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    // --- apoyo ---

    private static MockHttpServletRequestBuilder start(String affiliateId, String newEmail) {
        return post(BASE).header("X-Affiliate-Id", affiliateId)
                .contentType(MediaType.APPLICATION_JSON).content(json("newEmail", newEmail));
    }

    private static MockHttpServletRequestBuilder confirm(String affiliateId, String transactionId, String code) {
        return post(BASE + "/" + transactionId + "/confirm").header("X-Affiliate-Id", affiliateId)
                .contentType(MediaType.APPLICATION_JSON).content(json("code", code));
    }

    private static String json(String field, String value) {
        return "{\"" + field + "\":\"" + value + "\"}";
    }

    private static String transactionId(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.transactionId");
    }

    private static String uniqueEmail(String domain) {
        return "nueva-" + UUID.randomUUID().toString().substring(0, 8) + "@" + domain;
    }
}
