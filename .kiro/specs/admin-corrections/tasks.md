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
- [x] A.5 `PaymentDistributionService` : une ligne par (Encaissement, Séance), plafond au prix net ;
  lecteurs de A.1 adaptés. Fait :
  - `distribute(imputation)` : lignes neuves seulement, chacune rattachée à son Imputation et datée
    de son Encaissement ; plafond = prix net − lignes actives de la séance ; une ligne inactive ou
    supprimée définitivement ne bloque plus (fin de l'erreur 500) ; `isCatchUp` renseigné ;
  - prix net : une seule définition, `PaymentQuoteService.netPricePerSession` ;
  - `PaymentDetailEntity` : une mise à jour ne redate plus la ligne (recettes par mois justes) ;
  - lecteurs : historique (somme des lignes d'une séance non facturable), reste à régler du détail
    de série (prix net − lignes de la séance), séances payées / impayées (seuil au prix net,
    lignes inactives exclues), revalidation d'une séance (lignes d'un Encaissement annulé ou
    supprimées définitivement laissées inactives) ;
  - supprimés : `findByPaymentIdAndSessionId`, `sumAmountByStudentAndGroup/Series` (morts),
    `calculateTotalCost` et l'alerte au tarif catalogue.
  Lecteur 6 (écran de confirmation) : la liste est transmise mais pas affichée, rien à regrouper ;
  lecteur 7 (gestion admin) reste au lot B. Tests : `PaymentDistributionServiceTest` réécrit,
  `PaymentDetailVentilationIntegrationTest` (H2), `PaymentDetailDeactivationServiceTest`,
  `PaymentCrudServiceSeriesDetailsTest`, `PaymentStatusServiceTest`, `StudentHistoryServiceTest`,
  bout en bout (deux Encaissements sur une séance, annulation de l'un). Huit mutations vérifiées
  _Exigences : 1.3, 1.6 — D3_
- [x] A.6 `recalculatePayment` ne réécrit plus le cumul depuis la ventilation ; migration V7 :
  liens vers l'Encaissement `NOT NULL`, et bascule de l'assertion « facultatifs » du test des
  migrations. Fait :
  - `recalculatePayment` = verrou + `EncashmentService.refreshSeriesCumul` : corriger, supprimer
    ou réactiver une ligne ne change plus l'argent reçu ; la règle « toutes les lignes supprimées
    → CANCELLED » disparaît (elle sortait la série des devis avec son argent) ;
  - réactiver une ligne d'un Encaissement annulé (réactivation ou « active = vrai ») : 409 ;
  - `POST /api/payments` retiré (cumul pris tel quel dans la requête, sans Encaissement ; aucun
    écran ne l'appelait), avec `PaymentCrudService.createPayment/save` et le service front ;
  - V7 et `@JoinColumn(nullable = false)` sur les trois liens : H2 refuse comme PostgreSQL une
    ligne sans Encaissement ; fixtures H2 rattachées à une Imputation ;
  - base locale `schoolManagement4` réinitialisée (sauvegarde `~/schoolManagement4-avant-A6.dump`),
    V1 à V7 appliquées au démarrage, encaissement de contrôle par l'API puis nettoyé ;
    `reset-database.sql` couvre les tables d'Encaissement et remet le compteur de reçus à zéro.
  Tests : `PaymentDetailAdminServiceTest` réécrit, bout en bout « défaut 2 », test PostgreSQL
  (V7, refus SQL des trois lignes sans Encaissement), H2 (ligne sans Imputation refusée). Quatre
  mutations vérifiées.
  Décision (propriétaire produit, aucune installation avant la fin des quatre lots) : une ligne
  de ventilation ne se corrige plus à l'unité. Modifier, désactiver, supprimer ou réactiver une
  ligne ne corrigeait pas le versement mais faisait diverger les recettes (qui somment la
  ventilation) du registre. `PATCH`, `DELETE` et `POST …/reactivate` sur `/api/payment-details`
  renvoient 409 en nommant le reçu à annuler ou corriger ; `recalculatePayment`,
  `PaymentDetailUpdateDTO`, `logAction` et les dialogues front de modification et de motif sont
  retirés ; l'écran « Gestion des paiements » garde la recherche, l'historique et le
  remboursement, avec une note qui renvoie au reçu. Mutation vérifiée (refus retiré)
  _Défaut 2 — D3, Data Models_
- [x] A.7 Propriété P1 « conservation de l'argent » : `MoneyConservationPropertyTest` (jqwik,
  H2 réelle, 100 scénarios de 1 à 12 étapes : versements acceptés ou refusés, reports, séries non
  ouvertes, annulations). Après chaque étape, relus en SQL : cumul = Imputations actives,
  encaissements actifs = somme des cumuls, encaissement actif imputé en entier et annulé sans rien
  d'actif, ventilation complète et un report par Imputation reportée, cumul ≤ coût, reçus
  consécutifs, refus sans effet. Couverture exigée des refus, reports, annulations et annulations
  d'un versement reporté. Mutations vérifiées : ligne non désactivée à l'annulation, cumul
  incrémenté en plus de l'Imputation. Remplacement : ajouté à la propriété avec le lot B (B.6)
  _Exigences : 1.4_
- [x] A.8 Reçu : `receipt_number` renvoyé par l'API et imprimé ; `GET /api/encashments/{id}`.
  Fait :
  - `/process` et `/process/catch-up` renvoient tous deux `PaymentAllocationResultDTO`, enrichi de
    l'Encaissement enregistré (`EncashmentDTO` : numéro, statut, nature, montant reçu, mode,
    date, auteur, Imputations, annulation, remplacement). Le rattrapage renvoyait une ligne de
    paiement ;
  - `GET /api/encashments/{id}` (réimpression) et `GET /api/students/{id}/encashments`
    (historique, plus récent d'abord, annulés compris), 404 sur identifiant inconnu, réservés à
    l'ADMIN comme les recettes (`SecurityConfig`) ;
  - le reçu imprime le numéro, la date et l'auteur du serveur : `buildReference` (identifiant de
    ligne + heure du navigateur) et la signature par le compte connecté sont retirés ; le montant
    imprimé est celui de l'Encaissement, jamais le cumul de la série ;
  - front : modèle `Encashment`, `EncashmentService` (lecture seule).
  Tests : bout en bout (réponse porteuse du reçu sur les deux chemins, relecture, historique,
  404), autorisation (ADMIN seul), `PaymentDialogComponent` (numéro, date, auteur, montant du
  versement, rattrapage), `EncashmentService` (adresses, motifs). Mutations vérifiées : règle de
  sécurité retirée, référence fabriquée, date du navigateur, auteur fixe, cumul imprimé, adresse
  de l'historique.
  Au passage : le seuil JaCoCo de `PaymentStatusService` (100 %) n'était plus tenu ; toutes les
  branches non couvertes étaient dans le code introduit par A.5 (lecture de la ventilation par
  séance, prix net). Gardes mortes retirées (paiement et montant `NOT NULL`,
  série sans identifiant) ; testés : ligne d'un paiement annulé, ligne sans séance, séance hors
  série au tarif du groupe, séance sans tarif. Quatre mutations vérifiées. Le contrôle se lit
  désormais au code de sortie de Maven, pas seulement aux rapports surefire
  _Exigences : 1.2_
- [x] A.9 Historique élève : liste des Encaissements. Fait :
  - panneau « Versements » sur la fiche élève (`StudentEncashmentsComponent`, ADMIN seul via
    `*appHasRole`) : un reçu par ligne, le plus récent d'abord — numéro, statut en toutes
    lettres, rattrapage, montant, date et heure, groupe, série visée, mode, auteur, note, une
    ligne par report ; un versement annulé reste listé, barré, avec date et auteur de
    l'annulation ; liens « remplace / remplacé par » affichés dès que B.4 les renseigne ;
  - réimpression : l'Encaissement est relu (`GET /api/encashments/{id}`) ; annulé entre-temps,
    rien n'est imprimé et la liste se recharge. Le reçu (`receiptFromEncashment`) reprend
    numéro, date, auteur, montant, imputé et reports ; la situation de la série au moment du
    versement n'étant pas conservée, elle n'est pas réimprimée. Un versement annulé ne se
    réimprime pas encore : le tampon « ANNULÉ » vient avec B.7 ;
  - la liste se recharge après un encaissement depuis la fiche ;
  - modes de règlement partagés (`PAYMENT_METHOD_OPTIONS`) entre dialogue et historique ;
  - le dialogue « Historique des paiements » (relevé par série et par séance) reste : il porte
    la facturation séance par séance (séances écartées, rattrapages à préciser).
  Tests : `receiptFromEncashment`, `StudentEncashmentsComponent` (ordre, contenu, annulé,
  vide, erreur, réimpression relue, annulé entre-temps), fiche (rechargement après versement,
  rien après annulation du dialogue). Mutations vérifiées : reports comptés dans l'imputé,
  réimpression d'un annulé, impression sans relecture, rechargement absent ou systématique
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

- [x] B.1 `CorrectionRunner` : exécution en mode PREVIEW / CONFIRM, photographie des Séries, Aperçu
  canonique, jeton SHA-256, 409 sur Aperçu périmé. Fait (`service/correction`) :
  - `CorrectionCommand` (empreinte de la commande, portée, exécution) ; `CorrectionScope` déclare
    avant toute écriture les Séries (ou groupes entiers) susceptibles d'être touchées ; une Série
    touchée hors portée est une erreur de programmation, refusée ;
  - `AmountSnapshot` (coût, dû à ce jour, versé, reste, en retard), tiré de `PaymentCostResolver` ;
    `CorrectionPreview` ne liste que les Séries dont un montant change, et dit explicitement quand
    aucun ne change (4.4) ; effets typés (`CorrectionEffectType`) ;
  - une transaction par exécution, refusée dans une transaction déjà ouverte ; écriture forcée
    avant la mesure « après », pour qu'une correction que la base refuserait échoue dès l'Aperçu ;
  - jeton = SHA-256 du format canonique (version, empreinte de la commande, montants, effets,
    champs préfixés de leur longueur) : un jeton ne confirme pas une autre commande ; jeton
    manquant : 400 ; différent : `StalePreviewException`, 409 avec le nouvel Aperçu et son jeton
    (`GlobalExceptionHandler`, `STALE_PREVIEW`) ;
  - un Aperçu ne consomme aucun numéro de reçu.
  Tests : `CorrectionRunnerIntegrationTest` (H2, vrais services : Aperçu sans écriture, numéro non
  consommé, Séries inchangées tues, confirmation = Aperçu, Aperçu périmé par un montant ou par un
  effet seul, jeton d'une autre commande, jeton manquant, refus métier après écriture, refus de la
  base dès l'Aperçu, portée, transaction ouverte), `CorrectionValuesTest`,
  `StalePreviewErrorHandlingTest`. Runner et valeurs sous le seuil JaCoCo 100 %. Onze mutations
  vérifiées (annulation de l'Aperçu, comparaison du jeton, commande, montants et effets hors
  empreinte, « avant » mesuré après, flush, portée, transaction ouverte, Séries inchangées,
  gestionnaire du 409)
  _Exigences : 4.1 à 4.4 — D7_
- [x] B.2 `CorrectionAuditService` (`CorrectionReason` et `CorrectionReasonType` livrés en A.2/A.3) avec `summary` et
  `amount_effect` rédigés à l'écriture. Fait :
  - la correction rédige ses traces (`AuditDraft` : domaine, action, donnée, étudiant, groupe,
    séance, Série, valeurs avant/après, résumé, motif) et les rend au runner dans
    `CorrectionExecution.audits` ; le runner les écrit une fois l'Aperçu mesuré, dans les deux
    modes et avant la comparaison du jeton : aucune trace après un Aperçu, un Aperçu périmé ou
    un refus (11.5) ;
  - `amount_effect` rédigé par `AmountEffectWriter` depuis les Séries changées de l'étudiant de
    la trace : « Janvier (Math 1ère A) : versé 3 000,00 → 0,00 DA, reste 1 000,00 → 4 000,00 DA,
    à jour → en retard » ; seuls les montants qui changent ; espace ordinaire entre les milliers
    (imprimable en PDF) ;
  - auteur = utilisateur authentifié lu dans le contexte de sécurité ; sans lui, refus 401
    plutôt qu'une trace signée « system » (11.4) ; rang = identifiant (11.4) ; `MANDATORY` :
    aucune trace hors de la transaction d'une correction ;
  - refus 400 « sans changement effectif » : valeurs avant et après identiques, ou correction
    qui ne rend aucune trace (11.3) ; une correction qui change un montant ou produit un effet
    sans trace est une erreur de programmation ;
  - valeurs structurées en JSON, clés triées, montants à leur échelle (`3000.00`, et non
    `3E+3` que produisait Jackson par défaut) ; résumé et effet ramenés à 500 caractères.
  Tests : `CorrectionRunnerIntegrationTest$Trace` (trace complète à la confirmation, aucune
  sans confirmation, refus sans authentification, valeurs identiques, rangs, survie à la
  disparition des données), `CorrectionAuditServiceIntegrationTest`, `AmountEffectWriterTest`.
  Quatorze mutations vérifiées
  _Exigences : 11.1 à 11.6 — D8, D10_
- [x] B.3 `EncashmentCorrectionService.cancel` : neutralisation, refus sous le total remboursé,
  refus d'une seconde annulation, année close. Fait :
  - commande exécutée par le runner ; portée = le groupe entier de l'Encaissement (Série visée et
    Séries suivantes du report), le runner refusant toute Série touchée hors portée ;
  - neutralisation par `EncashmentService.neutralize` (Imputations, reports, ventilation,
    cumuls) ; l'Encaissement reste au registre, marqué date, auteur, Motif (2.2, 2.3) ;
  - refus : introuvable (404) ; déjà annulé, « le JJ/MM/AAAA par X » (409, 2.5) ; année close,
    reçu nommé (409, 2.7) ; versé sous le remboursé, vérifié **après** neutralisation sur le
    cumul recalculé, chaque remboursement nommé (numéro, date, montant) — `RefundFloorException`,
    409 `REFUND_FLOOR` avec `blockingRefunds` (2.4) ; descendre exactement au remboursé est permis ;
  - Motifs admis : erreur de saisie, mauvais élève, montant mal saisi, autre (D10) ; un autre
    Motif : 400 avant toute exécution ; le Motif et son texte entrent dans l'empreinte ;
  - effets : « Reçu … de 3 000,00 DA annulé », « Imputation de … sur « Janvier » neutralisée »,
    « Report de … sur « Février » neutralisé » ; trace « Reçu … de … DA annulé (Janvier, Math 1ère A) ».
  Tests : `EncashmentCancellationIntegrationTest` (Aperçu sans écriture, confirmation complète et
  tracée, report neutralisé sur chaque Série, autres versements intacts, seconde annulation, 404,
  année close, plancher des remboursements à un et deux remboursements, égalité permise, Motif
  lié au jeton, Motifs admis et refusés), rendu HTTP du 409. Socle commun
  `CorrectionIntegrationTestSupport`. Onze mutations vérifiées
  _Exigences : 2.1 à 2.5, 2.7_
- [x] B.4 `EncashmentCorrectionService.correct` : Remplacement par le chemin ordinaire, liens
  dans les deux sens ; mode et note sans Remplacement. Fait :
  - l'administratrice décrit l'Encaissement tel qu'il aurait dû être saisi (`EncashmentChanges` :
    montant, élève, groupe, Série, mode, note — l'état voulu complet, rien d'implicite) ;
  - montant, élève, groupe ou Série changés → Remplacement : neutralisation de l'original, puis
    `PaymentProcessingService.processPayment` tel quel (plafond, report, refus en totalité,
    inscription, année close), liens `replaces` / `replaced_by`, une seule transaction : un
    remplacement refusé n'annule rien (3.2 à 3.5). Plancher des remboursements jugé sur l'état
    final ;
  - seuls le mode ou la note changés → correction en place (`EncashmentService.editDetails`,
    mêmes normalisations qu'à l'encaissement), aucun nouveau reçu, Aperçu « aucun montant ne
    change » (3.6, 4.4) ; rien de changé, espaces compris → 400 (11.3) ;
  - un seul point d'entrée pour les deux cas, avec Aperçu : le `PATCH …/details` du design n'a
    plus lieu d'être ;
  - traces : « Reçu X de 5 000,00 DA annulé, remplacé par Y de 2 000,00 DA (élève A → B, groupe
    « … » → « … », série « … » → « … ») » ; quand l'élève change, une seconde trace au nom du
    nouvel élève, pour que chaque Journal montre son argent ; « Reçu X : mode de paiement espèces
    → chèque, note aucune → « … » » ;
  - le jeton couvre montant, élève, groupe, Série, mode, note et Motif ; les effets ne citent pas
    le numéro du reçu de remplacement, attribué à la confirmation ;
  - limite assumée : un versement de rattrapage (`CATCH_UP`) ne se remplace pas — l'Encaissement
    ne garde pas la séance payée ; il s'annule, puis se ré-encaisse. Son mode et sa note se
    corrigent ;
  - un reçu déjà annulé : le refus nomme son remplacement.
  Tests : `EncashmentReplacementIntegrationTest` (Aperçu, confirmation reliée et tracée, report du
  remplacement, élève faux et ses deux traces, Série puis groupe faux, montant et note ensemble,
  groupe sans sa Série, refus des règles d'encaissement, élève non inscrit, plancher sur l'état
  final, rattrapage, reçu déjà remplacé, année close, jeton lié aux changements, au mode et à la
  note ; mode et note en place, l'un ou l'autre, rien de changé, mode trop long ; entrées
  incomplètes). Quatorze mutations vérifiées
  _Exigences : 3.1 à 3.6 — D4_
