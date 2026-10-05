# Design Document — Paie des enseignants

Exigences : `requirements.md`. Règles métier : `.kiro/steering/business-rules.md`, section « Paie des
enseignants ». Réutilise le moteur de correction de la spec `admin-corrections`.

## Vue d'ensemble

```
Série_Terminée ──► Encaissé_Net (source unique, = relevé de recettes)
                         │
Taux choisi ─────────────┼──► Aperçu (Part_Enseignant, Part_École) ──► Paie PAIE-AAAA-NNNN ──► Bordereau
                         │
Argent arrivé/rendu ─────┴──► Écart_À_Régulariser ──► Régularisation (Complément / Retenue)

Paie active ──► bloque la dévalidation d'une Séance de sa Série
Erreur ──► Correction (annuler / remplacer), Motif, Aperçu, Trace
```

## Décisions

**D1. Copie figée.** Une Paie recopie l'Enseignant, le Groupe, la Série, l'Encaissé_Net, le libellé
et le pourcentage du taux, et les deux parts. Changer un taux, un Enseignant de Groupe ou un
encaissement ne réécrit jamais une Paie (exigences 1.6, 6.1). Le lien vers le taux est conservé pour
le filtre, nullable.

**D2. Une Paie_Initiale, des Régularisations.** Au plus une Paie_Initiale active par Série, garantie
par un index unique partiel. Une Régularisation est une Paie de type `REGULARIZATION` rattachée à la
Paie_Initiale, au pourcentage figé de celle-ci. Pour toute Paie :

```
base_delta     = Encaissé_Net couvert par cette Paie − Encaissé_Net couvert par la précédente
teacher_amount = Paie_Initiale : arrondi(base × p / 100)
                 Régularisation : arrondi(Encaissé_Net actuel × p / 100) − Σ teacher_amount actifs
school_amount  = base_delta − teacher_amount
```

La Régularisation absorbe l'écart d'arrondi : après elle, Σ teacher_amount = arrondi(Net × p / 100)
exactement, et Σ teacher_amount + Σ school_amount = Net couvert. C'est la propriété vérifiée en
jqwik (P1).

**D3. Corriger la plus récente seulement.** Annuler ou remplacer n'est permis que sur la Paie active
la plus récente de la Série (exigence 7.3). Sans cette règle, annuler une Paie_Initiale suivie d'une
Régularisation laisserait une Régularisation calculée sur une base qui n'existe plus.

**D4. Encaissé_Net, source unique.** Nouveau `SeriesCollectionService.of(seriesId)` →
`(brut, remboursé, net)`, lu comme `GroupRevenueService` : en-têtes `payments` non annulés, moins
remboursements actifs de la Série. `GroupRevenueService` passe par lui pour sa ventilation par Série.
Un test vérifie l'égalité des deux écrans (exigence 3.3).

**D5. Série_Terminée en une requête.** `SessionRepository.countCompletionBySeries(seriesIds)` →
par Série, séances actives et séances actives validées. Terminée ⇔ actives > 0 et validées = actives.
`session_series.sessions_completed` n'est jamais tenu à jour : il n'est pas lu.

