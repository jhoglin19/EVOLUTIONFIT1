# Respaldos de la base de datos

Procedimiento para respaldar y restaurar la base de EvolutionFit: MySQL 8.4 en Docker, base `evolution_fit`. En
producción corre en el VPS ([PUESTA_EN_MARCHA.md](PUESTA_EN_MARCHA.md)). Los scripts están en `scripts/` y se
ejecutan desde la carpeta del proyecto. Las decisiones del dueño están en
[REGLAS_NEGOCIO.md](REGLAS_NEGOCIO.md), sección 13.

## Las capas de respaldo

| Capa | Dónde | Frecuencia | Para qué |
| --- | --- | --- | --- |
| 1. Respaldo local | En el VPS, `~/evolutionfit-backups/` | Diario, 10:30 p.m., automático | Restaurar rápido un error del día |
| 2. Copia externa cifrada | Backblaze B2 (recomendado) o Google Drive del dueño, cifrada con age y subida con rclone | Diario, justo después del local | Sobrevivir a la pérdida total del VPS o de la cuenta del proveedor |
| 3. Disco externo | Un disco que se guarda **fuera del gimnasio**, con copias cifradas | Mensual, a mano | Una copia que no depende de internet ni de ninguna cuenta |
| 4. Copias del proveedor | Snapshots o "backups" del VPS | Según el proveedor (semanal o diaria) | Capa extra para recuperar el servidor completo. **No reemplaza** las anteriores: la retención es corta y depende del mismo proveedor |

Además hay una **prueba de restauración mensual** (más abajo). Un respaldo que nunca se ha restaurado no es un
respaldo.

## Qué se respalda

Toda la base `evolution_fit`:
- clientes, membresías, pagos y comprobantes;
- caja y auditoría;
- usuarios y configuración, incluido el logo del comprobante.

El historial de Flyway va incluido, así que al restaurar el backend sabe en qué migración está.

No se respaldan aquí:
- el `.env` del servidor: se guarda en el gestor de contraseñas del dueño;
- la llave privada de age: ver más abajo;
- el código: está en Git.

## Respaldo local (capa 1)

```bash
scripts/backup.sh                    # respaldo manual
scripts/backup.sh --label pre_V8     # antes de una migración, de una actualización o de la puesta en marcha
scripts/backup.sh --auto             # el programado: diario, semanal (domingo) o mensual (día 1), con rotación
```

- **Dónde queda:** en `~/evolutionfit-backups/` (carpeta con permisos 700), como
  `evolution_fit_AAAAMMDD_HHMMSS_<etiqueta>.sql.gz`, con permisos 600 y su suma SHA-256 en un archivo `.sha256`.
- **No interrumpe a recepción:** la copia es consistente aunque esté trabajando (`--single-transaction`).
- **Contraseñas:** el script usa la que el contenedor ya tiene en su entorno; no lee `.env` ni imprime
  contraseñas.
- **Validación:** si el volcado queda incompleto, dañado o sospechosamente pequeño, se descarta y el script
  termina con error.
- **Rotación (`--auto`):** se conservan 14 diarios, 8 semanales y 12 mensuales. Los manuales nunca se borran
  solos.
- **Sin cifrar:** los archivos locales quedan sin cifrar, en el mismo servidor que la base. Quien llegue a
  ellos ya tendría acceso a la base, así que no agregan riesgo. Todo lo que **sale** del servidor va cifrado.

## Llave de cifrado (age)

