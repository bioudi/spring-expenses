# Contributing to Spendifi

This guide defines how we write code in this repository. Read it before opening a PR — reviewers will hold changes to these rules. For an overview of the stack, commands, and environment variables, see [CLAUDE.md](CLAUDE.md) and [README.md](README.md).

---

## 1. Architecture rules

The backend is **package-by-layer** and the dependency direction is one-way:

```
controller  →  service  →  repository  →  database
     ↓            ↓
    dto        entity
```

- **Controllers** parse/validate input, resolve the current user via `SecurityUtils.getCurrentUserId()`, delegate to a service, and shape the HTTP response. No business logic, no repository access (the only exception is trivially read-only lookups already established in `WebhookController`).
- **Services** own all business logic and transaction boundaries. They accept and return **DTOs**, never let entities escape to controllers.
- **Repositories** are Spring Data interfaces. Derived query methods where possible, `@Query` when the derived name would be unreadable.
- **Entities** are persistence models only. Never serialize an entity to a client — write a DTO with a static `fromEntity()` factory (see `ExpenseResponse.fromEntity`).

If a change needs to skip a layer, the design is wrong — refactor instead.

## 2. Money handling (the most important section)

- **`BigDecimal` only.** Never `float`/`double` for amounts, and always pass an explicit scale and `RoundingMode` when dividing: `total.divide(count, 2, RoundingMode.HALF_UP)`.
- **CREDIT sign convention.** A CREDIT account's `balance` is *outstanding debt, stored positive*. Money flowing **out** of the user's pocket into a CREDIT account (payment, income/refund) **decreases** the balance; spending on the card **increases** it. `TransferService`'s javadoc holds the canonical four-case matrix — any code touching account balances must follow it. If you add a new money-moving feature, write out its sign case in a comment and mirror the matrix.
- **Balance mutations must be atomic.** Never read a balance, compute in Java, and write it back — concurrent requests will lose updates. Use the single-statement guarded updates in `AccountRepository` (`decrementBalanceIfSufficient`, `addToBalance`), ideally through `AccountService.adjustBalance`, which also enforces the "non-CREDIT balances never go negative" invariant (`InsufficientFundsException` → HTTP 422).
- **Beware Hibernate's first-level cache after bulk updates.** The `@Modifying` queries above bypass the persistence context. An entity loaded earlier in the same transaction will show a *stale* balance afterwards — re-read via `EntityManager.refresh()` (see `AccountService.freshBalance` and `TransferService`) and never make funds decisions from an in-memory balance once a bulk update has run.
- **Every creation path must have a symmetric reversal path.** If creating a record adjusts a balance, updating and deleting that record must reverse it with the exact opposite delta (account-type aware). When you add a new path that materialises expenses/incomes (webhooks, schedulers, imports), check both directions — this class of bug has bitten us before.

## 3. Transactions & scheduled jobs

- `@Transactional` on every service method that writes; `@Transactional(readOnly = true)` on every service method that only reads.
- Multi-step money operations (transfer, expense update with account change) must live in **one** transactional service method so all mutations commit or roll back together.
- In `@Scheduled` batch jobs, isolate failures per item (`try/catch` inside the loop) so one bad row doesn't kill the whole run — but be careful: an exception that crosses a `@Transactional` **proxy** boundary marks the shared transaction rollback-only even if you catch it. Inside batch loops, call repositories directly or throw/catch within the same bean.
- Order side effects so a caught failure can't leave half a record: debit the account *before* saving the row it funds.

## 4. Error handling

- Throw a **specific domain exception** (`ExpenseNotFoundException`, `InsufficientFundsException`, …) and map it to an HTTP status in `GlobalExceptionHandler`. Never return raw 500s for expected failure modes.
- Status conventions already established: unknown/foreign resource → **404**; validation problems → **400**; same-resource conflicts → **409**; insufficient funds → **422**.
- Every user-owned lookup goes through a `findByIdAndUserId(id, userId)` repository method. A resource belonging to another user is indistinguishable from one that doesn't exist (404) — never leak existence.
- Don't throw bare `RuntimeException` (a few legacy spots still do — fix them when you touch that code, don't add more).

## 5. Security

- **Ownership scoping is non-negotiable.** Every query for user data filters by `userId` from the security context. Never trust a client-supplied user id.
- Session auth for the SPA; per-user API keys (via `ApiKeyFilter`) only for `/api/webhook/**`. New endpoints are authenticated by default — add explicit `permitAll` entries to `SecurityConfig` only with justification in the PR.
- Passwords: BCrypt only, via the shared `PasswordEncoder` bean. Never log passwords, API keys, or full session identifiers.
- CSRF is disabled for `/api/**`, so the `SameSite=Lax` session cookie (see `application.properties`) is a load-bearing mitigation — do not remove it.

