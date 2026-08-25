# Implementation Plan: Payment & Attendance Rules

## Overview

Test-driven implementation of series-based billing, naming, rollover, catch-up workflow,
payment notes, multi-level discounts/exemptions, immediate late status, refunds, and the
strict `BigDecimal` money model, plus the four defect repairs (double→BigDecimal refactor,
calculator wiring, `SUM` query fix, missing `/api/catch-ups` backend).

Ordering follows a test-driven bottom-up path: build/test tooling → entities & repositories →
pure logic (`PaymentCostCalculator` wiring + tests) → resolver → services (naming, rollover,
catch-up, discount, refund) → defect corrections → REST endpoints → mappers/DTOs → frontend
integration (history + PDF legends) → coverage verification.

Language/stack: Java 21, Spring Boot 3.4.1, JPA/Hibernate, MapStruct + `MappingContext`,
JUnit 5 + Mockito, jqwik for property-based tests, JaCoCo for coverage. Build & test via
`./build.sh` (e.g. `./build.sh clean test`).

Conventions honored: the `persistance` folder is NOT renamed; new entities live there.
DTO↔Entity mapping goes through `MappingContext`. Controllers stay thin. Upload stays
multipart with optional photo. French comments/messages are preserved.

### Testing policy (user requirement: 100% coverage of new backend business code)

- **All test sub-tasks are MANDATORY** (no `*` optional marking on tests). The target is
  100% line and branch coverage on new/modified backend business packages
  (`service`, `service.payment`, calculator, resolver, naming, rollover, catch-up, discount,
  refund, validation).
- **Property-based tests** (jqwik): one test per design property (Properties 1–22), each
  `@Property(tries = 100)` minimum, tagged with the comment
  `Feature: payment-attendance-rules, Property {N}: {property text}`.
- **Example-based unit tests** (JUnit 5 + Mockito): cover nominal, boundary, error/validation,
  and every conditional branch to reach 100%.
- **Integration tests** (Spring Boot Test, H2): repository queries and endpoint wiring.
- A dedicated final task enforces the JaCoCo 100% threshold on the concerned packages.

## Tasks

- [x] 1. Set up test & coverage tooling in the build
  - Add the jqwik dependency (test scope) and JUnit 5 platform engine wiring to `back/pom.xml`.
  - Add the JaCoCo Maven plugin with `prepare-agent` + `report` and a `check` rule targeting
    100% line and branch coverage scoped to the new/modified business packages (calculator,
    resolver, naming, rollover, catch-up, discount, refund, payment status/validation).
  - Verify the toolchain compiles and runs an empty jqwik `@Property` via `./build.sh clean test`.
  - _Requirements: 4.1_

- [x] 2. Create and modify JPA entities in `persistance`
  - [x] 2.1 Add `CatchUpStatus` and `DiscountScope` enums
    - `CatchUpStatus`: PENDING, SCHEDULED, COMPLETED, CANCELLED.
    - `DiscountScope`: GROUP, SERIES, SESSION.
    - _Requirements: 9.2, 12.1_

  - [x] 2.2 Create `CatchUpRequestEntity` (extends `BaseEntity`)
    - Map student, originalSession, originalGroup, originalAttendance, catchUpSession,
      catchUpGroup, status, requestDate, scheduledDate, completedDate, cancellationReason,
      notes as specified in the design; fields align 1:1 with the front `CatchUpRequest`.
    - _Requirements: 9.2, 9.3, 9.4, 9.5, 10.1_

  - [x] 2.3 Create `DiscountEntity` (extends `BaseEntity`)
    - Map student, scope, groupId/seriesId/sessionId, rate (`precision 3, scale 2`).
    - Add `@PrePersist` invariant: exactly one scope id set, matching `scope`, others null.
    - _Requirements: 12.1, 12.7, 12.8_

  - [x] 2.4 Create `RefundEntity` (extends `BaseEntity`)
    - Map payment, student, amount (`precision 12, scale 2`), refundDate.
    - _Requirements: 13.1_

  - [x] 2.5 Modify `AttendanceEntity`
    - Add `catchUpRight` (Boolean, default true) and `missedSession` (`@ManyToOne` to `SessionEntity`).
    - _Requirements: 7.1, 7.3, 10.1_

  - [x] 2.6 Modify `PaymentEntity` (and `PaymentDetailEntity` as needed) for notes
    - Add nullable `notes` column (`length 1000`) to `PaymentEntity`.
    - _Requirements: 11.1, 11.3_

  - [x] 2.7 Write persistence unit/integration tests for entity defaults & invariants
    - Assert `catchUpRight` defaults to true on absent attendance; discount `@PrePersist`
      rejects zero/multi-scope; notes persists null when absent.
    - _Requirements: 7.1, 11.3, 12.8_

