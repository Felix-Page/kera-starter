---
name: kera
description: >-
  Architecture and development guidelines for Java / Spring Boot applications built with the Kera framework (fr.fpage.kera).
  Activate this skill whenever creating or modifying domain models, use cases, interactors, ports, JPA entities, MapStruct mappers, persistence adapters, REST controllers, DTOs, Spring bean configurations, or Liquibase migrations.
---

# Kera — Architecture & Development Guidelines

This project follows **Clean Architecture** powered by the **`kera`** framework (`fr.fpage.kera`).
All code is strictly split across **5 Maven modules**. Dependencies must ONLY point inward toward the `domain`.

> **IMPORTANT FOR AI AGENTS**: All public classes and method signatures of the `kera` library are fully documented in `./references/01-domain.md` through `05-bootstrap.md`. **Do NOT run `javap` or decompile `.jar` files from `~/.m2`** — read the corresponding `./references/0X-*.md` file instead.

---

## 1. Module Topology & Dependency Matrix

| # | Application Module | Kera Dependency | Role | Allowed Framework Dependencies | Detailed Guide |
| :- | :--- | :--- | :--- | :--- | :--- |
| **1** | `domain` | `kera-domain` | Enterprise business entities, value objects, domain invariants. | **NONE (Pure JDK 25 only)**. Zero Spring, JPA, Jackson, or MapStruct. | [01-domain.md](./references/01-domain.md) |
| **2** | `application` | `kera-application` | Primary ports (`*UseCase`), orchestrators (`*Interactor`), secondary ports (`GenericPort` / `*Port`). | **NONE (Pure JDK 25 only)**. Zero `@Service`, `@Component`, `@Transactional`, or Spring Data. | [02-application.md](./references/02-application.md) |
| **3** | `persistence` | `kera-spring-persistence` | Driven JPA adapter (`*Entity`, `*JpaRepository`, `*EntityMapper`, `*PersistenceAdapter`) & Liquibase SQL changelogs (`db/changelog/`). | Spring Data JPA, Hibernate, Jakarta Persistence, MapStruct. | [03-persistence.md](./references/03-persistence.md) |
| **4** | `web` | `kera-spring-web` | Driving REST adapter (`*Controller`, `record *Request` / `*Response`, `*DomainMapper`). | Spring Web MVC, MapStruct. **Must NEVER depend on `persistence`**. | [04-web.md](./references/04-web.md) |
| **5** | `bootstrap` | `kera-spring-bootstrap` | Composition Root (`Bootstrap`, `InteractorConfig`), `application.yml`. | Spring Boot, Liquibase, Runtime JDBC Driver. | [05-bootstrap.md](./references/05-bootstrap.md) |

---

## 2. The 6 Golden Rules (Non-Negotiable Invariants)

1. **Pure Domain & Application**: Never import `org.springframework.*`, `jakarta.persistence.*`, or `com.fasterxml.jackson.*` inside `domain` or `application`.
2. **POJO Interactors (Strict 1 UseCase = 1 Interactor)**:
   * Interactors in `application` are pure Java classes. Never annotate them with `@Service` or `@Component`. They are wired as `@Bean` methods inside `bootstrap` (`InteractorConfig`).
   * **Single Responsibility Principle**: Every `*UseCase` interface MUST have its own dedicated `*Interactor` class with the exact matching prefix (e.g., `GetAllRecipesUseCase` $\rightarrow$ `GetAllRecipesInteractor`, `CreateIngredientUseCase` $\rightarrow$ `CreateIngredientInteractor`). Never group multiple use cases into a single generic `XxxInteractor`.
3. **Immutable Domain Metadata**: Domain classes inherit from `IdentifiableDomain<ID>`, `AuditableDomain<ID>`, or `VersionedDomain<ID>`. Their metadata fields (`id`, `createdAt`, `updatedAt`, `version`) are `final` and hydrated exclusively via constructor.
4. **No Boilerplate Ports or Adapters**:
   * In `application`, use `GenericPort<ID, DOMAIN>` directly for standard CRUD operations. Only create a dedicated `XxxPort extends GenericPort<ID, DOMAIN>` if custom queries are required.
   * In `persistence`, adapters extend `GenericPortImpl` and mappers extend `AbstractEntityMapper` with `@Mapper(config = PersistenceMapperConfig.class)`.
5. **Autonomous vs Child Entities (`ReferenceableEntity`)**:
   * **Autonomous entities (Aggregate Roots)** that have their own `GenericPort` / `JpaRepository` or are referenced via `@ManyToOne` **MUST implement `ReferenceableEntity`** so `JpaEntityResolver` resolves them via Hibernate proxy (`entityManager.getReference`).
   * **Internal child entities** owned via `@OneToMany(cascade = CascadeType.ALL)` **MUST NOT implement `ReferenceableEntity`** so MapStruct maps all their fields.
