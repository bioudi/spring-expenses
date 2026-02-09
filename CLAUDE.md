# CLAUDE.md — Spring Expenses Tracker

## Project Overview

Full-stack expense tracking application with a Spring Boot 3.2 backend (Java 21) and a React 19 + TypeScript frontend. Features AI-powered merchant categorization (Anthropic Claude), monthly email insights, and multi-user support with session-based auth.

## Tech Stack

| Layer       | Technology                                              |
|-------------|---------------------------------------------------------|
| Backend     | Java 21, Spring Boot 3.2.0, Spring Data JPA, Spring Security |
| Frontend    | React 19, TypeScript 5.9, Vite 7, TailwindCSS 3.4, Radix UI |
| Database    | PostgreSQL 15                                           |
| Build       | Maven 3.9.6 (backend + frontend via frontend-maven-plugin) |
| Deployment  | Docker (multi-stage Alpine), Railway.app                |

## Project Structure

```
src/main/java/com/expensetracker/
├── config/          # Spring configuration (Security, SPA, Jackson)
├── controller/      # REST controllers
├── dto/             # Request/response DTOs
├── entity/          # JPA entities (User, Expense, MerchantCategory)
├── exception/       # Custom exceptions + GlobalExceptionHandler
├── repository/      # Spring Data JPA repositories
├── security/        # ApiKeyFilter, SecurityUtils
├── service/         # Business logic
└── util/            # Utilities (FlexibleBigDecimalDeserializer)

frontend/src/
├── components/      # React components (layout, ui)
├── lib/             # API client, utilities
├── pages/           # Page components (Dashboard, Expenses, Merchants, Settings, Login, Register)
└── types/           # TypeScript type definitions
```

## Common Commands

```bash
# --- Backend ---
mvn clean package -DskipTests        # Build JAR (includes frontend)
mvn spring-boot:run                   # Run backend on :8080

# --- Frontend ---
cd frontend && npm install            # Install frontend deps
cd frontend && npm run dev            # Dev server on :5173 (proxies to :8080)
cd frontend && npm run build          # Build to src/main/resources/static/
cd frontend && npm run lint           # ESLint check

# --- Docker ---
docker compose up postgres -d         # Start Postgres only (local dev)
docker compose up --build             # Full stack (Postgres + app)
docker compose down -v                # Stop and wipe database volume
```

## Development Workflow

- **Backend runs on** `localhost:8080`, **frontend dev server on** `localhost:5173` (with proxy).
- Frontend builds output to `src/main/resources/static/` and are served by Spring Boot as a SPA.
- The Maven build automatically triggers the frontend build via `frontend-maven-plugin` (Node v20.11.0).

## Database

- **PostgreSQL 15** with Hibernate `ddl-auto=update` (no migration tool — schema is auto-managed).
- **Entities:** `User`, `Expense`, `MerchantCategory` — all use **UUID** primary keys.
- **Timezone:** `America/Montreal` (set in Hibernate config and Docker entrypoint).
- **Seed data:** `DataMigrationRunner` creates admin user `admin@expensetracker.local` / `changeme` on first run if no users exist.
- Local dev credentials: `expenseuser` / `expensepass` on `localhost:5432/expense_tracker`.

## Code Conventions

- **Lombok everywhere:** `@Getter`, `@Setter`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`.
- **Constructor injection** via `@RequiredArgsConstructor` (no `@Autowired`).
- **DTOs** for all request/response payloads — entities are never exposed directly.
- **BigDecimal** for monetary amounts (never float/double).
- **@Transactional(readOnly = true)** on read-only service methods.
- **Package-by-layer:** controller → service → repository.
- Frontend uses **functional components with hooks**, centralized API client in `lib/api.ts`.

## Key Environment Variables

| Variable                 | Purpose                        | Default                                         |
|--------------------------|--------------------------------|-------------------------------------------------|
| `SPRING_DATASOURCE_URL`  | PostgreSQL JDBC URL            | `jdbc:postgresql://localhost:5432/expense_tracker` |
| `SPRING_DATASOURCE_USERNAME` | DB username                | `expenseuser`                                   |
| `SPRING_DATASOURCE_PASSWORD` | DB password                | `expensepass`                                   |
| `ANTHROPIC_API_KEY`      | Claude API for categorization  | _(empty — feature disabled if missing)_         |
| `MAIL_USERNAME`          | Gmail address for SMTP         | _(empty)_                                       |
| `MAIL_PASSWORD`          | Gmail app-specific password    | _(empty)_                                       |
| `INSIGHTS_EMAIL_ENABLED` | Toggle monthly email insights  | `true`                                          |

## Architecture Notes

- **Auth:** Session-based form login + per-user API keys for webhook endpoints. BCrypt password hashing. No JWT.
- **AI categorization:** `CategorizationService` calls Claude Haiku to auto-categorize merchants. Results are cached per user in `MerchantCategory` table. Falls back to "Uncategorized" if API key is missing.
- **Monthly insights:** `MonthlyInsightsService` runs via `@Scheduled` cron to email spending summaries.
- **SPA routing:** `SpaWebConfig` forwards non-API, non-static paths to `index.html` for React Router.
- **35 expense categories** defined in `ExpenseCategory.java`.

## Testing

No tests exist yet. `spring-boot-starter-test` is included in `pom.xml` but unused.

## Gotchas

- The frontend build **must** run before the backend build (Maven handles this automatically via `frontend-maven-plugin`, but manual builds require running `npm run build` in `frontend/` first).
- Hibernate `ddl-auto=update` means schema changes happen automatically — be careful with entity field renames or removals as Hibernate won't drop columns.
- The seed user is only created when the `users` table is empty.
- Timezone is hardcoded to `America/Montreal` in multiple places (application.properties, Dockerfile).
