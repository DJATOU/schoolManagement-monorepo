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

- [ ] Q.1 `TeacherPayRateService` + contrôleur : création, libellé, désactivation, unicité, bornes
  _Exigence 1_
- [ ] Q.2 `PayoutNumberService` (compteur verrouillé) ; aucun numéro consommé par un Aperçu (P4)
  _Exigence 4.6 — D8_
- [ ] Q.3 `TeacherPayoutService` : Séries à payer et à régulariser ; payer et régulariser en Aperçu /
  confirmation à jeton ; verrou de Série ; refus nommés. Propriété P2 sur PostgreSQL
  _Exigences 2, 3.4, 4, 6 — D2, D6, D10_
- [ ] Q.4 `PayoutSlipService` : données du Bordereau, rang du duplicata
  _Exigence 5_

## Lot U — Corrections et cohérence

- [ ] U.1 `PayoutCorrectionService` : annuler, remplacer ; plus récente seulement ; Traces
  _Exigence 7 — D3, D7_
- [ ] U.2 `PaidSeriesGuard` branché sur dévalidation, suppression, désactivation de Séance
  _Exigence 8 — D9_
- [ ] U.3 Routes, `SecurityConfig`, `WriteRoutesAuthorizationIntegrationTest` (lectures financières,
  corrections)
  _Exigence 9.4_

## Lot S — Écrans

- [ ] S.1 Services Angular `TeacherPayRateService`, `TeacherPayoutService` ; modèles
- [ ] S.2 Page `admin/teacher-payroll`, onglet Taux ; entrée de menu
  _Exigences 1, 9.1_
- [ ] S.3 Onglet À payer, dialogue de paie (taux, Aperçu en clair, confirmation), Régularisation
  _Exigences 2, 3, 4, 6_
- [ ] S.4 Bordereau PDF (duplicata, annulée, retenue) et réimpression
  _Exigence 5_
- [ ] S.5 Onglet Paies versées : filtres, totaux, annuler / remplacer par `CorrectionDialogComponent`
  _Exigences 7, 9.2_
- [ ] S.6 Fiche Enseignant : panneau Paies
  _Exigence 9.3_
- [ ] S.7 Clés i18n FR / EN, parité ; build de production

## Livraison

- [ ] L.1 Suites complètes, mutations, `business-rules.md` relu, mode d'emploi d'une page
