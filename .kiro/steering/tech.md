# Tech Stack

## Backend (`back/`)

| Concern | Technology |
|---------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3.4.1 |
| Build | Maven (wrapper: `./mvnw`) |
| Database | PostgreSQL (H2 for tests) |
| ORM | Spring Data JPA / Hibernate |
| Mapping | MapStruct 1.5.5 + ModelMapper 3.2.0 |
| Validation | Jakarta Validation + Hibernate Validator |
| Security | Spring Security (currently minimal config) |
| API Docs | SpringDoc OpenAPI (Swagger UI at `/swagger-ui.html`) |
| PDF | Apache PDFBox 2.0.24 |
| Utilities | Lombok, Commons Lang 3 |
| Testing | JUnit 5, Mockito, Spring Boot Test |

## Frontend (`front/`)

| Concern | Technology |
|---------|-----------|
| Framework | Angular 17.3 |
| Language | TypeScript 5.4 |
| UI Library | Angular Material 17 + Angular CDK |
| Styling | SCSS with shared partials (`src/styles/`) |
| Calendar | FullCalendar 6.x |
| i18n | ngx-translate |
| PDF Client | pdfmake, jspdf, html2canvas |
| Animation | GSAP, Lottie (ngx-lottie) |
| Date | Luxon, Moment |
| State | RxJS (service-based, no NgRx) |
| Linting | ESLint (Angular ESLint) |
| Testing | Karma + Jasmine |
| Build | Angular CLI / Vite (dev) |

## Common Commands

### Backend

```bash
# Build
cd back && ./mvnw clean package

# Run (dev profile)
cd back && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Run tests
cd back && ./mvnw test

# Skip tests on build
cd back && ./mvnw clean package -DskipTests
```

### Frontend

```bash
# Install dependencies
cd front && npm install

# Dev server (localhost:4200)
cd front && npm start

# Production build
cd front && npm run build

# Lint
cd front && npm run lint

# Unit tests
cd front && npm test
```

### Docker

```bash
cd back && docker-compose up
```

## Environment

- Backend listens on port 8080 by default
- Frontend proxies API calls to the backend
- PostgreSQL database: `schoolManagement4` (configurable via env vars)
- Image uploads stored at `./uploads/images` (configurable via `UPLOAD_DIR`)
