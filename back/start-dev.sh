#!/bin/bash

# Script de démarrage pour l'environnement de développement
# Usage: ./start-dev.sh

echo "🚀 Starting School Management in DEV mode..."

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
