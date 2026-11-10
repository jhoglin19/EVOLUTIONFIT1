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
- Una fase a la vez, en este orden: clientes y planes, membresías y congelaciones, pagos y comprobantes PDF, caja, control de acceso, notificaciones/dashboard/reportes.
- Antes de programar una fase: mostrar el plan y esperar mi aprobación.
- Cada fase termina con pruebas unitarias de las reglas críticas. No dar una fase por terminada hasta que pasen ./mvnw test (en backend/) y npm test -- --watch=false, y compile ng build (en frontend/).
- Hacer commit al terminar cada fase. NUNCA hacer push sin que yo lo pida.

## Estado
- Hecho:
  - Fases 1 y 2: estructura, login JWT, usuarios y roles.
  - Fase 3: clientes y planes.
  - Fase 4: membresías y congelaciones (backend, frontend y migración V3). Probada en el navegador.
- Pendiente:
  - Fase 5: pagos y comprobantes PDF. El pago total activa las membresías PENDING y recalcula sus fechas ese día.
  - Luego, en orden: caja, control de acceso (incluye el hardware de la fase 8) y notificaciones/dashboard/reportes.
- Pruebas con MySQL real: V3MigrationTest y MembershipConcurrencyTest usan Testcontainers. Con Docker apagado se omiten (aparecen como Skipped), así que hay que revisarlo antes de dar una fase por terminada.
