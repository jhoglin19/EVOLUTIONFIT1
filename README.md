# Evolution Fit

Sistema web para administrar un gimnasio: clientes, planes, membresías, pagos, caja y control de acceso.
Arquitectura de tres capas (Angular → API REST Spring Boot → MySQL), como monolito modular.

## Tecnologías

| Capa | Tecnología |
| --- | --- |
| Backend | Java 21, Spring Boot 3.3.5, Spring Security + JWT, Spring Data JPA/Hibernate, Flyway, Swagger/OpenAPI |
| Frontend | Angular 22, Angular Material, Reactive Forms, RxJS |
| Base de datos | MySQL 8.4 |
| Infraestructura | Docker, Docker Compose |

## Estructura

```
backend/    API REST (Maven). Código en com.evolutionfit.{config,security,common,modules.*}
frontend/   Aplicación Angular (core, shared, layout, features)
docker-compose.yml   MySQL + backend
.env.example         Variables de entorno (copiar a .env)
```

## Estado actual

| Fase | Módulo | Estado |
| --- | --- | --- |
| 1 | Estructura, Docker, Flyway, Swagger, manejo global de errores | Hecho |
| 2 | Autenticación JWT, usuarios y roles (backend + login/guards/interceptor/usuarios en Angular) | Hecho |
| 3 | Clientes y planes | Pendiente |
| 4 | Membresías y congelaciones | Pendiente |
| 5 | Pagos y comprobantes PDF | Pendiente |
| 6 | Caja | Pendiente |
| 7 | Control de acceso | Pendiente |
| 8 | Notificaciones, dashboard y reportes | Pendiente |

## Cómo ejecutarlo

Requisitos: Java 21, Node 22+, Docker. El proyecto incluye el wrapper de Maven (`mvnw`), no hace falta instalar Maven.

### 1. Configuración (una sola vez)

```bash
cp .env.example .env      # si todavía no existe .env
```

Completa `.env`: las contraseñas de MySQL deben ser **las mismas con las que ya existe tu base**,
`JWT_SECRET` se genera con `openssl rand -base64 48`, y `BOOTSTRAP_ADMIN_PASSWORD` es la contraseña
que tendrá el primer administrador (solo se usa si la tabla `users` está vacía).
El backend lee `.env` automáticamente; no hace falta exportar variables. **Nunca subas `.env` a Git.**

### 2. Base de datos (MySQL en Docker, puerto 3306)

```bash
docker compose up -d
```

Es el mismo contenedor y volumen de siempre. Flyway detecta que la base ya existe y la toma como
línea base: **no la modifica**. En una base vacía crea todo el esquema con `V1__baseline_schema.sql`.

### 3. Backend (desde `backend/`)

```bash
./mvnw spring-boot:run     # API en http://localhost:8080
./mvnw test                # pruebas unitarias
```

### 4. Frontend (desde `frontend/`, en otra terminal)

```bash
npm install
npm start                  # http://localhost:4200 (redirige /api al backend en :8080)
npm test -- --watch=false
```

Opcional: backend también en contenedor con `docker compose --profile full up --build`.

## API

- Swagger UI: http://localhost:8080/swagger-ui.html
- Salud: http://localhost:8080/actuator/health

| Método | Ruta | Acceso |
| --- | --- | --- |
| POST | `/api/auth/login` | Público |
| GET | `/api/auth/me` | Autenticado |
| POST | `/api/auth/change-password` | Autenticado |
| GET/POST | `/api/users` | Administrador |
| GET/PUT | `/api/users/{id}` | Administrador |
| PATCH | `/api/users/{id}/status` | Administrador |
| PUT | `/api/users/{id}/password` | Administrador |
| GET | `/api/roles` | Administrador |

## Seguridad

- Contraseñas con BCrypt; el login acepta usuario o correo.
- JWT firmado (HS256). Se valida el usuario contra la base en cada petición: desactivar a alguien tiene efecto inmediato.
- `JWT_SECRET`, credenciales de BD y del administrador inicial vienen **solo de variables de entorno** (`.env` está en `.gitignore`).
- Los roles se validan en el backend con `@PreAuthorize`; los guards de Angular son solo comodidad de navegación.
- Roles esperados en la tabla `roles`: `ADMIN` y `RECEPTIONIST`.

## Pruebas

Las pruebas del backend son unitarias y **no necesitan MySQL**. Los comandos están en "Cómo ejecutarlo".
