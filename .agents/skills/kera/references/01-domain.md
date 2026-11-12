# Layer 1 — Domain Module (`domain`)

## 1. Role & Dependency Rules
- **Maven Module**: `domain` (depends ONLY on `fr.fpage.kera:kera-domain`).
- **Pure Java 25**: This module contains enterprise business models, enums, value objects, and domain invariants.
- **Strictly Forbidden Imports**: Never import `jakarta.persistence.*`, `org.springframework.*`, `com.fasterxml.jackson.*`, `org.mapstruct.*`, or `lombok.*`.

---

## 2. Kera Domain Public API (`fr.fpage.kera.domain`)

> **Do NOT run `javap` on `kera-domain.jar`** — here is the complete public API of the module:

```java
package fr.fpage.kera.domain;

public abstract class IdentifiableDomain<ID> {
    protected final ID id;
    protected IdentifiableDomain(ID id) { this.id = id; }
    public ID getId() { return id; }
}

public abstract class AuditableDomain<ID> extends IdentifiableDomain<ID> {
    protected final LocalDateTime createdAt;
    protected final LocalDateTime updatedAt;
    protected AuditableDomain(ID id, LocalDateTime createdAt, LocalDateTime updatedAt) { ... }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

public abstract class VersionedDomain<ID> extends AuditableDomain<ID> {
    protected final Long version;
    protected VersionedDomain(ID id, LocalDateTime createdAt, LocalDateTime updatedAt, Long version) { ... }
    public Long getVersion() { return version; }
}
```

### Choosing the Base Class (Must mirror the JPA entity contract in `persistence`):

| Base Class | Inherited `final` Fields | Counterpart Interface in `persistence` | When to Use |
| :--- | :--- | :--- | :--- |
| `IdentifiableDomain<ID>` | `id` | `IdentifiableEntity<ID>` | Simple entities without audit timestamps or optimistic locking. |
| `AuditableDomain<ID>` | `id`, `createdAt`, `updatedAt` | `AuditableEntity<ID>` | Append-only or auditable entities without concurrent update locking. |
| `VersionedDomain<ID>` | `id`, `createdAt`, `updatedAt`, `version` | `VersionedEntity<ID>` | **Default choice** for standard business entities and aggregate roots (enables JPA `@Version` optimistic locking). |

All metadata fields (`id`, `createdAt`, `updatedAt`, `version`) are `protected final` and have **no setters**.

---

## 3. Constructor & Instantiation Rules (MapStruct Compatibility)

1. **Single Public Full Constructor**:
   * MapStruct instantiates domain objects in `persistence` using their public constructor.
   * Always provide **one public constructor** accepting all metadata fields (`id`, `createdAt`, `updatedAt`, `version`) + all domain fields, with exact parameter names matching the getters.
2. **Use Static Factory Methods Instead of Constructor Overloading**:
   * **Why?** Adding a second public constructor (e.g., `public Book(String title)`) causes MapStruct compilation failure (`Ambiguous constructors found for creating...`), and you cannot use MapStruct's `@Default` annotation in `domain` because `domain` has zero external dependencies.
   * **Solution**: For creating a brand-new domain instance before persistence, provide a **static factory method** (e.g., `public static Ingredient create(String name)`) that calls the single constructor with `null` for all metadata fields (`new Ingredient(null, null, null, null, name)`). **Do NOT generate the ID (`UUID.randomUUID()`) in the domain** — Hibernate generates it automatically on save via the `@GeneratedValue` strategy defined on the project's `@MappedSuperclass` (`AbstractEntity`) or JPA `@Entity`.

---

## 4. Self-Validating Domain Model (Always-Valid Pattern)

Domain entities must enforce their own field invariants in **pure Java** (never use `jakarta.validation` annotations in `domain`):
1. **One `private void validate<Field>(...)` method per validated field**:
   * Throw `IllegalArgumentException` (or a domain exception) with a clear message when an invariant is violated.
2. **Validate in the Setter AND Constructor**:
   * Call `this.validate<Field>(value)` at the start of `set<Field>(value)`.
   * Call `this.set<Field>(value)` inside the constructor so that both `new Entity(...)` / `Entity.create(...)` and subsequent mutations enforce the exact same validation rules without duplicating logic.
