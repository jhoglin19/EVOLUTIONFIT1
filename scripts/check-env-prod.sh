#!/usr/bin/env bash
# Valida el archivo de variables de producción ANTES de arrancar. Uso: scripts/check-env-prod.sh /opt/evolutionfit/.env.prod
#
# - No ejecuta el archivo (no hace "source"): lo lee como texto CLAVE=valor.
# - Nunca imprime valores: solo nombres de variables y longitudes mínimas.
# - Termina con error si algo bloquea el arranque; los avisos no lo bloquean.
set -euo pipefail

file="${1:-}"
[ -n "$file" ] || { echo "Uso: $0 <archivo .env.prod>" >&2; exit 2; }
[ -f "$file" ] || { echo "[env] ERROR: no existe $file" >&2; exit 1; }

errors=0
warn() { echo "[env] AVISO: $*"; }
fail() { echo "[env] ERROR: $*"; errors=$((errors + 1)); }

# Permisos 600 (Linux: stat -c; macOS: stat -f).
perm="$(stat -c '%a' "$file" 2>/dev/null || stat -f '%Lp' "$file")"
[ "$perm" = "600" ] || fail "los permisos de $file son $perm; deben ser 600 (chmod 600 $file)"

# Dentro de un repositorio git: no debe estar ahí.
if git -C "$(dirname "$file")" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  fail "$file está dentro de un repositorio git; debe vivir fuera (por ejemplo /opt/evolutionfit/.env.prod)"
fi

# Se normaliza a "CLAVE<TAB>valor" en un temporal (bash 3.2 de macOS no tiene arreglos asociativos).
parsed="$(mktemp)"; trap 'rm -f "$parsed"' EXIT
lineno=0
while IFS= read -r line || [ -n "$line" ]; do
  lineno=$((lineno + 1))
  [[ "$line" =~ ^[[:space:]]*(#|$) ]] && continue
  if [[ "$line" =~ ^([A-Z][A-Z0-9_]*)=(.*)$ ]]; then
    key="${BASH_REMATCH[1]}"; value="${BASH_REMATCH[2]}"
    value="${value%\"}"; value="${value#\"}"; value="${value%\'}"; value="${value#\'}"
    if cut -f1 "$parsed" | grep -qx "$key"; then fail "la variable $key está repetida (línea $lineno)"; fi
    printf '%s\t%s\n' "$key" "$value" >> "$parsed"
  else
    fail "la línea $lineno no tiene el formato CLAVE=valor"
  fi
done < "$file"

has() { cut -f1 "$parsed" | grep -qx "$1"; }
get() { awk -F'\t' -v k="$1" '$1 == k { sub(/^[^\t]*\t/, ""); print; exit }' "$parsed"; }

need() { # nombre longitud_mínima
  local v; v="$(get "$1")"
  if [ -z "$v" ]; then fail "falta $1"; elif [ "${#v}" -lt "$2" ]; then fail "$1 debe tener al menos $2 caracteres"; fi
}
need DOMAIN 4
need ACME_EMAIL 6
need MYSQL_ROOT_PASSWORD 20
need MYSQL_USER 3
need MYSQL_PASSWORD 20
need JWT_SECRET 32
need CORS_ALLOWED_ORIGINS 8
need BOOTSTRAP_ADMIN_USERNAME 3

domain="$(get DOMAIN)"
if [ -n "$domain" ] && [ "$(get CORS_ALLOWED_ORIGINS)" != "https://$domain" ]; then
  fail "CORS_ALLOWED_ORIGINS debe ser exactamente https://<DOMAIN>"
fi
[[ "$(get ACME_EMAIL)" == *@*.* ]] || fail "ACME_EMAIL no parece un correo"
if [ -n "$(get MYSQL_PASSWORD)" ] && [ "$(get MYSQL_PASSWORD)" = "$(get MYSQL_ROOT_PASSWORD)" ]; then
  fail "MYSQL_PASSWORD y MYSQL_ROOT_PASSWORD deben ser distintas"
fi
for k in DOMAIN ACME_EMAIL CORS_ALLOWED_ORIGINS; do
  [[ "$(get "$k")" != *example.com* ]] || fail "$k todavía tiene el valor de ejemplo"
done

# Lo que nunca va en producción.
for k in APP_E2E_ENABLED SPRING_PROFILES_ACTIVE SWAGGER_ENABLED DB_URL SERVER_PORT; do
  if has "$k"; then fail "$k no va en .env.prod (lo fija docker-compose.prod.yml o no existe en producción)"; fi
done

pw="$(get BOOTSTRAP_ADMIN_PASSWORD)"
if [ -n "$pw" ]; then
  [ "${#pw}" -ge 12 ] || fail "BOOTSTRAP_ADMIN_PASSWORD debe tener al menos 12 caracteres"
  warn "BOOTSTRAP_ADMIN_PASSWORD está definida: bórrala después del primer ingreso del ADMIN (PUESTA_EN_MARCHA, sección 8)"
fi

for k in $(cut -f1 "$parsed"); do
  case "$k" in
    E2E_*) fail "$k es de las pruebas e2e" ;;
    DOMAIN|ACME_EMAIL|MYSQL_ROOT_PASSWORD|MYSQL_USER|MYSQL_PASSWORD|JWT_SECRET|JWT_EXPIRATION_MINUTES|\
    CORS_ALLOWED_ORIGINS|BOOTSTRAP_ADMIN_*|LOGIN_MAX_FAILURES_PER_USER|LOGIN_MAX_FAILURES_PER_IP|LOGIN_WINDOW|\
    HSTS_MAX_AGE|APP_VERSION|MEMBERSHIP_*|RECEIPT_PAPER_SIZE|CASH_CLOSING_TIME|REPORTS_MAX_RANGE_DAYS|\
    APP_E2E_ENABLED|SPRING_PROFILES_ACTIVE|SWAGGER_ENABLED|DB_URL|SERVER_PORT) ;;
    *) warn "variable desconocida: $k (¿error de escritura?)" ;;
  esac
done

if [ "$errors" -gt 0 ]; then
  echo "[env] $errors problema(s): corrígelos antes de arrancar."
  exit 1
fi
echo "[env] OK: $file listo para producción."
