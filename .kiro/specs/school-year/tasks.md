# Implementation Plan: Année Scolaire (School Year)

## Overview

Test-driven implementation of the **année scolaire** temporal dimension (Option A): a new
`SchoolYearEntity` in `persistance`, `schoolYearId` on `GroupEntity`, `levelSequence` on
`LevelEntity`, `status` (ACTIVE/INACTIVE) on `StudentEntity`; pure logic
(`LevelSequenceService`, `PromotionCalculator`, `deriveNextLabel`); orchestration services
(`SchoolYearService`, `CurrentSchoolYearService`, `ReadOnlyYearGuard`, `YearEndWorkflowService`,
`StudentParcoursService`); a `SchoolYearMigrationRunner`; DTOs/mappers via `MappingContext`;
thin REST controllers; and the Angular frontend (context service, global selector, year-end
workflow, parcours panel, read-only rendering, FR+EN i18n).

Ordering is bottom-up and incremental so correctness-critical logic is validated early:
build/coverage tooling reuse → entities & enums → repositories → pure logic with property tests
→ services with property tests → migration runner → DTOs/mappers → REST controllers → frontend
→ coverage verification. Each step builds on the previous one and ends wired into the app; no
orphaned code.

Language/stack: Java 21, Spring Boot 3.4.1, JPA/Hibernate, MapStruct + `MappingContext`,
JUnit 5 + Mockito, jqwik for property-based tests, JaCoCo for coverage; Angular 17.3
(NgModule-based) with Karma + Jasmine and ngx-translate (FR+EN). Backend build & test via
`back/build.sh` (e.g. `./build.sh clean test`); frontend via `npm test`.

Conventions honored: the `persistance` folder is NOT renamed; new entities live there.
DTO↔Entity mapping goes through `MappingContext` (never `ApplicationContextProvider`).
Controllers stay thin. French comments/messages are preserved. Frontend is one-service-per-entity,
NgModule-based. Multipart/optional-photo upload behavior is unaffected.

### Testing policy

- **Property-based tests** (jqwik): exactly one property-based test per design property
  (Properties 1–14), each `@Property(tries = 100)` minimum, tagged with the comment
  `Feature: school-year, Property {N}: {property text}`. All property tasks are **MANDATORY**.
- **Example-based unit tests** (JUnit 5 + Mockito): nominal, boundary, and error/validation cases.
- **Integration tests** (Spring Boot Test, H2): repository queries, endpoint wiring, migration runner.
- **Frontend tests** (Karma + Jasmine): selector defaulting/selection/session preservation,
  read-only disabling, and i18n key parity between `fr.json` and `en.json`.
- Coverage (JaCoCo) on new/modified backend business packages is verified in a final task.
- Property and coverage tasks are mandatory; genuinely redundant/optional sub-tasks are marked `*`.

## Tasks

- [x] 1. Reuse and verify test & coverage tooling in the build
  - Confirm the jqwik dependency (test scope) and JUnit 5 platform engine are present in
    `back/pom.xml`; add them if missing so `@Property` tests run.
  - Confirm the JaCoCo Maven plugin (`prepare-agent` + `report`) is configured; ensure the new
    school-year business packages (`service`, `service.exception`, pure calculator/level-sequence
    helpers, migration) are within its scope.
  - Verify the toolchain compiles and runs a trivial jqwik `@Property` via
    `./build.sh clean test` (run from `back/`).
  - _Requirements: 15.1 (tooling only — no user-facing behavior)_

- [x] 2. Create enums and JPA entity changes in `persistance`
  - [x] 2.1 Add `StudentStatus` and `PromotionDecision` enums
    - `StudentStatus { ACTIVE, INACTIVE }` and `PromotionDecision { PROMOTION, REDOUBLEMENT, DEPARTURE }`.
    - _Requirements: 7.1, 5.3, 5.4, 5.5_

  - [x] 2.2 Create `SchoolYearEntity` (extends `BaseEntity`) in `persistance`
    - Fields: `label` (unique, not null), `startDate` (DATE), `endDate` (DATE),
      `isCurrent` (Boolean, default false); `@Table(name = "school_year")` with unique
      constraint `uk_school_year_label` on `label`.
    - _Requirements: 1.1, 1.4, 2.1_

  - [x] 2.3 Modify `GroupEntity` to reference a School_Year
    - Add `@ManyToOne(fetch = LAZY) @JoinColumn(name = "school_year_id") SchoolYearEntity schoolYear`.
    - _Requirements: 3.1_

  - [x] 2.4 Modify `LevelEntity` to add `levelSequence`
    - Add `Integer levelSequence` (`@Column(name = "level_sequence")`) as the ordering/rank.
    - _Requirements: 8.3_

  - [x] 2.5 Modify `StudentEntity` to add `status`
    - Add `@Enumerated(EnumType.STRING) StudentStatus status` defaulting to `ACTIVE`.
    - _Requirements: 7.1_

  - [x] 2.6 Write persistence unit/integration tests for entity defaults & constraints
    - Assert `isCurrent` defaults false; `status` defaults ACTIVE; duplicate `label` insert is
      rejected by the unique constraint; `schoolYear` on Group is nullable at column level.
    - _Requirements: 1.4, 2.1, 7.1_

