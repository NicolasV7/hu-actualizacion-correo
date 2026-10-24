package com.canalesdigitales.contactupdate.integration.stub;

import com.canalesdigitales.contactupdate.domain.Masking;
import com.canalesdigitales.contactupdate.domain.Provider;
import com.canalesdigitales.contactupdate.integration.IntegrationException;
import com.canalesdigitales.contactupdate.integration.MessagingClient;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Simulación de Messaging API (docs/02 sección 10): registra los envíos; el dominio {@code down.test} simula indisponibilidad. */
@Component
@ConditionalOnProperty(name = "app.integration.mode", havingValue = "stub")
public class StubMessagingClient implements MessagingClient {

    static final String UNAVAILABLE_DOMAIN = "@down.test";

    private static final Logger log = LoggerFactory.getLogger(StubMessagingClient.class);

    private final List<EmailNotification> sent = new CopyOnWriteArrayList<>();

    @Override
    public void sendEmail(EmailNotification notification) {
        if (notification.to().endsWith(UNAVAILABLE_DOMAIN)) {
            throw new IntegrationException(Provider.MESSAGING, IntegrationException.Category.UNAVAILABLE,
                    "stub: Messaging API no disponible");
        }
        sent.add(notification);
        log.info("stub messaging: plantilla={} destinatario={}", notification.template(), Masking.email(notification.to()));
    }

    public List<EmailNotification> sent() {
        return List.copyOf(sent);
    }
}
