# Layer 4 — Web Module (`web`)

## 1. Role & Dependency Rules
- **Maven Module**: `web` (depends on `fr.fpage.kera:kera-spring-web`, `application`, and `domain`).
- **Role**: Primary (Driving) HTTP/REST adapter. Translates HTTP requests into `*UseCase` calls and maps `domain` / `application` results into JSON DTOs.
- **Strictly Forbidden**:
  * Never depend on the `persistence` module or import `jakarta.persistence.*` / `*Entity` / `*JpaRepository`.
  * Never inject concrete `*Interactor` classes into controllers (always inject `*UseCase` interfaces).
  * Never expose `domain` classes directly as `@RequestBody` or controller return types.
- **Maven Compiler Requirement**: Ensure `org.apache.maven.plugins:maven-compiler-plugin` is declared in `web/pom.xml` `<build><plugins>` so MapStruct generates the `@Mapper` implementations at compile time.

---

## 2. Kera Web Public API (`fr.fpage.kera.web`)

> **Do NOT run `javap` on `kera-spring-web.jar`** — here is the complete public API of the module:

```java
package fr.fpage.kera.web;

import org.mapstruct.MapperConfig;
import org.mapstruct.MappingConstants;

@MapperConfig(componentModel = MappingConstants.ComponentModel.SPRING)
public interface WebMapperConfig {
}
```

---

## 3. Package Structure & Components

1. **`controllers/`**:
   * `Constants.java`: Centralizes route path constants (`API = "/api"`, resource paths like `RECIPES = "/recipes"`).
   * `*Controller.java`: Annotated with `@RestController` and `@RequestMapping(API + RESOURCE)`. Injects only `*UseCase` interfaces and `*DomainMapper` interfaces via constructor.
   * **Transaction Boundary (`@Transactional`)**: Because `application` is pure Java and `persistence` ports do not manage transaction boundaries, **the driving controller is responsible for declaring `@Transactional`** (`@Transactional` on write endpoints `POST`/`PUT`/`DELETE`/`PATCH`, and `@Transactional(readOnly = true)` on read endpoints `GET` when lazy collections or multi-port reads are involved).
2. **`responses/` & `requests/`**:
   * Always use immutable Java **`record`** types for HTTP payloads (e.g., `SimpleRecipeResponse`, `RecipeDetailResponse`, `CreateIngredientRequest`).
   * **Input Validation (`jakarta.validation.constraints.*`)**: Annotate `*Request` record components with standard Jakarta Bean Validation annotations (`@NotBlank`, `@NotNull`, `@Positive`, `@Size`, `@Valid`) and annotate `@RequestBody` parameters in controllers with `@Valid` (`@Valid @RequestBody CreateIngredientRequest request`).
   * Only include fields required by the API contract (do not leak internal `version`, `createdAt`, `updatedAt` unless explicitly needed by the endpoint).
3. **`mappers/`**:
   * MapStruct interfaces annotated with `@Mapper(config = WebMapperConfig.class)` (`fr.fpage.kera.web.WebMapperConfig`).
   * Convert `Domain` objects (or `application` `*UseCase.Result` records) to `*Response` records, and `*Request` records to `Domain` objects (or `*UseCase.Query` / `Command` records).

---

## 3. Canonical Code Templates

### 3.1. Request & Response DTOs (`CreateIngredientRequest.java`, `SimpleRecipeResponse.java`)
```java
package com.example.app.requests;

import jakarta.validation.constraints.NotBlank;

public record CreateIngredientRequest(@NotBlank String name) {
}
```
```java
package com.example.app.responses;

import java.util.UUID;

public record SimpleRecipeResponse(UUID id, String name) {
}
```

### 3.2. Web MapStruct Mapper (`RecipeDomainMapper.java`)
```java
package com.example.app.mappers;

import fr.fpage.kera.web.WebMapperConfig;
import com.example.app.domain.Recipe;
import com.example.app.responses.SimpleRecipeResponse;
import org.mapstruct.Mapper;

@Mapper(config = WebMapperConfig.class)
public interface RecipeDomainMapper {
    SimpleRecipeResponse toSimpleResponse(Recipe domain);
}
```

### 3.3. Route Constants (`Constants.java`)
```java
package com.example.app.controllers;

public class Constants {
    public static final String API = "/api";
    public static final String RECIPES = "/recipes";
}
```

### 3.4. REST Controller (`RecipeController.java`)
```java
package com.example.app.controllers;

import com.example.app.mappers.RecipeDomainMapper;
import com.example.app.responses.SimpleRecipeResponse;
import com.example.app.usecases.GetAllRecipesUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.example.app.controllers.Constants.API;
import static com.example.app.controllers.Constants.RECIPES;

@RestController
@RequestMapping(API + RECIPES)
public class RecipeController {
    private final GetAllRecipesUseCase getAllRecipesUseCase;
    private final RecipeDomainMapper recipeDomainMapper;

    public RecipeController(GetAllRecipesUseCase getAllRecipesUseCase, RecipeDomainMapper recipeDomainMapper) {
        this.getAllRecipesUseCase = getAllRecipesUseCase;
        this.recipeDomainMapper = recipeDomainMapper;
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @Transactional(readOnly = true)
    public List<SimpleRecipeResponse> getAllRecipes() {
        return this.getAllRecipesUseCase.execute().stream()
                .map(recipeDomainMapper::toSimpleResponse)
                .toList();
    }
}
```

---

## 4. Layer Checklist (`DO` / `DON'T`)
- ✅ **DO** use `@Mapper(config = WebMapperConfig.class)` on every Web mapper so it is registered as a Spring `@Component`.
- ✅ **DO** use Java `record` for all `*Request` and `*Response` DTOs.
- ✅ **DO** validate incoming `*Request` records using `jakarta.validation.constraints.*` (`@NotBlank`, `@NotNull`, `@Positive`) and `@Valid @RequestBody` on controller parameters.
- ✅ **DO** inject `*UseCase` interfaces (never `*Interactor` classes) into controllers.
- ✅ **DO** place `@Transactional` (or `@Transactional(readOnly = true)`) on controller methods to define the transaction boundary of the request.
- ❌ **DON'T** put business logic or calculations inside a controller or Web mapper; delegate all business rules to the `application` / `domain` layers.
- ❌ **DON'T** return `domain` objects directly from `@RestController` methods.
