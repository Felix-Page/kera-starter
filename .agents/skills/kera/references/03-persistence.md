# Layer 3 — Persistence Module (`persistence`)

## 1. Role & Dependency Rules
- **Maven Module**: `persistence` (depends on `fr.fpage.kera:kera-spring-persistence`, `domain`, and `application` only when implementing a custom `*Port`).
- **Role**: Secondary (Driven) JPA/Hibernate adapter. Maps `domain` models to/from `@Entity` classes and persists them via Spring Data JPA.
- **Maven Compiler Requirement**: Ensure `org.apache.maven.plugins:maven-compiler-plugin` is declared in `persistence/pom.xml` `<build><plugins>` so MapStruct generates `*EntityMapperImpl`.

---

## 2. Kera Persistence API Signatures (No need to run `javap`)

Here is the complete public API provided by `fr.fpage.kera.persistence`:

```java
package fr.fpage.kera.persistence.entity;

public interface ReferenceableEntity {}

public interface IdentifiableEntity<ID> {
    ID getId();
    void setId(ID id);
}

public interface AuditableEntity<ID> extends IdentifiableEntity<ID> {
    LocalDateTime getCreatedAt();
    void setCreatedAt(LocalDateTime createdAt);
    LocalDateTime getUpdatedAt();
    void setUpdatedAt(LocalDateTime updatedAt);
}

public interface VersionedEntity<ID> extends AuditableEntity<ID> {
    Long getVersion();
    void setVersion(Long version);
}

package fr.fpage.kera.persistence;
@MapperConfig(componentModel = MappingConstants.ComponentModel.SPRING, uses = JpaEntityResolver.class)
public interface PersistenceMapperConfig {}

public interface AbstractEntityMapper<ID, ENTITY extends IdentifiableEntity<ID> & ReferenceableEntity, DOMAIN extends IdentifiableDomain<ID>> {
    DOMAIN toDomain(ENTITY entity);
    List<DOMAIN> toDomain(List<ENTITY> entities);
    ENTITY toEntity(DOMAIN domain);
}

public abstract class GenericPortImpl<ID, DOMAIN extends IdentifiableDomain<ID>, ENTITY extends IdentifiableEntity<ID> & ReferenceableEntity, REPOSITORY extends JpaRepository<ENTITY, ID>, MAPPER extends AbstractEntityMapper<ID, ENTITY, DOMAIN>> implements GenericPort<ID, DOMAIN> {
    protected final REPOSITORY repository;
    protected final MAPPER mapper;
    public GenericPortImpl(REPOSITORY repository, MAPPER mapper) { ... }
    // Implements: findById(ID), findAll(), findAllById(Collection<ID>), save(DOMAIN), saveAll(Collection<DOMAIN>), delete(DOMAIN), deleteById(ID)
}
```

---

## 3. Kera Entity Contracts, `AbstractEntity` & `ReferenceableEntity` Rule (CRITICAL)

### 3.1. Interface Contracts (`fr.fpage.kera.persistence.entity`) & Project `@MappedSuperclass`
In `kera-spring-persistence`, `IdentifiableEntity<ID>`, `AuditableEntity<ID>`, and `VersionedEntity<ID>` are **interfaces** (not abstract classes).
* **Why interfaces?** Defining `@Id` on a generic `ID` field inside a library `@MappedSuperclass` prevents projects from configuring custom JPA/Hibernate ID generators (`@GeneratedValue(strategy = GenerationType.UUID)`, `@SequenceGenerator`, `@IdGeneratorType`, etc.).
* **Project Convention**: The application's `persistence` module defines its own `@MappedSuperclass` (e.g., `AbstractEntity implements VersionedEntity<UUID>`) holding `@Id`, its chosen `@GeneratedValue` / custom ID generator strategy, `@Version`, `@CreationTimestamp`, and `@UpdateTimestamp`.
* Every JPA entity must implement (directly or via `AbstractEntity`) the exact counterpart of its `domain` class:
  - `IdentifiableEntity<ID>` $\leftrightarrow$ `IdentifiableDomain<ID>`
  - `AuditableEntity<ID>` $\leftrightarrow$ `AuditableDomain<ID>`
  - `VersionedEntity<ID>` $\leftrightarrow$ `VersionedDomain<ID>`

