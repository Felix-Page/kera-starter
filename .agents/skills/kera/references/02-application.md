# Layer 2 — Application Module (`application`)

## 1. Role & Dependency Rules
- **Maven Module**: `application` (depends ONLY on `fr.fpage.kera:kera-application` and the local `domain` module).
- **Pure Java 25**: Orchestrates application use cases and defines input/output port contracts.
- **Strictly Forbidden Imports**: Never import `org.springframework.*` (`@Service`, `@Component`, `@Transactional`, `Pageable`, `Page`), `jakarta.persistence.*`, or `web`/`persistence` classes.

---

## 2. Package Structure & Components

1. **`usecases/` (Primary / Driving Ports)**:
   * One interface per business use case (Interface Segregation Principle): e.g., `GetAllRecipesUseCase`, `GetRecipeByIdUseCase`, `CreateRecipeUseCase`.
   * Expose a single method `execute(...)` taking domain objects/primitives/command records and returning domain objects (`List<Recipe>`, `Optional<Recipe>`, `Recipe`, `void`) or a composite UseCase `Result` record.
2. **`interactors/` (Use Case Implementations — Strict `1 UseCase = 1 Interactor`)**:
   * Pure Java POJOs (no Spring annotations).
   * **Strict 1-to-1 Rule (Single Responsibility Principle)**: Each `*UseCase` interface MUST be implemented by its own dedicated `*Interactor` class bearing the exact same action name:
     * `GetAllRecipesUseCase` $\rightarrow$ `GetAllRecipesInteractor`
     * `CreateIngredientUseCase` $\rightarrow$ `CreateIngredientInteractor`
   * **Never** create a "God Interactor" per entity (like `RecipeInteractor` or `IngredientInteractor`) that implements multiple `*UseCase` interfaces.
   * Inject only the secondary ports (`GenericPort<ID, DOMAIN>` or custom `*Port`) required by that specific use case via constructor.
3. **`results/` / `commands/` (or nested `record` inside `*UseCase`)**:
   * Used when a UseCase needs to return an aggregation of values coming from multiple domain objects or computed metrics (Output Boundary), or accept a structured multi-field input (Input Boundary).
4. **`ports/` (Secondary / Driven Ports — Optional)**:
   * Only created when an entity requires queries beyond standard CRUD.

---

## 3. Composite Return Values & Input/Output Models

When a UseCase must return a combination of values from multiple domain entities or computed data (rather than a single `Domain` entity):
1. **UseCase-Specific Aggregation (`application` layer)**:
   * Define an immutable Java `record` either **nested directly inside the `*UseCase` interface** (preferred when specific to that single UseCase) or in a **`results/` package** in `application` (`*Result`, e.g., `RecipeSummaryResult`) if shared across multiple classes.
   * The `web` layer mapper (`@Mapper(config = WebMapperConfig.class)`) will map this `*Result` record to the HTTP `*Response` record.
2. **Reusable Business Concept (`domain` layer)**:
   * If the aggregated values represent a core domain concept with its own business invariants reused across the domain (e.g., `NutritionalProfile`), define it as a **Value Object (`record`) inside `domain`** instead.

---

## 4. Kera Application Public API (`fr.fpage.kera.application`)

> **Do NOT run `javap` on `kera-application.jar`** — here is the complete public API of the module:

```java
package fr.fpage.kera.application;

import fr.fpage.kera.domain.IdentifiableDomain;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GenericPort<ID, DOMAIN extends IdentifiableDomain<ID>> {
    Optional<DOMAIN> findById(final ID id);
    List<DOMAIN> findAll();
    List<DOMAIN> findAllById(final Collection<ID> ids);

    DOMAIN save(final DOMAIN domain);
    List<DOMAIN> saveAll(final Collection<DOMAIN> domains);

    void delete(final DOMAIN domain);
    void deleteById(final ID id);
}
```

### Secondary Port Strategy (`GenericPort<ID, DOMAIN>` vs Custom `*Port`):
1. **Standard CRUD (Default)**: Inject `GenericPort<UUID, MyDomain>` directly into the `*Interactor` constructor. **Do NOT create an empty `MyDomainPort` interface**.
2. **Custom Queries Required**: Create `public interface MyDomainPort extends GenericPort<UUID, MyDomain>` inside `application/src/main/java/.../ports/`, add the custom method (e.g., `Optional<Recipe> findByName(String name)`), and make `MyPersistenceAdapter` in `persistence` implement `MyDomainPort` (adding `application` as a dependency of `persistence/pom.xml` if not already present).

---

## 5. Canonical Code Templates

### 5.1. Primary Port (`GetAllRecipesUseCase.java`)
```java
package com.example.app.usecases;

import com.example.app.domain.Recipe;

import java.util.List;

public interface GetAllRecipesUseCase {
    List<Recipe> execute();
}
```

### 5.2. Primary Port Returning a Composite Value (`GetRecipePlanningUseCase.java`)
```java
package com.example.app.usecases;

import com.example.app.domain.Ingredient;
import com.example.app.domain.Recipe;

import java.util.List;
import java.util.UUID;

public interface GetRecipePlanningUseCase {
    Result execute(Query query);

    record Query(UUID recipeId, int guestCount) {}
    record Result(Recipe recipe, List<Ingredient> missingIngredients, double scaledServings) {}
}
```

### 5.3. POJO Interactor Using `GenericPort` (`GetAllRecipesInteractor.java`)
```java
package com.example.app.interactors;

import fr.fpage.kera.application.GenericPort;
import com.example.app.domain.Recipe;
import com.example.app.usecases.GetAllRecipesUseCase;

import java.util.List;
import java.util.UUID;

public class GetAllRecipesInteractor implements GetAllRecipesUseCase {
    private final GenericPort<UUID, Recipe> recipePort;

    public GetAllRecipesInteractor(GenericPort<UUID, Recipe> recipePersistence) {
        this.recipePort = recipePersistence;
    }

    @Override
    public List<Recipe> execute() {
        return this.recipePort.findAll();
    }
}
```

### 5.4. Custom Secondary Port (Only when non-CRUD queries are needed)
```java
package com.example.app.ports;

import fr.fpage.kera.application.GenericPort;
import com.example.app.domain.Recipe;

import java.util.Optional;
import java.util.UUID;

public interface RecipePort extends GenericPort<UUID, Recipe> {
    Optional<Recipe> findByName(String name);
}
```

---

## 6. Layer Checklist (`DO` / `DON'T`)
- ✅ **DO** follow the strict **1 UseCase = 1 Interactor** rule (`<ActionName>UseCase` $\rightarrow$ `<ActionName>Interactor`).
- ✅ **DO** keep Interactors 100% free of Spring annotations (they are instantiated in `bootstrap/InteractorConfig`).
- ✅ **DO** use `GenericPort<ID, DOMAIN>` directly whenever the 6 standard CRUD methods are sufficient.
- ✅ **DO** use a `record` (`*UseCase.Result` or `results/*Result`) in `application` when returning composite data from multiple domain objects, or a Value Object `record` in `domain` if it is a core domain concept.
- ❌ **DON'T** implement multiple `*UseCase` interfaces on a single `*Interactor` class (no `RecipeInteractor` or `IngredientInteractor`).
- ❌ **DON'T** create empty `XxxPort extends GenericPort<UUID, Xxx>` interfaces with no extra methods.
- ❌ **DON'T** return Web DTOs (`*Response`) or JPA Entities (`*Entity`) from a UseCase. Always manipulate and return `domain` objects or `application` result records.
