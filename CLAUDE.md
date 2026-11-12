# EvolutionFit

Sistema web para administrar un gimnasio. Documento técnico y funcional: docs/ (léelo antes de implementar un módulo).

**Reglas de negocio: [docs/REGLAS_NEGOCIO.md](docs/REGLAS_NEGOCIO.md) es fuente obligatoria.** Léelo antes de implementar cualquier módulo. Prevalece sobre el documento técnico y sobre cualquier suposición.

## Stack
- Backend: Java 21, Spring Boot 4.1 (Spring 7, Security 7, Hibernate 7, Jackson 3), Spring Security + JWT, JPA/Hibernate, Flyway, Swagger. Carpeta backend/
- Frontend: Angular, Angular Material, Reactive Forms. Carpeta frontend/
- Base de datos: MySQL 8.4 en Docker (docker compose up -d). Ya existe con datos.

## Convenciones
- Backend modular: com.evolutionfit.modules.<modulo> con controller, service, repository, entity, dto.
- Errores: lanzar BusinessException / ResourceNotFoundException / ConflictException; el formato lo da GlobalExceptionHandler.
- Frontend: features/<modulo>, componentes standalone, servicios en cada feature, textos en español.

## Reglas obligatorias
- La seguridad se valida SIEMPRE en el backend (@PreAuthorize); los guards de Angular son solo comodidad.
- Roles: ADMIN y RECEPTIONIST.
- Nunca escribir contraseñas ni claves en el código ni en archivos versionados: todo va en .env (ignorado por git).
- Base de datos: Hibernate NO crea tablas (ddl-auto=none). Todo cambio de esquema es una migración nueva de Flyway (V2, V3...). Nunca editar V1 ni borrar datos.
- No asumir reglas de negocio: las definidas están en docs/REGLAS_NEGOCIO.md; lo que no esté ahí, preguntar o dejarlo configurable y documentado.
- El control de acceso físico (docs/HARDWARE_CONTROL_ACCESO.md) es la fase 8: no analizarlo ni programarlo antes.
- Mantener el precio histórico en cada membresía (price_at_purchase).

## Forma de trabajo
- Una fase a la vez, en este orden: clientes y planes, membresías y congelaciones, pagos y comprobantes PDF, caja, fase 7 panel de inicio/alertas de vencimiento/reportes, fase 8 control de acceso (incluye el hardware).
- Antes de programar una fase: mostrar el plan y esperar mi aprobación.
- Cada fase termina con pruebas unitarias de las reglas críticas. No dar una fase por terminada hasta que pasen ./mvnw test (en backend/) y npm test -- --watch=false, y compile ng build (en frontend/).
- Hacer commit al terminar cada fase. NUNCA hacer push sin que yo lo pida.
- Pruebas de punta a punta (Playwright, carpeta e2e/): ejecutarlas antes de cerrar una fase y después de cualquier cambio en pantallas o formularios.
  - Instalación, una sola vez: `cd e2e && npm install && npx playwright install chromium`.
  - Ejecución, con Docker encendido: `cd e2e && npm run e2e`. Para ver el navegador: `npm run e2e -- --headed`.
  - Reporte: `e2e/playwright-report/flujos` y `e2e/playwright-report/caja`.
  - Usan la interfaz real (navegador, backend y base reales); nada está simulado.
  - `scripts/run-e2e.mjs`:
    - Levanta una MySQL de pruebas en localhost:3308, con datos en memoria y contraseñas aleatorias que no se guardan.
    - Compila y arranca el backend en el 8082 con el perfil `e2e`.
    - Sirve el frontend en el 4300.
    - Carga los datos iniciales por la API.
    - Corre los flujos y después la caja (que adelanta el reloj).
    - Al terminar, siempre borra todo.
  - Con `E2E_SIMULATE_NEXT_DAY=1` el reloj empieza a las 8:00 a.m. del día siguiente (lo mismo que pasa si se ejecuta después de las 8:00 p.m.).
  - **El perfil `e2e` solo funciona con la base `evolution_fit_e2e` en localhost:3308.**
    - E2eGuard impide que el backend arranque si el perfil está activo y no se cumple todo: `APP_E2E_ENABLED=true`, `e2e` como único perfil y esa base. Rechaza la de desarrollo (3306) y la 3307.
    - Nunca activarlo contra otra base ni en producción.
    - El backend de pruebas corre desde e2e/.run/ con un entorno armado desde cero, así que nunca lee el `.env` real.
    - No crear e2e/.env: si existe, el orquestador se detiene.
  - El reloj de pruebas (E2eClock) solo avanza.
    - Caja, arqueo, cierre automático y vencimientos usan ese reloj.
    - Algunos contadores de reportes y del panel usan `created_at`, que es la hora real; no se verifican con el reloj adelantado.

## Estado
- Hecho:
  - Fases 1 y 2: estructura, login JWT, usuarios y roles.
  - Fase 3: clientes y planes.
  - Fase 4: membresías y congelaciones (backend, frontend y migración V3). Probada en el navegador.
  - Fase 5: pagos, asistente de inscripción y comprobantes PDF (backend, frontend y migración V4). Comprobante en A5 para celular, enviado a mano por WhatsApp con el nombre comercial en el mensaje. Probada en el navegador.
  - Fase 6: caja (backend, frontend y migración V5). Cierre automático a las 10 p.m. de Bogotá que se pone al día al arrancar, retiros, arqueo ajustado por los movimientos posteriores al corte, revisión del ADMIN y reporte mensual. Probada de punta a punta por API contra una copia del respaldo; falta probarla en el navegador.
  - Fase 7: panel de inicio, alertas de vencimiento (Vencimientos, con "Recordar por WhatsApp" manual) y reportes del ADMIN con CSV (backend, frontend y migración V7, solo índices). Reglas en docs/REGLAS_NEGOCIO.md, sección 11. Probada de punta a punta por API contra una copia del respaldo; falta probarla en el navegador.
