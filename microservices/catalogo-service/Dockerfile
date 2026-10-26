# ─── Etapa 1: Construcción ───────────────────────────────────────────────────
FROM maven:3.9.5-eclipse-temurin-17 AS builder

WORKDIR /app

# Copiar pom.xml primero para aprovechar caché de capas de Maven
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiar el código fuente y compilar
COPY src ./src
RUN mvn clean package -DskipTests -B

# ─── Etapa 2: Imagen final liviana ───────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copiar solo el JAR generado
COPY --from=builder /app/target/catalogo-service-1.0.0.jar app.jar

# Variables de entorno por defecto
ENV SERVER_PORT=8080
ENV CATALOGO_DELAY_MS=0

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
