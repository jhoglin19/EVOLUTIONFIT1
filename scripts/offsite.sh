#!/usr/bin/env bash
# Copia externa cifrada de un respaldo (capa 2 de docs/RESPALDOS.md). Uso:
#
#   scripts/offsite.sh                    cifra y sube el respaldo local más reciente
#   scripts/offsite.sh ARCHIVO.sql.gz     cifra y sube ese respaldo
#
# Pasos: verifica la suma SHA-256 del respaldo, lo cifra con age usando SOLO la llave pública, lo sube con
# rclone (B2 recomendado; Google Drive como opción), comprueba en el destino el tamaño y la suma (descargándolo)
# y borra el cifrado local. Si algo falla termina con error, y opcionalmente avisa a un monitor externo.
#
# age y rclone corren en contenedores de Docker efímeros: no hay que instalarlos en el servidor.
#
# Variables:
#   EVOLUTIONFIT_OFFSITE_REMOTE   obligatoria. Destino de rclone, p. ej. "b2:evolutionfit-backups/diarios" o
#                                 "gdrive:evolutionfit-backups".
#   EVOLUTIONFIT_BACKUP_DIR       carpeta de los respaldos locales (por defecto ~/evolutionfit-backups).
#   EVOLUTIONFIT_AGE_RECIPIENT    archivo con la llave PÚBLICA de age (por defecto ~/.config/evolutionfit/backup.pub).
#   EVOLUTIONFIT_RCLONE_CONFIG    carpeta con rclone.conf (por defecto ~/.config/rclone).
#   EVOLUTIONFIT_OFFSITE_PING_URL opcional: URL de un monitor (p. ej. healthchecks.io); se llama con /fail si falla.
#   EVOLUTIONFIT_OFFSITE_TEST_DIR solo pruebas: carpeta local que se monta en /remote para un destino "local".
set -euo pipefail
umask 077

BACKUP_DIR="${EVOLUTIONFIT_BACKUP_DIR:-$HOME/evolutionfit-backups}"
RECIPIENT="${EVOLUTIONFIT_AGE_RECIPIENT:-$HOME/.config/evolutionfit/backup.pub}"
RCLONE_CONFIG_DIR="${EVOLUTIONFIT_RCLONE_CONFIG:-$HOME/.config/rclone}"
REMOTE="${EVOLUTIONFIT_OFFSITE_REMOTE:-}"
PING_URL="${EVOLUTIONFIT_OFFSITE_PING_URL:-}"
TEST_DIR="${EVOLUTIONFIT_OFFSITE_TEST_DIR:-}"
AGE_IMAGE="evolutionfit-age:alpine-3.24.4"
RCLONE_IMAGE="rclone/rclone:1.76.0"

log() { echo "[externa] $(date '+%F %T') $*"; }
ping_monitor() { [ -z "$PING_URL" ] || curl -fsS -m 10 --retry 3 -o /dev/null "$PING_URL$1" || true; }
die() { echo "[externa] $(date '+%F %T') ERROR: $*" >&2; ping_monitor /fail; exit 1; }

sha() { # suma SHA-256 de un archivo (Linux: sha256sum; macOS: shasum)
  if command -v sha256sum >/dev/null; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi
}

[ -n "$REMOTE" ] || die "falta EVOLUTIONFIT_OFFSITE_REMOTE (por ejemplo b2:evolutionfit-backups/diarios)."
command -v docker >/dev/null || die "no se encontró docker."
[ -f "$RECIPIENT" ] || die "no existe la llave pública de age: $RECIPIENT"
grep -q "AGE-SECRET-KEY" "$RECIPIENT" && die "$RECIPIENT contiene una llave PRIVADA. En el servidor solo va la pública."
grep -qE '^age1[0-9a-z]{58}$' "$RECIPIENT" || die "$RECIPIENT no parece una llave pública de age (age1...)."
[ -f "$RCLONE_CONFIG_DIR/rclone.conf" ] || die "no existe $RCLONE_CONFIG_DIR/rclone.conf (configurar con: rclone config)."

# Respaldo a subir: el indicado o el más reciente.
file="${1:-}"
if [ -z "$file" ]; then
  file="$(ls -1t "$BACKUP_DIR"/*.sql.gz 2>/dev/null | head -1 || true)"
  [ -n "$file" ] || die "no hay respaldos en $BACKUP_DIR."
