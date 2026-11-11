# Salida a producción

Cómo poner EvolutionFit a funcionar con datos reales del gimnasio. Se hace en orden y cada casilla se marca al
terminarla. Decisiones del dueño: [REGLAS_NEGOCIO.md](REGLAS_NEGOCIO.md), sección 13. Respaldos:
[RESPALDOS.md](RESPALDOS.md).

## Cómo queda funcionando

Producción va en la nube, como el sistema anterior:

```
Computador del gimnasio (solo navegador)
        │  https://<dominio>   (internet del gimnasio)
        ▼
VPS con Docker ──────────────────────────────────────────────
  Firewall: solo 80 y 443 abiertos a internet
  Proxy inverso con HTTPS (único servicio con puertos publicados)
     ├── /        → frontend Angular (archivos estáticos)
     └── /api     → backend Spring Boot (red interna de Docker)
                       └── MySQL 8.4 (red interna, sin puertos publicados)
  Respaldo diario 10:30 p.m. → cifrado con age → rclone → copia fuera del servidor
──────────────────────────────────────────────────────────────
```

- **Un único dominio**, con el frontend y la API en el mismo origen: no hay CORS entre dominios ni Vercel.
- **El computador del gimnasio solo abre el navegador.** No corre la base ni el backend; es lento y no hace
  falta.
- **La puerta (fase 8)** usará un puente en el computador del gimnasio que se conecta *de salida* al servidor.
  Se diseña en la fase 8 ([HARDWARE_CONTROL_ACCESO.md](HARDWARE_CONTROL_ACCESO.md)); no afecta este arranque.

### Pendiente antes del arranque

Estas decisiones o tareas siguen abiertas y bloquean el arranque:

1. **Actualizar Spring Boot.** Las dependencias de la versión 3.3.5 tienen vulnerabilidades críticas conocidas
   (Tomcat, Spring, Jackson). Falta decidir hasta qué versión subir y hacerlo con todas las pruebas.
2. **Cambios de seguridad aprobados:**
   - límite de intentos en el inicio de sesión (valores por decidir);
   - Swagger apagado en producción;
   - protección para que el perfil `e2e` no pueda arrancar con el perfil de producción.
3. **Archivos de despliegue para el servidor** (por crear y aprobar; no son código de la aplicación):
   - `docker-compose.prod.yml`: MySQL, backend y proxy, sin puertos de MySQL ni del backend;
   - la configuración del proxy (por ejemplo, Caddy, que obtiene y renueva el certificado HTTPS solo);
   - la compilación del frontend que sirve el proxy.

## 1. Proveedor, región y servidor

- [ ] **Proveedor:** uno con VPS por hora o por mes, panel con firewall propio, snapshots o copias automáticas y
      autenticación en dos pasos. Ejemplos: DigitalOcean, Vultr, Linode (Akamai) o Hetzner. Si el sistema
      anterior ya usaba uno de confianza, se puede repetir.
- [ ] **Región:** la más cercana a Colombia en latencia. Suele ser Miami o la costa este de EE. UU. Hay que
      medirla desde el gimnasio: `ping` al servidor de prueba de cada región; menos de 100 ms va bien.
- [ ] **Datos personales:** el servidor guardará cédulas, celulares y pagos de clientes fuera de Colombia.
      Revisar con el dueño lo que pide la Ley 1581 de 2012 (habeas data) para la transferencia internacional de
      datos, y que el proveedor no comparta datos.
- [ ] **Tamaño:** mínimo 4 GB de RAM y 2 vCPU, con 50 GB o más de disco SSD.
      - Uso aproximado: MySQL ~1 GB; backend con `-Xmx1g` ~1,2 GB; proxy y sistema ~0,5 GB.
      - Holgura para picos y actualizaciones.
- [ ] **Sistema:** Ubuntu Server LTS (24.04).
- [ ] **Cuenta del proveedor:** a nombre del dueño, con autenticación en dos pasos y pago automático. Si la cuenta
      se suspende por falta de pago, el gimnasio se queda sin sistema.
