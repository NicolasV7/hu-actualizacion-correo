package com.canalesdigitales.contactupdate.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CorrelationIdFilterTest {

    @Test
    void should_keepIncomingId_when_itIsValid() {
        assertThat(CorrelationIdFilter.resolveCorrelationId("portal-2026.10.24_abc")).isEqualTo("portal-2026.10.24_abc");
    }

    @Test
    void should_generateUuid_when_idIsMissingOrInvalid() {
        assertThat(CorrelationIdFilter.resolveCorrelationId(null)).matches("[0-9a-f-]{36}");
        assertThat(CorrelationIdFilter.resolveCorrelationId("con espacios")).matches("[0-9a-f-]{36}");
        assertThat(CorrelationIdFilter.resolveCorrelationId("x".repeat(65))).matches("[0-9a-f-]{36}");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15 | Safari",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 | Chrome",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0 | Edge",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0 | Firefox",
            "curl/8.4.0 | Other"
    })
    void should_classifyBrowserFamily(String userAgent, String expected) {
        assertThat(CorrelationIdFilter.browserFamily(userAgent)).isEqualTo(expected);
    }

    @Test
    void should_returnOther_when_userAgentIsMissing() {
        assertThat(CorrelationIdFilter.browserFamily(null)).isEqualTo("Other");
    }
}
