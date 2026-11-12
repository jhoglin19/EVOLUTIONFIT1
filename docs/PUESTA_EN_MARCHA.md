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
  Caddy con HTTPS (único servicio con puertos publicados)
     ├── /           → frontend Angular (archivos estáticos)
     ├── /api        → backend Spring Boot, perfil prod (red interna "app", sin salida a internet)
     └── /api/health → salud para el aviso externo de caída
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

1. **Actualizar Spring Boot.** Hecho: 4.1.2, fusionado en `main` el 12/11/2026. Procedimiento y resultados:
   [ACTUALIZACION_SPRING_BOOT.md](ACTUALIZACION_SPRING_BOOT.md).
   La 4.1 tiene soporte gratuito hasta el 31/07/2027; después hay que subir a la siguiente versión menor.
2. **Cambios de seguridad aprobados:** hechos el 12/11/2026.
   - V8: cambiar o restablecer una contraseña cierra las sesiones abiertas;
   - límite de intentos en el inicio de sesión: 5 fallos en 15 minutos, por cuenta y por IP
     ([REGLAS_NEGOCIO.md](REGLAS_NEGOCIO.md), sección 13, punto 6);
   - Swagger apagado con el perfil `prod` (`SWAGGER_ENABLED=true` lo enciende);
   - logs sin cédulas en los errores de restricciones;
   - una prueba falla si algún endpoint no tiene `@PreAuthorize`;
   - el perfil `prod` no arranca si detecta el perfil `e2e` o `APP_E2E_ENABLED=true` (ProdGuard).
3. **Archivos de despliegue:** hechos el 12/11/2026 y ensayados en un Mac (sección 14):
   - `docker-compose.prod.yml`, `backend/Dockerfile`, `deploy/caddy/Dockerfile` y `deploy/caddy/Caddyfile`;
   - `deploy/env.prod.example` y `scripts/check-env-prod.sh`;
   - `scripts/deploy.sh`, `scripts/rollback.sh`, `scripts/prod-health.sh` y `scripts/offsite.sh`.

   **Lo que falta para arrancar** son decisiones y cuentas del dueño: proveedor y región del VPS, dominio con
   acceso a su DNS, correo para Let's Encrypt, cuenta de Backblaze B2 (o Google Drive) y el par de llaves de age.

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
- [ ] **Swap de 2 GB.** Con 4 GB de RAM, un pico (compilar las imágenes en una actualización, un reporte grande)
      puede agotar la memoria y el sistema mataría a MySQL. El swap da margen y no se usa en la operación normal:

      sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
      sudo mkswap /swapfile && sudo swapon /swapfile
      echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
      echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-swap.conf && sudo sysctl --system

      Comprobar con `free -h` (debe verse `Swap: 2.0Gi`) y después de un reinicio.
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
- [ ] Cabeceras de seguridad en Caddy (`deploy/caddy/Caddyfile`): HSTS, CSP, `X-Content-Type-Options`,
      `X-Frame-Options`, `Referrer-Policy` y `Permissions-Policy`. Se comprueban con `scripts/prod-smoke.sh`.
- [ ] **HSTS a un año, más adelante.** Se arranca con `max-age=86400` (un día) a propósito.
      - **Qué hace HSTS:** el navegador recuerda, durante `max-age`, que el dominio solo se abre por HTTPS. Si en
        ese tiempo el certificado falla (venció, se cambió de servidor o de dominio y quedó mal), el navegador
        **no deja entrar ni con "continuar de todos modos"**.
      - **No se puede revertir rápido:** bajar `max-age` solo surte efecto cuando cada navegador vuelve a entrar
        con HTTPS funcionando. Con un año, un error de certificado puede dejar el computador del gimnasio sin
        sistema hasta que se arregle el certificado; no hay forma de "desactivarlo" desde el servidor.
      - **Cuándo subirlo:** después de al menos 2 semanas de operación real sin ningún problema de HTTPS, con el
        dominio definitivo (no se va a cambiar) y la renovación automática del certificado comprobada al menos una
        vez (`docker compose logs caddy | grep -i renew`, o revisar la fecha del certificado en el navegador).
      - **Cómo:** en `/opt/evolutionfit/.env.prod` poner `HSTS_MAX_AGE=31536000`, recrear Caddy
        (`docker compose -f docker-compose.prod.yml --env-file /opt/evolutionfit/.env.prod up -d caddy`) y
        comprobar la cabecera: `curl -sI https://<dominio> | grep -i strict`.
      - No se usa `includeSubDomains` ni `preload`: el dominio puede tener otros usos y la lista de precarga de
        los navegadores tarda meses en quitarse.

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
- [ ] **Registros de Docker con límite de tamaño:** ya están en `docker-compose.prod.yml` (10 MB × 5 archivos por
      servicio).