### 3.2. Autonomous Entities vs Child Entities (`ReferenceableEntity`)
Kera uses `JpaEntityResolver` (`entityManager.getReference`) configured in `PersistenceMapperConfig`. To distinguish between `@ManyToOne` references and `@OneToMany` child compositions **without writing any `@Mapping` annotations**:

| Entity Kind | Implements `ReferenceableEntity`? | MapStruct & Hibernate Behavior | Examples |
| :--- | :---: | :--- | :--- |
| **Autonomous Entity (Aggregate Root)** | ✅ **YES** (`implements ReferenceableEntity`) | Required by `AbstractEntityMapper` & `GenericPortImpl`. When referenced inside another entity (`@ManyToOne`), `JpaEntityResolver` resolves it via `entityManager.getReference(id)` (no extra `SELECT`). | `RecipeEntity`, `IngredientEntity` |
| **Child Sub-Entity (`@OneToMany` cascade)** | ❌ **NO** | Ignored by `JpaEntityResolver`. MapStruct automatically generates full field-by-field mapping (`id`, `version`, and business columns) so Hibernate `CascadeType.ALL` inserts/updates child rows. | `RecipeIngredientEntity`, `RecipeStepEntity` |

---

## 4. Canonical Code Templates

### 4.1. Project `@MappedSuperclass` (`AbstractEntity.java`)
```java
package com.example.app.entities;

import fr.fpage.kera.persistence.entity.VersionedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@MappedSuperclass
public abstract class AbstractEntity implements VersionedEntity<UUID> {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    protected UUID id;
    @Version
    protected Long version;
    @CreationTimestamp
    protected LocalDateTime createdAt;
    @UpdateTimestamp
    protected LocalDateTime updatedAt;

    @Override
    public UUID getId() { return id; }
    @Override
    public void setId(UUID id) { this.id = id; }
    @Override
    public Long getVersion() { return version; }
    @Override
    public void setVersion(Long version) { this.version = version; }
    @Override
    public LocalDateTime getCreatedAt() { return createdAt; }
    @Override
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    @Override
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    @Override
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

### 4.2. Autonomous Aggregate Root Entity (`RecipeEntity.java`)
```java
package com.example.app.entities;

import fr.fpage.kera.persistence.entity.ReferenceableEntity;
import jakarta.persistence.*;

import java.util.List;

@Entity(name = "recipe")
public class RecipeEntity extends AbstractEntity implements ReferenceableEntity {
    @Column(nullable = false)
    private String name;

    @Column(nullable = false, check = @CheckConstraint(constraint = "servings > 0"))
    private int servings;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "recipe_id", nullable = false)
    private List<RecipeIngredientEntity> ingredients;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "recipe_id", nullable = false)
    @OrderBy("stepNumber ASC")
    private List<RecipeStepEntity> steps;

    public RecipeEntity() {
        super();
    }

    // Standard plain getters and setters (no business validation inside persistence setters)...
}
```

### 4.3. Child Sub-Entity with a `@ManyToOne` Reference (`RecipeIngredientEntity.java`)
*Notice: Extends `AbstractEntity` (`VersionedEntity<UUID>`) but does **NOT** implement `ReferenceableEntity`, while its `@ManyToOne IngredientEntity` **does** implement `ReferenceableEntity`.*
```java
package com.example.app.entities;

import com.example.app.domain.Unit;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name = "recipe_ingredient")
public class RecipeIngredientEntity extends AbstractEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private IngredientEntity ingredient;

    @Column(nullable = false, check = @CheckConstraint(constraint = "quantity > 0"))
    private double quantity;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = Constants.UNIT_TYPE)
    private Unit unit;

    public RecipeIngredientEntity() {
        super();
    }

    // Standard plain getters and setters...
}
```

### 4.4. Spring Data Repository (`RecipeJpaRepository.java`)
```java
package com.example.app.repositories;

import com.example.app.entities.RecipeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RecipeJpaRepository extends JpaRepository<RecipeEntity, UUID> {
}
```

### 4.5. MapStruct Entity Mapper (`RecipeEntityMapper.java` — 3 lines, zero boilerplate)
```java
package com.example.app.mappers;

