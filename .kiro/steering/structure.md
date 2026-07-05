# Project Structure

Monorepo with two top-level modules:

```
├── back/          # Spring Boot backend (Java/Maven)
├── front/         # Angular frontend (TypeScript/npm)
```

## Backend (`back/src/main/java/com/school/management/`)

```
├── SchoolManagementApplication.java   # Entry point
├── config/            # Spring configuration (Security, CORS, ModelMapper, ImageUrl)
├── controller/        # REST controllers (one per entity)
├── domain/
│   ├── model/         # Domain models
│   └── valueobject/   # Value objects
├── dto/               # Data Transfer Objects (request/response)
├── mapper/            # MapStruct/ModelMapper entity↔DTO mappers
├── persistance/       # JPA entities (note: typo "persistance" is the actual folder name)
├── repository/        # Spring Data JPA repositories
├── service/           # Business logic services
│   ├── exception/     # Custom exceptions
│   ├── interfaces/    # Service interfaces
│   ├── group/         # Group-specific services
│   ├── payment/       # Payment-specific services
│   ├── storage/       # File storage services
│   └── student/       # Student-specific services
├── shared/            # Shared exceptions and mappers
├── infrastructure/    # Infrastructure (storage config)
├── api/response/      # API response wrappers
└── util/              # Utilities (error handling, file validation)
```

### Architecture Pattern

Layered architecture: Controller → Service → Repository → Entity.  
DTOs separate the API contract from persistence. MapStruct handles mapping.

## Frontend (`front/src/`)

```
├── app/
│   ├── components/    # Feature components (one folder per domain entity)
│   │   ├── student/       # student-form, student-search, student-profile
│   │   ├── teacher/       # teacher-form, teacher-search, teacher-profile
│   │   ├── group/         # group-form, group-search, group-profile
│   │   ├── session/       # session-form, calendar
│   │   ├── payment/       # Payment components
│   │   ├── admin/         # Admin-specific views
│   │   ├── attendance/    # Attendance tracking
│   │   ├── navigation/    # Nav bar
│   │   ├── side-menu/     # Side menu
│   │   ├── shared/        # Shared UI components
│   │   └── ...            # level, room, subject, pricing, groupType, serie, etc.
│   ├── models/        # TypeScript interfaces/models
│   ├── services/      # Angular services (HTTP calls)
│   ├── pipes/         # Custom pipes
│   ├── shared/        # Shared modules/utilities
│   ├── utils/         # Helper functions
│   ├── app.module.ts  # Root NgModule
│   ├── app.routes.ts  # Route definitions (NgModule-based routing)
│   └── app.config.ts  # App configuration
├── assets/
│   └── i18n/          # Translation JSON files
├── environments/      # Environment configs (dev/prod)
└── styles/            # Global SCSS partials (_variables, _mixins, _forms, etc.)
```

### Frontend Conventions

- Each domain entity has its own folder under `components/` with sub-components (form, search/table, profile)
- Services follow a one-service-per-entity pattern in `services/`
- Uses NgModule-based architecture (not standalone components)
- Routes defined in `app.routes.ts` using `RouterModule.forRoot()`
- SCSS partials imported into `styles.scss` for global theming
