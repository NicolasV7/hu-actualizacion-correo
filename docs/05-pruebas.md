# E5. Pruebas

Tipos exigidos (doc 09 y enunciado): unitarias, integración, negativas, regresión, compatibilidad (Safari) e indisponibilidad de proveedor externo. Todas las evidencias provienen de ejecuciones reales (sección 7).

## 1. Estrategia

| Nivel | Herramienta | Alcance | Cantidad |
|---|---|---|---|
| Unitarias backend | JUnit 5, Mockito, reloj controlable | Servicio, enmascarado, filtro de correlación, configuración | 31 |
| Integración API | `@SpringBootTest` + MockMvc, perfil `test` | Endpoints, `ProblemDetail` por código, correlation ID, trazas | 17 |
| Integración HTTP | `MockRestServiceServer` | Contratos asumidos, mapeo de fallos, reintento, correlación | 9 |
| Arranque | `@SpringBootTest` | Contexto en perfil `test` | 1 |
| Unitarias frontend | `node:test` | Validación, estados, cliente HTTP, fechas, compatibilidad | 25 |
| Compatibilidad e2e | Playwright WebKit y Chromium | Flujo completo contra el backend local | 2 |
| Manual | `backend/requests.http`, demo | Diez escenarios con datos sintéticos | 10 |

## 2. Cobertura de los criterios de aceptación

| CA | Pruebas |
|---|---|
| CA-01 | `should_startPendingTransaction_when_affiliateIsActiveWithMobile`; API `should_completeTheWholeFlow_when_codeIsValid` |
| CA-02 | `should_rejectEmail_when_itEqualsCurrentOne`; API correo inválido, igual al actual, JSON malformado; `validation.test.js` |
| CA-03 | `should_rejectStart_when_noMobileIsRegistered`; API con `AFF-002` |
| CA-04 | `should_rejectStart_when_affiliateIsInactive`; API con `AFF-003` |
| CA-05 | `should_updateNotifyAndComplete_when_codeIsValid`; API flujo completo; e2e |
| CA-06 | `should_countAttemptsAndLock_when_codeIsInvalidRepeatedly`; API `should_return422WithRemainingAttempts_when_codeIsWrong` |
| CA-07 | `should_countAttemptsAndLock_when_codeIsInvalidRepeatedly` (bloqueo) |
| CA-08 | `should_expireTransaction_when_ttlHasElapsed` |
| CA-09 | `should_reportAlreadyCompleted_when_confirmingTwice`; API `should_return409WithResult_when_transactionIsAlreadyCompleted`; `state.test.js` |
| CA-10 | `should_rejectResend_when_cooldownHasNotElapsed`, `should_renewCodeAndKeepAttempts_when_resendingAfterCooldown`; API `should_return429WithRetryAfter_when_resendingTooSoon` |
| CA-11 | `should_traceProviderErrorAndPropagate_when_identityProviderTimesOut`, `should_markTransactionFailed_when_updateFailsAfterValidCode`; API 504, 503 y actualización fallida; `HttpClientsTest` |
| CA-12 | `should_completeWithoutNotification_when_messagingFails`; API `should_completeWithoutNotification_when_messagingIsDown` |
| CA-13 | API flujo completo y `should_generateCorrelationId_when_requestHasNone`; `CorrelationIdFilterTest`; propagación en `HttpClientsTest` |
| CA-14 | `should_neverExposeCodeOrContactDataInTraces`; `MaskingTest`; aserción de trazas en el flujo de API |
| CA-15 | `should_hideTransaction_when_itBelongsToAnotherAffiliate`; API `should_return404_when_transactionBelongsToAnotherAffiliate` |
| CA-16 | e2e `safari-confirm.e2e.mjs` (WebKit y Chromium); `compat.test.js`; Edge comparte motor con Chromium y se certifica en QA |

## 3. Pruebas negativas por código de error

