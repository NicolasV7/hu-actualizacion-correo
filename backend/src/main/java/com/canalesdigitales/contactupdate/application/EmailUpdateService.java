package com.canalesdigitales.contactupdate.application;

import com.canalesdigitales.contactupdate.config.AppProperties;
import com.canalesdigitales.contactupdate.domain.ContactUpdateException;
import com.canalesdigitales.contactupdate.domain.ErrorCode;
import com.canalesdigitales.contactupdate.domain.Masking;
import com.canalesdigitales.contactupdate.domain.Provider;
import com.canalesdigitales.contactupdate.domain.UpdateTransaction;
import com.canalesdigitales.contactupdate.domain.UpdateTransaction.Status;
import com.canalesdigitales.contactupdate.integration.CustomerDataClient;
import com.canalesdigitales.contactupdate.integration.CustomerDataClient.AffiliateProfile;
import com.canalesdigitales.contactupdate.integration.CustomerDataClient.ContactUpdateConfirmation;
import com.canalesdigitales.contactupdate.integration.IdentityCapabilityClient;
import com.canalesdigitales.contactupdate.integration.IdentityCapabilityClient.VerificationChallenge;
import com.canalesdigitales.contactupdate.integration.IntegrationException;
import com.canalesdigitales.contactupdate.integration.MessagingClient;
import com.canalesdigitales.contactupdate.integration.MessagingClient.EmailNotification;
import com.canalesdigitales.contactupdate.integration.MessagingClient.Template;
import com.canalesdigitales.contactupdate.observability.TraceLog;
import com.canalesdigitales.contactupdate.observability.TraceLog.Event;
import com.canalesdigitales.contactupdate.observability.TraceLog.Result;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orquesta la actualización del correo (docs/02 sección 3): inicio con validación de identidad, confirmación con el código,
 * actualización por la interfaz gobernada, notificación de mejor esfuerzo y trazabilidad de cada paso.
 */
@Service
public class EmailUpdateService {

    private static final Logger log = LoggerFactory.getLogger(EmailUpdateService.class);
    private static final String CHANNEL = "SMS";

    private final CustomerDataClient customerData;
    private final IdentityCapabilityClient identity;
    private final MessagingClient messaging;
    private final TransactionStore store;
    private final TraceLog trace;
    private final Clock clock;
    private final AppProperties.Verification policy;

    public EmailUpdateService(CustomerDataClient customerData, IdentityCapabilityClient identity,
                              MessagingClient messaging, TransactionStore store, TraceLog trace, Clock clock,
                              AppProperties properties) {
        this.customerData = customerData;
        this.identity = identity;
        this.messaging = messaging;
        this.store = store;
        this.trace = trace;
        this.clock = clock;
        this.policy = properties.verification();
    }

    public EmailUpdateStarted start(String affiliateId, String requestedEmail) {
        String newEmail = normalize(requestedEmail);
        AffiliateProfile profile = callProvider(affiliateId, null, () -> customerData.findProfile(affiliateId))
                .orElseThrow(() -> new ContactUpdateException(ErrorCode.AFFILIATE_NOT_FOUND));
        if (!profile.active()) {
            throw new ContactUpdateException(ErrorCode.AFFILIATE_NOT_ELIGIBLE);
        }
        if (newEmail.equalsIgnoreCase(profile.email())) {
            throw new ContactUpdateException(ErrorCode.VALIDATION_ERROR, "El correo nuevo es igual al registrado actualmente.")
                    .with("errors", List.of(Map.of("field", "newEmail", "message", "Debe ser distinto del correo actual")));
        }
        if (profile.mobile() == null || profile.mobile().isBlank()) {
            throw new ContactUpdateException(ErrorCode.IDENTITY_CHANNEL_UNAVAILABLE);
        }
        VerificationChallenge challenge = callProvider(affiliateId, null,
                () -> identity.requestCode(affiliateId, profile.mobile()));
        Instant now = clock.instant();
        UpdateTransaction transaction = UpdateTransaction.start(affiliateId, newEmail, profile.email(), profile.mobile(),
                challenge.challengeId(), now, policy.codeTtl());
        store.save(transaction);
        trace.record(Event.STARTED, Result.OK, Provider.IDENTITY, transaction.id(), affiliateId,
                "destino " + Masking.mobile(profile.mobile()));
        return started(transaction, now);
    }

    public EmailUpdateCompleted confirm(String affiliateId, String transactionId, String code) {
        UpdateTransaction transaction = pendingTransaction(affiliateId, transactionId);
        boolean valid = callProvider(affiliateId, transaction.id(),
                () -> identity.validateCode(transaction.challengeId(), code));
        if (!valid) {
            throw rejectedCode(transaction);
        }
        ContactUpdateConfirmation confirmation;
        try {
            confirmation = customerData.updateEmail(affiliateId, transaction.newEmail(), transaction.id());
        } catch (IntegrationException e) {
            store.save(transaction.withStatus(Status.FAILED));
            trace.record(Event.UPDATE_FAILED, Result.FAIL, Provider.CUSTOMER_DATA, transaction.id(), affiliateId,
                    e.category().name());
            throw e;
        }
        UpdateTransaction updated = transaction.completed(confirmation.updatedAt(), false);
        store.save(updated);
        trace.record(Event.CONTACT_UPDATED, Result.OK, Provider.CUSTOMER_DATA, updated.id(), affiliateId,
                Masking.email(updated.newEmail()));
        UpdateTransaction done = updated.completed(confirmation.updatedAt(), notify(updated));
        store.save(done);
        return completed(done);
    }

