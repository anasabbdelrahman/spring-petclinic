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

## Test plan

- **Scope and risk** — Keep a change inside the feature package it belongs to (`owner/`, `vet/`, `system/`). The recurring risk areas repo-wide are custom validation, form binding and type conversion, persistence through the `Owner` aggregate, internationalized message keys, and date/time boundaries — for example, a visit date must be strictly after its reference date. Cover the risk areas a change actually touches.
- **Test types and levels** — Plain JUnit, no Spring context, for pure logic (see `OwnerTests`); `@WebMvcTest` with a mocked repository for controllers (`OwnerControllerTests`); JPA against H2 (`ClinicServiceTests`); Docker-backed integration tests (`MySqlIntegrationTests`, `PostgresIntegrationTests`) — outside the default loop. Test pure logic at the unit level; add a controller test only for the wiring, not to re-prove the rule.
- **Runner commands** — focused on every red/green step, full suite and formatting as the gates below require:

```bash
./gradlew test --tests "org.springframework.samples.petclinic.owner.<Class>Tests"   # focused
./gradlew build                                                                     # full suite + checks
./mvnw spring-javaformat:apply                                                      # formatting
```

- **Required case coverage** — the happy path; every boundary value plus one step either side of it; null and absent inputs, with the intended outcome stated up front; and independence from the wall clock (a rule driven by an explicit reference date must behave the same for dates in the real-world past and future).
- **Testing rules** — Strict tests-first TDD: no production code until a test demands it, and the test that drove an implementation is never edited to accommodate it. Dates must be deterministic — never call `LocalDate.now()` inside logic under test or in an assertion; pass a reference date and use literal dates in fixtures. One behavior per test; `*Tests` suffix; mirror the production package.
- **Red-phase gate** — format the new test; run the focused test; confirm it fails for the expected reason; capture the failure output; `git diff --check` clean; commit the failing test alone. The full build is **not** required to pass during this intentional red phase.
- **Green-phase gate** — do not modify the committed tests; run the focused test and the relevant controller tests; run formatting; confirm formatting did not alter the committed tests; run the full build; `git diff --check` clean; commit the implementation separately. Then do a fresh-session overfitting review.

## Internationalization

UI strings live in `src/main/resources/messages/messages*.properties` (11 locales). Add new keys to the base `messages.properties` and keep locale files in sync (enforced by `I18nPropertiesSyncTest`).

## Brownfield migration ledger - vet pagination contract (PAID D1)

Additive to everything above: the build commands, code style, architecture, data/profile and
testing notes all still apply unchanged. Read this section on every session start, and run the
staleness check at the end of section 4 before proposing work.

### 1. Migration header

- **Area:** `org.springframework.samples.petclinic.vet` - the `page` contract of `GET /vets.html`
- **PAID item:** D1, unvalidated `page` -> HTTP 500 on `page <= 0`
  (`paid-grid-vet-2026-09.md:33, 141-153`)
- **Engagement option:** Refactor. Wrap rejected - the only wrapper that removes the 500 without
  deciding the contract is an `@ExceptionHandler(IllegalArgumentException.class)`, which silently
  reclassifies `GET /owners?page=0` (`four-phase-evaluation-2026-09.md:46-51`). Rewrite rejected -
  33-60 h rebuild estimate to add one bound. Delete rejected - `layout.html:51` links the endpoint.
  Tolerate rejected - 5xx from an ordinary URL, and the TDR > 5% note requires scheduled work.
- **Pattern:** Branch-by-Abstraction. **Fit is partial and stays recorded as partial.** The
  pattern's own non-fit clause is "the piece is small enough that in-place refactoring with the
  4-phase loop suffices", and this piece is two lines. Iterations 2 and 3 exist because the
  pattern's phase boundaries require them, not because the code demands them; a reviewer may
  fairly call that ceremony. Strongest rejected: Expand/Contract - the change is an API-shape
  change, which is E/C's home territory, but clients deploy atomically in a single-binary
  application and the one in-repo `page` client (`vetList.html:26-54`) ships in the same jar.
  Strangler Fig, Anti-Corruption Layer and Parallel Run are rejected in
  `acceptance-2026-09.md` section 2.
