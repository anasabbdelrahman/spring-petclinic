# Trust calibration - Spring PetClinic, 2026-09-24

This document says how far agent-produced changes can be trusted in each area of the repository,
and what verification each area needs before a change lands.

Every file reference is a full path relative to the repository root. Every referenced file was
checked with `git ls-files` on 2026-09-24, and every file:line citation was read on the same date.
A test class named in prose by class name alone is the file of that name listed in the same row's
"What is automated" column.

## Decision tree

1. Does the change touch authentication, authorization, access control, production data or
   schema, PII or other sensitive data, security controls, or audit logging? -> **HIGH**
2. Otherwise, does it touch revenue-affecting business rules, APIs or external services, user
   input, or reliability mechanisms? -> **MEDIUM**
3. Otherwise -> **LOW**. Reconsider widely used utilities and UI components that accept input;
   they may belong in MEDIUM.

## Reviewers and automation

This is a single-maintainer repository. "Human" below means `<repository owner>`. The agent
reviewers are `/review-changes` (it sees the staged diff only) and `/security-review`.

Automation available in the repository:

- JUnit suites run by `./gradlew build` and `./mvnw verify`.
- spring-javaformat.
- checkstyle.
- nohttp.
- `I18nPropertiesSyncTest`
  (`src/test/java/org/springframework/samples/petclinic/system/I18nPropertiesSyncTest.java`).
- detect-secrets as a pre-commit hook (`.pre-commit-config.yaml:5`).
- The `.claude/hooks/guard-git.sh` PreToolUse hook.

Each row names only the automation that actually covers that row. A general build pass is not
evidence for an area that no test exercises.

## Area map

