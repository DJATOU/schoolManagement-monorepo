# Plan d'implémentation — Authentification & Autorisation

## Vue d'ensemble

Ce plan convertit la conception (JWT stateless + autorisation par rôle) en une série d'étapes de
codage incrémentales, dans un ordre **bottom-up** : dépendances/configuration → entité et enum →
repository → services de sécurité (JWT, hachage, chargement du compte) → authentification et
contrôleur d'auth → chaîne de sécurité (401/403) → filtre JWT → gestion des comptes → compte ADMIN
initial → audit JPA réel → DTO/mappers → frontend Angular → i18n → checkpoints de build → couverture.

Chaque étape s'appuie sur les précédentes et se termine par un câblage concret ; aucun code
orphelin. Les **12 Correctness Properties** du design sont chacune implémentées par **exactement un**
test jqwik `@Property(tries = 100)` minimum, étiqueté du commentaire
`Feature: authentication-authorization, Property {N}: {texte de la propriété}`. Ces tâches de
property-test sont **obligatoires** (non marquées optionnelles).

### Conventions de build (IMPORTANT)

- Le shell peut exposer un JDK 17/25 incompatible avec Lombok (`TypeTag :: UNKNOWN`). **Tous** les
  builds/tests backend DOIVENT s'exécuter avec **Java 21**, via `back/build.sh` (qui force
  `JAVA_HOME` sur le JDK 21) ou `./mvnw` avec un `JAVA_HOME` Java 21.
  - Exemples : `bash back/build.sh clean test`, `bash back/build.sh clean package`.
- Frontend : `cd front && npm test -- --watch=false --browsers=ChromeHeadless` (exécution unique).
- Ne pas renommer le dossier `persistance`. Mapping DTO ↔ entité via `MappingContext`. Contrôleurs
  minces. Commentaires/messages français préservés. Un-service-par-entité côté frontend. i18n FR+EN.
  Aucun secret codé en dur.

## Tâches

- [x] 1. Ajouter les dépendances et la configuration externe de sécurité
  - Ajouter la dépendance **jjwt** (`io.jsonwebtoken` : `jjwt-api`, `jjwt-impl`, `jjwt-jackson`) au
    `back/pom.xml` sans toucher aux versions gérées par le BOM Spring Boot.
  - Ajouter dans `application.properties` les clés `security.jwt.secret=${JWT_SECRET}`,
    `security.jwt.expiration-ms=${JWT_EXPIRATION_MS:3600000}`,
    `security.admin.username=${ADMIN_USERNAME:admin}`, `security.admin.password=${ADMIN_PASSWORD}`.
  - Documenter `JWT_SECRET`, `ADMIN_PASSWORD`, `JWT_EXPIRATION_MS`, `ADMIN_USERNAME` dans
    `back/.env.example` (aucune valeur secrète en dur ; fail-fast si `JWT_SECRET` absent).
  - Vérifier le build avec `bash back/build.sh clean compile` (Java 21).
  - _Requirements: 6.2, 12.4_

- [x] 2. Créer l'entité UserEntity et l'enum Role dans `persistance`
  - [x] 2.1 Créer l'enum `Role` (`persistance`)
    - Définir `Role { ADMIN, VIEWER }` avec commentaires français (accès complet / lecture seule).
    - _Requirements: 3.1, 4.1, 12.1_

  - [x] 2.2 Créer l'entité `UserEntity` (`persistance`) étendant `BaseEntity`
    - `@Entity @Table(name = "app_user", uniqueConstraints = uk_user_username)` ; champs `id`,
      `username` (unique, non nul), `password` (haché, non nul), `role` (`@Enumerated(STRING)`),
      `enabled` (défaut `true`).
    - Placer la classe dans le dossier `persistance` sans le renommer.
    - _Requirements: 5.1, 7.2, 7.3, 12.1_

  - [ ]* 2.3 Écrire un test structurel de localisation de UserEntity/Role
    - Vérifier que `UserEntity` et `Role` sont dans le package `com.school.management.persistance`.
    - _Requirements: 12.1_

- [x] 3. Créer le UserRepository
  - Interface `UserRepository extends JpaRepository<UserEntity, Long>` avec
    `findByUsername(String)`, `existsByUsername(String)`, `existsByRole(Role)`.
  - _Requirements: 6.1, 7.2_

