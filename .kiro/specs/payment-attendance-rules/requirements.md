# Requirements Document

## Introduction

This feature formalizes and implements the payment, attendance, catch-up, discount, and
refund business rules for the private-school management application (Spring Boot / Java 21
backend, Angular 17 frontend). The existing steering file `.kiro/steering/business-rules.md`
is the current source of truth but contains ambiguities that the product owner has now
resolved. This specification captures those decisions as verifiable requirements and drives
the implementation.

The work covers seven decision areas: series-based billing with automatic naming and
rollover, catch-up (rattrapage) workflow, payment notes, multi-level discounts/exemptions,
immediate late status, refunds, and a strict `BigDecimal` money model. It also repairs known
defects: `PaymentStatusService` still computes with `double`, the `PaymentCostCalculator`
(pure `BigDecimal`) is not wired into production, the amount-paid query does not `SUM`
non-cancelled payments, and the backend `/api/catch-ups` workflow does not exist even though
the frontend `catch-up.service.ts` already calls it.

## Glossary

- **System**: The school management application as a whole (backend + frontend) unless a more
  specific component is named.
- **Cost_Calculator**: The `PaymentCostCalculator` component, a pure `BigDecimal` calculator
  that is the single source of truth for monetary amounts.
- **Payment_Status_Service**: The backend service that derives late / paid statuses.
- **Catch_Up_Service**: The backend service exposing the `/api/catch-ups` workflow.
- **Series**: A `SessionSeriesEntity`; the billing unit. Contains a fixed planned number of
  sessions for a group. Referred to in French as "mois" but billing follows the series, not
  the calendar month.
- **Session**: A single scheduled class occurrence (`SessionEntity`) belonging to a Series.
- **Group**: A class grouping (`GroupEntity`) with a type, subject, level, and a
  price per session.
- **Group_Type**: The category of a Group (large / medium / small / individual).
- **Attendance**: An `AttendanceEntity` record linking a Student and a Session, carrying
  `isPresent`, `isJustified`, and `isCatchUp` flags.
- **Catch_Up**: A rattrapage; a make-up session a Student attends to replace a missed one.
- **Catch_Up_Request**: A tracked record of a catch-up moving through the lifecycle
  PENDING → SCHEDULED → COMPLETED (or CANCELLED).
- **Catch_Up_Right**: A per-Attendance boolean, defaulting to true (checked), granting the
  Student the right to make up a missed Session.
- **Planned_Sessions**: The configured number of sessions for a Series
  (`SessionSeriesEntity.totalSessions`).
- **Attended_Sessions**: The count of Attendance records with `isPresent == true` for a
  Student, counted cross-group within the relevant Series scope.
- **Price_Per_Session**: The monetary price of one Session, taken from the Group price.
- **Month_Total_Cost**: `Planned_Sessions × Price_Per_Session`, after any applicable discount;
  the full cost of the Series. Used for receipts and "is the Series fully paid".
- **Amount_Due_So_Far**: `Attended_Sessions × Price_Per_Session`, after any applicable
  discount; the late threshold.
- **Amount_Paid**: The sum of all non-cancelled payment amounts recorded for a Student and a
  Series.
- **Discount**: A reduction applied to the amount owed, expressed as a rate in [0.00, 1.00],
  definable at Group, Series, or Session level (also called exemption).
- **Exempted_Group**: A Group for which a Student is exempted for the whole year (a 100%
  Group-level Discount), shown with the dedicated legend "Présent et exempté".
- **Refund**: A recorded reversal of money to a Student, tracked in payment history.
- **Payment_Notes**: A free-text comment an administrator may attach to a payment.
- **Administrator**: A user managing payments, attendance, discounts, and catch-ups.
- **MONEY_SCALE**: Monetary scale of 2 decimal places.
- **MONEY_ROUNDING**: Rounding policy `HALF_UP`.

## Requirements

### Requirement 1: Series as the billing unit

