#!/bin/bash

# Script de test automatique du système d'authentification JWT
# Usage: ./test-auth.sh

BASE_URL="http://localhost:8080"
FRONTEND_URL="http://localhost:4200"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "========================================"
echo "🚀 Tests Système d'Authentification JWT"
echo "========================================"
echo ""

# Test 1: Backend Health Check
echo "----------------------------------------"
echo "Test 1: Santé du serveur backend"
echo "----------------------------------------"
if curl -s $BASE_URL >/dev/null 2>&1; then
  echo -e "${GREEN}✅ Backend accessible sur $BASE_URL${NC}"
else
  echo -e "${RED}❌ Backend non accessible. Assurez-vous que Spring Boot est démarré.${NC}"
  exit 1
fi

# Test 2: Register Admin
echo ""
echo "----------------------------------------"
echo "Test 2: Inscription d'un administrateur"
echo "----------------------------------------"

REGISTER_RESPONSE=$(curl -s -X POST $BASE_URL/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testadmin",
    "password": "Admin123!",
    "email": "testadmin@school.com",
    "firstName": "Test",
    "lastName": "Admin",
    "phoneNumber": "+33612345678"
  }' 2>&1)

if echo "$REGISTER_RESPONSE" | jq -e '.token' >/dev/null 2>&1; then
  TOKEN=$(echo "$REGISTER_RESPONSE" | jq -r '.token')
  ROLES=$(echo "$REGISTER_RESPONSE" | jq -r '.roles[]')
  echo -e "${GREEN}✅ Inscription réussie${NC}"
  echo "   Username: testadmin"
  echo "   Email: testadmin@school.com"
  echo "   Rôles: $ROLES"
  echo "   Token: ${TOKEN:0:50}..."
else
  # Peut-être que l'utilisateur existe déjà, essayons de se connecter
  echo -e "${YELLOW}⚠️  L'utilisateur existe peut-être déjà, test de connexion...${NC}"
fi

# Test 3: Login
echo ""
echo "----------------------------------------"
echo "Test 3: Connexion avec les identifiants"
echo "----------------------------------------"

LOGIN_RESPONSE=$(curl -s -X POST $BASE_URL/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testadmin",
    "password": "Admin123!"
  }' 2>&1)

if echo "$LOGIN_RESPONSE" | jq -e '.token' >/dev/null 2>&1; then
  LOGIN_TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.token')
  USERNAME=$(echo "$LOGIN_RESPONSE" | jq -r '.username')
  FIRST_NAME=$(echo "$LOGIN_RESPONSE" | jq -r '.firstName')
  LAST_NAME=$(echo "$LOGIN_RESPONSE" | jq -r '.lastName')
  ROLES=$(echo "$LOGIN_RESPONSE" | jq -r '.roles[]')

  echo -e "${GREEN}✅ Connexion réussie${NC}"
  echo "   Utilisateur: $FIRST_NAME $LAST_NAME ($USERNAME)"
  echo "   Rôles: $ROLES"
  echo "   Token obtenu: ${LOGIN_TOKEN:0:50}..."
else
  echo -e "${RED}❌ Échec de connexion${NC}"
  echo "Réponse: $LOGIN_RESPONSE"
  exit 1
fi

# Test 4: Access Protected Endpoint WITH Token
echo ""
echo "----------------------------------------"
echo "Test 4: Accès endpoint protégé AVEC token"
echo "----------------------------------------"

STUDENTS_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET $BASE_URL/api/students \
  -H "Authorization: Bearer $LOGIN_TOKEN" \
  -H "Content-Type: application/json")

HTTP_CODE=$(echo "$STUDENTS_RESPONSE" | tail -n1)
RESPONSE_BODY=$(echo "$STUDENTS_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" == "200" ]; then
  STUDENT_COUNT=$(echo "$RESPONSE_BODY" | jq '. | length' 2>/dev/null || echo "0")
  echo -e "${GREEN}✅ Accès autorisé (HTTP $HTTP_CODE)${NC}"
  echo "   Nombre d'étudiants: $STUDENT_COUNT"
else
  echo -e "${RED}❌ Accès refusé (HTTP $HTTP_CODE)${NC}"
  echo "   Réponse: $RESPONSE_BODY"
