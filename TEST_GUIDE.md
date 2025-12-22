# Guide de Test - Système d'Authentification JWT

## 🎯 Objectif

Ce guide vous permet de tester complètement le système d'authentification avec rôles que nous avons implémenté.

---

## 🚀 Démarrage Rapide

### 1. Démarrer le Backend

```bash
cd back
mvn clean install
mvn spring-boot:run
```

**Vérifications** :
- ✅ Le serveur démarre sur http://localhost:8080
- ✅ Les logs affichent "Initializing roles..."
- ✅ Les logs affichent "Created role: ROLE_ADMIN", "ROLE_TEACHER", etc.

### 2. Démarrer le Frontend

```bash
cd front
npm install
ng serve
```

**Vérifications** :
- ✅ Le serveur démarre sur http://localhost:4200
- ✅ Aucune erreur de compilation
- ✅ Vous êtes automatiquement redirigé vers `/login` si non connecté

---

## 📋 Scénarios de Test

### Test 1: Créer le Premier Administrateur

**Via curl** :
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "Admin123!",
    "email": "admin@school.com",
    "firstName": "Super",
    "lastName": "Admin",
    "phoneNumber": "+33612345678"
  }'
```

**Réponse attendue** (200 Created):
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "id": 1,
  "username": "admin",
  "email": "admin@school.com",
  "firstName": "Super",
  "lastName": "Admin",
  "roles": ["ROLE_ADMIN"]
}
```

✅ **Vérifications** :
- Le token est retourné
- Le rôle est "ROLE_ADMIN"
- L'email correspond

---

### Test 2: Se Connecter via le Frontend

1. **Ouvrir le navigateur** : http://localhost:4200/login
2. **Entrer les identifiants** :
   - Username: `admin`
   - Password: `Admin123!`
3. **Cliquer** sur "Se connecter"

✅ **Vérifications** :
- ✅ Redirection vers `/dashboard`
- ✅ Dans la navbar, vous voyez "Super Admin"
- ✅ Le rôle affiché est "Administrateur"
- ✅ Le menu utilisateur (en haut à droite) affiche votre nom

**Inspecter le localStorage** :
```javascript
// Ouvrir la console du navigateur (F12)
console.log(localStorage.getItem('auth_token'));  // Affiche le JWT
console.log(localStorage.getItem('auth_user'));   // Affiche les infos user
```

---

### Test 3: Vérifier le Token JWT

**Copier le token** depuis localStorage et aller sur https://jwt.io

**Payload attendu** :
```json
{
  "sub": "admin",
  "roles": "ROLE_ADMIN",
  "iat": 1703260800,
  "exp": 1703347200
}
```

✅ **Vérifications** :
- `sub` = username
- `roles` contient "ROLE_ADMIN"
- `exp` est dans le futur (24h après `iat`)

---

### Test 4: Appeler un Endpoint Protégé

**Récupérer le token** :
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin123!"}' \
  | jq -r '.token')

echo "Token: $TOKEN"
```

**Tester l'accès avec token** :
```bash
curl -X GET http://localhost:8080/api/students \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"
```

✅ **Résultat attendu** : 200 OK avec la liste des étudiants (peut être vide)

**Tester l'accès SANS token** :
```bash
curl -v -X GET http://localhost:8080/api/students
```

✅ **Résultat attendu** : 401 Unauthorized

---

### Test 5: Protection par Rôle - Frontend

1. **Se connecter** en tant qu'admin
2. **Observer les menus** :
   - Tous les menus sont visibles (admin a accès à tout)

**Vérifier la protection des routes** :

Dans la console du navigateur :
```javascript
// Récupérer le service d'authentification
const authService = angular.injector.get('AuthService');

// Tester les méthodes
console.log(authService.isAdmin());      // true
console.log(authService.isTeacher());    // false
console.log(authService.isStudent());    // false
```

---

### Test 6: Créer un Enseignant (pour tester les permissions)

**Remarque** : Pour l'instant, seuls les administrateurs peuvent se connecter. Pour tester les autres rôles, vous devriez :

**Option 1** - Modifier manuellement en base de données :
```sql
-- Se connecter à PostgreSQL
psql -U postgres -d schoolManagement4

-- Voir les rôles créés
SELECT * FROM role;

-- Créer un utilisateur teacher
INSERT INTO administrator (username, password, email, first_name, last_name, active, date_creation)
VALUES ('teacher1', '$2a$10$...', 'teacher@school.com', 'John', 'Teacher', true, NOW());

-- Récupérer l'ID de l'admin créé
SELECT id FROM administrator WHERE username = 'teacher1';

