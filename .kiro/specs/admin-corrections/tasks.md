# Implementation Plan — Corrections administrateur, étape 1

Exigences 1, 2, 3, 7 et journal par Étudiant (8.2, 8.3, 8.5). Design : `design.md`.
Règles métier : `.kiro/steering/business-rules.md`, sections « Avant son inscription, l'étudiant
n'est pas concerné » et « Aucune dette d'une année scolaire sur l'autre ».

Chaque tâche se termine par `./mvnw test` vert (JDK 21). Les propriétés sont vérifiées par
mutation : un défaut injecté doit les faire échouer, puis le code est restauré.

## 1. Socle : fuseau, trace commune, Motif

- [ ] 1.1 Migration V6 : table `correction_audit`, séquence `correction_audit_rank_seq`, index ;
  troncature au jour de `student_groups.date_assigned`
  _Exigences : 1.5, 7.4, 7.5 — design D1, D3_
- [ ] 1.2 `CorrectionAuditEntity`, `CorrectionDomain`, `CorrectionField`, dépôt ; rang lu depuis la
  séquence, jamais calculé par `MAX + 1`
  _Exigences : 7.4_
- [ ] 1.3 `CorrectionReason.require` : trim, refus si vide ou > 500 caractères
  _Exigences : 7.1_
- [ ] 1.4 `CorrectionAuditService.record` : auteur par `AuditorAware`, écriture dans la transaction
  appelante
  _Exigences : 7.3, 7.6_
- [ ] 1.5 Contrôle du fuseau au démarrage : journaliser `ZoneId.systemDefault()`, refuser de
  démarrer s'il diffère de `app.expected-timezone` (défaut `Africa/Algiers`) ; valeur de test dans
  `src/test/resources/application.properties`
  _Design D1_
- [ ] 1.6 Tests : `CorrectionReason` (vide, espaces, 500, 501, trim) ; Trace attribuée et ordonnée

## 2. Fenêtre d'inscription et date réelle

- [ ] 2.1 `EnrolmentWindow` : `normalize`, `activeEnrolment`, `isConcerned`, `absencesOutside`,
  `assertWithinSchoolYear`
  _Exigences : 1.4, 1.5, 2.1 — design D1_
- [ ] 2.2 `StudentGroupEntity.onCreate` : ne pose la date du jour que si elle est absente ;
  normalisation au jour à l'enregistrement
  _Exigences : 1.1, 1.2 — design D2_
- [ ] 2.3 `StudentGroupDTO` : retrait de `@PastOrPresent` ; contrôle « dans l'année scolaire du
  Groupe » dans `addStudentsToGroup` et `addGroupsToStudent`
  _Exigences : 1.3, 1.4_
- [ ] 2.4 `getStudentsForSession` : comparaison sur la date de séance normalisée
  _Exigences : 2.1, 1.5_
- [ ] 2.5 Tests : date fournie conservée, date absente = aujourd'hui, date future acceptée, date
  hors année refusée, séance du jour de l'inscription incluse quelle que soit l'heure
  _Exigences : 1.1 à 1.5_

## 3. Refus serveur des absences hors fenêtre

- [ ] 3.1 `AttendanceService.saveAll` : valider toutes les lignes avant d'en écrire une ; refuser
  en bloc les absences hors fenêtre ou sans inscription active, corps `rejected` nommant chaque
  ligne ; les présences restent acceptées
  _Exigences : 2.3, 2.4, 2.5, 2.6 — design D4_
- [ ] 3.2 `POST /api/attendances` (création unitaire) : même contrôle
  _Exigences : 2.3_
- [ ] 3.3 Propriété 1 « fenêtre respectée à l'écriture », par mutation
  _Exigences : 2.3, 3.4_
- [ ] 3.4 Propriété 5 « jour calendaire »
  _Exigences : 1.5_

## 4. Correction de la date d'inscription

- [ ] 4.1 `EnrolmentCorrectionService.correctDate`, dans l'ordre des contrôles du design :
  404, année close, Motif, bornes de l'année, date inchangée, séance payée écartée (D6), absences
  en conflit (D5), écritures
  _Exigences : 1.6 à 1.12, 7.2 — design D5, D6, D7_