- [x] B.5 Points d'entrée `cancel` et `correct`, `preview` et `confirm`. Fait
  (`controller/correction/EncashmentCorrectionController`, contrôleur mince) :
  - `POST /api/encashments/{id}/cancel/preview|confirm` (`reasonType`, `reasonText`,
    `previewToken`) et `POST /api/encashments/{id}/correct/preview|confirm` (état voulu complet,
    Motif, jeton) ; réponse commune `{ preview, previewToken, result }`, `result` nul en Aperçu ;
  - `GET /api/encashments/correction-reasons` : Motifs proposés, dans l'ordre d'affichage (D10) ;
  - Motif reçu en texte, insensible à la casse : absent, inconnu (« Motif inconnu : « FOO » »),
    « Autre » sans texte → 400 en français, plutôt qu'une erreur de lecture JSON ; corps absent
    traité comme vide ;
  - sécurité : écritures et Aperçus sous `POST /api/**`, ADMIN seul ; lecture des Motifs sous
    `GET /api/encashments/**`, ADMIN seul (11.7).
  Tests : `EncashmentCorrectionEndpointIntegrationTest`, filtres de sécurité actifs (Aperçu sans
  écriture malgré `open-in-view`, confirmation tracée au nom du compte connecté, Motif lié au
  jeton, 409 Aperçu périmé et plancher des remboursements, refus de Motif, jeton manquant, 404,
  déjà annulé, remplacement et reçu à imprimer, mode et note en place, corrections incomplètes ou
  refusées, 403 VIEWER sur chaque Aperçu et confirmation, 401 anonyme, liste des Motifs). Huit
  mutations vérifiées (dont la règle de sécurité)
