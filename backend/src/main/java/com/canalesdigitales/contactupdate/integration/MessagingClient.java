package com.canalesdigitales.contactupdate.integration;

import java.util.Map;

/** Puerto hacia Messaging API (doc 06): notificaciones transaccionales por correo. */
public interface MessagingClient {

    void sendEmail(EmailNotification notification);

    record EmailNotification(String to, Template template, Map<String, String> params) {
    }

    enum Template { EMAIL_UPDATED, SECURITY_NOTICE }
}