**User Story:** As an administrator, I want billing to follow the Series rather than the
calendar month, so that each configured block of sessions is charged as one unit.

#### Acceptance Criteria

1. THE System SHALL treat the Series as the billing unit for all payment, late-status, and
   receipt calculations.
2. THE System SHALL compute Month_Total_Cost as Planned_Sessions multiplied by
   Price_Per_Session for the Series.
3. WHERE a Student attends the same subject in two different Groups, THE System SHALL count
   Attended_Sessions from both Groups within the relevant Series scope.

### Requirement 2: Series naming

**User Story:** As an administrator, I want each Series named consistently with a monthly
sequence number, so that I can identify and order Series unambiguously.

#### Acceptance Criteria

1. WHEN a Series is created, THE System SHALL assign a name in the format
   "Série {group_name} - {Month}-{Year}-{NNN}" where {NNN} is a zero-padded three-digit
   sequence number.
2. WHEN a Series is created, THE System SHALL record the Series start date.
3. WHEN a Series is created in a calendar month that already contains one or more Series for
   the same Group, THE System SHALL assign the next sequence number in that month
   incremented by one.
4. WHEN a Series is the first created for a Group in a given calendar month, THE System SHALL
   assign the sequence number 001.
5. WHEN the calendar month of the Series start date changes from the previous Series, THE
   System SHALL restart the sequence number at 001.

### Requirement 3: Automatic series rollover for supplementary sessions

**User Story:** As a teacher, I want additional sessions beyond a Series' planned count to
start a new Series automatically, so that the extra sessions form a new billing unit.

#### Acceptance Criteria

1. WHEN a Session is added to a Group and the current Series already contains a number of
   sessions equal to its Planned_Sessions, THE System SHALL create the next Series for the
   Group and attach the added Session to that next Series.
2. WHEN the next Series is created by rollover, THE System SHALL name it following the Series
   naming rules in Requirement 2.
3. WHEN a Session is added to a Group and the current Series contains fewer sessions than its
   Planned_Sessions, THE System SHALL attach the added Session to the current Series.

### Requirement 4: BigDecimal money model and calculator wiring

**User Story:** As a developer, I want all monetary values computed with a single
`BigDecimal` calculator, so that payment statuses are consistent and free of floating-point
error.

#### Acceptance Criteria

1. THE Cost_Calculator SHALL represent every monetary amount as a BigDecimal with MONEY_SCALE
   decimal places rounded using MONEY_ROUNDING.
2. THE Payment_Status_Service SHALL derive Month_Total_Cost, Amount_Due_So_Far, late status,
   and fully-paid status by delegating to the Cost_Calculator.
3. THE Cost_Calculator SHALL compute Amount_Due_So_Far as Attended_Sessions multiplied by
   Price_Per_Session, after applying the applicable Discount.
4. THE Cost_Calculator SHALL compute Month_Total_Cost as Planned_Sessions multiplied by
   Price_Per_Session, after applying the applicable Discount.
5. WHERE the applicable Discount rate equals 1.00, THE Cost_Calculator SHALL return
   Amount_Due_So_Far and Month_Total_Cost equal to zero.
6. IF a monetary input to the Cost_Calculator is negative, THEN THE Cost_Calculator SHALL
   reject the input with a validation error.

### Requirement 5: Correct amount-paid aggregation

**User Story:** As an administrator, I want the amount a Student has paid to be the sum of all
their non-cancelled payments for a Series, so that late status reflects the real total paid.

#### Acceptance Criteria

1. THE System SHALL compute Amount_Paid as the sum of the amounts of all payment records for
   the Student and the Series whose status is not cancelled.
2. WHEN no non-cancelled payment record exists for the Student and the Series, THE System
   SHALL compute Amount_Paid as zero.
3. WHEN a payment record status is cancelled, THE System SHALL exclude that record from
   Amount_Paid.

### Requirement 6: Immediate late status