- [x] 4. Implémenter le JwtService (émission/validation de jeton)
  - [x] 4.1 Implémenter `JwtService` avec jjwt (HS256)
    - `generateToken(username, role)` (subject + claim `role` + `iat` + `exp`),
      `isTokenValid(token, username)`, `extractUsername(token)`, `extractRole(token)`,
      `extractExpiration(token)`.
    - Clé de signature depuis `security.jwt.secret`, durée depuis `security.jwt.expiration-ms` ;
      `isTokenValid` renvoie `false` pour signature invalide ou jeton expiré.
    - _Requirements: 1.1, 2.3, 9.1_

  - [x] 4.2 Écrire le property test du round-trip d'émission/validation du jeton
    - **Property 1: Round-trip d'émission/validation du jeton** — pour tout (identifiant, rôle),
      le jeton émis est valide pour cet identifiant et réextrait exactement l'identifiant et le rôle.
    - Commentaire : `Feature: authentication-authorization, Property 1: Round-trip d'émission/validation du jeton`.
    - `@Property(tries = 100)`, générateurs sur identifiants et rôles ADMIN/VIEWER.
    - **Validates: Requirements 1.1, 1.4, 2.3**

  - [x] 4.3 Écrire le property test du rejet de jeton absent/invalide/expiré
    - **Property 3: Jeton absent, invalide ou expiré refusé** — pour tout jeton absent, malformé,
      de signature invalide ou expiré (exp = maintenant), la validation échoue (`isTokenValid` faux).
    - Commentaire : `Feature: authentication-authorization, Property 3: Jeton absent, invalide ou expiré refusé sur ressource protégée`.
    - `@Property(tries = 100)`, générateurs incluant jetons tout juste expirés et signatures altérées.
    - **Validates: Requirements 2.1, 9.1**

- [x] 5. Implémenter le hachage des mots de passe (PasswordEncoder)
  - [x] 5.1 Exposer un bean `PasswordEncoder` (`BCryptPasswordEncoder`)
    - Bean déclaré dans la configuration de sécurité, réutilisable par les services.
    - _Requirements: 5.1, 5.2_

  - [x] 5.2 Écrire le property test du round-trip de hachage
    - **Property 6: Round-trip de hachage des mots de passe** — pour tout mot de passe en clair,
      la valeur stockée diffère du clair, `matches(clair, stocké)` est vrai et `matches` est faux
      pour tout autre mot de passe.
    - Commentaire : `Feature: authentication-authorization, Property 6: Round-trip de hachage des mots de passe`.
    - `@Property(tries = 100)`, générateurs incluant mots de passe vides/longs/non-ASCII.
    - **Validates: Requirements 5.1, 5.2**

- [x] 6. Implémenter le UserDetailsService
  - [x] 6.1 Implémenter `UserDetailsService` de chargement du compte
    - Charger `UserEntity` par `username`, convertir en `UserDetails` avec l'autorité `ROLE_<role>`
      et l'état `enabled` ; compte introuvable → `UsernameNotFoundException`.
    - _Requirements: 1.1, 1.3, 2.3_

  - [ ]* 6.2 Écrire les tests unitaires du UserDetailsService
    - Cas compte trouvé (autorité + enabled), compte introuvable (exception), compte désactivé.
    - _Requirements: 1.3_

- [x] 7. Implémenter AuthenticationService et AuthController
  - [x] 7.1 Implémenter `AuthenticationService` (login)
    - Déléguer à l'`AuthenticationManager` ; succès → émission du JWT via `JwtService` et
      construction d'`AuthResponseDTO { token, username, role, expiresAt }` ; échec (identifiants
      invalides / compte désactivé) → exception traduite en 401 générique.
    - _Requirements: 1.1, 1.2, 1.3_

  - [x] 7.2 Implémenter `AuthController` mince (`/api/v1/auth`)
    - `POST /login` (public) → `AuthenticationService` ; `GET /me` (authentifié) → renvoie
      `{ username, role }` depuis le `SecurityContext`. Logique dans le service, contrôleur mince.
    - _Requirements: 1.1, 1.4, 12.3_

  - [x] 7.3 Écrire le property test de connexion refusée (identifiants invalides / compte désactivé)
    - **Property 2: Connexion refusée pour identifiants invalides ou compte désactivé** — pour tout
      identifiant inexistant, tout mot de passe incorrect, et tout compte désactivé, la connexion est
      rejetée en 401 avec un message identique ne révélant pas le champ erroné.
    - Commentaire : `Feature: authentication-authorization, Property 2: Connexion refusée pour identifiants invalides ou compte désactivé`.
    - `@Property(tries = 100)`, mock `AuthenticationManager`/`UserDetailsService`, vérifier le
      message générique unique.
    - **Validates: Requirements 1.2, 1.3**

  - [ ]* 7.4 Écrire les tests unitaires d'AuthController/AuthenticationService
    - Login réussi (jeton + rôle exposés), endpoint `/me`, message d'erreur générique.
    - _Requirements: 1.1, 1.4_

