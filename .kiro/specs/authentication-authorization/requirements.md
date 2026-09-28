# Document des exigences

## Introduction

Cette fonctionnalité introduit **l'authentification et l'autorisation par rôle** dans le
système School Management (backend Spring Boot 3.4.1 / Java 21, frontend Angular 17). À ce
jour, l'application est entièrement ouverte : `SecurityConfig` applique
`.anyRequest().permitAll()`, il n'existe ni écran de connexion, ni compte utilisateur, ni
notion de rôle. Par ailleurs, `BaseEntity` possède déjà des champs d'audit (`createdBy`,
`updatedBy`, `dateCreation`, `dateUpdate`), mais `createdBy` est codé en dur à la valeur
« admin » dans `@PrePersist` et `updatedBy` n'est jamais renseigné : l'audit n'a donc
aucune valeur réelle tant qu'un utilisateur authentifié n'est pas introduit.

L'objectif fonctionnel est le suivant :

- Exiger une connexion (identifiant + mot de passe) au démarrage de l'application, avant tout
  accès aux données métier.
- Définir deux rôles :
  - **ADMIN** : accès complet à toutes les opérations (lecture et écriture : création,
    modification, suppression).
  - **VIEWER** : consultation uniquement (lecture seule ; les opérations d'écriture
    POST/PUT/PATCH/DELETE sont interdites).
- Assurer la **traçabilité** : chaque écriture doit enregistrer l'identité de l'utilisateur
  réellement connecté dans les champs « créé par » (`createdBy`) et « modifié par »
  (`updatedBy`).
- Fournir côté frontend un écran de connexion, la protection des routes, le masquage ou la
  désactivation des actions d'écriture pour un VIEWER, et la gestion de l'expiration / de la
  déconnexion.
- Garantir un stockage sécurisé des mots de passe (hachés, jamais en clair) et la présence
  d'au moins un compte ADMIN initial pour éviter tout verrouillage hors de l'application.

Ce document capture ces décisions sous forme d'exigences vérifiables et sert de base au
document de conception. Il décrit **quoi** faire (le besoin), pas **comment** le faire (la
solution technique — mécanisme de jeton, filtres, structure des tables — sera traitée au
design). Toutes les chaînes visibles par l'utilisateur sont traduisibles en français et en
anglais via ngx-translate.

## Glossaire

- **Système** : l'application School Management dans son ensemble (backend + frontend), sauf
  lorsqu'un composant plus précis est nommé.
- **Service_Authentification** : le composant backend responsable de vérifier les identifiants
  fournis et d'établir une session authentifiée.
- **Service_Autorisation** : le composant backend responsable d'autoriser ou de refuser une
  opération selon le rôle de l'Utilisateur authentifié.
- **Utilisateur** : une personne disposant d'un Compte lui permettant de se connecter au
  Système.
- **Compte** : l'enregistrement d'un Utilisateur, comprenant un Identifiant, un mot de passe
  haché, un Rôle et un indicateur d'activation.
- **Identifiant** : la valeur unique servant à identifier un Compte lors de la connexion
  (login).
- **Rôle** : l'attribut d'un Compte déterminant ses droits, valant ADMIN ou VIEWER.
- **ADMIN** : le Rôle autorisant toutes les opérations de lecture et d'écriture.
- **VIEWER** : le Rôle autorisant uniquement les opérations de lecture.
- **Opération_Lecture** : une requête HTTP de méthode GET (consultation de données).
- **Opération_Écriture** : une requête HTTP de méthode POST, PUT, PATCH ou DELETE (création,
  modification ou suppression de données).
- **Session_Authentifiée** : l'état prouvant qu'un Utilisateur s'est connecté, matérialisé par
  un justificatif d'authentification (par exemple un jeton) émis à la connexion et présenté à
  chaque requête ultérieure.
- **Justificatif_Authentification** : la preuve d'authentification émise à la connexion et
  transmise par le client à chaque requête protégée (mécanisme précis défini au design).
