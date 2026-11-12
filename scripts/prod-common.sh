# Configuración común de deploy.sh, rollback.sh y prod-health.sh (se carga con ".", no se ejecuta).
#
# Variables (todas con valor por defecto para el VPS):
#   EVOLUTIONFIT_ENV_FILE        archivo de variables de producción (/opt/evolutionfit/.env.prod)
#   EVOLUTIONFIT_EXTRA_ENV_FILE  solo la prueba en el Mac: ajustes locales (proyecto, puertos, tls internal)
#   EVOLUTIONFIT_STATE_DIR       dónde se anotan la versión actual y la anterior (/opt/evolutionfit/estado)
#   EVOLUTIONFIT_PUBLIC_URL      URL pública (por defecto https://<DOMAIN del .env.prod>)
#   EVOLUTIONFIT_CURL_EXTRA      solo la prueba en el Mac: opciones extra de curl (--resolve, --cacert)
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${EVOLUTIONFIT_ENV_FILE:-/opt/evolutionfit/.env.prod}"
EXTRA_ENV_FILE="${EVOLUTIONFIT_EXTRA_ENV_FILE:-}"
STATE_DIR="${EVOLUTIONFIT_STATE_DIR:-/opt/evolutionfit/estado}"

env_value() { # lee una variable del .env.prod sin ejecutarlo
  local v=""
  for f in "$ENV_FILE" ${EXTRA_ENV_FILE:+"$EXTRA_ENV_FILE"}; do
    local line; line="$(grep -E "^$1=" "$f" 2>/dev/null | tail -1 || true)"
    [ -z "$line" ] || v="${line#*=}"
  done
  printf '%s' "$v"
}

compose() {
  local args=(-f "$REPO/docker-compose.prod.yml" --env-file "$ENV_FILE")
  [ -z "$EXTRA_ENV_FILE" ] || args+=(--env-file "$EXTRA_ENV_FILE")
  (cd "$REPO" && docker compose "${args[@]}" "$@")
}

PUBLIC_URL="${EVOLUTIONFIT_PUBLIC_URL:-https://$(env_value DOMAIN)}"
CURL_ARGS=(--max-time 20)
# shellcheck disable=SC2206
[ -z "${EVOLUTIONFIT_CURL_EXTRA:-}" ] || CURL_ARGS+=(${EVOLUTIONFIT_CURL_EXTRA})