**D6. Payer n'est pas une Correction.** Payer suit le même protocole Aperçu / confirmation à jeton,
mais dans `TeacherPayoutService` : le jeton est l'empreinte SHA-256 de `payout-preview-v1 | type |
Série | Enseignant | taux | pourcentage | brut | remboursé | net | net couvert | base | part
enseignant | part école | déjà versé | dernière Paie active`, montants sans zéros de queue. Un
changement d'état de la Série (non terminée, sans enseignant) est un refus nommé, pas un jeton
périmé. Une confirmation périmée renvoie 409 `STALE_PREVIEW` avec le nouvel Aperçu et son jeton.
La ligne `session_series` est verrouillée (`PESSIMISTIC_WRITE`) pendant la confirmation : deux
confirmations simultanées se sérialisent, l'index unique reste le filet de sécurité.

**D7. Corriger passe par `CorrectionRunner`.** Annuler et remplacer sont des `CorrectionCommand`
(`service/correction/PayoutCorrectionService`, à côté des autres corrections), domaine
`TEACHER_PAYOUT`, actions `PAYOUT_CANCELLED` et `PAYOUT_REPLACED`, portée vide (aucun montant
d'élève ne change). Effets `PAYOUT_CANCELLED`, `PAYOUT_CREATED`, `PAYOUT_SHARES_CHANGED` : ce que la
Série a versé à l'enseignant et gardé pour l'école, avant / après, ce qui rend l'Aperçu périmable.
Traces dans `correction_audit` (`entity_id` = Paie, `group_id`, `series_id`, `student_id` nul ;
valeurs avant / après avec les cumuls de la Série). Motifs : `DATA_ENTRY_ERROR`, `WRONG_AMOUNT`,
`OTHER`. La Série est verrouillée avant de lire la Paie : une paie ou une autre correction de la
même Série attend.

**D7 bis. Ce qu'un remplacement garde.** Paie initiale seulement, taux actif et différent :
même enseignant, même groupe, même Série que l'originale, Encaissé_Net relu. L'originale est annulée
et écrite **avant** l'insertion de la remplaçante (l'index n'admet qu'une Paie_Initiale active), puis
reliée à elle. Payer un autre enseignant passe par annuler, puis payer.

**D8. Numéro de Paie.** Compteur verrouillé à une ligne, `payout_counter`, sur le modèle de
`receipt_counter` (le rejeu sur collision du modèle `REMB` échoue sur PostgreSQL). Un Aperçu ou un
refus ne consomme aucun numéro (transaction annulée).

**D9. Garde sur les séances.** `PaidSeriesGuard.assertNoActivePayout(series, opération)` est appelé
par la dévalidation (`AttendanceCorrectionService`, dès l'Aperçu), la suppression, la désactivation
et la réactivation d'une Séance, et par `PATCH /api/sessions/{id}` quand il dévalide réellement
(validée avant, plus après) ou change le groupe de la Séance. Refus 409 nommant les Paies actives,
la plus récente d'abord à annuler (exigence 8). Un `PATCH` qui renvoie la séance entière sans
changer sa validation passe : le client envoie toujours l'objet complet.

Non gardé, délibérément : le rattachement d'une **nouvelle** Séance à une Série payée mais pas
pleine (`SeriesRolloverService`, création ou génération récurrente). Le refuser bloquerait la
planification ordinaire ; l'argent qu'elle apportera passe par une Régularisation.

**D10. Année close.** Les écritures de Paie n'appellent pas `ReadOnlyYearGuard` (exigence 10.1).
La garde D9 s'ajoute à celle de l'année, elle ne la remplace pas.

**D11. Périmètre de « À payer ».** Sans filtre de groupe : toutes les Séries des groupes actifs de
l'année courante, et, des autres années (groupe sans année compris), seulement celles qui appellent
un paiement (`PAYABLE`, `TO_REGULARIZE`). Limiter la liste à l'année courante ferait disparaître la
dernière Série d'une année close dès la bascule, alors que la payer est voulu (exigence 10.1) ; tout
lister ferait remonter chaque Série inachevée des années passées. Avec un filtre de groupe, tout est
listé. Une Série sans séance active, ou payée et à jour, n'est jamais listée.

**D12. Violation de contrainte à l'enregistrement.** Seule la violation de
`uk_teacher_payout_initial_active` se traduit en 409 « payée par ailleurs ». Toute autre violation
remonte telle quelle : la traduire maquillerait un défaut de calcul (ou de numérotation) en conflit
d'utilisateur.

## Modèle de données — migration V9

```
teacher_pay_rate
  id, label VARCHAR(100), teacher_percent NUMERIC(5,2) CHECK (> 0 AND < 100),
  active BOOLEAN, created_at, created_by, updated_at, updated_by
  uk_teacher_pay_rate_label : UNIQUE (lower(btrim(label))) WHERE active

teacher_payout
  id, payout_number VARCHAR(32) UNIQUE,
  kind VARCHAR(20) CHECK IN ('INITIAL','REGULARIZATION'),
  initial_payout_id → teacher_payout   (NULL ⇔ INITIAL)
  teacher_id → teacher, group_id → groups, series_id → session_series,
  rate_id → teacher_pay_rate (NULL toléré), rate_label, teacher_percent,
  collected_gross, refunded, collected_net,       -- Encaissé_Net couvert, et sa composition
  base_delta, teacher_amount, school_amount NUMERIC(12,2),
  note TEXT, paid_at, paid_by,
  status CHECK IN ('ACTIVE','CANCELLED'), cancelled_at, cancelled_by,
  cancel_reason_type, cancel_reason_text, replaces_id, replaced_by_id
  ck_payout_parts         : teacher_amount + school_amount = base_delta
  ck_payout_initial       : INITIAL ⇒ teacher_amount > 0 AND initial_payout_id IS NULL
  ck_payout_regularization: REGULARIZATION ⇒ teacher_amount <> 0 AND initial_payout_id IS NOT NULL
  ck_payout_cancellation, ck_payout_cancel_reason_other  (modèle encashment)
  uk_payout_initial_active : UNIQUE (series_id) WHERE kind = 'INITIAL' AND status = 'ACTIVE'
  idx_payout_teacher (teacher_id, paid_at DESC), idx_payout_series (series_id)