- **Expiration** : le moment au-delà duquel un Justificatif_Authentification n'est plus valide
  et impose une reconnexion.
- **Compte_ADMIN_Initial** : un Compte de Rôle ADMIN présent dès le premier démarrage du
  Système, garantissant qu'un administrateur peut toujours se connecter.
- **Champ_Audit_Créé_Par** : le champ `createdBy` de `BaseEntity`, enregistrant l'Identifiant
  de l'Utilisateur ayant créé un enregistrement.
- **Champ_Audit_Modifié_Par** : le champ `updatedBy` de `BaseEntity`, enregistrant
  l'Identifiant de l'Utilisateur ayant modifié un enregistrement en dernier.
- **Ressource_Protégée** : tout point d'accès (endpoint) de l'API métier exigeant une
  Session_Authentifiée valide.
- **Point_Accès_Public** : un point d'accès accessible sans Session_Authentifiée (par exemple
  la connexion elle-même et la documentation Swagger).

## Exigences

### Exigence 1 : Connexion par identifiant et mot de passe

**User Story:** En tant qu'utilisateur, je veux me connecter avec un identifiant et un mot de
passe, afin d'accéder à l'application selon mes droits.

#### Critères d'acceptation

1. WHEN un Utilisateur soumet un Identifiant et un mot de passe correspondant à un Compte actif,
   THE Service_Authentification SHALL établir une Session_Authentifiée et retourner un
   Justificatif_Authentification associé au Rôle du Compte.
2. IF un Utilisateur soumet un Identifiant inexistant ou un mot de passe incorrect, THEN THE
   Service_Authentification SHALL rejeter la demande avec un code HTTP 401 et un message
   d'erreur ne précisant pas lequel des deux champs est erroné.
3. IF un Utilisateur soumet des identifiants correspondant à un Compte désactivé, THEN THE
   Service_Authentification SHALL rejeter la demande avec un code HTTP 401.
4. WHEN un Utilisateur se connecte avec succès, THE Système SHALL exposer l'Identifiant et le
   Rôle de l'Utilisateur connecté afin que le frontend adapte l'interface.

### Exigence 2 : Protection des ressources et des points d'accès

**User Story:** En tant que responsable de l'application, je veux que les données métier ne
soient accessibles qu'après connexion, afin de protéger les informations de l'école.

#### Critères d'acceptation

1. WHEN une requête vise une Ressource_Protégée sans Justificatif_Authentification valide, THE
   Service_Autorisation SHALL refuser l'accès avec un code HTTP 401.
2. THE Service_Autorisation SHALL autoriser l'accès aux Points_Accès_Public (connexion,
   documentation Swagger / OpenAPI) sans Session_Authentifiée.
3. WHEN une requête présente un Justificatif_Authentification valide vers une
   Ressource_Protégée, THE Service_Autorisation SHALL laisser la requête se poursuivre selon
   les règles d'autorisation par rôle.

### Exigence 3 : Autorisation par rôle (ADMIN accès complet)

**User Story:** En tant qu'administrateur, je veux disposer d'un accès complet, afin de créer,
modifier et supprimer toutes les données de l'école.

#### Critères d'acceptation

1. WHERE l'Utilisateur connecté a le Rôle ADMIN, THE Service_Autorisation SHALL autoriser les
   Opérations_Lecture sur toutes les Ressources_Protégées.
2. WHERE l'Utilisateur connecté a le Rôle ADMIN, THE Service_Autorisation SHALL autoriser les
   Opérations_Écriture sur toutes les Ressources_Protégées.

### Exigence 4 : Autorisation par rôle (VIEWER lecture seule)

**User Story:** En tant que responsable, je veux qu'un utilisateur en consultation ne puisse
rien modifier, afin de préserver l'intégrité des données.

#### Critères d'acceptation

1. WHERE l'Utilisateur connecté a le Rôle VIEWER, THE Service_Autorisation SHALL autoriser les
   Opérations_Lecture sur toutes les Ressources_Protégées.
