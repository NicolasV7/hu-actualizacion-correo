# Imagen para ejecutar la demo en cualquier máquina con Docker, sin instalar JDK ni Node.
# Etapa 1: compila el backend y ejecuta sus pruebas (la construcción falla si alguna prueba falla).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY backend backend
RUN --mount=type=cache,target=/root/.m2 \
    cd backend && chmod +x mvnw && ./mvnw -B package

# Etapa 2: imagen de ejecución con el jar y el frontend servido por el perfil local.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend/target/*.jar app.jar
COPY frontend/src frontend
ENV SPRING_PROFILES_ACTIVE=local \
    FRONTEND_DIR=/app/frontend
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
