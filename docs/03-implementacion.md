# E3. Implementación: tareas, estructura, ramas, configuración local y PR

## 1. Tareas técnicas

| ID | Tarea | Entrega | Validación |
|---|---|---|---|
| T-01 | Análisis de la HU y del Mini-RAG | `docs/01` | Matriz de requisitos cruzada |
| T-02 | Diseño técnico | `docs/02` | Plantilla de la sección 8 completa |
| T-03 | Proyecto Spring Boot 4.1 con Maven Wrapper | `backend/pom.xml`, `mvnw` | `./mvnw test` |
| T-04 | Dominio, puertos y stubs con datos sintéticos | `domain`, `integration`, `integration/stub` | Pruebas de API con perfil `test` |
| T-05 | Servicio de orquestación y políticas del código | `application` | `EmailUpdateServiceTest` (17) |
| T-06 | API REST, validación y manejador `ProblemDetail` | `api` | `EmailUpdateApiTest` (17) |
| T-07 | Correlation ID, familia de navegador, trazas | `observability` | `CorrelationIdFilterTest` y aserciones de traza |
| T-08 | Configuración tipada y perfiles | `application*.yml`, `AppProperties` | Arranque sin URLs falla nombrando la variable |
| T-09 | Adaptadores HTTP con timeouts, reintento y correlación | `integration/http` | `HttpClientsTest` (9) |
| T-10 | Componente web, estado, cliente HTTP, validación | `frontend/src` | `npm test` (25) y recorrido manual |
| T-11 | Incidente Safari: reproducción, fix, regresión, evidencia | `compat.js`, `e2e`, `docs/06` | e2e en WebKit antes y después |
| T-12 | Pseudocódigo, pruebas, evidencia de IA, README | `docs/04`, `docs/05`, `docs/07`, `README.md` | Clon limpio desde el README |

## 2. Estructura

```
hu-actualizacion-correo/
|-- README.md, .env.example, .gitignore, .gitattributes
|-- docs/                      entregables E1 a E7 y evidencias
|-- backend/                   contact-update-service (Spring Boot 4.1, Java 21)
|   |-- pom.xml, mvnw, mvnw.cmd, .mvn/, requests.http
|   `-- src/main/java/com/canalesdigitales/contactupdate/
|       |-- api/               controlador, DTOs, ApiExceptionHandler
|       |-- application/       EmailUpdateService, TransactionStore, respuestas
|       |-- domain/            UpdateTransaction, ErrorCode, ContactUpdateException, Masking
|       |-- integration/       puertos; stub/ y http/
|       |-- observability/     CorrelationIdFilter, TraceLog
|       `-- config/            AppProperties, ClockConfig, HttpIntegrationConfig
`-- frontend/                  componente web nativo, sin build
    |-- package.json           test (node:test) y test:e2e (Playwright)
    |-- src/                   email-update.js, state.js, api-client.js, validation.js, dates.js, compat.js, index.html
    |-- test/                  unitarias
    `-- e2e/                   compatibilidad en WebKit y Chromium
```

Dependencias hacia adentro: `api` usa `application`, que usa `domain` y los puertos; `integration` los implementa. Sin librerías fuera de los starters (D-03).

## 3. Ramas

| Rama | Propósito |
|---|---|
| `main` | Integración; solo recibe merges de PR |
| `feature/HU-001-actualizar-correo` | Backend, frontend, E1 a E4, README |
| `fix/INC-safari-confirmacion` | Fix del incidente Safari con regresión, E5 y E6 |
| `docs/cierre-entregables` | E7 y cierre del README |

Conventional Commits con asunto en español; un PR por rama; merge con commit de merge; `main` nunca se reescribe.

## 4. Configuración local

Requisitos: JDK 21+, Node.js 20+, acceso a Maven Central la primera vez; o Docker para no instalar nada.

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # demo en http://localhost:8080
cd backend && ./mvnw test
cd frontend && npm test
cd frontend && npm install && npx playwright install webkit chromium && npm run test:e2e

docker compose up --build                               # alternativa portable: demo en http://localhost:8080
docker compose --profile test run --rm frontend-tests   # pruebas del frontend en contenedor
docker compose --profile test run --rm e2e              # WebKit y Chromium en contenedor
```

`Dockerfile` (multietapa: compila y prueba con JDK 21, ejecuta con JRE 21 y sirve el frontend) y `compose.yaml` evitan diferencias entre máquinas del equipo o del evaluador. Variables en el README y en `.env.example`. Sin perfil `local`, el servicio exige las URL de las tres APIs y falla al arrancar nombrando la que falta (INC-004). Peticiones de ejemplo en `backend/requests.http`.

## 5. Pull request

Plantilla usada en los tres PR del repositorio:

```markdown
## Qué cambia
## HU / incidente
## Cómo probar
## Checklist
- [ ] Suites en verde (./mvnw test, npm test)
- [ ] Pruebas negativas y de indisponibilidad de externos
- [ ] Compatibilidad Chrome, Edge y Safari (WebKit) con evidencia
- [ ] Variables nuevas documentadas en README y .env.example (INC-004)
- [ ] Sin secretos ni datos reales; datos enmascarados en logs
- [ ] Correlation ID en logs, trazas y respuestas
- [ ] Evaluación y escaneo de ciberseguridad solicitados con 3 días hábiles (doc 08)
- [ ] Registro de uso de IA actualizado (doc 15)
## Despliegue (doc 12)
Comité martes 11:00; ventana después de 21:00; notificación en los canales oficiales; cierre con evidencias en Mesa de Servicio; rollback a la versión anterior (sin migraciones).
```

Los PR reales están enlazados en el README.

## 6. Definición de terminado

- CA-01 a CA-16 cubiertos por pruebas automatizadas o por el recorrido documentado en `docs/05`.
- Suites en verde desde un clon limpio siguiendo el README.
- Sin secretos, datos reales ni archivos temporales.
- Documentos E1 a E7 consistentes con el código.