6. **Strict API, Validation & Schema Isolation**:
   * **Domain Self-Validation**: Domain objects validate their own invariants in pure Java via `private void validate<Field>(...)` called inside each setter (and invoked by the constructor via `this.set<Field>(...)`).
   * **Web DTO & Validation**: Controllers in `web` never return or accept `domain` objects directly; always map to/from Java `record` DTOs via `@Mapper(config = WebMapperConfig.class)` and validate incoming `*Request` records with `jakarta.validation` (`@Valid`, `@NotBlank`, `@NotNull`).
   * **Liquibase Validation**: Hibernate runs with `ddl-auto: validate`. Every change to a JPA `*Entity` MUST have a matching Liquibase migration in `persistence/src/main/resources/db/changelog/`.
7. **Interactive Clarification Before Coding (Ask, Don't Guess)**:
   * Whenever a user request is missing specifications — such as **which validation constraints** apply to domain/request fields, **which fields** should be exposed in a `*Response` record, or **whether uniqueness/business checks** are needed in the Interactor — **always ask the user for confirmation before generating code**.
   * Proactively suggest relevant validations (e.g., *"Should we validate that `name` is not blank and `servings > 0` in the Domain and Request DTO?"*) and wait for the user's answer.

---

## 3. Vertical Slice Workflow (Implementing a Feature End-to-End)

When implementing or modifying a feature, **always read the reference guide of each targeted layer before editing files** and follow this exact execution order:

1. **Step 1 — Domain (`domain/`)**: Read [01-domain.md](./references/01-domain.md) $\rightarrow$ Create/update the domain model (`VersionedDomain<UUID>`), constructors, and domain logic.
2. **Step 2 — Application (`application/`)**: Read [02-application.md](./references/02-application.md) $\rightarrow$ Define the `*UseCase` interface(s), secondary `*Port` (if custom beyond `GenericPort`), and `*Interactor` POJO.
3. **Step 3 — Persistence & DB (`persistence/`)**: Read [03-persistence.md](./references/03-persistence.md) $\rightarrow$ Create/update the project's `@MappedSuperclass` base entity (e.g., `AbstractEntity implements VersionedEntity<UUID>`) if not yet present, then `*Entity` (`extends AbstractEntity implements ReferenceableEntity`), `*JpaRepository`, `*EntityMapper`, `*PersistenceAdapter`, and add/update the Liquibase SQL changelog in `persistence/src/main/resources/db/changelog/`.
4. **Step 4 — Web (`web/`)**: Read [04-web.md](./references/04-web.md) $\rightarrow$ Create/update `record` DTOs (`*Request` / `*Response`), `*DomainMapper`, and `*Controller`.
5. **Step 5 — Bootstrap (`bootstrap/`)**: Read [05-bootstrap.md](./references/05-bootstrap.md) $\rightarrow$ Register `@Bean` definitions in `InteractorConfig` / `BeanConfig`.
6. **Step 6 — Verification**: Run `mvn clean compile` from the project root to verify MapStruct code generation and module dependency boundaries.

---

## 4. Audit & Compliance Mode (When Asked to Audit / Refactor the Project)

When the user asks to **audit**, **verify**, or **align** the project with Kera conventions:

1. **Read All 5 Layer Guides**: Read `01-domain.md` through `05-bootstrap.md` completely.
2. **Scan All 5 Modules for Violations**:
   * **`domain/`**: Missing `this.set<Field>(...)` in constructors, missing `private void validate<Field>(...)` in setters, missing static `create(...)` factory passing `null` for metadata, or forbidden framework imports.
   * **`application/`**: Generic interactors grouping multiple use cases (violating `1 UseCase = 1 Interactor`), missing `*PersistentPort` naming, or forbidden `@Service` / `@Transactional` annotations.
   * **`persistence/`**: Missing or misused `ReferenceableEntity`, missing `PersistenceMapperConfig`, missing `public` + `protected` constructors on entities, or Liquibase drift.
   * **`web/`**: Missing `jakarta.validation.constraints.*` (`@NotBlank`, `@NotNull`, `@Positive`, `@Size`) on `*Request` records, missing `@Valid @RequestBody` or `@Transactional` / `@Transactional(readOnly = true)` on `*Controller` methods, or missing `WebMapperConfig`.
   * **`bootstrap/`**: Outdated `@Bean` wiring in `BeanConfig` / `InteractorConfig`.
3. **Present Findings & Ask Before Refactoring**:
   * Report a concise summary table of all detected rule violations.
   * **Ask the user to confirm business validation rules** (required fields, max lengths, positive numbers) for existing entities before modifying code.
4. **Apply Fixes & Compile**: Once confirmed by the user, refactor all layers and run `mvn clean compile` to ensure zero errors and zero MapStruct warnings.
