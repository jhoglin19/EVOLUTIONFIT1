# Respaldos de la base de datos

Procedimiento para respaldar y restaurar la base de EvolutionFit (MySQL 8.4 en Docker, contenedor
`evolutionfit-mysql`, base `evolution_fit`). Los scripts están en `scripts/` y se ejecutan desde la raíz del
proyecto.

## Qué se respalda

Toda la base `evolution_fit`: clientes, membresías, pagos, comprobantes, caja, auditoría, usuarios y la
configuración (incluido el logo del comprobante). El historial de Flyway va incluido, así que al restaurar el
backend sabe en qué migración está.

No se respaldan aquí: el archivo `.env` (guárdalo aparte, en un gestor de contraseñas) ni el código (está en Git).

## Hacer un respaldo

```bash
scripts/backup.sh                    # respaldo manual
scripts/backup.sh --label pre_V8     # antes de una migración, de una actualización o de la puesta en marcha
```

- Queda en `~/evolutionfit-backups/` (carpeta con permisos 700) como
  `evolution_fit_AAAAMMDD_HHMMSS_<etiqueta>.sql.gz`, con permisos 600 y su suma SHA-256 en un archivo `.sha256`.
- La copia es consistente aunque recepción esté trabajando (`--single-transaction`); no hay que detener nada.
- El script usa la contraseña que el contenedor ya tiene en su entorno: no lee `.env` ni imprime contraseñas.
- Si el volcado queda incompleto, dañado o sospechosamente pequeño, se descarta y el script termina con error.

## Verificar un respaldo (sin tocar la base real)

```bash
scripts/restore.sh ~/evolutionfit-backups/evolution_fit_AAAAMMDD_HHMMSS_manual.sql.gz
scripts/restore.sh <archivo> --compare-live    # además lo compara con la base real, tabla por tabla
```

1. Comprueba la suma SHA-256 y que el archivo no esté dañado.
2. Levanta una MySQL 8.4 temporal en `127.0.0.1:3309`, con datos en memoria y una contraseña aleatoria.
3. Carga el respaldo y muestra, por tabla, las filas y una suma de verificación (`CHECKSUM TABLE`), y las
   migraciones de Flyway que contiene.
4. Borra la MySQL temporal, aunque algo falle.

Con `--compare-live`, si la base real no cambió desde el respaldo, deben salir "IGUALES". Si alguien registró
algo después, las diferencias en esas tablas son esperadas.

**Un respaldo que nunca se ha restaurado no es un respaldo.** Verifica uno al menos una vez al mes y siempre
antes de una migración.

## Restaurar sobre la base real (emergencia)

Solo cuando la base real se perdió o quedó dañada, o una migración falló.

1. Detén el backend (el script se niega si el puerto 8080 está ocupado).
2. Verifica primero el respaldo que vas a usar (sección anterior).
3. Ejecuta, en una terminal:

   ```bash
   scripts/restore.sh <archivo> --into-real
   ```

   - Pide escribir el nombre de la base (`evolution_fit`) para confirmar.
   - **Antes** de tocar nada hace un respaldo de la base actual con la etiqueta `pre_restore`, para poder volver
     atrás.
   - Borra y vuelve a crear la base, y carga el respaldo. Los permisos del usuario de la aplicación se conservan.
4. Arranca el backend: Flyway aplica las migraciones que falten.
5. Todo lo registrado después de la hora del respaldo se pierde (pagos, retiros, arqueos). Revisa con recepción
   los comprobantes físicos o los mensajes de WhatsApp de ese intervalo y vuelve a registrarlos.

## Frecuencia recomendada

| Cuándo | Cómo | Se conservan |
| --- | --- | --- |
| Todos los días a las 10:30 p.m. (después del cierre de caja de las 10:00) | `scripts/backup.sh --auto` programado | 14 diarios |
| Domingos (lo hace el mismo programado) | `--auto` lo etiqueta "semanal" | 8 semanales |
| Día 1 de cada mes (lo hace el mismo programado) | `--auto` lo etiqueta "mensual" | 12 mensuales |
| Antes de cada migración, actualización o la puesta en marcha | `scripts/backup.sh --label <motivo>` | Para siempre (no se rotan) |

La rotación solo borra respaldos automáticos (`diario`, `semanal`, `mensual`) de la carpeta; los manuales y los
`pre_restore` se conservan hasta que alguien los borre a mano.

### Programarlo

Dónde corre producción está por definir; esto aplica al equipo donde esté el contenedor de MySQL. Con
`crontab -e` (macOS o Linux):

```
PATH=/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin
30 22 * * * cd /ruta/a/evolution-fit && scripts/backup.sh --auto >> ~/evolutionfit-backups/respaldo.log 2>&1
```

- La línea `PATH` hace falta porque cron no encuentra `docker` por su cuenta.
- El equipo debe estar encendido a esa hora; si estaba apagado, ese día no hay respaldo automático.
- Revisa `respaldo.log` cada semana: un respaldo que falla en silencio es el riesgo más común.

## Dónde guardar las copias

Regla 3-2-1: tres copias, en dos medios distintos, una fuera del gimnasio.

1. **Local:** `~/evolutionfit-backups/` en el equipo donde corre la base (restauración rápida).
2. **Disco externo:** copia semanal de la carpeta.
3. **Fuera del gimnasio:** una nube o un disco que se guarda en otro lugar, **siempre cifrada**. Los respaldos
   contienen cédulas, celulares y pagos de clientes. Por ejemplo, con `age` o `gpg`:

   ```bash
   gpg --symmetric --cipher-algo AES256 evolution_fit_AAAAMMDD_HHMMSS_semanal.sql.gz
   ```

   La clave de cifrado va en un gestor de contraseñas, no junto a los respaldos.

Nunca guardes respaldos dentro del repositorio ni los envíes por WhatsApp o correo sin cifrar.
