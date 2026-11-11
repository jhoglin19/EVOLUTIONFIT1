# Evolution Fit

Sistema web para administrar un gimnasio: clientes, planes, membresías y congelaciones, pagos con comprobante PDF,
caja diaria con arqueo, panel de inicio, alertas de vencimiento y reportes. El control de acceso físico es la
siguiente fase.

Arquitectura de tres capas (Angular → API REST Spring Boot → MySQL), como monolito modular.

- Reglas de negocio (fuente obligatoria): [docs/REGLAS_NEGOCIO.md](docs/REGLAS_NEGOCIO.md)
- Instrucciones de trabajo del proyecto: [CLAUDE.md](CLAUDE.md)

## Tecnologías

| Capa | Tecnología |
| --- | --- |
| Backend | Java 21, Spring Boot 3.3.5, Spring Security + JWT, Spring Data JPA/Hibernate, Flyway, Swagger/OpenAPI, OpenPDF |
| Frontend | Angular 22, Angular Material, Reactive Forms, RxJS |
| Base de datos | MySQL 8.4 (Docker) |
| Pruebas | JUnit 5, Testcontainers, Vitest (Angular), Playwright |

## Estado

| Fase | Módulo | Estado |
| --- | --- | --- |
| 1 | Estructura, Docker, Flyway, Swagger, manejo global de errores | Hecho |
| 2 | Autenticación JWT, usuarios y roles | Hecho |
| 3 | Clientes y planes | Hecho |
| 4 | Membresías y congelaciones | Hecho |
| 5 | Pagos, asistente de inscripción y comprobantes PDF | Hecho |
| 6 | Caja (cierre automático, retiros, arqueo, revisión) | Hecho |
| 7 | Panel de inicio, alertas de vencimiento y reportes | Hecho |
| 8 | Control de acceso (incluye el hardware) | Pendiente |

Salida a producción: [docs/PUESTA_EN_MARCHA.md](docs/PUESTA_EN_MARCHA.md) (checklist, con las decisiones
pendientes marcadas) y la sección "Pendientes de definir" de [docs/REGLAS_NEGOCIO.md](docs/REGLAS_NEGOCIO.md).

## Requisitos

- Java 21 (el proyecto trae el wrapper de Maven, `mvnw`; no hace falta instalar Maven).
- Node 22 o superior y npm.
- Docker Desktop (para la base y para las pruebas con MySQL real).

## Instalación (una sola vez)

```bash
cp .env.example .env              # y completa los valores (ver "Variables de entorno")
cd frontend && npm install        # dependencias del frontend
cd ../e2e && npm install && npx playwright install chromium   # solo si vas a correr las pruebas de punta a punta
```

`.env` está en `.gitignore`: **nunca lo subas a Git** ni copies sus valores en otros archivos.

## Cómo levantarlo

1. **Base de datos** (MySQL en Docker, puerto 3306):

   ```bash
   docker compose up -d
   ```

   Es el mismo contenedor (`evolutionfit-mysql`) y volumen de siempre. Al arrancar, el backend aplica con Flyway
   las migraciones pendientes (V1, V2, …). Hibernate nunca crea ni modifica tablas.

2. **Backend** (desde `backend/`), API en http://localhost:8080:

   ```bash
   ./mvnw spring-boot:run
   ```

   Lee `.env` solo (desde `backend/` o desde la raíz); no hace falta exportar variables.

3. **Frontend** (desde `frontend/`, en otra terminal), en http://localhost:4200:

   ```bash
   npm start
   ```

   Redirige `/api` al backend del puerto 8080 (`proxy.conf.json`).

Opcional: backend también en contenedor con `docker compose --profile full up --build`.

Documentación de la API: http://localhost:8080/swagger-ui.html · Salud: http://localhost:8080/actuator/health

## Pruebas

Antes de dar por terminado un cambio deben pasar las tres capas.

### Backend: unitarias y con MySQL real (desde `backend/`)

```bash
./mvnw test
```

- Las pruebas unitarias no necesitan base de datos.
- Las de migraciones, concurrencia, caja, reportes y borrado de clientes usan **Testcontainers** (una MySQL
  temporal que se crea y se borra sola). **Necesitan Docker encendido**: si está apagado se omiten y aparecen como
  *Skipped*, así que revisa que no haya omitidas.

### Frontend: componentes e integración (desde `frontend/`)

```bash
npm test -- --watch=false
npm run build
```

- `src/app/testing/` monta la aplicación real (proveedores, rutas, diálogos) y hace clic en el DOM.
- Antes de `npm test` y de `npm run build` corre `scripts/check-forms.mjs`: todo `<form>` debe llevar
  `[formGroup]` (sin él, el navegador recarga la página al enviar).

### De punta a punta con Playwright (desde `e2e/`, con Docker encendido)

```bash
npm run e2e                 # sin navegador visible
npm run e2e -- --headed     # viendo el navegador
```

- Usan la interfaz real: navegador, frontend, backend y MySQL, nada simulado.
- Todo es temporal y aislado: una MySQL de pruebas en `localhost:3308` con datos en memoria, el backend en el
  8082 con el perfil `e2e` y el frontend en el 4300. Las contraseñas son aleatorias y no se guardan. Al terminar
  se borra todo.
- **El perfil `e2e` solo funciona con la base `evolution_fit_e2e` en `localhost:3308`**: si no, el backend no
  arranca. Nunca lo actives contra otra base ni en producción, y no crees `e2e/.env`.
