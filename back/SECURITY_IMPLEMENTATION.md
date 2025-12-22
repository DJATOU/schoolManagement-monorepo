# Implémentation de la Sécurité JWT - Guide Complet

## ✅ Résumé de l'implémentation

Un système d'authentification complet basé sur JWT a été implémenté pour sécuriser votre application School Management.

### Ce qui a été créé

#### 1. **Infrastructure JWT**
- `JwtTokenProvider` - Génération et validation des tokens JWT
- `JwtAuthenticationFilter` - Filtre Spring Security pour intercepter et valider les requêtes
- Configuration JWT dans `application.properties`

#### 2. **Authentification**
- `AuthController` - Endpoints `/login`, `/register`, `/me`
- `CustomUserDetailsService` - Chargement des utilisateurs depuis la base
- `AdministratorService` - Service métier pour gérer les administrateurs

#### 3. **DTOs**
- `LoginRequest` - Requête de connexion (username, password)
- `LoginResponse` - Réponse avec token JWT et infos utilisateur
- `RegisterRequest` - Requête d'inscription

#### 4. **Sécurité**
- `SecurityConfig` mis à jour avec JWT et BCrypt
- Hachage des mots de passe avec BCryptPasswordEncoder
- Session stateless (API REST)
- CORS configuré pour Angular

---

## 🔐 Endpoints d'authentification

### 1. Inscription (Register)
```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "username": "admin",
  "password": "password123",
  "email": "admin@school.com",
  "firstName": "John",
  "lastName": "Doe",
  "phoneNumber": "+33612345678"
}
```

**Réponse** (201 Created):
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "id": 1,
  "username": "admin",
  "email": "admin@school.com",
  "firstName": "John",
  "lastName": "Doe"
}
```

### 2. Connexion (Login)
```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "password123"
}
```

**Réponse** (200 OK):
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "id": 1,
  "username": "admin",
  "email": "admin@school.com",
  "firstName": "John",
  "lastName": "Doe"
}
```

