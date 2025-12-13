# --------- STAGE 1 : build du JAR ---------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# On est dans un monorepo, le contexte de build est la racine du repo.
# On copie donc le module "back" dans /app
COPY back/pom.xml ./pom.xml
COPY back/src ./src

# Build du JAR (on zappe les tests pour accélérer)
RUN mvn -q -DskipTests clean package


# --------- STAGE 2 : image finale ---------
FROM eclipse-temurin:21-jdk

WORKDIR /app

# On copie le JAR généré depuis le stage 'build'
COPY --from=build /app/target/*.jar app.jar

# Spring Boot écoute sur 8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
