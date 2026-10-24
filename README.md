# HU: Actualización de correo electrónico del afiliado

Solución de la prueba técnica "Digital Engineering Challenge - Desarrollador" (stack anonimizado).

> Como afiliado quiero actualizar mi correo electrónico para recibir comunicaciones en mi nueva dirección.
> Criterios: validar identidad, validar correo, confirmar actualización, notificar y registrar trazabilidad.

Dos piezas: el microservicio `contact-update-service` (Java 21, Spring Boot 4.1), que orquesta la validación de identidad por código SMS, la actualización por la interfaz gobernada, la notificación y la trazabilidad; y el componente web nativo `<email-update>`, que el portal carga sin dependencias.

## Entregables

| Entregable | Documento |
|---|---|
| E1 Análisis de HU | [`docs/01-analisis-hu.md`](docs/01-analisis-hu.md) |
| E2 Diseño técnico | [`docs/02-diseno-tecnico.md`](docs/02-diseno-tecnico.md) |
| E3 Implementación | [`docs/03-implementacion.md`](docs/03-implementacion.md) y los PR |
| E4 Pseudocódigo | [`docs/04-pseudocodigo.md`](docs/04-pseudocodigo.md) |
| E5 Pruebas | [`docs/05-pruebas.md`](docs/05-pruebas.md) |
| E6 Debug Safari | [`docs/06-debug-safari.md`](docs/06-debug-safari.md), capturas en [`docs/evidencias/`](docs/evidencias/) |
| E7 Evidencia IA | [`docs/07-evidencia-ia.md`](docs/07-evidencia-ia.md) |

Convención: cada afirmación se etiqueta como **Hecho (doc NN)** del Mini-RAG, **Recomendación** o **Supuesto**.

## Requisitos

JDK 21 o superior, Node.js 20 o superior y acceso a Maven Central la primera vez (el Maven Wrapper descarga Maven y las dependencias). Alternativa sin instalar nada: Docker (ver "Ejecución con Docker").

## Ejecución

```bash
git clone https://github.com/NicolasV7/hu-actualizacion-correo.git
cd hu-actualizacion-correo/backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local      # Windows: mvnw.cmd ...
```

El perfil `local` usa stubs con datos sintéticos, logs legibles y sirve la demo en <http://localhost:8080>: afiliado `AFF-001`, correo nuevo, código `123456`. Sin perfil, el servicio arranca en modo `http` y exige las URL de las tres APIs; si falta una, falla al iniciar nombrando la variable (INC-004). Peticiones de ejemplo: [`backend/requests.http`](backend/requests.http).

| Dato sintético | Comportamiento |
|---|---|
| `AFF-001` | Activo, correo `ana.perez@example.com`, celular `***4567` |
| `AFF-002` | Sin celular: `IDENTITY_CHANNEL_UNAVAILABLE` (422) |
| `AFF-003` | Inactivo: `AFFILIATE_NOT_ELIGIBLE` (422) |
| `AFF-004` | Sin correo previo |
| `AFF-IDP-TIMEOUT` / `AFF-IDP-DOWN` | Proveedor de identidad lento (504) / caído (503) |
| `AFF-CDS-DOWN` | La actualización falla tras validar el código: 503, transacción `FAILED` |
| Correo `@down.test` | Notificación fallida: 200 con `notificationSent=false` |
| Código `123456` | Único código válido (`STUB_VERIFICATION_CODE`) |

## Ejecución con Docker

Para probar en cualquier máquina (Linux, macOS o Windows) sin JDK ni Node:

```bash
docker compose up --build                               # construye, ejecuta las 58 pruebas del backend y sirve la demo en http://localhost:8080
docker compose --profile test run --rm frontend-tests   # 25 pruebas unitarias del frontend
docker compose --profile test run --rm e2e              # flujo completo en WebKit (motor de Safari) y Chromium contra el contenedor
```

La imagen usa el perfil `local` (stubs y datos sintéticos). Para el modo `http`, pasar las variables de la sección "Configuración" con `-e` o en `compose.yaml`.

## Pruebas

```bash
cd backend && ./mvnw test        # 58 pruebas
cd frontend && npm test          # 25 pruebas, sin dependencias
cd frontend && npm install && npx playwright install webkit chromium && npm run test:e2e   # con el backend en perfil local
```