- [x] 3. Create and extend repositories
  - [x] 3.1 Create `SchoolYearRepository`
    - `findAllByOrderByStartDateDesc()`, `findByIsCurrentTrue()`, `findByLabel(String)`, `count()`.
    - _Requirements: 1.6, 2.5, 13.1, 1.4_

  - [x] 3.2 Extend `GroupRepository`
    - Add `findBySchoolYearId(Long)` and `findBySchoolYearIsNull()`.
    - _Requirements: 10.5, 12.2_

  - [x] 3.3 Extend `StudentRepository`
    - Add `findByStatus(StudentStatus)` for active/inactive listing.
    - _Requirements: 7.3, 7.4_

  - [x] 3.4 Extend `LevelRepository`
    - Add `findAllByOrderByLevelSequenceAsc()`.
    - _Requirements: 8.1, 8.3_

  - [x] 3.5 Write repository integration tests (Spring Boot Test, H2)
    - Verify ordering-by-start-date-desc, `findByIsCurrentTrue`, `findByLabel`,
      `findBySchoolYearIsNull`, and level ordering against seeded H2 data.
    - _Requirements: 1.6, 2.5, 12.2, 8.3_

- [x] 4. Implement pure logic: level sequence
  - [x] 4.1 Implement `LevelSequenceService` (pure ordering)
    - `orderedBySequence()` (sort asc by `levelSequence`), `nextLevel(current, ordered)`
      (smallest sequence strictly greater than current, else empty), `isHighest(level, ordered)`
      (true iff `nextLevel` empty). Ordering logic is pure over the supplied list; repository
      only loads. Throw `IllegalArgumentException` on malformed input.
    - _Requirements: 8.1, 8.3_

  - [x] 4.2 Write property test for level-sequence ordering
    - **Property 2: Level sequence next-level and highest-level**
    - Tag: `Feature: school-year, Property 2: For any set of Levels with distinct levelSequence values, nextLevel(L) returns the Level with the smallest levelSequence strictly greater than L's when one exists and empty otherwise, and isHighest(L) is true iff nextLevel(L) is empty.`
    - `@Property(tries = 100)`; generators include single-level systems and lowest/highest levels.
    - **Validates: Requirements 8.1**

- [x] 5. Implement pure logic: promotion decision
  - [x] 5.1 Implement `PromotionCalculator` (pure) and `PromotionOutcome`
    - `PromotionOutcome(Long targetLevelId, StudentStatus status, boolean needsReview)`.
    - `decide(currentLevelId, Optional<Long> nextLevelId, PromotionDecision)`: PROMOTION+next →
      next/ACTIVE/false; PROMOTION+no-next → current/ACTIVE/true; REDOUBLEMENT → current/ACTIVE;
      DEPARTURE → current/INACTIVE. `targetLevelId` is always current or next (never fabricated).
    - _Requirements: 5.3, 5.4, 5.5, 6.2, 6.3, 7.1, 8.1, 8.3_

  - [x] 5.2 Write property test for promotion decision correctness
    - **Property 3: Promotion decision correctness**
    - Tag: `Feature: school-year, Property 3: For any Student current Level and any decision, decide yields next/ACTIVE for PROMOTION with next; unchanged/ACTIVE/needsReview for PROMOTION at highest; unchanged/ACTIVE for REDOUBLEMENT; unchanged/INACTIVE for DEPARTURE; defaulting to PROMOTION; outcome independent of enrollment.`
    - `@Property(tries = 100)`.
    - **Validates: Requirements 5.3, 5.4, 5.5, 5.7, 6.2, 6.3, 7.1, 8.1, 14.3**

  - [x] 5.3 Write property test for promotion never producing a non-existent Level
    - **Property 4: Promotion never produces a non-existent Level**
    - Tag: `Feature: school-year, Property 4: For any current Level, next-Level option, and decision, the targetLevelId returned by decide is always one of the supplied Level ids (current or next); never a Level absent from the Level_Sequence.`
    - `@Property(tries = 100)`.
    - **Validates: Requirements 8.3**

