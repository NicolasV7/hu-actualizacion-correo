package com.canalesdigitales.contactupdate.integration;

import java.time.Instant;
import java.util.Optional;

/** Puerto hacia Customer Data API (doc 06): perfil del afiliado y actualización de contacto por interfaz gobernada. */
public interface CustomerDataClient {

    Optional<AffiliateProfile> findProfile(String affiliateId);

    /** Actualiza el correo. {@code validationReference} identifica la validación de identidad previa (supuesto S4). */
    ContactUpdateConfirmation updateEmail(String affiliateId, String newEmail, String validationReference);

    record AffiliateProfile(String affiliateId, String email, String mobile, boolean active) {
    }

    record ContactUpdateConfirmation(Instant updatedAt) {
    }
}
