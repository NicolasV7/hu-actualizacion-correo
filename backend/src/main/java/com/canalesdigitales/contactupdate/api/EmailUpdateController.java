package com.canalesdigitales.contactupdate.api;

import com.canalesdigitales.contactupdate.application.EmailUpdateCompleted;
import com.canalesdigitales.contactupdate.application.EmailUpdateService;
import com.canalesdigitales.contactupdate.application.EmailUpdateStarted;
import com.canalesdigitales.contactupdate.domain.ContactUpdateException;
import com.canalesdigitales.contactupdate.domain.ErrorCode;
import jakarta.validation.Valid;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de la HU (docs/02 sección 5). */
@RestController
@RequestMapping(path = "/api/v1/contact/email-updates", produces = MediaType.APPLICATION_JSON_VALUE)
public class EmailUpdateController {

    public static final String AFFILIATE_HEADER = "X-Affiliate-Id";

    private static final Pattern AFFILIATE_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    private final EmailUpdateService service;

    public EmailUpdateController(EmailUpdateService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public EmailUpdateStarted start(@RequestHeader(value = AFFILIATE_HEADER, required = false) String affiliateId,
                                    @Valid @RequestBody StartEmailUpdateRequest request) {
        return service.start(requireAffiliate(affiliateId), request.newEmail());
    }

    @PostMapping(path = "/{transactionId}/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    public EmailUpdateCompleted confirm(@RequestHeader(value = AFFILIATE_HEADER, required = false) String affiliateId,
                                        @PathVariable String transactionId,
                                        @Valid @RequestBody ConfirmEmailUpdateRequest request) {
        return service.confirm(requireAffiliate(affiliateId), transactionId, request.code());
    }

    @PostMapping(path = "/{transactionId}/resend")
    public EmailUpdateStarted resend(@RequestHeader(value = AFFILIATE_HEADER, required = false) String affiliateId,
                                     @PathVariable String transactionId) {
        return service.resend(requireAffiliate(affiliateId), transactionId);
    }

    /** El afiliado se toma solo de la cabecera que inyecta el gateway (docs/02 D-06), nunca del cuerpo. */
    private static String requireAffiliate(String header) {
        if (header == null || header.isBlank()) {
            throw new ContactUpdateException(ErrorCode.UNAUTHENTICATED);
        }
        if (!AFFILIATE_ID.matcher(header).matches()) {
            throw new ContactUpdateException(ErrorCode.VALIDATION_ERROR, "El identificador de afiliado no es válido.");
        }
        return header;
    }
}
