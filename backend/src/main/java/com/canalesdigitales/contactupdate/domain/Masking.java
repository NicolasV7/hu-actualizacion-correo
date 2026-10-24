package com.canalesdigitales.contactupdate.domain;

/** Enmascarado de datos de contacto para respuestas, logs y trazas (doc 08: sin datos en claro). */
public final class Masking {

    private Masking() {
    }

    /** {@code ana.perez@example.com} se convierte en {@code a***@example.com}. */
    public static String email(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    /** {@code 3001234567} se convierte en {@code ***4567}. */
    public static String mobile(String mobile) {
        if (mobile == null || mobile.length() < 4) {
            return "***";
        }
        return "***" + mobile.substring(mobile.length() - 4);
    }
}