- [ ] **Aviso externo de caída.** Un servicio gratuito fuera del VPS (por ejemplo UptimeRobot) revisa cada 5
      minutos `https://<dominio>/api/health`, con la palabra clave `"UP"`, y avisa al dueño por correo (y si se
      quiere por Telegram o WhatsApp) cuando no responde o no dice UP.
      - `/api/health` comprueba el backend y su conexión a MySQL, no solo que Caddy esté arriba; no muestra
        detalles. `/actuator` no es accesible desde fuera.
      - Probarlo una vez: detener el backend (`docker compose ... stop backend`), esperar el aviso y arrancarlo.
- [ ] **Alerta de disco casi lleno (80 %).** Si el disco se llena, MySQL se detiene y los respaldos fallan.
      - Preferido: la alerta de disco del propio proveedor (por ejemplo, "Monitoring alerts" de DigitalOcean).
      - Si el proveedor no la tiene: un chequeo de healthchecks.io que espera una señal cada hora y avisa si
        no llega. En `crontab -e` del usuario administrador (la señal solo se envía si el disco está bajo 80 %):

            0 * * * * [ "$(df --output=pcent / | tail -1 | tr -dc 0-9)" -lt 80 ] && curl -fsS -m 10 -o /dev/null https://hc-ping.com/<uuid-del-chequeo-de-disco>

      - Qué hacer al recibirla: `docker system df` y `docker image prune` (imágenes viejas, dejando las de la
        versión anterior para la reversa) y revisar `~/evolutionfit-backups`.

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

## 6. Variables de producción (`/opt/evolutionfit/.env.prod`)

Todas nuevas: **ningún valor se copia de desarrollo.** Carpetas en el servidor:

```
/opt/evolutionfit/app          clon del repositorio (se instalan etiquetas de versión, nunca se edita aquí)
/opt/evolutionfit/.env.prod    variables de producción: FUERA del repositorio, permisos 600, dueño el administrador
/opt/evolutionfit/estado       versión instalada y anterior (la escriben deploy.sh y rollback.sh)
/opt/evolutionfit/offsite.env  destino de la copia externa (RESPALDOS.md)
```

- [ ] Partir de `deploy/env.prod.example`. Una copia del archivo lleno queda en el gestor de contraseñas del dueño.
- [ ] **Validarlo siempre antes de arrancar:** `scripts/check-env-prod.sh /opt/evolutionfit/.env.prod`. No lo
      ejecuta ni imprime valores; revisa permisos, que esté fuera de git, longitudes mínimas, que CORS sea
      `https://<DOMAIN>` y que no haya variables de pruebas.