- **Started:** 2026-09-22
- **Acceptance criterion:** frozen by the setup commit, before any test or production code.
  Default H2 profile, six seeded vets (`db/h2/data.sql:1-6`), page size 5. `GET /vets.html`
  answers `?page=0` -> 400, `?page=-1` -> 400, absent -> 200, `?page=1` -> 200, `?page=2` -> 200,
  `?page=999` -> 200 with zero rows, `?page=abc` -> 400 unchanged; `GET /owners?page=0` stays
  500; `GET /vets` JSON shape unchanged; no file under `src/main/resources/messages/` modified.
  **The body text of the 400 page is outside the contract.** The full table is
  `acceptance-2026-09.md` section 1, which is never edited in place - amendments go to its
  section 7 with a date and a reason.
- **Kill-switch conditions:** (1) Iteration 1 cannot pin `?page=0` to 500 at any test level - the
  premise is falsified, re-open the PAID assessment rather than proceed; (2) reaching the
  criterion would need an `@ExceptionHandler`, a `@ControllerAdvice`, or any edit to
  `OwnerController`; (3) any iteration needs a second corrective commit to go green; (4) any
  iteration needs to edit `src/main/resources/messages/`; (5) four working sessions elapse
  without Iteration 4 landing.
- **Owner:** Anas Abdelrahman. Single-maintainer repository; no on-call rotation exists.
- **Rollback rule:** one iteration = one commit = `git revert <sha>`. Iteration 1's
  characterization commit is the named rollback point. No runtime toggle exists, deliberately: a
  property gate for a two-line change is an abstraction nobody asked for, and there is no
  deployment to flip without a build.
- **Recorded limitations, not blockers:** no access log, error-rate series or incident data exists
  for this module - every bug-burden input in the PAID grid is the unmeasured `1*`
  (`paid-grid-vet-2026-09.md:43-46, 410-411`), so this migration proceeds on contract correctness
  rather than on measured harm. The teammate-review entry in `architecture-vet-2026-09.md:141-145`
  is still pending.

### 2. Pathology inventory

- **Unvalidated framework-boundary parameter:** `@RequestParam(defaultValue = "1") int page`
  reaches `PageRequest.of(page - 1, 5)` with no bound at either end
  (`VetController.java:45, 59-63`).
- **Pagination unobserved by the existing tests:** the existing `VetControllerTests` do not
  observe pagination because they stub `findAll(any(Pageable.class))`
  (`VetControllerTests.java:77-78`) and do not capture or assert the `Pageable`. A new
  `@WebMvcTest` could observe it with an argument captor. The integration-test level is selected
  for end-to-end HTTP status and error-page dispatch, not because controller tests categorically
  cannot observe pagination; precedent `OwnerNotFoundIntegrationTests.java:44-46`.
- **Duplicated idiom across packages:** the identical unbounded pagination pair lives in
  `OwnerController.java:120-133`. Out of scope here (PAID D5) and a trap - a fix generalised
  across both controllers breaks `paginationErrorIsNotTreatedAsNotFound`.
- **Model attribute the template never reads:** `totalItems` (`VetController.java:54`) - PAID D7,
  out of scope, do not tidy.
- **Error view with no 400 case, echoing exception text:** `error.html:11-15` switches on
  `${status}` with cases `404`, `500` and `*` only; `error.html:18` renders `${message}`
  unconditionally.
- **Absent pathologies, stated so they are not hunted:** no god class, no large static method, no
  direct instantiation in the change area, no external integration. `VetController` is 74 lines.

### 3. Techniques applied

