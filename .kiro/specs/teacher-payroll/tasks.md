# Implementation Plan — Paie des enseignants

Exigences : `requirements.md`. Design : `design.md`. Branche : `feat/teacher-payroll`, créée depuis
`feat/admin-corrections`.

Chaque tâche se termine par `./mvnw test` vert (JDK 21) et, côté front, Karma vert ; chaque règle
d'argent est vérifiée par mutation, puis le code est restauré.

## Lot P — Socle

- [x] P.1 Migration V9 : `teacher_pay_rate`, `teacher_payout`, `payout_counter`,
  `payout_slip_issuance` ; entités et dépôts. Sur PostgreSQL jetable : V1 à V9 appliquées, toutes les
  entités validées par Hibernate, 11 contraintes vérifiées en SQL (bornes et unicité du taux parmi les
  actifs, parts exactes, net = brut − remboursé, paie initiale qui partage tout, régularisation liée et
  non nulle, une seule paie initiale active par série, annulation datée et motivée, compteur à une
  ligne, rang de bordereau unique)
  _Exigences 1, 4.4, 6 — D1, D2, D8_
- [x] P.2 `SeriesCollectionService` (Encaissé_Net d'une Série, par groupe en deux requêtes, ou d'une
  série par la même lecture) ; `GroupRevenueService` y passe pour sa ventilation par Série. Test
  d'égalité des deux lectures (P3)
  _Exigence 3.3 — D4_
- [x] P.3 `SeriesCompletionService` + `SessionRepository.countCompletionBySeries` : séances actives,
  validées, restantes ; une série sans séance n'est pas terminée
  _Exigence 2.3 — D5_
- [x] P.4 `PayoutCalculator` : bornes du pourcentage (deux décimales au plus, refus plutôt qu'arrondi),
  parts au centime demi supérieur, école = différence exacte, écart calculé sur le cumul ; propriété P1
  en jqwik (1 000 essais). 22 mutations du lot tuées (calcul, avancement, encaissé, requêtes, V9)
  _Exigences 3.1, 3.2, 6.2 — D2_

## Lot Q — Catalogue et paie

- [x] Q.1 `TeacherPayRateService` + `TeacherPayRateController` (`/api/teacher-pay-rates`) : création,
  libellé nettoyé et borné, pourcentage refusé hors ]0 ; 100[ ou à trois décimales, unicité parmi les
  actifs, modification refusée sur un taux désactivé, désactivation sans effet si répétée ; une paie
  garde sa copie quand le taux change
  _Exigence 1_
- [x] Q.2 `PayoutNumberService` (compteur verrouillé, transaction obligatoire, rang remis à 1 chaque
  année, horloge arrière refusée) ; aucun numéro consommé par un Aperçu, un refus ou une confirmation
  périmée (P4, sur H2 et PostgreSQL)
  _Exigence 4.6 — D8_
- [x] Q.3 `TeacherPayoutService` + `TeacherPayoutController` : Séries à payer et à régulariser (D11) ;
  payer et régulariser en Aperçu / confirmation à jeton, 409 `STALE_PREVIEW` avec le nouvel Aperçu ;
  verrou de Série ; refus nommés ; consultation filtrée avec totaux des seules paies actives.
  Propriété P2 sur PostgreSQL : deux confirmations simultanées, la seconde attend le verrou et est
  refusée « déjà payée », un seul numéro consommé. Routes de lecture ajoutées aux lectures financières
  réservées ADMIN
  _Exigences 2, 3.4, 4, 6, 9 — D2, D6, D10, D11, D12_
- [x] Q.4 `PayoutSlipService` : données du Bordereau, rang du duplicata, nom de fichier stable ; une
  paie annulée reste imprimable. Seuil JaCoCo 100 % étendu au calcul, aux taux, à l'avancement, au
  numéro et au bordereau. 40 mutations du lot tuées
  _Exigence 5_

## Lot U — Corrections et cohérence

- [x] U.1 `PayoutCorrectionService` (par `CorrectionRunner`) : annuler, remplacer une paie initiale
  par une paie à un autre taux, même enseignant ; plus récente seulement, série verrouillée ; refus
  nommés (déjà annulée et remplaçante, régularisation, même taux, rien à partager) ; Traces avec les
  cumuls de la série ; la série redevient à payer ; aucun numéro consommé par un Aperçu
  _Exigence 7 — D3, D7, D7 bis_
- [x] U.2 `PaidSeriesGuard` branché sur dévalidation (dès l'Aperçu), suppression, désactivation,
  réactivation, et `PATCH` de séance qui dévalide ou change de groupe ; refus nommant les paies, la
  plus récente d'abord
  _Exigence 8 — D9_
- [x] U.3 `PayoutCorrectionController` (`/api/teacher-payouts/correction-reasons`,
  `/{id}/cancel|replace/{preview|confirm}`) ; paie, régularisation et corrections nommées dans
  `WriteRoutesAuthorizationIntegrationTest`, motifs parmi les lectures financières. Seuil JaCoCo
  étendu à la correction et à la garde. 31 mutations du lot tuées
  _Exigence 9.4_

## Lot S — Écrans

- [x] S.1 Services Angular `TeacherPayRateService`, `TeacherPayoutService` (refus de paie en
  `PayoutError` portant le nouvel Aperçu, corrections en `CorrectionError`) ; modèles
  `models/payroll/payroll.ts`
- [x] S.2 Page `admin/teacher-payroll` (`roleGuard('ADMIN')`, onglets recréés à l'ouverture), onglet
  Taux (création, modification, désactivation, part de l'école annoncée) ; entrée de menu ADMIN
  _Exigences 1, 9.1_
- [x] S.3 Onglet À payer (état et raison de chaque série), dialogue de paie : taux sans défaut,
  calcul en clair, confirmation, nouvel Aperçu sur 409 ; régularisation complément ou retenue
  _Exigences 2, 3, 4, 6_
- [x] S.4 Bordereau PDF (`PayoutSlipPdfService.buildDocument`) : duplicata et rang, tampon ANNULÉE
  et remplaçante, retenue lue comme une somme due ; proposé après chaque paie, réimprimable
  _Exigence 5_
- [x] S.5 Onglet Paies versées : filtres, totaux des paies actives, annuler / refaire à un autre
  taux par `CorrectionDialogComponent` (« Modifier » ramène au choix du taux)
  _Exigences 7, 9.2_
- [x] S.6 Fiche Enseignant : panneau Paies (ADMIN), total versé, réimpression
  _Exigence 9.3_
- [x] S.7 Clés i18n FR / EN listées dans la parité ; build de production. Karma 701 verts,
  14 mutations du lot tuées

## Livraison

- [x] L.1 Suites complètes (backend 1429 dont PostgreSQL, Karma 704), 107 mutations tuées sur les
  quatre lots, `business-rules.md` relu et complété (remplacement, garde des séries payées), mode
  d'emploi d'une page : `docs/guides/paie-des-enseignants.md` (sans captures : elles demandent
  l'application lancée sur le poste de l'école)
