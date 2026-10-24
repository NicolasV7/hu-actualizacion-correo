package com.canalesdigitales.contactupdate.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Transacción de actualización de correo (docs/02 sección 4). Inmutable: cada cambio de estado produce una copia.
 * {@code destination} es el celular al que se envía el código; nunca se expone ni se registra en claro.
 */
public record UpdateTransaction(String id,
                                String affiliateId,
                                String newEmail,
                                String previousEmail,
                                String destination,
                                String challengeId,
                                Status status,
                                int attempts,
                                Instant createdAt,
                                Instant lastSentAt,
                                Instant expiresAt,
                                Instant updatedAt,
                                Boolean notificationSent) {

    public enum Status { PENDING_CODE, COMPLETED, LOCKED, EXPIRED, FAILED }

    public static UpdateTransaction start(String affiliateId, String newEmail, String previousEmail, String destination,
                                          String challengeId, Instant now, Duration ttl) {
        return new UpdateTransaction(UUID.randomUUID().toString(), affiliateId, newEmail, previousEmail, destination,
                challengeId, Status.PENDING_CODE, 0, now, now, now.plus(ttl), null, null);
    }

    public boolean isPending() {
        return status == Status.PENDING_CODE;
    }

    public boolean isExpiredAt(Instant now) {
        return isPending() && !now.isBefore(expiresAt);
    }

    public UpdateTransaction withStatus(Status newStatus) {
        return new UpdateTransaction(id, affiliateId, newEmail, previousEmail, destination, challengeId, newStatus,
                attempts, createdAt, lastSentAt, expiresAt, updatedAt, notificationSent);
    }

    public UpdateTransaction withFailedAttempt() {
        return new UpdateTransaction(id, affiliateId, newEmail, previousEmail, destination, challengeId, status,
                attempts + 1, createdAt, lastSentAt, expiresAt, updatedAt, notificationSent);
    }

    /** Reenvío: nuevo reto y vigencia renovada; los intentos se conservan para no eludir el límite. */
    public UpdateTransaction resent(String newChallengeId, Instant now, Duration ttl) {
        return new UpdateTransaction(id, affiliateId, newEmail, previousEmail, destination, newChallengeId, status,
                attempts, createdAt, now, now.plus(ttl), updatedAt, notificationSent);
    }

    public UpdateTransaction completed(Instant at, boolean notified) {
        return new UpdateTransaction(id, affiliateId, newEmail, previousEmail, destination, challengeId,
                Status.COMPLETED, attempts, createdAt, lastSentAt, expiresAt, at, notified);
    }
}
