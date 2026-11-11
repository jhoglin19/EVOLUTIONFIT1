#!/usr/bin/env bash
# Restauración de un respaldo de EvolutionFit. Uso y procedimiento: docs/RESPALDOS.md
#
#   scripts/restore.sh ARCHIVO.sql.gz                  VERIFICA el respaldo en una MySQL temporal y la borra al terminar
#   scripts/restore.sh ARCHIVO.sql.gz --compare-live   además compara tabla por tabla con la base real
#   scripts/restore.sh ARCHIVO.sql.gz --into-real      RESTAURA sobre la base real (pide confirmación escrita)
#
# Verificar (por defecto) nunca toca la base real: levanta un contenedor MySQL 8.4 temporal en 127.0.0.1
# (puerto EVOLUTIONFIT_VERIFY_PORT, 3309 por defecto) con datos en memoria y una contraseña aleatoria que no se
# guarda ni se imprime, carga el respaldo, muestra filas y suma de verificación por tabla y las migraciones de
# Flyway, y borra el contenedor aunque algo falle.
#
# Restaurar sobre la base real:
#   - exige que el backend esté detenido (puerto 8080 libre) y que escribas el nombre de la base para confirmar;
#   - ANTES hace un respaldo automático de la base actual (etiqueta pre_restore) con scripts/backup.sh;
#   - borra y vuelve a crear la base, y carga el respaldo. Los permisos del usuario de la aplicación se conservan.
set -euo pipefail
umask 077

HERE="$(cd "$(dirname "$0")" && pwd)"
CONTAINER="${EVOLUTIONFIT_DB_CONTAINER:-evolutionfit-mysql}"
DB="${EVOLUTIONFIT_DB_NAME:-evolution_fit}"
VERIFY_PORT="${EVOLUTIONFIT_VERIFY_PORT:-3309}"
VERIFY_CONTAINER="evolutionfit-restore-check"
VERIFY_DB="evolution_fit_restore"

die() { echo "[restauración] ERROR: $*" >&2; exit 1; }
log() { echo "[restauración] $*"; }

[ $# -ge 1 ] || { sed -n '2,18p' "$0"; exit 1; }
file="$1"; shift
mode="verify"
compare=false
while [ $# -gt 0 ]; do
  case "$1" in
    --compare-live) compare=true ;;
    --into-real) mode="real" ;;
    *) die "opción desconocida: $1" ;;
  esac
  shift
done

[ -f "$file" ] || die "no existe el archivo $file."
case "$file" in *.sql.gz) reader=(gzip -dc) ;; *.sql) reader=(cat) ;; *) die "se espera un archivo .sql.gz o .sql." ;; esac

if [ -f "$file.sha256" ]; then
  ( cd "$(dirname "$file")" && shasum -a 256 -c "$(basename "$file").sha256" >/dev/null ) \
    || die "la suma SHA-256 no coincide: el archivo cambió o está dañado."
  log "Suma SHA-256 correcta."
else
  log "AVISO: $file no tiene archivo .sha256; no se puede comprobar su integridad."
fi
if [ "${reader[0]}" = gzip ]; then gzip -t "$file" || die "el archivo comprimido está dañado."; fi

