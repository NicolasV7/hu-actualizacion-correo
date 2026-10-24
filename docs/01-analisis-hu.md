# E1. Análisis de la historia de usuario

Convención de todos los documentos: **Hecho (doc NN)** viene del Mini-RAG o del enunciado; **Recomendación** es una decisión técnica justificada; **Supuesto** es algo no documentado que requiere validación de PO/LT.

## 1. HU y criterios iniciales

**Hecho (enunciado).** "Como afiliado quiero actualizar mi correo electrónico para recibir comunicaciones en mi nueva dirección." Criterios: validar identidad, validar correo, confirmar actualización, notificar y registrar trazabilidad.

## 2. Entendimiento del problema

**Hecho (doc 01, doc 14).** Los afiliados no completan procesos digitales porque el código de verificación llega a datos de contacto desactualizados. La iniciativa INI-2026-001 busca un flujo digital con validación de identidad, trazabilidad y confirmación. Restricciones: flujo en menos de 3 minutos, validación de identidad en 60 segundos o menos.

**Observación clave.** Si el correo está desactualizado, el código debe ir por otro canal: el **celular registrado** (SMS). Sin celular válido se necesitaría validación facial, cuyo proveedor y contrato no existen (doc 01, doc 06).

| Dentro del alcance | Fuera (extensión documentada en E2) |
|---|---|
| Actualizar el correo del afiliado autenticado | Actualizar el celular |
| Validar identidad con código SMS al celular registrado | Validación facial |
| Validar el correo nuevo (formato, normalización, distinto del actual) | Verificar propiedad del correo nuevo (P-03) |
| Confirmar por interfaz gobernada, notificar y trazar | Flujo asistido para validaciones fallidas |

## 3. Hechos del Mini-RAG que condicionan la HU

| Doc | Hecho | Implicación |
|---|---|---|
| 01 Iniciativa (borrador) | Pendientes: proveedor facial, reglas de reintentos, atención de validaciones fallidas | Expiración y reintentos configurables; respuesta clara sin canal de validación |
| 02 Arquitectura (vigente) y 03 Roadmap | Portal 7.0 con legados; lo nuevo desacoplado sobre 7.4; riesgo de JS incompatible y sin regresión en Safari | Componente nativo, sin librerías del portal, probado en Safari |
| 04 IntegrationHub | Actualizaciones solo por interfaces gobernadas; límites de tiempo de respuesta | Timeouts explícitos; ninguna escritura directa |
| 06 Catálogo APIs | Customer Data (perfil, actualizar contacto), Identity Capability (generar y validar código), Messaging (correo, SMS); contratos sin detalle | Tres integraciones con contratos asumidos (E2) |
| 07 Microservicios | Logging estructurado, correlation ID, errores centralizados, pruebas unitarias, endpoints y variables documentadas | Lineamientos no funcionales del servicio |
| 08 Ciberseguridad | TLS, secretos, validación de entradas, OWASP, eventos de seguridad, sin biometría, escaneo de 3 días | Controles de diseño y checklist del PR |
| 09 QA | Pruebas funcionales, negativas, integración, regresión, navegadores, indisponibilidad de externos | Matriz de pruebas (E5) |
| 11 Observabilidad y 14 KPIs | Correlation ID, logs estructurados, errores por navegador, disponibilidad del proveedor | Trazabilidad por paso y familia de navegador |
| 12 Cambios | Comité martes 11:00, ventana después de 21:00, cierre con evidencias | Checklist de despliegue |
| 13 Incidentes | INC-001 Safari, INC-002 timeout, INC-003 código vencido, INC-004 variable faltante, INC-005 proveedor degradado | Riesgos y pruebas negativas |

Los documentos de estado actual (02, 04, 06) son anteriores a los lineamientos objetivo (03, 05); se sigue lo vigente y lo objetivo se marca como recomendación.

## 4. Componentes involucrados

| Componente | Rol | Tipo |
|---|---|---|
| Portal, módulo Mi Perfil / Actualización de Datos | Entrada con sesión autenticada | Hecho (doc 02) |
| Componente web `email-update` (nuevo) | Captura correo y código; muestra el resultado | Recomendación |
| Gateway / Identity Orchestration Service | Entrega la identidad del afiliado al backend | Supuesto S1 |
| Microservicio `contact-update-service` (nuevo) | Orquesta validación, actualización, notificación y trazas | Recomendación |
| Identity Capability API, Customer Data API, Messaging API | Código de verificación, actualización gobernada, notificación | Hecho (doc 06) |

**Recomendación.** Un microservicio orquestador nuevo: la HU combina tres capacidades con reglas propias, el roadmap pide capacidades desacopladas y así no se toca un servicio core con regresión costosa.

## 5. Validaciones