-- Assigner le rôle TEACHER (supposons ID admin = 2, ID role = 2)
INSERT INTO administrator_roles (administrator_id, role_id)
VALUES (2, 2);
```

**Option 2** - Créer un endpoint admin-only pour créer des utilisateurs (recommandé pour plus tard)

---

### Test 7: Déconnexion

**Dans le frontend** :
1. Cliquer sur le menu utilisateur (en haut à droite)
2. Cliquer sur "Déconnexion"

✅ **Vérifications** :
- ✅ Redirection vers `/login`
- ✅ localStorage est vidé
- ✅ Impossible d'accéder aux pages protégées

**Tester la redirection** :
1. Se déconnecter
2. Essayer d'aller sur http://localhost:4200/dashboard
3. ✅ Vous êtes redirigé vers `/login?returnUrl=/dashboard`
4. Se reconnecter
5. ✅ Vous êtes redirigé vers `/dashboard`

---

### Test 8: Token Expiré

**Simuler un token expiré** :

Dans la console du navigateur :
```javascript
// Modifier le token pour qu'il soit expiré
const fakeExpiredToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbiIsInJvbGVzIjoiUk9MRV9BRE1JTiIsImlhdCI6MTAwMDAwMDAwMCwiZXhwIjoxMDAwMDAwMDAxfQ.invalid';
localStorage.setItem('auth_token', fakeExpiredToken);

// Essayer d'accéder à une page
// -> Vous devriez être déconnecté automatiquement
```

---

### Test 9: Permissions Backend par Rôle

**Tester l'accès aux endpoints admin-only** :

```bash
# Se connecter en tant qu'admin
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin123!"}' \
  | jq -r '.token')

# Accéder à un endpoint admin-only (actuellement tous sont ouverts aux users authentifiés)
# Pour tester vraiment, décommentez les lignes dans SecurityConfig.java
curl -X GET http://localhost:8080/api/administrators \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

**Pour activer la restriction par rôle** :

Dans `back/src/main/java/com/school/management/config/SecurityConfig.java`, décommentez :

```java
// Admin only endpoints
.requestMatchers("/api/administrators/**").hasRole("ADMIN")
.requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")
```

Puis relancez le backend et retestez.

---

## 🧪 Tests Automatisés

### Script de Test Complet

Créez un fichier `test-auth.sh` :

```bash
#!/bin/bash

BASE_URL="http://localhost:8080"
FRONTEND_URL="http://localhost:4200"

echo "========================================"
echo "Test 1: Santé du serveur backend"
echo "========================================"
curl -s $BASE_URL/actuator/health || curl -s $BASE_URL

echo -e "\n\n========================================"
echo "Test 2: Inscription d'un administrateur"
echo "========================================"
REGISTER_RESPONSE=$(curl -s -X POST $BASE_URL/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "Admin123!",
    "email": "admin@test.com",
    "firstName": "Test",
    "lastName": "Admin"
  }')

echo "$REGISTER_RESPONSE" | jq .

TOKEN=$(echo "$REGISTER_RESPONSE" | jq -r '.token')

if [ "$TOKEN" != "null" ]; then
  echo "✅ Token reçu: ${TOKEN:0:50}..."
else
  echo "❌ Échec de l'inscription"
  exit 1
fi

echo -e "\n\n========================================"
echo "Test 3: Connexion"
echo "========================================"
LOGIN_RESPONSE=$(curl -s -X POST $BASE_URL/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "Admin123!"
  }')

echo "$LOGIN_RESPONSE" | jq .

LOGIN_TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.token')

if [ "$LOGIN_TOKEN" != "null" ]; then
  echo "✅ Connexion réussie"
else
  echo "❌ Échec de connexion"
  exit 1
fi

echo -e "\n\n========================================"
echo "Test 4: Accès endpoint protégé AVEC token"
echo "========================================"
STUDENTS_RESPONSE=$(curl -s -X GET $BASE_URL/api/students \
  -H "Authorization: Bearer $LOGIN_TOKEN" \
  -H "Content-Type: application/json")

if [ $? -eq 0 ]; then
  echo "✅ Accès autorisé avec token"
  echo "$STUDENTS_RESPONSE" | jq .
else
  echo "❌ Échec d'accès avec token"
fi

echo -e "\n\n========================================"
echo "Test 5: Accès endpoint protégé SANS token"
echo "========================================"
RESPONSE_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X GET $BASE_URL/api/students)

if [ "$RESPONSE_CODE" == "401" ]; then
  echo "✅ Accès refusé sans token (401)"
else
  echo "❌ L'endpoint devrait retourner 401 mais retourne $RESPONSE_CODE"
fi

echo -e "\n\n========================================"
echo "Test 6: Vérifier les rôles dans le JWT"
echo "========================================"
PAYLOAD=$(echo $LOGIN_TOKEN | awk -F'.' '{print $2}' | base64 -d 2>/dev/null)
echo "Payload JWT:"
echo "$PAYLOAD" | jq .

ROLES=$(echo "$PAYLOAD" | jq -r '.roles')
if [[ "$ROLES" == *"ROLE_ADMIN"* ]]; then
  echo "✅ Rôle ADMIN trouvé dans le token"
else
  echo "❌ Rôle ADMIN non trouvé"
fi

echo -e "\n\n========================================"
echo "✅ Tous les tests terminés"
echo "========================================"
```

