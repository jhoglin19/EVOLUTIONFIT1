#!/usr/bin/env bash
# Ensayo de PRODUCCIÓN en este Mac, antes de tocar un VPS. Uso, con Docker encendido:
#
#   scripts/prod-smoke.sh
#
# Levanta docker-compose.prod.yml completo (MySQL, backend con el perfil prod, Caddy con HTTPS) como el proyecto
# "evolutionfit-prodlocal", en https://evolutionfit.localhost:8443 (el certificado lo emite la CA interna de
# Caddy: "tls internal"). No toca la base de desarrollo: tiene su propio volumen.
#
# Comprueba, punto por punto: HTTPS válido y redirección desde HTTP; cabeceras de seguridad y compresión;
# rutas de Angular; caché; rutas internas en 404; que MySQL y el backend no publican puertos ni salen a internet;
# que nada corre como root; sistema de archivos de solo lectura; rotación de logs y reinicio automático; el
# flujo del ADMIN inicial (cambio de su contraseña); la IP real del cliente con el Caddy real (dos clientes con
# IPs distintas y cuentas distintas, con un X-Forwarded-For inventado); Playwright contra la app (login,
# Clientes, PDF, sin errores de CSP, fuentes ni imágenes); y un respaldo con su restauración de verificación y su
# copia externa cifrada a un destino local.
#
# Secretos: aleatorios, en una carpeta temporal (700) que se borra al terminar; nunca se imprimen.
# Al terminar (bien o mal) se borra todo: contenedores, redes, volúmenes, imágenes del ensayo y la carpeta temporal.
set -uo pipefail
umask 077

REPO="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
DOMAIN=evolutionfit.localhost
PORT=8443
URL="https://$DOMAIN:$PORT"
PROJECT=evolutionfit-prodlocal
MYSQL_CONTAINER=$PROJECT-mysql
SIM_NET=evolutionfit-prodlocal-internet
CURL_IMAGE=curlimages/curl:8.23.0
passed=0
failed=0

ok() { passed=$((passed + 1)); echo "  ✓ $*"; }
bad() { failed=$((failed + 1)); echo "  ✗ $*"; }
check() { # descripción, comando...
  local what="$1"; shift
  if "$@" >/dev/null 2>&1; then ok "$what"; else bad "$what"; fi
}
section() { echo; echo "== $*"; }
rnd() { openssl rand -hex "$1"; }

cleanup() {
  section "Limpieza"
  docker network disconnect -f "$SIM_NET" "$PROJECT-caddy-1" >/dev/null 2>&1
  docker network rm "$SIM_NET" >/dev/null 2>&1
  compose down -v --remove-orphans >/dev/null 2>&1
  docker image rm "evolutionfit-backend:prodlocal" "evolutionfit-web:prodlocal" >/dev/null 2>&1
  rm -rf "$TMP"
  echo "  Contenedores, redes, volúmenes, imágenes del ensayo y secretos temporales borrados."
}
trap cleanup EXIT

# ---- Variables de la simulación (secretos aleatorios, nunca impresos) ----
ADMIN_PW="Inicial-$(rnd 8)"
ADMIN_PW2="Cambiada-$(rnd 8)"
cat > "$TMP/env.prod" <<EOF
DOMAIN=$DOMAIN
ACME_EMAIL=pruebas@$DOMAIN
MYSQL_ROOT_PASSWORD=$(rnd 16)
MYSQL_USER=evolution_app
MYSQL_PASSWORD=$(rnd 16)
JWT_SECRET=$(rnd 32)
CORS_ALLOWED_ORIGINS=https://$DOMAIN
BOOTSTRAP_ADMIN_USERNAME=admin
BOOTSTRAP_ADMIN_PASSWORD=$ADMIN_PW
EOF
chmod 600 "$TMP/env.prod"
cat > "$TMP/local.env" <<EOF
COMPOSE_PROJECT_NAME=$PROJECT
MYSQL_CONTAINER_NAME=$MYSQL_CONTAINER
CADDY_TLS=tls internal
HTTP_PORT=8088
HTTPS_PORT=$PORT
APP_VERSION=prodlocal
# Solo aquí: el navegador manda el origen con el puerto 8443 (en el VPS es https://<dominio>, puerto 443).
CORS_ALLOWED_ORIGINS=https://$DOMAIN,$URL
EOF
export EVOLUTIONFIT_ENV_FILE="$TMP/env.prod" EVOLUTIONFIT_EXTRA_ENV_FILE="$TMP/local.env" \
  EVOLUTIONFIT_STATE_DIR="$TMP/estado" EVOLUTIONFIT_PUBLIC_URL="$URL" \
  EVOLUTIONFIT_CURL_EXTRA="--resolve $DOMAIN:$PORT:127.0.0.1 --cacert $TMP/root.crt"
