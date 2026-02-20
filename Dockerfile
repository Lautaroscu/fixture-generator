# ETAPA 1: Construcción
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline

COPY src ./src
RUN ./mvnw clean package -DskipTests

# ETAPA 2: Ejecución (Acá estaba el error, ahora es jammy)
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# 1. Crear el usuario y el grupo (Sintaxis de Debian/Ubuntu)
RUN groupadd -r spring && useradd -r -g spring spring

# 2. Crear la carpeta de datos y darle permisos al usuario spring
RUN mkdir -p /app/data && chown -R spring:spring /app/data

# 3. Cambiar al usuario spring ANTES de copiar y ejecutar
USER spring:spring

# 4. Copiar el jar
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
