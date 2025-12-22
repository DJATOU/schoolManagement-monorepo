# Guide Complet - Authentification JWT avec Rôles

## 🎉 Ce qui a été implémenté

Un système d'authentification complet avec gestion des rôles a été créé pour votre application School Management :

### Backend (Spring Boot)
✅ Système de rôles (ADMIN, TEACHER, STUDENT, PARENT)
✅ Authentification JWT avec rôles dans le token
✅ Endpoints `/login` et `/register`
✅ Protection automatique de tous les endpoints
✅ Initialisation automatique des rôles au démarrage

### Frontend (Angular)
✅ Service d'authentification
✅ Page de connexion moderne et responsive
✅ Guard pour protéger les routes
✅ Intercepteur HTTP pour ajouter automatiquement le token
✅ Gestion des rôles côté client

---

## 📋 Configuration Angular Requise

### 1. Enregistrer les providers dans `app.module.ts` ou `app.config.ts`

#### Pour Angular avec NgModule (`app.module.ts`):

```typescript
import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { ReactiveFormsModule } from '@angular/forms';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { LoginComponent } from './components/login/login.component';
import { AuthInterceptor } from './interceptors/auth.interceptor';

@NgModule({
  declarations: [
    AppComponent,
    LoginComponent,
    // ... vos autres composants
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    HttpClientModule,
    ReactiveFormsModule,
    // ... vos autres modules
  ],
  providers: [
    {
      provide: HTTP_INTERCEPTORS,
      useClass: AuthInterceptor,
      multi: true
    }
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
```

#### Pour Angular Standalone (`app.config.ts`):

```typescript
import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { authInterceptor } from './interceptors/auth.interceptor';

import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(
      withInterceptors([authInterceptor])
    )
  ]
};
```

### 2. Configurer les routes (`app-routing.module.ts` ou `app.routes.ts`)

#### Avec NgModule:

```typescript
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { LoginComponent } from './components/login/login.component';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { AuthGuard } from './guards/auth.guard';

const routes: Routes = [
  { path: 'login', component: LoginComponent },
  {
    path: 'dashboard',
    component: DashboardComponent,
    canActivate: [AuthGuard]
  },
  {
    path: 'students',
    loadChildren: () => import('./components/student/student.module').then(m => m.StudentModule),
    canActivate: [AuthGuard],
    data: { roles: ['ROLE_ADMIN', 'ROLE_TEACHER'] } // Seulement admin et teacher
  },
  {
    path: 'teachers',
    loadChildren: () => import('./components/teacher/teacher.module').then(m => m.TeacherModule),
    canActivate: [AuthGuard],
    data: { roles: ['ROLE_ADMIN'] } // Seulement admin
  },
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
  { path: '**', redirectTo: '/dashboard' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
```

#### Avec Standalone Components:

```typescript
import { Routes } from '@angular/router';
import { AuthGuard } from './guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./components/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./components/dashboard/dashboard.component').then(m => m.DashboardComponent),
    canActivate: [AuthGuard]
  },
  {
    path: 'students',
    loadComponent: () => import('./components/student/student.component').then(m => m.StudentComponent),
    canActivate: [AuthGuard],
    data: { roles: ['ROLE_ADMIN', 'ROLE_TEACHER'] }
  },
  {
    path: 'teachers',
    loadComponent: () => import('./components/teacher/teacher.component').then(m => m.TeacherComponent),
    canActivate: [AuthGuard],
    data: { roles: ['ROLE_ADMIN'] }
  },
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' }
];
```

### 3. Ajouter un bouton de déconnexion dans votre navbar/sidemenu

```typescript
// Dans votre component de navigation
import { Component } from '@angular/core';
import { AuthService, User } from '../../services/auth.service';
import { Observable } from 'rxjs';

@Component({
  selector: 'app-navigation',
  templateUrl: './navigation.component.html'
})
export class NavigationComponent {
  currentUser$: Observable<User | null>;

  constructor(public authService: AuthService) {
    this.currentUser$ = authService.currentUser;
  }

  logout(): void {
    this.authService.logout();
  }

  get isAdmin(): boolean {
    return this.authService.isAdmin();
  }
}
```

