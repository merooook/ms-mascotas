FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/ms-mascotas-0.0.1-SNAPSHOT.jar app.jar
# usuario sin privilegios: si alguien compromete el proceso, no es root dentro del contenedor
RUN groupadd --system app && useradd --system --gid app --uid 10001 app
USER app
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]