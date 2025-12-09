#!/bin/bash

# Script de démarrage pour l'environnement de production
# Usage: ./start-prod.sh

echo "🚀 Starting School Management in PROD mode..."

# Vérifier que les variables d'environnement critiques sont définies
if [ -z "$SERVER_BASE_URL" ]; then
    echo "❌ Error: SERVER_BASE_URL environment variable is not set"
    echo "💡 Example: export SERVER_BASE_URL=https://api.votre-domaine.com"
    exit 1
fi

if [ -z "$UPLOAD_DIR" ]; then
    echo "⚠️  Warning: UPLOAD_DIR not set, using default: /var/www/school-management/uploads"
    export UPLOAD_DIR=/var/www/school-management/uploads
fi

# S'assurer que le profil prod est actif
export SPRING_PROFILES_ACTIVE=prod

# Créer le répertoire d'upload s'il n'existe pas
mkdir -p "$UPLOAD_DIR"
chmod 755 "$UPLOAD_DIR"
echo "📁 Upload directory: $UPLOAD_DIR"

# Afficher la configuration (sans afficher les secrets)
echo ""
echo "📋 Configuration:"
echo "   Profile: $SPRING_PROFILES_ACTIVE"
echo "   Server URL: $SERVER_BASE_URL"
echo "   Upload Dir: $UPLOAD_DIR"
echo "   Database: ${DB_URL:-not set}"
echo ""

# Vérifier que le JAR existe
if [ ! -f "target/school-management-0.0.1-SNAPSHOT.jar" ]; then
    echo "❌ Error: JAR file not found. Please build the project first:"
    echo "   mvn clean package"
    exit 1
fi

# Options JVM pour la production
JVM_OPTS="-Xms512m -Xmx1024m"
JVM_OPTS="$JVM_OPTS -XX:+UseG1GC"
JVM_OPTS="$JVM_OPTS -XX:MaxGCPauseMillis=200"
JVM_OPTS="$JVM_OPTS -Djava.security.egd=file:/dev/./urandom"

echo "🏃 Starting application..."
java $JVM_OPTS -jar target/school-management-0.0.1-SNAPSHOT.jar

# En cas d'arrêt
echo ""
echo "👋 Application stopped"
