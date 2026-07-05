# Backend Remediation Plan

Sequenced fix list derived from the read-only backend audit. Tasks are ordered by **dependency and risk**, not by severity number. Work top to bottom.

## Rules for Kiro (read before starting)

- Do **one task at a time**, in order. Mark `[x]` when done.
- After every behavioral change, run `cd back && ./mvnw test`. The build must be **green** before moving to the next task.
- Do **not** start a new phase until I have reviewed the previous one.
- Before editing, briefly confirm each audit finding is real by reading the actual code — do not assume the report is correct.
- Phases marked **[SEPARATE SPEC]** are high-risk: do not code them from this checklist. Create a proper spec (requirements → design → my approval) first.
- Keep changes scoped: one logical change per commit. Don't mix unrelated fixes.
- Respect existing conventions (steering docs): don't rename `persistance`, don't translate French domain comments.

---

## Phase 0 — Test safety net

- [ ] 0.1 Confirm the test infrastructure runs (JUnit 5, Mockito, H2). Add one trivial passing test and run `./mvnw test` to verify the pipeline works.

## Phase 1 — Unify payment cost & status (foundation — do first)

Fixes **H5**. Everything else about payments depends on this being correct.

- [ ] 1.1 Compare the three "expected cost" computations: `processPayment` (`attendedSessionsCost`), `recalculatePayment` (`getTotalSessions`), and `PaymentCrudService.calculateTotalSeriesCost` (`getSessions().size()`). Document how they diverge and what the **correct intended formula** should be. **STOP and confirm the formula with me before writing code.**
- [ ] 1.2 Create a single `PaymentCostCalculator` (and status resolver) as the one source of truth.
- [ ] 1.3 Route all three code paths through it; delete the duplicated logic.
- [ ] 1.4 Unit tests for the unified calculator: fully paid, partial, overpaid, absent session, catch-up session.

## Phase 2 — Money precision: Double → BigDecimal  **[SEPARATE SPEC]**

Fixes **H4**. Touches entities, arithmetic, and DB schema — must be a spec.

- [ ] 2.1 Convert monetary fields (`PaymentEntity.amountPaid`, `pricePerSession`, distribution math) from `Double` to `BigDecimal`.
- [ ] 2.2 Update all arithmetic and comparisons; define an explicit scale + rounding policy.
- [ ] 2.3 DB migration for the affected column types.
- [ ] 2.4 Update mappers (MapStruct / ModelMapper) for the new type.
- [ ] 2.5 Tests: rounding, summation, `totalPaid >= expectedAmount` comparisons.

## Phase 3 — Error handling & consistency (low risk, high value)

- [ ] 3.1 **H6** — In `AttendanceController` (`submitAttendance`, `createAttendance`) replace `System.out.println` / `e.printStackTrace()` with the SLF4J logger; let `GlobalExceptionHandler` handle errors instead of returning raw `e.getMessage()`.
- [ ] 3.2 **H7 / L4** — Replace generic `throw new RuntimeException(...)` with `ResourceNotFoundException` / `CustomServiceException` in `PaymentDetailAdminService` and `AttendanceService`.
- [ ] 3.3 **M12** — Remove `PaymentController`'s local `@ExceptionHandler(CustomServiceException)`; rely on `GlobalExceptionHandler` so the API error contract is consistent.
- [ ] 3.4 **H8** — In `PaymentDistributionService.distributePayment`, replace the "throw `CustomServiceException(..., HttpStatus.OK)` for success" control flow with a returned result object. Behavioral change — add tests covering the surplus/refund path.

## Phase 4 — Thin controllers & file handling

