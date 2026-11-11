# Checklist de salida a producción

Pasos para poner EvolutionFit a funcionar con datos reales del gimnasio. Se hacen en orden; cada casilla se marca
al terminarla. Reglas que aplican: [REGLAS_NEGOCIO.md](REGLAS_NEGOCIO.md) (secciones 8.7, 10 y "Pendientes de
definir"). Respaldos: [RESPALDOS.md](RESPALDOS.md).

> **Decisiones pendientes** (marcadas con ⚠ abajo). Hasta tenerlas, este documento es la guía, no la orden de
> arranque:
> 1. Dónde corre producción (PC del gimnasio, servidor en la nube o este equipo).
> 2. Arrancar con una base nueva (recomendado) o limpiar la de desarrollo con un script.
> 3. Cuántos clientes hay que migrar (a mano o con una importación por CSV, que sería una función nueva).
> 4. Actualizar Spring Boot y los cambios de seguridad (límite de intentos del login, invalidar sesiones al
>    cambiar la contraseña) antes de arrancar.
> 5. Qué se hace con el efectivo sobrante después del arqueo (hoy rige el valor por defecto).

## 0. Antes del día de arranque

- [ ] Todas las pruebas pasan sobre el commit que se va a instalar: `./mvnw test` (Docker encendido, sin pruebas
      omitidas), `npm test -- --watch=false`, `npm run build` y `npm run e2e`.
- [ ] Ese commit tiene una etiqueta de versión: `git tag v1.0.0` (sirve para la reversa).
- [ ] Respaldo de la base de desarrollo (`scripts/backup.sh --label antes_produccion`) y verificado con
      `scripts/restore.sh`. Se conserva como archivo; no se borra.
- [ ] ⚠ Equipo de producción definido, con Docker, Java 21 y hora de Bogotá.
- [ ] Respaldo automático programado en ese equipo (RESPALDOS.md, "Programarlo") y una copia cifrada fuera del
      gimnasio definida.

## 1. Base de datos nueva (sin datos de prueba)

⚠ Recomendado: **no limpiar la base de desarrollo, sino empezar con una nueva.** Así no se borra nada (la regla
del proyecto es no borrar datos), no queda ningún resto de pruebas y el consecutivo de comprobantes empieza en 1
por sí solo (lo deja así la migración V4).

- [ ] Volumen MySQL nuevo, distinto del de desarrollo (otro nombre de volumen; si es el mismo equipo, también otro
      contenedor y otro puerto, y nunca el 3308 de las pruebas).
- [ ] MySQL publicada solo en `127.0.0.1` (no en toda la red).
- [ ] Al arrancar el backend por primera vez, Flyway crea el esquema completo (V1 a la última).
- [ ] Verificación (como root en el contenedor de producción):

      SELECT next_value FROM receipt_sequence;            -- debe ser 1
      SELECT COUNT(*) FROM clients;                       -- 0
      SELECT COUNT(*) FROM payments;                      -- 0
      SELECT COUNT(*) FROM memberships;                   -- 0
      SELECT COUNT(*) FROM cash_closings;                 -- 0
      SELECT COUNT(*) FROM users WHERE username LIKE 'e2e%';  -- 0
      SELECT version FROM flyway_schema_history ORDER BY installed_rank;  -- todas las migraciones, success = 1

## 2. Variables de producción (`.env` del equipo de producción)

Todas nuevas: **ningún valor se copia de desarrollo.** El `.env` queda con permisos 600 y una copia en un gestor de
contraseñas.

- [ ] `MYSQL_ROOT_PASSWORD`, `MYSQL_PASSWORD`: generadas (`openssl rand -base64 24`).
- [ ] `JWT_SECRET`: nueva (`openssl rand -base64 48`). Si se filtra, se cambia y todas las sesiones se cierran.
- [ ] `JWT_EXPIRATION_MINUTES`: duración de la sesión de recepción (por defecto 480 = 8 h).
- [ ] `CORS_ALLOWED_ORIGINS`: la dirección real del frontend (no `localhost:4200`).
- [ ] `BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` (mínimo 8, larga).
- [ ] Reglas configurables solo si el dueño pidió algo distinto del valor por defecto (README, tabla de variables).
- [ ] **No** existe `APP_E2E_ENABLED` ni `SPRING_PROFILES_ACTIVE=e2e`.
- [ ] `TZ=America/Bogota` en el backend y en MySQL (`--default-time-zone=-05:00`, como en docker-compose.yml).
- [ ] ⚠ HTTPS si se accede desde fuera del equipo (proxy con certificado). Swagger restringido o apagado.

## 3. Administrador real y usuarios

- [ ] Primer arranque del backend: crea el ADMIN con los datos `BOOTSTRAP_ADMIN_*` (solo si `users` está vacía).
- [ ] Ingresar como ese ADMIN y **cambiar la contraseña** desde la aplicación.
- [ ] **Borrar `BOOTSTRAP_ADMIN_PASSWORD` del `.env`** y reiniciar el backend.
- [ ] Crear un usuario de recepción por persona (nunca compartir usuario). Cada uno cambia su contraseña.

## 4. Configuración desde la aplicación (como ADMIN), en este orden

1. [ ] **Datos del comprobante:** nombre comercial, razón social, NIT, dirección, ciudad, teléfono, correo y
       logo (máximo 500 KB). El prefijo del consecutivo (`EF-` por defecto) se decide **antes** del primer pago.
2. [ ] **Planes** con sus precios reales (nombre, días, precio). Cada membresía guarda el precio del momento.
3. [ ] **Días de atención:** lunes a sábado por defecto; festivos de Colombia calculados solos; excepciones del
       ADMIN si abre o cierra un día especial.
4. [ ] **Clientes migrados** (paso 5) — antes de activar la caja.
5. [ ] **Configuración de caja:** base de efectivo y **fecha de inicio** — se hace al final y con cuidado:
       - la fecha no puede estar en el pasado y **se bloquea después del primer cierre** (no tiene arreglo);
       - la caja cuenta desde las 10 p.m. del día anterior a esa fecha;
       - fijarla el día de arranque, con el efectivo de la base contado en el cajón.

## 5. Carga de clientes migrados

⚠ Hoy se cargan uno por uno desde la pantalla. Si son muchos, hay que decidir si se hace una importación por CSV
(función nueva, con su propio plan).

- [ ] Lista del sistema anterior con: tipo y número de documento, nombres, apellidos, celular colombiano
      (obligatorio), plan, fecha de inicio, vencimiento real, precio pagado y si ya usó la congelación.
- [ ] Solo membresías **vigentes** (vencimiento ≥ hoy); las vencidas no se migran (regla 8.7).
- [ ] Por cada uno: crear el cliente y registrar la membresía **migrada** (solo ADMIN), con la nota de migración.
      No genera pago ni comprobante.
- [ ] Control: el número de clientes y de membresías migradas coincide con la lista; revisar 5 al azar contra la
      lista (fechas y precio).

## 6. Prueba de humo (sin cobrar)

**No registrar pagos de prueba en producción:** cada pago consume un número de comprobante y anularlo no lo
devuelve; el consecutivo quedaría con un hueco y un comprobante anulado desde el primer día.

- [ ] `/actuator/health` responde `UP`.
- [ ] Ingreso como ADMIN y como recepción; recepción no ve Reportes ni Usuarios.
- [ ] Panel de inicio, Clientes, Membresías, Vencimientos y Caja cargan sin errores.
- [ ] La ficha de un cliente migrado muestra su membresía y vencimiento correctos.
- [ ] El primer cobro real del día se revisa con calma: comprobante `EF-000001`, datos del gimnasio y logo
      correctos, PDF legible en el celular.
- [ ] Esa noche: el cierre automático de las 10 p.m. aparece en Caja, y al día siguiente recepción registra el
      arqueo.
- [ ] A la mañana siguiente: existe el respaldo automático de la noche y `respaldo.log` no tiene errores.

## 7. Plan de reversa

Antes de empezar: respaldo hecho y verificado, etiqueta `v1.0.0` en Git, y el procedimiento en papel listo
(recibos manuales).

| Qué falla | Qué se hace | Datos |
| --- | --- | --- |
| Un error de la aplicación (pantalla, cálculo) | Detener el backend y volver al jar de la versión anterior (`git checkout <etiqueta>` y compilar), o corregir y volver a instalar | No se tocan |
| Una migración falla al actualizar | Restaurar el respaldo hecho justo antes (`scripts/restore.sh <archivo> --into-real`) y volver al jar anterior. Flyway no tiene marcha atrás | Se vuelve al momento del respaldo |
| La base se daña o se pierde | Restaurar el último respaldo verificado; volver a registrar lo posterior desde los comprobantes y mensajes de WhatsApp | Se pierde lo posterior al respaldo |
| El sistema no se puede usar el primer día | Volver a los recibos en papel. Lo ya cobrado en el sistema queda guardado; se registra lo de papel cuando vuelva a funcionar | Se conserva lo registrado |

- [ ] Quién decide revertir: el dueño (o quien él designe), y en cuánto tiempo sin sistema se pasa a papel.
- [ ] Después de revertir: anotar qué pasó, la hora y qué datos hay que volver a registrar.
