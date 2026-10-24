package com.canalesdigitales.contactupdate.integration.stub;

import com.canalesdigitales.contactupdate.domain.Provider;
import com.canalesdigitales.contactupdate.integration.CustomerDataClient;
import com.canalesdigitales.contactupdate.integration.IntegrationException;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Datos sintéticos de Customer Data API (docs/02 sección 10). Activo solo con {@code app.integration.mode=stub}.
 * El correo se actualiza en memoria para que un segundo intento lo vea como el correo actual.
 */
@Component
@ConditionalOnProperty(name = "app.integration.mode", havingValue = "stub")
public class StubCustomerDataClient implements CustomerDataClient {

    static final String UPDATE_UNAVAILABLE_AFFILIATE = "AFF-CDS-DOWN";

    private final Map<String, AffiliateProfile> profiles = new ConcurrentHashMap<>(Map.of(
            "AFF-001", new AffiliateProfile("AFF-001", "ana.perez@example.com", "3001234567", true),
            "AFF-002", new AffiliateProfile("AFF-002", "luis.gomez@example.com", null, true),
            "AFF-003", new AffiliateProfile("AFF-003", "maria.ruiz@example.com", "3155550123", false),
            "AFF-004", new AffiliateProfile("AFF-004", null, "3109876543", true),
            StubIdentityCapabilityClient.TIMEOUT_AFFILIATE,
            new AffiliateProfile(StubIdentityCapabilityClient.TIMEOUT_AFFILIATE, "timeout@example.com", "3000000001", true),
            StubIdentityCapabilityClient.UNAVAILABLE_AFFILIATE,
            new AffiliateProfile(StubIdentityCapabilityClient.UNAVAILABLE_AFFILIATE, "down@example.com", "3000000002", true),
            UPDATE_UNAVAILABLE_AFFILIATE,
            new AffiliateProfile(UPDATE_UNAVAILABLE_AFFILIATE, "cds@example.com", "3000000003", true)));

    private final Clock clock;

    public StubCustomerDataClient(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Optional<AffiliateProfile> findProfile(String affiliateId) {
        return Optional.ofNullable(profiles.get(affiliateId));
    }

    @Override
    public ContactUpdateConfirmation updateEmail(String affiliateId, String newEmail, String validationReference) {
        if (UPDATE_UNAVAILABLE_AFFILIATE.equals(affiliateId)) {
            throw new IntegrationException(Provider.CUSTOMER_DATA, IntegrationException.Category.UNAVAILABLE,
                    "stub: Customer Data API no disponible");
        }
        AffiliateProfile current = profiles.get(affiliateId);
        if (current == null) {
            throw new IntegrationException(Provider.CUSTOMER_DATA, IntegrationException.Category.REJECTED,
                    "stub: afiliado desconocido");
        }
        profiles.put(affiliateId, new AffiliateProfile(affiliateId, newEmail, current.mobile(), current.active()));
        return new ContactUpdateConfirmation(clock.instant());
    }
}
