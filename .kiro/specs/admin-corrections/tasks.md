# Implementation Plan — Corrections administrateur, étape 1

Exigences 1 à 12. Design : `design.md`. Règles métier : `.kiro/steering/business-rules.md`.
Branche : `feat/admin-corrections`.

Quatre lots, chacun déployable seul et vérifié chez le client avant le suivant. Chaque tâche se
termine par `./mvnw test` vert (JDK 21) ; chaque propriété est vérifiée par mutation, puis le code
est restauré.

## Lot A — L'Encaissement (exigences 1, 11)

Socle de tout le reste : sans Encaissement identifiable, aucune correction d'argent n'est possible.

- [ ] A.1 Inventaire des lecteurs qui supposent une ligne de ventilation unique par (paiement,
  Séance), dont `findByPaymentIdAndSessionId` ; liste consignée dans ce fichier avant tout code
  _Design : Risques, D3_
- [ ] A.2 Migration V6 (partie paiement) : `encashment`, `encashment_allocation`, colonnes
  `encashment_id` sur ventilation, report, idempotence ; `correction_audit` et sa séquence
  _Exigences : 1.1, 1.3, 11.4 — D2, D3, D8_
- [ ] A.3 `EncashmentService` : création, `RECU-AAAA-NNNN` (modèle `RefundNumberService`),
  neutralisation ; cumul de Série recalculé depuis les Imputations actives
  _Exigences : 1.1, 1.2, 1.4 — D2, D3_
- [ ] A.4 `PaymentProcessingService` et chemin rattrapage : encaissement via `EncashmentService` ;
  idempotence sur le chemin rattrapage
  _Exigences : 1.1, 1.7_
- [ ] A.5 `PaymentDistributionService` : une ligne par (Encaissement, Séance), plafond au prix net ;
  lecteurs de A.1 adaptés
  _Exigences : 1.3, 1.6 — D3_
- [ ] A.6 `recalculatePayment` ne réécrit plus le cumul depuis la ventilation
  _Défaut 2 — D3_
- [ ] A.7 Migration V7 : reprise `LEGACY` des cumuls existants
  _Exigences : 1.8_
- [ ] A.8 Propriété P1 « conservation de l'argent » ; propriété P8 « reprise sans perte »
- [ ] A.9 Reçu : `receipt_number` renvoyé par l'API et imprimé ; `GET /api/encashments/{id}`
  _Exigences : 1.2_
- [ ] A.10 Historique élève : liste des Encaissements
  _Exigences : 1.1_

## Lot B — Corriger l'argent (exigences 2, 3, 4)

- [ ] B.1 `CorrectionRunner` : exécution en mode PREVIEW / CONFIRM, photographie des Séries, Aperçu
  canonique, jeton SHA-256, 409 sur Aperçu périmé
  _Exigences : 4.1 à 4.4 — D7_
- [ ] B.2 `CorrectionReason`, `CorrectionReasonType`, `CorrectionAuditService` avec `summary` et
  `amount_effect` rédigés à l'écriture
  _Exigences : 11.1 à 11.6 — D8, D10_
- [ ] B.3 `EncashmentCorrectionService.cancel` : neutralisation, refus sous le total remboursé,
  refus d'une seconde annulation, année close
  _Exigences : 2.1 à 2.5, 2.7_
- [ ] B.4 `EncashmentCorrectionService.correct` : Remplacement par le chemin ordinaire, liens
  dans les deux sens ; mode et note sans Remplacement
  _Exigences : 3.1 à 3.6 — D4_
- [ ] B.5 Points d'entrée `cancel` et `correct`, `preview` et `confirm`
- [ ] B.6 Propriétés P2 « remplacer équivaut à avoir bien saisi », P3 « indivisibilité »,
  P4 « l'Aperçu ne ment pas »