# shellcheck source=scripts/prod-common.sh
. "$REPO/scripts/prod-common.sh"
c() { curl --resolve "$DOMAIN:$PORT:127.0.0.1" --cacert "$TMP/root.crt" -s --max-time 20 "$@"; }
code() { c -o /dev/null -w '%{http_code}' "$@"; }
api() { # método ruta token [cuerpo]
  c -X "$1" -H "Authorization: Bearer $3" -H 'Content-Type: application/json' ${4:+--data "$4"} "$URL/api$2"
}
login_token() { c -H 'Content-Type: application/json' -d "{\"identifier\":\"$1\",\"password\":\"$2\"}" "$URL/api/auth/login" \
  | python3 -I -c 'import sys,json; print(json.load(sys.stdin)["token"])'; }

section "Variables y arranque"
check "check-env-prod.sh acepta el .env.prod de la simulación" "$REPO/scripts/check-env-prod.sh" "$TMP/env.prod"
docker ps -a --format '{{.Names}}' | grep -q "^$PROJECT" && { echo "Ya existe una pila $PROJECT: bórrala primero."; exit 1; }
compose build --quiet || { bad "construir las imágenes"; exit 1; }
compose up -d >/dev/null 2>&1 || { bad "levantar la pila"; exit 1; }
for _ in $(seq 1 60); do
  docker exec "$PROJECT-caddy-1" cat /data/caddy/pki/authorities/local/root.crt > "$TMP/root.crt" 2>/dev/null && break
  sleep 3
done
[ -s "$TMP/root.crt" ] || { bad "Caddy no generó su CA interna"; exit 1; }
if "$REPO/scripts/prod-health.sh"; then ok "prod-health.sh: servicios sanos, app por HTTPS, login 401, Swagger 404"; else bad "prod-health.sh"; exit 1; fi

section "HTTPS, cabeceras y archivos"
check "certificado válido para $DOMAIN (verificado contra la CA, sin -k)" c -f -o /dev/null "$URL/"
redirect="$(curl -s -o /dev/null -w '%{http_code} %{redirect_url}' --resolve "$DOMAIN:8088:127.0.0.1" "http://$DOMAIN:8088/clients")"
[[ "$redirect" == "308 https://$DOMAIN/clients" ]] && ok "HTTP redirige a HTTPS (308)" || bad "redirección HTTP→HTTPS: $redirect"
headers="$(c -D - -o /dev/null -H 'Accept-Encoding: zstd, gzip' "$URL/" | tr -d '\r' | tr 'A-Z' 'a-z')"
for h in "strict-transport-security: max-age=86400" "x-content-type-options: nosniff" "x-frame-options: deny" \
         "referrer-policy: strict-origin-when-cross-origin" "content-security-policy: default-src 'self'; script-src 'self';" \
         "permissions-policy: camera=()" "cache-control: no-cache"; do
  [[ "$headers" == *"$h"* ]] && ok "cabecera $h" || bad "falta la cabecera $h"
done
[[ "$headers" == *"content-encoding: zstd"* || "$headers" == *"content-encoding: gzip"* ]] && ok "respuesta comprimida" || bad "sin compresión"
[[ "$headers" != *"server:"* ]] && ok "sin cabecera Server" || bad "la respuesta trae Server"
main="$(c "$URL/" | grep -oE 'main-[A-Za-z0-9_-]+\.js' | head -1)"
[[ "$(c -D - -o /dev/null "$URL/$main" | tr -d '\r' | grep -i '^cache-control')" == *"immutable"* ]] \
  && ok "$main con caché de un año" || bad "$main sin caché larga"
