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

## Don't
- Don't rename the `persistance` folder (intentional, used everywhere).
- Don't translate existing French comments/messages.