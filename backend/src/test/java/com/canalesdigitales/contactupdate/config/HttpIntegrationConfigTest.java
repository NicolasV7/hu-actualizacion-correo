package com.canalesdigitales.contactupdate.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.canalesdigitales.contactupdate.config.AppProperties.Endpoint;
import com.canalesdigitales.contactupdate.support.TestProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class HttpIntegrationConfigTest {

    @Test
    void should_failFastNamingTheVariable_when_baseUrlIsMissing() {
        AppProperties.Integration integration = TestProperties.defaults().integration();

        assertThatThrownBy(() -> HttpIntegrationConfig.restClient(RestClient.builder(), integration,
                new Endpoint("", "key"), "IDENTITY_BASE_URL"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IDENTITY_BASE_URL");
    }

    @Test
    void should_buildClient_when_baseUrlIsPresent() {
        AppProperties.Integration integration = TestProperties.defaults().integration();

        RestClient client = HttpIntegrationConfig.restClient(RestClient.builder(), integration,
                new Endpoint("https://identity.test", null), "IDENTITY_BASE_URL");

        assertThat(client).isNotNull();
    }
}
