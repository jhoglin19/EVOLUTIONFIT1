# Imagen del backend para producción (docker-compose.prod.yml) y para el perfil "full" de docker-compose.yml.
# No lleva secretos: todo llega por variables de entorno al arrancar (.env.prod, fuera del repositorio).

# ---- Compilación ----
FROM maven:3.10.0-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
# Las pruebas corren antes de desplegar (./mvnw test con Docker); aquí solo se empaqueta.
RUN mvn -q -B -DskipTests package && cp target/evolutionfit-backend-*.jar /build/app.jar

# ---- Ejecución ----
FROM eclipse-temurin:21.0.13_9-jre-noble
# curl solo para el chequeo de salud del compose. Usuario sin privilegios con UID fijo.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system --gid 10001 evolutionfit \
 && useradd --system --uid 10001 --gid evolutionfit --no-create-home --shell /usr/sbin/nologin evolutionfit
WORKDIR /app
# El jar pertenece a root y es de solo lectura para el usuario de la aplicación.
COPY --from=build --chown=root:root --chmod=0444 /build/app.jar /app/app.jar
ENV TZ=America/Bogota \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp"
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