**User Story:** As an administrator, I want a Student who attended a Session but has not paid
enough to be flagged late immediately, so that overdue balances are visible without delay.

#### Acceptance Criteria

1. WHEN Amount_Paid is less than Amount_Due_So_Far, THE Payment_Status_Service SHALL report
   the Student as late for the Series.
2. WHEN Amount_Paid is greater than or equal to Amount_Due_So_Far, THE Payment_Status_Service
   SHALL report the Student as not late for the Series.
3. THE Payment_Status_Service SHALL apply no grace period before reporting late status.
4. WHEN Amount_Paid is greater than or equal to Month_Total_Cost, THE Payment_Status_Service
   SHALL report the Series as fully paid.
5. THE Payment_Status_Service SHALL count only Attendance records with isPresent equal to
   true toward Attended_Sessions.

### Requirement 7: Catch-up right

**User Story:** As an administrator, I want a paid Student who was absent to have a catch-up
right by default that I can revoke, so that make-ups are permitted unless explicitly denied.

#### Acceptance Criteria

1. WHEN an Attendance record is created with isPresent equal to false, THE System SHALL set
   the Catch_Up_Right to true by default.
2. THE System SHALL grant the Catch_Up_Right regardless of whether the absence is justified or
   unjustified.
3. WHERE an administrator revokes the Catch_Up_Right for an Attendance, THE System SHALL set
   the Catch_Up_Right to false.
4. IF a Catch_Up_Request is created for an Attendance whose Catch_Up_Right is false, THEN THE
   Catch_Up_Service SHALL reject the request with an error message.
5. IF a Catch_Up_Request is created for a Student who has not paid for the missed Session,
   THEN THE Catch_Up_Service SHALL reject the request with an error message.

### Requirement 8: Catch-up group compatibility

**User Story:** As an administrator, I want catch-ups restricted to compatible groups, so
that a make-up is billed at the same price as the original.

#### Acceptance Criteria

1. WHEN available catch-up Sessions are requested for a missed Session, THE Catch_Up_Service
   SHALL return only Sessions whose Group has the same Group_Type as the original Group.
2. WHEN available catch-up Sessions are requested for a missed Session, THE Catch_Up_Service
   SHALL return only Sessions whose Group has the same Price_Per_Session as the original
   Group.
3. THE Catch_Up_Service SHALL allow the catch-up Session to belong to the original Group or to
   a different Group meeting the compatibility rules.
4. IF a Catch_Up_Request is scheduled against a Session whose Group violates the Group_Type or
   Price_Per_Session compatibility rules, THEN THE Catch_Up_Service SHALL reject the schedule
   with an error message.

### Requirement 9: Catch-up workflow lifecycle

**User Story:** As an administrator, I want a complete backend catch-up workflow, so that the
existing frontend catch-up calls succeed and the process is tracked end to end.

#### Acceptance Criteria

1. THE Catch_Up_Service SHALL expose endpoints under `/api/catch-ups` for creating a request,
   listing pending requests, listing requests by student, listing available sessions,
   scheduling, completing, and cancelling.
2. WHEN a Catch_Up_Request is created, THE Catch_Up_Service SHALL set the request status to
   PENDING and record the request date, the Student, the missed Session, and the missed
   Session's Group.
3. WHEN a PENDING Catch_Up_Request is scheduled against a compatible catch-up Session, THE
   Catch_Up_Service SHALL set the request status to SCHEDULED and record the catch-up Session,
   the catch-up Group, and the scheduled date.
4. WHEN a SCHEDULED Catch_Up_Request is completed, THE Catch_Up_Service SHALL set the request
   status to COMPLETED, record the completed date, and create an Attendance for the catch-up
   Session with isPresent equal to true and isCatchUp equal to true.
5. WHEN a Catch_Up_Request is cancelled, THE Catch_Up_Service SHALL set the request status to
   CANCELLED and record the cancellation reason when a reason is provided.