- [x] 8. Configurer la chaîne de sécurité (SecurityConfig + points 401/403)
  - [x] 8.1 Créer les handlers 401 et 403
    - `RestAuthenticationEntryPoint` → 401 avec message français générique (Exigences 2.1, 9.1) ;
      `RestAccessDeniedHandler` → 403 avec message français (Exigences 4.2, 7.5).
    - _Requirements: 2.1, 4.2, 7.5, 9.1, 12.4_

  - [x] 8.2 Remplacer `SecurityConfig` (règles par méthode HTTP, stateless)
    - Conserver la config CORS existante ; désactiver CSRF ; `SessionCreationPolicy.STATELESS` ;
      points publics (`/api/v1/auth/login`, Swagger) ; `/api/v1/users/**` = `hasRole("ADMIN")` ;
      GET `/api/**` = `hasAnyRole("ADMIN","VIEWER")` ; POST/PUT/PATCH/DELETE `/api/**` =
      `hasRole("ADMIN")` ; brancher entry point 401 et access denied handler 403 ; exposer
      `AuthenticationManager` et `DaoAuthenticationProvider`. Ordre : règle `/users/**` avant les
      règles génériques ; corriger l'ancien typo `/api/v1/aith/**`.
    - _Requirements: 2.1, 2.2, 2.3, 3.1, 3.2, 4.1, 4.2, 7.5_