- [x] B.6 Propriétés P2 « remplacer équivaut à avoir bien saisi », P3 « indivisibilité »,
  P4 « l'Aperçu ne ment pas ». Fait (`CorrectionPropertiesTest`, jqwik, H2 réelle, états relus en
  SQL et comparés par rangs) :
  - **P2a** (100 essais) : historique quelconque, A, puis A remplacé par B = historique, puis B saisi
    directement — montants de chaque Série, cumuls et statuts des lignes, reports, ventilation par
    séance ; refus de l'un ⟺ refus de l'autre, au même statut ;
  - **P2b** (100 essais) : pour un A quelconque, remplacer = annuler puis encaisser B, quand les
    deux aboutissent ;
  - **P3** (200 essais) : refus à l'Aperçu, à la confirmation, Aperçu périmé par un versement
    intermédiaire, plancher des remboursements → rien n'est écrit (Encaissements, Imputations,
    ventilation, reports, cumuls, remboursements, compteur de reçus, Traces) ; une confirmation
    n'est refusée que si l'Aperçu a changé — et le 409 porte exactement le nouvel Aperçu — ou si
    la correction est devenue impossible ;
  - **P4** (100 essais) : un Aperçu n'écrit rien ; confirmé aussitôt, il est accepté, ses montants
    « avant » sont ceux d'avant et ses montants « après » ceux d'après, les Séries non listées
    restant inchangées ; annulations, remplacements et corrections de mode et note ;
  - **P1** étendue : `MoneyConservationPropertyTest` intègre les remplacements (Aperçu puis
    confirmation, acceptés ou refusés).
  Couverture exigée de chaque cas (refus, reports, Aperçus périmés, plancher, aucun montant
  changé). Écart assumé avec l'énoncé du design : un remplacement ne recalcule pas la
  répartition des versements postérieurs à A ; l'équivalence avec « B saisi à la place de A »
  n'est donc exacte que pour le dernier versement (P2a), P2b couvrant les autres. Une ligne de
  paiement entièrement annulée reste, à 0 et « en attente » (ses Imputations neutralisées la
  désignent) : elle compte comme absente dans la comparaison. Sept mutations vérifiées (Aperçu
  non annulé, « après » mesuré avant, jeton non comparé, remplacement sur la mauvaise Série, B
  encaissé avant l'annulation de A, refus du remplacement avalé, original non annulé)
- [x] B.7 `CorrectionPreviewDialog` (composant commun) ; actions « annuler », « corriger »,
  « réimprimer » dans l'historique ; tampon « ANNULÉ » à la réimpression. Ce sont les actions
  qu'annoncent déjà la note de « Gestion des paiements » et le refus 409 des corrections de ligne
  (A.6) : elles doivent exister à la livraison. Fait :
  - `CorrectionPreviewComponent` (présentationnel) : par Série, coût, dû à ce jour, versé, reste,
    statut, avant / après ; changement marqué par une flèche et du gras, pas par la seule couleur ;
    « Aucun montant ne change » en toutes lettres ; autres effets listés (4.1, 4.2, 4.4) ;
  - `CorrectionDialogComponent` (commun à toutes les corrections, ne connaît d'elles qu'une fonction
    `run(step, motif, jeton)`) : Motif sans valeur par défaut, « Autre » avec texte, Aperçu,
    confirmation du jeton lu ; changer de Motif efface l'Aperçu ; 409 périmé → nouvel Aperçu
    affiché avec avertissement, à confirmer ; refus rédigé par le serveur, remboursements en cause
    listés ; « Modifier » pour revenir à la saisie ;
  - `EncashmentEditDialogComponent` : l'état voulu (montant, groupe parmi ceux où l'élève peut payer,
    élève parmi les inscrits du groupe, Série, mode, note) ; « Continuer » désactivé tant que rien ne
    change. Un versement à reporter sur un élève sans groupe commun s'annule puis s'encaisse depuis
    sa fiche ;
  - panneau « Versements » : « Corriger », « Annuler » sur un versement actif (désactivés en lecture
    seule, année passée), « Réimprimer » sur tous ; après un Remplacement, « Imprimer le nouveau
    reçu » proposé (3.7) ; liste rechargée après chaque correction ;
  - réimpression d'un versement annulé : tampon « ANNULÉ » sur toute la page, date de l'annulation,
    reçu de remplacement (2.6) ; un versement annulé depuis le chargement ressort tamponné ;
  - `EncashmentService` : Motifs, annuler et corriger en Aperçu / confirmation ; les refus gardent
    le nouvel Aperçu, son jeton et les remboursements en cause (`CorrectionError`) ;
  - traductions fr / en (`correction.*`, mentions d'annulation du reçu).
  Tests Karma : dialogue (Motif, « Autre », Aperçu, jeton confirmé, Motif changé, Aperçu périmé,
  refus et remboursements, erreur inattendue, retour), Aperçu (lignes, marques, statut, effets,
  aucun montant), saisie (valeurs initiales, listes, normalisation, changement de groupe, valeurs
  actuelles toujours présentes, montant nul, retour d'Aperçu), panneau (actions selon statut et
  lecture seule, réimpression tamponnée, annulation, Motifs chargés une fois, correction, retour,
  impression proposée, mode et note), service (routes, corps, refus enrichis), reçu et document
  PDF annulés
  _Exigences : 2.6, 3.7, 4.1_

## Lot C — Dates d'arrivée et de départ, Feuille_Appel (exigences 5, 6, 7)

- [x] C.1 **V8** (et non V6, déjà prise par le lot A) : colonne `student_groups.date_left`,
  facultative, et deux contraintes — une fenêtre finit au plus tôt le jour où elle commence
  (`ck_student_groups_window_ordered`), une inscription est close si et seulement si elle porte
  une Date_Sortie (`ck_student_groups_closure_dated`). `MigrationSchemaPostgresIntegrationTest`
  applique V8 et éprouve les deux contraintes
  _D1, D5_
- [x] C.2 `EnrolmentWindow` (`domain/valueobject`) : jours calendaires lus dans le fuseau de la
  JVM, arrivée et départ inclus, `describe()` pour les messages ; une colonne `DATE` revenue en
  `java.sql.Date` est lue sans `toInstant()`. `StudentGroupEntity` :
  - `dateLeft` ; `onCreate` garde la date fournie, sinon le jour même ; `onCreate` et `onUpdate`
    ramènent les deux dates à 00:00, quel que soit le chemin d'écriture ;
  - `window()` donne la Fenêtre_Inscription de la ligne.
  `StudentGroupService`, sur les deux chemins d'inscription (`addGroups`, `addStudents`) :
  - `dateAssigned` devient un `LocalDate` (`yyyy-MM-dd`), sans `@PastOrPresent` ; une date future
    est admise dans l'année du groupe, bornes comprises ; hors de l'année, 400 nommant l'année et
    ses bornes ; sans date, le jour même, soumis au même contrôle ;
  - déjà membre = inscription **active** : `addStudents` testait `group.getStudents()`, qui lit
    les clôtures, et réinscrire un étudiant parti ne faisait rien sans le dire. Le retour crée une
    nouvelle inscription, refusée (409) si elle recouvre la fenêtre d'un départ du même groupe ;
  - le retrait (`DELETE`) clôture avec Date_Sortie au jour même, en attendant le départ avec Motif
    et Aperçu de C.6 ; refusé (409) pour une inscription qui n'a pas commencé.
  `StudentGroupController` ne rend plus en 500 les refus métier (niveau, année close, bornes) : ils
  remontent au gestionnaire global avec leur statut. `GroupChangeDetector` date une clôture par sa
  Date_Sortie et non par `date_update`, qui datait la dernière écriture de la ligne.
  Tests : `EnrolmentWindowTest`, `StudentGroupEnrolmentEndpointIntegrationTest` (HTTP, dates relues
  en SQL), détecteur et requêtes de changement de groupe adaptés ; 17 mutations tuées
  _Exigences : 5.1 à 5.4 — D1, D5_
- [x] C.3 Feuille_Appel par fenêtre, clôtures comprises ; suppression du repli côté écran.
  `GET /api/sessions/{id}/roll-call` (`RollCallService`) : désignée par la Séance, le serveur lit
  son jour et son groupe ; retient chaque étudiant dont une inscription, active ou close, contient
  ce jour — une fois, même revenu dans le groupe ; renvoie aussi les non-concernés et leurs
  fenêtres, qui expliquent une feuille vide. Retirés : `GET /api/student-groups/{id}/studentsForSession`
  (inscriptions actives seulement, instants comparés, date venue du navigateur), et
  `GET /api/student-groups/{id}/students` (toutes inscriptions, sans fenêtre), qui ne servait qu'au
  repli. Écran : feuille du serveur sans repli ; badge « Parti le … » ; feuille vide expliquée
  (« Lina Haddad : inscrit à partir du 21/01/2030 ») ; échec de chargement dit comme tel, et non
  présenté comme une feuille vide ; jours affichés sans conversion en `Date` (`formatCalendarDay`).
  Tests : `RollCallEndpointIntegrationTest` (arrivée et départ le jour même, lendemain du départ à
  00:30, retour dans le groupe, feuille vide, VIEWER, 404), `RollCallServiceTest` (cas limites),
  Karma de la feuille et du format de jour ; 17 mutations tuées
  _Exigences : 6.2, 7.1, 7.2_
- [x] C.4 Refus serveur des absences hors fenêtre, en bloc à la validation, sur tous les points
  d'entrée. `AbsenceWindowGuard` : est une absence toute ligne qui n'est pas une présence
  (`isPresent` nul compris) ; elle doit tomber dans une fenêtre, active ou close, de l'étudiant au
  groupe de la séance. Une présence est toujours admise (7.4). Refus 409 `ABSENCE_OUTSIDE_WINDOW`,
  corps `rejected` : une ligne par absence, motif (`NOT_ENROLLED`, `OUTSIDE_WINDOW`), fenêtres et
  message (« Absence de Lina Haddad le 07/01/2030 : hors de son inscription au groupe « Math 1ère A »
  (à partir du 14/01/2030). »). Points d'entrée :
  - feuille de présence (`POST /api/attendances/bulk`) : toutes les lignes jugées avant toute
    écriture, refus entier ; l'interception générale qui rendait tout refus en 500 est retirée ;
  - présence unitaire (`POST /api/attendances`) ;
  - modification d'une séance pointée (`PATCH /api/sessions/{id}`) : changer son jour ou son groupe
    change qui elle concerne, ses absences actives sont rejugées ; refus « Modification refusée » ;
  - `AttendanceService.save`, sans appelant et sans contrôle, retiré.
  Le rattrapage (`CatchUpService.complete`) n'écrit que des présences.
  Tests : `AbsenceWindowEndpointIntegrationTest` (refus en bloc et lignes nommées, revalidation sans
  les lignes, présences admises, jours d'arrivée et de départ, lendemain du départ, étudiant revenu,
  présence non renseignée, doublon en 409, présence unitaire, séance déplacée de jour ou de groupe),
  `AbsenceWindowGuardTest` (cas limites) ; 15 mutations tuées. L'écran affiche encore un message
  générique sur ce refus : le retrait des lignes en un clic est en C.8
  _Exigences : 7.3 à 7.5_
- [x] C.5 Résolveur : Séances facturables d'une inscription clôturée par sa fenêtre.
  `BillableSessionsResolverImpl` lit toutes les inscriptions de l'étudiant au groupe, closes
  comprises (`findByGroupIdAndStudentId`) : facturable = séance qu'une fenêtre contient, ou séance
  suivie. Exemple : parti le 14/01, rien de validé, la série de janvier lui coûte toujours
  2 × 2 000 DA ; avant, 0 séance due et 4 000 DA annoncés en trop-perçu. Étudiant revenu : ses deux
  fenêtres comptent, pas l'intervalle. `BillableSessions.enrollmentDate` devient
  `withinEnrolmentSessionIds` : le motif d'historique (`AFTER_ENROLMENT` = dans une fenêtre,
  `ATTENDED_BEFORE_ENROLMENT` = suivie hors fenêtre, avant l'arrivée ou après le départ) vient du
  résolveur au lieu d'être recalculé depuis une date unique. `enrolled` = inscription active (une
  série entière antérieure à l'arrivée reste affichée, comme avant) ou fenêtre close touchant la
  série. Inchangé pour un étudiant inscrit sans départ. Relevé de groupe (`GroupRevenueService`) :
  tous les étudiants passés par le groupe, une fois chacun, et non plus les seuls inscrits actifs —
  le dû et le trop-perçu d'un étudiant parti disparaissaient du relevé. Relevé, non corrigé : la
  liste des groupes payables (`StudentPayableGroupsService`) ne propose un groupe quitté que s'il
  porte une présence de l'étudiant.
  Tests : `BillableSessionsResolverTest` (fenêtre close, venu après le départ, série postérieure,
  étudiant revenu, jour d'arrivée quelle que soit l'heure, ligne héritée), `DepartedStudentBilling
  IntegrationTest` (devis et relevé de groupe sur H2), `GroupRevenueBalanceTest` ; 10 mutations
  tuées
  _Changement de calcul assumé — D5_
- [x] C.6 `EnrolmentCorrectionService` : arrivée reculée (absences, présences ordinaires) et
  avancée (Séances validées sans Présence), départ, correction de départ, réouverture ;
  `VentilationMover`. Les trois corrections passent par le `CorrectionRunner` et un seul code, qui
  compare la période avant et après :
  - séance qui sort de la période : absence retirée (P5) ; présence ordinaire maintenue, facturée
    comme séance consommée, sauf départ avec `removePresencesAfter` (6.3) ; rattrapage intact (6.4) ;
  - séance validée qui y entre sans présence : listée, facturable, ou notée présent / absent dans
    la même opération (`attendances`, 5.7) ; toute autre séance notée est refusée (400) ;
  - ventilation d'une séance devenue non facturable : `VentilationMover` retire ses lignes et les
    ventile de nouveau par `PaymentDistributionService.place`, même série, même Encaissement ;
    reliquat dit non ventilé ; trop-perçu de la série annoncé, ni reporté ni remboursé (5.9) ;
  - statut stocké des lignes de paiement du groupe recalculé, le coût ayant pu changer.
  Refus : même date, hors de l'année (400) ; arrivée après le départ, départ avant l'arrivée,
  chevauchement d'une autre inscription au groupe, réouverture d'une inscription ouverte (409) ;
  année close (409) ; Motif hors de la sous-liste (400). Un départ futur dans l'année est admis :
  l'inscription est close à l'enregistrement, l'étudiant attendu jusqu'à ce jour inclus. Liste
  changée depuis l'Aperçu : 409 avec la liste à jour (5.8). Une Trace pour la correction, une par
  présence retirée ou ajoutée. Points d'entrée `POST /api/enrolments/{id}/arrival|departure|reopen/
  preview|confirm`, `GET /api/enrolments/correction-reasons` (Motifs par correction).
  `CorrectionReason.parse` est partagé avec les Encaissements, `EnrolmentDates` avec l'inscription.
  Tests : `EnrolmentCorrectionEndpointIntegrationTest` (Aperçu sans écriture, confirmation, absence
  retirée, présence maintenue ou retirée, rattrapage intact, séance validée listée ou notée,
  ventilation déplacée, reliquat et trop-perçu, refus, Aperçu périmé, VIEWER), cas limites
  (`EnrolmentCorrectionEdgeCasesIntegrationTest`, `VentilationMoverTest`)
  _Exigences : 5.5 à 5.9, 6.1, 6.3 à 6.5 — D6_
- [x] C.7 Propriétés P5 « fenêtre respectée », P6 « déplacer la ventilation ne change aucun
  montant ». `EnrolmentWindowPropertiesTest` (jqwik, H2, oracles relus en SQL et comparés en jours) :
  - **P5** : trois étudiants aux fenêtres tirées (ouvertes, closes, retours), puis 3 à 12 opérations
    parmi feuille de présence, présence unitaire, correction d'arrivée, de départ, réouverture,
    séance déplacée. Après chaque opération : aucune absence active hors d'une fenêtre de son
    étudiant, et la Feuille_Appel de chaque séance est exactement l'ensemble des étudiants dont une
    fenêtre contient son jour, départs compris ; une opération refusée ne change rien (présences,
    inscriptions, séances, ventilation, Traces) ;
  - **P6** : versements, puis une correction qui réduit la période (arrivée repoussée, départ
    avancé). Encaissements, Imputations, reports, cumuls et versé de chaque série inchangés ; la
    ventilation d'une Imputation ne grossit jamais, ne reste que sur des séances facturables, ne
    dépasse jamais le prix net d'une séance, et ne perd d'argent que si chaque séance facturable de
    la série est déjà couverte.
  Couverture vérifiée par jqwik (feuilles acceptées et refusées, corrections acceptées et refusées,
  déplacements avec et sans reliquat) ; 8 mutations du code tuées par les seules propriétés
- [x] C.8 Écrans : date d'arrivée à l'inscription ; corriger l'arrivée, enregistrer le départ,
  rouvrir ; lignes refusées retirables en un clic à la validation.
  Serveur :
  - `GET /api/student-groups/{studentId}/enrolments?schoolYearId=` (`EnrolmentDTO`) : inscriptions
    ouvertes et closes, arrivée et départ en jours, par groupe puis par arrivée — un étudiant revenu
    y a deux lignes. Lisible par VIEWER ;
  - `CorrectionEffect.sessionId` : posé sur `SESSION_BECAME_BILLABLE` et `ATTENDANCE_RECORDED`, la
    séance où l'écran propose « présent / absent » (5.7). Il entre dans l'empreinte de l'Aperçu
    (format `correction-preview-v2`) : deux séances du même jour et de la même série ont la même
    description ;
  - retiré : `DELETE /api/student-groups/{groupId}/students/{studentId}` et
    `StudentGroupService.removeStudentFromGroup`, qui clôturaient au jour même sans Motif ni Aperçu
    (6.1). Une adresse sans point d'entrée répond 404 « Adresse inconnue : DELETE /api/… » : le
    gestionnaire global la rendait en 500, comme une panne.
  Écrans :
  - inscription (fiche élève, fiche groupe) : date d'arrivée, proposée au jour même, bornée à
    l'année au calendrier ; saisie et envoyée en `yyyy-MM-dd`, sans passer par `Date` ;
  - refus d'inscription : le `message` du serveur tel quel (année et bornes, départ recouvert,
    niveau), les groupes déjà suivis lus dans `alreadyAssociatedEntities` (`enrolmentRefusalMessage`) ;
  - fiche élève, panneau « Inscriptions : arrivées et départs » : chaque inscription de l'année,
    close comprise, avec « corriger l'arrivée », « enregistrer le départ » (ouverte), « corriger le
    départ » et « rouvrir » (close) ; ADMIN et année ouverte seulement. Une correction confirmée
    relit les groupes et les versements ;
  - fiche groupe : « retirer » devient « enregistrer le départ », par le même enchaînement ;
  - enchaînement commun (`EnrolmentCorrectionFlow`) : date (départ proposé au jour même, case
    « retirer aussi ses présences après cette date »), puis le dialogue de correction commun, d'où
    « Modifier » ramène à la date saisie ; la réouverture va droit à l'Aperçu ;
  - dialogue de correction : à côté d'une séance que l'Aperçu désigne, « Sans présence (facturée) /
    Présent / Absent » ; un choix redemande l'Aperçu, la confirmation porte les choix lus, un refus
    les efface. L'Aperçu reste présentationnel : il rend le gabarit que l'hôte lui confie ;
  - validation d'une séance : sur 409 `ABSENCE_OUTSIDE_WINDOW`, chaque ligne refusée nommée et
    « Retirer ces lignes » ; la validation se refait, la feuille sous les yeux. Les autres refus
    s'affichent tels que le serveur les rédige (tout 409 était lu « présence déjà saisie »).
  Tests : `StudentGroupEnrolmentEndpointIntegrationTest` (lecture, filtre d'année, ordre contraire
  aux identifiants, VIEWER, `DELETE` en 404, adresse inconnue), séance désignée et empreinte
  (`EnrolmentCorrectionEndpointIntegrationTest`, `CorrectionValuesTest`) ; Karma : dialogue
  (choix, Aperçu redemandé, confirmation, refus), Aperçu (gabarit de l'hôte), saisie de date,
  enchaînement, panneau d'inscriptions, dialogues d'inscription, fiche élève, fiche groupe,
  feuille de présence (lignes refusées), services et utilitaires de jour ; mutations tuées côté
  serveur et côté écran
  _Exigences : 5, 6, 7.5_
- [x] C.9 Fuseau de l'école fixé par l'application au démarrage — révisé avec le propriétaire produit :
  le bandeau d'alerte prévu signalait le risque, le réglage le supprime (D1 mis à jour).
  `ApplicationTimeZone`, inscrit par `main`, juste après l'initialisation des journaux et avant tout
  composant (la source de données transmet le fuseau de la JVM à PostgreSQL) :
  - `app.timezone` (variable `APP_TIMEZONE`, ajoutée à `docker-compose.yml` et `.env.example`),
    `Africa/Algiers` sans réglage ou réglage vide, appliqué même si le conteneur est en UTC. Exemple
    évité : un versement de 10:00 imprimé à 09:00, une séance de 00:30 datée de la veille ;
  - une ligne au journal : « Fuseau de l'école : Africa/Algiers (UTC+01:00), heure locale … ;
    fuseau du système : UTC (non utilisé par l'application) » ;
  - valeur inconnue (« Africa/Alger ») : démarrage refusé en la nommant, exemples à l'appui ;
  - autre pays : la seule valeur `APP_TIMEZONE` change. Hors périmètre, relevé : la monnaie « DA »
    est écrite en dur dans les écrans, reçus et messages.
  `TZ` reste dans le conteneur : il ne règle plus que l'heure en tête des lignes du journal.
  Tests : `ApplicationTimeZoneTest` (JVM partie d'UTC : défaut, vide, autre pays, valeur inconnue
  sans effet, variable `APP_TIMEZONE` lue, séance de 00:30 du jour même, ligne du journal, ordre),
  `SchoolManagementApplicationStartupTest` ; démarrage réel vérifié, conteneur simulé en UTC
  (`TZ=UTC`) : valeur inconnue refusée, défaut appliqué avant « Starting » ; 10 mutations tuées
  _D1_

## Lot D — Présences, rattrapages, dévalidation, Journal (exigences 8, 9, 10, 12)

- [x] D.1 `AttendanceCorrectionService` : présent ↔ absent avec justification, ajout, retrait
  par désactivation. Une ligne d'une Séance validée à la fois, par le `CorrectionRunner` :
  - présent → absent : justification fixée dans la même action (8.2) ; absent → présent :
    justification effacée, valeurs avant et après dans la Trace (8.3). Exemple : « Séance du
    07/01/2030 (Math 1ère A) : Amine Belkacem absent (justifié) → présent », dû à ce jour de janvier
    0 → 2 000 DA ;
  - ajout d'une ligne manquante, pour un inscrit du groupe (inscription active ou close : une
    présence après le départ est facturée comme séance consommée) ; un non-inscrit est renvoyé vers
    la demande de rattrapage ; ligne déjà présente : 409 ;
  - retrait par désactivation, la ligne reste en base (8.4) ; une présence consommée retirée rend sa
    séance non facturable et sa ventilation passe sur une autre séance de la série.
  Refus : absence sur une Séance_Non_Concernée (409 `ABSENCE_OUTSIDE_WINDOW`, ligne nommée, 8.5) ;
  année close (8.6) ; séance non validée ou supprimée ; ligne retirée ; présence de rattrapage
  (correction dédiée, D.2) ; séance rattrapée ou dont une demande de rattrapage est en cours, qui ne
  peut devenir suivie ni perdre sa ligne ; même état ; présence « justifiée » ; Motif hors de
  [Erreur de saisie, Justificatif reçu, Autre]. Points d'entrée `POST /api/attendances/{id}/correct|
  remove/{preview|confirm}`, `POST /api/sessions/{id}/attendances/add/{preview|confirm}`,
  `GET /api/attendances/correction-reasons`.
  `SeriesSettlement` extrait de `EnrolmentCorrectionService` et partagé : ventilation déplacée,
  statut stocké recalculé, trop-perçu annoncé — mêmes effets, mêmes mots.
  Tests : `AttendanceCorrectionEndpointIntegrationTest` (34 : Aperçu sans écriture, confirmation et
  Trace relues en SQL, justification, ajout après départ, ventilation déplacée, chaque refus, Aperçu
  périmé, rattrapage lié, VIEWER) ; mutations tuées
  _Exigences : 8.1 à 8.6_
- [x] D.2 Retrait d'une présence de rattrapage, réouverture du droit, demande passée à
  `CANCELLED`. Même point d'entrée que le retrait d'une ligne (`POST /api/attendances/{id}/remove/…`) :
  le serveur reconnaît le rattrapage. Dans la même opération :
  - présence de rattrapage désactivée ; demande qui l'a produite (même séance d'accueil, même séance
    manquée, non annulée) passée à `CANCELLED`, raison « Présence de rattrapage retirée par correction
    (Motif : texte) » ; l'absence d'origine redevient éligible à une demande (9.2) ;
  - Trace `CATCH_UP_REMOVED` gardant la séance manquée, la décision « déjà payée », l'état de
    facturation et les demandes avant et après (9.3) ;
  - portée : groupe d'accueil et groupe de la séance manquée, tous deux dans l'Aperçu. Exemple :
    « Rattrapage de Amine Belkacem du 09/01/2030 (Math 1ère B) retiré : séance manquée du 07/01/2030
    (Math 1ère A), déjà payée : oui » ; janvier (A), dû à ce jour 2 000 → 0 DA. Facturé sur place :
    la série d'accueil perd la séance, le versé reste, annoncé comme trop-perçu.
  Pas de séance validée exigée : un rattrapage enregistré par sa demande existe avant la validation,
  et la feuille ne sait pas le retirer. Refus : séance d'accueil supprimée, année close à l'accueil
  ou à l'origine. Une présence de rattrapage ne passe jamais à absent (409, « retirez-la »).
  Tests : `CatchUpRemovalEndpointIntegrationTest` (Aperçu sur les deux groupes, confirmation, demande
  annulée, absence de nouveau rattrapable puis corrigeable, Motif « Autre », facturé sur place et
  trop-perçu, décisions écrites en clair, seules les demandes productrices annulées, refus) ;
  mutations tuées
  _Exigences : 9.1 à 9.3 — D9_
- [x] D.3 Dévalidation avec Motif et Aperçu ; validation et dévalidation refusées sur année close.
  Une opération, `POST /api/sessions/{id}/unvalidate/{preview|confirm}` (Motifs : Erreur de saisie,
  Autre ; `GET /api/sessions/unvalidation-reasons`) :
  - toutes les lignes actives désactivées, la séance de nouveau à valider. Exemple : « Séance du
    07/01/2030 (Math 1ère A) dévalidée : 2 lignes retirées », janvier d'Amine, dû à ce jour 2 000 →
    0 DA ; « 1 ligne retirée », « sans ligne de présence » ;
  - chaque ligne suit sa correction : ordinaire comme un retrait (D.1, ventilation déplacée si la
    séance n'est plus facturable), rattrapage accueilli comme en D.2 (demande annulée, séance
    d'origine de nouveau à rattraper, groupe d'origine dans l'Aperçu), ligne héritée sans étudiant
    désactivée avec la feuille ;
  - une Trace de la séance, en tête, listant chaque ligne telle qu'elle était et les identifiants
    désactivés (10.1) ; une Trace par ligne, pour le Journal de chaque élève. Lignes rangées par nom.
  Refus : séance non validée, supprimée, inconnue ; absence rattrapée ailleurs ou dont une demande
  est en cours (409, le rattrapage d'abord) ; Motif hors liste ; Aperçu périmé ou Motif changé.
  Année close (10.2) : dévalidation, feuille, présence isolée et validation de la séance refusées
  (409) avant écriture ; une ligne sans séance, dont l'année ne se résout pas, est refusée (400) au
  lieu de finir en erreur serveur.
  Retirés, faute de Motif et de Trace : `PATCH /api/sessions/{id}/unfinish`, `PATCH /api/attendances/
  deactivate/{id}`, `DELETE /api/attendances/{id}` et `…/session/{id}` (suppressions définitives).
  Écran : « Dévalider la séance » ouvre le dialogue commun ; confirmée, la modale reste ouverte et
  recharge la feuille du serveur. Le calendrier et la liste d'une série relisent l'état de la séance à
  la fermeture de la modale, quelle qu'elle soit : la séance restait affichée validée.
  Tests : `SessionUnvalidationEndpointIntegrationTest` (Aperçu sans écriture, confirmation et
  Traces relues en SQL, ventilation déplacée, ligne sans étudiant, séance hors série, rattrapage
  compensatoire et facturé sur place, refus, Aperçu périmé, Motif changé, VIEWER, anciens raccourcis
  absents, année close, ligne sans séance) ; Karma : dialogue ouvert et appels, rechargement de la
  feuille, abandon, refus, `SessionService`, calendrier, liste de la série ; 31 mutations serveur et
  20 mutations écran tuées
  _Exigences : 10.1, 10.2_
- [x] D.4 `CorrectionJournalService` : entrées en français, effet sur le dû, du plus récent au plus
  ancien. `GET /api/students/{id}/journal?from&to` (bornes incluses, facultatives ; ADMIN seul, le
  Journal nomme les reçus ; ouvert sur une année close). Une source par table, révisé : trois et non
  quatre —
  - `correction_audit` : phrase et effet écrits avec la correction (« Reçu RECU-2030-0001 de
    2 000,00 DA annulé (Janvier, Math 1ère A) », « Janvier (Math 1ère A) : versé 2 000,00 → 0,00 DA,
    … ») ; la Trace d'une séance dévalidée n'a pas d'élève, chaque ligne retirée a la sienne ;
  - `attendance_justification_audit` : « Séance du 07/01/2030 (Math 1ère A) : absence non justifiée
    → justifiée », commentaire en guise de Motif, aucun effet sur le dû ;
  - `catch_up_billing_audit` : « Rattrapage du 09/01/2030 (Math 1ère B) : séance manquée aucune →
    07/01/2030 (Math 1ère A) », « … : déjà payée non tranché → oui », séance disparue « supprimée » ;
  - `payment_detail_audit` non lue : plus d'écrivain depuis A.6, base neuve à l'installation, la
    table restera vide.
  À horodatage égal, la dernière écrite d'abord. Un paramètre mal formé rend 400 en le nommant
  (« Paramètre « from » invalide : 2030-13-40 »), partout : c'était une 500.
  Tests : `CorrectionJournalEndpointIntegrationTest` (justification, correction de présence et
  versement annulé écrits par leurs vrais services ; décisions de rattrapage ; valeurs jamais
  renseignées ; catégories ; autre élève ; Journal vide ; même horodatage dans chaque source ;
  période et ses bornes ; année close ; période à l'envers, date mal formée, élève inconnu ; VIEWER,
  anonyme) ; 40 mutations tuées
  _Exigences : 12.1 à 12.3, 12.5 — D8_
- [x] D.5 Journal imprimable par période — avec le panneau de la fiche élève qui le porte (avancé de
  D.7 : une impression sans écran pour choisir la période ne servait à rien) :
  - fiche élève, panneau « Journal des corrections » (ADMIN seul, année close comprise) : lu à
    l'ouverture et relu à chaque ouverture ; période Du / Au, « Afficher », « Toute période » ; une
    période à l'envers est dite sur place, sans appel ; chaque entrée : catégorie, date et heure de
    l'école, auteur, phrase du serveur, une ligne par série touchée, « Sans effet sur le dû » pour
    une justification, Motif et son texte ;
  - « Imprimer » relit le Journal sur la période saisie puis imprime celui-là : une période tapée
    mais non affichée ne sort pas sous l'en-tête de la précédente. A4 paysage : logo, « Journal des
    corrections », élève, « Du 01/01/2030 au 31/01/2030 », nombre d'entrées ; une ligne par entrée
    (date, catégorie et phrase, effet par série, Motif, auteur), fond alterné ; « Aucune correction
    sur cette période. » ; « Édité le … », « Page 2 / 5 » ; fichier
    `journal_Amine_Belkacem_2030-01-01_2030-01-31.pdf` ;
  - la police embarquée (Roboto) n'a pas de « → » : imprimé « -> », un chevron isolé se lisant
    « plus grand que » entre deux montants ; espaces typographiques ramenées à une espace ;
  - impression par iframe masquée, téléchargement en repli (`utils/pdf-print`, `PdfOutputService`).
  Tests Karma : panneau (lecture à l'ouverture, rendu de chaque champ, période, période à l'envers,
  vide, refus, impression relue, double clic, échec d'impression, lectures concurrentes, changement
  d'élève), document (en-tête, périodes, lignes, flèche, justification, tirets, fonds, vide, logo,
  pied, nom de fichier, impression), service HTTP, impression en iframe et repli, logo, format de
  l'heure, fiche élève (ADMIN seul) ; 48 mutations tuées
  _Exigences : 12.4_
- [ ] D.6 Propriété P7 « une Trace par changement effectif »
- [ ] D.7 `session-modal` : corriger par élève, ajouter un élève (la dévalidation avec motif est
  livrée en D.3, le Journal de la fiche élève en D.5)
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
