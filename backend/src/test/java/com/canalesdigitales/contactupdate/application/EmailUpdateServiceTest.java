package com.canalesdigitales.contactupdate.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.canalesdigitales.contactupdate.domain.ContactUpdateException;
import com.canalesdigitales.contactupdate.domain.ErrorCode;
import com.canalesdigitales.contactupdate.domain.Provider;
import com.canalesdigitales.contactupdate.domain.UpdateTransaction;
import com.canalesdigitales.contactupdate.domain.UpdateTransaction.Status;
import com.canalesdigitales.contactupdate.integration.CustomerDataClient;
import com.canalesdigitales.contactupdate.integration.CustomerDataClient.AffiliateProfile;
import com.canalesdigitales.contactupdate.integration.CustomerDataClient.ContactUpdateConfirmation;
import com.canalesdigitales.contactupdate.integration.IdentityCapabilityClient;
import com.canalesdigitales.contactupdate.integration.IdentityCapabilityClient.VerificationChallenge;
import com.canalesdigitales.contactupdate.integration.IntegrationException;
import com.canalesdigitales.contactupdate.integration.IntegrationException.Category;
import com.canalesdigitales.contactupdate.integration.MessagingClient;
import com.canalesdigitales.contactupdate.integration.MessagingClient.EmailNotification;
import com.canalesdigitales.contactupdate.integration.MessagingClient.Template;
import com.canalesdigitales.contactupdate.observability.TraceLog;
import com.canalesdigitales.contactupdate.observability.TraceLog.Event;
import com.canalesdigitales.contactupdate.support.TestClock;
import com.canalesdigitales.contactupdate.support.TestProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailUpdateServiceTest {

    private static final String AFFILIATE = "AFF-001";
    private static final String CURRENT_EMAIL = "ana.perez@example.com";
    private static final String MOBILE = "3001234567";
    private static final String NEW_EMAIL = "nueva@example.com";
    private static final String VALID_CODE = "123456";
    private static final String WRONG_CODE = "000000";
    private static final Instant START = Instant.parse("2026-10-24T15:00:00Z");
    private static final AffiliateProfile ACTIVE_PROFILE = new AffiliateProfile(AFFILIATE, CURRENT_EMAIL, MOBILE, true);

    @Mock
    private CustomerDataClient customerData;
    @Mock
    private IdentityCapabilityClient identity;
    @Mock
    private MessagingClient messaging;

    private final TestClock clock = new TestClock(START);
    private final TransactionStore store = new TransactionStore();
    private TraceLog trace;
    private EmailUpdateService service;

    @BeforeEach
    void setUp() {
        trace = new TraceLog(clock);
        service = new EmailUpdateService(customerData, identity, messaging, store, trace, clock, TestProperties.defaults());
    }

    // --- inicio ---

    @Test
    void should_startPendingTransaction_when_affiliateIsActiveWithMobile() {
        givenProfile(ACTIVE_PROFILE);
        givenChallenge("ch-1");

        EmailUpdateStarted started = service.start(AFFILIATE, "  Nueva@Example.com ");

        assertThat(started.status()).isEqualTo("PENDING_CODE");
        assertThat(started.channel()).isEqualTo("SMS");
        assertThat(started.maskedDestination()).isEqualTo("***4567");
        assertThat(started.expiresInSeconds()).isEqualTo(TestProperties.CODE_TTL.toSeconds());
        assertThat(started.resendAvailableInSeconds()).isEqualTo(TestProperties.RESEND_COOLDOWN.toSeconds());
        assertThat(started.attemptsAllowed()).isEqualTo(TestProperties.MAX_ATTEMPTS);
        UpdateTransaction stored = store.find(started.transactionId()).orElseThrow();
        assertThat(stored.newEmail()).isEqualTo(NEW_EMAIL);
        assertThat(stored.previousEmail()).isEqualTo(CURRENT_EMAIL);
        assertThat(stored.challengeId()).isEqualTo("ch-1");
        assertThat(events(started.transactionId())).containsExactly(Event.STARTED);
    }

    @Test
    void should_rejectEmail_when_itEqualsCurrentOne() {
        givenProfile(ACTIVE_PROFILE);

        assertThatThrownBy(() -> service.start(AFFILIATE, "ANA.PEREZ@example.com"))
                .isInstanceOfSatisfying(ContactUpdateException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(identity, never()).requestCode(anyString(), anyString());
    }

    @Test
    void should_rejectStart_when_affiliateIsInactive() {
        givenProfile(new AffiliateProfile(AFFILIATE, CURRENT_EMAIL, MOBILE, false));

        assertThatThrownBy(() -> service.start(AFFILIATE, NEW_EMAIL))
                .isInstanceOfSatisfying(ContactUpdateException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.AFFILIATE_NOT_ELIGIBLE));
    }

    @Test
    void should_rejectStart_when_noMobileIsRegistered() {
        givenProfile(new AffiliateProfile(AFFILIATE, CURRENT_EMAIL, " ", true));

        assertThatThrownBy(() -> service.start(AFFILIATE, NEW_EMAIL))
                .isInstanceOfSatisfying(ContactUpdateException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.IDENTITY_CHANNEL_UNAVAILABLE));
        verify(identity, never()).requestCode(anyString(), anyString());
    }

    @Test
    void should_rejectStart_when_affiliateDoesNotExist() {
        when(customerData.findProfile(AFFILIATE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(AFFILIATE, NEW_EMAIL))
                .isInstanceOfSatisfying(ContactUpdateException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.AFFILIATE_NOT_FOUND));
    }

    @Test
    void should_traceProviderErrorAndPropagate_when_identityProviderTimesOut() {
        givenProfile(ACTIVE_PROFILE);
        when(identity.requestCode(AFFILIATE, MOBILE))
                .thenThrow(new IntegrationException(Provider.IDENTITY, Category.TIMEOUT, "timeout"));

        assertThatThrownBy(() -> service.start(AFFILIATE, NEW_EMAIL))
                .isInstanceOfSatisfying(IntegrationException.class,
                        ex -> assertThat(ex.category()).isEqualTo(Category.TIMEOUT));
        assertThat(trace.events()).extracting(TraceLog.TraceEvent::event, TraceLog.TraceEvent::provider)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(Event.PROVIDER_ERROR, Provider.IDENTITY));
    }

    // --- confirmación ---

    @Test
    void should_updateNotifyAndComplete_when_codeIsValid() {
        String transactionId = givenPendingTransaction();
        when(identity.validateCode("ch-1", VALID_CODE)).thenReturn(true);
        Instant updatedAt = START.plusSeconds(42);
        when(customerData.updateEmail(AFFILIATE, NEW_EMAIL, transactionId))
                .thenReturn(new ContactUpdateConfirmation(updatedAt));

        EmailUpdateCompleted completed = service.confirm(AFFILIATE, transactionId, VALID_CODE);

        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.maskedEmail()).isEqualTo("n***@example.com");
        assertThat(completed.updatedAt()).isEqualTo(updatedAt);
        assertThat(completed.notificationSent()).isTrue();
        ArgumentCaptor<EmailNotification> notifications = ArgumentCaptor.forClass(EmailNotification.class);
        verify(messaging, times(2)).sendEmail(notifications.capture());
        assertThat(notifications.getAllValues()).extracting(EmailNotification::to, EmailNotification::template)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(NEW_EMAIL, Template.EMAIL_UPDATED),
                        org.assertj.core.groups.Tuple.tuple(CURRENT_EMAIL, Template.SECURITY_NOTICE));
        assertThat(events(transactionId)).containsExactly(Event.STARTED, Event.CONTACT_UPDATED,
                Event.NOTIFICATION_SENT, Event.NOTIFICATION_SENT);
    }

    @Test
    void should_skipSecurityNotice_when_thereIsNoPreviousEmail() {
        givenProfile(new AffiliateProfile(AFFILIATE, null, MOBILE, true));
        givenChallenge("ch-1");
        String transactionId = service.start(AFFILIATE, NEW_EMAIL).transactionId();
        when(identity.validateCode("ch-1", VALID_CODE)).thenReturn(true);
        when(customerData.updateEmail(eq(AFFILIATE), eq(NEW_EMAIL), anyString()))
                .thenReturn(new ContactUpdateConfirmation(START));

        service.confirm(AFFILIATE, transactionId, VALID_CODE);

        verify(messaging, times(1)).sendEmail(any());
    }

    @Test
    void should_completeWithoutNotification_when_messagingFails() {
        String transactionId = givenPendingTransaction();
        when(identity.validateCode("ch-1", VALID_CODE)).thenReturn(true);
        when(customerData.updateEmail(AFFILIATE, NEW_EMAIL, transactionId))
                .thenReturn(new ContactUpdateConfirmation(START));
        doThrow(new IntegrationException(Provider.MESSAGING, Category.UNAVAILABLE, "down"))
                .when(messaging).sendEmail(any());

        EmailUpdateCompleted completed = service.confirm(AFFILIATE, transactionId, VALID_CODE);

        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.notificationSent()).isFalse();
        assertThat(store.find(transactionId).orElseThrow().status()).isEqualTo(Status.COMPLETED);
        assertThat(events(transactionId)).contains(Event.CONTACT_UPDATED, Event.NOTIFICATION_FAILED);
    }

    @Test
    void should_countAttemptsAndLock_when_codeIsInvalidRepeatedly() {
        String transactionId = givenPendingTransaction();
        when(identity.validateCode("ch-1", WRONG_CODE)).thenReturn(false);

        assertThatThrownBy(() -> service.confirm(AFFILIATE, transactionId, WRONG_CODE))
                .isInstanceOfSatisfying(ContactUpdateException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_CODE);
                    assertThat(ex.properties()).containsEntry("remainingAttempts", 2);
                });
        assertThatThrownBy(() -> service.confirm(AFFILIATE, transactionId, WRONG_CODE))
                .isInstanceOfSatisfying(ContactUpdateException.class,
                        ex -> assertThat(ex.properties()).containsEntry("remainingAttempts", 1));
        assertThatThrownBy(() -> service.confirm(AFFILIATE, transactionId, WRONG_CODE))
                .isInstanceOfSatisfying(ContactUpdateException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.MAX_ATTEMPTS_EXCEEDED));
        assertThat(store.find(transactionId).orElseThrow().status()).isEqualTo(Status.LOCKED);

        assertThatThrownBy(() -> service.confirm(AFFILIATE, transactionId, VALID_CODE))
                .isInstanceOfSatisfying(ContactUpdateException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.MAX_ATTEMPTS_EXCEEDED));
        verify(identity, times(3)).validateCode(anyString(), anyString());
        verify(customerData, never()).updateEmail(anyString(), anyString(), anyString());
        assertThat(events(transactionId)).containsExactly(Event.STARTED, Event.CODE_REJECTED, Event.CODE_REJECTED,
                Event.LOCKED);
    }

    @Test
    void should_expireTransaction_when_ttlHasElapsed() {
        String transactionId = givenPendingTransaction();
        clock.advance(TestProperties.CODE_TTL);

        assertThatThrownBy(() -> service.confirm(AFFILIATE, transactionId, VALID_CODE))
                .isInstanceOfSatisfying(ContactUpdateException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.CODE_EXPIRED));
        assertThat(store.find(transactionId).orElseThrow().status()).isEqualTo(Status.EXPIRED);
        verify(identity, never()).validateCode(anyString(), anyString());
        assertThat(events(transactionId)).containsExactly(Event.STARTED, Event.EXPIRED);
    }

    @Test
    void should_reportAlreadyCompleted_when_confirmingTwice() {
        String transactionId = givenPendingTransaction();
        when(identity.validateCode("ch-1", VALID_CODE)).thenReturn(true);
        when(customerData.updateEmail(AFFILIATE, NEW_EMAIL, transactionId))
                .thenReturn(new ContactUpdateConfirmation(START));
        service.confirm(AFFILIATE, transactionId, VALID_CODE);

        assertThatThrownBy(() -> service.confirm(AFFILIATE, transactionId, VALID_CODE))
                .isInstanceOfSatisfying(ContactUpdateException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ErrorCode.ALREADY_COMPLETED);
                    assertThat(ex.properties().get("result")).isInstanceOfSatisfying(EmailUpdateCompleted.class,
                            result -> assertThat(result.status()).isEqualTo("COMPLETED"));
                });
        verify(customerData, times(1)).updateEmail(anyString(), anyString(), anyString());
    }

    @Test
    void should_markTransactionFailed_when_updateFailsAfterValidCode() {
        String transactionId = givenPendingTransaction();
        when(identity.validateCode("ch-1", VALID_CODE)).thenReturn(true);
        when(customerData.updateEmail(AFFILIATE, NEW_EMAIL, transactionId))
                .thenThrow(new IntegrationException(Provider.CUSTOMER_DATA, Category.UNAVAILABLE, "down"));

        assertThatThrownBy(() -> service.confirm(AFFILIATE, transactionId, VALID_CODE))
                .isInstanceOf(IntegrationException.class);
        assertThat(store.find(transactionId).orElseThrow().status()).isEqualTo(Status.FAILED);
        assertThat(events(transactionId)).containsExactly(Event.STARTED, Event.UPDATE_FAILED);
        verify(messaging, never()).sendEmail(any());

        assertThatThrownBy(() -> service.confirm(AFFILIATE, transactionId, VALID_CODE))
                .isInstanceOfSatisfying(ContactUpdateException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.TRANSACTION_FAILED));
    }

    @Test
    void should_hideTransaction_when_itBelongsToAnotherAffiliate() {
        String transactionId = givenPendingTransaction();

        assertThatThrownBy(() -> service.confirm("AFF-999", transactionId, VALID_CODE))
                .isInstanceOfSatisfying(ContactUpdateException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.TRANSACTION_NOT_FOUND));
        assertThatThrownBy(() -> service.resend("AFF-999", transactionId))
                .isInstanceOfSatisfying(ContactUpdateException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.TRANSACTION_NOT_FOUND));
    }

    // --- reenvío ---

    @Test
    void should_rejectResend_when_cooldownHasNotElapsed() {
        String transactionId = givenPendingTransaction();

        assertThatThrownBy(() -> service.resend(AFFILIATE, transactionId))
                .isInstanceOfSatisfying(ContactUpdateException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(ErrorCode.RESEND_TOO_SOON);
                    assertThat(ex.properties()).containsEntry("retryAfterSeconds", TestProperties.RESEND_COOLDOWN.toSeconds());
                });
        verify(identity, times(1)).requestCode(anyString(), anyString());
    }

    @Test
    void should_renewCodeAndKeepAttempts_when_resendingAfterCooldown() {
        String transactionId = givenPendingTransaction();
        when(identity.validateCode("ch-1", WRONG_CODE)).thenReturn(false);
        assertThatThrownBy(() -> service.confirm(AFFILIATE, transactionId, WRONG_CODE))
                .isInstanceOf(ContactUpdateException.class);
        clock.advance(TestProperties.RESEND_COOLDOWN);
        when(identity.requestCode(AFFILIATE, MOBILE)).thenReturn(new VerificationChallenge("ch-2"));

        EmailUpdateStarted resent = service.resend(AFFILIATE, transactionId);

        assertThat(resent.transactionId()).isEqualTo(transactionId);
        assertThat(resent.expiresInSeconds()).isEqualTo(TestProperties.CODE_TTL.toSeconds());
        UpdateTransaction stored = store.find(transactionId).orElseThrow();
        assertThat(stored.challengeId()).isEqualTo("ch-2");
        assertThat(stored.attempts()).isEqualTo(1);
        assertThat(events(transactionId)).containsExactly(Event.STARTED, Event.CODE_REJECTED, Event.CODE_RESENT);
    }

    // --- trazabilidad ---

    @Test
    void should_neverExposeCodeOrContactDataInTraces() {
        String transactionId = givenPendingTransaction();
        when(identity.validateCode("ch-1", VALID_CODE)).thenReturn(true);
        when(customerData.updateEmail(AFFILIATE, NEW_EMAIL, transactionId))
                .thenReturn(new ContactUpdateConfirmation(START));
        service.confirm(AFFILIATE, transactionId, VALID_CODE);

        assertThat(trace.events()).extracting(TraceLog.TraceEvent::detail)
                .noneMatch(detail -> detail != null && (detail.contains(VALID_CODE) || detail.contains(NEW_EMAIL)
                        || detail.contains(CURRENT_EMAIL) || detail.contains(MOBILE)));
    }

    // --- apoyo ---

    private void givenProfile(AffiliateProfile profile) {
        when(customerData.findProfile(AFFILIATE)).thenReturn(Optional.of(profile));
    }

    private void givenChallenge(String challengeId) {
        when(identity.requestCode(AFFILIATE, MOBILE)).thenReturn(new VerificationChallenge(challengeId));
    }

    private String givenPendingTransaction() {
        givenProfile(ACTIVE_PROFILE);
        givenChallenge("ch-1");
        return service.start(AFFILIATE, NEW_EMAIL).transactionId();
    }

    private java.util.List<Event> events(String transactionId) {
        return trace.eventsOf(transactionId).stream().map(TraceLog.TraceEvent::event).toList();
    }
}
