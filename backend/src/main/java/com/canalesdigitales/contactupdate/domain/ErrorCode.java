package com.canalesdigitales.contactupdate.domain;

/**
 * Catálogo de errores del contrato (docs/02 sección 5.4). El detalle por defecto está escrito para el afiliado:
 * en español, sin información interna.
 */
public enum ErrorCode {

    VALIDATION_ERROR(400, "Datos inválidos", "Revisa los datos enviados."),
    UNAUTHENTICATED(401, "No autenticado", "No fue posible identificar al afiliado."),
    AFFILIATE_NOT_FOUND(404, "Afiliado no encontrado", "No encontramos tu información de afiliado."),
    TRANSACTION_NOT_FOUND(404, "Solicitud no encontrada",
            "La solicitud no existe o ya no está disponible. Inicia el proceso de nuevo."),
    ALREADY_COMPLETED(409, "Actualización ya realizada", "Tu correo ya fue actualizado con esta solicitud."),
    TRANSACTION_FAILED(409, "Solicitud no completada",
            "La solicitud anterior no pudo completarse. Inicia el proceso de nuevo."),
    CODE_EXPIRED(410, "Código vencido", "El código de verificación venció. Inicia el proceso de nuevo."),
    AFFILIATE_NOT_ELIGIBLE(422, "Afiliado no habilitado",
            "Tu afiliación no está activa para realizar esta actualización."),
    IDENTITY_CHANNEL_UNAVAILABLE(422, "Sin canal de verificación",
            "No tienes un celular registrado para enviarte el código. Comunícate con el Contact Center o acércate a una oficina."),
    INVALID_CODE(422, "Código de verificación inválido", "El código no es válido."),
    MAX_ATTEMPTS_EXCEEDED(423, "Intentos agotados",
            "Superaste el número de intentos permitidos. Inicia el proceso de nuevo."),
    RESEND_TOO_SOON(429, "Reenvío no disponible aún", "Espera unos segundos antes de solicitar un nuevo código."),
    INTERNAL_ERROR(500, "Error inesperado", "Ocurrió un error inesperado. Intenta de nuevo más tarde."),
    PROVIDER_UNAVAILABLE(503, "Servicio no disponible",
            "Uno de nuestros servicios no está disponible en este momento. Intenta de nuevo en unos minutos."),
    PROVIDER_TIMEOUT(504, "Tiempo de espera agotado",
            "El servicio tardó demasiado en responder. Intenta de nuevo en unos minutos.");

    private final int httpStatus;
    private final String title;
    private final String defaultDetail;

    ErrorCode(int httpStatus, String title, String defaultDetail) {
        this.httpStatus = httpStatus;
        this.title = title;
        this.defaultDetail = defaultDetail;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String title() {
        return title;
    }

    public String defaultDetail() {
        return defaultDetail;
    }
}