[ "$(c "$URL/" | grep -c '<script>')" = 0 ] && ok "index.html sin scripts en línea" || bad "index.html con scripts en línea"
[[ "$(c "$URL/clients/123")" == *"<app-root"* ]] && ok "ruta de Angular (/clients/123) devuelve la app" || bad "ruta de Angular"
health="$(c "$URL/api/health")"
[ "$health" = '{"status":"UP"}' ] && ok "/api/health (aviso externo de caída) responde {\"status\":\"UP\"} sin detalles" || bad "/api/health: $health"
for p in /actuator/health /actuator/env /v3/api-docs /swagger-ui.html /swagger-ui/index.html; do
  [ "$(code "$URL$p")" = 404 ] && ok "$p responde 404" || bad "$p no responde 404"
done

section "Aislamiento y endurecimiento"
for s in mysql backend; do
  bindings="$(docker inspect -f '{{json .HostConfig.PortBindings}}' "$(compose ps -q $s)")"
  [ "$bindings" = "{}" ] && ok "$s no publica puertos" || bad "$s publica puertos: $bindings"
done
if docker exec "$PROJECT-backend-1" curl -s -m 5 -o /dev/null https://example.com; then bad "el backend sale a internet"; else ok "el backend no sale a internet (red interna)"; fi
[ "$(docker exec "$PROJECT-backend-1" id -u)" = 10001 ] && ok "backend corre como 10001" || bad "backend no corre como 10001"
[ "$(docker exec "$PROJECT-caddy-1" id -u)" = 10002 ] && ok "Caddy corre como 10002" || bad "Caddy no corre como 10002"
mysqld="$(docker exec "$MYSQL_CONTAINER" sh -c 'cat /proc/1/comm; awk "/^Uid:/{print \$2}" /proc/1/status' | tr '\n' ' ')"
[[ "$mysqld" == "mysqld "* && "$mysqld" != *" 0 " ]] && ok "mysqld corre sin root (uid ${mysqld#mysqld })" || bad "mysqld: $mysqld"
if docker exec "$PROJECT-backend-1" touch /app/x 2>/dev/null; then bad "el backend puede escribir en /app"; else ok "backend con sistema de archivos de solo lectura"; fi
for s in mysql backend caddy; do
  id="$(compose ps -q $s)"
  lc="$(docker inspect -f '{{.HostConfig.LogConfig.Type}} {{index .HostConfig.LogConfig.Config "max-size"}} {{index .HostConfig.LogConfig.Config "max-file"}} {{.HostConfig.RestartPolicy.Name}}' "$id")"
  [ "$lc" = "json-file 10m 5 unless-stopped" ] && ok "$s: logs 10m×5 y restart unless-stopped" || bad "$s: $lc"
done
before="$(docker inspect -f '{{.RestartCount}}' "$PROJECT-backend-1")"
docker exec "$PROJECT-backend-1" kill -TERM 1 >/dev/null 2>&1
for _ in $(seq 1 60); do
  [ "$(docker inspect -f '{{.RestartCount}} {{.State.Health.Status}}' "$PROJECT-backend-1")" = "$((before + 1)) healthy" ] && break
  sleep 5
done
[ "$(docker inspect -f '{{.RestartCount}} {{.State.Health.Status}}' "$PROJECT-backend-1")" = "$((before + 1)) healthy" ] \
  && ok "el backend se cayó y Docker lo reinició solo (sano otra vez)" || bad "el backend no se reinició solo"

section "ADMIN inicial"
token="$(login_token admin "$ADMIN_PW")" && [ -n "$token" ] && ok "el ADMIN entra con BOOTSTRAP_ADMIN_PASSWORD" || bad "el ADMIN inicial no entra"
st="$(c -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
  -d "{\"currentPassword\":\"$ADMIN_PW\",\"newPassword\":\"$ADMIN_PW2\"}" "$URL/api/auth/change-password")"
[ "$st" = 204 ] && ok "cambia su contraseña (204)" || bad "cambio de contraseña: $st"
[ "$(code -H "Authorization: Bearer $token" "$URL/api/auth/me")" = 401 ] && ok "su sesión anterior queda cerrada (401)" || bad "la sesión anterior sigue abierta"
old_login="$(code -H 'Content-Type: application/json' -d "{\"identifier\":\"admin\",\"password\":\"$ADMIN_PW\"}" "$URL/api/auth/login")"
[ "$old_login" = 401 ] && ok "la contraseña inicial ya no entra" || bad "la contraseña inicial respondió $old_login"
token="$(login_token admin "$ADMIN_PW2")" && [ -n "$token" ] && ok "la nueva sí entra" || bad "la nueva contraseña no entra"