fi
[ -f "$file" ] || die "no existe $file."
[ -f "$file.sha256" ] || die "falta $file.sha256 (lo crea scripts/backup.sh)."
name="$(basename "$file")"
expected="$(cut -d' ' -f1 < "$file.sha256")"
[ "$(sha "$file")" = "$expected" ] || die "la suma SHA-256 de $name no coincide: el respaldo local está dañado."
log "Respaldo $name verificado."

# Imagen de age (Alpine con el paquete age), se construye la primera vez.
if ! docker image inspect "$AGE_IMAGE" >/dev/null 2>&1; then
  log "Construyendo la imagen $AGE_IMAGE..."
  printf 'FROM alpine:3.24.4\nRUN apk add --no-cache age\nUSER 65534:65534\n' \
    | docker build -q -t "$AGE_IMAGE" - >/dev/null || die "no se pudo construir la imagen de age."
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
chmod 755 "$work" # el contenedor de rclone corre con otro usuario y debe poder leer
encrypted="$work/$name.age"

# Cifrado: sin red, sistema de archivos de solo lectura; el respaldo entra por stdin y sale cifrado por stdout.
docker run --rm -q -i --network none --read-only -v "$RECIPIENT:/recipient.pub:ro" "$AGE_IMAGE" \
  age -R /recipient.pub < "$file" > "$encrypted" || die "no se pudo cifrar $name."
[ -s "$encrypted" ] || die "el cifrado de $name quedó vacío."
age_sum="$(sha "$encrypted")"
printf '%s  %s\n' "$age_sum" "$name.age" > "$encrypted.sha256"
cp "$file.sha256" "$work/$name.sha256"
chmod 644 "$work"/*
log "Cifrado: $name.age ($(wc -c < "$encrypted" | tr -d ' ') bytes)."

rclone() { # rclone en contenedor, con su configuración y la carpeta de trabajo
  local mounts=(-v "$RCLONE_CONFIG_DIR:/config/rclone" -v "$work:/data:ro")
  [ -z "$TEST_DIR" ] || mounts+=(-v "$TEST_DIR:/remote")
  docker run --rm -q "${mounts[@]}" --user "$(id -u):$(id -g)" -e XDG_CACHE_HOME=/tmp "$RCLONE_IMAGE" "$@"
}

# Si ya se subió antes (p. ej. un reintento), no se reemplaza: se comprueba que lo que hay en el destino
# coincide con su propia suma y se termina sin volver a subir (age cifra distinto cada vez).
if [ -n "$(rclone lsf "$REMOTE/$name.age" 2>/dev/null | tr -d '\r')" ]; then
  rclone cat "$REMOTE/$name.age.sha256" > "$work/remoto.sha256" || die "$name.age ya está en el destino pero falta su .sha256."
  rclone cat "$REMOTE/$name.age" > "$work/descargado.age" || die "no se pudo descargar $name.age para verificarlo."
  [ "$(sha "$work/descargado.age")" = "$(cut -d' ' -f1 < "$work/remoto.sha256")" ] \
    || die "$name.age ya estaba en el destino y NO coincide con su suma: revisar a mano."
  log "$name.age ya estaba en $REMOTE y se verificó; no se vuelve a subir."
  ping_monitor ""
  exit 0
fi

# Subida: copy (nunca sync, que borraría en el destino lo que la rotación local borró). --immutable: si ya existe
# un archivo con ese nombre y distinto contenido, falla en vez de reemplazarlo.
rclone copy --immutable "/data/$name.age" "$REMOTE/" || die "no se pudo subir $name.age."
rclone copy --immutable "/data/$name.age.sha256" "$REMOTE/" || die "no se pudo subir $name.age.sha256."
rclone copy --immutable "/data/$name.sha256" "$REMOTE/" || die "no se pudo subir $name.sha256."

# Verificación en el destino: el tamaño y la suma del archivo descargado.
remote_size="$(rclone lsf --format s "$REMOTE/$name.age" | tr -d '\r')"
[ "$remote_size" = "$(wc -c < "$encrypted" | tr -d ' ')" ] \
  || die "el tamaño de $name.age en el destino ($remote_size) no coincide con el local."
rclone cat "$REMOTE/$name.age" > "$work/descargado.age" || die "no se pudo descargar $name.age para verificarlo."
[ "$(sha "$work/descargado.age")" = "$age_sum" ] || die "la suma de $name.age descargado no coincide con la subida."

log "Subido y verificado en $REMOTE: $name.age (suma $age_sum)."
ping_monitor ""