```html
<!-- Dans votre template de navigation -->
<div *ngIf="currentUser$ | async as user" class="user-menu">
  <span>Bonjour, {{ user.firstName }} {{ user.lastName }}</span>
  <span class="role-badge">{{ user.roles[0] }}</span>
  <button (click)="logout()" class="logout-btn">Déconnexion</button>
</div>

<!-- Affichage conditionnel basé sur les rôles -->
<nav>
  <a routerLink="/dashboard">Dashboard</a>
  <a routerLink="/students" *ngIf="authService.hasAnyRole(['ROLE_ADMIN', 'ROLE_TEACHER'])">
    Étudiants
  </a>
  <a routerLink="/teachers" *ngIf="isAdmin">
    Enseignants
  </a>
  <a routerLink="/payments" *ngIf="isAdmin">
    Paiements
  </a>
</nav>
```

---

## 🚀 Démarrage Rapide

### 1. Backend

```bash
cd back
mvn clean install
mvn spring-boot:run
```

Au démarrage, l'application va automatiquement créer les 4 rôles dans la base de données :
- ROLE_ADMIN
- ROLE_TEACHER
- ROLE_STUDENT
- ROLE_PARENT

### 2. Créer le premier administrateur

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "Admin123!",
    "email": "admin@school.com",
    "firstName": "Super",
    "lastName": "Admin"
  }'
