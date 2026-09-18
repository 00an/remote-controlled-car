# Build configuration
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Build installations
COPY pom.xml .
COPY src ./src

# Build
RUN mvn clean package -DskipTests


# Run configuration
FROM eclipse-temurin:21-jre
WORKDIR /app
EXPOSE 8080

# Run installation
COPY --from=build /app/target/*.jar app.jar

# Run
ENTRYPOINT ["java", "-jar", "app.jar"]
