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
- Attended count is **cross-group within the current month**: if a student changes
  group mid-month, sum sessions attended in the OLD group + the NEW group for that month.
- Catch-up (rattrapage): a session a student makes up in another group counts as
  attended for them; the date and the group where they attended must be recorded.
- A student may attend the same subject in two different groups (G1 + G2) — both count.

## Planned session count (source for monthTotalCost)

- The planned number of sessions per series comes from the group / series configuration
  (the school sets it per group type — e.g. 8).
- ⚠ Reality drifts from the plan: supplementary sessions, teacher absences, group
  changes. The spec explicitly flags end-of-year reconciliation as an UNRESOLVED problem
  ("les séances restantes ne sont pas exactement définies").

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
