package com.canalesdigitales.contactupdate.application;

import java.time.Instant;

/** Resultado final de la actualización (docs/02 sección 5.2). {@code updatedAt} siempre en ISO-8601 UTC (D-08). */
public record EmailUpdateCompleted(String transactionId,
                                   String status,
                                   String maskedEmail,
                                   Instant updatedAt,
                                   boolean notificationSent) {
}
