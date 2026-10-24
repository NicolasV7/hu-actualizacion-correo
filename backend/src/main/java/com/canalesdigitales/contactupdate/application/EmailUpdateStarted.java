package com.canalesdigitales.contactupdate.application;

/** Respuesta al iniciar o reenviar: lo que el afiliado necesita para digitar el código (docs/02 sección 5.1). */
public record EmailUpdateStarted(String transactionId,
                                 String status,
                                 String channel,
                                 String maskedDestination,
                                 long expiresInSeconds,
                                 long resendAvailableInSeconds,
                                 int attemptsAllowed) {
}
