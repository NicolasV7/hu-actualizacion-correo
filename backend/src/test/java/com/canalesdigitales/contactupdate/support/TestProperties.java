package com.canalesdigitales.contactupdate.support;

import com.canalesdigitales.contactupdate.config.AppProperties;
import com.canalesdigitales.contactupdate.config.AppProperties.Endpoint;
import com.canalesdigitales.contactupdate.config.AppProperties.Integration;
import com.canalesdigitales.contactupdate.config.AppProperties.Integration.Mode;
import com.canalesdigitales.contactupdate.config.AppProperties.Stub;
import com.canalesdigitales.contactupdate.config.AppProperties.Verification;
import java.time.Duration;

public final class TestProperties {

    public static final Duration CODE_TTL = Duration.ofMinutes(5);
    public static final int MAX_ATTEMPTS = 3;
    public static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private TestProperties() {
    }

    public static AppProperties defaults() {
        Endpoint endpoint = new Endpoint("http://localhost:1", "test-key");
        return new AppProperties(
                new Verification(CODE_TTL, MAX_ATTEMPTS, RESEND_COOLDOWN),
                new Integration(Mode.STUB, Duration.ofSeconds(2), Duration.ofSeconds(5), endpoint, endpoint, endpoint),
                new Stub("123456"));
    }
}
