#!/usr/bin/env bash
# Vuelve a una versión anterior. Uso (en el VPS, desde /opt/evolutionfit/app):
#
#   scripts/rollback.sh v1.0.0                              solo el código (las imágenes de v1.0.0 ya existen)
#   scripts/rollback.sh v1.0.0 --restore ARCHIVO.sql.gz     además restaura la base desde ese respaldo
#
# Sin --restore: sirve cuando la versión nueva no tuvo migraciones, o si sus migraciones son compatibles hacia
# atrás (columnas nuevas que admiten NULL, índices). Flyway ignora en la versión vieja las migraciones que no
# conoce ("*:future"), así que arranca.
#
# Con --restore: cuando la versión nueva dejó la base incompatible. Detiene backend y Caddy, restaura el respaldo
# (restore.sh --into-real pide escribir el nombre de la base y antes hace OTRO respaldo, pre_restore) y arranca
# la versión indicada. SE PIERDE lo registrado desde ese respaldo (pagos, membresías...): anotarlo antes.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=scripts/prod-common.sh
. "$HERE/prod-common.sh"

log() { echo "[reversa] $*"; }
die() { echo "[reversa] ERROR: $*" >&2; exit 1; }

tag="${1:-}"
[ -n "$tag" ] || die "uso: $0 <etiqueta> [--restore ARCHIVO.sql.gz]"
restore=""
if [ "${2:-}" = "--restore" ]; then
  restore="${3:-}"
  [ -f "$restore" ] || die "no existe el respaldo $restore"
fi

cd "$REPO"
git rev-parse -q --verify "refs/tags/$tag" >/dev/null || die "no existe la etiqueta $tag."
export APP_VERSION="$tag"
need_build=false
for image in "evolutionfit-backend:$tag" "evolutionfit-web:$tag"; do
  docker image inspect "$image" >/dev/null 2>&1 || need_build=true
done

current="$(cat "$STATE_DIR/actual" 2>/dev/null || echo "")"
log "Volviendo de ${current:-?} a $tag..."
git checkout --quiet --detach "refs/tags/$tag"
if $need_build; then
  log "Las imágenes de $tag no están guardadas: se construyen."
  compose build --quiet
fi

if [ -n "$restore" ]; then
  mysql_container="$(env_value MYSQL_CONTAINER_NAME)"; mysql_container="${mysql_container:-evolutionfit-mysql}"
  log "Deteniendo backend y Caddy para restaurar $restore..."
  compose stop caddy backend
  [ -z "$(compose ps -q backend)" ] || [ "$(docker inspect -f '{{.State.Running}}' "$(compose ps -q backend)")" = false ] \
    || die "el backend sigue corriendo; no se restaura."
  EVOLUTIONFIT_DB_CONTAINER="$mysql_container" EVOLUTIONFIT_BACKEND_PORT=none "$HERE/restore.sh" "$restore" --into-real
fi

compose up -d --no-build --remove-orphans
"$HERE/prod-health.sh" || die "$tag no pasó la comprobación después de la reversa. Revisar: docker compose logs backend"

mkdir -p "$STATE_DIR"
echo "$tag" > "$STATE_DIR/actual"
[ -z "$current" ] || [ "$current" = "$tag" ] || echo "$current" > "$STATE_DIR/revertida"
log "Listo: $tag instalada de nuevo y comprobada."