2. WHERE l'Utilisateur connecté a le Rôle VIEWER, IF la requête est une Opération_Écriture,
   THEN THE Service_Autorisation SHALL refuser l'accès avec un code HTTP 403.
3. WHEN le Service_Autorisation refuse une Opération_Écriture à un Utilisateur de Rôle VIEWER,
   THE Système SHALL laisser les données inchangées.

### Exigence 5 : Stockage sécurisé des mots de passe

**User Story:** En tant que responsable de la sécurité, je veux que les mots de passe soient
stockés de façon sécurisée, afin qu'ils ne soient jamais exposés en clair.

#### Critères d'acceptation

1. WHEN un mot de passe est enregistré ou modifié pour un Compte, THE Système SHALL le stocker
   sous forme hachée à l'aide d'un algorithme de hachage adapté aux mots de passe.
2. THE Système SHALL comparer les mots de passe fournis à la connexion à leur forme hachée
   sans jamais stocker ni journaliser le mot de passe en clair.
3. WHEN le Système retourne les informations d'un Compte via l'API, THE Système SHALL exclure
   le mot de passe (haché ou non) de la réponse.

### Exigence 6 : Compte administrateur initial

**User Story:** En tant qu'administrateur, je veux qu'un compte ADMIN existe dès le premier
démarrage, afin de ne jamais être verrouillé hors de l'application.

#### Critères d'acceptation

1. WHEN le Système démarre et qu'aucun Compte de Rôle ADMIN n'existe, THE Système SHALL créer
   un Compte_ADMIN_Initial.
2. THE Système SHALL permettre de définir l'Identifiant et le mot de passe du
   Compte_ADMIN_Initial par configuration externe, sans valeur secrète codée en dur dans le
   code source.
3. WHEN un Compte de Rôle ADMIN existe déjà au démarrage, THE Système SHALL conserver les
   Comptes existants sans les écraser.

### Exigence 7 : Gestion des comptes utilisateurs

**User Story:** En tant qu'administrateur, je veux gérer les comptes utilisateurs, afin de
donner un accès en consultation ou en administration aux bonnes personnes.

#### Critères d'acceptation

1. WHERE l'Utilisateur connecté a le Rôle ADMIN, THE Système SHALL permettre de créer un Compte
   en précisant un Identifiant, un mot de passe initial et un Rôle (ADMIN ou VIEWER).
2. IF une demande de création de Compte utilise un Identifiant déjà attribué, THEN THE Système
   SHALL rejeter la demande avec un code HTTP 409 et laisser les Comptes existants inchangés.
3. WHERE l'Utilisateur connecté a le Rôle ADMIN, THE Système SHALL permettre de désactiver un
   Compte afin d'en interdire la connexion sans le supprimer.
4. WHERE l'Utilisateur connecté a le Rôle ADMIN, THE Système SHALL permettre de réinitialiser
   le mot de passe d'un Compte.
5. IF un Utilisateur de Rôle VIEWER demande une opération de gestion de Compte, THEN THE
   Service_Autorisation SHALL refuser l'accès avec un code HTTP 403.

### Exigence 8 : Traçabilité « créé par » et « modifié par »

**User Story:** En tant qu'administrateur, je veux que chaque modification enregistre son
auteur, afin de savoir qui a créé ou modifié une donnée.

#### Critères d'acceptation

1. WHEN un enregistrement dérivant de `BaseEntity` est créé par un Utilisateur connecté, THE
   Système SHALL renseigner le Champ_Audit_Créé_Par avec l'Identifiant de cet Utilisateur.
2. WHEN un enregistrement dérivant de `BaseEntity` est modifié par un Utilisateur connecté, THE
   Système SHALL renseigner le Champ_Audit_Modifié_Par avec l'Identifiant de cet Utilisateur.
3. WHEN un enregistrement dérivant de `BaseEntity` est créé, THE Système SHALL renseigner le
   Champ_Audit_Créé_Par avec l'Identifiant réel de l'Utilisateur connecté au lieu de la valeur
   codée en dur « admin ».