- [x] 9. Implémenter le JwtAuthenticationFilter
  - Extraire le `Bearer`, valider via `JwtService`, peupler le `SecurityContext` avec l'autorité
    `ROLE_<role>` ; en-tête absent/non-Bearer ou jeton invalide/expiré → ne pas authentifier
    (l'entry point renverra 401) ; enregistrer le filtre via `addFilterBefore(...)` dans
    `SecurityConfig`.
  - _Requirements: 2.1, 2.3, 9.1_

- [ ] 10. Checkpoint — build et tests backend (Java 21)
  - Lancer `bash back/build.sh clean test`. S'assurer que tous les tests passent ; poser une
    question à l'utilisateur en cas de souci.

- [x] 11. Câbler les tests d'intégration de la chaîne de sécurité (matrice rôle × méthode)
  - [x] 11.1 Mettre en place le socle de tests d'intégration sécurité (Spring Boot Test + H2 + MockMvc)
    - Contexte de test avec H2, comptes ADMIN et VIEWER, endpoints d'appui pour exercer GET et les
      quatre méthodes d'écriture.
    - _Requirements: 2.1, 2.2_

  - [x] 11.2 Écrire le property test de la matrice d'autorisation rôle × méthode
    - **Property 4: Autorisation par rôle (matrice rôle × méthode)** — ADMIN autorisé sur toute
      méthode ; VIEWER autorisé en GET et refusé en 403 sur POST/PUT/PATCH/DELETE, y compris sur
      `/api/v1/users/**`.
    - Commentaire : `Feature: authentication-authorization, Property 4: Autorisation par rôle (matrice rôle × méthode)`.
    - `@Property(tries = 100)` paramétré sur (rôle × méthode), via MockMvc.
    - **Validates: Requirements 3.1, 3.2, 4.1, 4.2, 7.5, 11.3, 2.3**

  - [x] 11.3 Écrire le property test « un refus d'écriture laisse les données inchangées »
    - **Property 5: Un refus d'écriture laisse les données inchangées** — pour tout état persistant
      et toute écriture refusée à un VIEWER (403), l'état persistant après le refus est identique à
      l'état avant la tentative.
    - Commentaire : `Feature: authentication-authorization, Property 5: Un refus d'écriture laisse les données inchangées`.
    - `@Property(tries = 100)`, comparaison de l'état H2 avant/après.
    - **Validates: Requirements 4.3**

  - [ ]* 11.4 Écrire les tests d'exemple des points publics et des codes 401/403
    - Accès public sans jeton (login, Swagger) → 200 ; ressource protégée sans jeton valide → 401 ;
      VIEWER en écriture → 403.
    - _Requirements: 2.1, 2.2_

- [x] 12. Implémenter UserAccountService et UserController (gestion des comptes)
  - [x] 12.1 Implémenter `UserAccountService`
    - `create` (hachage BCrypt, `enabled=true`, identifiant en double → `DuplicateUsernameException`
      → 409, comptes existants inchangés) ; `disable`/`enable` ; `resetPassword` (ré-encodage BCrypt) ;
      toutes les réponses via `UserResponseDTO` sans mot de passe.
    - _Requirements: 5.3, 7.1, 7.2, 7.3, 7.4_

  - [x] 12.2 Implémenter `UserController` mince (`/api/v1/users`)
    - `POST /`, `GET /`, `PATCH /{id}/disable`, `PATCH /{id}/enable`, `PATCH /{id}/reset-password` ;
      protégé ADMIN par la chaîne de sécurité ; logique dans le service.
    - _Requirements: 7.1, 7.3, 7.4, 7.5, 12.3_

  - [x] 12.3 Écrire le property test du cycle de vie d'un compte
    - **Property 9: Cycle de vie d'un compte (création, désactivation, réinitialisation)** — création
      valide → persisté avec mot de passe haché et retrouvable par identifiant ; désactivation →
      `enabled=false` et connexion refusée ; reset → seul le nouveau mot de passe est accepté.
    - Commentaire : `Feature: authentication-authorization, Property 9: Cycle de vie d'un compte (création, désactivation, réinitialisation)`.
    - `@Property(tries = 100)`, H2.
    - **Validates: Requirements 7.1, 7.3, 7.4**

  - [x] 12.4 Écrire le property test du rejet des identifiants en double
    - **Property 10: Rejet des identifiants en double** — pour tout identifiant déjà attribué, une
      nouvelle création avec cet identifiant est rejetée en 409 et les comptes existants restent
      inchangés.
    - Commentaire : `Feature: authentication-authorization, Property 10: Rejet des identifiants en double`.
    - `@Property(tries = 100)`, H2.
    - **Validates: Requirements 7.2**

  - [ ]* 12.5 Écrire les tests unitaires de UserAccountService
    - Cas d'erreur (compte introuvable → 404), reset, disable/enable.
    - _Requirements: 7.3, 7.4_

- [x] 13. Implémenter les DTOs et le UserMapper (via MappingContext)
  - [x] 13.1 Créer les DTOs de sécurité
    - `LoginRequestDTO`, `AuthResponseDTO`, `CreateUserRequestDTO` (validation Jakarta `@NotBlank`/
      `@NotNull`), `ResetPasswordRequestDTO`, `UserResponseDTO` (aucun champ mot de passe).
    - _Requirements: 5.3, 7.1, 7.4_

  - [x] 13.2 Implémenter `UserMapper` (MapStruct via MappingContext)
    - `toResponse(UserEntity)` ignore explicitement `password` ; mapping conforme au motif
      `MappingContext` (pas `ApplicationContextProvider`) ; le mapper ne manipule jamais de mot de
      passe en clair.
    - _Requirements: 5.3, 12.2_

  - [x] 13.3 Écrire le property test d'exclusion du mot de passe des réponses
    - **Property 7: Le mot de passe est exclu des réponses de l'API** — pour tout compte, le DTO
      sérialisé ne contient ni le champ mot de passe ni sa valeur (hachée ou en clair).
    - Commentaire : `Feature: authentication-authorization, Property 7: Le mot de passe est exclu des réponses de l'API`.
    - `@Property(tries = 100)`, sérialisation JSON générée, absence de la valeur du mot de passe.
    - **Validates: Requirements 5.3**

  - [ ]* 13.4 Écrire le test d'exemple du mapping via MappingContext
    - Vérifier que le mapping suit le motif `MappingContext` du projet.
    - _Requirements: 12.2_

- [x] 14. Implémenter le InitialAdminRunner (compte ADMIN initial)
  - [x] 14.1 Implémenter `InitialAdminRunner` (`ApplicationRunner` idempotent)
    - Si `existsByRole(ADMIN)` → ne rien faire ; sinon créer un ADMIN depuis
      `security.admin.username`/`security.admin.password` (haché BCrypt, `enabled=true`) ; aucun
      secret en dur ; `ADMIN_PASSWORD` absent → message explicite.
    - _Requirements: 6.1, 6.2, 6.3_

  - [x] 14.2 Écrire le property test d'idempotence du compte ADMIN initial
    - **Property 8: Idempotence de la création du compte ADMIN initial** — pour tout état initial,
      après exécution au moins un ADMIN existe ; si aucun n'existait, exactement un est créé ; si un
      ADMIN existait, aucun n'est créé ni écrasé, et une exécution répétée ne change rien.
    - Commentaire : `Feature: authentication-authorization, Property 8: Idempotence de la création du compte ADMIN initial`.
    - `@Property(tries = 100)`, H2, états initiaux générés.
    - **Validates: Requirements 6.1, 6.3**

- [x] 15. Activer l'audit JPA réel (SecurityAuditorAware + BaseEntity)
  - [x] 15.1 Implémenter `SecurityAuditorAware` et activer `@EnableJpaAuditing`
    - `AuditorAware<String>` lisant l'utilisateur courant du `SecurityContext`, repli `system` si
      anonyme/non authentifié ; activer `@EnableJpaAuditing(auditorAwareRef = "securityAuditorAware")`.
    - _Requirements: 8.1, 8.2, 8.4_

  - [x] 15.2 Adapter `BaseEntity` pour l'audit automatique
    - Ajouter `@EntityListeners(AuditingEntityListener.class)` ; annoter `createdBy` `@CreatedBy` et
      `updatedBy` `@LastModifiedBy` ; **retirer** la ligne `createdBy = "admin";` du `@PrePersist` ;
      préserver les commentaires français existants.
    - _Requirements: 8.1, 8.2, 8.3, 12.4_

  - [x] 15.3 Écrire le property test de traçabilité de l'audit
    - **Property 11: Traçabilité de l'audit sur l'utilisateur courant** — pour tout enregistrement
      dérivant de `BaseEntity`, `createdBy`/`updatedBy` valent l'identifiant de l'utilisateur
      authentifié (jamais « admin » codé en dur), et `system` en l'absence d'utilisateur.
    - Commentaire : `Feature: authentication-authorization, Property 11: Traçabilité de l'audit sur l'utilisateur courant`.
    - `@Property(tries = 100)`, `SecurityContext` simulé (authentifié / anonyme), entités persistées en H2.
    - **Validates: Requirements 8.1, 8.2, 8.3, 8.4**

- [ ] 16. Checkpoint — build et tests backend complets (Java 21)
  - Lancer `bash back/build.sh clean test`. S'assurer que tous les tests passent ; poser une
    question à l'utilisateur en cas de souci.

- [x] 17. Implémenter le socle d'authentification frontend (auth.service + login)
  - [x] 17.1 Implémenter `auth.service.ts` (un service par entité)
    - `login()`, `logout()`, `getToken()`, `currentUser$` (BehaviorSubject `{ username, role }`),
      `hasRole(role)` ; stockage du jeton (localStorage), restauration de l'utilisateur au démarrage
      tant que le jeton n'est pas expiré ; gestion d'erreur centralisée (motif `payment.service.ts`).
    - _Requirements: 1.4, 9.2, 10.2, 12.5_

  - [x] 17.2 Implémenter le composant `login` (NgModule)
    - Formulaire identifiant/mot de passe ; message d'erreur traduisible sans révéler le champ
      erroné ; redirection vers la vue principale au succès ; clés ngx-translate.
    - _Requirements: 10.2, 10.3, 12.6_

  - [ ]* 17.3 Écrire les tests frontend du login (Karma + Jasmine)
    - Connexion réussie (stockage jeton + redirection), échec (message générique traduit).
    - _Requirements: 10.2, 10.3_

- [x] 18. Implémenter l'intercepteur HTTP et les gardes de route
  - [x] 18.1 Implémenter `authInterceptor`
    - Joindre `Authorization: Bearer <token>` aux requêtes vers les ressources protégées ; sur 401,
      purger le jeton et rediriger vers `/login`.
    - _Requirements: 9.3, 10.4_

  - [x] 18.2 Implémenter `authGuard` / `roleGuard`
    - Bloquer l'accès aux vues métier sans connexion (redirection `/login`) ; réserver les vues
      d'administration au rôle ADMIN ; câbler dans `app.routes.ts`.
    - _Requirements: 10.1_

  - [x] 18.3 Écrire le property test de l'intercepteur (en-tête Bearer)
    - **Property 12: L'intercepteur joint le justificatif aux requêtes protégées** — pour toute
      requête sortante vers une ressource protégée alors qu'un jeton est présent, l'intercepteur
      ajoute l'en-tête `Authorization: Bearer <token>`.
    - Commentaire : `Feature: authentication-authorization, Property 12: L'intercepteur joint le justificatif aux requêtes protégées`.
    - Property test (fast-check si disponible, sinon test paramétré exhaustif Karma + Jasmine),
      minimum 100 itérations, requêtes générées.
    - **Validates: Requirements 10.4**

  - [ ]* 18.4 Écrire les tests frontend des gardes et de la redirection 401
    - Garde bloquant une vue protégée (redirection login), redirection sur réponse 401.
    - _Requirements: 9.3, 10.1_

- [x] 19. Implémenter l'adaptation de l'UI selon le rôle et la gestion des comptes
  - [x] 19.1 Implémenter la directive `hasRole` (`*appHasRole`)
    - Masquer/désactiver les commandes d'écriture pour un VIEWER et les afficher pour un ADMIN.
    - _Requirements: 11.1, 11.2_

  - [x] 19.2 Implémenter `user.service.ts` et les vues de gestion des comptes
    - `user.service.ts` (un service par entité, appels HTTP de gestion des comptes, gestion d'erreur
      centralisée) ; vues de création/désactivation/réinitialisation réservées à l'ADMIN.
    - _Requirements: 7.1, 7.3, 7.4, 12.5_

  - [ ]* 19.3 Écrire les tests frontend de masquage selon le rôle
    - Directive `hasRole` : commandes masquées pour VIEWER, affichées pour ADMIN.
    - _Requirements: 11.1, 11.2_

- [x] 20. Ajouter les traductions i18n FR + EN
  - Ajouter les clés des nouvelles chaînes (login, erreurs génériques, gestion des comptes, messages
    de rôle) dans `fr.json` et `en.json`.
  - _Requirements: 12.6_

  - [ ]* 20.1 Écrire le test de parité des clés i18n FR/EN
    - Vérifier que `fr.json` et `en.json` possèdent exactement les mêmes clés.
    - _Requirements: 12.6_

- [x] 21. Étendre la couverture JaCoCo aux nouveaux packages de sécurité
  - Ajouter aux `includes` de la règle `jacoco-check` du `back/pom.xml` les nouvelles classes de
    sécurité écrites à la main (`JwtService`, `AuthenticationService`, `UserAccountService`,
    `UserDetailsService`, `SecurityAuditorAware`, `InitialAdminRunner`, `UserMapper`), en excluant
    le code généré `*MapperImpl` conformément à la politique existante.
  - _Requirements: 5.1, 5.2, 7.1, 8.1_

- [ ] 22. Checkpoint final — build, tests et couverture (Java 21)
  - Lancer `bash back/build.sh clean verify` (backend, Java 21) et
    `cd front && npm test -- --watch=false --browsers=ChromeHeadless` (frontend).
  - S'assurer que tous les tests passent et que le seuil de couverture JaCoCo est respecté sur les
    nouveaux packages de sécurité ; poser une question à l'utilisateur en cas de souci.

## Notes

- Les tâches suffixées par `*` sont optionnelles (tests d'exemple/unitaires supplémentaires,
  tests structurels) et peuvent être omises pour un MVP plus rapide.
- Les tâches de property test (4.2, 4.3, 5.2, 7.3, 11.2, 11.3, 12.3, 12.4, 13.3, 14.2, 15.3, 18.3)
  ne sont **pas** optionnelles : elles couvrent les 12 Correctness Properties, chacune par exactement
  un test étiqueté `@Property(tries = 100)`.
- Tous les builds/tests backend s'exécutent en **Java 21** (`back/build.sh` ou `./mvnw` avec
  `JAVA_HOME` Java 21) pour éviter l'erreur Lombok sous JDK 17/25.
- Chaque tâche référence les exigences correspondantes pour la traçabilité ; les checkpoints valident
  l'avancement de façon incrémentale.
