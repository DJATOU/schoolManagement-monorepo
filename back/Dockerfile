# =====================================================================
# Backend Spring Boot — image de production
# Contexte de build attendu : ./back
# =====================================================================

# --------- Étape 1 : compilation du JAR ---------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Le pom est copié seul dans un premier temps : tant qu'il ne change pas, Docker
# réutilise le cache et ne retélécharge pas les dépendances à chaque build.
COPY pom.xml ./
# go-offline ne résout pas toujours l'intégralité des plugins (jqwik, jacoco) et
# peut sortir en erreur alors que le projet compile parfaitement. L'échec est
# donc toléré : cette étape n'est qu'une optimisation de cache, et l'étape
# package ci-dessous récupère de toute façon ce qui manque.
RUN mvn -B -q dependency:go-offline || true

COPY src ./src

# Les tests tournent sur le poste de développement (./mvnw test), pas dans
# l'image : ils exigent H2 et rallongeraient chaque déploiement.
RUN mvn -B -q -DskipTests clean package

# --------- Étape 2 : image d'exécution ---------
# JRE et non JDK : ni compilateur ni outillage dans l'image finale, ce qui réduit
# la taille et la surface d'attaque.
FROM eclipse-temurin:21-jre

# curl sert à la sonde de disponibilité (/actuator/health) déclarée dans
# docker-compose.yml ; l'image de base ne l'embarque pas.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# Exécution sans privilèges : un défaut applicatif ne donne pas root dans le
# conteneur. Le dossier des photos doit appartenir à cet utilisateur, sinon les
# téléversements échouent en écriture.
RUN groupadd --system --gid 1001 spring \
 && useradd --system --uid 1001 --gid spring spring

WORKDIR /app

COPY --from=build /build/target/*.jar app.jar

# Point de montage des photos. Le chemin réel côté hôte est fourni par
# docker-compose (variable UPLOAD_HOST_DIR) ; rien d'absolu n'est figé ici.
RUN mkdir -p /app/uploads/images && chown -R spring:spring /app

USER spring

EXPOSE 8080

# Le conteneur reçoit sa mémoire de Docker, pas de la machine hôte :
# MaxRAMPercentage laisse la JVM s'ajuster à la limite du conteneur.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

# Aucun secret, aucune URL de base de données ici : tout arrive par
# l'environnement au démarrage (voir docker-compose.yml et .env).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