section "Datos para las pruebas (por la API)"
today="$(TZ=America/Bogota date +%F)"
api POST /plans "$token" '{"name":"Mensual Ensayo","description":"Ensayo de producción","durationDays":30,"price":90000}' >/dev/null
plan="$(api GET /plans "$token" | python3 -I -c 'import sys,json; print(json.load(sys.stdin)["content"][0]["id"])')"
api PUT /cash/settings "$token" "{\"openingBase\":50000,\"startDate\":\"$today\",\"openDays\":[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\",\"SATURDAY\",\"SUNDAY\"],\"closedOnHolidays\":false}" >/dev/null
for u in a b c; do
  api POST /users "$token" "{\"username\":\"ensayo_$u\",\"password\":\"Cuenta-$u-$ADMIN_PW2\",\"firstName\":\"Ensayo\",\"lastName\":\"Cuenta $u\",\"role\":\"RECEPTIONIST\"}" >/dev/null
done
doc="9$(date +%s | tail -c 9)"
enrollment="$(api POST /enrollments "$token" "{\"client\":{\"documentType\":\"CC\",\"documentNumber\":\"$doc\",\"firstName\":\"Cliente\",\"lastName\":\"Ensayo\",\"phone\":\"3001234567\"},\"planId\":$plan,\"payment\":{\"requestId\":\"$(uuidgen | tr A-Z a-z)\",\"method\":\"CASH\",\"expectedAmount\":90000}}")"
[[ "$enrollment" == *'"receiptNumber"'* ]] && ok "inscripción con pago y comprobante" || bad "no se pudo registrar la inscripción: ${enrollment:0:200}"

section "IP real del cliente detrás del Caddy real (red que simula internet: 203.0.113.0/24)"
docker network create --subnet 203.0.113.0/24 "$SIM_NET" >/dev/null
docker network connect --ip 203.0.113.2 "$SIM_NET" "$PROJECT-caddy-1"
client() { # ip cuenta clave [x-forwarded-for]
  docker run --rm --network "$SIM_NET" --ip "$1" -v "$TMP/root.crt:/root.crt:ro" "$CURL_IMAGE" \
    -s -o /dev/null -w '%{http_code}' --resolve "$DOMAIN:443:203.0.113.2" --cacert /root.crt \
    -H 'Content-Type: application/json' ${4:+-H "X-Forwarded-For: $4"} \
    -d "{\"identifier\":\"$2\",\"password\":\"$3\"}" "https://$DOMAIN/api/auth/login"
}
codes=""
for i in 1 2 3 4 5; do codes="$codes$(client 203.0.113.10 ensayo_a "Errada-$i" "198.51.100.$i") "; done
[ "$codes" = "401 401 401 401 401 " ] && ok "cliente A (203.0.113.10): 5 fallos con la cuenta A, inventando X-Forwarded-For" || bad "fallos del cliente A: $codes"
[ "$(client 203.0.113.10 ensayo_c "Cuenta-c-$ADMIN_PW2" "198.51.100.99")" = 429 ] \
  && ok "la IP de A queda bloqueada: ni otra cuenta (C) con su clave correcta entra, aunque invente otra IP" || bad "la IP de A no quedó bloqueada"
[ "$(client 203.0.113.11 ensayo_b "Cuenta-b-$ADMIN_PW2")" = 200 ] \
  && ok "cliente B (203.0.113.11) entra con su propia cuenta (B): contador de IP distinto" || bad "el cliente B no pudo entrar"
logs="$(compose logs backend 2>/dev/null)"
[[ "$logs" == *"usuario=ensayo_a ip=203.0.113.10"* ]] && ok "el backend registra la IP real 203.0.113.10" || bad "el backend no registró la IP real"
[[ "$logs" != *"ip=198.51.100."* ]] && ok "ninguna IP inventada llegó al backend" || bad "una IP inventada llegó al backend"
[[ "$logs" != *"Errada-"* && "$logs" != *"$ADMIN_PW2"* ]] && ok "ninguna contraseña en los logs del backend" || bad "hay contraseñas en los logs"
[[ "$(compose logs caddy 2>/dev/null)" == *'"remote_ip":"203.0.113.10"'* ]] && ok "Caddy registra remote_ip 203.0.113.10" || bad "Caddy no registró la IP del cliente A"

