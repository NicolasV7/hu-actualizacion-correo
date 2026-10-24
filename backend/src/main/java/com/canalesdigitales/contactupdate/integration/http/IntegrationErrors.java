package com.canalesdigitales.contactupdate.integration.http;

import com.canalesdigitales.contactupdate.domain.Provider;
import com.canalesdigitales.contactupdate.integration.IntegrationException;
import com.canalesdigitales.contactupdate.integration.IntegrationException.Category;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

/** Traduce los fallos de RestClient a las tres categorías del diseño (docs/02 sección 6). */
final class IntegrationErrors {

    private IntegrationErrors() {
    }

    static IntegrationException map(Provider provider, RestClientException exception) {
        if (exception instanceof ResourceAccessException access) {
            Category category = isTimeout(access.getCause()) ? Category.TIMEOUT : Category.UNAVAILABLE;
            return new IntegrationException(provider, category, access.getMessage(), access);
        }
        if (exception instanceof HttpServerErrorException server) {
            return new IntegrationException(provider, Category.UNAVAILABLE, "HTTP " + server.getStatusCode(), server);
        }
        if (exception instanceof HttpStatusCodeException client) {
            return new IntegrationException(provider, Category.REJECTED, "HTTP " + client.getStatusCode(), client);
        }
        return new IntegrationException(provider, Category.UNAVAILABLE, exception.getMessage(), exception);
    }

    private static boolean isTimeout(Throwable cause) {
        return cause instanceof SocketTimeoutException
                || cause instanceof HttpTimeoutException
                || cause instanceof HttpConnectTimeoutException;
    }
}