- Pendiente:
  - Salida a producción en un VPS con Docker, un único dominio con HTTPS (decisiones del dueño en docs/REGLAS_NEGOCIO.md, sección 13). Seguir docs/PUESTA_EN_MARCHA.md. Lo técnico está hecho (Spring Boot 4.1, seguridad, archivos de despliegue); falta lo del dueño: proveedor del VPS, dominio y DNS, correo de Let's Encrypt, cuenta de B2 (o Drive) y la llave de age. Producción usa una base nueva: no se borran datos de prueba y el consecutivo empieza en 1 por la V4.
  - Respaldos: scripts/backup.sh, scripts/restore.sh y scripts/offsite.sh (age y rclone en contenedores), procedimiento en docs/RESPALDOS.md. Antes de cada migración, `scripts/backup.sh --label pre_V<n>` y verificarlo con `scripts/restore.sh` (deploy.sh lo hace solo en producción).
  - Cargar los datos reales del gimnasio desde "Datos del comprobante" (nunca en el código) y los 80 a 100 clientes del sistema anterior a mano, como membresías migradas (docs/PUESTA_EN_MARCHA.md, sección 10). Luego, el ADMIN fija la fecha de inicio de la caja.
- Ajustes posteriores a la fase 6: celular colombiano obligatorio en clientes, días de atención (lunes a sábado, festivos de Colombia calculados en ColombianHolidays, excepciones del ADMIN; migración V6).
- Sesiones (V8): cambiar o restablecer una contraseña llena users.password_changed_at y JwtAuthenticationFilter rechaza los tokens emitidos antes (hora real, no E2eClock, porque se compara con la emisión del JWT).
- Despliegue (docs/PUESTA_EN_MARCHA.md, secciones 6, 8, 13 y 14):
  - `docker-compose.prod.yml` (MySQL y backend sin puertos y en una red interna sin internet; solo Caddy publica 80/443), `backend/Dockerfile`, `deploy/caddy/` (Caddyfile y la imagen con el frontend), `deploy/env.prod.example`.
  - Variables en /opt/evolutionfit/.env.prod (fuera del repositorio, 600), validadas con `scripts/check-env-prod.sh`.
  - `scripts/deploy.sh <etiqueta>` (respaldo verificado, imágenes etiquetadas, prod-health.sh) y `scripts/rollback.sh <etiqueta> [--restore <respaldo>]`.
  - Ensayo completo en el Mac: `scripts/prod-smoke.sh` (https://evolutionfit.localhost:8443; incluye Playwright con `e2e/playwright.prod.config.ts`, que solo corre contra esa URL). Lo borra todo al terminar.
  - IP del cliente: Caddy reescribe X-Forwarded-For; en prod el backend solo lo acepta de la red 172.28.10.0/24 (`server.tomcat.remoteip.internal-proxies`). Si se cambia APP_SUBNET, cambiar TRUSTED_PROXIES_REGEX.
  - `angular.json` sin CSS crítico en línea: su `<script>` en línea chocaría con la CSP (`script-src 'self'`).
- Siguiente: fase 8, control de acceso (incluye el hardware, docs/HARDWARE_CONTROL_ACCESO.md). Ahí se agregan los reportes de asistencia y entradas.
- Caja: los ingresos se leen de payments (no hay movimientos INCOME); cash_movements guarda solo retiros. Un cierre solo marca filas sin marcar (cash_closing_id); nunca recalcular ni editar un cierre.
- Ajustes posteriores a la fase 7:
  - Corregido: los `<form>` sin `[formGroup]` (búsqueda del asistente, diálogo de motivo de todas las anulaciones y arqueo) recargaban la página en el navegador.
  - Aviso global de errores (UserErrorHandler).
  - El ADMIN elimina clientes sin historial (docs/REGLAS_NEGOCIO.md, sección 12).
- Frontend, regla: todo `<form>` lleva `[formGroup]` (solo se usa ReactiveFormsModule; sin él, `(ngSubmit)` nunca corre y el navegador recarga). Lo verifica `scripts/check-forms.mjs`, que corre antes de `npm test` y de `npm run build`.
- Frontend, pruebas de integración: `src/app/testing/` monta la app real (appProviders, rutas con el shell perezoso, MatDialog) y hace clic en el DOM. Cada acción nueva necesita una prueba ahí, no solo pruebas de componentes aislados con diálogos simulados.
- Panel y reportes: "cliente nuevo frente a renovación" está definido una sola vez (ReportRepository.FIRST_OF_CLIENT); las alertas reutilizan MembershipSpecifications. El reporte de ingresos usa la regla de Caja en días calendario.
- Pruebas con MySQL real: V3MigrationTest, V4MigrationTest, V5MigrationTest, V6MigrationTest, V7MigrationTest, V8MigrationTest, MembershipConcurrencyTest, PaymentConcurrencyTest, CashClosingMySqlTest, CashConcurrencyTest, ReportQueriesMySqlTest, ClientDeletionMySqlTest, SensitiveDataMySqlTest, LoginMySqlTest, TrustedProxyClientIpMySqlTest y UntrustedProxyClientIpMySqlTest usan Testcontainers. Con Docker apagado se omiten (aparecen como Skipped), así que hay que revisarlo antes de dar una fase por terminada.