import fr.fpage.kera.persistence.AbstractEntityMapper;
import fr.fpage.kera.persistence.PersistenceMapperConfig;
import com.example.app.domain.Recipe;
import com.example.app.entities.RecipeEntity;
import org.mapstruct.Mapper;

import java.util.UUID;

@Mapper(config = PersistenceMapperConfig.class)
public interface RecipeEntityMapper extends AbstractEntityMapper<UUID, RecipeEntity, Recipe> {
}
```

### 4.6. Persistence Adapter (`RecipePersistenceAdapter.java` — 6 lines)
```java
package com.example.app;

import fr.fpage.kera.persistence.GenericPortImpl;
import com.example.app.domain.Recipe;
import com.example.app.entities.RecipeEntity;
import com.example.app.mappers.RecipeEntityMapper;
import com.example.app.repositories.RecipeJpaRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RecipePersistenceAdapter extends GenericPortImpl<UUID, Recipe, RecipeEntity, RecipeJpaRepository, RecipeEntityMapper> {
    public RecipePersistenceAdapter(RecipeJpaRepository repository, RecipeEntityMapper mapper) {
        super(repository, mapper);
    }
}
```

---

## 5. Liquibase Migrations & Schema Validation (`ddl-auto: validate`)

Hibernate is configured with `spring.jpa.hibernate.ddl-auto: validate`. Any `@Entity` addition or modification **must** be accompanied by a Liquibase SQL migration inside `persistence/src/main/resources/db/changelog/`.

### 5.1. Changelog Directory Structure (`persistence/src/main/resources/db/changelog/`)
```text
db/changelog/
├── db.changelog-master.yaml
└── 0.0.1/
    ├── db-changelog-v0.0.1.yaml
    ├── schema/
    │   ├── create_type_unit.sql
    │   ├── create_table_ingredient.sql
    │   ├── create_table_recipe.sql
    │   ├── create_table_recipe_ingredient.sql
    │   └── create_table_recipe_step.sql
    └── data/
        └── ...
```

### 5.2. SQL Table Template for `VersionedEntity<UUID>`
Every table backing a `VersionedEntity<UUID>` **must** declare `id`, `created_at`, `updated_at`, and `version` with these exact types:

```sql
CREATE TABLE recipe
(
    id         UUID PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP          DEFAULT CURRENT_TIMESTAMP,
    version    BIGINT    NOT NULL,
    name       TEXT      NOT NULL,
    servings   INTEGER   NOT NULL
);
```
- For `AuditableEntity<UUID>`: include `id`, `created_at`, `updated_at` (omit `version`).
- For `IdentifiableEntity<UUID>`: include `id` (omit `created_at`, `updated_at`, `version`).

---

## 6. Layer Checklist (`DO` / `DON'T`)
- ✅ **DO** implement `VersionedEntity<ID>` (or `AuditableEntity<ID>` / `IdentifiableEntity<ID>`) via a project `@MappedSuperclass` (such as `AbstractEntity`) that defines the `@Id` and its `@GeneratedValue` strategy.
- ✅ **DO** implement `ReferenceableEntity` on every autonomous entity (Aggregate Root or independent table referenced via `@ManyToOne`).
- ✅ **DO** omit `ReferenceableEntity` on child entities owned via `@OneToMany(cascade = CascadeType.ALL)`.
- ✅ **DO** provide a public no-arg constructor and plain getters/setters on all JPA `*Entity` classes, using `@Column(nullable = false, check = @CheckConstraint(...))` for DB-level constraints.
- ✅ **DO** create or update the Liquibase SQL script in `persistence/src/main/resources/db/changelog/` and register it in `db-changelog-v*.yaml` whenever adding or modifying a JPA `*Entity`.
- ❌ **DON'T** duplicate Java business validation inside JPA `*Entity` setters (business validation belongs in `domain` and input validation in `web`).
- ❌ **DON'T** put `@Transactional` on `*PersistenceAdapter` methods (transaction boundaries are controlled by the driving adapter in `web`).
- ❌ **DON'T** create mappers or repositories for internal `@OneToMany` child entities (`RecipeStepEntity`, `RecipeIngredientEntity`); they are saved and mapped through their parent Aggregate Root (`RecipeEntityMapper`).