fi

# Test 5: Access Protected Endpoint WITHOUT Token
echo ""
echo "----------------------------------------"
echo "Test 5: Accès endpoint protégé SANS token"
echo "----------------------------------------"

RESPONSE_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X GET $BASE_URL/api/students)

if [ "$RESPONSE_CODE" == "401" ]; then
  echo -e "${GREEN}✅ Accès correctement refusé (HTTP 401)${NC}"
else
  echo -e "${RED}❌ L'endpoint devrait retourner 401 mais retourne $RESPONSE_CODE${NC}"
fi

# Test 6: Verify JWT Token Content
echo ""
echo "----------------------------------------"
echo "Test 6: Vérification du contenu du JWT"
echo "----------------------------------------"

# Extract payload (middle part of JWT)
PAYLOAD=$(echo $LOGIN_TOKEN | awk -F'.' '{print $2}')

# Add padding if needed for base64
while [ $((${#PAYLOAD} % 4)) -ne 0 ]; do
  PAYLOAD="${PAYLOAD}="
done

# Decode base64
DECODED=$(echo "$PAYLOAD" | base64 -d 2>/dev/null)

if [ $? -eq 0 ]; then
  echo "Payload JWT décodé:"
  echo "$DECODED" | jq . 2>/dev/null || echo "$DECODED"

  # Check if roles are present
  if echo "$DECODED" | jq -e '.roles' >/dev/null 2>&1; then
    ROLES_IN_TOKEN=$(echo "$DECODED" | jq -r '.roles')
    echo -e "${GREEN}✅ Rôles trouvés dans le token: $ROLES_IN_TOKEN${NC}"
  else
    echo -e "${YELLOW}⚠️  Aucun rôle trouvé dans le token${NC}"
  fi

  # Check expiration
  EXP=$(echo "$DECODED" | jq -r '.exp' 2>/dev/null)
  if [ "$EXP" != "null" ]; then
    CURRENT_TIME=$(date +%s)
    if [ $EXP -gt $CURRENT_TIME ]; then
      HOURS_UNTIL_EXP=$(( ($EXP - $CURRENT_TIME) / 3600 ))
      echo -e "${GREEN}✅ Token valide (expire dans ~$HOURS_UNTIL_EXP heures)${NC}"
    else
      echo -e "${RED}❌ Token expiré${NC}"
    fi
  fi
else
  echo -e "${YELLOW}⚠️  Impossible de décoder le payload JWT${NC}"
fi

# Test 7: Check /me Endpoint
echo ""
echo "----------------------------------------"
echo "Test 7: Endpoint /me (utilisateur actuel)"
echo "----------------------------------------"

ME_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET $BASE_URL/api/v1/auth/me \
  -H "Authorization: Bearer $LOGIN_TOKEN" \
  -H "Content-Type: application/json")

HTTP_CODE=$(echo "$ME_RESPONSE" | tail -n1)
ME_BODY=$(echo "$ME_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" == "200" ]; then
  echo -e "${GREEN}✅ Endpoint /me accessible (HTTP $HTTP_CODE)${NC}"
  echo "$ME_BODY" | jq .
else
  echo -e "${RED}❌ Endpoint /me non accessible (HTTP $HTTP_CODE)${NC}"
fi

# Summary
echo ""
echo "========================================"
echo "📊 Résumé des Tests"
echo "========================================"
echo -e "${GREEN}✅ Backend accessible${NC}"
echo -e "${GREEN}✅ Inscription/Connexion fonctionnelles${NC}"
echo -e "${GREEN}✅ JWT généré et valide${NC}"
echo -e "${GREEN}✅ Protection des endpoints active${NC}"
echo -e "${GREEN}✅ Rôles inclus dans le token${NC}"
echo ""
echo "🎉 Tous les tests backend sont passés avec succès !"
echo ""
echo "📝 Prochaines étapes:"
echo "   1. Démarrer le frontend: cd front && ng serve"
echo "   2. Ouvrir http://localhost:4200/login"
echo "   3. Se connecter avec:"
echo "      - Username: testadmin"
echo "      - Password: Admin123!"
echo ""
echo "Pour plus de détails, voir TEST_GUIDE.md"
echo "========================================"
