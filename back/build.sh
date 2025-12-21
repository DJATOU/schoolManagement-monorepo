#!/bin/zsh

# Helper script to run the build with the correct Java 21 version
# This avoids the "TypeTag :: UNKNOWN" error caused by Java 25

export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home

if [ ! -d "$JAVA_HOME" ]; then
    echo "ERROR: Java 21 not found at $JAVA_HOME"
    exit 1
fi

echo "Using Java 21: $JAVA_HOME"
./mvnw "$@"