| Validación | Dónde | Regla |
|---|---|---|
| Identidad del afiliado | Backend | Id desde el contexto autenticado, nunca del cuerpo (evita IDOR) |
| Formato del correo | Frontend y backend | Sintaxis válida, máximo 254 caracteres, normalizado a minúsculas |
| Correo distinto del actual | Backend | Rechazo con mensaje claro |
| Estado del afiliado | Backend | Solo afiliados activos |
| Canal de validación | Backend | Debe existir celular; si no, remisión a canal asistido |
| Código | Backend | 6 dígitos; vigencia, intentos y espera entre reenvíos configurables |
| Transacción | Backend | Propia, vigente y pendiente; reconfirmar no duplica |
| Entradas HTTP | Backend | Bean Validation; JSON malformado responde 400 |

## 6. Supuestos

| ID | Supuesto | Validar con |
|---|---|---|
| S1 | El gateway entrega el id del afiliado autenticado en una cabecera de confianza | LT / Arquitectura |
| S2 | El canal de validación es el celular registrado; facial fuera de alcance | PO |
| S3 | Identity Capability API genera, entrega y valida el código; el servicio nunca lo conoce | LT / Integración |
| S4 | Customer Data API acepta la actualización con la referencia de la validación previa | Integración |
| S5 | Los contratos JSON de las tres APIs son los de E2 sección 6 | Integración |
| S6 | Messaging no garantiza entrega; la notificación es de mejor esfuerzo y se traza | PO / Integración |
| S7 | Código vigente 5 minutos, 3 intentos, reenvío cada 60 segundos | PO |

## 7. Dudas para PO / LT

| ID | Pregunta | Supuesto mientras tanto |
|---|---|---|
| P-01 | Sin celular registrado, a dónde se remite al afiliado (oficina, Contact Center, facial) | Mensaje con remisión a canal asistido |
| P-02 | Se envía aviso de seguridad al correo anterior | Sí, de mejor esfuerzo |
| P-03 | Se exige verificar la propiedad del correo nuevo con un código | No se implementa; diseñado como extensión |
| P-04 | Vigencia, intentos y espera entre reenvíos del código | S7 |
| P-05 | Identity Capability API entrega el SMS o solo genera el código | S3 |
| P-06 | Cómo recibe el servicio la identidad del afiliado (JWT, cabecera) | S1 |
| P-07 | Contrato y referencia de validación que exige Customer Data al actualizar | S4 |
| P-08 | La actualización es idempotente o requiere clave de idempotencia | No se reintenta; el afiliado reinicia |
| P-09 | Existe un flujo OTP productivo reutilizable (doc 04) | Se consume vía API |
| P-10 | Dónde persiste la trazabilidad | En memoria; DDL propuesto en E2 |

## 8. Criterios de aceptación refinados

| ID | Dado / cuando / entonces |
|---|---|
| CA-01 | Afiliado activo con celular envía correo válido y distinto: recibe transacción pendiente con destino enmascarado y vigencia |
| CA-02 | Correo inválido o igual al actual: rechazo en frontend y 400 en backend con campo y motivo |
| CA-03 | Sin celular registrado: mensaje con canal asistido y sin código generado |
| CA-04 | Afiliado inactivo: rechazo explícito sin código |
| CA-05 | Código correcto: correo actualizado, transacción completada, notificación y resultado con fecha |
| CA-06 | Código incorrecto: rechazo con intentos restantes; transacción sigue pendiente |
| CA-07 | Intentos agotados: transacción bloqueada; debe iniciar de nuevo |
| CA-08 | Transacción vencida: rechazo por expiración; puede iniciar de nuevo |
| CA-09 | Transacción completada confirmada otra vez: no se repite la actualización; se informa el resultado |
| CA-10 | Reenvío antes de la espera: rechazo con tiempo restante; después: código nuevo con vigencia renovada |
| CA-11 | Proveedor lento o caído: error controlado con indicación de reintento; sin estado inconsistente |
| CA-12 | Actualización hecha y notificación fallida: resultado exitoso con aviso y fallo trazado |
| CA-13 | Toda petición: correlation ID aceptado o generado, en respuesta y logs |
| CA-14 | Logs y trazas: correo y celular enmascarados; el código nunca aparece |
| CA-15 | Id de afiliado distinto del dueño: 404 |
| CA-16 | Chrome, Edge y Safari: confirmación visible sin errores de JavaScript |

## 9. Riesgos técnicos

| Riesgo | Fuente | Mitigación |
|---|---|---|
| JavaScript no soportado en Safari | INC-001, doc 03 | APIs vetadas, detección de capacidades, prueba en WebKit |
| Timeout con proveedor externo | INC-002, INC-005 | Timeouts configurables, reintento solo en lecturas, 503/504 con `Retry-After` |
| Código vencido antes de terminar | INC-003 | Vigencia configurable, cuenta regresiva, reenvío |
| Variable de ambiente faltante | INC-004 | Configuración validada al arranque; tabla en README |
| Dependencias legadas del portal | doc 02, doc 03 | El componente no usa librerías del portal |
| Revisión de ciberseguridad tardía | doc 08 | Escaneo en el checklist del PR |