| Date | It. | Technique (supplement) | Invariant that governs the diff | Macro phase | Target | Commit |
|---|---|---|---|---|---|---|
| 2026-09-22 | 1/4 | Section 2: Pin behavior, then change | Section 2 characterization invariant | Phase 0 | `/vets.html` page domain | this commit — Characterize /vets.html page contract (plan-step 1/4) |
| _pending_ | 2/4 | Section 6 time-budget move: Extract pure function | BbA Phase 1 (Abstract) | Phase 1 | `VetController.findPaginated` | _pending_ |
| _pending_ | 3/4 | Section 1: Add new code in a new class | decision tree, new-class branch, plus BbA Phase 2 (Implement) | Phase 2 | `InvalidVetPageException`, `VetPageRequests` | _pending_ |
| _pending_ | 4/4 | Decision tree, bug-fix branch | "do not modify other code while fixing" | Phase 3 | `findPaginated` selection | _pending_ |

Append one row per landed iteration; never rewrite a prior row. If a diff turned out to be
constrained by an invariant other than the one named, record that - a mismatched quotation is
itself a finding, and two occurred during planning (see section 5).

### 4. Standing prompt invariants

Every iteration opens with the Section 6 time-budget invariant, verbatim:

> "I have [N] minutes / hours / days. Pick the move that gives me the most value in that time
> without skipping characterization tests. If no move fits, tell me the smallest meaningful change
> that fits the budget, even if it is just adding tests."

Iteration 2's move is named precisely as **Section 6 time-budget move: Extract pure function** -
"pull a computation that does not need framework context", which is exactly what
`page -> Pageable` is. That move carries no diff invariant of its own in the supplement, so the
Branch-by-Abstraction Phase 1 invariant supplies it.

Codebase-specific rules that must hold in every prompt touching this migration:

- **Never generalise the page fix to `OwnerController`.**
  `OwnerNotFoundIntegrationTests.java:118-124` pins `GET /owners?page=0` to 500 and asserts it is
  not 404. A shared fix, an `@ExceptionHandler(IllegalArgumentException.class)`, or a
  `@ControllerAdvice` breaks it.
- **Bind status to a dedicated exception type, never to a shared one.** The single precedent is
  `OwnerNotFoundException.java:28`. There is no `@ControllerAdvice` and no `@ExceptionHandler`
  anywhere in `src/main/java`; that absence is a property to preserve, not an omission to fix.
- **End-to-end status and error-page dispatch are characterized at
  `@SpringBootTest(webEnvironment = RANDOM_PORT)` level**, because that is the level at which the
  real status and the `error.html` dispatch are observable. Do not assert a status at a level that
  cannot observe it, and do not infer which exception produced a status from a `TestRestTemplate`
  response - exception identity is an MVC-layer assertion. A `@WebMvcTest` remains the right level
  for `Pageable` capture and for resolved-exception assertions.
- **Exception message visibility is environment-dependent.** `error.html` attempts to render
  `${message}`, but message inclusion is environment-dependent. The measured `bootRun` environment
  with devtools included the exception message, while `@SpringBootTest` omitted it. Treat every
  exception message as potentially client-visible and never put secrets or internal details in it,
  but do not assert exact message text unless a specification makes it contractual.
