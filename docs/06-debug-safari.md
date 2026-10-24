# E6. Debug del incidente Safari

## 1. Reporte

**Hecho (enunciado).** "Safari 17 falla al confirmar actualización. API retorna HTTP 200, pero consola muestra Promise rejected - Unhandled JavaScript exception. Chrome y Edge funcionan."

Lectura antes de tocar código:

- La API respondió 200: el backend actualizó el correo; el fallo está en lo que el navegador ejecuta después.
- "Unhandled Promise Rejection" es el texto de Safari cuando una función `async` lanza sin captura.
- Solo Safari: mismo patrón que INC-001 (doc 13), "funcionalidad JavaScript no soportada de forma consistente en Safari".

## 2. Reproducción

| Paso | Detalle |
|---|---|
| Entorno | Safari 17 de QA o el motor WebKit vía Playwright (`npx playwright install webkit`), que comparte JavaScriptCore con Safari |
| Datos | Backend en perfil `local`, afiliado `AFF-001`, código `123456` |
| Pasos | Escribir el correo, "Enviar código", escribir `123456`, "Confirmar" |
| WebKit | Botón bloqueado en "Validando...", sin confirmación. Red: `POST .../confirm` 200. Consola: `Unhandled Promise Rejection: ReferenceError: Can't find variable: requestIdleCallback` |
| Chromium | Flujo completo y evento `email-update:completed` |

```text
$ npm run test:e2e   (componente antes del fix)
FAIL la confirmación muestra el resultado sin errores de JavaScript en webkit (5683 ms)
OK   la confirmación muestra el resultado sin errores de JavaScript en chromium (394 ms)
```

Capturas: `docs/evidencias/safari-webkit-antes-del-fix.png` y `docs/evidencias/chrome-antes-del-fix.png`.

## 3. Hipótesis

| # | Hipótesis | Verificación | Resultado |
|---|---|---|---|
| H1 | API de JavaScript no soportada en Safari en el camino posterior a la respuesta | Stack de la consola; `typeof requestIdleCallback` devuelve `"undefined"` en Safari; MDN y caniuse confirman que Safari no la implementa | Confirmada |
| H2 | Interpretación distinta de la fecha `updatedAt` | El contrato entrega ISO-8601 UTC (D-08); sonda en WebKit: se interpreta y formatea bien | Descartada |
| H3 | APIs que exigen gesto del usuario o almacenamiento en modo privado | El componente no las usa | Descartada |
| H4 | Diferencias de `fetch`, CORS o cookies | La petición llega y responde 200 en ambos motores | Descartada |

## 4. Causa raíz

El stack apunta a `#confirm` en `email-update.js`. La telemetría de éxito (indicador del doc 11) se programaba con `requestIdleCallback` antes de pintar el resultado y dentro de un `async` sin captura. Safari no define la función: `ReferenceError`, promesa rechazada sin manejar, componente bloqueado en "Validando..." aunque el backend ya actualizó. El afiliado reintenta y recibe `409 ALREADY_COMPLETED`.

```js
const result = await this.#api.confirm(transactionId, code).catch(/* errores de la API */);
if (!result) return;
requestIdleCallback(() => this.#emitCompleted(result));   // Safari: ReferenceError
this.#dispatch({ type: 'CONFIRM_SUCCEEDED', result });    // nunca se ejecuta en Safari
```

Node tampoco define `requestIdleCallback`, por lo que la prueba unitaria `compat.test.js` reproduce el error sin navegador.

## 5. Fix (PR #2, rama `fix/INC-safari-confirmacion`)

1. `compat.js`: `deferNonCritical` usa `requestIdleCallback` si existe y `setTimeout(..., 0)` si no, y captura errores de la tarea diferida (el "polyfill" acotado que recomienda INC-001).
2. Orden: primero `CONFIRM_SUCCEEDED` (el afiliado ve el resultado), después la telemetría.
3. Red de seguridad `#run`: todo manejador envuelve su acción; un error inesperado muestra "Ocurrió un error inesperado. Intenta de nuevo." en lugar de bloquear la interfaz.

```js
let result;
try {
  result = await this.#api.confirm(transactionId, code);
} catch (error) {
  this.#dispatch({ type: 'CONFIRM_FAILED', error });
  return;
}
this.#stopTimer();
this.#dispatch({ type: 'CONFIRM_SUCCEEDED', result });
deferNonCritical(() => this.#emitCompleted(result));
```

El backend no cambió: ya respondía `ALREADY_COMPLETED` con el resultado, y el componente lo muestra como éxito. Prevención: lista de APIs vetadas (E2 sección 12), prueba e2e en WebKit en la matriz de QA, `unhandledrejection` registrado en la página y familia de navegador en los logs del backend (doc 11).

## 6. Pruebas

| Prueba | Tipo | Resultado |
|---|---|---|
| `compat.test.js` (4): reproduce el `ReferenceError`, alternativa sin la API, uso de `requestIdleCallback` si existe, fallo de la tarea no se propaga | Unitaria, regresión | 4/4 |
| `safari-confirm.e2e.mjs`: confirmación sin errores de página en WebKit y Chromium | e2e | Antes: WebKit falla, Chromium pasa. Después: 2/2 |
| Suites completas | Regresión | Frontend 25/25, backend 58/58 |

```text
$ npm run test:e2e   (componente después del fix)
OK la confirmación muestra el resultado sin errores de JavaScript en webkit (706 ms)
OK la confirmación muestra el resultado sin errores de JavaScript en chromium (376 ms)
```

Captura: `docs/evidencias/safari-webkit-despues-del-fix.png`. Edge no se ejecutó directamente (comparte motor con Chromium); Edge y Safari 17 físico se certifican en QA (doc 09).

## 7. Evidencia para el LT

| Elemento | Dónde |
|---|---|
| Causa raíz e hipótesis | Secciones 3 y 4 |
| Diff del fix con pruebas | PR #2 |
| Antes y después | Secciones 2 y 6; `docs/evidencias/` |
| Regresión permanente | `compat.test.js`, `safari-confirm.e2e.mjs` |
| Base de conocimiento (doc 13) | Propuesta INC-006: `requestIdleCallback` no soportado; detección de capacidad, orden render-primero, e2e en WebKit |
| Impacto en afiliados | Ninguna actualización perdida; los reintentos recibían `ALREADY_COMPLETED` y ahora se muestran como éxito |

Resumen para el ticket: causa raíz identificada y corregida (`requestIdleCallback` no soportado por Safari en el manejador de confirmación); fix con detección de capacidad, orden render-primero y red de seguridad; regresión unitaria y e2e en verde; sin impacto en datos; pendiente certificación de QA en Safari 17 físico y Edge.