```

**Réponse** :
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

### 3. Frontend

```bash
cd front
npm install
ng serve
```

Ouvrez http://localhost:4200/login et connectez-vous avec :
- **Username** : `admin`
- **Password** : `Admin123!`

---

## 🎨 Personnalisation de la Page de Login

La page de login est dans `front/src/app/components/login/` :
- `login.component.ts` - Logique TypeScript
- `login.component.html` - Template HTML
- `login.component.css` - Styles CSS (déjà stylé avec un dégradé moderne)

Vous pouvez personnaliser :
- Les couleurs dans le CSS (actuellement violet/bleu)
- Le logo en ajoutant un `<img>` dans le header
- Les textes et labels

---

## 🔐 Utilisation des Rôles

### Dans le Backend

#### Protéger un endpoint spécifique

Dans `SecurityConfig.java`, décommentez et personnalisez :

```java
.authorizeHttpRequests(authz -> authz
    // Admin seulement
    .requestMatchers("/api/administrators/**").hasRole("ADMIN")
    .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")

    // Admin et Teacher
    .requestMatchers("/api/sessions/**").hasAnyRole("ADMIN", "TEACHER")
    .requestMatchers("/api/attendance/**").hasAnyRole("ADMIN", "TEACHER")

    // Lecture pour tous, écriture pour admin/teacher
    .requestMatchers(HttpMethod.GET, "/api/students/**").hasAnyRole("ADMIN", "TEACHER", "STUDENT", "PARENT")
    .requestMatchers(HttpMethod.POST, "/api/students/**").hasAnyRole("ADMIN", "TEACHER")

    .anyRequest().authenticated()
)
```

#### Dans un Controller avec `@PreAuthorize`

```java
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'STUDENT')")
    public ResponseEntity<List<StudentDTO>> getAllStudents() {
        // ...
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StudentDTO> createStudent(@RequestBody StudentDTO dto) {
        // ...
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteStudent(@PathVariable Long id) {
        // ...
    }
}
```

**Important** : Activez `@EnableMethodSecurity` dans `SecurityConfig` (déjà fait).

### Dans le Frontend Angular

#### Dans les templates (HTML)

```html
<!-- Afficher uniquement pour les admins -->
<button *ngIf="authService.isAdmin()" (click)="deleteStudent()">
  Supprimer
</button>

<!-- Afficher pour admin ET teacher -->
<div *ngIf="authService.hasAnyRole(['ROLE_ADMIN', 'ROLE_TEACHER'])">
  Contenu réservé aux admins et enseignants
</div>

<!-- Cacher pour les étudiants -->
<div *ngIf="!authService.isStudent()">
  Contenu pas pour les étudiants
</div>
```

#### Dans les composants (TypeScript)

```typescript
export class StudentListComponent implements OnInit {
  canEdit = false;
  canDelete = false;

  constructor(private authService: AuthService) {}

  ngOnInit(): void {
    this.canEdit = this.authService.hasAnyRole(['ROLE_ADMIN', 'ROLE_TEACHER']);
    this.canDelete = this.authService.isAdmin();
  }

  deleteStudent(id: number): void {
    if (!this.canDelete) {
      alert('Vous n\'avez pas la permission de supprimer un étudiant');
      return;
    }
    // ...
  }
}
```

#### Dans le routing (Guards)

```typescript
// Route accessible uniquement aux admins
{
  path: 'admin',
  component: AdminPanelComponent,
  canActivate: [AuthGuard],
  data: { roles: ['ROLE_ADMIN'] }
}

// Route accessible aux admins et teachers
{
  path: 'students',
  component: StudentsComponent,
  canActivate: [AuthGuard],
  data: { roles: ['ROLE_ADMIN', 'ROLE_TEACHER'] }
}

// Route accessible à tous les utilisateurs authentifiés
{
  path: 'dashboard',
  component: DashboardComponent,
  canActivate: [AuthGuard]
  // Pas de data.roles = tous les rôles acceptés
}
```

---

## 🔄 Créer d'autres types d'utilisateurs (Teachers, Students avec login)

### Option 1: Via l'interface admin (à créer)

Créez un composant admin pour gérer les utilisateurs et leurs rôles.

### Option 2: Via l'API directement

#### Créer un Teacher avec compte de connexion

```bash
# D'abord, il faudrait étendre TeacherEntity pour supporter username/password
# Ou créer un endpoint spécial admin pour créer des comptes liés
```

**Recommandation** : Pour l'instant, gardez l'authentification seulement pour les administrateurs. Vous pouvez étendre plus tard pour permettre aux teachers et students de se connecter.

---

## 🛡️ Sécurité - Bonnes Pratiques

### ✅ Déjà implémenté

- ✅ Mots de passe hashés avec BCrypt
- ✅ Tokens JWT signés
- ✅ Protection CSRF désactivée (normal pour API REST)
- ✅ Sessions stateless
- ✅ Validation côté serveur
- ✅ Gestion automatique de l'expiration des tokens

### ⚠️ À faire en production

1. **Changer la clé JWT**
```bash
export JWT_SECRET="VotreCleSecreteSuperLongueEtAleatoire256Bits"
```

2. **Activer HTTPS** (obligatoire en production)

3. **Configurer CORS correctement**
```properties
cors.allowed.origins=https://votre-domaine.com
```

4. **Ajouter rate limiting** pour éviter le brute force

5. **Implémenter un refresh token** pour renouveler les tokens sans re-login

6. **Logs d'audit** pour tracer les connexions

---

## 🧪 Tests

### Test Manuel avec curl

```bash
# 1. Login
TOKEN=$(curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin123!"}' \
  | jq -r '.token')

echo "Token: $TOKEN"

# 2. Appeler un endpoint protégé
curl -X GET http://localhost:8080/api/students \
  -H "Authorization: Bearer $TOKEN"

# 3. Essayer sans token (devrait échouer avec 401)
curl -X GET http://localhost:8080/api/students
```

### Test avec Postman

1. **Login**
   - POST `http://localhost:8080/api/v1/auth/login`
   - Body (JSON):
     ```json
     {
       "username": "admin",
       "password": "Admin123!"
     }
     ```
   - Copier le `token` de la réponse

2. **Appeler endpoint protégé**
   - GET `http://localhost:8080/api/students`
   - Header: `Authorization: Bearer <votre-token>`

### Test du Frontend

1. Démarrer backend : `mvn spring-boot:run`
2. Démarrer frontend : `ng serve`
3. Ouvrir http://localhost:4200/login
4. Se connecter avec admin/Admin123!
5. Vérifier la redirection vers /dashboard
6. Vérifier que le menu affiche le nom de l'utilisateur
7. Tester la déconnexion

---

## 📊 Structure des Rôles

| Rôle | Description | Accès Typique |
|------|-------------|---------------|
| ROLE_ADMIN | Administrateur | Accès complet à tout |
| ROLE_TEACHER | Enseignant | Gérer cours, sessions, notes |
| ROLE_STUDENT | Étudiant | Voir ses propres infos |
| ROLE_PARENT | Tuteur/Parent | Voir les infos de ses enfants |

---

## 🔧 Personnalisation Avancée

### Ajouter un nouveau rôle

1. **Backend** - Ajouter dans `RoleEntity.java`:
```java
public enum RoleName {
    ROLE_ADMIN,
    ROLE_TEACHER,
    ROLE_STUDENT,
    ROLE_PARENT,
    ROLE_SECRETARY  // Nouveau rôle
}
```

2. **Backend** - Initialiser dans `DataInitializer.java`:
```java
createRoleIfNotExists(RoleName.ROLE_SECRETARY, "Secretary - Manage administrative tasks");
```

3. **Frontend** - Ajouter méthode dans `auth.service.ts`:
```typescript
isSecretary(): boolean {
  return this.hasRole('ROLE_SECRETARY');
}
```

### Permettre à un utilisateur d'avoir plusieurs rôles

C'est déjà supporté ! Un administrateur peut être aussi teacher par exemple.

**Dans le code**:
```java
// Assigner plusieurs rôles
Set<RoleEntity> roles = new HashSet<>();
roles.add(adminRole);
roles.add(teacherRole);
administrator.setRoles(roles);
```

---

## ❓ FAQ

### Comment ajouter un logo sur la page de login ?

Dans `login.component.html`, ajoutez avant le `<h1>` :
```html
<div class="login-header">
  <img src="assets/logo.png" alt="Logo" class="login-logo" />
  <h1>School Management</h1>
  <!-- ... -->
</div>
```

Dans `login.component.css` :
```css
.login-logo {
  width: 80px;
  height: 80px;
  margin-bottom: 20px;
}
```

### Comment personnaliser l'URL de retour après login ?

C'est déjà géré ! Si l'utilisateur essaie d'accéder à `/students` sans être connecté, il sera redirigé vers `/login?returnUrl=/students`, puis retournera à `/students` après login.

### Comment savoir quel utilisateur est connecté ?

Dans n'importe quel composant :
```typescript
constructor(private authService: AuthService) {}

ngOnInit() {
  this.authService.currentUser.subscribe(user => {
    if (user) {
      console.log('Utilisateur connecté:', user);
      console.log('Rôles:', user.roles);
    }
  });
}
```

### Le token expire, que faire ?

Le token JWT expire après 24h par défaut. L'utilisateur doit se reconnecter.

**Solution recommandée** : Implémenter un refresh token (non inclus dans cette implémentation).

### Comment changer la durée d'expiration du token ?

Dans `application.properties` :
```properties
# 1 heure = 3600000ms
jwt.expiration=3600000

# 7 jours = 604800000ms
jwt.expiration=604800000
```

---

## 📞 Support et Documentation

- **Backend** : Voir `SECURITY_IMPLEMENTATION.md`
- **API Documentation** : http://localhost:8080/swagger-ui/index.html
- **Logs** : Activez avec `logging.level.org.springframework.security=DEBUG`

---

**Créé le** : 2025-12-22
**Version** : 2.0
**Auteur** : Claude Sonnet 4.5