- [ ] 4.1 **M5** — Move photo/upload orchestration out of `StudentController` (831 lines) into a dedicated service; controller delegates only.
- [ ] 4.2 **M6** — Centralize image serving + `getMediaTypeForFileName` / `getFileExtension` in one component; remove the duplication between `StudentController` and `ImageController`.
- [ ] 4.3 **M7** — Use `FileValidationUtil.isSafeFilename` instead of inline path-traversal checks; resolve the real media type instead of hardcoding `IMAGE_JPEG`.
- [ ] 4.4 **M1** — Restrict `PatchService` mass-assignment: whitelist patchable fields per entity (or use dedicated patch DTOs) so `id` / `active` / relations can't be overwritten.
- [ ] 4.5 **M11** — Wire a real `MappingContext` in `PaymentController.createPayment` (currently passes `null` with TODOs); finish or remove the endpoint.
- [ ] 4.6 **M14** — Fix the `/api/v1/aith/**` typo (→ `auth`).
- [ ] 4.7 Tests for the new file/photo service.

## Phase 5 — Modernize dates & types

- [ ] 5.1 **M8** — Replace `SimpleDateFormat`/`Date` in `SessionService.updateSessionTime` with `DateTimeFormatter` + `LocalDateTime`; parse at the boundary via `@DateTimeFormat`.
- [ ] 5.2 **M9** — Migrate remaining `java.util.Date` + `@Temporal` fields to `LocalDate` / `LocalDateTime` (align with `BaseEntity`). DB migration where needed.
- [ ] 5.3 **M10** — Fix `BaseEntity.isActive()` nullable-`Boolean` auto-unboxing NPE risk; standardize active-checks.
- [ ] 5.4 **M3** — Introduce a `PaymentStatus` enum to replace the repeated `"CANCELLED"`/`"PENDING"`/`"COMPLETED"`/`"IN_PROGRESS"` strings. ⚠ Currently persisted as `String` — migrate carefully (`@Enumerated(STRING)` or a converter). Depends on Phase 1.
- [ ] 5.5 **M4** — Refactor `PaymentStatusService.getSessionPaymentStatuses`: extract helpers, fix the N+1 query (batch-fetch attendances/details), remove the dead `isPaidEvenIfAbsent` branch.
- [ ] 5.6 Update/extend tests after each change.

## Phase 6 — Broaden test coverage (rest of H1)

- [ ] 6.1 `PaymentDistributionService`, `PaymentProcessingService`, `PaymentDetailAdminService.recalculatePayment`.
- [ ] 6.2 `AttendanceService` edge cases.
- [ ] 6.3 `StudentPayableGroupsService` (fixed vs catch-up group logic).

## Phase 7 — Security  **[SEPARATE SPEC — only before deployment]**

Do not half-implement security. Decide the auth model with stakeholders first, then spec it.

- [ ] 7.1 **H2** — Require authentication on `/api/**`; protect admin / payment-detail mutation routes (currently `permitAll()`).
- [ ] 7.2 **H3** — Derive admin identity from the security context, not the spoofable `X-Admin-Name` header.
- [ ] 7.3 **M2** — Populate `createdBy` / audit user from `AuditorAware` / security context instead of hardcoded `"admin"`.

## Phase 8 — Cosmetic cleanup (batch last)

Low impact — do as one quick pass at the very end.

- [ ] 8.1 **L1 / L2** — Remove or properly implement the no-op `updateAttendance` / `updatePayment` methods.
- [ ] 8.2 **L3** — Strip process/changelog comments (`@author Claude Code`, "Phase X Refactoring", `// ← AJOUTÉ`); keep meaningful French domain comments.
- [ ] 8.3 **L5** — Replace `status(500)` literal with `HttpStatus.INTERNAL_SERVER_ERROR`.
- [ ] 8.4 **L6** — Replace wildcard imports with explicit ones.
- [ ] 8.5 **L7** — Standardize stream terminal ops on `.toList()`.
- [ ] 8.6 **L8** — Standardize on constructor injection without `@Autowired`.
- [ ] 8.7 **L9** — (Optional) add magic-byte content sniffing in `FileValidationUtil` for defense-in-depth.

---

## Deferred / verify-before-acting

- Modern-Java records for DTOs: only convert **construction-only** DTOs; ModelMapper / `PatchService` rely on mutable setters and will break otherwise. Verify each mapper before changing a DTO to a record.
