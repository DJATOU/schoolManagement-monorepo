# Requirements Document

## Introduction

This feature introduces the notion of **année scolaire** (school year) into the School
Management System (Spring Boot / Java 21 backend, Angular 17 frontend). Today the data model
has no temporal dimension: a Student has a single current `level`, belongs to Groups through
the `student_groups` join table, and Groups carry Series, Sessions, payments, and attendance
with no notion of which year they belong to. As a result, when a new academic year begins
(for example 2026-2027, where students who were in 2ème move up to 3ème), there is no clean
way to move students up while preserving the historical record of what happened in previous
years.

The design direction has already been decided with the product owner (referred to below as
the Option A approach):

- Introduce a `SchoolYear` reference entity (a label such as "2025-2026", a start date, an end
  date, and a current-year flag), with exactly one current School Year at a time.
- Attach the School Year to the **Group** (a `schoolYearId` on the Group). Series, Sessions,
  payments, and attendance inherit their School Year through their Group; no year column is
  duplicated across those entities.
- The Student's `level` continues to represent the **current** level. The historical level for
  a past year is derived from the Groups the Student belonged to during that year, using the
  existing `student_groups` enrollment link as the natural history.
- A **promotion / year-end workflow** (an assistant to "close the current year / open the next
  year") creates the next School Year, marks it current, and promotes Students to the next
  level. Redoublement (repeating the same level) is supported, and Students can be marked as
  leaving (archived / inactive).
- Past years' Groups, payments, and attendance remain **consultable in read-only**; history is
  viewable, not merely archived.
- Navigation is driven by a **global School Year selector** at the top of the application
  (a context filter), defaulting to the current School Year; lists (Groups, Sessions, payments)
  are filtered by the selected School Year, and the Student profile shows a per-year
  **parcours** (level plus Groups followed each year).
- All user-facing strings are translatable in French and English via ngx-translate.

This document captures those decisions as verifiable requirements and drives the design.

## Glossary

- **System**: The school management application as a whole (backend + frontend) unless a more
  specific component is named.
- **School_Year**: A new reference entity (`SchoolYearEntity`) representing an academic year.
  Carries a label, a start date, an end date, and a current-year flag.
- **School_Year_Label**: The human-readable identifier of a School_Year, in the format
  "YYYY-YYYY" where the second year is the first year plus one (for example "2025-2026").
- **Current_School_Year**: The single School_Year whose current-year flag is true; the default
  context for the application.
- **Group**: A class grouping (`GroupEntity`) with a type, subject, level, price, teacher,
  Students, and Series. Extended by this feature to reference a School_Year.
- **Level**: A reference entity (`LevelEntity`) such as 2ème or 3ème, ordered by a level
  sequence so that a "next level" can be determined.
- **Level_Sequence**: An ordering over Levels that determines, for a given Level, which Level
  is the next Level for promotion and whether a Level is the highest Level.
- **Highest_Level**: The Level that has no next Level in the Level_Sequence.
- **Student**: A person enrolled in the school (`StudentEntity`). Carries a current Level, a
  set of Groups, and an enrollment status.
- **Student_Status**: The enrollment state of a Student for the Current_School_Year, one of
  ACTIVE or INACTIVE (a Student marked as leaving is INACTIVE / archived).
- **Enrollment**: A `student_groups` link (`StudentGroupEntity`) between a Student and a Group,
  carrying an assignment date. Because a Group belongs to a School_Year, an Enrollment locates
  a Student in a Group for a specific School_Year.
- **Parcours**: The per-year history of a Student, listing for each School_Year the Student's
  Level for that year and the Groups the Student followed that year.
- **Promotion**: Moving a Student from the Student's current Level to the next Level in the
  Level_Sequence as part of the Year_End_Workflow.
- **Redoublement**: Keeping a Student at the same Level for the next School_Year instead of
  promoting the Student.
- **Departure**: Marking a Student as leaving, setting Student_Status to INACTIVE, so that the
  Student is not carried into the next School_Year.
- **Year_End_Workflow**: The assistant that closes the Current_School_Year and opens the next
  School_Year, creating the next School_Year, marking it current, and applying Promotion,
  Redoublement, or Departure decisions per Student.
- **Selected_School_Year**: The School_Year currently chosen in the global selector; the filter
  applied to Group, Session, and payment lists.
- **School_Year_Selector**: The global frontend control that lets a user choose the
  Selected_School_Year, defaulting to the Current_School_Year.
- **Read_Only_History**: The state of data belonging to a School_Year other than the
  Current_School_Year, which may be viewed but not modified.
- **Migration**: The one-time data operation that creates an initial School_Year and assigns
  all existing Groups (and, through them, their Series, Sessions, payments, and attendance) to
  that School_Year.
- **Administrator**: A user managing School Years, promotions, groups, and enrollments.

## Requirements

### Requirement 1: School Year entity and lifecycle

**User Story:** As an administrator, I want a School Year reference entity with a defined
lifecycle, so that every organizational and financial record can be situated in a specific
academic year.

#### Acceptance Criteria

1. THE System SHALL persist a School_Year with a School_Year_Label, a start date, an end date,
   and a current-year flag.
2. WHEN an administrator creates a School_Year, THE System SHALL require the School_Year_Label,
   the start date, and the end date.
3. IF a School_Year is submitted with a start date that is not before its end date, THEN THE
   System SHALL reject the School_Year with a validation error.
4. IF a School_Year is submitted with a School_Year_Label that already exists, THEN THE System
   SHALL reject the School_Year with a validation error.
5. THE System SHALL expose endpoints to create a School_Year, list all School Years, and
   retrieve a single School_Year.
6. WHEN School Years are listed, THE System SHALL order them by start date in descending order.

### Requirement 2: Exactly one current School Year

**User Story:** As an administrator, I want exactly one School Year to be current at any time,
so that the application always has an unambiguous default context.

#### Acceptance Criteria

1. THE System SHALL allow at most one School_Year to have the current-year flag set to true at
   any time.
2. WHEN an administrator marks a School_Year as current, THE System SHALL set the current-year
   flag of the previously Current_School_Year to false in the same operation.
3. WHEN the first School_Year is created, THE System SHALL mark that School_Year as the
   Current_School_Year.
4. IF an operation would leave no School_Year marked as current, THEN THE System SHALL reject
   the operation with a validation error.
5. WHEN the Current_School_Year is requested, THE System SHALL return the single School_Year
   whose current-year flag is true.

### Requirement 3: Attach School Year to Group

**User Story:** As an administrator, I want each Group to belong to a School Year, so that a
Group and everything derived from it is situated in one academic year.

#### Acceptance Criteria

1. THE System SHALL associate each Group with exactly one School_Year.
2. WHEN a Group is created, THE System SHALL assign the Group to the Current_School_Year by
   default.
3. WHERE an administrator specifies a School_Year when creating a Group, THE System SHALL
   assign the Group to the specified School_Year.
4. THE System SHALL derive the School_Year of a Series, a Session, a payment, and an attendance
   record from the School_Year of the associated Group.
5. THE System SHALL NOT store a School_Year reference directly on Series, Session, payment, or
   attendance records.

### Requirement 4: Current level and per-year historical level

**User Story:** As an administrator, I want the Student's level to reflect the current year
while past levels are derived from history, so that promotions do not erase what level a
Student held in earlier years.

#### Acceptance Criteria

1. THE System SHALL treat the Student Level as the Student's Level for the Current_School_Year.
2. WHEN the historical Level of a Student for a past School_Year is requested, THE System SHALL
   derive the Level from the Levels of the Groups the Student was enrolled in during that
   School_Year.
3. WHERE a Student was enrolled in Groups of a single Level during a School_Year, THE System
   SHALL report that Level as the Student's Level for that School_Year.
4. WHERE a Student was enrolled in Groups of more than one Level during a School_Year, THE
   System SHALL report all such Levels for that School_Year.
5. THE System SHALL determine the Groups a Student followed during a School_Year from the
   Student Enrollments whose Group belongs to that School_Year.

### Requirement 5: Year-end promotion workflow

**User Story:** As an administrator, I want an assistant to close the current year and open the
next year, so that I can move students up a level and start a fresh academic year in one guided
process.

#### Acceptance Criteria

1. WHEN an administrator starts the Year_End_Workflow, THE System SHALL create the next
   School_Year using the School_Year_Label that follows the Current_School_Year and mark the
   new School_Year as current.
2. WHEN the Year_End_Workflow creates the next School_Year, THE System SHALL set the previous
   Current_School_Year current-year flag to false.
3. WHEN the Year_End_Workflow is applied to a Student whose decision is Promotion, THE System
   SHALL set the Student Level to the next Level in the Level_Sequence.
4. WHEN the Year_End_Workflow is applied to a Student whose decision is Redoublement, THE
   System SHALL keep the Student Level unchanged.
5. WHEN the Year_End_Workflow is applied to a Student whose decision is Departure, THE System
   SHALL set the Student_Status to INACTIVE.
6. THE System SHALL preserve all Enrollments, Groups, Series, Sessions, payments, and
   attendance records of the previous School_Year unchanged when the Year_End_Workflow
   completes.
7. WHERE an administrator does not specify a decision for a Student in the Year_End_Workflow,
   THE System SHALL default that Student's decision to Promotion.

### Requirement 6: Redoublement support

**User Story:** As an administrator, I want to keep a student at the same level for the next
year, so that students who need to repeat a level are handled correctly.

#### Acceptance Criteria

1. THE System SHALL allow an administrator to mark a Student for Redoublement in the
   Year_End_Workflow.
2. WHEN a Student is marked for Redoublement, THE System SHALL keep the Student Level equal to
   the Student's Level before the Year_End_Workflow.
3. WHEN a Student is marked for Redoublement, THE System SHALL keep the Student_Status equal to
   ACTIVE.

### Requirement 7: Student departure

**User Story:** As an administrator, I want to mark a student as leaving, so that departed
students are not carried into the new year while their history is retained.

#### Acceptance Criteria

1. WHEN an administrator marks a Student as Departure, THE System SHALL set the Student_Status
   to INACTIVE.
2. THE System SHALL retain all Enrollments, payments, and attendance records of a Student whose
   Student_Status is INACTIVE.
3. WHEN Students are listed for the Current_School_Year, THE System SHALL exclude Students whose
   Student_Status is INACTIVE by default.
4. WHERE an administrator requests inactive Students, THE System SHALL include Students whose
   Student_Status is INACTIVE in the result.
5. WHERE an administrator reactivates a Student whose Student_Status is INACTIVE, THE System
   SHALL set the Student_Status to ACTIVE.

### Requirement 8: Highest-level promotion edge case

**User Story:** As an administrator, I want the workflow to handle students already at the
highest level, so that promotion never produces an invalid level.

#### Acceptance Criteria

1. IF the Year_End_Workflow attempts to promote a Student whose current Level is the
   Highest_Level, THEN THE System SHALL keep the Student Level unchanged and flag the Student
   for administrator review.
2. WHEN a Student at the Highest_Level is flagged for administrator review, THE System SHALL
   allow the administrator to choose Redoublement or Departure for that Student.
3. THE System SHALL NOT set a Student Level to a value that does not exist in the
   Level_Sequence.

### Requirement 9: Read-only consultation of past years

**User Story:** As an administrator, I want to consult past years' groups, payments, and
attendance without changing them, so that historical records stay intact and auditable.

#### Acceptance Criteria

1. WHEN the Selected_School_Year is a School_Year other than the Current_School_Year, THE
   System SHALL present that School_Year's Groups, Sessions, payments, and attendance as
   Read_Only_History.
2. IF a create, update, or delete operation targets data belonging to a School_Year other than
   the Current_School_Year, THEN THE System SHALL reject the operation with an error message.
3. THE System SHALL allow read operations on data belonging to any School_Year regardless of
   which School_Year is current.
4. WHEN Read_Only_History is displayed in the frontend, THE System SHALL disable editing
   controls for that data.

### Requirement 10: Global School Year selector and filtering

**User Story:** As a user, I want a global school-year selector at the top of the application,
so that I can choose which year's data I am viewing and default to the current year.

#### Acceptance Criteria

1. THE System SHALL display a School_Year_Selector accessible from the top of the application.
2. WHEN the application loads and no School_Year has been chosen, THE System SHALL set the
   Selected_School_Year to the Current_School_Year.
3. WHEN a user chooses a School_Year in the School_Year_Selector, THE System SHALL set the
   Selected_School_Year to the chosen School_Year.
4. WHEN the Selected_School_Year changes, THE System SHALL filter the Group list, the Session
   list, and the payment list to records belonging to the Selected_School_Year.
5. THE System SHALL expose an endpoint that returns Groups filtered by a specified School_Year.
6. THE System SHALL preserve the Selected_School_Year across navigation within a single
   application session.

### Requirement 11: Student parcours view

**User Story:** As an administrator, I want the student profile to show a per-year parcours, so
that I can see each year's level and the groups the student followed.

#### Acceptance Criteria

1. WHEN a Student profile is displayed, THE System SHALL show a Parcours listing each
   School_Year in which the Student was enrolled.
2. WHEN the Parcours is displayed, THE System SHALL show, for each School_Year, the Student's
   Level for that School_Year and the Groups the Student followed that School_Year.
3. THE System SHALL order the Parcours entries by School_Year start date in descending order.
4. WHERE a Student has no Enrollment in a School_Year, THE System SHALL omit that School_Year
   from the Parcours.
5. THE System SHALL expose an endpoint that returns the Parcours for a specified Student.

### Requirement 12: Migration of existing data

**User Story:** As an administrator, I want existing data assigned to an initial school year
when the feature is deployed, so that current groups, payments, and attendance remain valid
under the new model.

#### Acceptance Criteria

1. WHEN the Migration runs, THE System SHALL create an initial School_Year and mark it as the
   Current_School_Year.
2. WHEN the Migration runs, THE System SHALL assign every existing Group to the initial
   School_Year.
3. THE System SHALL leave existing Series, Sessions, payments, and attendance records reachable
   through their Group's initial School_Year without adding a direct School_Year reference to
   those records.
4. THE System SHALL set every existing Student's Student_Status to ACTIVE during the Migration.
5. WHEN the Migration completes, THE System SHALL ensure no Group is left without a School_Year.

### Requirement 13: Absence of a current School Year

**User Story:** As a user, I want the application to behave predictably when no current school
year is defined, so that the system guides me to set one instead of failing silently.

#### Acceptance Criteria

1. IF no School_Year has the current-year flag set to true, THEN THE System SHALL report that
   no Current_School_Year is defined.
2. WHILE no Current_School_Year is defined, THE System SHALL prompt the administrator to create
   or designate a Current_School_Year.
3. WHILE no Current_School_Year is defined, THE System SHALL prevent the creation of a Group
   until a Current_School_Year is designated.

### Requirement 14: Student with no group in a year

**User Story:** As an administrator, I want the system to handle a student who followed no group
in a given year, so that the parcours and level derivation stay correct.

#### Acceptance Criteria

1. WHERE a Student has no Enrollment in a School_Year, THE System SHALL report no historical
   Level for that Student for that School_Year.
2. WHERE a Student has no Enrollment in a School_Year, THE System SHALL omit that School_Year
   from the Student's Parcours.
3. WHEN the Year_End_Workflow is applied to a Student who has no Enrollment in the
   Current_School_Year, THE System SHALL apply the Student's promotion decision to the Student
   Level without requiring an Enrollment.

### Requirement 15: Internationalization (French and English)

**User Story:** As a user, I want every school-year interface element available in French and
English, so that I can use the feature in either language.

#### Acceptance Criteria

1. THE System SHALL provide a French translation and an English translation for every
   user-facing string introduced by this feature.
2. THE System SHALL render school-year interface elements using ngx-translate translation keys
   rather than hardcoded text.
3. WHEN the active language is changed, THE System SHALL display school-year interface elements
   in the newly selected language.
4. THE System SHALL define each new translation key in both the French translation file and the
   English translation file.
