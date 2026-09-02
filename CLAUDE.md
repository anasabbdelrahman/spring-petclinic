# CLAUDE.md

## Overview

Spring PetClinic — a Spring Boot 4.1 / Java 17 web application (Thymeleaf server-rendered UI + Spring MVC + Spring Data JPA). The project builds with **both Maven and Gradle**; either works, and both are kept in sync. Use the wrappers (`./mvnw`, `./gradlew`).

## Common commands

Maven is the primary build; Gradle equivalents are shown where they differ.

```bash
# Run the app (H2 in-memory DB, http://localhost:8080)
./mvnw spring-boot:run          # or: ./gradlew bootRun

# Full build + tests
./mvnw verify                   # or: ./gradlew build

# Run all tests
./mvnw test                     # or: ./gradlew test

# Run a single test class / method (Maven)
./mvnw test -Dtest=OwnerControllerTests
./mvnw test -Dtest=OwnerControllerTests#testInitCreationForm
# Gradle equivalent
./gradlew test --tests "org.springframework.samples.petclinic.owner.OwnerControllerTests"
./gradlew test --tests "org.springframework.samples.petclinic.owner.OwnerControllerTests.testInitCreationForm"

# Recompile CSS after editing src/main/scss/*.scss (Maven only — no Gradle equivalent)
./mvnw package -P css

# Build a container image (no Dockerfile in repo; uses Spring Boot buildpacks)
./mvnw spring-boot:build-image
```

## Code style

Follow `.editorconfig`:

- Use LF line endings and include a final newline.
- Java and XML files use tabs with width 4 and no trailing whitespace.
- HTML, SQL and LESS files use two-space indentation.
- Gradle files use two-space indentation.
- Tests mirror the production package structure under `src/test/java`.
- Test classes use the `*Tests` suffix.

## Formatting & checks

Code style is enforced by **spring-javaformat** and **checkstyle** (config in `src/checkstyle/`), plus **nohttp** (no plain `http://` URLs in sources). These run during the build and will fail it on violation.

```bash
./mvnw spring-javaformat:apply   # auto-format before committing
```

Gradle wires `checkstyleMain`/`checkstyleNohttp` into the `format` tasks; AOT format/checkstyle tasks are explicitly disabled in `build.gradle`.

## Architecture

Source is organized **by domain feature**, not by technical layer. Each package under `org.springframework.samples.petclinic` holds its own entities, JPA repository, and MVC controller together:

- `owner/` — the richest aggregate. `Owner` is the aggregate root and owns its `Pet`s and each pet's `Visit`s (all persisted via `OwnerRepository` — there is intentionally **no** separate PetRepository/VisitRepository for writes). `OwnerController`, `PetController`, `VisitController` all route through `OwnerRepository`. `PetTypeFormatter` converts between `PetType` and its string form for form binding; `PetValidator` does custom validation.
- `vet/` — `Vet`, `Specialty`, `VetRepository`. `VetController` serves both an HTML paginated list (`/vets.html`) and a JSON endpoint. Vet queries are cached (see `system/CacheConfiguration`, JCache + Caffeine, cache name `vets`).
- `model/` — shared MappedSuperclasses: `BaseEntity` (id), `NamedEntity` (name), `Person` (first/last name).
- `system/` — cross-cutting config: `CacheConfiguration`, `WebConfiguration` (locale), `WelcomeController`, and `CrashController` (deliberately throws, to demo error handling at `error.html`).

`PetClinicRuntimeHints` registers reflection/resource hints for GraalVM native-image builds — update it if you add resources that native builds must see.

## Data & profiles

- Default profile uses **H2 in-memory**, seeded at startup from `src/main/resources/db/h2/{schema,data}.sql`. H2 console at `/h2-console` (JDBC URL with the random UUID is printed to the console at startup).
- Switch DB by profile: `spring.profiles.active=mysql` or `postgres`. The `database` property in `application.properties` selects which `db/<database>/` SQL scripts load. Profile-specific settings live in `application-mysql.properties` / `application-postgres.properties`. `docker-compose.yml` provides `mysql` and `postgres` services named after the profiles.
- `spring.jpa.hibernate.ddl-auto=none` — schema comes from the SQL scripts, not Hibernate DDL.

## Testing notes

- Prefer running the `main()` methods in `PetClinicIntegrationTests` (H2 + Devtools), `MysqlTestApplication`, or `PostgresIntegrationTests` for fast local app startup during development.
- `MySqlIntegrationTests` uses **Testcontainers**; `PostgresIntegrationTests` uses **Docker Compose** — both require a running Docker daemon.
- `I18nPropertiesSyncTest` verifies the `messages_*.properties` translation files stay in sync — update all locale files when changing message keys.
- `@WebMvcTest`-style controller tests (e.g. `OwnerControllerTests`) mock the repository layer; `ClinicServiceTests` exercises the JPA layer against H2.

## Internationalization

UI strings live in `src/main/resources/messages/messages*.properties` (11 locales). Add new keys to the base `messages.properties` and keep locale files in sync (enforced by `I18nPropertiesSyncTest`).