6. IF a Catch_Up_Request transition is requested from a status that does not permit that
   transition, THEN THE Catch_Up_Service SHALL reject the transition with an error message.
7. THE Catch_Up_Service SHALL count a completed Catch_Up as an Attended_Session for the
   Student in the catch-up Group.

### Requirement 10: Catch-up traceability

**User Story:** As an administrator, I want every catch-up recorded in the Student history and
frontend, so that I can see where and when a make-up happened and which missed Session it
replaces.

#### Acceptance Criteria

1. WHEN a Catch_Up_Request reaches COMPLETED, THE System SHALL record the catch-up date, the
   Group where the catch-up occurred, and the link to the missed Session.
2. THE System SHALL include completed Catch_Ups in the Student payment and attendance history.
3. WHEN Student history is displayed, THE System SHALL identify each catch-up Attendance
   distinctly from a regular Attendance.

### Requirement 11: Payment notes

**User Story:** As an administrator, I want to attach a free-text note to a payment, so that I
can record context at the time of payment.

#### Acceptance Criteria

1. WHERE an administrator provides a note when recording a payment, THE System SHALL persist
   the note with the payment record.
2. WHEN a payment with a note is displayed, THE System SHALL show the note text.
3. WHERE no note is provided when recording a payment, THE System SHALL record the payment
   without a note.

### Requirement 12: Multi-level discounts and exemptions

**User Story:** As an administrator, I want to apply a single discount of one type — either
for a whole Group, a whole Series, or a chosen Session — so that social cases and exemptions
reduce the amount owed without ambiguity.

#### Acceptance Criteria

1. THE System SHALL define a Discount with exactly one scope: Group, Series, or Session.
2. WHERE a Discount is defined for a Group, THE Cost_Calculator SHALL apply the Discount to
   the amounts owed for that Group.
3. WHERE a Discount is defined for a Series, THE Cost_Calculator SHALL apply the Discount to
   the amounts owed for that Series.
4. WHERE a Discount is defined for a Session, THE Cost_Calculator SHALL apply the Discount to
   the amount owed for that Session.
5. THE System SHALL NOT combine or cumulate Discounts of different scopes for the same
   Student; only one Discount scope applies at a time.
6. WHERE a Student is an Exempted_Group member, THE System SHALL apply a 100% Group-scope
   Discount for that Group for the whole year.
7. IF a Discount rate outside the range 0.00 to 1.00 is submitted, THEN THE System SHALL
   reject the Discount with a validation error.
8. IF more than one Discount scope is submitted for the same Student and billing context,
   THEN THE System SHALL reject the operation with a validation error.

### Requirement 13: Refunds

**User Story:** As an administrator, I want to record a refund to a Student, so that returned
money is tracked in the payment history.

#### Acceptance Criteria

1. WHEN an administrator records a Refund for a Student, THE System SHALL persist the Refund
   with its amount, date, and the related payment.
2. THE System SHALL include recorded Refunds in the Student payment history.
3. THE System SHALL exclude the refunded amount from Amount_Paid for late-status calculation.
4. IF a Refund amount greater than the related Amount_Paid is submitted, THEN THE System SHALL
   reject the Refund with a validation error.

### Requirement 14: Frontend history and PDF traceability

**User Story:** As an administrator, I want the frontend history and PDF exports to show
payments, catch-ups, exemptions, and refunds with color legends, so that a Student's status
is auditable at a glance.

#### Acceptance Criteria

1. THE System SHALL display the Student payment and attendance history in the frontend
   including payments, completed Catch_Ups, Discounts, and Refunds.
2. WHEN a PDF export is generated, THE System SHALL include a color legend containing at least
   "Présent et exempté" and a catch-up indicator.
3. WHEN a Student is a member of an Exempted_Group, THE System SHALL display the "Présent et
   exempté" legend color for that Student's presence in the exempted Group.
4. WHEN a catch-up Attendance is displayed in the frontend or a PDF export, THE System SHALL
   mark it with the catch-up indicator.