- [x] 3. Create repositories and fix the amount-paid query
  - [x] 3.1 Create `CatchUpRequestRepository`, `DiscountRepository`, `RefundRepository`
    - `RefundRepository.sumRefundsForStudentAndSeries(...)` returning `BigDecimal`
      (COALESCE to 0).
    - _Requirements: 13.3_

  - [x] 3.2 Add `AttendanceRepository.countPresentForStudentAndSeries(...)`
    - Count only `isPresent = true`, cross-group within the series scope.
    - _Requirements: 1.3, 6.5_

  - [x] 3.3 Fix `PaymentRepository` amount-paid aggregation
    - Replace `findAmountPaidForStudentAndSeries` with
      `sumAmountPaidForStudentAndSeries` using `COALESCE(SUM(p.amountPaid),0)` over
      non-`CANCELLED` payments, returning `BigDecimal`.
    - _Requirements: 5.1, 5.2, 5.3_

  - [x] 3.4 Write integration tests (Spring Boot Test, H2) for repository queries
    - Cover: empty set → 0, all-cancelled → 0, mixed cancelled/non-cancelled sum;
      cross-group present-only attendance count; refund sum aggregation.
    - _Requirements: 1.3, 5.1, 5.2, 5.3, 6.5, 13.3_

- [x] 4. Wire and validate the pure `PaymentCostCalculator`
  - [x] 4.1 Confirm/adjust `PaymentCostCalculator` documentation and usage
    - Keep the existing pure signature (plannedSessions, attendedSessions, pricePerSession,
      rate); document that the rate parameter now receives the resolved discount rate.
    - _Requirements: 4.1, 4.3, 4.4, 4.5, 4.6_

  - [x] 4.2 Write property test — month total cost arithmetic
    - **Property 1: Month total cost arithmetic**
    - **Validates: Requirements 1.2, 4.4**

  - [x] 4.3 Write property test — amount-due-so-far arithmetic
    - **Property 2: Amount-due-so-far arithmetic**
    - **Validates: Requirements 4.3, 4.5**

  - [x] 4.4 Write property test — monetary outputs are scale-2
    - **Property 3: Monetary outputs are scale-2**
    - **Validates: Requirements 4.1**

  - [x] 4.5 Write property test — negative monetary inputs are rejected
    - **Property 4: Negative monetary inputs are rejected**
    - **Validates: Requirements 4.6**

  - [x] 4.6 Write property test — amount due is monotonic and bounded
    - **Property 5: Amount due is monotonic and bounded**
    - **Validates: Requirements 1.2, 4.3, 6.5**

  - [x] 4.7 Write example-based unit tests for `PaymentCostCalculator`
    - Cover rate 0.00, rate 1.00 (both amounts zero), zero sessions, zero price, rounding
      HALF_UP boundary, each validation branch — reach 100% line/branch on the calculator.
    - _Requirements: 4.1, 4.3, 4.4, 4.5, 4.6_

- [x] 5. Implement `DiscountService` (needed by the resolver)
  - [x] 5.1 Implement `DiscountService.create` and `resolveRate`
    - `create` validates exactly one scope, rate ∈ [0.00, 1.00], no conflict.
    - `resolveRate(studentId, seriesId)` selects the single most-specific applicable scope
      (Session > Series > Group), never sums; returns 0.00 when none; exemption = group rate 1.00.
    - Throw domain validation exceptions (French messages) from `service/exception`.
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 12.6, 12.7, 12.8_

  - [x] 5.2 Write property test — discount has exactly one scope
    - **Property 20: Discount has exactly one scope**
    - **Validates: Requirements 12.1, 12.8**

  - [x] 5.3 Write property test — discount rate range
    - **Property 21: Discount rate range**
    - **Validates: Requirements 12.7**

  - [x] 5.4 Write property test — single-scope discount selection
    - **Property 22: Single-scope discount selection**
    - **Validates: Requirements 12.5, 12.6**

  - [x] 5.5 Write example-based unit tests for `DiscountService`
    - Cover each scope branch, no-discount → 0.00, exemption 1.00, conflict rejection,
      boundary rates 0.00/1.00 — 100% line/branch.
    - _Requirements: 12.2, 12.3, 12.4, 12.5, 12.6_