- [x] 6. Implement pure logic: next-year label derivation
  - [x] 6.1 Implement `deriveNextLabel` (pure)
    - Place as a pure static helper (used by `YearEndWorkflowService`): for "YYYY-(YYYY+1)"
      return "(YYYY+1)-(YYYY+2)"; reject malformed labels with `IllegalArgumentException`.
    - _Requirements: 5.1_

  - [x] 6.2 Write property test for next-year label derivation
    - **Property 5: Next-year label derivation**
    - Tag: `Feature: school-year, Property 5: For any School_Year_Label of the form YYYY-(YYYY+1), deriveNextLabel returns (YYYY+1)-(YYYY+2); both years incremented by one and the second always equals the first plus one.`
    - `@Property(tries = 100)`.
    - **Validates: Requirements 5.1**

- [x] 7. Checkpoint - Ensure all tests pass
  - Run `./build.sh clean test` from `back/`. Ensure all tests pass, ask the user if questions arise.

- [x] 8. Implement `SchoolYearService` + validation
  - [x] 8.1 Implement `SchoolYearService.create` / `findAll` / `findById`
    - Validate label present, start/end present, `startDate < endDate` (throw domain validation
      exception from `service/exception`, French message), unique label; if first year, mark
      current. `findAll` ordered by start date desc.
    - _Requirements: 1.2, 1.3, 1.4, 1.5, 1.6, 2.3_

  - [x] 8.2 Write unit tests for `SchoolYearService` validation & first-year-current
    - Missing-field rejection (1.2), duplicate-label rejection (1.4), first year becomes current (2.3).
    - _Requirements: 1.2, 1.4, 2.3_

  - [x] 8.3 Write property test for School Year listing order
    - **Property 11: School Year listing order**
    - Tag: `Feature: school-year, Property 11: For any set of School Years, listing them returns a permutation of the input ordered by start date descending.`
    - `@Property(tries = 100)`; generators include empty sets and equal dates.
    - **Validates: Requirements 1.6**

  - [x] 8.4 Write property test for date range validation
    - **Property 12: Date range validation**
    - Tag: `Feature: school-year, Property 12: For any pair of start and end dates, creation of a School Year succeeds only when start is strictly before end and is rejected otherwise.`
    - `@Property(tries = 100)`; generators include equal start/end dates.
    - **Validates: Requirements 1.3**

- [x] 9. Implement `CurrentSchoolYearService` and single-current invariant
  - [x] 9.1 Implement `CurrentSchoolYearService`
    - `findCurrent()` (2.5, 13.1), `requireCurrent()` throwing `NoCurrentSchoolYearException`
      (13.1), `makeCurrent(target)` flipping the previous current flag to false and the target to
      true atomically in one transaction. Reject operations that would leave no current year.
    - _Requirements: 2.1, 2.2, 2.4, 2.5, 13.1_

  - [x] 9.2 Write unit tests for current lookup and reject-no-current
    - Current lookup (2.5), reject leaving no current (2.4).
    - _Requirements: 2.4, 2.5_

  - [x] 9.3 Write property test for the single-current-year invariant
    - **Property 1: Single current School Year invariant**
    - Tag: `Feature: school-year, Property 1: For any sequence of School Year creations and set-current / year-end operations, at most one School Year is current at any point, and after any successful set-current/year-end the target is the only current year while the previous is no longer current.`
    - `@Property(tries = 100)`; mock repository, drive random operation sequences.
    - **Validates: Requirements 2.1, 2.2, 5.2**

- [x] 10. Implement `ReadOnlyYearGuard`
  - [x] 10.1 Implement `ReadOnlyYearGuard`
    - `assertMutable(SchoolYearEntity)` throwing `ReadOnlySchoolYearException` (French message,
      HTTP 409) when the year is not current; `assertGroupMutable`, `assertSeriesMutable`,
      `assertSessionMutable` resolving the year by walking to the Group. Read operations never
      consult the guard.
    - _Requirements: 9.2, 9.3_

  - [x] 10.2 Write property test for read-only past-year rejection
    - **Property 7: Read-only past years reject mutations**
    - Tag: `Feature: school-year, Property 7: For any Group/Session/payment/attendance whose resolved School Year is not current, create/update/delete is rejected; when current, the operation is permitted by the guard.`
    - `@Property(tries = 100)`; mock `CurrentSchoolYearService`.
    - **Validates: Requirements 9.2**

