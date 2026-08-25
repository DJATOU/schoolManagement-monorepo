#!/bin/bash

# Script de démarrage pour l'environnement de développement
# Usage: ./start-dev.sh

echo "🚀 Starting School Management in DEV mode..."

# S'assurer d'utiliser un JDK 21 (le projet cible Java 21).
# Lombok plante avec un JDK plus ancien lors d'un build forké.
if [ -x /usr/libexec/java_home ]; then
    JAVA21_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null)
    if [ -n "$JAVA21_HOME" ]; then
        export JAVA_HOME="$JAVA21_HOME"
        echo "☕ Using JDK 21: $JAVA_HOME"
    else
        echo "⚠️  JDK 21 introuvable. Le build peut échouer (le projet cible Java 21)."
        echo "💡 Installe-le, par ex: brew install --cask temurin@21"
    fi
fi

# Charger les variables d'environnement depuis .env si le fichier existe
if [ -f .env ]; then
    echo "📝 Loading environment variables from .env"
    export $(cat .env | grep -v '^#' | xargs)
else
    echo "⚠️  No .env file found. Using default values."
    echo "💡 Tip: Copy .env.example to .env and customize it"
fi

# S'assurer que le profil dev est actif
export SPRING_PROFILES_ACTIVE=dev

# Créer le répertoire d'upload s'il n'existe pas
UPLOAD_DIR=${UPLOAD_DIR:-./uploads/images}
mkdir -p "$UPLOAD_DIR"
echo "📁 Upload directory: $UPLOAD_DIR"

# Afficher la configuration
echo ""
echo "📋 Configuration:"
echo "   Profile: $SPRING_PROFILES_ACTIVE"
echo "   Server URL: ${SERVER_BASE_URL:-http://localhost:8080}"
echo "   Upload Dir: $UPLOAD_DIR"
echo ""

# Lancer l'application
if [ -f "target/school-management-0.0.1-SNAPSHOT.jar" ]; then
    echo "🏃 Running from JAR..."
    java -jar target/school-management-0.0.1-SNAPSHOT.jar
elif [ -f "pom.xml" ]; then
    echo "🏃 Running with Maven..."
    ./mvnw spring-boot:run
else
    echo "❌ Error: No JAR file or pom.xml found"
    exit 1
fi
