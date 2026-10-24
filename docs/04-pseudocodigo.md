# E4. Pseudocódigo y fragmento representativo

El fragmento central es la **confirmación**: valida el código, actualiza por la interfaz gobernada, notifica y deja traza, cubriendo los caminos de error que anticipa el Mini-RAG (INC-002, INC-003, INC-005).

## 1. Pseudocódigo

```text
FUNCION confirmar(afiliadoId, transaccionId, codigo):
    tx = buscar transaccion(transaccionId)
    SI tx no existe O tx.afiliadoId != afiliadoId: LANZAR TRANSACTION_NOT_FOUND (404)
    SI tx pendiente Y ahora >= tx.venceEn: tx.estado = EXPIRED; trazar EXPIRED
    SEGUN tx.estado:
        COMPLETED: LANZAR ALREADY_COMPLETED (409) con el resultado final
        LOCKED:    LANZAR MAX_ATTEMPTS_EXCEEDED (423)
        EXPIRED:   LANZAR CODE_EXPIRED (410)
        FAILED:    LANZAR TRANSACTION_FAILED (409)

    valido = IdentityCapability.validarCodigo(tx.retoId, codigo)   # fallo tecnico: trazar PROVIDER_ERROR y propagar (503/504)
    SI NO valido:
        tx.intentos = tx.intentos + 1
        SI tx.intentos >= maximo: tx.estado = LOCKED; trazar LOCKED; LANZAR MAX_ATTEMPTS_EXCEEDED
        trazar CODE_REJECTED; LANZAR INVALID_CODE con intentos restantes

    INTENTAR: confirmacion = CustomerData.actualizarCorreo(afiliadoId, tx.correoNuevo, referencia = tx.id)
    SI falla: tx.estado = FAILED; trazar UPDATE_FAILED; PROPAGAR (503/504)

    tx.estado = COMPLETED; tx.actualizadoEn = confirmacion.actualizadoEn; trazar CONTACT_UPDATED
    notificado = enviar(correoNuevo, EMAIL_UPDATED)             # mejor esfuerzo: se traza, no revierte
    SI tx.correoAnterior: enviar(correoAnterior, SECURITY_NOTICE)
    DEVOLVER { transaccionId, COMPLETED, correoEnmascarado, actualizadoEn (ISO-8601 UTC), notificado }
```

## 2. Código real (`EmailUpdateService.java`)

```java
public EmailUpdateCompleted confirm(String affiliateId, String transactionId, String code) {
    UpdateTransaction transaction = pendingTransaction(affiliateId, transactionId);
    boolean valid = callProvider(affiliateId, transaction.id(),
            () -> identity.validateCode(transaction.challengeId(), code));
    if (!valid) {
        throw rejectedCode(transaction);
    }
    ContactUpdateConfirmation confirmation;
    try {
        confirmation = customerData.updateEmail(affiliateId, transaction.newEmail(), transaction.id());
    } catch (IntegrationException e) {
        store.save(transaction.withStatus(Status.FAILED));
        trace.record(Event.UPDATE_FAILED, Result.FAIL, Provider.CUSTOMER_DATA, transaction.id(), affiliateId,
                e.category().name());
        throw e;
    }
    UpdateTransaction updated = transaction.completed(confirmation.updatedAt(), false);
    store.save(updated);
    trace.record(Event.CONTACT_UPDATED, Result.OK, Provider.CUSTOMER_DATA, updated.id(), affiliateId,
            Masking.email(updated.newEmail()));
    UpdateTransaction done = updated.completed(confirmation.updatedAt(), notify(updated));
    store.save(done);
    return completed(done);
}

private UpdateTransaction pendingTransaction(String affiliateId, String transactionId) {
    UpdateTransaction transaction = store.find(transactionId)
            .filter(t -> t.affiliateId().equals(affiliateId))
            .orElseThrow(() -> new ContactUpdateException(ErrorCode.TRANSACTION_NOT_FOUND));
    if (transaction.isExpiredAt(clock.instant())) {
        transaction = transaction.withStatus(Status.EXPIRED);
        store.save(transaction);
        trace.record(Event.EXPIRED, Result.FAIL, null, transaction.id(), affiliateId, "vigencia vencida");
    }
    return switch (transaction.status()) {
        case PENDING_CODE -> transaction;
        case COMPLETED -> throw new ContactUpdateException(ErrorCode.ALREADY_COMPLETED)
                .with("result", completed(transaction));
        case LOCKED -> throw new ContactUpdateException(ErrorCode.MAX_ATTEMPTS_EXCEEDED);
        case EXPIRED -> throw new ContactUpdateException(ErrorCode.CODE_EXPIRED);
        case FAILED -> throw new ContactUpdateException(ErrorCode.TRANSACTION_FAILED);
    };
}
```

Decisiones visibles:

- Una transacción de otro afiliado responde 404, no 403: no revela identificadores (OWASP A01).
- Expiración perezosa al usar la transacción, con reloj inyectado para probar vigencias sin esperar (INC-003).
- Si la actualización falla después de validar, la transacción queda `FAILED`: ningún reto consumido permanece pendiente.
- La notificación nunca lanza (`notify`): devuelve si el correo principal se envió y traza cada intento (D-10).
- Toda salida va enmascarada y en ISO-8601 UTC (D-08).

## 3. Prueba unitaria representativa

```java
@Test
void should_countAttemptsAndLock_when_codeIsInvalidRepeatedly() {
    String transactionId = givenPendingTransaction();
    when(identity.validateCode("ch-1", WRONG_CODE)).thenReturn(false);

    assertThatThrownBy(() -> service.confirm(AFFILIATE, transactionId, WRONG_CODE))
            .isInstanceOfSatisfying(ContactUpdateException.class, ex -> {
                assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_CODE);
                assertThat(ex.properties()).containsEntry("remainingAttempts", 2);
            });
    assertThatThrownBy(() -> service.confirm(AFFILIATE, transactionId, WRONG_CODE))
            .isInstanceOfSatisfying(ContactUpdateException.class,
                    ex -> assertThat(ex.properties()).containsEntry("remainingAttempts", 1));
    assertThatThrownBy(() -> service.confirm(AFFILIATE, transactionId, WRONG_CODE))
            .isInstanceOfSatisfying(ContactUpdateException.class,
                    ex -> assertThat(ex.code()).isEqualTo(ErrorCode.MAX_ATTEMPTS_EXCEEDED));
    assertThat(store.find(transactionId).orElseThrow().status()).isEqualTo(Status.LOCKED);
    verify(identity, times(3)).validateCode(anyString(), anyString());
    verify(customerData, never()).updateEmail(anyString(), anyString(), anyString());
}
```

## 4. Parte asistida por IA y validación

| Generado con IA | Ajuste | Validación |
|---|---|---|
| Servicio, máquina de estados y catálogo de errores a partir de E2 | Una sola excepción de negocio y una técnica en lugar de una jerarquía | 17 pruebas unitarias y 17 de API en verde |
| Cálculo de segundos restantes para `expiresInSeconds` y `Retry-After` | Truncaba milisegundos (`Retry-After: 59`); se cambió a redondeo hacia arriba | Prueba de API del reenvío en verde tras el ajuste |
| Prueba de API | Usaba una librería JSON ausente; se reemplazó por `JsonPath` del starter de pruebas | Compilación y ejecución de la suite |
| Stub de identidad | Comparación en tiempo constante y ningún código generado ni registrado | `should_neverExposeCodeOrContactDataInTraces` |

Registro completo en `docs/07-evidencia-ia.md`.