4. WHILE une écriture est réalisée sans Utilisateur authentifié identifiable (contexte
   système ou tâche d'initialisation), THE Système SHALL renseigner les champs d'audit avec un
   Identifiant de repli explicite réservé à cet usage.

### Exigence 9 : Expiration de session et déconnexion

**User Story:** En tant qu'utilisateur, je veux pouvoir me déconnecter et voir ma session
expirer, afin que mon accès ne reste pas ouvert indéfiniment.

#### Critères d'acceptation

1. WHEN un Justificatif_Authentification atteint son Expiration, THE Service_Autorisation SHALL
   refuser les requêtes ultérieures présentant ce justificatif avec un code HTTP 401.
2. WHEN un Utilisateur demande la déconnexion, THE Système SHALL invalider la
   Session_Authentifiée courante côté client de sorte que le Justificatif_Authentification ne
   soit plus utilisé.
3. WHEN le frontend reçoit une réponse HTTP 401 sur une Ressource_Protégée, THE frontend SHALL
   rediriger l'Utilisateur vers l'écran de connexion.

### Exigence 10 : Écran de connexion du frontend

**User Story:** En tant qu'utilisateur, je veux un écran de connexion au démarrage, afin de
saisir mes identifiants avant d'accéder à l'application.

#### Critères d'acceptation

1. WHILE aucun Utilisateur n'est connecté, THE frontend SHALL afficher l'écran de connexion et
   empêcher l'accès aux vues métier protégées.
2. WHEN un Utilisateur soumet le formulaire de connexion avec succès, THE frontend SHALL
   conserver le Justificatif_Authentification et rediriger vers la vue principale.
3. IF la connexion échoue en raison d'identifiants invalides, THEN THE frontend SHALL afficher
   un message d'erreur traduisible sans révéler lequel des champs est erroné.
4. WHEN une requête est envoyée vers une Ressource_Protégée, THE frontend SHALL joindre le
   Justificatif_Authentification à la requête.

### Exigence 11 : Adaptation de l'interface selon le rôle

**User Story:** En tant qu'utilisateur en consultation, je veux que les actions d'écriture
soient indisponibles, afin de ne pas tenter des opérations qui me sont interdites.

#### Critères d'acceptation

1. WHERE l'Utilisateur connecté a le Rôle VIEWER, THE frontend SHALL masquer ou désactiver les
   commandes déclenchant des Opérations_Écriture (création, modification, suppression).
2. WHERE l'Utilisateur connecté a le Rôle ADMIN, THE frontend SHALL afficher et activer les
   commandes déclenchant des Opérations_Écriture.
3. IF un Utilisateur de Rôle VIEWER parvient tout de même à déclencher une Opération_Écriture,
   THEN THE Système SHALL s'appuyer sur le Service_Autorisation backend pour refuser
   l'opération, l'interface ne constituant pas l'unique protection.

### Exigence 12 : Respect des conventions du projet

**User Story:** En tant que mainteneur, je veux que la fonctionnalité respecte les conventions
existantes, afin de préserver la cohérence du code.

#### Critères d'acceptation

1. THE Système SHALL placer les entités JPA de la fonctionnalité dans le dossier `persistance`
   existant sans le renommer.
2. THE Système SHALL réaliser le mapping DTO ↔ entité via MappingContext, conformément aux
   conventions du projet.
3. THE Système SHALL conserver des contrôleurs minces en plaçant la logique métier
   d'authentification et d'autorisation dans des services dédiés.
4. THE Système SHALL préserver les commentaires et messages français existants dans le code.
5. WHERE une nouvelle fonctionnalité frontend nécessite un accès HTTP à une entité, THE Système
   SHALL suivre le motif « un service par entité » pour les appels HTTP.
6. THE Système SHALL fournir les chaînes visibles par l'Utilisateur en français et en anglais
   via ngx-translate.
