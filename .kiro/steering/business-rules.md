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
  arbitraire. ⚠ Cette règle vise le cas où **aucune autre série ne facture cette séance** : voir
  « Rattrapage : une séance consommée est facturée une fois et une seule » pour la distinction
  entre rattrapage compensatoire et rattrapage consommé.
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

## La justification d'absence est documentaire — TRANCHÉ

**Question posée** : une absence justifiée change-t-elle le montant dû ?

**Décision du propriétaire produit : non. Aucun effet financier.** L'indicateur de justification
sert au **suivi disciplinaire** et au **droit au rattrapage**, jamais au calcul. Il n'apparaît dans
aucun chemin de calcul monétaire, et cette absence est vérifiée par une propriété exécutable
(`JustificationNeutralityPropertyTest`) : faire varier arbitrairement la justification des absences
d'une série laisse le coût au prorata, le montant dû à ce jour, le plafond encaissable et le statut
de paiement identiques.

Concrètement, et c'est ce qu'il faut dire à l'écran :

- **aucune absence n'augmente le montant dû à ce jour**, justifiée ou non — seules les séances
  suivies le font, donc une absence ne met jamais un étudiant en retard ;
- **toute absence postérieure à l'inscription reste comptée dans le coût de la série**, justifiée ou
  non : la place était réservée.

Cette décision devait être affichée, pas seulement codée : l'ambiguïté avait déjà produit une
attente erronée (« si c'est justifié, la séance n'est pas facturée »). L'interface énonce donc les
deux règles ci-dessus sur la ligne de chaque absence.

**Modification et traçabilité.** La justification est corrigeable après saisie, par un point d'entrée
dédié réservé à l'ADMIN, et chaque changement laisse une trace immuable — valeur avant, valeur après,
auteur, horodatage, commentaire. La trace survit à la suppression de la présence auditée : c'est
justement après la disparition d'une donnée qu'on a besoin de savoir qui l'a modifiée.

## Rattrapage : une séance consommée est facturée une fois et une seule

Un étudiant qui manque une séance dans son groupe et la rattrape dans un autre ne doit pas la payer
deux fois. Deux cas, distingués par une **comparaison de dates et rien d'autre** :

- **Rattrapage compensatoire** — la séance manquée est postérieure ou égale à la date d'inscription
  de l'étudiant dans le groupe de cette séance. Elle est donc facturée dans sa série d'origine, où la
  place était réservée. Le rattrapage est alors **gratuit dans le groupe d'accueil**, et la séance
  manquée compte comme **suivie** dans sa série d'origine, sans que la présence d'origine cesse d'être
  une absence.
- **Rattrapage consommé** — aucune autre série ne facture cette séance (séance manquée antérieure à
  l'inscription, inconnue, ou sans inscription active dans son groupe). Elle est alors **facturable
  côté accueil**, ce qui est la lecture exacte de la règle « une séance suivie en rattrapage avant
  l'inscription est facturable » énoncée plus haut : elle a été consommée et personne d'autre ne la
  facture.

Deux points d'implémentation qui ont valeur de règle :

- la qualification **ne consulte jamais** le coût, le montant versé ni le statut de paiement de la
  série d'origine. Elle ne compare que des dates stockées, ce qui interdit toute récursion entre
  séries et rend le résultat indépendant de l'ordre d'évaluation ;
- une séance couverte à la fois par un rattrapage compensatoire **et** par une présence ordinaire
  reste facturable côté accueil : la gratuité ne vaut que si le rattrapage est la seule raison d'être
  présent.

Un seul rattrapage est possible par couple (étudiant, séance manquée) : deux rattrapages d'une même
séance rendraient indéterminé lequel compense l'absence.

## DEFERRED policy decision — do NOT hardcode an assumption

At month / year end, does a student owe for sessions they were **absent** from?
- For the daily "en retard" status: only attended sessions count (rule above), and the
  justification flag has no bearing on it (décision tranchée ci-dessus).
- For final reconciliation: the spec leaves this open. Leave a clear extension point;
  do not bake in an assumption. Ask the product owner before implementing it.