Matriz, cobertura de criterios y evidencias en `docs/05-pruebas.md`.

## Configuración

Valores por defecto en `backend/src/main/resources/application.yml`; plantilla en [`.env.example`](.env.example).

| Variable | Por defecto | Descripción |
|---|---|---|
| `PORT` | `8080` | Puerto HTTP |
| `SPRING_PROFILES_ACTIVE` | vacío | `local`: stubs, logs legibles y demo |
| `INTEGRATION_MODE` | `http` | `http` o `stub` (`local` y `test` fijan `stub`) |
| `CUSTOMER_DATA_BASE_URL`, `IDENTITY_BASE_URL`, `MESSAGING_BASE_URL` | vacío | Obligatorias en modo `http` |
| `CUSTOMER_DATA_API_KEY`, `IDENTITY_API_KEY`, `MESSAGING_API_KEY` | vacío | Credenciales |
| `INTEGRATION_CONNECT_TIMEOUT` / `INTEGRATION_READ_TIMEOUT` | `PT2S` / `PT5S` | Timeouts hacia las APIs |
| `VERIFICATION_CODE_TTL` / `VERIFICATION_MAX_ATTEMPTS` / `VERIFICATION_RESEND_COOLDOWN` | `PT5M` / `3` / `PT60S` | Política del código |
| `STUB_VERIFICATION_CODE` | `123456` | Código del stub (solo `local` y `test`) |
| `LOG_FORMAT` | `ecs` | Formato de logs estructurados |
| `FRONTEND_DIR` | `../frontend/src` | Carpeta servida en `local` |

Sin secretos en el repositorio.

## API

Base `/api/v1/contact/email-updates`. Cabeceras: `X-Affiliate-Id` (obligatoria; la inyecta el gateway) y `X-Correlation-Id` (opcional; se genera si falta y siempre se devuelve).

| Ruta | Descripción | Respuesta |
|---|---|---|
| `POST /` | Valida al afiliado y el correo; envía el código al celular registrado | 201 con `transactionId`, `maskedDestination`, `expiresInSeconds` |
| `POST /{id}/confirm` | Valida el código, actualiza, notifica y traza | 200 con `status`, `maskedEmail`, `updatedAt`, `notificationSent` |
| `POST /{id}/resend` | Reenvía el código respetando la espera mínima | 200 como el inicio |

Errores en RFC 9457 (`application/problem+json`) con `code` y `correlationId`; catálogo en `docs/02-diseno-tecnico.md`, sección 5.

## Estructura y decisiones

```
backend/   api, application, domain, integration (stub, http), observability, config
frontend/  src/, test/, e2e/
docs/      E1 a E7 y evidencias
```

- Microservicio orquestador nuevo, sin tocar un servicio core (D-01).
- Solo starters de Spring Boot: records, `ProblemDetail`, logging estructurado nativo y `RetryTemplate` (D-03).
- Puertos con adaptadores stub y HTTP (timeouts, reintento solo en lecturas, correlación) (D-05).
- Identidad por cabecera del gateway; evolución a JWT documentada (D-06).
- Transacciones y trazas en memoria con DDL propuesto (D-07).
- Componente nativo compatible con Safari 16.4+, Chrome y Edge, con APIs vetadas y detección de capacidades.

## Historial y PR

| PR | Rama | Contenido |
|---|---|---|
| [#1](https://github.com/NicolasV7/hu-actualizacion-correo/pull/1) | `feature/HU-001-actualizar-correo` | Microservicio, componente web, E1 a E4, README |
| [#2](https://github.com/NicolasV7/hu-actualizacion-correo/pull/2) | `fix/INC-safari-confirmacion` | Fix del incidente Safari con regresión, E5 y E6 |
| [#3](https://github.com/NicolasV7/hu-actualizacion-correo/pull/3) | `docs/cierre-entregables` | E7 y cierre del README |

## Uso de IA

Se usó Claude Code para acelerar análisis, diseño, código, pruebas y documentación, con validación por ejecución de las suites y revisión contra el Mini-RAG. Registro en `docs/07-evidencia-ia.md`.
