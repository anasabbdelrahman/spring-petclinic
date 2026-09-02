# CLAUDE.md Verification Notes — 2026–09

## Auto-memory review

Auto-memory was enabled, but the folder was empty because this was a newly cloned repository with no previous Claude sessions. There was therefore no overlap between auto-memory and `CLAUDE.md`.

## Verification question

I need to add a new owner-facing endpoint and its tests. Which package should contain the production code, where should the tests go, what test-class naming pattern should I follow, and what Gradle command runs only that test? Answer only; do not edit any files.

## Claude’s answer

```text
Production code: org.springframework.samples.petclinic.owner — i.e. src/main/java/org/springframework/samples/petclinic/owner/. The project is organized by domain feature, not layer, so the entity, repository, and controller for owners all live together there. Route persistence through the existing OwnerRepository (Owner is the aggregate root for Pet/Visit; there's deliberately no separate PetRepository/VisitRepository for writes).

Tests: mirror the production package under src/test/java/org/springframework/samples/petclinic/owner/.

Naming: *Tests suffix — e.g. OwnerControllerTests. For a controller endpoint, follow the existing @WebMvcTest-style pattern that mocks the repository layer; if you need real JPA behavior, that's the ClinicServiceTests-against-H2 pattern instead.

Gradle, single test:
./gradlew test --tests "org.springframework.samples.petclinic.owner.OwnerControllerTests"

Single method:
./gradlew test --tests "org.springframework.samples.petclinic.owner.OwnerControllerTests.testInitCreationForm"

Also worth noting: if the endpoint adds any new UI strings, add the keys to src/main/resources/messages/messages.properties and keep all 11 locale files in sync — I18nPropertiesSyncTest fails the build otherwise. And run ./mvnw spring-javaformat:apply before committing, since spring-javaformat/checkstyle run as part of the build.
```

## Verification result

PASS: Claude correctly identified the owner production package, matching test package, `*Tests` naming convention, controller-testing pattern, and fully qualified Gradle command for running the affected test.

## What `/init` missed

`/init` missed the repository’s EditorConfig indentation rules and did not explicitly document the matching test-package structure or the `*Tests` naming convention.

## External verification

Command:

```bash
./gradlew test --tests "org.springframework.samples.petclinic.owner.OwnerControllerTests"
```

Result:

```text
BUILD SUCCESSFUL in 8s
9 actionable tasks: 1 executed, 8 up-to-date
```

The documented Gradle command was verified successfully against the repository.