3. **Ask the User When Invariants Are Unspecified**:
   * If the user has not specified the validation rules for a new domain field (e.g., min/max length, non-blank, positive number, non-empty list), **proactively ask the user** which constraints to apply before writing the validation methods.

---

## 5. Canonical Code Templates

### 5.1. Simple Domain Entity (`Ingredient`)
```java
package com.example.app.domain;

import fr.fpage.kera.domain.VersionedDomain;

import java.time.LocalDateTime;
import java.util.UUID;

public class Ingredient extends VersionedDomain<UUID> {
    private String name;

    public Ingredient(UUID id, LocalDateTime createdAt, LocalDateTime updatedAt, Long version, String name) {
        super(id, createdAt, updatedAt, version);
        this.setName(name);
    }

    public static Ingredient create(String name) {
        return new Ingredient(null, null, null, null, name);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.validateName(name);
        this.name = name;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Ingredient name cannot be null or blank");
        }
    }
}
```

### 5.2. Aggregate Root with Child Entities (`Recipe`)
```java
package com.example.app.domain;

import fr.fpage.kera.domain.VersionedDomain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class Recipe extends VersionedDomain<UUID> {
    private String name;
    private Integer servings;
    private List<RecipeIngredient> ingredients;
    private List<RecipeStep> steps;

    public Recipe(
            UUID id,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long version,
            String name,
            Integer servings,
            List<RecipeIngredient> ingredients,
            List<RecipeStep> steps
    ) {
        super(id, createdAt, updatedAt, version);
        this.setName(name);
        this.setServings(servings);
        this.setIngredients(ingredients);
        this.setSteps(steps);
    }

    // Getters and setters (each setter calls its private validate<Field> method)
    public String getName() { return name; }
    public void setName(String name) {
        this.validateName(name);
        this.name = name;
    }
    public Integer getServings() { return servings; }
    public void setServings(Integer servings) {
        this.validateServings(servings);
        this.servings = servings;
    }
    public List<RecipeIngredient> getIngredients() { return ingredients; }
    public void setIngredients(List<RecipeIngredient> ingredients) { this.ingredients = ingredients; }
    public List<RecipeStep> getSteps() { return steps; }
    public void setSteps(List<RecipeStep> steps) { this.steps = steps; }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Recipe name cannot be null or blank");
        }
    }

    private void validateServings(Integer servings) {
        if (servings == null || servings <= 0) {
            throw new IllegalArgumentException("Recipe servings must be strictly positive");
        }
    }
}
```

---

## 6. Layer Checklist (`DO` / `DON'T`)
- ✅ **DO** match the exact parameter names in the constructor (`id`, `createdAt`, `updatedAt`, `version`, ...) to the field/getter names.
- ✅ **DO** validate fields via a `private void validate<Field>(...)` method called inside `set<Field>(...)`, and call `this.set<Field>(...)` inside the constructor.
- ✅ **DO** ask the user to confirm field validation rules when they are not explicitly stated in the prompt.
- ✅ **DO** pass `null` for `id`, `createdAt`, `updatedAt`, and `version` in static `create(...)` factory methods (the persistence layer generates them via `@GeneratedValue`, `@CreationTimestamp`, `@UpdateTimestamp`, and `@Version`).
- ✅ **DO** use pure Java types (`UUID`, `LocalDateTime`, `BigDecimal`, `List`, enums, records for Value Objects).
- ❌ **DON'T** use `jakarta.validation` annotations (`@NotBlank`, `@NotNull`) inside `domain` (annotations are only for `web` request records).
- ❌ **DON'T** call `UUID.randomUUID()` when creating a new domain entity (let `@GeneratedValue` on the JPA entity / `AbstractEntity` generate the ID on save).
- ❌ **DON'T** add a no-arg constructor `public Xxx()` or multiple public constructors to a `Domain` class (breaks MapStruct constructor resolution).
- ❌ **DON'T** add setters for `id`, `createdAt`, `updatedAt`, or `version`.
