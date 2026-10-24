# E7. Evidencia de uso de IA

Registro según los campos del doc 15 y la regla del enunciado: la IA se permite si se documentan el uso y las validaciones.

## 1. Resumen

| Campo | Valor |
|---|---|
| Iniciativa / HU | INI-2026-001, HU-001 Actualizar correo electrónico; incidente Safari 17 |
| Herramienta | Claude Code con Claude Opus 4.8 |
| Forma de trabajo | El desarrollador definió objetivos, reglas y prioridades; el asistente propuso análisis, diseño, código, pruebas y documentación; el desarrollador revisó, decidió y aprobó cada fase. Toda salida se validó ejecutando las pruebas y contrastando con los documentos citados |
| Tokens o créditos | No medidos por tarea |
| Observaciones de calidad | Suites en verde desde la sesión y desde un clon limpio; dos defectos del asistente detectados por las propias pruebas y corregidos antes del commit (R-04); una hipótesis descartada con evidencia (R-02) |

## 2. Registro de interacciones

| ID | Prompt relevante | Resultado relevante | Ajustes y validación | Decisión / descarte |
|---|---|---|---|---|
| R-01 Análisis y plan | Rol de ingeniero senior; leer DOCX y ZIP; no asumir requisitos; proponer alternativas; definir reglas de trabajo; detenerse antes de implementar | El ZIP solo trae los 15 documentos; matriz de 21 requisitos; tres alternativas de stack; plan en 9 fases | El desarrollador aprobó la alternativa Java + JS nativo y las recomendaciones | Descartados: Node.js (menor alineación con doc 07), solo documentación, H2 + JPA |
| R-02 Hipótesis Safari | Causa raíz verificable para el incidente | Primera hipótesis: fechas con formato legado en JavaScriptCore | Sondas en WebKit (Playwright): no reproduce; `requestIdleCallback` es `undefined` | Hipótesis de fechas descartada; `requestIdleCallback` adoptada y verificada en WebKit y Node |
| R-03 Diseño técnico | Componentes, contrato, estados, errores, seguridad, trazabilidad, plantilla de la sección 8 | Decisiones D-01 a D-10, 15 códigos de error, DDL, datos sintéticos, extensiones | Propiedad del correo nuevo quedó como pregunta al PO; identidad por cabecera del gateway con JWT como evolución | Aprobado como contrato antes de codificar |
| R-04 Backend | Implementar el diseño con Spring Boot 4.1: servicio, API, stubs, correlation ID, trazas, configuración, adaptadores HTTP, pruebas | 27 clases de producción, 8 de prueba, 58 pruebas | `Retry-After` devolvía 59 s por truncar milisegundos (lo detectó la prueba de API): redondeo hacia arriba. Import de una librería JSON ausente: `JsonPath`. `RetryTemplate` y paquetes de Spring Boot 4 verificados en los jar antes de usarlos | Solo starters; simplificaciones marcadas en `TransactionStore` y `TraceLog` |
| R-05 Frontend | Componente nativo sin build, estado puro, cliente HTTP, validación, demo | Seis módulos y 21 pruebas iniciales | la prueba e2e lleva el sufijo `.e2e.mjs` para que `node --test` no la ejecute con las unitarias (igual en Node 20 y 24, Windows y Linux) | La versión inicial llevaba la telemetría con `requestIdleCallback`; se dejó en el PR de la HU para reproducir el incidente y corregirlo en un PR aparte |
| R-06 Incidente Safari | Reproducir en WebKit, aislar, corregir con regresión y documentar | Reproducción, fix en `compat.js` y en el orden del manejador, red de seguridad, 4 pruebas unitarias, e2e en WebKit y Chromium, capturas | e2e ejecutada con el componente anterior (falla en WebKit) y con el corregido (pasa) | APIs vetadas y e2e en la matriz de compatibilidad |
| R-07 Documentación | Documentos con etiquetas Hecho / Recomendación / Supuesto; README; PR con checklist | Siete documentos, README, `.env.example`, `requests.http`, descripciones de PR | Cada hecho contrastado con el documento citado; comandos del README ejecutados desde un clon limpio | Textos revisados para que sean concisos |
| R-08 Repositorio | Repositorio privado, ramas por PR, merges con commit de merge | Tres PR mergeados | El merge desde GitHub no conservaba el autor acordado: se rehizo con merges locales | Integrar siempre con merge local y push a `main` |

## 3. Validaciones aplicadas

| Validación | Cómo |
|---|---|
| Fidelidad al Mini-RAG | Cada "Hecho (doc NN)" verificado contra el documento; lo no documentado quedó como Supuesto con pregunta a PO/LT |
| Corrección funcional | 58 pruebas de backend, 25 de frontend y 2 e2e, en la sesión y desde un clon limpio |
| Compatibilidad | WebKit y Chromium antes y después del fix |
| Configuración | Arranque sin URLs falla nombrando la variable (INC-004) |
| Seguridad | El código OTP nunca se registra, datos enmascarados, sin secretos; `should_neverExposeCodeOrContactDataInTraces` |
| APIs de terceros | Firma de `RetryTemplate`, `HttpClientSettings` y anotaciones de prueba comprobada en los jar |

## 4. Decisiones del desarrollador

- Alcance: solo la HU de correo; celular, facial y propiedad del correo como extensiones.
- Stack: Java 21 + Spring Boot 4.1 sin dependencias adicionales; frontend nativo.
- Proceso: fases aprobadas una a una; pruebas antes de cada commit; convención de commits y PR.
- Entrega: repositorio privado, autor de Git, ramas y PR.

## 5. Límites

- Los contratos de las tres APIs son supuestos a partir del catálogo (doc 06); confirmar con Integración antes de activar el modo `http`.
- El código generado se revisó y probó; E2 y E4 explican las decisiones y el fragmento central para su mantenimiento.
