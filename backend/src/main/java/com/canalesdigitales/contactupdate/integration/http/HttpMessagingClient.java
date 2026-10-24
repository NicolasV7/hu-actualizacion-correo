package com.canalesdigitales.contactupdate.integration.http;

import com.canalesdigitales.contactupdate.domain.Provider;
import com.canalesdigitales.contactupdate.integration.MessagingClient;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Adaptador HTTP de Messaging API (docs/02 sección 6). Sin reintentos: el envío no es idempotente (supuesto S6). */
public class HttpMessagingClient implements MessagingClient {

    private final RestClient restClient;

    public HttpMessagingClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public void sendEmail(EmailNotification notification) {
        try {
            restClient.post()
                    .uri("/messages/email")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new EmailRequest(notification.to(), notification.template().name(), notification.params()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw IntegrationErrors.map(Provider.MESSAGING, exception);
        }
    }

    record EmailRequest(String to, String template, Map<String, String> params) {
    }
}
