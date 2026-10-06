# Coding Conventions

## Backend
- Upload endpoints use multipart/form-data, never JSON.
- Photo upload is ALWAYS optional: @RequestParam(required = false) + null-check.
- Date fields parsed from requests need explicit @DateTimeFormat(pattern = "yyyy-MM-dd").
- DTO ↔ Entity mapping goes through MappingContext, NOT ApplicationContextProvider.
- File handling goes through FileManagementService (it does automatic rollback on failure).
- Validate filenames with FileValidationUtil (path-traversal protection) before any file access.
- Controllers stay thin: business logic lives in services, split by responsibility.

## Frontend
- One service per entity, HTTP calls only.
- Centralized HTTP error handling (see payment.service.ts handleError pattern).
- User guide (`front/src/assets/guides/{fr,en,ar}/`, shown at `/guide`): a change visible on screen
  (label, button, flow, rule) updates the matching guide page in the same PR, in all three languages.
  Pages are separated by `<!-- page -->` and must fit the page format (`user-guide-content.spec.ts`).

## Don't
- Don't rename the `persistance` folder (intentional, used everywhere).
- Don't translate existing French comments/messages.

## Java version policy
- Target the current Java LTS (Java 25). Use modern language features available up to that version.
- Prefer LTS releases over the absolute latest non-LTS release for stability.
- When upgrading, verify the Spring Boot version officially supports the target Java version before bumping.