- [x] 11. Implement `StudentParcoursService`
  - [x] 11.1 Implement `StudentParcoursService.getParcours(studentId)`
    - Load active enrollments, group by `enrollment.group.schoolYear`, compute distinct Levels of
      the enrolled Groups per year, omit years with no enrollment, order entries by school-year
      start date descending.
    - _Requirements: 4.2, 4.3, 4.4, 4.5, 11.1, 11.2, 11.3, 11.4, 14.1, 14.2_

  - [x] 11.2 Write property test for historical-level and parcours derivation
    - **Property 6: Historical level and parcours derivation**
    - Tag: `Feature: school-year, Property 6: For any set of Student enrollments across School Years, the parcours contains exactly the distinct years with at least one enrollment (others omitted, no historical level), ordered by start date descending; each year's reported Level set equals the distinct Levels of the enrolled Groups.`
    - `@Property(tries = 100)`; mock repositories, generate random enrollment graphs including
      students with no enrollment and years with no enrolled groups.
    - **Validates: Requirements 4.2, 4.3, 4.4, 4.5, 11.1, 11.2, 11.3, 11.4, 14.1, 14.2**

- [x] 12. Implement `YearEndWorkflowService`
  - [x] 12.1 Implement `YearEndWorkflowService.run` and `preview`
    - Single transaction: `requireCurrent()`, derive/validate next label, create next year,
      `makeCurrent(nextYear)`, iterate eligible active students defaulting decision to PROMOTION,
      compute next level via `LevelSequenceService`, apply `PromotionCalculator.decide`, set
      student level/status, collect highest-level review list, save; preserve all prior data.
      Apply the decision without requiring a current-year enrollment. `preview` returns proposed
      next label and default decisions with highest-level students flagged.
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 6.1, 6.2, 6.3, 7.1, 8.1, 8.2, 14.3_

  - [x] 12.2 Write unit tests for the year-end workflow orchestration
    - Previous year flag cleared (5.2), history preserved (5.6), highest-level student added to
      review list (8.1, 8.2), redoublement keeps level+ACTIVE (6.2, 6.3), departure sets INACTIVE
      (7.1), student without enrollment still promoted (14.3).
    - _Requirements: 5.2, 5.6, 6.2, 6.3, 7.1, 8.1, 8.2, 14.3_

- [x] 13. Implement Group creation year assignment and reactivation/status listing
  - [x] 13.1 Wire Group creation to assign the School_Year
    - On create, default to `currentSchoolYearService.requireCurrent()` (blocking creation when no
      current year, 13.3) or use the explicitly supplied year; consult `ReadOnlyYearGuard` on
      Group/Series/Session mutations.
    - _Requirements: 3.2, 3.3, 9.2, 13.3_

  - [x] 13.2 Implement student status listing and reactivation in the student service
    - Default current-year listing excludes INACTIVE; explicit request includes INACTIVE;
      reactivation sets ACTIVE.
    - _Requirements: 7.3, 7.4, 7.5_

  - [x] 13.3 Write property test for Group year assignment on creation
    - **Property 9: Group year assignment on creation**
    - Tag: `Feature: school-year, Property 9: For any Group created without an explicit year while a current year exists, its year equals the current year; for any Group created with an explicit year, its year equals the specified year.`
    - `@Property(tries = 100)`.
    - **Validates: Requirements 3.2, 3.3**

  - [x] 13.4 Write property test for child records inheriting their Group's year
    - **Property 10: Child records inherit their Group's year**
    - Tag: `Feature: school-year, Property 10: For any Group with a School Year and any Series/Session/payment/attendance reachable from it, the resolved School Year of that child equals the Group's School Year.`
    - `@Property(tries = 100)`.
    - **Validates: Requirements 3.4**

  - [x] 13.5 Write property test for student status listing filter
    - **Property 14: Student status listing filter**
    - Tag: `Feature: school-year, Property 14: For any set of Students with mixed statuses, the default listing returns exactly ACTIVE students, and the include-inactive listing additionally returns INACTIVE students.`
    - `@Property(tries = 100)`; generators include mixed status sets.
    - **Validates: Requirements 7.3, 7.4**

  - [x] 13.6 Write unit test for reactivation
    - Reactivating an INACTIVE student sets ACTIVE (7.5).
    - _Requirements: 7.5_

