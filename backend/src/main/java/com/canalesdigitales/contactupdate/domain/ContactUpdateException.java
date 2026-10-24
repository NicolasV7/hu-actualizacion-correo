package com.canalesdigitales.contactupdate.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Única excepción de negocio del flujo. El manejador de la API la traduce a un ProblemDetail. */
public class ContactUpdateException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> properties = new LinkedHashMap<>();

    public ContactUpdateException(ErrorCode code) {
        this(code, code.defaultDetail());
    }

    public ContactUpdateException(ErrorCode code, String detail) {
        super(detail);
        this.code = code;
    }

    /** Agrega una propiedad extra al ProblemDetail (por ejemplo, intentos restantes). */
    public ContactUpdateException with(String property, Object value) {
        properties.put(property, value);
        return this;
    }

    public ErrorCode code() {
        return code;
    }

    public String detail() {
        return getMessage();
    }

    public Map<String, Object> properties() {
        return Collections.unmodifiableMap(properties);
    }
}