# Consulta de filas y suma de verificación por tabla (CHECKSUM TABLE), en una base de un contenedor.
SUMMARY_SQL='SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = "BASE TABLE" ORDER BY table_name'
summary() { # contenedor base
  local c="$1" d="$2" t
  for t in $(docker exec "$c" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -B "$1" -e "$2"' sh "$d" "$SUMMARY_SQL"); do
    docker exec "$c" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -B "$1" -e "SELECT \"$2\", COUNT(*) FROM \`$2\`; CHECKSUM TABLE \`$2\`;"' sh "$d" "$t" \
      | paste - - | awk -v OFS='\t' '{print $1, $2, $4}'
  done
}

if [ "$mode" = "verify" ]; then
  command -v docker >/dev/null || die "no se encontró docker."
  if nc -z 127.0.0.1 "$VERIFY_PORT" 2>/dev/null; then die "el puerto $VERIFY_PORT está ocupado (EVOLUTIONFIT_VERIFY_PORT)."; fi
  docker rm -f "$VERIFY_CONTAINER" >/dev/null 2>&1 || true
  cleanup() { docker rm -f -v "$VERIFY_CONTAINER" >/dev/null 2>&1 || true; }
  trap cleanup EXIT

  log "MySQL temporal de verificación en 127.0.0.1:$VERIFY_PORT (datos en memoria)..."
  # La contraseña va por el entorno del comando, no en sus argumentos (no aparece en la lista de procesos).
  MYSQL_ROOT_PASSWORD="$(openssl rand -hex 16)" docker run -d --name "$VERIFY_CONTAINER" \
    -e MYSQL_ROOT_PASSWORD -e MYSQL_DATABASE="$VERIFY_DB" -e TZ=America/Bogota \
    -p "127.0.0.1:$VERIFY_PORT:3306" --tmpfs /var/lib/mysql:rw \
    mysql:8.4 --default-time-zone=-05:00 >/dev/null
  # Lista solo cuando acepta conexiones TCP: el servidor provisional de la inicialización no las acepta.
  for _ in $(seq 1 90); do
    if docker exec "$VERIFY_CONTAINER" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -h127.0.0.1 -e "SELECT 1"' >/dev/null 2>&1; then
      ready=1; break
    fi
    sleep 2
  done
  [ "${ready:-0}" = 1 ] || die "la MySQL temporal no arrancó."

  log "Cargando el respaldo..."
  "${reader[@]}" "$file" | docker exec -i "$VERIFY_CONTAINER" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$1"' sh "$VERIFY_DB" \
    || die "MySQL rechazó el respaldo."

  restored="$(summary "$VERIFY_CONTAINER" "$VERIFY_DB")"
  [ -n "$restored" ] || die "la base restaurada no tiene tablas."
  echo
  echo "Tabla	Filas	Suma de verificación"
  echo "$restored"
  echo
  log "Migraciones de Flyway en el respaldo:"
  docker exec "$VERIFY_CONTAINER" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -B "$1" -e "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank"' sh "$VERIFY_DB" \
    | awk -F'\t' '{printf "  V%s %s (%s)\n", $1, $2, ($3 == 1 ? "ok" : "FALLIDA")}'

  if $compare; then
    echo
    log "Comparando con la base real $DB del contenedor $CONTAINER (si alguien la usó después del respaldo, habrá diferencias esperadas)..."
    live="$(summary "$CONTAINER" "$DB")"
    if diff <(echo "$restored") <(echo "$live") >/dev/null; then
      log "IGUALES: mismas tablas, mismas filas y misma suma de verificación en todas."
    else
      log "DIFERENCIAS (< respaldo restaurado, > base real):"
      diff <(echo "$restored") <(echo "$live") || true
      exit 2
    fi
  fi
  echo
  log "El respaldo se restauró completo. Se borra la MySQL temporal."
  exit 0
fi

# ---- Restaurar sobre la base real ----
docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null | grep -q true || die "el contenedor $CONTAINER no está corriendo."
if nc -z 127.0.0.1 8080 2>/dev/null; then die "el backend parece estar corriendo (puerto 8080). Detenlo antes de restaurar."; fi
[ -t 0 ] || die "la restauración sobre la base real solo se hace en una terminal interactiva."

echo "Vas a REEMPLAZAR toda la base '$DB' del contenedor '$CONTAINER' por el contenido de:"
echo "  $file"
echo "Antes se hará un respaldo de la base actual. Escribe el nombre de la base para confirmar:"
read -r answer
[ "$answer" = "$DB" ] || die "confirmación incorrecta; no se hizo nada."

log "Respaldo previo de la base actual..."
"$HERE/backup.sh" --label pre_restore

log "Restaurando..."
docker exec "$CONTAINER" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -e "DROP DATABASE \`$1\`; CREATE DATABASE \`$1\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"' sh "$DB"
"${reader[@]}" "$file" | docker exec -i "$CONTAINER" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$1"' sh "$DB" \
  || die "MySQL rechazó el respaldo. La base quedó incompleta: restaura el respaldo pre_restore que se acaba de hacer."
log "Restaurado. Filas por tabla:"
summary "$CONTAINER" "$DB" | awk -F'\t' '{printf "  %-24s %s\n", $1, $2}'
log "Arranca el backend: Flyway aplicará las migraciones que falten."
