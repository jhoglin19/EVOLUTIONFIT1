# Imagen web de producción: Caddy con el frontend ya compilado (el VPS no necesita Node).
# Contexto: la raíz del repositorio (docker-compose.prod.yml); .dockerignore deja entrar solo frontend/ y deploy/caddy/.

# ---- Compilación del frontend ----
FROM node:24.22.3-alpine AS build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
# "prebuild" corre scripts/check-forms.mjs: si algún <form> no tiene [formGroup], la imagen no se construye.
RUN npm run build

# ---- Caddy ----
FROM caddy:2.11.9-alpine
# Usuario sin privilegios; el binario de la imagen oficial ya tiene la capacidad de abrir los puertos 80 y 443.
RUN addgroup -S -g 10002 caddy \
 && adduser -S -D -H -u 10002 -G caddy caddy \
 && chown -R caddy:caddy /data /config
COPY deploy/caddy/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist/frontend/browser /srv
USER caddy
