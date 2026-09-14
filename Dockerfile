# --- Etapa 1: build ---------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copia so o pom primeiro para o Docker cachear as dependencias entre builds
# (so baixa tudo de novo se o pom.xml mudar).
COPY pom.xml .
RUN mvn -q dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

# --- Etapa 2: runtime ---------------------------------------------------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Usuario nao-root
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
