# EvolutionFit

Sistema web para administrar un gimnasio. Documento técnico y funcional: docs/ (léelo antes de implementar un módulo).

**Reglas de negocio: [docs/REGLAS_NEGOCIO.md](docs/REGLAS_NEGOCIO.md) es fuente obligatoria.** Léelo antes de implementar cualquier módulo. Prevalece sobre el documento técnico y sobre cualquier suposición.

## Stack
- Backend: Java 21, Spring Boot 3.3.5, Spring Security + JWT, JPA/Hibernate, Flyway, Swagger. Carpeta backend/
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

## Estado
- Hecho:
  - Fases 1 y 2: estructura, login JWT, usuarios y roles.
  - Fase 3: clientes y planes.
  - Fase 4: membresías y congelaciones (backend, frontend y migración V3). Probada en el navegador.
  - Fase 5: pagos, asistente de inscripción y comprobantes PDF (backend, frontend y migración V4). Comprobante en A5 para celular, enviado a mano por WhatsApp con el nombre comercial en el mensaje. Probada en el navegador.
  - Fase 6: caja (backend, frontend y migración V5). Cierre automático a las 10 p.m. de Bogotá que se pone al día al arrancar, retiros, arqueo ajustado por los movimientos posteriores al corte, revisión del ADMIN y reporte mensual. Probada de punta a punta por API contra una copia del respaldo; falta probarla en el navegador.
  - Fase 7: panel de inicio, alertas de vencimiento (Vencimientos, con "Recordar por WhatsApp" manual) y reportes del ADMIN con CSV (backend, frontend y migración V7, solo índices). Reglas en docs/REGLAS_NEGOCIO.md, sección 11. Probada de punta a punta por API contra una copia del respaldo; falta probarla en el navegador.
- Pendiente:
  - Cargar los datos reales del gimnasio desde "Datos del comprobante" (nunca en el código).
  - Puesta en marcha: definir un procedimiento seguro para borrar los datos de prueba y reiniciar el consecutivo de comprobantes a 1 (docs/REGLAS_NEGOCIO.md, sección 10); no está implementado. Luego, el ADMIN fija la fecha de inicio de la caja.
  - Confirmar con el dueño qué se hace con el efectivo sobrante después del arqueo (hoy rige el valor por defecto).
- Ajustes posteriores a la fase 6: celular colombiano obligatorio en clientes, días de atención (lunes a sábado, festivos de Colombia calculados en ColombianHolidays, excepciones del ADMIN; migración V6).
- Siguiente: fase 8, control de acceso (incluye el hardware, docs/HARDWARE_CONTROL_ACCESO.md). Ahí se agregan los reportes de asistencia y entradas.
- Caja: los ingresos se leen de payments (no hay movimientos INCOME); cash_movements guarda solo retiros. Un cierre solo marca filas sin marcar (cash_closing_id); nunca recalcular ni editar un cierre.
- Ajustes posteriores a la fase 7:
  - Corregido: los `<form>` sin `[formGroup]` (búsqueda del asistente, diálogo de motivo de todas las anulaciones y arqueo) recargaban la página en el navegador.
  - Aviso global de errores (UserErrorHandler).
  - El ADMIN elimina clientes sin historial (docs/REGLAS_NEGOCIO.md, sección 12).
- Frontend, regla: todo `<form>` lleva `[formGroup]` (solo se usa ReactiveFormsModule; sin él, `(ngSubmit)` nunca corre y el navegador recarga). Lo verifica `scripts/check-forms.mjs`, que corre antes de `npm test` y de `npm run build`.
- Frontend, pruebas de integración: `src/app/testing/` monta la app real (appProviders, rutas con el shell perezoso, MatDialog) y hace clic en el DOM. Cada acción nueva necesita una prueba ahí, no solo pruebas de componentes aislados con diálogos simulados.
- Panel y reportes: "cliente nuevo frente a renovación" está definido una sola vez (ReportRepository.FIRST_OF_CLIENT); las alertas reutilizan MembershipSpecifications. El reporte de ingresos usa la regla de Caja en días calendario.
- Pruebas con MySQL real: V3MigrationTest, V4MigrationTest, V5MigrationTest, V6MigrationTest, V7MigrationTest, MembershipConcurrencyTest, PaymentConcurrencyTest, CashClosingMySqlTest, CashConcurrencyTest, ReportQueriesMySqlTest y ClientDeletionMySqlTest usan Testcontainers. Con Docker apagado se omiten (aparecen como Skipped), así que hay que revisarlo antes de dar una fase por terminada.
