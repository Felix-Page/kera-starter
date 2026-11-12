# Layer 5 — Bootstrap Module (`bootstrap`)

## 1. Role & Dependency Rules
- **Maven Module**: `bootstrap` (depends on `fr.fpage.kera:kera-spring-bootstrap`, `application`, `persistence`, `web`, and the runtime JDBC driver `org.postgresql:postgresql`).
- **Role**: **The Composition Root**. Assembles all modules, registers `application` POJO Interactors as Spring `@Bean`s, and holds `application.yml` (while Liquibase SQL changelogs reside in `persistence/src/main/resources/db/changelog/`).

---

## 2. Spring Boot Entry Point (`Bootstrap.java`)

Because `kera-spring-persistence` registers its components (`JpaEntityResolver`) via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, `@SpringBootApplication` does **not** need custom `scanBasePackages`:

```java
package com.example.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Bootstrap {
    static void main(String[] args) {
        SpringApplication.run(Bootstrap.class, args);
    }
}
```

---

## 3. Wiring UseCases (`config/InteractorConfig.java`)

Since `*Interactor` classes in `application` are pure Java POJOs without `@Service`, instantiate them as `@Bean` methods in `InteractorConfig`:

1. **Return Type**: Always return the primary port interface (`*UseCase`), never the concrete `*Interactor` class.
2. **Method Parameters**: Always inject the secondary port interface (`GenericPort<UUID, MyDomain>` or custom `*Port`), **never** the concrete `*PersistenceAdapter` class.

```java
package com.example.app.config;

import fr.fpage.kera.application.GenericPort;
import com.example.app.domain.Recipe;
import com.example.app.interactors.GetAllRecipesInteractor;
import com.example.app.usecases.GetAllRecipesUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class InteractorConfig {
    @Bean
    public GetAllRecipesUseCase getAllRecipesUseCase(GenericPort<UUID, Recipe> recipePersistenceAdapter) {
        return new GetAllRecipesInteractor(recipePersistenceAdapter);
    }
}
```

---

## 4. Layer Checklist (`DO` / `DON'T`)
- ✅ **DO** register every new `*UseCase` / `*Interactor` pair in `InteractorConfig`.
- ✅ **DO** inject `GenericPort<ID, DOMAIN>` (or custom `*Port`) as the parameter type in `InteractorConfig` `@Bean` methods.
- ❌ **DON'T** inject concrete `*PersistenceAdapter` classes in `InteractorConfig`.
- ❌ **DON'T** change `ddl-auto: validate` to `update` or `create-drop`.