    public EmailUpdateStarted resend(String affiliateId, String transactionId) {
        UpdateTransaction transaction = pendingTransaction(affiliateId, transactionId);
        Instant now = clock.instant();
        Instant availableAt = transaction.lastSentAt().plus(policy.resendCooldown());
        if (now.isBefore(availableAt)) {
            throw new ContactUpdateException(ErrorCode.RESEND_TOO_SOON)
                    .with("retryAfterSeconds", secondsUntil(now, availableAt));
        }
        VerificationChallenge challenge = callProvider(affiliateId, transaction.id(),
                () -> identity.requestCode(affiliateId, transaction.destination()));
        UpdateTransaction renewed = transaction.resent(challenge.challengeId(), now, policy.codeTtl());
        store.save(renewed);
        trace.record(Event.CODE_RESENT, Result.OK, Provider.IDENTITY, renewed.id(), affiliateId,
                "destino " + Masking.mobile(renewed.destination()));
        return started(renewed, now);
    }

    /** Transacción del afiliado en estado pendiente; cualquier otro estado se traduce al error correspondiente. */
    private UpdateTransaction pendingTransaction(String affiliateId, String transactionId) {
        UpdateTransaction transaction = store.find(transactionId)
                .filter(t -> t.affiliateId().equals(affiliateId))
                .orElseThrow(() -> new ContactUpdateException(ErrorCode.TRANSACTION_NOT_FOUND));
        if (transaction.isExpiredAt(clock.instant())) {
            transaction = transaction.withStatus(Status.EXPIRED);
            store.save(transaction);
            trace.record(Event.EXPIRED, Result.FAIL, null, transaction.id(), affiliateId, "vigencia vencida");
        }
        return switch (transaction.status()) {
            case PENDING_CODE -> transaction;
            case COMPLETED -> throw new ContactUpdateException(ErrorCode.ALREADY_COMPLETED)
                    .with("result", completed(transaction));
            case LOCKED -> throw new ContactUpdateException(ErrorCode.MAX_ATTEMPTS_EXCEEDED);
            case EXPIRED -> throw new ContactUpdateException(ErrorCode.CODE_EXPIRED);
            case FAILED -> throw new ContactUpdateException(ErrorCode.TRANSACTION_FAILED);
        };
    }

    private ContactUpdateException rejectedCode(UpdateTransaction transaction) {
        UpdateTransaction attempted = transaction.withFailedAttempt();
        int remaining = policy.maxAttempts() - attempted.attempts();
        if (remaining <= 0) {
            store.save(attempted.withStatus(Status.LOCKED));
            trace.record(Event.LOCKED, Result.FAIL, Provider.IDENTITY, attempted.id(), attempted.affiliateId(),
                    "intentos agotados");
            return new ContactUpdateException(ErrorCode.MAX_ATTEMPTS_EXCEEDED);
        }
        store.save(attempted);
        trace.record(Event.CODE_REJECTED, Result.FAIL, Provider.IDENTITY, attempted.id(), attempted.affiliateId(),
                "intentos restantes " + remaining);
        return new ContactUpdateException(ErrorCode.INVALID_CODE,
                "El código no es válido. Te quedan " + remaining + " intentos.")
                .with("remainingAttempts", remaining);
    }

    /** Notificación de mejor esfuerzo (D-10): el fallo no revierte la actualización, se traza y se informa. */
    private boolean notify(UpdateTransaction transaction) {
        Map<String, String> params = Map.of("maskedEmail", Masking.email(transaction.newEmail()));
        boolean sent = send(transaction, new EmailNotification(transaction.newEmail(), Template.EMAIL_UPDATED, params));
        if (transaction.previousEmail() != null && !transaction.previousEmail().isBlank()) {
            send(transaction, new EmailNotification(transaction.previousEmail(), Template.SECURITY_NOTICE, params));
        }
        return sent;
    }

    private boolean send(UpdateTransaction transaction, EmailNotification notification) {
        String detail = notification.template() + " a " + Masking.email(notification.to());
        try {
            messaging.sendEmail(notification);
            trace.record(Event.NOTIFICATION_SENT, Result.OK, Provider.MESSAGING, transaction.id(),
                    transaction.affiliateId(), detail);
            return true;
        } catch (IntegrationException e) {
            log.warn("Notificación no enviada transactionId={} causa={}", transaction.id(), e.getMessage());
            trace.record(Event.NOTIFICATION_FAILED, Result.FAIL, Provider.MESSAGING, transaction.id(),
                    transaction.affiliateId(), detail + " (" + e.category() + ")");
            return false;
        }
    }

    /** Ejecuta una llamada a un proveedor dejando traza del fallo técnico antes de propagarlo. */
    private <T> T callProvider(String affiliateId, String transactionId, Supplier<T> call) {
        try {
            return call.get();
        } catch (IntegrationException e) {
            trace.record(Event.PROVIDER_ERROR, Result.FAIL, e.provider(), transactionId, affiliateId, e.category().name());
            throw e;
        }
    }

    private EmailUpdateStarted started(UpdateTransaction transaction, Instant now) {
        return new EmailUpdateStarted(transaction.id(), transaction.status().name(), CHANNEL,
                Masking.mobile(transaction.destination()), secondsUntil(now, transaction.expiresAt()),
                secondsUntil(now, transaction.lastSentAt().plus(policy.resendCooldown())), policy.maxAttempts());
    }

    private static EmailUpdateCompleted completed(UpdateTransaction transaction) {
        return new EmailUpdateCompleted(transaction.id(), transaction.status().name(),
                Masking.email(transaction.newEmail()), transaction.updatedAt(),
                Boolean.TRUE.equals(transaction.notificationSent()));
    }

    /** Redondea hacia arriba: nunca anuncia menos tiempo del que realmente queda. */
    private static long secondsUntil(Instant now, Instant target) {
        long millis = Duration.between(now, target).toMillis();
        return millis <= 0 ? 0 : (millis + 999) / 1000;
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