- [ ] 4.2 `PATCH /api/enrolments/{id}/date-assigned` ; corps 409 `conflictingAbsences` ou
  `paidSessions`
  _Exigences : 1.6, 1.8_
- [ ] 4.3 Recalcul du statut stocké du paiement de la Série après correction
  _Design D7_
- [ ] 4.4 Propriété 2 « corriger équivaut à avoir bien saisi » (métamorphique)
  _Exigences : 1.11_
- [ ] 4.5 Propriété 3 « indivisibilité » de la correction combinée
  _Exigences : 1.9, 7.6_
- [ ] 4.6 Tests : liste présentée différente de la liste à jour → 409 sans écriture ; année close
  → 409 ; séance payée écartée → 409 nommant séance et montant
  _Exigences : 1.10, 1.12 — design D6_

## 5. Correction d'une présence

- [ ] 5.1 `AttendanceCorrectionService` : `setPresence`, `addToValidatedSession`, `remove`
  (désactivation), avec les contrôles du tableau du design ; passage à présent efface
  `isJustified` dans la même Trace ; rattrapage renvoyé vers `/api/catch-up-billing/{id}/correct`
  _Exigences : 3.1 à 3.8 — design D6, D7_
- [ ] 5.2 Points d'entrée : `PATCH /api/attendances/{id}/presence`,
  `POST /api/sessions/{sessionId}/attendances/corrections`, `POST /api/attendances/{id}/removal`
  _Exigences : 3.1, 3.2, 3.3_
- [ ] 5.3 Propriété 4 « une Trace par changement effectif », sur les deux services de correction
  _Exigences : 7.2, 7.4, 7.6_

## 6. Journal par Étudiant

- [ ] 6.1 `CorrectionJournalService.forStudent` : `correction_audit` + trois tables existantes,
  la plus récente d'abord
  _Exigences : 8.2, 8.3_
- [ ] 6.2 `GET /api/students/{id}/corrections`, lecture ouverte à ADMIN et VIEWER
  _Exigences : 8.5_
- [ ] 6.3 Tests : les quatre sources présentes et ordonnées ; Trace conservée après
  désactivation de la donnée
  _Exigences : 7.5, 8.2, 8.3_

## 7. Contrat HTTP et autorisation

- [ ] 7.1 `AdminCorrectionEndpointIntegrationTest` (`@SpringBootTest`, H2, MockMvc) : statuts,
  corps, absence d'écriture après chaque refus, sur le modèle de
  `PaymentProcessingEndpointIntegrationTest`
  _Exigences : 1, 2, 3_
- [ ] 7.2 403 pour le rôle VIEWER sur chaque point d'entrée de correction ; 200 en lecture du
  journal
  _Exigences : 7.7, 8.5_

## 8. Frontend

- [ ] 8.1 `session-modal` : suppression du repli « tout le groupe » ; affichage d'une Feuille_Appel
  vide avec explication ; affichage du corps `rejected` à la validation
  _Exigences : 2.1, 2.2, 2.6_
- [ ] 8.2 Ajout d'élèves (`group-profile`, `student-profile`) : champ « date d'arrivée », défaut
  aujourd'hui, borné à l'année scolaire
  _Exigences : 1.1 à 1.4_
- [ ] 8.3 Fiche élève : dialogue « corriger la date d'arrivée » ; sur 409, liste des absences et
  case « les retirer avec la correction » ; liste des séances payées bloquantes
  _Exigences : 1.6 à 1.12_
- [ ] 8.4 `session-modal` sur séance validée : « corriger » par élève (présent / absent / retirer)
  et « ajouter un élève », avec Motif
  _Exigences : 3.1 à 3.3_
- [ ] 8.5 Fiche élève : onglet « journal des corrections »
  _Exigences : 8.5_
- [ ] 8.6 Clés i18n FR et EN, parité vérifiée
- [ ] 8.7 `npm run build` vert

## 9. Livraison

- [ ] 9.1 Suite complète verte, build front vert
- [ ] 9.2 Vérification de bout en bout avec `docker compose up` sur une base neuve : V1 à V6
  appliquées, fuseau `Africa/Algiers` journalisé, parcours « élève arrivé à la 3ᵉ séance »
  _Exigences : 2, design D1_