### 3. Récupérer l'utilisateur connecté
```http
GET /api/v1/auth/me
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

---

## 🔒 Sécurité des Endpoints

### Endpoints Publics (pas d'authentification requise)
- ✅ `/api/v1/auth/**` - Authentification
- ✅ `/v3/api-docs/**` - Documentation Swagger
- ✅ `/swagger-ui/**` - Interface Swagger

### Endpoints Protégés (authentification JWT requise)
- 🔐 `/api/students/**` - Gestion des étudiants
- 🔐 `/api/teachers/**` - Gestion des enseignants
- 🔐 `/api/payments/**` - Gestion des paiements
- 🔐 `/api/groups/**` - Gestion des groupes
- 🔐 **Tous les autres endpoints**

---

## 🧪 Comment Tester

### Méthode 1: Avec curl

```bash
# 1. S'inscrire
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testadmin",
    "password": "password123",
    "email": "test@school.com",
    "firstName": "Test",
    "lastName": "Admin"
  }'

# 2. Se connecter
TOKEN=$(curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testadmin","password":"password123"}' \
  | jq -r '.token')

# 3. Accéder à un endpoint protégé
curl -X GET http://localhost:8080/api/students \
  -H "Authorization: Bearer $TOKEN"
```

### Méthode 2: Avec Postman/Insomnia

1. **Créer un compte**
   - POST `http://localhost:8080/api/v1/auth/register`
   - Body (JSON): username, password, email, firstName, lastName

2. **Se connecter**
   - POST `http://localhost:8080/api/v1/auth/login`
   - Body (JSON): username, password
   - Copier le `token` de la réponse

3. **Accéder aux endpoints protégés**
   - GET `http://localhost:8080/api/students`
   - Header: `Authorization: Bearer <votre-token>`

### Méthode 3: Swagger UI

1. Ouvrir http://localhost:8080/swagger-ui/index.html
2. Cliquer sur `POST /api/v1/auth/login`
3. Copier le token de la réponse
4. Cliquer sur le bouton "Authorize" en haut
5. Entrer: `Bearer <votre-token>`
6. Tester les autres endpoints

---

## 🔧 Configuration Production

### Variables d'environnement recommandées

```bash
# JWT Secret (OBLIGATOIRE en production - générer une clé aléatoire sécurisée)
export JWT_SECRET="VotreCleSuperSecreteAleatoireDe256BitsMinimum123456789"

# JWT Expiration (optionnel - défaut 24h)
export JWT_EXPIRATION=86400000

# Database
export SPRING_DATASOURCE_URL=jdbc:postgresql://votre-serveur:5432/schooldb
export SPRING_DATASOURCE_USERNAME=votre-user
export SPRING_DATASOURCE_PASSWORD=votre-password

# CORS
export CORS_ALLOWED_ORIGINS=https://votre-app.com,https://www.votre-app.com
```

### Générer une clé JWT sécurisée

```bash
# Avec OpenSSL
openssl rand -base64 64

# Avec Python
python3 -c "import secrets; print(secrets.token_urlsafe(64))"

# Avec Node.js
node -e "console.log(require('crypto').randomBytes(64).toString('base64'))"
```

---

## ⚠️ Actions Importantes Avant Production

### 1. **Changer la clé JWT** (CRITIQUE)
- La clé par défaut est **PUBLIQUE** dans le code
- Générez une clé aléatoire sécurisée (voir ci-dessus)
- Définissez `JWT_SECRET` en variable d'environnement

### 2. **Créer un premier administrateur**
Deux options:

**Option A: Via l'endpoint /register** (recommandé pour le premier admin)
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "superadmin",
    "password": "VotreMotDePasseSecurise",
    "email": "admin@votre-ecole.com",
    "firstName": "Super",
    "lastName": "Admin"
  }'
```

**Option B: Directement en base de données**
```sql
-- Le mot de passe sera hashé automatiquement par l'application au prochain login
-- Ou hashé manuellement avec BCrypt
INSERT INTO administrator (username, password, email, first_name, last_name, active)
VALUES ('admin', '$2a$10$...', 'admin@school.com', 'Admin', 'User', true);
```

### 3. **Désactiver /register en production** (optionnel mais recommandé)
Si vous ne voulez pas que n'importe qui puisse créer un compte admin:

```java
// Dans SecurityConfig.java, ligne 86
.requestMatchers(
    "/api/v1/auth/login",
    "/api/v1/auth/me"
).permitAll()
// /register est maintenant protégé, seuls les admins connectés peuvent créer d'autres admins
```

### 4. **Activer HTTPS**
- Le JWT sera transmis dans le header `Authorization`
- **TOUJOURS** utiliser HTTPS en production pour éviter l'interception

---

## 🔄 Intégration avec Angular

### Service d'authentification Angular

```typescript
// auth.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = 'http://localhost:8080/api/v1/auth';
  private tokenKey = 'auth_token';

  constructor(private http: HttpClient) {}

  login(username: string, password: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/login`, { username, password })
      .pipe(
        tap((response: any) => {
          if (response.token) {
            localStorage.setItem(this.tokenKey, response.token);
          }
        })
      );
  }

  register(userData: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/register`, userData)
      .pipe(
        tap((response: any) => {
          if (response.token) {
            localStorage.setItem(this.tokenKey, response.token);
          }
        })
      );
  }

  logout(): void {
    localStorage.removeItem(this.tokenKey);
  }

  getToken(): string | null {
    return localStorage.getItem(this.tokenKey);
  }

  isAuthenticated(): boolean {
    return this.getToken() !== null;
  }
}
```

### Intercepteur HTTP Angular

```typescript
// auth.interceptor.ts
import { Injectable } from '@angular/core';
import { HttpInterceptor, HttpRequest, HttpHandler } from '@angular/common/http';
import { AuthService } from './auth.service';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  constructor(private authService: AuthService) {}

  intercept(req: HttpRequest<any>, next: HttpHandler) {
    const token = this.authService.getToken();

    if (token) {
      const cloned = req.clone({
        headers: req.headers.set('Authorization', `Bearer ${token}`)
      });
      return next.handle(cloned);
    }

    return next.handle(req);
  }
}
```

### Enregistrement dans app.module.ts

```typescript
import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { AuthInterceptor } from './auth.interceptor';

@NgModule({
  // ...
  providers: [
    {
      provide: HTTP_INTERCEPTORS,
      useClass: AuthInterceptor,
      multi: true
    }
  ]
})
export class AppModule { }
```

---

## 📊 Architecture de Sécurité

```
Client (Angular)
    ↓
    POST /api/v1/auth/login {username, password}
    ↓
AuthController
    ↓
AuthenticationManager
    ↓
CustomUserDetailsService → AdministratorRepository → Database
    ↓
BCryptPasswordEncoder (compare passwords)
    ↓
JwtTokenProvider (generate JWT)
    ↓
    Return JWT Token to client
    ↓
Client stores token (localStorage/sessionStorage)
    ↓
    GET /api/students (Header: Authorization: Bearer <token>)
    ↓
JwtAuthenticationFilter
    ↓
JwtTokenProvider (validate token)
    ↓
SecurityContext (set authentication)
    ↓
Controller processes request
```

---

## 🛡️ Fonctionnalités de Sécurité Implémentées

✅ **Authentification JWT**
- Tokens signés avec HMAC-SHA256
- Expiration configurable (défaut: 24h)
- Validation côté serveur

✅ **Hachage des mots de passe**
- BCrypt avec salt automatique
- Force de hachage: 10 rounds (par défaut)
- Impossible de retrouver le mot de passe original

✅ **Protection CSRF**
- Désactivé (normal pour API REST stateless avec JWT)
- Protégé par JWT au lieu de cookies

✅ **CORS**
- Configuré pour permettre Angular
- Configurable via variable d'environnement

✅ **Session Stateless**
- Pas de sessions serveur
- Scalabilité horizontale facilitée

✅ **Endpoints protégés**
- Tous les endpoints (sauf auth et docs) nécessitent un token
- Validation automatique via filter

---

## 🚀 Prochaines Étapes Recommandées

### Priorité Haute
1. ✅ **Changer le JWT_SECRET** en production
2. ✅ **Créer un premier administrateur**
3. ✅ **Tester l'authentification complète**
4. ⏳ **Implémenter le refresh token** (pour renouveler le token sans re-login)
5. ⏳ **Ajouter des rôles/permissions** (ADMIN, TEACHER, STUDENT, etc.)

### Priorité Moyenne
6. ⏳ **Rate limiting** (limiter les tentatives de login)
7. ⏳ **Audit logging** (logger les connexions et actions sensibles)
8. ⏳ **Email verification** (vérification email lors de l'inscription)
9. ⏳ **Password reset** (récupération mot de passe oublié)
10. ⏳ **2FA** (authentification à deux facteurs)

### Priorité Basse
11. ⏳ **Token blacklist** (révoquer tokens avant expiration)
12. ⏳ **Remember me** (tokens longue durée)
13. ⏳ **Account lockout** (après X tentatives échouées)

---

## 📝 Notes Importantes

### Mots de passe
- ✅ Stockés hashés avec BCrypt
- ✅ Jamais retournés dans les réponses API
- ⚠️ L'AdministratorDto expose toujours le password (ligne 34) - À corriger si utilisé

### Tokens JWT
- ⏰ Expiration: 24h par défaut
- 🔄 Pas de refresh token pour l'instant (l'utilisateur doit se reconnecter)
- 📦 Contenu: username, issued_at, expiration

### Rôles
- 👤 Pour l'instant: un seul rôle `ROLE_ADMIN` pour tous les administrateurs
- 🔜 À implémenter: ROLE_TEACHER, ROLE_STUDENT, permissions granulaires

---

## ❓ FAQ

### Comment tester si mon JWT fonctionne ?
1. Connectez-vous avec `/api/v1/auth/login`
2. Copiez le token de la réponse
3. Utilisez jwt.io pour le décoder et vérifier son contenu
4. Testez un endpoint protégé avec `Authorization: Bearer <token>`

### Le token expire, que faire ?
- Actuellement: se reconnecter
- Recommandé: implémenter un refresh token

### Comment ajouter un nouvel administrateur ?
- Option 1: Utiliser `/api/v1/auth/register`
- Option 2: Créer un endpoint admin-only pour créer des admins

### Puis-je changer l'expiration du token ?
Oui, dans `application.properties`:
```properties
jwt.expiration=3600000  # 1 heure en millisecondes
```

### Comment déboguer les problèmes d'authentification ?
Activez les logs Spring Security:
```properties
logging.level.org.springframework.security=DEBUG
```

---

## 📞 Support

Pour toute question ou problème:
1. Vérifiez les logs de l'application
2. Testez avec curl ou Postman
3. Vérifiez que le token n'est pas expiré
4. Assurez-vous que le header Authorization est bien formaté: `Bearer <token>`

---

**Date de création**: 2025-12-22
**Auteur**: Claude Sonnet 4.5
**Version**: 1.0