- [x] 6. Implement `PaymentCostResolver` (calculator wiring layer)
  - [x] 6.1 Implement `PaymentCostResolver.calculatorFor` and `resolve`
    - Load series (plannedSessions, group, pricePerSession at scale 2), attended count
      (present-only, cross-group), discount rate; construct the calculator; compute effective
      amountPaid = sum(payments) − sum(refunds); return `PaymentStatusResult`.
    - Translate calculator `IllegalArgumentException` into the domain validation exception.
    - _Requirements: 1.1, 1.2, 1.3, 4.2, 5.1, 6.5, 13.3_

  - [x] 6.2 Write property test — attended count uses only present records, cross-group
    - **Property 6: Attended count uses only present records, cross-group**
    - **Validates: Requirements 1.3, 6.5, 9.7**

  - [x] 6.3 Write property test — amount paid is the sum of non-cancelled payments
    - **Property 9: Amount paid is the sum of non-cancelled payments**
    - **Validates: Requirements 5.1, 5.2, 5.3**

  - [x] 6.4 Write property test — effective amount paid excludes refunds
    - **Property 10: Effective amount paid excludes refunds**
    - **Validates: Requirements 13.3**

  - [x] 6.5 Write example-based unit tests for `PaymentCostResolver` (mocked repositories)
    - Cover null/zero conversions at the boundary, empty payment/refund sets, discount
      applied, and error translation — 100% line/branch.
    - _Requirements: 4.2, 5.2, 13.3_

- [x] 7. Refactor `PaymentStatusService` to delegate to the resolver (double→BigDecimal)
  - [x] 7.1 Replace `double` math with `PaymentCostResolver` delegation
    - Refactor `isStudentPaymentOverdueForSeries` and the per-session/series/group status
      builders; remove the `double pricePerSession` parameter in favor of internal
      `BigDecimal` resolution; keep public status DTO signatures stable.
    - _Requirements: 4.2, 6.1, 6.2, 6.3, 6.4, 6.5_

  - [x] 7.2 Write property test — payment status derivation is deterministic and idempotent
    - **Property 7: Payment status derivation is deterministic and idempotent**
    - **Validates: Requirements 6.1, 6.2, 6.3, 6.4**

  - [x] 7.3 Write property test — status service matches the calculator
    - **Property 8: Status service matches the calculator**
    - **Validates: Requirements 4.2**

  - [x] 7.4 Write example-based unit tests for `PaymentStatusService`
    - Cover late/not-late boundary (paid == due), fully-paid boundary (paid == total),
      no grace period, present-only counting — 100% line/branch.
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

- [x] 8. Checkpoint — Ensure all tests pass
  - Run `./build.sh clean test`. Ensure all tests pass, ask the user if questions arise.

- [x] 9. Implement `SeriesNamingService`
  - [x] 9.1 Implement `buildName` and `nextSequenceNumber`
    - Name format `"Série {group} - {MM}-{yyyy}-{NNN}"`; sequence counts existing series for
      the group in the same calendar month/year, zero-padded to 3 digits; restart at 001 on
      new month; record series start date.
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

  - [x] 9.2 Write property test — series name round-trip
    - **Property 12: Series name round-trip**
    - **Validates: Requirements 2.1**

  - [x] 9.3 Write property test — series sequence numbering
    - **Property 13: Series sequence numbering**
    - **Validates: Requirements 2.3, 2.4, 2.5**

  - [x] 9.4 Write example-based unit tests for `SeriesNamingService`
    - Cover first-in-month → 001, Nth-in-month → N+1, month change restart, zero-padding
      edge (e.g. 009→010, 099→100) — 100% line/branch.
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

- [x] 10. Implement `SeriesRolloverService`
  - [x] 10.1 Implement `attachSessionToSeries` (Option A automatic rollover)
    - Attach to current series when `size < totalSessions`; create next series (named via
      `SeriesNamingService`) when full; create first series (001) when none; wire invocation
      into the existing session-creation flow.
    - _Requirements: 3.1, 3.2, 3.3_

  - [x] 10.2 Write property test — rollover invariant
    - **Property 14: Rollover invariant**
    - **Validates: Requirements 3.1, 3.2, 3.3**

  - [x] 10.3 Write example-based unit tests for `SeriesRolloverService` (mocked repositories)
    - Cover no-series → first, not-full → current, exactly-full → new next series; assert no
      series exceeds `totalSessions` — 100% line/branch.
    - _Requirements: 3.1, 3.2, 3.3_

