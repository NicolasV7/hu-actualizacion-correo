package com.canalesdigitales.contactupdate.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Parámetros operativos del servicio (ver README, tabla de variables).
 * Se validan al arranque: una variable faltante o inválida impide iniciar (lección de INC-004).
 */
@ConfigurationProperties(prefix = "app")
@Validated
public record AppProperties(@Valid @NotNull Verification verification,
                            @Valid @NotNull Integration integration,
                            @Valid @NotNull Stub stub) {

    /** Política del código de verificación (INC-003: vigencia y reintentos configurables). */
    public record Verification(@NotNull Duration codeTtl,
                               @Min(1) @Max(10) int maxAttempts,
                               @NotNull Duration resendCooldown) {
    }

    public record Integration(@NotNull Mode mode,
                              @NotNull Duration connectTimeout,
                              @NotNull Duration readTimeout,
                              @Valid @NotNull Endpoint customerData,
                              @Valid @NotNull Endpoint identity,
                              @Valid @NotNull Endpoint messaging) {

        public enum Mode { STUB, HTTP }
    }

    /** URL base y credencial de una API corporativa. Obligatorias solo en modo HTTP (se verifican al crear los clientes). */
    public record Endpoint(String baseUrl, String apiKey) {
    }

    /** Único código aceptado por el adaptador stub (perfiles local y test). */
    public record Stub(@NotNull @Pattern(regexp = "\\d{6}") String verificationCode) {
    }
}
