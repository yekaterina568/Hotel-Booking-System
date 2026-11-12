# Stage 0 repository audit

Audit date: November 12, 2026 (Asia/Almaty). Starting branch: `main`; starting HEAD: `bef47c2`. Work branch: `codex/stage0-team-commitments`.

## Scope and preservation

Read all original Java controllers, services, entities, repositories, enum models, frontend files, Maven/wrapper configuration, application configuration, Compose, Liquibase master/001, the sole existing test, and the local README/SQL seed. No repository or ancestor AGENTS.md was found. No benchmark generator or benchmark dataset is committed.

The working tree already contained changes to Java 25, executable mvnw, booking/archive IDs, database configuration/Compose, and untracked README, .env.example and backend/database/seed.sql. They were preserved in a separate baseline snapshot commit `f73b613`; these are prior local work, not Stage 0 implementation claims. `.env` was not committed or printed. Migration 001 is immutable.

## Initial findings (before implementation)

| Area | Implemented in starting code | Verified working before changes? | Gap |
|---|---|---|---|
| Build | Spring Boot 4.1.2, Java 25 local fix, JPA/Liquibase/cache dependencies | `mvnw -DskipTests compile` passed on Java 25.0.1 | Original committed Java target was 26; cache dependency alone did nothing |
| Database | 001 creates six tables, four native enum types, FKs, price/passport checks, arrival index | Source inspected; baseline full application not verified | Uppercase Java labels differed from title-case DB enum labels; several repositories used BigInteger with Long entity IDs |
| Guest/room CRUD | Entity-based controller/service scaffolding | No | Most entities had no accessors for JSON; API accepted persistence entities, no request validation |
| Reservation | Create/list/get/cancel/check-in methods | No | No dates, availability, concurrency guard, cleaning guard, DTOs or transactional check-in |
| Keycards | Separate CRUD scaffolding | No | Check-in did not issue a card; no lifecycle coordination |
| Archive | Controller and transaction | Broken by inspection | Saved a new empty archive entity without copying ID/mandatory fields |
| Refunds | Table and CRUD scaffolding | No | No validation, paid-balance checks, payment link or frontend form |
| Payments/hotels/reviews/cost | Absent | No | Need entities, migrations, APIs and payment UI |
| Cache | Caffeine dependency | No | No cache manager, @EnableCaching, @Cacheable or eviction |
| Frontend | HTML/CSS and mock rooms/bookings | Mock rendering only | Zero fetch calls or API paths; check-in alert falsely claimed success/RFID issuance; regex falsely claimed passport was verified in DB |
| Security | Spring Security dependency/default generated login | No | No usable staff integration or response minimization; DTOs do not provide authorization |
| QA | One contextLoads test tied to ambient database | Not executed in this audit | No behavioral, PostgreSQL, concurrent or frontend integration coverage |
| Environment | JDK 25.0.1, Maven wrapper 3.10.0, Docker 28.3.2, PostgreSQL images installed | Compile verified | Docker daemon initially stopped; started Docker Desktop without modifying existing volumes |

## Implementation checklist

- [x] Preserve local work and create a separate branch.
- [x] Audit source and identify false success messages and startup/mapping risks.
- [x] Add additive schema, legacy-safe upgrade, dev-only seed, joins/index examples.
- [x] Implement booking dates, costs, availability/concurrency, keycard/check-in and archive lifecycle.
- [x] Implement simulated payment/refund records, validation and retry protection.
- [x] Add authenticated DTO APIs and transaction-aware Caffeine cache invalidation.
- [x] Replace mock frontend operations with real requests and truthful errors.
- [x] Execute at least 20 meaningful tests, compile, fresh migration, populated 001 upgrade, real API/browser/SQL checks.
- [x] Write implementation, test and demo reports; only then plan Stages 1–3.

Final verification evidence and remaining limits are in STAGE0_TEST_REPORT.md and STAGE0_IMPLEMENTATION_REPORT.md. Source implementation must not be read as proof that a runtime scenario passed.

## Final audit disposition

The Stage 0 implementation is now verified by the 28 backend/migration cases, five frontend cases and ten browser/SQL checkpoints recorded in STAGE0_TEST_REPORT.md. Compilation/packaging, real PostgreSQL startup, fresh migration and a populated 001 upgrade all completed. The working-project database itself was deliberately not opened or migrated. Legacy date reconciliation, multi-role staff access, pagination and production operations remain explicit future work, not verified Stage 0 features.
