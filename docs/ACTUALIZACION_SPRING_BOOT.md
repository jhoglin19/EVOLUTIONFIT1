# Actualización de Spring Boot 3.3.5 → 3.5.16 → 4.1.x

Rama `upgrade/spring-boot-4.1`. `main` no cambia hasta que el dueño apruebe el merge.

## Por qué

- **Soporte gratuito.** La rama 3.3 dejó de recibir parches gratuitos el 30/06/2025 y la 3.5 el 30/06/2026. La 4.1 los recibe hasta el 31/07/2027.
- **Avisos de seguridad que sí nos aplican:**
  - CVE-2026-22732: cabeceras de seguridad en las respuestas que escribe la aplicación (PDF del comprobante, CSV de reportes). Se corrige en Spring Security 6.5.9 o en 7.0.4.
  - CVE-2025-48988: caída del servicio por exceso de partes multipart (afecta la subida del logo). Se corrige en Tomcat 10.1.42 o superior.
- **Ruta.** La guía oficial pide pasar primero por la última 3.5, para ver los avisos de lo que desaparece antes del salto a 4.x.

## Pasos (un commit cada uno)

1. Base verde y respaldo `pre_boot4` (este documento).
2. Spring Boot 3.5.16 y springdoc 2.8.x.
3. `@MockBean` → `@MockitoBean`.
4. Spring Boot 4.1.x: starters por módulo, Testcontainers 2, springdoc 3.x y Spring Security 7.
5. Jackson 3 (`com.fasterxml.jackson` → `tools.jackson`).
6. Limpieza: README, CLAUDE.md y resultados en este documento.

## Verificación en cada commit

- `./mvnw test` con Docker encendido: las pruebas con MySQL real (Testcontainers) no deben quedar omitidas.
- `npm test -- --watch=false`, `ng build` y `npm run e2e` (con `caffeinate -dimsu`).

## Validación con datos reales (fuera del repositorio)

1. **Respaldo.** Se hace con `scripts/backup.sh --label pre_boot4` y se verifica con `scripts/restore.sh <archivo> --compare-live`.
2. **Base temporal.** Una copia del respaldo se carga en una MySQL temporal, con los datos en memoria y contraseñas aleatorias que no se guardan.
3. **Arranque de cada versión contra esa copia.** Se arranca primero el backend actual y después el nuevo:
   - Flyway valida las sumas de V2 a V7 con datos reales (V1 es el baseline);
   - Hibernate carga todas las entidades.
4. **Comparación de respuestas.** Se guardan las respuestas de los endpoints principales, sin escribir nada:
   - lecturas como ADMIN y como recepción;
   - errores 400, 401, 403 y 404;
   - el CSV y los dos PDF.

   Luego se comparan el JSON normalizado y las cabeceras, y se revisa el PDF del comprobante.
5. **Limpieza.** La base temporal se borra al terminar.
