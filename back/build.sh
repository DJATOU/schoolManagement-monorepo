#!/bin/bash

# Helper pour lancer Maven avec le JDK 21 (le projet cible Java 21).
# Évite l'erreur Lombok "TypeTag :: UNKNOWN" qui survient avec un JDK 17/25.
#
# Usage :
#   ./build.sh clean package          # build complet
#   ./build.sh clean compile          # compilation seule
#   ./build.sh spring-boot:run -Dspring-boot.run.profiles=dev   # lancer en dev

# 1) Détection automatique du JDK 21 (macOS)
if [ -x /usr/libexec/java_home ]; then
    DETECTED=$(/usr/libexec/java_home -v 21 2>/dev/null)
    if [ -n "$DETECTED" ]; then
        export JAVA_HOME="$DETECTED"
    fi
fi

# 2) Repli sur l'emplacement Temurin classique si non détecté
if [ -z "$JAVA_HOME" ] || [ ! -d "$JAVA_HOME" ]; then
    export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
fi

if [ ! -d "$JAVA_HOME" ]; then
    echo "❌ JDK 21 introuvable."
    echo "💡 Installe-le, par ex : brew install --cask temurin@21"
    exit 1
fi

echo "☕ Using JDK 21: $JAVA_HOME"
./mvnw "$@"
