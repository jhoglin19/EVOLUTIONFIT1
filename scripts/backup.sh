#!/usr/bin/env bash
# Respaldo de la base de EvolutionFit. Uso y procedimiento: docs/RESPALDOS.md
#
#   scripts/backup.sh                  respaldo manual (etiqueta "manual")
#   scripts/backup.sh --label pre_V8   respaldo manual con etiqueta (antes de una migración, de una actualización...)
#   scripts/backup.sh --auto           respaldo programado: diario, semanal (domingo) o mensual (día 1), con rotación
#
# - Se ejecuta mysqldump DENTRO del contenedor de MySQL con la contraseña que el contenedor ya tiene en su entorno:
#   este script no lee .env ni recibe, imprime o escribe contraseñas.
# - El archivo queda comprimido, con permisos 600, y con su suma SHA-256 al lado (.sha256).
# - Si el volcado queda incompleto o sospechosamente pequeño, se borra y el script termina con error.
# - La rotación (--auto) solo borra respaldos automáticos (diario/semanal/mensual); nunca los manuales.
#
# Variables opcionales: EVOLUTIONFIT_BACKUP_DIR (por defecto ~/evolutionfit-backups),
# EVOLUTIONFIT_DB_CONTAINER (evolutionfit-mysql), EVOLUTIONFIT_DB_NAME (evolution_fit).
set -euo pipefail
umask 077

BACKUP_DIR="${EVOLUTIONFIT_BACKUP_DIR:-$HOME/evolutionfit-backups}"
CONTAINER="${EVOLUTIONFIT_DB_CONTAINER:-evolutionfit-mysql}"
DB="${EVOLUTIONFIT_DB_NAME:-evolution_fit}"
KEEP_DAILY=14
KEEP_WEEKLY=8
KEEP_MONTHLY=12
MIN_BYTES=10000 # comprimido; la base vacía (solo esquema) ya supera este tamaño

die() { echo "[respaldo] ERROR: $*" >&2; exit 1; }
log() { echo "[respaldo] $*"; }

label="manual"
auto=false
while [ $# -gt 0 ]; do
  case "$1" in
    --auto) auto=true ;;
    --label) shift; label="${1:-}" ;;
    -h|--help) sed -n '2,17p' "$0"; exit 0 ;;
    *) die "opción desconocida: $1" ;;
  esac
  shift
done
[[ "$label" =~ ^[A-Za-z0-9_-]{1,40}$ ]] || die "la etiqueta solo admite letras, números, _ y - (máximo 40)."

if $auto; then
  if [ "$(date +%d)" = "01" ]; then label="mensual"
  elif [ "$(date +%u)" = "7" ]; then label="semanal"
  else label="diario"; fi
fi

command -v docker >/dev/null || die "no se encontró docker."
docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null | grep -q true || die "el contenedor $CONTAINER no está corriendo."

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

stamp="$(date +%Y%m%d_%H%M%S)"
file="$BACKUP_DIR/${DB}_${stamp}_${label}.sql.gz"
partial="$file.partial"
trap 'rm -f "$partial"' EXIT

log "Respaldando $DB del contenedor $CONTAINER..."
# --single-transaction: copia consistente sin bloquear a recepción (todas las tablas son InnoDB).
# --hex-blob: el logo del comprobante (binario) se guarda en hexadecimal y no se corrompe.
# Sin --databases: el archivo se puede restaurar en una base con otro nombre (la de verificación).
if ! docker exec "$CONTAINER" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot \
    --single-transaction --quick --hex-blob --routines --triggers --events \
    --set-gtid-purged=OFF --no-tablespaces --default-character-set=utf8mb4 "$1"' sh "$DB" \
  | gzip -9 > "$partial"; then
  die "mysqldump falló; no se guardó ningún respaldo."
fi

gzip -t "$partial" || die "el archivo comprimido está dañado."
gzip -dc "$partial" | tail -n 1 | grep -q '^-- Dump completed' || die "el volcado no terminó (falta '-- Dump completed')."
gzip -dc "$partial" | grep -q 'CREATE TABLE `payments`' || die "el volcado no contiene la tabla payments."
size=$(wc -c < "$partial" | tr -d ' ')
[ "$size" -ge "$MIN_BYTES" ] || die "el respaldo pesa solo $size bytes; se descarta."

mv "$partial" "$file"
chmod 600 "$file"
( cd "$BACKUP_DIR" && shasum -a 256 "$(basename "$file")" > "$(basename "$file").sha256" )
chmod 600 "$file.sha256"
trap - EXIT
log "Listo: $file ($((size / 1024)) KB)."

rotate() {
  local kind="$1" keep="$2"
  # Solo respaldos automáticos de esta base, del más nuevo al más viejo.
  { ls -1t "$BACKUP_DIR"/"${DB}"_????????_??????_"$kind".sql.gz 2>/dev/null || true; } | tail -n +"$((keep + 1))" | while read -r old; do
    rm -f "$old" "$old.sha256"
    log "Rotación: se borró $(basename "$old")."
  done
}
if $auto; then
  rotate diario "$KEEP_DAILY"
  rotate semanal "$KEEP_WEEKLY"
  rotate mensual "$KEEP_MONTHLY"
fi
