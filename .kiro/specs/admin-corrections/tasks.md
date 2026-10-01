# Implementation Plan — Corrections administrateur, étape 1

Exigences 1 à 12. Design : `design.md`. Règles métier : `.kiro/steering/business-rules.md`.
Branche : `feat/admin-corrections`.

Quatre lots, chacun déployable seul et vérifié chez le client avant le suivant. Chaque tâche se
termine par `./mvnw test` vert (JDK 21) ; chaque propriété est vérifiée par mutation, puis le code
est restauré.

## Lot A — L'Encaissement (exigences 1, 11)

Socle de tout le reste : sans Encaissement identifiable, aucune correction d'argent n'est possible.

- [x] A.1 Inventaire des lecteurs qui supposent une ligne de ventilation unique par (paiement,
  Séance), dont `findByPaymentIdAndSessionId` ; liste consignée dans ce fichier avant tout code
  _Design : Risques, D3_ — voir « Inventaire A.1 » ci-dessous
- [x] A.2 Migration V6 (partie paiement), structure seulement : `encashment`,
  `encashment_allocation`, `correction_audit` ; liens facultatifs sur ventilation, report,
  idempotence ; entités et dépôts. Tests : `MigrationSchemaPostgresIntegrationTest` (V1 à V6 sur
  PostgreSQL jetable, validation Hibernate de toutes les entités, contraintes en SQL, vérifié par
  mutation) et `EncashmentPersistenceIntegrationTest` (écriture et relecture JPA)
  _Exigences : 1.1, 1.3, 11.4 — D2, D3, D8_
- [x] A.3 `EncashmentService` : création, imputation, neutralisation ; cumul de Série recalculé
  depuis les Imputations actives. `RECU-AAAA-NNNN` par compteur verrouillé (`receipt_counter`,
  ajouté à V6 jamais appliquée hors tests) au lieu du modèle `RefundNumberService`, dont le rejeu
  échoue sur PostgreSQL. `PaymentLineStatus` partagé ; `CorrectionReason` créé dès A.3 (prévu en
  B.2). Tests : `EncashmentServiceIntegrationTest`, `ReceiptNumberServiceTest`,
  `PaymentLineStatusTest`, `CorrectionReasonTest`, et sur PostgreSQL ligne unique, verrou entre
  deux transactions, annulation sans numéro consommé. Mutations vérifiées : verrou retiré, plafond
  d'imputation retiré
  _Exigences : 1.1, 1.2, 1.4 — D2, D3_
