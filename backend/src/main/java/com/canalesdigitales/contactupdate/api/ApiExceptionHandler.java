package com.canalesdigitales.contactupdate.api;

import com.canalesdigitales.contactupdate.domain.ContactUpdateException;
import com.canalesdigitales.contactupdate.domain.ErrorCode;
import com.canalesdigitales.contactupdate.integration.IntegrationException;
import com.canalesdigitales.contactupdate.observability.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Manejo centralizado de errores (doc 07): toda respuesta de error es un ProblemDetail (RFC 9457) con
 * {@code code} del catálogo y {@code correlationId}. Los errores propios de Spring MVC (404, 405, JSON inválido...)
 * los resuelve la clase base con el mismo formato.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String DEFAULT_RETRY_AFTER_SECONDS = "30";

    @ExceptionHandler(ContactUpdateException.class)
    public ResponseEntity<Object> handleDomain(ContactUpdateException ex, HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        if (ex.code() == ErrorCode.RESEND_TOO_SOON) {
            headers.set(HttpHeaders.RETRY_AFTER,
                    String.valueOf(ex.properties().getOrDefault("retryAfterSeconds", DEFAULT_RETRY_AFTER_SECONDS)));
        }
        return respond(ex.code(), ex.detail(), ex.properties(), request.getRequestURI(), headers);
    }

    @ExceptionHandler(IntegrationException.class)
    public ResponseEntity<Object> handleIntegration(IntegrationException ex, HttpServletRequest request) {
        ErrorCode code = ex.category() == IntegrationException.Category.TIMEOUT
                ? ErrorCode.PROVIDER_TIMEOUT : ErrorCode.PROVIDER_UNAVAILABLE;
        log.warn("Fallo de proveedor provider={} category={} detail={}", ex.provider(), ex.category(), ex.getMessage());
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, DEFAULT_RETRY_AFTER_SECONDS);
        return respond(code, code.defaultDetail(), Map.of("provider", ex.provider().name()), request.getRequestURI(),
                headers);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage())))
                .toList();
        return respond(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultDetail(), Map.of("errors", errors),
                uri(request), new HttpHeaders());
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        return respond(ErrorCode.VALIDATION_ERROR, "El cuerpo de la petición no es un JSON válido.", Map.of(),
                uri(request), new HttpHeaders());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Error no controlado path={}", request.getRequestURI(), ex);
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultDetail(), Map.of(),
                request.getRequestURI(), new HttpHeaders());
    }

    private static ResponseEntity<Object> respond(ErrorCode code, String detail, Map<String, ?> properties,
                                                  String instance, HttpHeaders headers) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(code.httpStatus()), detail);
        problem.setTitle(code.title());
        problem.setInstance(URI.create(instance));
        problem.setProperty("code", code.name());
        problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID));
        properties.forEach(problem::setProperty);
        return ResponseEntity.status(code.httpStatus())
                .headers(headers)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private static String uri(WebRequest request) {
        return ((ServletWebRequest) request).getRequest().getRequestURI();
    }
}