- [ ] B.7 `CorrectionPreviewDialog` (composant commun) ; actions « annuler », « corriger »,
  « réimprimer » dans l'historique ; tampon « ANNULÉ » à la réimpression
  _Exigences : 2.6, 3.7, 4.1_

## Lot C — Dates d'arrivée et de départ, Feuille_Appel (exigences 5, 6, 7)

- [ ] C.1 V6 (partie inscription) : `date_left`, troncature au jour, `date_left` des clôtures
  existantes
  _D1, D5_
- [ ] C.2 `EnrolmentWindow` ; `StudentGroupEntity.onCreate` ne pose la date que si elle est
  absente ; retrait de `@PastOrPresent`, contrôle dans l'année courante
  _Exigences : 5.1 à 5.4 — D1, D5_
- [ ] C.3 Feuille_Appel par fenêtre, clôtures comprises ; suppression du repli côté écran
  _Exigences : 6.2, 7.1, 7.2_
- [ ] C.4 Refus serveur des absences hors fenêtre, en bloc à la validation, sur tous les points
  d'entrée
  _Exigences : 7.3 à 7.5_
- [ ] C.5 Résolveur : Séances facturables d'une inscription clôturée par sa fenêtre
  _Changement de calcul assumé — D5_
- [ ] C.6 `EnrolmentCorrectionService` : arrivée reculée (absences, présences ordinaires) et
  avancée (Séances validées sans Présence), départ, correction de départ, réouverture ;
  `VentilationMover`
  _Exigences : 5.5 à 5.9, 6.1, 6.3 à 6.5 — D6_
- [ ] C.7 Propriétés P5 « fenêtre respectée », P6 « déplacer la ventilation ne change aucun
  montant »
- [ ] C.8 Écrans : date d'arrivée à l'inscription ; corriger l'arrivée, enregistrer le départ,
  rouvrir ; lignes refusées retirables en un clic à la validation
  _Exigences : 5, 6, 7.5_
- [ ] C.9 Contrôle du fuseau au démarrage et bandeau d'alerte
  _D1_

## Lot D — Présences, rattrapages, dévalidation, Journal (exigences 8, 9, 10, 12)

- [ ] D.1 `AttendanceCorrectionService` : présent ↔ absent avec justification, ajout, retrait
  par désactivation
  _Exigences : 8.1 à 8.6_
- [ ] D.2 Retrait d'une présence de rattrapage, réouverture du droit, demande passée à
  `CANCELLED`
  _Exigences : 9.1 à 9.3 — D9_
- [ ] D.3 Dévalidation avec Motif et Aperçu ; validation et dévalidation refusées sur année close
  _Exigences : 10.1, 10.2_
- [ ] D.4 `CorrectionJournalService` : quatre sources, entrées en français, effet sur le dû ;
  adaptateurs des trois audits existants
  _Exigences : 12.1 à 12.3, 12.5 — D8_
- [ ] D.5 Journal imprimable par période
  _Exigences : 12.4_
- [ ] D.6 Propriété P7 « une Trace par changement effectif »
- [ ] D.7 `session-modal` : corriger par élève, ajouter un élève, dévalider avec motif ; fiche élève,
  onglet journal
  _Exigences : 8, 10, 12_

## Transverse, à chaque lot

- [ ] T.1 Tests HTTP de bout en bout du lot : statuts, corps, absence d'écriture après refus,
  403 VIEWER sur chaque `preview` et `confirm`
- [ ] T.2 Tests Karma des composants du lot
- [ ] T.3 Clés i18n FR et EN, parité vérifiée
- [ ] T.4 `npm run build` et `./mvnw test` verts
- [ ] T.5 Déploiement sur une copie de la base de l'école : sauvegarde, `docker compose up`,
  vérification des migrations et du fuseau, parcours du lot, retour arrière testé

## Livraison

- [ ] L.1 Script `mise-a-jour.ps1` : sauvegarde datée, mise à jour, vérification, retour arrière
- [ ] L.2 Mode d'emploi administrateur d'une page par lot, en français, avec captures