- [x] 14. Checkpoint - Ensure all tests pass
  - Run `./build.sh clean test` from `back/`. Ensure all tests pass, ask the user if questions arise.

- [x] 15. Implement `SchoolYearMigrationRunner`
  - [x] 15.1 Implement the idempotent migration runner
    - `ApplicationRunner`: if `schoolYearRepository.count() > 0` do nothing; otherwise create the
      initial current School_Year (label from a configurable property with a derived default),
      assign every Group with a null year to it, and set every Student with a null status to
      ACTIVE. Series/Session/payment/attendance untouched (reachable via Group).
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5_

  - [x] 15.2 Write property test for migration completeness
    - **Property 13: Migration completeness**
    - Tag: `Feature: school-year, Property 13: For any pre-migration state of Groups and Students, after migration there is a current School Year, no Group has a null School Year, and every Student has status ACTIVE.`
    - `@Property(tries = 100)`; H2 integration with generated pre-migration data including null
      group years and null student statuses.
    - **Validates: Requirements 12.2, 12.4, 12.5**

  - [x] 15.3 Write integration test for migration idempotency and no-direct-year invariant
    - Re-running the runner creates no duplicate year (12.1); Series/Session/payment/attendance
      carry no direct year column and resolve via Group (3.5, 12.3).
    - _Requirements: 12.1, 3.5, 12.3_

- [x] 16. Implement DTOs and mappers (via `MappingContext`)
  - [x] 16.1 Create School Year and parcours DTOs + mappers
    - `SchoolYearDTO` + `SchoolYearMapper`; `ParcoursDTO`, `ParcoursYearDTO`; date fields use
      `@DateTimeFormat(pattern = "yyyy-MM-dd")`. Mapping resolves entities through `MappingContext`.
    - _Requirements: 1.5, 11.5_

  - [x] 16.2 Extend Group/Student DTOs and mappers
    - `GroupDTO` gains `schoolYearId` (+ optional `schoolYearLabel`), resolved via `MappingContext`;
      `StudentDTO` gains `status`.
    - _Requirements: 3.1, 7.1_

  - [x] 16.3 Create year-end workflow DTOs
    - `YearEndRequestDTO { newLabel?, startDate?, endDate?, List<StudentDecisionDTO> decisions }`,
      `StudentDecisionDTO { studentId, decision }`,
      `YearEndResultDTO { newYear, reviewList, appliedCount }`.
    - _Requirements: 5.1, 5.3, 8.2_

  - [x] 16.4 Write unit tests for mappers
    - Round-trip Group↔DTO with `schoolYearId`; Student↔DTO with `status`; parcours assembly.
    - _Requirements: 3.1, 7.1, 11.5_

- [x] 17. Implement thin REST controllers
  - [x] 17.1 Create `SchoolYearController`
    - `POST /api/school-years`, `GET /api/school-years`, `GET /api/school-years/{id}`,
      `GET /api/school-years/current`, `PATCH /api/school-years/{id}/set-current`.
    - _Requirements: 1.5, 1.6, 2.1, 2.2, 2.4, 2.5, 13.1_

  - [x] 17.2 Create `YearEndWorkflowController`
    - `POST /api/year-end/run`, `GET /api/year-end/preview`; delegates to `YearEndWorkflowService`.
    - _Requirements: 5.1, 5.3, 8.2_

  - [x] 17.3 Extend `GroupController` with the year filter
    - `GET /api/groups?schoolYearId={id}` backed by `findBySchoolYearId`, defaulting to the
      current year when absent.
    - _Requirements: 10.4, 10.5_

  - [x] 17.4 Add the parcours endpoint to `StudentController`
    - `GET /api/students/{id}/parcours` delegating to `StudentParcoursService`; extend student
      listing to accept an include-inactive flag.
    - _Requirements: 11.5, 7.3, 7.4_

  - [x] 17.5 Write property test for group filtering by School Year
    - **Property 8: Group filtering by School Year**
    - Tag: `Feature: school-year, Property 8: For any set of Groups spread across School Years and any specified year, the filter-by-year query returns exactly the Groups whose schoolYear equals the specified year and no others.`
    - `@Property(tries = 100)`; H2 integration with generated groups across years.
    - **Validates: Requirements 10.4, 10.5**

  - [x] 17.6 Write integration tests for endpoint wiring (Spring Boot Test, H2)
    - School Year CRUD (1.5), set-current single-current enforcement (2.1, 2.2), current lookup
      (2.5), no-current-year response (13.1), parcours endpoint (11.5), read-only rejection on a
      past-year mutation returns 409 (9.2), read access to any year (9.3).
    - _Requirements: 1.5, 2.1, 2.2, 2.5, 9.2, 9.3, 11.5, 13.1_

