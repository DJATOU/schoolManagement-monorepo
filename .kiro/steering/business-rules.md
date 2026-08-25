# Business Rules — Payments & Attendance

Derived from the functional spec. This is the authoritative domain reference for any
payment / attendance / status logic. When code disagrees with this file, the code is wrong.

## Domain recap

- A **group** has a type (large / medium / small / individual), a subject, and a level.
- Each group type defines a number of **series** ("mois" / months). Each series has a
  fixed number of **sessions**. Each session has a **price** set by the school.
- Example: small math group = 8 sessions in the month, 30 € per session
  → month total = 8 × 30 = 240 €.

## The two money quantities — DO NOT CONFLATE (this is audit finding H5)

Earlier code merged two different amounts into one, producing contradictory payment
statuses. They answer different questions and must be computed by separate functions.

### 1. Month total cost
```
monthTotalCost = plannedSessionsForSeries × pricePerSession
```
- What the full month / series costs.
- Used for: receipts, "how much is the month", "is the month fully paid".

### 2. Amount due so far (the late / overdue threshold)
```
amountDueSoFar = attendedSessions × pricePerSession
```
- Spec rule (verbatim): "Si le nombre de séance étudiée × prix séance <= ce que
  l'étudiant a versé → il n'est pas en retard, sinon il est en retard."
- Only **attended (present)** sessions count. Absent sessions do NOT raise this threshold.
- Used for: the "en retard / à jour" status (the red/green indicator).

### Derived statuses
```
isLate        = amountPaid < amountDueSoFar
isMonthFullyPaid = amountPaid >= monthTotalCost
```
A student may legitimately pay session-by-session ("par facilité"): as long as
`amountPaid >= attendedSessions × price` they are "à jour" (not late), even if the
full month is not yet paid.

## Attendance counting rules

- "Attended" = attendance record with `isPresent == true`
  (must stay consistent with `PaymentStatusService.isStudentPaymentOverdueForSeries`).
- **L'unité de facturation est la Série, pas le mois civil.** Le décompte des séances suivies
  est donc borné à la série (`attendance.sessionSeries.id`). Un mois civil contient couramment
  2 à 3 séries pour un même groupe : « une série = un mois » est faux et ne doit pas être
  supposé.
- Une version antérieure de cette règle demandait un décompte **cross-group sur le mois civil**
  (sommer l'ancien et le nouveau groupe en cas de changement en cours de mois). Cette
  agrégation automatique est **abandonnée** : elle introduisait une seconde granularité ne
  correspondant à aucune entité, alors que paiements, reçus, relevés et devis sont tous indexés
  par série. Le changement de groupe en cours de mois est **traité administrativement**, cas
  rare et manuel.
- En contrepartie, le système **doit rendre ce cas visible** : lorsqu'un étudiant a des séances
  suivies dans deux groupes différents sur le même mois civil, l'interface doit le signaler à
  l'administrateur pour qu'il ajuste manuellement. Le signalement est obligatoire ; l'ajustement
  ne l'est pas.
- Catch-up (rattrapage): a session a student makes up in another group counts as
  attended for them; the date and the group where they attended must be recorded.
- A student may attend the same subject in two different groups (G1 + G2) — both count, mais
  **chacun sur sa propre série**. Les deux décomptes ne sont jamais additionnés en un seul
  seuil de retard : chaque série porte son propre statut.

## Planned session count (source for monthTotalCost)

- The planned number of sessions per series comes from the group / series configuration
  (the school sets it per group type — e.g. 8).
- ⚠ Reality drifts from the plan: supplementary sessions, teacher absences, group
  changes. The spec explicitly flags end-of-year reconciliation as an UNRESOLVED problem
  ("les séances restantes ne sont pas exactement définies").

## Prorata : arrivée en cours de série

Un étudiant qui rejoint un groupe alors que des séances sont déjà passées **ne paie pas les
séances auxquelles il n'a pas assisté**.

```
séance facturable = séance postérieure ou égale à la date d'inscription
                    OU séance où l'étudiant a une présence active
monthTotalCost    = séances facturables × pricePerSession × (1 − réduction)
```

Conséquences à ne pas manquer :

- **Le statut de paiement s'évalue contre le coût au prorata**, jamais contre
  `total_sessions × prix`. Un étudiant arrivé à la 4ᵉ séance d'une série de 4 et ayant réglé cette
  séance est **à jour et soldé**. L'évaluer contre le coût nominal le ferait apparaître
  indéfiniment en retard.
- Une séance suivie **en rattrapage avant l'inscription est facturable** : elle a été consommée.
  Elle doit être identifiée comme rattrapage dans l'historique, sinon sa facturation paraît
  arbitraire.
- Les séances exclues doivent rester **visibles** dans l'historique, marquées non facturées et
  non présentes. Une séance exclue n'est pas une dette et ne doit pas s'afficher comme telle.

## Versement excédentaire : report sur la série suivante

Un versement s'arrête au montant dû de la série visée. Le surplus est **reporté sur les séries
suivantes** par identifiant croissant, jusqu'à épuisement.

- Une série ne peut recevoir un report que si elle comporte au moins une séance facturable :
  **une série sans séances planifiées n'est pas ouverte**.
- Une série exemptée ou soldée donne un plafond nul : elle est sautée, le report continue.
- Si une part du surplus ne peut être placée nulle part, **le versement est refusé en totalité**,
  avec le maximum encaissable et l'action corrective (créer les séances de la série suivante).
  Un encaissement partiel ferait diverger l'argent reçu du montant enregistré, et laisserait la
  différence en main sans trace.
- Le report est automatique. Aucun trop-perçu n'est créé par un report.

## Reductions

- Some students pay with a discount (social cases / connections). The calculation must
  support a per-student or per-payment reduction on the amount owed.

## Money type

- All monetary values must use `BigDecimal`, never `double` (audit H4). Define an
  explicit scale and rounding policy in the calculator.

## Explicitly OUT OF SCOPE for the cost calculator

- Payment installments ("paiement par facilité") = HOW an amount is paid (multiple
  payment records over time), not how much is owed. Handle in payment recording.
- Book payments, teacher payroll, reminders / SMS / email — separate features.

## DEFERRED policy decision — do NOT hardcode an assumption

At month / year end, does a student owe for sessions they were **absent** from
(and does justified vs unjustified absence change that)?
- For the daily "en retard" status: only attended sessions count (rule above).
- For final reconciliation: the spec leaves this open. Leave a clear extension point;
  do not bake in an assumption. Ask the product owner before implementing it.