| Code area and files | Tier | Reason | Verification commitment | Who reviews | What is automated |
|---|---|---|---|---|---|
| **Owner personal data** - `src/main/java/org/springframework/samples/petclinic/owner/Owner.java`, `src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java`, `src/main/java/org/springframework/samples/petclinic/owner/OwnerRepository.java`, `src/main/java/org/springframework/samples/petclinic/model/Person.java` | HIGH | PII: names, address, city, telephone. No authentication or authorization exists, so every owner record can be read and edited by anyone. Binding relies on a denylist (`src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java:58-59`, `setDisallowedFields("id", "*.id")`), and CLAUDE.md's security requirements call for an allowlist. | Before any change: tests first; the focused owner tests plus `ClinicServiceTests`; a full `./gradlew build`. Any binding or query change needs a security regression test. Never log field values. | Human review required, plus `/security-review` and `/review-changes`. | `src/test/java/org/springframework/samples/petclinic/owner/OwnerControllerTests.java`, `src/test/java/org/springframework/samples/petclinic/owner/OwnerNotFoundIntegrationTests.java`, `src/test/java/org/springframework/samples/petclinic/owner/OwnerTests.java`, `src/test/java/org/springframework/samples/petclinic/service/ClinicServiceTests.java`; format, checkstyle. Nothing automated checks access control, because none exists. |
| **Schema and datasource configuration** - `src/main/resources/db/h2/schema.sql`, `src/main/resources/db/mysql/schema.sql`, `src/main/resources/db/postgres/schema.sql`, `src/main/resources/db/mysql/user.sql`, `src/main/resources/application-mysql.properties`, `src/main/resources/application-postgres.properties` | HIGH | Production data and schema. `spring.jpa.hibernate.ddl-auto=none`, so the SQL scripts are the schema. The datasource password is read from an environment variable (`MYSQL_PASS` / `POSTGRES_PASS`) but has a non-empty literal fallback default (`src/main/resources/application-mysql.properties:5`, `src/main/resources/application-postgres.properties:5`), and `src/main/resources/db/mysql/user.sql:7` creates a user with a literal password. Values are deliberately not reproduced here. | Change all three schema dialects together. Run `ClinicServiceTests` on H2, and run the Docker-backed `MySqlIntegrationTests` / `PostgresIntegrationTests` whenever a dialect changes. Never add or change a credential literal; run detect-secrets and inspect any baseline change. | Human review required, plus `/security-review`. | `src/test/java/org/springframework/samples/petclinic/service/ClinicServiceTests.java` (H2 schema only) and detect-secrets pre-commit. `src/test/java/org/springframework/samples/petclinic/MySqlIntegrationTests.java` and `src/test/java/org/springframework/samples/petclinic/PostgresIntegrationTests.java` exist but are outside the default loop because they need Docker. |
| **Actuator exposure** - `src/main/resources/application.properties:21` (`management.endpoints.web.exposure.include=*`); actuator is a dependency in `build.gradle:41` (`runtimeOnly`) and `pom.xml:44` | HIGH | A security control. All available web actuator endpoints are configured for exposure, with no authentication, against the CLAUDE.md requirement to keep management endpoints restricted. | Any change is checked by starting the app and requesting the endpoints over HTTP, recording each status. A green build proves nothing here. A restriction lands with a regression test that pins it. | Human review required, plus `/security-review`. | **None.** No test asserts which endpoints are exposed. |
| **Agent security controls** - `.claude/hooks/guard-git.sh`, `.claude/settings.json`, `.pre-commit-config.yaml`, `.secrets.baseline` | HIGH | Security controls over what the agent, and commits, can do. A mistake here silently removes a guard. | Re-run the hook's PreToolUse JSON cases (recorded in `guard-hook-output-2026-09.txt`) and check the exit status of each. Run `bash -n` and `jq empty`. Inspect every `.secrets.baseline` diff by hand. Runtime permission-rule matching is still **untested** (see `failure-patterns-2026-09.md`), so treat claims about it as unverified. | Human review required, plus `/review-changes` over all staged files together. | detect-secrets pre-commit. `.claude/hooks/guard-git.sh` blocks the destructive Git forms it matches, but its test cases are run by hand, not in CI. Nothing tests permission-rule matching. |
| **Vet pagination input and JSON API** - `src/main/java/org/springframework/samples/petclinic/vet/VetController.java`, `src/main/java/org/springframework/samples/petclinic/vet/VetPageRequests.java`, `src/main/java/org/springframework/samples/petclinic/vet/InvalidVetPageException.java` | MEDIUM | User input (`page`) and an API (the `/vets` JSON). The lower bound was fixed by the D1 migration, and the upper bound is still open (CLAUDE.md ledger, section 6). | Follow the CLAUDE.md migration ledger. Contract tests stay frozen. Run the focused vet tests, `OwnerNotFoundIntegrationTests` (it pins `GET /owners?page=0` to 500; file `src/test/java/org/springframework/samples/petclinic/owner/OwnerNotFoundIntegrationTests.java`), and the full build. | `/review-changes`; human review for any contract change. | `src/test/java/org/springframework/samples/petclinic/vet/VetPaginationContractTests.java`, `src/test/java/org/springframework/samples/petclinic/vet/VetPaginationCharacterizationTests.java`, `src/test/java/org/springframework/samples/petclinic/vet/VetPageRequestsTests.java`, `src/test/java/org/springframework/samples/petclinic/vet/VetControllerTests.java`; format, checkstyle. |
| **Pet and visit form input and validation** - `src/main/java/org/springframework/samples/petclinic/owner/PetController.java`, `src/main/java/org/springframework/samples/petclinic/owner/PetValidator.java`, `src/main/java/org/springframework/samples/petclinic/owner/PetTypeFormatter.java`, `src/main/java/org/springframework/samples/petclinic/owner/VisitController.java`, `src/main/java/org/springframework/samples/petclinic/owner/VisitDateValidator.java` | MEDIUM | User input, custom validation and type conversion, with date boundaries (a visit date must be strictly after its reference date). Writes are persisted through the `Owner` aggregate. | Tests first. Test boundary values plus one step either side, null inputs, and deterministic literal dates (never `LocalDate.now()` in logic under test). Run the focused tests and the full build. | `/review-changes`; human review for any validation-rule change. | `src/test/java/org/springframework/samples/petclinic/owner/PetControllerTests.java`, `src/test/java/org/springframework/samples/petclinic/owner/PetValidatorTests.java`, `src/test/java/org/springframework/samples/petclinic/owner/PetTypeFormatterTests.java`, `src/test/java/org/springframework/samples/petclinic/owner/VisitControllerTests.java`, `src/test/java/org/springframework/samples/petclinic/owner/VisitDateValidatorTests.java`; format, checkstyle. |
| **Vet cache** - `src/main/java/org/springframework/samples/petclinic/system/CacheConfiguration.java`; `@Cacheable("vets")` at `src/main/java/org/springframework/samples/petclinic/vet/VetRepository.java:45` and `:55` | MEDIUM | A reliability mechanism. The CLAUDE.md ledger records, as INFERRED, that the cache may be inert: Caffeine is `runtimeOnly` (`build.gradle:45`) and no JSR-107 provider is declared. | Do not claim the cache works, or does not work, without a test that observes a cache hit. Any change comes with such a test. | `/review-changes`; human review. | **None specific.** No test observes caching behaviour. |
| **Shared form fragments and error page** - `src/main/resources/templates/fragments/inputField.html`, `src/main/resources/templates/fragments/selectField.html`, `src/main/resources/templates/error.html` | MEDIUM (raised from LOW) | Shared UI components that accept input, used by every form. The fragments use escaped `th:field` / `th:text`. `src/main/resources/templates/error.html:18` renders `${message}`, which can echo exception text (it happens under devtools, per the CLAUDE.md ledger). | Load the rendered pages in a browser and read the console; a green build proves non-regression only. Keep output escaped and never use `th:utext`. | `/review-changes`; `/security-review` for any output-encoding change. | Rendered indirectly by the controller and integration tests, e.g. `src/test/java/org/springframework/samples/petclinic/system/CrashControllerIntegrationTests.java` and `src/test/java/org/springframework/samples/petclinic/vet/VetPaginationCharacterizationTests.java` for `src/main/resources/templates/error.html`. `src/test/java/org/springframework/samples/petclinic/system/I18nPropertiesSyncTest.java` covers the message keys the fragments use. Nothing checks the rendered page itself. |
| **Static presentation** - `src/main/java/org/springframework/samples/petclinic/system/WelcomeController.java`, `src/main/resources/templates/welcome.html`, `src/main/resources/static/resources/css/petclinic.css`, `src/main/resources/banner.txt` | LOW | Nothing here takes input, stores data or enforces a control. The CSS is compiled output (`./mvnw package -P css`). | A green build, plus a visual check of the welcome page for template changes. | `/review-changes`. | `src/test/java/org/springframework/samples/petclinic/system/WelcomeControllerTests.java`; nohttp; format/checkstyle for the Java file. |

## Summary

- HIGH: 4 areas - owner PII, schema and datasource, actuator exposure, agent security controls.
- MEDIUM: 4 areas - vet pagination, pet and visit forms, cache, shared fragments and error page.
- LOW: 1 area - static presentation.

Two HIGH areas have **no automated coverage of the property that makes them HIGH**: actuator
exposure, and access control for owner PII, which does not exist. A green build is not evidence
for either one.