Los respaldos que salen del servidor se cifran con [age](https://age-encryption.org). Se usa un par de llaves:

- **Llave pública** (empieza por `age1…`):
  - va en el servidor y sirve para **cifrar**;
  - no es secreta.
- **Llave privada** (empieza por `AGE-SECRET-KEY-1…`):
  - es la única que permite **descifrar**;
  - **nunca va en el servidor**, ni en el repositorio, ni en el mismo lugar que los respaldos.

Así, si alguien entra al servidor o a la cuenta de B2 o Drive, no puede leer los respaldos.

Se crea una sola vez, en el computador de quien administra (no en el servidor):

```bash
age-keygen -o evolutionfit-backup.key     # muestra la llave pública en pantalla
```

**Dónde se guarda la llave privada:**
1. En el **gestor de contraseñas del dueño**, como nota segura. Es la copia principal.
2. **Impresa en papel**, en un sobre cerrado guardado con los documentos importantes del dueño, fuera del
   gimnasio. Es la copia de emergencia.
3. Se borra el archivo `evolutionfit-backup.key` del computador después de guardarla en los dos lugares.
4. Se anota en este documento **dónde** está, nunca la llave:
   - Ubicación 1: ____________
   - Ubicación 2: ____________

> **⚠ ADVERTENCIA: si se pierde la llave privada, TODOS los respaldos cifrados quedan inservibles para
> siempre.** Nadie puede recuperarlos: ni el proveedor, ni Backblaze, ni Google, ni quien hizo el sistema. Por eso
> se guarda en dos lugares distintos y la prueba mensual de restauración la usa: si un mes no aparece, se nota a
> tiempo.

Si la llave privada se filtra, se genera un par nuevo y se cambia la pública en el servidor. Los respaldos viejos
siguen abiertos con la llave filtrada, así que se borran del destino externo cuando haya suficientes respaldos
nuevos.

## Copia externa cifrada (capa 2)

### Destino

- **Recomendado: Backblaze B2.**
  - Se crea un bucket privado para los respaldos y una *application key* limitada a ese bucket **y sin permiso de
    borrar**.
  - Así, aunque alguien entre al servidor, no puede borrar las copias externas.
  - La retención la hacen las reglas de ciclo de vida del bucket: diarios 30 días y mensuales 12 meses.
- **Alternativa: Google Drive del dueño.**
  - Se configura rclone con el alcance `drive.file`, que solo ve los archivos que el propio rclone creó, no
    todo el Drive del dueño.
  - Desventaja: desde el servidor sí se podrían borrar las copias.

La configuración de rclone guarda credenciales. Va en `~/.config/rclone/rclone.conf` del usuario administrador
del servidor, con permisos 600, y nunca en el repositorio.

### Pasos diarios (después del respaldo local)

```bash
f=$(ls -1t ~/evolutionfit-backups/evolution_fit_*_{diario,semanal,mensual}.sql.gz | head -1)
age -r "$(cat ~/.config/evolutionfit/backup.pub)" -o "$f.age" "$f"     # cifra con la llave pública
rclone copy "$f.age"    remoto:evolutionfit-backups/                     # sube el cifrado
rclone copy "$f.sha256" remoto:evolutionfit-backups/                     # y su suma (no es secreta)
rm "$f.age"
```

- Se usa `rclone copy`, nunca `rclone sync`. `sync` borraría en el destino lo que la rotación local borró.
- La llave pública va en `~/.config/evolutionfit/backup.pub`.
- **Pendiente:** juntar el respaldo local y estos pasos en un script (por ejemplo, `scripts/offsite.sh`) que
  avise si algo falla. Se crea con aprobación antes de la puesta en marcha.

### Programarlo en el servidor

La hora del servidor debe estar en Bogotá (`timedatectl set-timezone America/Bogota`); si no, cron usa otra
hora. Con `crontab -e` del usuario administrador:

```
PATH=/usr/local/bin:/usr/bin:/bin
30 22 * * * cd /opt/evolutionfit && scripts/backup.sh --auto && scripts/offsite.sh >> ~/evolutionfit-backups/respaldo.log 2>&1
```

- 10:30 p.m. es justo después del cierre automático de caja de las 10:00, así el respaldo incluye el cierre del
  día.
- La línea `PATH` hace falta porque cron no encuentra `docker`, `age` ni `rclone` por su cuenta.
- Revisa `respaldo.log` cada semana, o mejor, que el script avise al dueño si falla. Un respaldo que falla en
  silencio es el riesgo más común.

## Disco externo (capa 3, mensual)

1. El primer día hábil de cada mes, quien administra descarga del destino externo el respaldo **mensual** (ya
   cifrado, `.age`) y su `.sha256`.
2. Lo copia al disco externo. Ahí se guardan los últimos 12 meses.
3. El disco se guarda **fuera del gimnasio**, por ejemplo en la casa del dueño. Nunca en el mismo lugar que el
   computador del gimnasio.

Los archivos van cifrados: si el disco se pierde, nadie puede leerlos sin la llave privada.

## Copias del proveedor (capa 4)

- Activar los backups o snapshots automáticos del VPS en el panel del proveedor.
- Tomar un snapshot manual antes de cada actualización importante y antes del día de arranque.
- Sirven para recuperar el servidor completo (sistema, Docker, configuración), pero **no reemplazan** las capas 1
  a 3. Su retención es corta, dependen de la misma cuenta y no se pueden verificar tabla por tabla.

## Verificar un respaldo (sin tocar la base real)

```bash
scripts/restore.sh ~/evolutionfit-backups/evolution_fit_AAAAMMDD_HHMMSS_diario.sql.gz
scripts/restore.sh <archivo> --compare-live    # además lo compara con la base real, tabla por tabla
```

1. Comprueba la suma SHA-256 y que el archivo no esté dañado.
2. Levanta una MySQL 8.4 temporal en `127.0.0.1:3309`, con datos en memoria y una contraseña aleatoria.
3. Carga el respaldo y muestra:
   - por cada tabla, las filas y una suma de verificación (`CHECKSUM TABLE`);
   - las migraciones de Flyway que contiene.
4. Borra la MySQL temporal, aunque algo falle.

Con `--compare-live`, si la base real no cambió desde el respaldo, deben salir "IGUALES". Si alguien registró
algo después, las diferencias en esas tablas son esperadas.

## Prueba de restauración mensual

Se hace el primer día hábil de cada mes, junto con la copia al disco externo. Toma unos 20 minutos.

1. **En el servidor**, antes de abrir el gimnasio (de noche no cambia nada después del respaldo de las 10:30
   p.m.):

   ```bash
   scripts/restore.sh ~/evolutionfit-backups/<último diario o mensual>.sql.gz --compare-live
   ```

   Debe decir "IGUALES".
2. **En el computador de quien administra** (con Docker), con la copia externa:
   - descargar el mismo respaldo `.age` y su `.sha256` del destino externo;
   - descifrarlo con la llave privada:

     ```bash
     age -d -i <llave privada> -o <archivo>.sql.gz <archivo>.sql.gz.age
     ```

   - verificarlo con `scripts/restore.sh <archivo>.sql.gz`: la suma SHA-256 debe ser correcta y las filas por
     tabla deben coincidir con el paso 1;
   - borrar del computador el archivo descifrado.
3. Anotar el resultado aquí abajo. Si algo falla, se arregla ese mismo día: sin respaldo verificado el gimnasio
   está expuesto.

| Fecha | Respaldo probado | Servidor (IGUALES) | Copia externa descifrada y verificada | Quién |
| --- | --- | --- | --- | --- |
| | | | | |

## Restaurar sobre la base real (emergencia)

Solo cuando la base real se perdió o quedó dañada, o una migración falló.

1. Detén el backend. El script se niega si el puerto 8080 está ocupado.
2. Verifica primero el respaldo que vas a usar (sección anterior). Si viene de la copia externa, descífralo antes
   con la llave privada y cópialo al servidor por SSH.
3. Ejecuta, en una terminal del servidor:

   ```bash
   scripts/restore.sh <archivo> --into-real
   ```

   - Pide escribir el nombre de la base (`evolution_fit`) para confirmar.
   - **Antes** de tocar nada hace un respaldo de la base actual con la etiqueta `pre_restore`, para poder volver
     atrás.
   - Borra la base, la vuelve a crear y carga el respaldo. Los permisos del usuario de la aplicación se
     conservan.
4. Arranca el backend: Flyway aplica las migraciones que falten.
5. Todo lo registrado después de la hora del respaldo se pierde: pagos, retiros y arqueos. Revisa con recepción
   los comprobantes y los mensajes de WhatsApp de ese intervalo, y vuelve a registrarlos.
6. Si se perdió el VPS completo, primero hay que crear uno nuevo con
   [PUESTA_EN_MARCHA.md](PUESTA_EN_MARCHA.md), o restaurar el snapshot del proveedor, y después restaurar la
   base.

## Reglas

- Nunca guardes respaldos dentro del repositorio ni los envíes por WhatsApp o correo.
- Nada sin cifrar sale del servidor.
- La llave privada de age nunca está en el servidor ni junto a los respaldos.