section "Navegador (Playwright) contra la app en producción"
if (cd "$REPO/e2e" && E2E_PROD_URL="$URL" E2E_PROD_USER=admin E2E_PROD_PASSWORD="$ADMIN_PW2" \
      E2E_PROD_CLIENT_NAME="Cliente Ensayo" npx playwright test -c playwright.prod.config.ts > "$TMP/playwright.log" 2>&1); then
  ok "login, Clientes y PDF del comprobante, sin errores de CSP, fuentes ni imágenes"
else
  bad "Playwright:"; sed 's/^/      /' "$TMP/playwright.log" | tail -30
fi

section "Respaldo, verificación y copia externa"
mkdir -p "$TMP/respaldos" "$TMP/remoto" "$TMP/rclone" "$TMP/llaves"
if EVOLUTIONFIT_BACKUP_DIR="$TMP/respaldos" EVOLUTIONFIT_DB_CONTAINER="$MYSQL_CONTAINER" "$REPO/scripts/backup.sh" --label ensayo > "$TMP/backup.log" 2>&1; then
  ok "backup.sh sobre la base de la simulación"
  dump="$(ls -1 "$TMP"/respaldos/*.sql.gz | head -1)"
  if EVOLUTIONFIT_DB_CONTAINER="$MYSQL_CONTAINER" "$REPO/scripts/restore.sh" "$dump" > "$TMP/restore.log" 2>&1 \
      && grep -q "V8 users password changed at (ok)" "$TMP/restore.log"; then
    ok "restore.sh lo restaura en una MySQL temporal (V1–V8 presentes)"
  else bad "restore.sh no pudo verificar el respaldo"; fi
  docker run --rm --network none "evolutionfit-age:alpine-3.24.4" age-keygen > "$TMP/llaves/privada.txt" 2>/dev/null \
    || { printf 'FROM alpine:3.24.4\nRUN apk add --no-cache age\nUSER 65534:65534\n' | docker build -q -t evolutionfit-age:alpine-3.24.4 - >/dev/null \
         && docker run --rm --network none "evolutionfit-age:alpine-3.24.4" age-keygen > "$TMP/llaves/privada.txt" 2>/dev/null; }
  grep -o 'age1[0-9a-z]*' "$TMP/llaves/privada.txt" > "$TMP/llaves/backup.pub"
  printf '[ensayo]\ntype = local\n' > "$TMP/rclone/rclone.conf"
  if EVOLUTIONFIT_BACKUP_DIR="$TMP/respaldos" EVOLUTIONFIT_AGE_RECIPIENT="$TMP/llaves/backup.pub" \
      EVOLUTIONFIT_RCLONE_CONFIG="$TMP/rclone" EVOLUTIONFIT_OFFSITE_REMOTE=ensayo:/remote/diarios \
      EVOLUTIONFIT_OFFSITE_TEST_DIR="$TMP/remoto" "$REPO/scripts/offsite.sh" > "$TMP/offsite.log" 2>&1; then
    name="$(basename "$dump")"
    docker run --rm -i --network none -v "$TMP/llaves/privada.txt:/k.txt:ro" "evolutionfit-age:alpine-3.24.4" \
      age -d -i /k.txt < "$TMP/remoto/diarios/$name.age" > "$TMP/descifrado.sql.gz"
    [ "$(shasum -a 256 "$TMP/descifrado.sql.gz" | cut -d' ' -f1)" = "$(cut -d' ' -f1 < "$dump.sha256")" ] \
      && ok "offsite.sh: cifrado, subido, verificado; descifrado desde el destino es idéntico" || bad "el descifrado no coincide con el respaldo"
  else bad "offsite.sh falló: $(tail -1 "$TMP/offsite.log")"; fi
else bad "backup.sh falló: $(tail -1 "$TMP/backup.log")"; fi

section "Resultado"
echo "  $passed comprobaciones correctas, $failed fallidas."
[ "$failed" -eq 0 ]