- [ ] **Copias del proveedor** (snapshots o "backups" del VPS) activadas. Son una capa extra: no reemplazan los
      respaldos de la base ([RESPALDOS.md](RESPALDOS.md)).

## 2. Dominio y HTTPS

- [ ] Dominio comprado a nombre del dueño (por ejemplo, `.com` o `.co`), con renovación automática y 2FA en el
      registrador. Si el dominio vence, el sistema deja de abrir.
- [ ] Registro DNS `A` (y `AAAA` si hay IPv6) del dominio o subdominio apuntando a la IP del VPS.
- [ ] El proxy obtiene el certificado HTTPS (Let's Encrypt) y lo renueva solo. Todo lo que llega por HTTP (80) se
      redirige a HTTPS (443).
- [ ] Cabeceras de seguridad en el proxy: HSTS, `X-Content-Type-Options`, `Referrer-Policy` y una CSP para la
      aplicación.

## 3. Seguridad del servidor

- [ ] **Usuario administrador sin root**, con `sudo`. Nadie trabaja como `root`.
- [ ] **SSH solo con llave:**
      - en `/etc/ssh/sshd_config`: `PasswordAuthentication no` y `PermitRootLogin no`;
      - la llave privada se guarda en el equipo de quien administra, con frase de paso, y una copia en el gestor
        de contraseñas del dueño.
- [ ] **Firewall: solo 80 y 443 abiertos a internet.**
      - En el **firewall del proveedor** (fuera del VPS): entrada permitida solo a 80 y 443.
      - SSH (22) **no queda abierto a todo internet**. Hay dos formas: permitirlo solo desde la IP de quien
        administra, en el firewall del proveedor, o entrar por la consola web del proveedor o una VPN como
        Tailscale.
      - **Cuidado con Docker:** un puerto publicado con `ports:` en Docker se salta `ufw`. Por eso MySQL y el
        backend **no publican puertos**: solo el proxy publica 80 y 443. El firewall del proveedor sigue
        protegiendo aunque Docker toque las reglas internas.
- [ ] **Actualizaciones automáticas de seguridad** del sistema (`unattended-upgrades`), con reinicio automático
      si hace falta a las 4:00 a.m. Es fuera del horario del gimnasio y después del respaldo de las 10:30 p.m.
- [ ] **Imágenes de Docker** (MySQL 8.4 y el runtime de Java): se actualizan una vez al mes, a mano. Siempre hay
      que hacer antes un respaldo con `scripts/backup.sh --label antes_actualizar` y verificarlo.
- [ ] **Hora del servidor en Bogotá** (`timedatectl set-timezone America/Bogota`), además de `TZ=America/Bogota`
      en los contenedores. El respaldo programado y los registros quedan en la hora del gimnasio.
- [ ] **Registros de Docker con límite de tamaño** (`max-size` y `max-file`), para que no llenen el disco.
- [ ] **Monitoreo externo** gratuito de `https://<dominio>/actuator/health` (por ejemplo, UptimeRobot), con aviso
      por correo o WhatsApp al dueño si el sistema cae. Además, una alerta si el disco pasa del 80 %.

## 4. MySQL sin exposición pública

- [ ] MySQL **sin `ports:`** en `docker-compose.prod.yml`: solo la ve el backend, por la red interna de Docker.
- [ ] Volumen de datos con nombre propio de producción. Es una **base nueva**: Flyway crea el esquema completo al
      primer arranque y la V4 deja el consecutivo de comprobantes en 1. No se copia nada de la base de desarrollo.
- [ ] El backend se conecta con el usuario de la aplicación (`MYSQL_USER`), nunca con root.
- [ ] Verificación después del primer arranque (como root, dentro del contenedor):

      SELECT next_value FROM receipt_sequence;                 -- debe ser 1
      SELECT COUNT(*) FROM clients;                            -- 0
      SELECT COUNT(*) FROM payments;                           -- 0
      SELECT COUNT(*) FROM memberships;                        -- 0
      SELECT COUNT(*) FROM cash_closings;                      -- 0
      SELECT COUNT(*) FROM users WHERE username LIKE 'e2e%';   -- 0
      SELECT version, success FROM flyway_schema_history ORDER BY installed_rank;  -- todas con success = 1

## 5. Arranque automático después de un reinicio

- [ ] El servicio Docker arranca con el sistema: `sudo systemctl enable docker`.
- [ ] Todos los servicios de `docker-compose.prod.yml` llevan `restart: unless-stopped`. El backend espera a que
      MySQL esté sano (`depends_on` con `condition: service_healthy`).
- [ ] **Prueba:** reiniciar el VPS (`sudo reboot`). En menos de 2 minutos `https://<dominio>` debe abrir solo, sin
      que nadie haga nada.
- [ ] Si el servidor estuvo apagado a las 10 p.m., la caja hace sola los cierres que faltaron al volver a arrancar
      (REGLAS_NEGOCIO.md, sección 10, punto 2).

## 6. Variables de producción (`.env` en el servidor)

Todas nuevas: **ningún valor se copia de desarrollo.** El archivo va en la carpeta del despliegue, por ejemplo
`/opt/evolutionfit/.env`, con permisos 600 y dueño el usuario administrador. Una copia queda en el gestor de
contraseñas del dueño.

- [ ] `MYSQL_ROOT_PASSWORD` y `MYSQL_PASSWORD`: generadas con `openssl rand -base64 24`.
- [ ] `JWT_SECRET`: nueva, con `openssl rand -base64 48`. Si se filtra, se cambia y todas las sesiones se cierran.
- [ ] `JWT_EXPIRATION_MINUTES`: duración de la sesión de recepción (por defecto 480, es decir, 8 h).
- [ ] `CORS_ALLOWED_ORIGINS=https://<dominio>`: el mismo origen; no se agregan otros.
- [ ] `BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_EMAIL` y `BOOTSTRAP_ADMIN_PASSWORD` (larga, mínimo 8).
- [ ] Reglas configurables: solo si el dueño pidió algo distinto del valor por defecto (README, tabla de
      variables).
- [ ] **No** existe `APP_E2E_ENABLED`, y `SPRING_PROFILES_ACTIVE` no incluye `e2e`.
- [ ] `TZ=America/Bogota` en el backend y en MySQL (`--default-time-zone=-05:00`, como en `docker-compose.yml`).

## 7. Antes del día de arranque

- [ ] Todas las pruebas pasan sobre el commit que se va a instalar:
      - `./mvnw test`, con Docker encendido y sin pruebas omitidas;
      - `npm test -- --watch=false` y `npm run build`;
      - `npm run e2e`.
- [ ] Ese commit tiene una etiqueta de versión, por ejemplo `git tag v1.0.0`. Sirve para la reversa.
- [ ] Respaldos funcionando **en el servidor** ([RESPALDOS.md](RESPALDOS.md)):
      - respaldo diario programado;
      - cifrado con age y subida con rclone probados;
      - una restauración de prueba hecha con `scripts/restore.sh`.
- [ ] El sistema anterior se sigue usando hasta el día de arranque (ver la reversa).

## 8. Administrador real y usuarios

- [ ] En el primer arranque del backend se crea el ADMIN con `BOOTSTRAP_ADMIN_*`. Solo pasa si la tabla `users`
      está vacía.
- [ ] Entrar como ese ADMIN y **cambiar la contraseña** desde la aplicación.
- [ ] **Borrar `BOOTSTRAP_ADMIN_PASSWORD` del `.env`** y reiniciar el backend.
- [ ] Crear un usuario de recepción por persona; nunca se comparte un usuario. Cada uno cambia su contraseña.

## 9. Configuración desde la aplicación (como ADMIN), en este orden

1. [ ] **Datos del comprobante:**
       - nombre comercial, razón social, NIT, dirección, ciudad, teléfono, correo y logo (máximo 500 KB);
       - el prefijo del consecutivo (`EF-` por defecto) se decide **antes** del primer pago.
2. [ ] **Planes** con sus precios reales. Cada membresía guarda el precio del momento.
3. [ ] **Días de atención:**
       - lunes a sábado por defecto;
       - los festivos de Colombia se calculan solos;
       - excepciones si el gimnasio abre o cierra un día especial.
4. [ ] **Clientes del sistema anterior** (sección 10). Se cargan antes de activar la caja.
5. [ ] **Configuración de caja: la base de efectivo y la fecha de inicio.** Se hace al final y con cuidado:
       - la fecha no puede estar en el pasado y **se bloquea después del primer cierre**; no tiene arreglo;
       - la caja cuenta desde las 10 p.m. del día anterior a esa fecha;
       - se fija el día de arranque, con la base contada en el cajón.

## 10. Carga de los clientes del sistema anterior (80 a 100, a mano)

Decisión del dueño: se cargan a mano con el flujo de **membresía migrada**, sin pagos y sin importación por CSV
(REGLAS_NEGOCIO.md, sección 8, punto 7, y sección 13).

### Preparación (dueño, 1 a 2 horas)

Una hoja de cálculo exportada o pasada del sistema anterior, con una fila por cliente y estas columnas:

| Columna | Regla |
| --- | --- |
| Tipo y número de documento | Sin repetidos |
| Nombres y apellidos | — |
| Celular | Colombiano, 10 dígitos que empiezan por 3: es obligatorio y el sistema rechaza otros |
| Correo, dirección, fecha de nacimiento | Opcionales |
| Plan | Uno de los planes ya creados en el sistema nuevo |
| Fecha de inicio | Hoy o antes; no puede ser futura |
| Vencimiento real | Hoy o después: **solo se migran membresías vigentes** |
| Precio pagado | El que pagó en el sistema anterior |
| ¿Ya usó la congelación? | Sí o no |
| Columna "cargado" | Para marcar el avance |

- Los clientes con la membresía **vencida no se migran.** Si vuelven, se inscriben con el asistente normal y en
  los reportes cuentan como cliente nuevo (sección 11 de las reglas).
- Los celulares que no cumplan la regla se corrigen en la hoja antes de empezar.

### Paso a paso por cliente (como ADMIN)

1. **Clientes → Nuevo cliente:** tipo y número de documento, nombres, apellidos y celular; lo opcional, si se
   tiene. Luego **Registrar cliente**.
2. Abrir la ficha del cliente → **Membresía migrada**:
   - Plan, Inicio, Vencimiento real y Precio pagado (COP).
   - Nota de migración (obligatoria). Ejemplo: "Migrado del sistema anterior, recibo N.º …".
   - Marcar "ya usó la congelación" si aplica.
3. Comprobar en la ficha que el vencimiento sea el de la hoja, y marcar la fila como cargada.

No genera pago, comprobante ni movimiento de caja. Solo el ADMIN puede registrar membresías migradas.

### Tiempo estimado

| Parte | Tiempo |
| --- | --- |
| Crear el cliente | ~1,5 min |
| Registrar la membresía migrada | ~1,5 a 2 min |
| Revisar y marcar en la hoja | ~0,5 min |
| **Por cliente** | **~3,5 a 4 min** |
| 80 clientes | ~4,5 a 5,5 horas |
| 100 clientes | ~6 a 7 horas |
| Preparar la hoja (antes) y control final (después) | ~1,5 a 2,5 horas |

- Conviene repartir la carga en **2 o 3 sesiones de 2 a 3 horas**: el cansancio es la principal fuente de
  errores de digitación.
- Con dos personas (una dicta y la otra digita) se acorta y se cometen menos errores.

### Cuándo hacerlo

- Después de crear los planes y **antes de fijar la fecha de inicio de la caja**.
- Lo más cerca posible del día de arranque, por ejemplo los dos días anteriores. Así hay menos cambios en el
  sistema anterior mientras se carga.
- Hay que llevar una lista de lo que cambie en el sistema anterior durante la carga: renovaciones, clientes
  nuevos y congelaciones. El día de arranque se pone al día:
  - un cliente nuevo o una renovación que ya empezó se registra como membresía migrada;
  - desde el día de arranque, todo se cobra en el sistema nuevo.

### Control final

- [ ] El número de clientes y de membresías migradas en el sistema coincide con las filas de la hoja.
- [ ] Revisar 10 clientes al azar contra la hoja: documento, celular, plan, vencimiento y precio.
- [ ] La alerta de vencimientos muestra a los que vencen en los próximos 5 días y coincide con la hoja.

## 11. Prueba de humo (sin cobrar)

**No se registran pagos de prueba en producción.** Cada pago consume un número de comprobante y anularlo no lo
devuelve, así que el consecutivo empezaría con un hueco y un comprobante anulado.

- [ ] `https://<dominio>` abre con candado (HTTPS válido), y `http://` redirige a `https://`.
- [ ] Desde fuera del servidor, el puerto de MySQL (3306) y el del backend (8080) **no responden**: se prueba con
      `nc -z <ip> 3306` y `nc -z <ip> 8080`.
- [ ] `/actuator/health` responde `UP`, y el monitoreo externo lo ve.
- [ ] Desde el computador del gimnasio se puede entrar como ADMIN y como recepción. Recepción no ve Reportes ni
      Usuarios.
- [ ] Panel de inicio, Clientes, Membresías, Vencimientos y Caja cargan sin errores y a buena velocidad.
- [ ] La ficha de un cliente migrado muestra su membresía y su vencimiento correctos.
- [ ] El **primer cobro real** se revisa con calma:
      - comprobante `EF-000001`;
      - datos del gimnasio y logo correctos;
      - el PDF se ve bien en el celular.
- [ ] Esa noche, el cierre automático de las 10 p.m. aparece en Caja. Al día siguiente, recepción registra el
      arqueo.
- [ ] A la mañana siguiente:
      - el respaldo de las 10:30 p.m. está en el servidor **y** en el destino externo;
      - `respaldo.log` no tiene errores.

## 12. Plan de reversa

Antes de empezar debe estar listo lo siguiente:
- etiqueta de versión en Git;
- respaldos funcionando y una restauración probada;
- un snapshot del VPS tomado justo antes del arranque;
- el sistema anterior todavía disponible;
- recibos en papel a mano.

| Qué falla | Qué se hace | Datos |
| --- | --- | --- |
| Un error de la aplicación (pantalla, cálculo) | Volver a la imagen o jar de la etiqueta anterior y reiniciar el backend, o corregir y volver a desplegar | No se tocan |
| Una migración de base falla al actualizar | Restaurar el respaldo hecho justo antes (`scripts/restore.sh <archivo> --into-real`) y volver a la versión anterior. Flyway no tiene marcha atrás | Se vuelve al momento del respaldo |
| El VPS se daña, se borra o no arranca | Restaurar el snapshot del proveedor, o crear un VPS nuevo con esta guía y restaurar el último respaldo cifrado. Después, apuntar el DNS a la nueva IP | Se pierde lo posterior al respaldo; se vuelve a registrar desde los comprobantes y los mensajes de WhatsApp |
| Se cae internet del gimnasio o el servidor | Recibos en papel; se registran en el sistema cuando vuelva. La puerta falla cerrada: se usan el botón manual de recepción y la llave (fase 8) | Se conserva lo registrado |
| El sistema no se puede usar el primer día | Volver al sistema anterior y a los recibos en papel. Lo cobrado en el sistema nuevo queda guardado y se reconcilia después | Se conserva lo registrado |

- [ ] El dueño, o quien él designe, decide revertir, y define cuánto tiempo sin sistema se espera antes de pasar a
      papel.
- [ ] Después de revertir, se anota qué pasó, a qué hora y qué datos hay que volver a registrar.