- [x] 18. Checkpoint - Ensure all backend tests pass
  - Run `./build.sh clean test` from `back/`. Ensure all tests pass, ask the user if questions arise.

- [x] 19. Implement frontend: School Year context and service
  - [x] 19.1 Create `school-year.service.ts` (one service per entity)
    - HTTP calls only for the School Year endpoints; centralized `handleError` following the
      `payment.service.ts` pattern.
    - _Requirements: 1.5, 2.5_

  - [x] 19.2 Create `SchoolYearContextService`
    - Hold `Selected_School_Year` in a `BehaviorSubject`, initialize to the current year on load,
      update on selection, preserve across navigation within the session.
    - _Requirements: 10.2, 10.3, 10.6_

  - [x] 19.3 Write Karma/Jasmine tests for the context service
    - Defaults to current year (10.2), updates on selection (10.3), preserved across navigation (10.6).
    - _Requirements: 10.2, 10.3, 10.6_

- [x] 20. Implement frontend: selector, filtering, workflow, parcours, read-only
  - [x] 20.1 Create the `school-year-selector` component (in the navigation bar)
    - Global control listing School Years and switching the selected year via the context service.
    - _Requirements: 10.1, 10.3_

  - [x] 20.2 Extend `group.service.ts` (and session/payment lists) to filter by selected year
    - Pass the selected year to the group-list endpoint so Group/Session/payment lists reflect
      `Selected_School_Year`.
    - _Requirements: 10.4, 10.5_

  - [x] 20.3 Create the `year-end-workflow` component
    - Preview decisions per active student (default Promotion, highest-level flagged) and run the
      workflow via the service.
    - _Requirements: 5.1, 5.3, 5.7, 8.1, 8.2_

  - [x] 20.4 Add the parcours panel to the student profile
    - Call `/api/students/{id}/parcours` and render per-year level(s) + groups ordered desc.
    - _Requirements: 11.1, 11.2, 11.3, 11.5_

  - [x] 20.5 Implement read-only rendering for non-current years
    - When `Selected_School_Year` is not the current year, disable editing controls in
      group/session/payment views.
    - _Requirements: 9.4_

  - [x] 20.6 Write Karma/Jasmine tests for selector, filtering, and read-only disabling
    - Selector switches selected year (10.3), list filtering reacts to selection (10.4),
      controls disabled for read-only history (9.4).
    - _Requirements: 9.4, 10.3, 10.4_

- [x] 21. Implement i18n (FR + EN)
  - [x] 21.1 Add ngx-translate keys for every new string in `fr.json` and `en.json`
    - Selector, year-end workflow, parcours, read-only notices, and validation/error messages;
      no hardcoded text in the new components.
    - _Requirements: 15.1, 15.2, 15.3, 15.4_

  - [x] 21.2 Write a Karma/Jasmine test enforcing FR/EN key parity
    - Assert every new key exists in both `fr.json` and `en.json` with no missing counterpart.
    - _Requirements: 15.1, 15.4_

- [x] 22. Final checkpoint - Ensure all tests pass and verify coverage
  - Run `./build.sh clean test` from `back/` and `npm test` from `front/`.
  - Verify JaCoCo coverage on the new/modified backend business packages (services, pure
    calculator/level-sequence helpers, migration) meets the project threshold.
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Sub-tasks marked with `*` are optional/redundant and can be skipped for a faster path;
  property-based test tasks and the coverage task are MANDATORY and are never marked `*`.
- Each of the 14 design correctness properties is implemented by exactly one jqwik
  `@Property(tries = 100)` test, tagged `Feature: school-year, Property {N}: {property text}`,
  placed next to the component it validates.
- Every task references specific requirement clause numbers for traceability.
- Checkpoints run `./build.sh clean test` (backend) to validate incrementally.
- Conventions preserved: `persistance` not renamed, `MappingContext` mapping, thin controllers,
  French comments/messages, one-service-per-entity frontend, NgModule-based Angular, and
  unaffected multipart/optional-photo upload.
