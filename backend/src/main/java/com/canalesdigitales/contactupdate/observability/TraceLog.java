package com.canalesdigitales.contactupdate.observability;

import com.canalesdigitales.contactupdate.domain.Provider;
import java.time.Clock;
import java.time.Instant;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Trazabilidad transaccional (docs/02 sección 9): cada evento se escribe como log estructurado y se conserva en memoria.
 * El detalle siempre llega enmascarado; nunca se registran códigos ni datos de contacto en claro (doc 08).
 */
@Component
public class TraceLog {

    public enum Event {
        STARTED, CODE_RESENT, CODE_REJECTED, LOCKED, EXPIRED, CONTACT_UPDATED, UPDATE_FAILED,
        NOTIFICATION_SENT, NOTIFICATION_FAILED, PROVIDER_ERROR
    }

    public enum Result { OK, FAIL }

    public record TraceEvent(Instant occurredAt, String transactionId, String correlationId, String affiliateId,
                             Event event, Result result, Provider provider, String detail, String browser) {
    }

    // Simplificación deliberada: memoria acotada de una sola instancia; en producción se persiste (DDL en docs/02 sección 9).
    private static final int MAX_EVENTS = 1000;
    private static final Logger log = LoggerFactory.getLogger(TraceLog.class);

    private final Deque<TraceEvent> events = new ConcurrentLinkedDeque<>();
    private final Clock clock;

    public TraceLog(Clock clock) {
        this.clock = clock;
    }

    public void record(Event event, Result result, Provider provider, String transactionId, String affiliateId,
                       String detail) {
        TraceEvent traceEvent = new TraceEvent(clock.instant(), transactionId, MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID),
                affiliateId, event, result, provider, detail, MDC.get(CorrelationIdFilter.MDC_BROWSER));
        events.addLast(traceEvent);
        while (events.size() > MAX_EVENTS) {
            events.pollFirst();
        }
        log.info("trace event={} result={} transactionId={} affiliateId={} provider={} detail={}",
                event, result, transactionId, affiliateId, provider, detail);
    }

    public List<TraceEvent> events() {
        return List.copyOf(events);
    }

    public List<TraceEvent> eventsOf(String transactionId) {
        return events.stream().filter(e -> transactionId.equals(e.transactionId())).toList();
    }
}