## 6. Java style

- **Lombok everywhere:** `@Getter`/`@Setter`/`@Builder` on entities and DTOs, `@RequiredArgsConstructor` + `final` fields for injection, `@Slf4j` for logging. No `@Autowired` field injection, no hand-written getters.
- **Javadoc for "why", comments for constraints.** Non-obvious business rules (sign conventions, guard rationale, ordering requirements) get a comment explaining *why the code must be this way*. Don't comment *what* the code does.
- Log meaningful state transitions at `info` (created/updated/deleted with ids), diagnostics at `debug`, recoverable anomalies at `warn`, failures with stack traces at `error`. Use parameterized logging (`log.info("... {}", id)`), never string concatenation.
- Prefer `java.time` (`LocalDate`, `LocalDateTime`) arithmetic over manual date math; remember the app is pinned to `America/Montreal`.
- Delete dead code — don't keep unused methods "for later"; git remembers.

## 7. Database & schema

- Schema is managed by Hibernate `ddl-auto=update` — there is **no migration tool**. Consequences:
  - Adding entities/columns is safe; **renaming or removing** columns leaves orphans in the DB. Note any manual cleanup needed in the PR description.
  - New non-null columns on existing tables need a default or a backfill plan (`SchemaMigrationRunner` exists for one-off data fixes).
- All primary keys are **UUID** (`GenerationType.UUID`).
- Real relations get a `@ManyToOne(fetch = FetchType.LAZY)` with a proper FK. If you add an FK to `Account`, extend the delete guard in `AccountService.deleteAccount` so users get an actionable 400 instead of a constraint-violation error.
- `@JsonIgnore` the `user` side of every association (entities shouldn't be serialized anyway — see rule 1).

## 8. Frontend (React + TypeScript)

- **Functional components with hooks** only. Pages in `src/pages/`, shared components in `src/components/`, shadcn primitives in `src/components/ui/` (available set: alert-dialog, badge, button, card, dialog, input, label, table, textarea — use native `<select>`, there is no shadcn Select).
- **All HTTP goes through `lib/api.ts`.** Never call `fetch` directly from a component — add a typed method to the `api` object and a matching type in `src/types/`. Types must mirror the backend DTOs field-for-field.
- Toasts via `sonner`; currency/date formatting via the helpers in `lib/formatters.ts` — don't hand-roll `toFixed(2)`.
- All user-facing strings go through the i18n layer (`src/i18n/en.ts` + `fr.ts`) — never hardcode display text; add both languages in the same PR.
- Run `npm run lint` before pushing. Don't add new lint errors (a handful of pre-existing ones are known; leave them or fix them in a dedicated PR).

## 9. Testing & verification

The test suite lives under `src/test/java` (integration tests with MockMvc + H2, plus entity/service tests). Keep it green and growing:

- **Run the full suite before every PR:** `mvn test -Dskip.installnodenpm -Dskip.npm` (the flags skip the slow frontend build). Frontend: `cd frontend && npm run lint && npm run build`.
- **New service-layer logic involving money must come with tests.** Mirror the main package layout; follow the existing style (nested `@Nested` classes per scenario, MockMvc through the full stack including `ApiKeyFilter`).
- **Never hardcode calendar dates in fixtures.** Tests that assume "the current month is June 2026" become time bombs the moment the calendar rolls over. Derive fixture dates from `YearMonth.now()` / `LocalDate.now()` and build any explicit date parameters from the same values.
- For balance-affecting changes, exercise the full cycle (create → update → delete) and confirm the account balance returns to its starting value. Test the CREDIT variant too — most balance bugs hide there.

## 10. Git & PR workflow

- Branch from `main`: `fix/<slug>` or `feat/<slug>`.
- Commit messages follow the existing conventional style: `fix(transfer): apply four-case sign-aware delta matrix for CREDIT accounts`.
- One logical change per PR. In the description state: what changed, why, and **how you verified it** (commands run, manual scenarios exercised).
- If your change alters a documented convention (sign matrix, error contract, schema), update the relevant javadoc/CLAUDE.md/this file in the same PR.

---

## Quick pre-PR checklist

- [ ] Layering respected (controller → service → repository; DTOs at the boundary)
- [ ] Money: `BigDecimal`, atomic balance updates, CREDIT sign convention, symmetric reversal
- [ ] `@Transactional` (`readOnly` where applicable) on service methods
- [ ] All lookups user-scoped (`findByIdAndUserId`)
- [ ] Domain exception + `GlobalExceptionHandler` mapping for new failure modes
- [ ] No entities serialized; no secrets logged
- [ ] Full backend test suite passes; frontend lints and builds
- [ ] Tests added for new money-path logic (no hardcoded calendar dates); create/update/delete cycle verified
