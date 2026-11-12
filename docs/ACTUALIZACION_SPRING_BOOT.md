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

## Resultados (12/11/2026)

**Versiones finales.**

| Componente | Antes | Después |
|---|---|---|
| Spring Boot | 3.3.5 | 4.1.2 |
| Spring Framework | 6.1 | 7.0.10 |
| Spring Security | 6.3 | 7.1.2 |
| Tomcat | 10.1.31 | 11.0.27 |
| Hibernate | 6.5 | 7.4.13 |
| Flyway | 10.10 | 12.4.0 |
| Jackson | 2.17 | 3.1.7 |
| springdoc | 2.6.0 | 3.2.0 |
| jjwt | 0.12.6 | 0.13.0 |
| Testcontainers | 1.21.4 | 2.0.5 |

Jackson 2.21.7 queda solo como dependencia interna de jjwt-jackson.

**Cambios que no son de versión:**
- `spring.jackson.deserialization.fail-on-null-for-primitives: false`. Jackson 3 rechaza un booleano primitivo ausente y la API lo aceptaba como `false`.
- `server.error.*` pasa a `spring.web.error.*`.

**Pruebas:**
- `./mvnw test`: 332 de 332, 0 omitidas, en cada commit.
- `npm test`: 178 de 178. `ng build` compila.
- e2e: 21 flujos y 3 de caja, en cada commit.

**Con datos reales (copia del respaldo `pre_boot4`):**
- **Flyway 12.4** valida V1 a V7 y no aplica nada.
- **Respuestas:** se compararon 48 entre 3.3.5 y 4.1.2. Hay 46 idénticas en cuerpo y cabeceras, entre ellas el PDF del comprobante, la vista previa, el CSV y los errores 400, 401, 403 y 404. Las diferencias son dos:
  - `/actuator/health` agrega los grupos `liveness` y `readiness`;
  - `/v3/api-docs` documenta mejor las validaciones.
- **Escritura:** una inscripción nueva guarda la instantánea del comprobante y la auditoría con las mismas claves y el mismo formato de fecha que 3.3.5.
- **PDF del comprobante:** revisado como imagen. Se ve completo.

**Avisos de seguridad:**
- **CVE-2025-48988:** probado en vivo. Una subida del logo con 60 partes de relleno se aceptaba en 3.3.5 (200) y se rechaza en 4.1.2 (413). Con el archivo solo sigue funcionando.
  - El mensaje del 413 dice que el archivo supera los 500 KB. No es exacto para este caso, pero solo lo ve quien arme la petición a mano.
- **CVE-2026-22732:** Spring Security 7.1.2 trae la corrección. El PDF y el CSV salen con `X-Content-Type-Options`, `Cache-Control: no-store`, `X-Frame-Options` y las demás cabeceras, iguales a las de antes.

**Soporte.** La 4.1 tiene soporte gratuito hasta el 31/07/2027. Antes de esa fecha hay que pasar a la siguiente versión menor (4.2 o la que siga), con este mismo procedimiento.