- Reporte: `e2e/playwright-report/flujos` y `e2e/playwright-report/caja` (`npx playwright show-report <carpeta>`).
- Con `E2E_SIMULATE_NEXT_DAY=1` el reloj de pruebas empieza a las 8:00 a.m. del día siguiente.

## Variables de entorno (`.env`)

Plantilla: [.env.example](.env.example). Escribe los valores sin comillas. Ningún valor real va en el código ni
en archivos versionados.

| Variable | Obligatoria | Por defecto | Para qué sirve |
| --- | --- | --- | --- |
| `MYSQL_ROOT_PASSWORD` | Sí | — | Contraseña de root de MySQL (Docker Compose). Solo se aplica al crear el volumen. |
| `MYSQL_USER` | No | `evolution_app` | Usuario de la aplicación en MySQL. |
| `MYSQL_PASSWORD` | Sí | — | Contraseña de ese usuario. Debe coincidir con la de la base existente. |
| `DB_URL` | No | MySQL en `localhost:3306/evolution_fit` | URL JDBC, si la base está en otro lugar. |
| `DB_USERNAME` / `DB_PASSWORD` | No | `MYSQL_USER` / `MYSQL_PASSWORD` | Credenciales de la base, si son distintas. |
| `JWT_SECRET` | Sí | — | Clave para firmar las sesiones; mínimo 32 caracteres. Sin ella el backend no arranca. Generarla con `openssl rand -base64 48`. |
| `JWT_EXPIRATION_MINUTES` | No | `480` | Duración de la sesión. |
| `CORS_ALLOWED_ORIGINS` | No | `http://localhost:4200` | Orígenes del frontend permitidos (separados por coma). |
| `SERVER_PORT` | No | `8080` | Puerto del backend. |
| `BOOTSTRAP_ADMIN_USERNAME` | No | `admin` | Usuario del primer administrador. |
| `BOOTSTRAP_ADMIN_EMAIL` | No | vacío | Correo del primer administrador. |
| `BOOTSTRAP_ADMIN_PASSWORD` | Solo la primera vez | vacío | Contraseña del primer administrador (mínimo 8). Solo se usa si la tabla `users` está vacía; luego se puede borrar del `.env`. |
| `BOOTSTRAP_ADMIN_FIRST_NAME` / `_LAST_NAME` | No | `Administrador` / `Sistema` | Nombre del primer administrador. |
| `MEMBERSHIP_EXPIRY_WARNING_DAYS` | No | `5` | Días antes del vencimiento en que una membresía aparece "por vencer". |
| `MEMBERSHIP_MAX_FREEZE_DAYS` | No | `30` | Máximo de días de congelación por membresía. |
| `MEMBERSHIP_RECENTLY_EXPIRED_DAYS` | No | `15` | Días hacia atrás de la alerta "vencidas recientes". |
| `RECEIPT_PAPER_SIZE` | No | `A5` | Tamaño del comprobante PDF: `A5` o `LETTER`. |
| `CASH_CLOSING_TIME` | No | `22:00` | Hora del cierre automático diario de caja (hora de Bogotá). |
| `REPORTS_MAX_RANGE_DAYS` | No | `366` | Días máximos del rango de fechas en los reportes. |

`APP_E2E_ENABLED` existe solo para las pruebas de punta a punta: **no la definas** en `.env` ni en producción.

## Estructura

```
backend/                 API REST (Maven)
  src/main/java/com/evolutionfit/
    config/              configuración, reloj, administrador inicial
    security/            JWT, filtro, CORS, manejo de 401/403
    common/              errores (BusinessException, GlobalExceptionHandler), utilidades
    modules/<módulo>/    controller, service, repository, entity, dto
                         (auth, users, roles, clients, plans, memberships, payments, receipts,
                          cash, dashboard, reports, audit)
    e2e/                 reloj y protección del perfil e2e (solo pruebas de punta a punta)
  src/main/resources/
    application.yml
    db/migration/        migraciones Flyway (V1…); nunca editar una ya aplicada
frontend/                aplicación Angular
  src/app/core/          autenticación, interceptores, servicios globales, manejo de errores
  src/app/shared/        componentes, modelos y utilidades compartidas
  src/app/layout/        menú lateral y barra superior
  src/app/features/      una carpeta por módulo (componentes standalone y su servicio)
  src/app/testing/       pruebas de integración con la aplicación real
  scripts/check-forms.mjs
e2e/                     pruebas de punta a punta con Playwright y su orquestador
docs/                    reglas de negocio, documento técnico y control de acceso
docker-compose.yml       MySQL (y el backend opcional con --profile full)
.env.example             plantilla de variables (copiar a .env)
```

## Respaldos

```bash
scripts/backup.sh                       # respaldo comprimido en ~/evolutionfit-backups, con suma SHA-256
scripts/restore.sh <archivo>            # lo verifica en una MySQL temporal, sin tocar la base real
```

Procedimiento completo, restauración de emergencia, frecuencia y dónde guardar las copias:
[docs/RESPALDOS.md](docs/RESPALDOS.md).

## Seguridad

- Contraseñas con BCrypt; el login acepta usuario o correo.
- Sesión con JWT firmado (HS256). En cada petición se vuelve a leer el usuario: desactivarlo tiene efecto
  inmediato.
- Roles `ADMIN` y `RECEPTIONIST`, validados siempre en el backend con `@PreAuthorize`; los guards de Angular son
  solo comodidad de navegación.
- Secretos y credenciales vienen solo de variables de entorno (`.env`, ignorado por Git).