- **Do not add a message key.** A 400 falls to `#{error.general}` ("An unexpected error
  occurred."), because `error.html:11-15` has no 400 case and the bundle has no `error.400`
  (`messages.properties:50-52`). That imprecise body is an accepted, recorded gap for this
  migration - not a defect to fix inside it - because the 400 body text is outside the acceptance
  contract. Localized `error.400` support is a separate follow-up. Adding the key would touch all
  11 files under `src/main/resources/messages/` and engage `I18nPropertiesSyncTest`, which
  kill-switch condition 4 forbids.
- **The status choice was made on HTTP semantics, never to fit a view.** 400 is the correct reading
  for a syntactically valid but out-of-domain query parameter.
- **`pageSize = 5` and the 1-based `page - 1` convention are contract, not debt.** Preserve them.
- **Out of scope, do not touch while nearby:** deterministic sort (D3), cache bound (D2),
  duplicated pagination (D5), dead `totalItems` (D7), `Specialty` native hint (D9), actuator
  exposure.
- **Two carve-outs from the Test plan section above, valid only inside this migration.** That
  section continues to govern all other work in this repository.
  1. *Iteration 1's characterization commit lands green.* It pins today's behavior and therefore
     cannot be red, so the red-phase gate's "confirm it fails for the expected reason" does not
     apply to it. Formatting, `git diff --check` and the separate-commit rule all still do.
  2. *Iteration 4 may update exactly two expectations* - those for `?page=0` and `?page=-1` - in
     `VetPaginationCharacterizationTests`, and nothing else in that file. The technique prescribes
     it: the tests should fail in the specific places the behavior was intended to change. The
     commit message names both the behavior change and the test delta.
  3. *`VetPaginationContractTests` is frozen while it drives the implementation.* It is the test
     that drives Iteration 4, so the standing rule - the test that drove an implementation is
     never edited to accommodate it - applies to it in full. Carve-out 2 does not weaken that
     rule: the characterization test pins the *old* behavior and drove nothing.
- **Staleness check, every session start, before proposing work:** does section 6's phase list
  match `git log --oneline`? Does section 3 have one row per landed commit? Does section 2 still
  describe `VetController.findPaginated` as it reads today? A discrepancy is a ledger bug - fix
  the ledger before doing any new work.

### 5. Claude failure modes specific to this codebase

Append-only. Add an entry whenever a reviewer check trips. Removing an entry needs an explicit
decision and a note saying why the mode no longer applies.

- **Quotes a prompt invariant that does not govern the move.** Happened twice during planning. The
  new-*method* invariant ("the legacy method's existing code must be byte-identical apart from
  that one added line") was quoted for a new-*class* move in Iteration 3, where nothing is wired
  so there is no call line at all; and for an *extraction* in Iteration 2, where lines leave the
  legacy method rather than being added to it. Both quotations permit diffs the real move forbids.
  Defence: before quoting, check each clause of the invariant against the planned diff.
- **Claims framework behavior the repository does not prove.** Asserting what Spring does at a
  boundary instead of pinning it with a test that runs here. Defence: every behavioral claim cites
  a file:line or a test that fails when the claim is false.
- **Treats a green Gradle build as evidence for a template or rendered-page change.**
  `./gradlew build` proves non-regression only; a rendered page must be loaded and its console
  read. One console error per non-2xx main document is inherent, not a defect
  (`four-phase-evaluation-2026-09.md:228-246`).
- **Tidies adjacent debt while nearby.** The dead `totalItems`, the missing `Sort` and the
  duplicated owner pagination all sit in this diff's line of sight and all belong to other PAID
  items.
- **Chooses a status, a type or an API shape to fit the existing view.** An earlier revision of
  this plan recommended 404 partly because `error.404` already existed and read well. Defence:
  decide the contract on its own semantics first, then handle the view consequence separately.
- **Infers exception identity from an HTTP status.** Two different mechanisms produce 400 on this
  endpoint - parameter type mismatch for `?page=abc`, and `InvalidVetPageException` for
  `?page=0`. A `TestRestTemplate` response cannot tell them apart.
- **Confuses devtools behavior with packaged/test behavior.** `bootRun` exposed the exception
  message while `@SpringBootTest` omitted it. Defence: verify behavior in the execution mode used
  by the acceptance test and treat local-dev observations separately.
- **Trusts a formatter's success without inspecting its diff.** spring-javaformat mangled Javadoc
  containing an inline table tag and then converged on the malformed result. Defence: inspect the
  formatted diff and run a second idempotence pass; prefer prose where inline HTML is unstable.
- **Reports the wrong command status after a pipeline.** `command | tail; echo $?` reported the
  status of `tail`, not the build command. Defence: run the command without a pipeline or enable
  pipefail and preserve the originating command's exit status.
- _pending - populate from the reviewer checks as iterations land._

### 6. Macro-pattern migration state - Branch-by-Abstraction

- [x] **Phase 0 - Characterize.** `/vets.html` page domain pinned, including today's 500s.
      Commit: this commit — Characterize /vets.html page contract (plan-step 1/4), 2026-09-22.
      `VetPaginationCharacterizationTests`, 8 tests, all green; whole suite 99 tests, 0
      failures, 0 errors, 0 skipped. Every expected value was captured by running the
      unmodified application and recording its responses, not copied from a prior document.
- [ ] **Phase 1 - Abstract.** Page translation behind a seam; behavior identical. Commit: _pending_
- [ ] **Phase 2 - Implement.** Validating translation and `InvalidVetPageException` added, **not
      selected**. Commit: _pending_
- [ ] **Phase 3 - Toggle.** Validating translation selected; **functional criterion AC-D1 met**.
      Commit: _pending_
- [ ] **Phase 4 - Remove. OPEN, and expected to stay open. The macro-pattern migration is
      therefore NOT complete, even once AC-D1 is met.** The gap: `VetPageRequests` retains both
      translations, the unvalidated one has zero callers but is not deleted, and the migration
      seam is not collapsed. The phase invariant requires production traffic clean over an agreed
      window and states that tests passing alone is not sufficient; this repository produces no
      such evidence. Trigger: the application runs somewhere with an access log showing the
      validating translation clean over an agreed window, at which point Phase 4 lands as its own
      commit. Deleting on test evidence alone would contradict the invariant and must be recorded
      as such if chosen.

**Open items.** None blocking. The MVC capability question is **answered**, measured 2026-09-22
with a throwaway `@WebMvcTest(VetController.class)` probe that was run and then deleted unstaged:

- `?page=0` and `?page=-1` - `mockMvc.perform` **throws** `jakarta.servlet.ServletException`, root
  cause `java.lang.IllegalArgumentException: Page index must not be less than zero`. There is **no
  resolved exception**, because nothing resolves it; the harness can inspect only the *propagated*
  exception.
- `?page=abc` - `mockMvc.perform` **returns**, status 400, and `getResolvedException()` is
  `org.springframework.web.method.annotation.MethodArgumentTypeMismatchException`, resolved by
  `DefaultHandlerExceptionResolver`.

Consequence for Iteration 4, now observed rather than inferred: the two 400s will differ in *kind*
at the MVC layer, not merely in value. Once `InvalidVetPageException` carries `@ResponseStatus`,
`ResponseStatusExceptionResolver` should resolve it, making the discriminator
`getResolvedException()` being `InvalidVetPageException` for `?page=0` versus
`MethodArgumentTypeMismatchException` for `?page=abc`. That type is not created until **Iteration
3** and not selected until **Iteration 4**, so the assertion belongs to **Iteration 4**. It is not
an AC-D1 row at any point.

**Observed but deliberately not asserted.** Under `gradlew bootRun` the 500 page body contains the
framework string `Page index must not be less than zero`; under `@SpringBootTest` the same page
renders an empty exception paragraph. The difference is `spring-boot-devtools`, which is
`developmentOnly` (`build.gradle:49`) and sets `server.error.include-message=always`. Nothing in
`src/main/resources/` configures that property, so the echo is a local-dev behavior only, not a
deployed one. The committed characterization therefore asserts the shared layout and the
status-specific message and says nothing about the exception text.

**Decisions log.** 400 on HTTP-semantic grounds, not 404 - the earlier 404 preference was a
view-reuse convenience and was corrected. The 400 body text is outside the contract and no message
key is added; localized `error.400` support is a separate follow-up. `?page=999` stays 200 as a
separate product decision. No runtime toggle. The D2 cache finding - Caffeine is runtime-scope
only in both builds and no JSR-107 provider artifact is declared, so
`CacheConfiguration.petclinicCacheConfigurationCustomizer()` is probably inert - is INFERRED, out
of scope here, and deferred to its own investigation.
