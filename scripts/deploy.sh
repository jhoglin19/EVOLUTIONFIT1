#!/usr/bin/env bash
# Instala una versión en producción. Uso (en el VPS, desde /opt/evolutionfit/app):
#
#   scripts/deploy.sh v1.0.1
#
# 1. Valida .env.prod (check-env-prod.sh) y que el repositorio no tenga cambios sin commit.
# 2. Si la base ya existe: respaldo "pre_<versión>" con backup.sh y su verificación con restore.sh.
# 3. git checkout de la etiqueta (se instalan etiquetas, nunca main a medias).
# 4. Construye las imágenes con la etiqueta como versión (evolutionfit-backend:<v>, evolutionfit-web:<v>) y
#    recrea los servicios. Las imágenes anteriores quedan guardadas para la reversa.
# 5. prod-health.sh: servicios sanos y comprobación por HTTPS.
# 6. Anota la versión instalada y la anterior en EVOLUTIONFIT_STATE_DIR.
#
# Si falla después del paso 3, NO vuelve atrás solo: muestra el comando de reversa (scripts/rollback.sh).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=scripts/prod-common.sh
. "$HERE/prod-common.sh"

log() { echo "[despliegue] $*"; }
die() { echo "[despliegue] ERROR: $*" >&2; exit 1; }

tag="${1:-}"
[ -n "$tag" ] || die "uso: $0 <etiqueta>, por ejemplo $0 v1.0.1"
[[ "$tag" =~ ^[A-Za-z0-9._-]{1,40}$ ]] || die "etiqueta no válida: $tag"

"$HERE/check-env-prod.sh" "$ENV_FILE" || die "corrige $ENV_FILE antes de desplegar."
cd "$REPO"
[ -z "$(git status --porcelain)" ] || die "el repositorio tiene cambios sin commit; en el servidor no se edita código."
git fetch --tags --quiet 2>/dev/null || log "AVISO: no se pudo consultar el remoto; se usan las etiquetas locales."
git rev-parse -q --verify "refs/tags/$tag" >/dev/null || die "no existe la etiqueta $tag."

mkdir -p "$STATE_DIR"
current="$(cat "$STATE_DIR/actual" 2>/dev/null || echo "")"
[ "$current" != "$tag" ] || log "AVISO: $tag ya es la versión instalada; se vuelve a instalar."

# Respaldo previo (solo si la base ya existe; en la primera instalación no hay nada que respaldar).
mysql_container="$(env_value MYSQL_CONTAINER_NAME)"; mysql_container="${mysql_container:-evolutionfit-mysql}"
if docker inspect -f '{{.State.Running}}' "$mysql_container" 2>/dev/null | grep -q true; then
  label="pre_$(printf '%s' "$tag" | tr '.' '_')"
  EVOLUTIONFIT_DB_CONTAINER="$mysql_container" "$HERE/backup.sh" --label "$label" | tee "$STATE_DIR/ultimo-respaldo.log"
  file="$(grep -oE '/[^ ]+\.sql\.gz' "$STATE_DIR/ultimo-respaldo.log" | tail -1)"
  [ -f "$file" ] || die "no se encontró el respaldo recién hecho."
  EVOLUTIONFIT_DB_CONTAINER="$mysql_container" "$HERE/restore.sh" "$file" > "$STATE_DIR/ultima-verificacion.log" 2>&1 \
    || die "el respaldo $file no se pudo verificar (ver $STATE_DIR/ultima-verificacion.log). No se desplegó nada."
  log "Respaldo previo verificado: $file"
  echo "$file" > "$STATE_DIR/respaldo-$tag"
else
  log "La base todavía no existe (primera instalación): no hay respaldo previo."
fi

log "Instalando $tag (antes: ${current:-ninguna})..."
git checkout --quiet --detach "refs/tags/$tag"
export APP_VERSION="$tag"
compose build --quiet || die "no se pudieron construir las imágenes de $tag. Nada cambió en producción; vuelve a la versión anterior con: git checkout ${current:-<etiqueta>}"
compose up -d --remove-orphans

if ! "$HERE/prod-health.sh"; then
  echo
  die "$tag quedó instalada pero NO pasó la comprobación. Para volver a ${current:-la versión anterior}:
  scripts/rollback.sh ${current:-<etiqueta anterior>}
Si $tag aplicó una migración incompatible, con el respaldo previo:
  scripts/rollback.sh ${current:-<etiqueta anterior>} --restore $(cat "$STATE_DIR/respaldo-$tag" 2>/dev/null || echo '<respaldo pre_>')"
fi

[ -z "$current" ] || [ "$current" = "$tag" ] || echo "$current" > "$STATE_DIR/anterior"
echo "$tag" > "$STATE_DIR/actual"
log "Listo: $tag instalada y comprobada (anterior: ${current:-ninguna})."