payout_counter            -- une ligne, verrouillée, modèle receipt_counter
payout_slip_issuance      -- id, payout_id, rank, issued_at, issued_by ; UNIQUE (payout_id, rank)
```

## Backend

```
persistance/  TeacherPayRateEntity, TeacherPayoutEntity, PayoutCounterEntity, PayoutSlipIssuanceEntity
              PayoutKind {INITIAL, REGULARIZATION}, PayoutStatus {ACTIVE, CANCELLED}
service/payroll/
  SeriesCollectionService      Encaissé_Net d'une Série (D4)
  SeriesCompletionService      Série_Terminée (D5)
  PayoutCalculator             parts et écart, BigDecimal pur, sans dépôt (D2)
  TeacherPayRateService        catalogue (exigence 1)
  TeacherPayoutService         à payer, Aperçu / confirmation, Régularisation (exigences 2 à 4, 6)
  (service/correction/) PayoutCorrectionService   annuler, remplacer via CorrectionRunner (exigence 7, D7)
  PayoutNumberService          PAIE-AAAA-NNNN (D8)
  PayoutSlipService            données du Bordereau, rang du duplicata (exigence 5)
  PaidSeriesGuard              (exigence 8, D9)
controller/
  TeacherPayRateController     GET/POST /api/teacher-pay-rates, PUT /{id}, PATCH /{id}/disable
  TeacherPayoutController      GET  /api/teacher-payouts/payable?teacherId&groupId
                               GET  /api/teacher-payouts?teacherId&groupId&from&to&status
                               GET  /api/teachers/{id}/payouts
                               POST /api/teacher-payouts/series/{seriesId}/pay/{preview|confirm}
                               POST /api/teacher-payouts/series/{seriesId}/regularize/{preview|confirm}
                               POST /api/teacher-payouts/{id}/slips
  PayoutCorrectionController   GET  /api/teacher-payouts/correction-reasons
                               POST /api/teacher-payouts/{id}/cancel/{preview|confirm}
                               POST /api/teacher-payouts/{id}/replace/{preview|confirm}
SecurityConfig : GET /api/teacher-pay-rates/**, /api/teacher-payouts/**, /api/teachers/*/payouts
                 → ADMIN, avant la règle GET générique. Écritures déjà ADMIN.
```

## Frontend

| Écran | Contenu |
|---|---|
| `admin/teacher-payroll` (menu Gestion financière, ADMIN) | onglets À payer, Paies versées, Taux |
| À payer | Séries terminées non payées, puis Séries à régulariser ; bouton « Payer » / « Régulariser » |
| Dialogue de paie | choix du taux actif, Aperçu du calcul en clair, note, confirmation ; Bordereau proposé |
| Paies versées | filtres, totaux des deux parts, statut, actions réimprimer / annuler / remplacer |
| Taux | tableau, création, modification du libellé, désactivation |
| Fiche Enseignant | panneau « Paies » : liste, total versé, réimpression |

Services : `TeacherPayRateService`, `TeacherPayoutService` (HTTP seul, erreurs centralisées).
Bordereau : `PayoutSlipPdfService.buildDocument` pur, impression par `PdfOutputService`. Correction :
`CorrectionDialogComponent` existant. Montants : pipe `amount`.

## Propriétés vérifiées

- **P1** Pour toute suite d'Encaissés_Nets et de Régularisations : Σ teacher + Σ school = Net couvert,
  et après Régularisation Σ teacher = arrondi(Net × p / 100).
- **P2** Deux confirmations concurrentes d'une même Série : une seule Paie_Initiale active.
- **P3** Encaissé_Net de la Paie = valeur de la Série dans le relevé de recettes du Groupe.
- **P4** Aucun Aperçu, refus ou confirmation périmée ne consomme de Numéro_Paie ni n'écrit de Paie.

## Risques

- Une Série de rattrapage pur (groupe d'accueil) encaisse au nom de l'enseignant d'accueil : c'est
  l'effet voulu de la base « Encaissé_Net de la Série », à rappeler dans le mode d'emploi.
- La garde D9 ajoute un refus à la dévalidation : les tests existants de dévalidation doivent rester
  verts (aucune Paie dans leurs fixtures) et un test dédié couvre le refus.
