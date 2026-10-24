package com.canalesdigitales.contactupdate.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MaskingTest {

    @Test
    void should_keepFirstCharAndDomain_when_maskingEmail() {
        assertThat(Masking.email("ana.perez@example.com")).isEqualTo("a***@example.com");
    }

    @Test
    void should_hideEverything_when_emailHasNoLocalPart() {
        assertThat(Masking.email("@example.com")).isEqualTo("***");
        assertThat(Masking.email("sin-arroba")).isEqualTo("***");
        assertThat(Masking.email(null)).isNull();
    }

    @Test
    void should_keepLastFourDigits_when_maskingMobile() {
        assertThat(Masking.mobile("3001234567")).isEqualTo("***4567");
    }

    @Test
    void should_hideEverything_when_mobileIsShortOrMissing() {
        assertThat(Masking.mobile("123")).isEqualTo("***");
        assertThat(Masking.mobile(null)).isEqualTo("***");
    }
}