- [x] 11. Implement `RefundService`
  - [x] 11.1 Implement `RefundService.create`
    - Validate refund amount ≤ related payment's paid amount (no commercial gesture);
      persist amount, date, related payment, student; French validation message.
    - _Requirements: 13.1, 13.4_

  - [x] 11.2 Write property test — refund cannot exceed the related paid amount
    - **Property 11: Refund cannot exceed the related paid amount**
    - **Validates: Requirements 13.4**

  - [x] 11.3 Write example-based unit tests for `RefundService`
    - Cover amount == paid (accept), amount > paid (reject), amount < paid (accept),
      persistence fields — 100% line/branch.
    - _Requirements: 13.1, 13.4_

- [x] 12. Implement `CatchUpService` (state machine + side effects)
  - [x] 12.1 Implement create / available-sessions / schedule / complete / cancel
    - **create**: validate original attendance `catchUpRight == true` and missed session paid;
      set PENDING, record request date, student, missed session and its group.
    - **available-sessions**: return sessions whose group has same `Group_Type` and
      `Price_Per_Session` as the original group (original group included when compatible).
    - **schedule**: only from PENDING; re-validate compatibility; set SCHEDULED, record
      catch-up session/group and scheduled date.
    - **complete**: only from SCHEDULED; set COMPLETED, record completed date, create an
      `AttendanceEntity` with `isPresent=true`, `isCatchUp=true`, linked to catch-up
      session/group and the missed session.
    - **cancel**: set CANCELLED, record reason when provided; reject illegal transitions.
    - French validation/conflict messages from `service/exception`.
    - _Requirements: 7.4, 7.5, 8.1, 8.2, 8.3, 8.4, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7, 10.1_

  - [x] 12.2 Write property test — catch-up right defaults true independent of justification
    - **Property 15: Catch-up right defaults true independent of justification**
    - **Validates: Requirements 7.1, 7.2**

  - [x] 12.3 Write property test — catch-up creation preconditions
    - **Property 16: Catch-up creation preconditions**
    - **Validates: Requirements 7.4, 7.5**

  - [x] 12.4 Write property test — catch-up compatibility filter
    - **Property 17: Catch-up compatibility filter**
    - **Validates: Requirements 8.1, 8.2, 8.3, 8.4**

  - [x] 12.5 Write property test — catch-up lifecycle state machine
    - **Property 18: Catch-up lifecycle state machine**
    - **Validates: Requirements 9.3, 9.4, 9.5, 9.6, 9.7, 10.1**

  - [x] 12.6 Write example-based unit tests for `CatchUpService` (mocked repositories)
    - Cover each precondition rejection, each legal/illegal transition, cancel with/without
      reason, completion attendance side-effect fields — 100% line/branch.
    - _Requirements: 7.4, 7.5, 8.4, 9.3, 9.4, 9.5, 9.6, 9.7, 10.1_

- [x] 13. Checkpoint — Ensure all tests pass
  - Run `./build.sh clean test`. Ensure all tests pass, ask the user if questions arise.

- [x] 14. DTOs and MapStruct mappers (via `MappingContext`)
  - [x] 14.1 Create catch-up DTO and mapper
    - `CatchUpRequestDTO` mirroring the front interface; `CatchUpRequestMapper` resolving
      student/session/group/attendance references through `MappingContext`.
    - _Requirements: 9.2, 9.3_

  - [x] 14.2 Create discount and refund DTOs and mappers
    - `DiscountRequestDTO`/`DiscountResponseDTO`, `RefundRequestDTO`/`RefundResponseDTO`
      with MapStruct mappers via `MappingContext`.
    - _Requirements: 12.1, 13.1_

  - [x] 14.3 Extend payment DTOs with optional `notes`
    - Add nullable `notes` to payment request/response DTOs; return note in responses.
    - _Requirements: 11.1, 11.2, 11.3_

  - [x] 14.4 Extend history DTOs for legend rendering
    - Add `isExempted` / catch-up indicator / refund fields to `SessionHistoryDTO` /
      `SeriesHistoryDTO`; migrate history money fields to `BigDecimal`.
    - _Requirements: 10.2, 10.3, 13.2, 14.1_

  - [x] 14.5 Write unit tests for mappers and DTO mapping
    - Cover reference resolution via `MappingContext`, notes present/absent, exemption/catch-up
      flags — 100% line/branch on new mappers.
    - _Requirements: 11.2, 11.3, 12.1, 13.1, 14.1_

