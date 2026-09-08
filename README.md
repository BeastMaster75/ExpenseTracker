# ExpenseTracker

ExpenseTracker is a backend expense management system built with Java Spring Boot.

Track income and expenses across multiple budgets with real-time account balances and spending ceilings. 
Every transaction is validated against your available funds and budget limits — no phantom spending, 
no arithmetic drift.

## Key Features

- **Multi-budget support** — organize spending by category with monthly resets
- **Income top-ups** — boost a budget's allowance for the month without changing its limit
- **Transactional integrity** — every edit is undo-then-redo to prevent budget corruption
- **Validated spending** — checks run against the full ceiling (limit + income top-ups)
- **User auth** — JWT-based authentication with httpOnly cookie storage
- **Soft deletion** — budgets can be archived; transactions reverse their impact on deletion
- **Complete audit trail** — every money movement is logged with before/after values

## Architecture

- Spring Boot 3.x with JPA/Hibernate
- PostgreSQL (or configured database via online mode)
- RESTful API with transaction and budget endpoints
- Comprehensive validation at transaction boundaries
- Rollback-safe mutation model (apply → validate → commit)