**Exécuter** :
```bash
chmod +x test-auth.sh
./test-auth.sh
```

---

## 🎨 Tests Manuels Frontend

### Checklist Complète

#### Page de Login
- [ ] La page s'affiche correctement sur http://localhost:4200/login
- [ ] Le formulaire a deux champs : username et password
- [ ] Le bouton "Se connecter" est présent
- [ ] Les erreurs de validation s'affichent si les champs sont vides
- [ ] Un message d'erreur s'affiche si les identifiants sont incorrects
- [ ] Un spinner s'affiche pendant la connexion
- [ ] Après connexion réussie, redirection vers /dashboard

#### Navigation Authentifiée
- [ ] Le nom complet de l'utilisateur s'affiche dans la navbar
- [ ] Le rôle correct s'affiche (Administrateur, Enseignant, etc.)
- [ ] Le menu utilisateur contient un bouton "Déconnexion"
- [ ] Cliquer sur "Déconnexion" redirige vers /login
- [ ] Après déconnexion, impossible d'accéder aux pages protégées

#### Guards et Permissions
- [ ] Essayer d'accéder à /dashboard sans être connecté → redirection vers /login
- [ ] Se connecter puis accéder à /dashboard → accès autorisé
- [ ] (Si implémenté) Un teacher ne peut pas accéder aux routes admin-only

#### Persistance
- [ ] Se connecter, fermer le navigateur, rouvrir → toujours connecté
- [ ] localStorage contient auth_token et auth_user
- [ ] Le token est envoyé dans toutes les requêtes HTTP

---

## 🔍 Debugging

### Voir les Requêtes HTTP

Dans la console du navigateur (F12 → Network) :
1. Se connecter
2. Aller sur un composant qui appelle l'API
3. Voir la requête dans l'onglet Network
4. Vérifier le header `Authorization: Bearer ...`

### Logs Backend

Activez les logs de sécurité dans `application.properties` :
```properties
logging.level.org.springframework.security=DEBUG
logging.level.com.school.management=DEBUG
```

Relancez le backend et observez les logs pour chaque requête.

### Logs Frontend

Dans n'importe quel composant :
```typescript
constructor(private authService: AuthService) {
  authService.currentUser.subscribe(user => {
    console.log('Current user:', user);
  });
}
```

---

## ❓ Troubleshooting

### Problème : "Cannot find module '@angular/common/http'"
**Solution** : Installer les dépendances
```bash
cd front
npm install
```

### Problème : "CORS error"
**Solution** : Vérifier que CORS est configuré dans `SecurityConfig.java` :
```java
cors.allowed.origins=http://localhost:4200
```

### Problème : "401 Unauthorized sur toutes les requêtes"
**Solutions** :
1. Vérifier que l'intercepteur est bien enregistré dans `app.config.ts`
2. Vérifier que le token est dans localStorage
3. Vérifier le format du header : `Authorization: Bearer <token>`

### Problème : "Token non valide"
**Solutions** :
1. Vider localStorage et se reconnecter
2. Vérifier que JWT_SECRET est le même entre la génération et la validation
3. Vérifier que le token n'est pas expiré

### Problème : "Cannot find AuthGuard"
**Solution** : Vérifier que le guard est créé dans `front/src/app/guards/auth.guard.ts`

---

## 📊 Résultat Attendu

Après avoir passé tous les tests, vous devriez avoir :

✅ Backend Spring Boot avec JWT fonctionnel
✅ 4 rôles créés automatiquement en base de données
✅ Au moins un administrateur créé
✅ Frontend Angular avec page de login
✅ Navigation affichant l'utilisateur connecté
✅ Toutes les routes protégées par AuthGuard
✅ Déconnexion fonctionnelle
✅ Token JWT envoyé automatiquement dans toutes les requêtes
✅ Permissions par rôle (si activées dans SecurityConfig)

---

**Créé le** : 2025-12-22
**Version** : 1.0