- [x] 15. REST controllers (thin) + payment notes wiring
  - [x] 15.1 Implement `CatchUpController` under `/api/catch-ups`
    - Endpoints matching the front `catch-up.service.ts` exactly: POST create, GET pending,
      GET student/{id}, GET available-sessions, PATCH {id}/schedule, PATCH {id}/complete,
      PATCH {id}/cancel; delegate all logic to `CatchUpService`.
    - _Requirements: 9.1_

  - [x] 15.2 Implement `DiscountController` and `RefundController`
    - Thin controllers over `DiscountService` / `RefundService`.
    - _Requirements: 12.1, 13.1_

  - [x] 15.3 Wire optional `notes` into the payment recording endpoint
    - Persist provided note; persist null when absent (keep multipart/optional-photo behavior
      unchanged elsewhere).
    - _Requirements: 11.1, 11.2, 11.3_

  - [x] 15.4 Write property test — payment note round-trip
    - **Property 19: Payment note round-trip**
    - **Validates: Requirements 11.1, 11.3**

  - [x] 15.5 Write integration tests for controllers (Spring Boot Test)
    - Cover `/api/catch-ups` full lifecycle wiring, discount/refund endpoints, payment note
      persistence and read-back; assert HTTP 400/404/409 mapping with French messages.
    - _Requirements: 9.1, 9.6, 11.1, 11.2, 12.7, 12.8, 13.4_

- [x] 16. Extend `StudentHistoryService` (backend history content)
  - [x] 16.1 Include catch-ups, discounts/exemptions, and refunds in history
    - Populate history/PDF-source DTOs with completed catch-ups (marked distinctly),
      discounts/exemptions, and refunds; use `BigDecimal` for money.
    - _Requirements: 10.2, 10.3, 13.2, 14.1_

  - [x] 16.2 Write unit tests for `StudentHistoryService`
    - Cover catch-up distinct marking, exemption flag, refund inclusion, empty history —
      100% line/branch.
    - _Requirements: 10.2, 10.3, 13.2, 14.1_

- [x] 17. Checkpoint — Ensure all backend tests pass
  - Run `./build.sh clean test`. Ensure all tests pass, ask the user if questions arise.

- [x] 18. Frontend history & PDF integration
  - [x] 18.1 Render history including catch-ups, discounts/exemptions, and refunds
    - Update the student history component to display payments, completed catch-ups (with
      catch-up indicator), discounts/exemptions, and refunds; keep one-service-per-entity and
      centralized HTTP error handling.
    - _Requirements: 14.1, 14.4_

  - [x] 18.2 Add PDF color legend and exempted-presence rendering
    - Add a color legend including "Présent et exempté" and a catch-up indicator to the PDF
      export; render exempted presence with the dedicated legend color.
    - _Requirements: 14.2, 14.3, 14.4_

  - [x] 18.3 Write frontend unit tests (Karma + Jasmine)
    - Cover history rendering of catch-ups/discounts/refunds, "Présent et exempté" legend,
      and catch-up indicator in the PDF legend.
    - _Requirements: 14.1, 14.2, 14.3, 14.4_

- [x] 19. Enforce 100% coverage on new backend business code
  - Run `./build.sh clean test` to produce the JaCoCo report.
  - Ensure the JaCoCo `check` rule passes at 100% line and branch coverage for the concerned
    packages (calculator, resolver, naming, rollover, catch-up, discount, refund, payment
    status/validation, new mappers).
  - Add targeted example tests to close any remaining uncovered branch until the threshold is met.
  - _Requirements: 4.1_

- [x] 20. Final checkpoint — Ensure all tests pass and coverage holds
  - Run `./build.sh clean test`. Ensure all tests pass and the 100% coverage gate is green;
    ask the user if questions arise.

## Notes

- Test sub-tasks are MANDATORY here (no `*` optional marking) because the user requires 100%
  coverage plus the 22 property-based tests. Only genuinely non-essential sub-tasks would be
  marked `*`; task 2.7 is the sole optional item (redundant with later stronger coverage).
- Each property (1–22) is a single jqwik `@Property` with `tries >= 100`, tagged
  `Feature: payment-attendance-rules, Property {N}: {property text}`.
- Property placement is close to the component it validates to catch errors early.
- Conventions preserved: `persistance` not renamed, `MappingContext` mapping, thin
  controllers, multipart upload with optional photo, French comments/messages.
- Build & test through `./build.sh` (JDK 21).