- [x] A.4 `PaymentProcessingService` et chemin rattrapage : encaissement via `EncashmentService` ;
  idempotence sur le chemin rattrapage. Fait :
  - chaque versement ouvre un Encaissement (`REGULAR` ou `CATCH_UP`) portant mode et note ; chaque
    part du plan est une Imputation ; le cumul n'est plus incrémenté, il est recalculé ;
  - report rattaché à son Imputation (refus si elle ne lui correspond pas) ; une seule date,
    celle de l'Encaissement ;
  - empreinte d'idempotence : désigne l'Encaissement, porte la séance d'un rattrapage
    (`payment_idempotency.session_id`, ajoutée à V6) ; rejeu relu depuis les Imputations ;
  - rattrapage : `Idempotency-Key`, plafond = prix net de la séance et reste dû de la série, pas
    de report ; statut au prorata (l'ancienne règle « présences × tarif catalogue » est retirée).
    Changement assumé : un rattrapage compensatoire ou « à préciser » n'est plus encaissable ;
  - front : la clé est aussi envoyée sur `/process/catch-up` (chemin que l'écran n'emprunte pas
    aujourd'hui, `nextCatchUpSessionId` n'étant jamais renseigné).
  Tests : `PaymentProcessingServiceTest`, `PaymentIdempotencyServiceTest`,
  `PaymentCarryOverServiceTest`, `PaymentProcessingEndpointIntegrationTest` (Encaissement de bout en
  bout, annulation qui fait retomber le cumul, six cas du rattrapage). Mutations vérifiées : lien du
  report retiré, séance hors empreinte, rejeu sur les seules Imputations actives, plafond du
  rattrapage retiré
  _Exigences : 1.1, 1.7_
- [ ] A.5 `PaymentDistributionService` : une ligne par (Encaissement, Séance), plafond au prix net ;
  lecteurs de A.1 adaptés
  _Exigences : 1.3, 1.6 — D3_
- [ ] A.6 `recalculatePayment` ne réécrit plus le cumul depuis la ventilation ; migration V7 :
  liens vers l'Encaissement `NOT NULL`, et bascule de l'assertion « facultatifs » du test des
  migrations
  _Défaut 2 — D3, Data Models_
- [ ] A.7 Propriété P1 « conservation de l'argent »
  _Exigences : 1.4_
- [ ] A.8 Reçu : `receipt_number` renvoyé par l'API et imprimé ; `GET /api/encashments/{id}`
  _Exigences : 1.2_
- [ ] A.9 Historique élève : liste des Encaissements
  _Exigences : 1.1_

### Inventaire A.1 — ventilation : une ligne par (paiement, Séance) aujourd'hui

Établi par lecture du code (branche `feat/admin-corrections`, base `c43394e`). Les défauts
marqués « déjà présent » sont déduits du code, non reproduits par un test : A.5 les épingle.

**Lecteurs qui casseraient ou fausseraient avec plusieurs lignes actives par Séance**

| # | Emplacement | Hypothèse | Effet avec plusieurs lignes |
|---|---|---|---|
| 1 | `PaymentDetailRepository.findByPaymentIdAndSessionId` (l.25), seul appelant `PaymentDistributionService.distributeToSession` (l.155) | `Optional` : une ligne au plus | `IncorrectResultSizeDataAccessException`, le versement est annulé. **Déjà présent** : la branche « ligne inactive » (l.203-213) crée une 2ᵉ ligne à côté de l'inactive ; le versement suivant sur la Séance échoue |
| 2 | `distributeToSession`, complément (l.188-200) | `stillOwed = prix − ligne unique` | doit devenir prix net − somme des lignes actives de la Séance, tous Encaissements confondus (1.6) |
| 3 | `distributeToSession`, ligne supprimée définitivement (l.161-166) | une suppression bloque la Séance | `IllegalStateException`, donc 500. **Déjà présent** : après la suppression d'une ligne par l'admin, plus aucun versement ne peut toucher cette Séance |
| 4 | `StudentHistoryService` l.201, `toMap(…, (existing, r) -> existing)` ; lu l.706 | une ligne par Séance | Séance non facturable : seul le montant de la première ligne s'affiche. Les Séances facturables passent par `allocatePayments` (cascade) et restent justes |
| 5 | `PaymentCrudService.convertToPaymentDetailDto` (`remainingBalance = prix catalogue − ligne`) | une ligne = la Séance | reste faux sur chaque ligne. **Déjà présent** : prix catalogue au lieu du prix net |
| 6 | `PaymentCrudService.getPaymentDetailsForSeries` → `payment-confirmation-dialog` (`paymentDetails: PaymentDetail[]`) | une ligne par Séance à l'écran | plusieurs lignes par Séance dans le récapitulatif ; affichage non relu en détail, à regrouper |
| 7 | `payment-management.component` (admin) : une ligne de tableau par ligne de ventilation, modifier / supprimer / rembourser | idem | doublons par Séance ; à remplacer par la liste des Encaissements (lot B) |

**Lecteurs justes avec plusieurs lignes** : agrégations `SUM … GROUP BY` par groupe, série, Séance
et mois (`sumCollected*`, `revenue*`) ; `PaymentStatusService.getSessionPaymentStatuses` (somme
des lignes) ; `StudentHistoryService.allocatePayments`.

**Écritures de `payment_detail`, à faire passer toutes par l'Encaissement**

- `PaymentDistributionService.distributeToSession` : trois créations et un complément.
- `PaymentDetailAdminService` : modification libre du montant, suppression, réactivation,
  puis `recalculatePayment` (défaut 2, A.6). La modification libre d'un montant viole
  l'invariant 1.4 : à retirer au profit de l'Annulation et du Remplacement (lot B).
- `PaymentDetailDeactivationService` (appelé par `SessionService`) : désactive et **réactive**
  toutes les lignes d'une Séance. La réactivation doit ignorer les lignes d'un Encaissement
  annulé, sinon de l'argent annulé redevient actif.

**Lecteurs de la date d'une ligne, à faire lire `encashment.received_at`**

- `sumCollectedByGroupGroupedByMonth`, `revenueByMonth`, filtre de dates `REVENUE_FILTERS`,
  `findAllWithFilters`, `countWithFilters` : `pd.paymentDate`. **Déjà présent** : une ligne
  complétée voit sa date réécrite, donc une Séance payée en deux fois, sur deux mois, est
  entièrement comptée au mois du second versement. Le nouveau modèle corrige ce défaut.
- `searchPaymentDetailsWithCompleteData` : `pd.dateCreation`.
- `StudentHistoryService` : tri chronologique des lignes et date affichée par Séance.
- `PaymentCrudService.convertToPaymentDetailDto` : date affichée.

**Autres constats**

- `PaymentStatusService.getPaidSessions` : toute ligne, même inactive, annulée ou partielle,
  rend la Séance « payée ». **Déjà présent**, sans lien avec le nombre de lignes.
- `sumAmountByStudentAndGroup`, `sumAmountByStudentAndSeries` : sans filtre actif ni annulé,
  sans appelant. Code mort, à supprimer.
- Branche « paiement CANCELLED » de `distributeToSession` (l.169-184) : inatteignable, la
  recherche portant sur le paiement courant non annulé.

**Tests qui figent l'hypothèse d'unicité, à réécrire**

- `PaymentDistributionServiceTest` : bouchonne `findByPaymentIdAndSessionId → Optional.empty()`.
- `PaymentDetailAdminServiceTest` : `recalculatePayment` (cumul = somme des lignes actives,
  `PENDING`, `CANCELLED`) fige le défaut 2 ; à réécrire avec A.6.
- `PaymentStatusServiceTest` : lit déjà une liste par Séance, inchangé.

## Lot B — Corriger l'argent (exigences 2, 3, 4)

- [ ] B.1 `CorrectionRunner` : exécution en mode PREVIEW / CONFIRM, photographie des Séries, Aperçu
  canonique, jeton SHA-256, 409 sur Aperçu périmé
  _Exigences : 4.1 à 4.4 — D7_
- [ ] B.2 `CorrectionAuditService` (`CorrectionReason` et `CorrectionReasonType` livrés en A.2/A.3) avec `summary` et
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

- [ ] C.1 V6 (partie inscription) : colonne `date_left`
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
- [ ] T.5 Déploiement sur une base neuve avec `docker compose up` : migrations et fuseau
  vérifiés, parcours du lot

## Livraison

- [ ] L.1 Script `mise-a-jour.ps1` pour les mises à jour après la mise en service : sauvegarde
  datée, mise à jour, vérification, retour arrière. Inutile pour l'installation initiale, qui
  part d'une base vide
- [ ] L.2 Mode d'emploi administrateur d'une page par lot, en français, avec captures