| Código | HTTP | Prueba |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Correo inválido, igual al actual, JSON malformado, código malformado |
| `UNAUTHENTICATED` | 401 | `should_return401_when_affiliateHeaderIsMissing` |
| `AFFILIATE_NOT_FOUND` | 404 | `should_return404_when_affiliateDoesNotExist` |
| `TRANSACTION_NOT_FOUND` | 404 | Transacción de otro afiliado (API y servicio) |
| `ALREADY_COMPLETED` | 409 | API y servicio |
| `TRANSACTION_FAILED` | 409 | API y servicio tras fallo de Customer Data |
| `CODE_EXPIRED` | 410 | Servicio con reloj avanzado |
| `AFFILIATE_NOT_ELIGIBLE` | 422 | API y servicio |
| `IDENTITY_CHANNEL_UNAVAILABLE` | 422 | API y servicio |
| `INVALID_CODE` | 422 | API y servicio |
| `MAX_ATTEMPTS_EXCEEDED` | 423 | Servicio |
| `RESEND_TOO_SOON` | 429 | API (`Retry-After`) y servicio |
| `PROVIDER_UNAVAILABLE` | 503 | API: identidad caída, actualización fallida |
| `PROVIDER_TIMEOUT` | 504 | API: identidad lenta |
| Ruta inexistente | 404 | `should_returnProblemDetail_when_pathDoesNotExist` |

## 4. Indisponibilidad de proveedor externo (INC-002, INC-005)

| Escenario | Simulación | Comportamiento verificado |
|---|---|---|
| Identidad excede el tiempo | `AFF-IDP-TIMEOUT`; `HttpTimeoutException` | 504 con `Retry-After`, traza `PROVIDER_ERROR`, sin transacción |
| Identidad caída | `AFF-IDP-DOWN`; 502 | 503 `PROVIDER_UNAVAILABLE` |
| Customer Data caído al actualizar | `AFF-CDS-DOWN`; 503 | 503, transacción `FAILED`, reconfirmación 409 |
| Customer Data falla una vez al leer | `ConnectException` en la primera llamada | Un reintento y éxito |
| Customer Data rechaza el contrato | 400 | Sin reintento, `REJECTED` |
| Messaging caído | correo `@down.test`; timeout | 200 con `notificationSent=false`, traza `NOTIFICATION_FAILED` |

## 5. Regresión

- Las tres suites se ejecutan completas al cierre de cada rama y antes de cada PR.
- La prueba e2e en WebKit es la regresión permanente del incidente Safari: falla con el componente anterior al fix y pasa con el actual.
- `backend/requests.http` cubre el recorrido manual de los diez escenarios.

## 6. Compatibilidad de navegador

| Navegador | Método | Resultado |
|---|---|---|
| Safari (motor WebKit 26, Playwright) | `npm run test:e2e` | Antes del fix: `ReferenceError: Can't find variable: requestIdleCallback`. Después: flujo completo sin errores |
| Chrome (Chromium, Playwright) | `npm run test:e2e` | Flujo completo antes y después |
| Edge y Safari 17 físico | Laboratorio de QA (doc 09) | Pendiente de certificación |

## 7. Ejecución y evidencias

```bash
cd backend && ./mvnw test
cd frontend && npm test
cd frontend && npm install && npx playwright install webkit chromium && npm run test:e2e   # backend en perfil local

# Equivalente portable con Docker (sin JDK ni Node en la máquina)
docker compose up --build                               # las pruebas del backend corren en la construcción de la imagen
docker compose --profile test run --rm frontend-tests
docker compose --profile test run --rm e2e
```

Resultados (2026-10-24):

```text
Backend: 58 pruebas, 0 fallos
  ContactUpdateServiceApplicationTests 1 | EmailUpdateApiTest 17 | EmailUpdateServiceTest 17
  HttpIntegrationConfigTest 2 | MaskingTest 4 | HttpClientsTest 9 | CorrelationIdFilterTest 8
Frontend: 25 pruebas, 0 fallos
Compatibilidad: webkit OK (706 ms), chromium OK (376 ms)
Arranque sin perfil ni URLs: "Falta la variable de ambiente CUSTOMER_DATA_BASE_URL (obligatoria con INTEGRATION_MODE=http)"
```

## 8. No automatizado

- Safari 17 físico y Edge: requieren el laboratorio de QA; WebKit y Chromium cubren el riesgo técnico.
- Carga y latencia real: sin ambientes de integración; los timeouts son configurables y están probados con simulaciones.
- APIs corporativas reales: los contratos son supuestos (E2 sección 6); el adaptador se probó contra un servidor simulado.