- [ ] `MYSQL_ROOT_PASSWORD` y `MYSQL_PASSWORD`: generadas con `openssl rand -base64 24`.
- [ ] `JWT_SECRET`: nueva, con `openssl rand -base64 48`. Si se filtra, se cambia y todas las sesiones se cierran.
- [ ] `JWT_EXPIRATION_MINUTES`: duración de la sesión de recepción (por defecto 480, es decir, 8 h).
- [ ] `DOMAIN=<dominio>` y `ACME_EMAIL=<correo del dueño>` (avisos de Let's Encrypt).
- [ ] `CORS_ALLOWED_ORIGINS=https://<dominio>`: el mismo origen; no se agregan otros.
- [ ] `BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_EMAIL` y `BOOTSTRAP_ADMIN_PASSWORD` (larga, mínimo 8).
- [ ] Reglas configurables: solo si el dueño pidió algo distinto del valor por defecto (README, tabla de
      variables).
- [ ] `SPRING_PROFILES_ACTIVE=prod` lo fija `docker-compose.prod.yml` (no va en `.env.prod`). Con ese perfil
      Swagger queda apagado y ProdGuard impide arrancar con algo de las pruebas e2e.
- [ ] Límite de intentos del inicio de sesión: los valores por defecto (5 fallos en 15 minutos, por cuenta y por IP)
      salvo que el dueño pida otros (`LOGIN_MAX_FAILURES_PER_USER`, `LOGIN_MAX_FAILURES_PER_IP`, `LOGIN_WINDOW`).
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

- [ ] **Primera instalación** (en el VPS, como el usuario administrador):

      sudo mkdir -p /opt/evolutionfit && sudo chown $USER /opt/evolutionfit
      git clone <repositorio> /opt/evolutionfit/app
      cp /opt/evolutionfit/app/deploy/env.prod.example /opt/evolutionfit/.env.prod
      chmod 600 /opt/evolutionfit/.env.prod     # y llenarlo (sección 6)
      cd /opt/evolutionfit/app && scripts/deploy.sh v1.0.0

      `deploy.sh` valida el `.env.prod`, construye las imágenes, levanta todo y comprueba la salud por HTTPS. En
      la primera instalación no hay respaldo previo porque la base no existe.
- [ ] En el primer arranque del backend se crea el ADMIN con `BOOTSTRAP_ADMIN_*`. Solo pasa si la tabla `users`
      está vacía.
- [ ] Entrar como ese ADMIN, abrir el menú del usuario (arriba a la derecha) → **Cambiar mi contraseña**. Al
      guardarla se cierra la sesión y el login dice "Tu contraseña cambió. Inicia sesión de nuevo.". Entrar con la
      nueva.
- [ ] **Borrar `BOOTSTRAP_ADMIN_PASSWORD` de `.env.prod`** y recrear el backend:
      `docker compose -f docker-compose.prod.yml --env-file /opt/evolutionfit/.env.prod up -d backend`.
      `check-env-prod.sh` avisa mientras siga definida.
- [ ] Crear un usuario de recepción por persona; nunca se comparte un usuario. El ADMIN les da una contraseña
      inicial y cada uno la cambia en su primer ingreso con **Cambiar mi contraseña**.

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
| Un error de la aplicación (pantalla, cálculo) | `scripts/rollback.sh <etiqueta anterior>` (usa las imágenes guardadas, no reconstruye), o corregir y volver a desplegar | No se tocan |
| Una migración de base falla o deja la base incompatible | `scripts/rollback.sh <etiqueta anterior> --restore <respaldo pre_<versión>>`: restaura el respaldo hecho justo antes de actualizar y vuelve a la versión anterior. Flyway no tiene marcha atrás | Se vuelve al momento del respaldo |
| El VPS se daña, se borra o no arranca | Restaurar el snapshot del proveedor, o crear un VPS nuevo con esta guía y restaurar el último respaldo cifrado. Después, apuntar el DNS a la nueva IP | Se pierde lo posterior al respaldo; se vuelve a registrar desde los comprobantes y los mensajes de WhatsApp |
| Se cae internet del gimnasio o el servidor | Recibos en papel; se registran en el sistema cuando vuelva. La puerta falla cerrada: se usan el botón manual de recepción y la llave (fase 8) | Se conserva lo registrado |
| El sistema no se puede usar el primer día | Volver al sistema anterior y a los recibos en papel. Lo cobrado en el sistema nuevo queda guardado y se reconcilia después | Se conserva lo registrado |

- [ ] El dueño, o quien él designe, decide revertir, y define cuánto tiempo sin sistema se espera antes de pasar a
      papel.
- [ ] Después de revertir, se anota qué pasó, a qué hora y qué datos hay que volver a registrar.

## 13. Actualizar a una versión nueva

1. En el equipo de desarrollo, con todas las pruebas en verde (sección 7): `git tag v1.0.1` y subir la etiqueta.
2. Avisar en recepción: el sistema queda 1 a 3 minutos sin responder. Mejor fuera del horario del gimnasio.
3. En el VPS: `cd /opt/evolutionfit/app && scripts/deploy.sh v1.0.1`. El script:
   - valida `.env.prod` y que el repositorio no tenga cambios;
   - hace el respaldo `pre_v1_0_1` y lo verifica con `restore.sh` (si no se puede verificar, no instala nada);
   - instala la etiqueta, construye las imágenes `evolutionfit-backend:v1.0.1` y `evolutionfit-web:v1.0.1`,
     levanta los servicios y comprueba la salud por HTTPS (`prod-health.sh`);
   - si la comprobación falla, muestra el comando exacto de reversa.
4. Entrar a la aplicación y revisar Inicio, Caja y un comprobante.
5. **Reversa** si algo salió mal: `scripts/rollback.sh <versión anterior>` (está en
   `/opt/evolutionfit/estado/anterior`). Si la versión nueva aplicó una migración incompatible, agregar
   `--restore <respaldo pre_>`: se pierde lo registrado desde ese respaldo (sección 12).
6. Las imágenes viejas se borran a mano cuando ya no hagan falta, dejando siempre las de la versión anterior.

## 14. Ensayo en un Mac antes del VPS

`scripts/prod-smoke.sh`, con Docker encendido, levanta la misma pila de producción en
`https://evolutionfit.localhost:8443` (certificado de la CA interna de Caddy, base propia, secretos aleatorios) y
comprueba: HTTPS y redirección; cabeceras, compresión y caché; rutas internas en 404 y `/api/health`; que MySQL y
el backend no publican puertos ni salen a internet; que nada corre como root; solo lectura, rotación de logs y
reinicio automático; el flujo del ADMIN inicial; la IP real del cliente con el Caddy real (dos clientes con IPs
y cuentas distintas, inventando `X-Forwarded-For`); Playwright (login, Clientes, PDF, sin errores de CSP, fuentes
ni imágenes); y un respaldo con su restauración de verificación y su copia externa cifrada. Al terminar borra
todo. El ensayo de actualización y reversa entre dos versiones se hizo igual, en un clon desechable.
