#!/usr/bin/env bash
# Comprueba que la pila de producción quedó sana. Lo usan deploy.sh y rollback.sh; también sirve a mano.
#
#   scripts/prod-health.sh            (con las mismas variables que deploy.sh)
#
# 1. Espera (hasta EVOLUTIONFIT_HEALTH_TIMEOUT segundos, 300 por defecto) a que mysql, backend y caddy estén
#    "healthy" según sus chequeos de docker compose.
# 2. Por HTTPS, a través de Caddy: / responde 200 con la app; /api/health responde {"status":"UP"}; un login
#    errado responde 401 (el backend contesta y la base responde); /v3/api-docs responde 404 (Swagger apagado).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=scripts/prod-common.sh
. "$HERE/prod-common.sh"

timeout="${EVOLUTIONFIT_HEALTH_TIMEOUT:-300}"
deadline=$(( $(date +%s) + timeout ))
while :; do
  states="$(compose ps --format '{{.Service}}={{.Health}}' | sort | tr '\n' ' ')"
  case "$states" in
    *backend=healthy*caddy=healthy*mysql=healthy*) break ;;
  esac
  [ "$(date +%s)" -lt "$deadline" ] || { echo "[salud] ERROR: los servicios no quedaron sanos en ${timeout}s: $states" >&2; exit 1; }
  sleep 5
done
echo "[salud] Servicios sanos: $states"

code() { curl "${CURL_ARGS[@]}" -s -o /dev/null -w '%{http_code}' "$@"; }
home="$(curl "${CURL_ARGS[@]}" -s "$PUBLIC_URL/")" || { echo "[salud] ERROR: no responde $PUBLIC_URL" >&2; exit 1; }
[[ "$home" == *"<app-root"* ]] || { echo "[salud] ERROR: $PUBLIC_URL/ no devuelve la aplicación" >&2; exit 1; }
login="$(code -H 'Content-Type: application/json' -d '{"identifier":"chequeo_de_salud","password":"no-es-una-clave"}' "$PUBLIC_URL/api/auth/login")"
[ "$login" = "401" ] || { echo "[salud] ERROR: un login errado respondió $login (se esperaba 401)" >&2; exit 1; }
health="$(curl "${CURL_ARGS[@]}" -s "$PUBLIC_URL/api/health")"
[ "$health" = '{"status":"UP"}' ] || { echo "[salud] ERROR: /api/health respondió: $health" >&2; exit 1; }
docs="$(code "$PUBLIC_URL/v3/api-docs")"
[ "$docs" = "404" ] || { echo "[salud] ERROR: /v3/api-docs respondió $docs (Swagger debe estar apagado)" >&2; exit 1; }
echo "[salud] OK por HTTPS en $PUBLIC_URL: la app carga, /api/health está UP, un login errado da 401 y Swagger está apagado."
