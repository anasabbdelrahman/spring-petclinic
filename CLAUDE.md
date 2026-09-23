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

## Security Requirements

These rules are normative for new and changed code; existing violations are tracked as security findings, not exempted.

- Validate every request parameter, path variable and form field at the application boundary; define bounds, lengths and accepted formats.
- Allowlist data-bound fields; reject unexpected and nested properties instead of relying only on denylisted identifiers.
- Use Bean Validation for domain constraints and add controller-level checks for values Bean Validation cannot express.
- Use Spring Data repositories or parameterized queries only; never concatenate untrusted input into SQL, JPQL, LDAP queries or commands.
- Require explicit authentication and authorization decisions for endpoints handling personal or mutable data; enforce object-level access where ownership matters.
- Keep CSRF protection enabled for cookie-authenticated state-changing requests and include valid CSRF tokens in forms.
- Never hardcode credentials or tokens; load them from environment variables or deployment-managed secret stores.
- Run detect-secrets before every commit; never baseline a real credential without an explicit documented risk decision.
- Use escaped template output such as `th:text` and `th:field`; never use unescaped output for untrusted data unless it is sanitized by an approved library.
- Return correct HTTP statuses with safe client messages; never expose stack traces, internal exception messages, queries or secrets.
- Never log credentials, tokens or unnecessary PII; treat request values as attacker-controlled.
- Add dependencies only from verified official coordinates; check both Maven and Gradle dependency graphs for known vulnerabilities.
- Keep production management endpoints restricted; never expose every actuator endpoint without authentication and network controls.
- Add focused security regression tests when fixing a confirmed vulnerability.

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

- **Unvalidated framework-boundary parameter - lower end resolved by Iteration 4.**
  `@RequestParam(defaultValue = "1") int page` (`VetController.java:43`) is still bound with no
  bound of its own, but since the Toggle `VetController.findPaginated`
  (`VetController.java:57-59`) delegates to `VetPageRequests.validated`
  (`VetPageRequests.java:62-67`), which rejects every `page < 1` with `InvalidVetPageException`
  - bound to 400 by `@ResponseStatus(HttpStatus.BAD_REQUEST)`
  (`InvalidVetPageException.java:29`) - and otherwise calls
  `PageRequest.of(page - 1, PAGE_SIZE)` (`VetPageRequests.java:66`) with `PAGE_SIZE = 5`
  (`VetPageRequests.java:39`). The legacy `VetPageRequests.unvalidated`
  (`VetPageRequests.java:52-54`, `PageRequest.of` at `:53`) is unchanged and has zero callers;
  it stays until Phase 4. The upper end remains unbounded - a separate product decision, and
  the INFERRED large-positive-page 500 in section 6 is outside this migration.
- **Pagination unobserved by the existing tests:** the existing `VetControllerTests` do not
  observe pagination because they stub `findAll(any(Pageable.class))`
  (`VetControllerTests.java:77-78`) and do not capture or assert the `Pageable`. A new
  `@WebMvcTest` could observe it with an argument captor. The integration-test level is selected
  for end-to-end HTTP status and error-page dispatch, not because controller tests categorically
  cannot observe pagination; precedent `OwnerNotFoundIntegrationTests.java:44-46`.
- **Duplicated idiom across packages:** the identical unbounded pagination pair lives in
  `OwnerController.java:120-133`. Out of scope here (PAID D5) and a trap - a fix generalised
  across both controllers breaks `paginationErrorIsNotTreatedAsNotFound`.
- **Model attribute the template never reads:** `totalItems` (`VetController.java:52`) - PAID D7,
  out of scope, do not tidy.
- **Error view with no 400 case, echoing exception text:** `error.html:11-15` switches on
  `${status}` with cases `404`, `500` and `*` only; `error.html:18` renders `${message}`
  unconditionally.
- **Absent pathologies, stated so they are not hunted:** no god class, no large static method, no
  direct instantiation in the change area, no external integration. `VetController` is 70 lines
  (74 before Iteration 2's extraction).

### 3. Techniques applied

| Date | It. | Technique (supplement) | Invariant that governs the diff | Macro phase | Target | Commit |
|---|---|---|---|---|---|---|
| 2026-09-22 | 1/4 | Section 2: Pin behavior, then change | Section 2 characterization invariant | Phase 0 | `/vets.html` page domain | Characterize /vets.html page contract (plan-step 1/4) — green only, carve-out 1 |
| 2026-09-22 | 2/4 | Section 6 time-budget move: Extract pure function | BbA Phase 1 (Abstract) | Phase 1 | `VetController.findPaginated` | red: Add tests for the vet page request translation; green: Extract vet page request translation behind a seam (plan-step 2/4) |
| 2026-09-23 | 3/4 | Section 1: Add new code in a new class | decision tree, new-class branch, plus BbA Phase 2 (Implement) | Phase 2 | `InvalidVetPageException`, `VetPageRequests` | characterization (green only, carve-out 4): Characterize Integer.MIN_VALUE page on /vets.html (plan-step 3/4, carve-out 4); red: Add tests for the validating vet page translation; green: Add validating vet page translation, unselected (plan-step 3/4); review: Record Iteration 3 fresh-session review |
| 2026-09-23 | 4/4 | Decision tree, bug-fix branch | "do not modify other code while fixing" | Phase 3 | `findPaginated` selection, and `@ResponseStatus(HttpStatus.BAD_REQUEST)` on `InvalidVetPageException` (deferred from Iteration 3, decided 2026-09-23) | red: Add HTTP contract tests for the vet page lower bound; green: Select validating vet page translation (plan-step 4/4); review: Record Iteration 4 fresh-session review |

**One row per numbered implementation iteration** - the four of this migration's plan - and never
one row per commit. An iteration that lands as a red/green pair occupies a single row that names
both commits, labelled `red:` and `green:`, in that order. An iteration exempted by a carve-out
lands green only and says so. An iteration that a carve-out opens with a planned green
characterization commit, followed by its red/green pair, occupies a single row that names all
three commits, labelled `characterization (green only, carve-out N):`, `red:` and `green:`, in
that order - Iteration 3 is the instance, under carve-out 4. The characterization commit is part
of the iteration, not a separate kind of commit, so it gets no row of its own and does not count
as a corrective commit for kill-switch condition 3. A numbered iteration's row may also name the
commit that records its post-green fresh-session review, labelled `review:` and placed last. That
commit belongs to the iteration it reviews - it is not ledger maintenance, not a new iteration and
not an acceptance amendment - and it does not count as a corrective commit for kill-switch
condition 3. It may record the review's verdict and evidence and resolve the review's findings in
this ledger, including a carve-out change the review decides; it changes no criterion, test or
implementation. Iteration 3 is the instance.

**Three kinds of commit carry no iteration row.** Each is tracked in the section that owns it, so
its absence from this table is correct rather than a discrepancy:

| Kind | Tracked in | Landed so far |
|---|---|---|
| Setup | this ledger's section 1 (header, criterion, kill switches) | `Charter vet pagination migration (setup)` |
| Acceptance amendment | `acceptance-2026-09.md` section 7, dated and reasoned | Amendments 1 and 2, 2026-09-23 |
| Ledger maintenance | the section whose text it corrects | `Fix stale characterization SHA in the acceptance ledger` |

**A ledger-maintenance commit is defined by purpose, not by the paths it touches.** It corrects
ledger metadata, a stale or wrong reference, or a recording error, and it does **not** change an
acceptance criterion, a migration decision, a phase state, or any implementation. Touching only
documentation is necessary but not sufficient: a setup commit and an acceptance-amendment commit
both touch documentation alone and neither qualifies, because both *decide* something rather than
correct a record. `ba08159` - *Fix stale characterization SHA in the acceptance ledger*, verified
an ancestor of `HEAD` by `git merge-base --is-ancestor` - qualifies: it replaced an unreachable
pre-amend SHA with the landed one and decided nothing. The staleness check in section 4 is worded
to match.

**Commits are cited by subject, not by SHA**, for the reason recorded in section 5. Do not write
`this commit` in a row: it is unambiguous only in the commit that adds the row and ambiguous for
every reader afterwards. **Do not rewrite an accurate prior row. A verified ledger error may be
corrected, with the reason recorded.** If a diff turned out to be constrained by an invariant
other than the one named, record that - a mismatched quotation is itself a finding, and two
occurred during planning (see section 5).

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
- **Carve-outs from the Test plan section above, valid only inside this migration.** That
  section continues to govern all other work in this repository. Carve-outs 2 and 4 were changed
  on 2026-09-23 by `acceptance-2026-09.md` section 7, Amendment 1, and corrected the same day by
  Amendment 2; carve-out 2 was widened again the same day by the Iteration 3 fresh-session review
  (section 5.2); carve-outs 1 and 3 are as chartered.
  1. *Iteration 1's characterization commit lands green.* It pins today's behavior and therefore
     cannot be red, so the red-phase gate's "confirm it fails for the expected reason" does not
     apply to it. Formatting, `git diff --check` and the separate-commit rule all still do.
  2. *Iteration 4 may update exactly three characterization methods and the class-level Javadoc*
     in `VetPaginationCharacterizationTests`, and nothing else in that file. The three methods are
     `pageZeroIsInternalServerErrorAndRendersErrorPage`,
     `negativePageIsInternalServerErrorAndRendersErrorPage` and
     `integerMinValuePageIsInternalServerErrorAndRendersErrorPage` - the `?page=0`, `?page=-1` and
     `?page=-2147483648` cases. In those three methods, and only those, Iteration 4 may:
     - rename the method, replacing `IsInternalServerError` with `IsBadRequest` and keeping the
       rest of the name - for example `pageZeroIsBadRequestAndRendersErrorPage`;
     - change the expected status from `HttpStatus.INTERNAL_SERVER_ERROR` (500) to
       `HttpStatus.BAD_REQUEST` (400);
     - remove the 500-specific assertion that the body contains
       `"An internal server error occurred."`, adding nothing in its place.

     The shared-layout assertions in those methods stay exactly as they are: the body is not null,
     it contains the layout title and `"Something happened..."`, and it does not contain
     `"Whitelabel Error Page"`. **No specific 400 body text is added or asserted** - not
     `"An unexpected error occurred."` nor any other string - because the 400 body text is outside
     the acceptance contract (`acceptance-2026-09.md` section 2.6).

     The class-level Javadoc may be corrected, and only so that it accurately describes the
     post-Toggle behavior, including the renamed methods it links. No other method, assertion or
     test documentation may change - not the other six methods, not the `vetRowCount` Javadoc, not
     any inline comment, not the helpers or constants.
     The technique prescribes it: the tests should fail in the specific places the behavior was
     intended to change. The commit message names the behavior change, all three method deltas
     (rename, status, removed assertion) and the Javadoc correction.
     **Widened from two to three on 2026-09-23** by Amendment 1, which brought
     `?page=-2147483648` inside the criterion as row 11. All three change from 500 to 400;
     Amendment 2 corrected row 11's baseline from 200 to 500. **Widened to the class-level
     Javadoc on 2026-09-23** by the Iteration 3 fresh-session review (section 5.2): that Javadoc
     already under-describes the file - it says two methods record a 500, and that every page of 0
     or below raises `IllegalArgumentException` - and would describe 500s that no longer occur
     once the Toggle lands. The same review made the per-method changes explicit, because "three
     expectations" did not say whether renames or the removal of the 500-specific assertion were
     allowed, and Iteration 4 cannot go green without the latter. This widening changes
     characterization tests and their documentation only, not the criterion, so it needs no
     acceptance amendment.
  3. *`VetPaginationContractTests` is frozen while it drives the implementation.* It is the test
     that drives Iteration 4, so the standing rule - the test that drove an implementation is
     never edited to accommodate it - applies to it in full. Carve-out 2 does not weaken that
     rule: the characterization test pins the *old* behavior and drove nothing.
  4. *Iteration 3 may add exactly one method to `VetPaginationCharacterizationTests`* -
     `integerMinValuePageIsInternalServerErrorAndRendersErrorPage` - and nothing else in that
     file. It pins today's 500 through the shared error page with the same assertions as the
     page-0 and page-(-1) methods. It lands **green**, for the same reason carve-out 1 exempts
     Iteration 1: it pins today's behavior and therefore cannot be red. It lands as its own
     commit, before the iteration's red/green pair, so each commit stays single-purpose and
     kill-switch condition 3 is not tripped by a commit that was planned as separate. Without it,
     row 11's 500 - raised in the persistence layer, not by `PageRequest.of` - has no HTTP-level
     *before* witness. **Added 2026-09-23** by Amendment 1; **corrected the same day** by
     Amendment 2, which renamed the method from `integerMinValuePageServesAnEmptyTable` because
     the 200 it named was never the application's behavior.
- **Staleness check, every session start, before proposing work:** does section 6's phase list
  match `git log --oneline`? Does section 3 have one row per landed **numbered implementation
  iteration** - not per commit - with every red/green pair named in its single row, every landed
  review-record commit named as `review:` in the row of the numbered iteration it reviews, and
  every setup, acceptance-amendment and ledger-maintenance commit correctly absent and tracked in
  the section that owns it? Do section 2's file and line references still resolve to the code as it
  reads today? A discrepancy is a ledger bug - fix the ledger before doing any new work.

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
- **Cites a rewritten commit's pre-amend SHA.** `acceptance-2026-09.md:151` named `8229a45` as the
  commit carrying `VetPaginationCharacterizationTests`; that object is unreachable from HEAD - no
  branch contains it, `git merge-base --is-ancestor` says no - and `de7c7d0` is the landed one, with
  the same subject and committer timestamp. Defence: cite commits by **subject**, the convention
  section 3 states, and check any SHA appearing in a document with `git merge-base --is-ancestor`
  before trusting it. The earlier `this commit — <subject>` form is withdrawn: it dodged SHA rot
  but was unambiguous only inside the commit that wrote it, and two rows ended up each claiming to
  be "this commit". The subject alone was always the identifying part.
- **Moves code without re-pointing the ledger's file:line references.** Iteration 2 extracted
  `page -> Pageable` out of `VetController` and left section 2 pointing at `VetController.java:45,
  59-63` for a `PageRequest.of` call that had moved to `VetPageRequests.java:52`, at `:54` for a
  `totalItems` now on `:52`, and at "74 lines" for a 70-line file. The fresh-session review after
  Iteration 2 caught all four. Defence: any commit that moves a line cited anywhere in section 2
  re-points that citation in the same commit, and the session-start staleness check resolves the
  references rather than eyeballing them.
- _pending - populate from the reviewer checks as iterations land._

#### 5.1 Iteration 2 fresh-session review - observations recorded, no action taken

The overfitting review after Iteration 2 returned a verdict of **appropriately scoped**: eight of
the nine `VetPageRequestsTests` protect behavior observable before the extraction, and an
adversarial sweep of behavior-preserving alternative extractions found none that these tests
reject except a change of the extracted method's signature, which is inherent to unit-testing any
extraction. Two tests - `noSortOrderIsApplied` and
`integerMinValueWrapsToTheHighestPageIndexAndIsNotRejected` - guard behavior that
`VetPaginationCharacterizationTests` structurally cannot observe, and both reject plausible
"tidying" extractions (adding a `Sort`; a `page < 1` guard clause or `Math.subtractExact`).

Three observations were recorded rather than acted on. **No existing test is renamed, removed or
modified on the strength of any of them**; they are noted so a later reader does not rediscover
them as defects.

- **Two test names claim more than their bodies assert.**
  `pageZeroIsRejectedByTheFrameworkAndNotByThisTranslation` and its negative-page twin assert only
  `assertThrows(IllegalArgumentException.class, ...)`, which cannot attribute the throw to
  `PageRequest.of` rather than to `VetPageRequests` - a guard-clause implementation inside
  `VetPageRequests` would pass both while making both names false. This is the same species as
  "infers exception identity from an HTTP status" above. It is **not fixed**: these are committed
  tests that drove an implementation, so the standing rule forbids editing them, and strengthening
  the bodies would mean asserting framework message text, which this ledger discourages. Revisit
  only if the file is legitimately touched for another reason.
- **The offset assertion is redundant, not wrong.**
  `offsetFollowsFromThePageIndexAndTheContractPageSize` cannot fail independently of the page-index
  and page-size tests, because `getOffset()` is `pageNumber * pageSize` by construction for a
  `PageRequest`. Offset is genuinely observable - it becomes the SQL `OFFSET` - so the assertion is
  legitimate; it simply adds no discriminating power. Left in place.
- **These nine tests lose their subject at the Toggle.** Once Iteration 4 selects the validating
  translation, `VetPageRequests.unvalidated` has zero callers and all nine tests exercise code no
  request reaches - permanently, because Phase 4 (Remove) is expected to stay open. The class
  javadoc's claim that it "must keep passing unchanged" stays true and stays uninformative. This is
  a recorded consequence of leaving Phase 4 open on purpose, not an oversight; it closes when
  Phase 4 closes, and not before.
- **Recorded at Iteration 3: the class javadoc now under-describes the file.** Iteration 3
  appended five `validated*` tests to `VetPageRequestsTests`, as Amendment 1's test mapping
  directs, so the class javadoc's "pin the extracted translation ... and nothing more" no longer
  describes the whole file. It is left unedited under the no-edit rule for committed tests.

#### 5.2 Iteration 3 fresh-session review - verdict and evidence

Run 2026-09-23, after `Add validating vet page translation, unselected (plan-step 3/4)`. Verdict:
**appropriately scoped.** The five `validatedRejects*` / `validatedAccepts*` tests express the
`page < 1` rule rather than the implementation. The reject tests assert only
`InvalidVetPageException` by type, with no message text and no claim about the mechanism. Each
accept test compares against a whole `PageRequest.of(n, 5)`, which pins the page index, the page
size and the absence of a sort in one assertion.

**Mutation evidence.** Twelve variants of `validated()` were run against `VetPageRequestsTests`
in a throwaway `git archive HEAD` copy outside the repository, then discarded:

- `page - 1 < 0` - fails `validatedRejectsIntegerMinValue` only (the recorded witness).
- no bound - fails all three `validatedRejects*` (the recorded witness).
- throws `IllegalArgumentException` instead - fails all three `validatedRejects*`.
- adds a `Sort`, page size 10, or drops the `- 1` - each fails both `validatedAccepts*`.
- `page < 2` - fails `validatedAcceptsPageOne`.
- equivalent rewrites pass: `page <= 0`; returning `unvalidated(page)` after the guard;
  `Pageable.ofSize(PAGE_SIZE).withPage(page - 1)`.
- survivors, accepted: an added upper bound `page > 1000` (the upper end is a separate product
  decision, section 6); mapping page 2 to index 0 (contrived). The second is the only thing a
  page-2 test would add, so omitting it is correct in intent, though it departs from the letter
  of the Test plan's "one step either side" - and Amendment 1's mapping named exactly these five
  methods.

Other evidence, all re-run in the review session: the red commit's `:compileTestJava` failure
reproduced as exactly 8 `cannot find symbol` errors, all `InvalidVetPageException` or
`validated(int)`; `unvalidated` and the original nine tests unchanged since Iteration 2;
`VetController` byte-identical to Iteration 2's green commit and still calling `unvalidated`;
no `@ControllerAdvice`, `@ExceptionHandler` or new `@ResponseStatus`;
`integerMinValuePageIsInternalServerErrorAndRendersErrorPage` green; `git diff --check` clean on
all three commits; `./gradlew build` green and `./gradlew test --rerun` 114 tests, 0 failures,
0 errors, 0 skipped. No file under `src/main/resources/messages/` changed since the setup commit.

Recorded, and resolved as stated:

- **The `Integer.MIN_VALUE` unit-test comment is stale at the HTTP level and stays unchanged.**
  `VetPageRequestsTests.java:77-78` says the input "answers an empty page today rather than
  raising". Amendment 2 disproved that over HTTP: the request answers 500, raised in the
  persistence layer. The assertion is still correct - it pins only the translation layer, where
  `page - 1` does wrap to `Integer.MAX_VALUE` - so the test is right and only its comment is wrong.
  It is a committed test that drove an implementation, so the no-edit rule forbids correcting the
  comment. Read that comment as describing the translation layer, not the HTTP response.
- **The characterization class Javadoc under-describes the file.**
  `VetPaginationCharacterizationTests.java:44-57` still says two methods record a 500 and that
  every page of 0 or below raises `IllegalArgumentException`; `Integer.MIN_VALUE` makes three, by
  `InvalidDataAccessApiUsageException`. Carve-out 4 rightly left it alone. Resolved by widening
  carve-out 2 so Iteration 4 may correct that Javadoc - and no other test documentation - to
  describe the post-Toggle behavior.
- **Carve-out 2's "three expectations" was ambiguous.** Each of the three methods also asserts the
  500-specific `"An internal server error occurred."`, which cannot hold after the Toggle, and
  the method names say `IsInternalServerError`. Resolved by listing in carve-out 2 exactly what
  Iteration 4 may do to those three methods: rename, change the status, remove that one
  assertion, keep the shared-layout assertions, and assert no 400 body text.
- **Section 3's row format did not describe Iteration 3's three-commit row.** Resolved by stating
  that form explicitly in section 3.

This review lands as Iteration 3's review-record commit, `Record Iteration 3 fresh-session
review`, named in Iteration 3's section 3 row under `review:` and in the Phase 2 entry of section
6. It is not ledger maintenance: it widens carve-out 2, which is a migration decision, and section
3 excludes those from ledger maintenance.

#### 5.3 Iteration 4 fresh-session review - verdict and evidence

Run 2026-09-23, after `Select validating vet page translation (plan-step 4/4)`. Verdict:
**appropriately scoped.** Functional criterion AC-D1 is met; Branch-by-Abstraction Phase 4
(Remove) is still OPEN, with the gap and trigger recorded in section 6 unchanged.

**The single actionable finding: the green commit's body did not satisfy carve-out 2.** Carve-out
2 requires the commit message to name the behavior change, all three method deltas (rename,
status, removed assertion) and the Javadoc correction. The green commit, as first landed, had a
subject line and no body.

**Resolution.** The green commit was amended in place, before the branch was pushed. The tree is
unchanged - `HEAD^{tree}` equals the pre-amend tree - and the parent is still `Add HTTP contract
tests for the vet page lower bound`. The subject is retained exactly, and the body now names the
behavior change, the three method deltas and the Javadoc correction. The amended green SHA is
`919e07a`, verified an ancestor of `HEAD` by `git merge-base --is-ancestor`. Repository
documents continue to reference the commit by subject, per section 3, so no citation changes.
The amend changed a message, not a tree, so it is not a corrective commit for kill-switch
condition 3.

Evidence, all re-run in the review session: `VetPaginationContractTests` byte-identical to the
red commit and `VetPageRequestsTests` to `Add tests for the validating vet page translation`, both
by `git diff --exit-code`; the characterization changes within carve-out 2 exactly; no change
since the setup commit under `src/main/resources/`, `owner/`, `system/`, `VetRepository`,
`VetControllerTests` or the build files; no `@ControllerAdvice` or `@ExceptionHandler`. Focused
run 41 tests, 0 failures; `spring-javaformat:validate` and `checkFormat` clean; `./gradlew test
--rerun` 122 tests in 26 classes, 0 failures, 0 errors, 0 skipped; `git diff --check` clean. The
red run and all four failing witnesses in section 6's Phase 3 entry were reproduced in throwaway
`git archive` copies, each with the recorded failing set and status.

Observations, recorded with no action required:

- **The tests sample three values below 1.** `0`, `-1` and `Integer.MIN_VALUE` are the only
  rejected inputs asserted, so a contrived implementation that special-cases exactly those three
  would pass. This is the same kind of accepted survivor as section 5.2's page-2 mapping. It is
  not worth changing frozen tests to close.
- **Rows 3-6 are coupled to the repository's pager markup.** They observe `currentPage` and
  `totalPages` through literal `href="/vets.html?page=N"` links in `vetList.html`, so a markup
  change could fail them without a behavior change. Acceptable for an in-repository view.

No further action is required. This review lands as Iteration 4's review-record commit, `Record
Iteration 4 fresh-session review`, named in Iteration 4's section 3 row under `review:` and in the
Phase 3 entry of section 6. It records a verdict and a resolved finding and decides nothing.

### 6. Macro-pattern migration state - Branch-by-Abstraction

- [x] **Phase 0 - Characterize.** `/vets.html` page domain pinned, including today's 500s.
      Commit: `Characterize /vets.html page contract (plan-step 1/4)`, 2026-09-22.
      `VetPaginationCharacterizationTests`, 8 tests, all green; whole suite 99 tests, 0
      failures, 0 errors, 0 skipped. Every expected value was captured by running the
      unmodified application and recording its responses, not copied from a prior document.
- [x] **Phase 1 - Abstract.** Page translation behind a seam; behavior identical.
      Commits: `Add tests for the vet page request translation` (red, `:compileTestJava` failing
      with "cannot find symbol: variable VetPageRequests") and `Extract vet page request
      translation behind a seam (plan-step 2/4)` (green), both 2026-09-22.
      `VetPageRequests.unvalidated` is the seam and `VetController.findPaginated` its only caller.
      `VetPageRequestsTests`, 9 tests, green; `VetPaginationCharacterizationTests` proved
      byte-identical to its `de7c7d0` revision by `git diff --exit-code` and its 8 tests still
      green, the two 500s included; whole suite 108 tests, 0 failures, 0 errors, 0 skipped. Two
      commits, not one: no section 4 carve-out covers this iteration, so the repo-wide tests-first
      gate applies in full. Reverting Iteration 2 therefore means reverting both, newest first -
      reverting only the green commit would leave the committed tests referencing a type that no
      longer exists.
- [x] **Phase 2 - Implement.** Validating translation and `InvalidVetPageException` added, **not
      selected**. Commits, all 2026-09-23: `Characterize Integer.MIN_VALUE page on /vets.html
      (plan-step 3/4, carve-out 4)` (green only, carve-out 4, pinning today's 500), `Add tests for
      the validating vet page translation` (red, `:compileTestJava` failing with 8 "cannot find
      symbol" errors - `class InvalidVetPageException` and `method validated(int)` only) and `Add
      validating vet page translation, unselected (plan-step 3/4)` (green, first attempt, no
      corrective commit). `VetPageRequests.validated` rejects every `page < 1` and otherwise
      returns `PageRequest.of(page - 1, PAGE_SIZE)`; `unvalidated` is unchanged and
      `VetController.findPaginated` still calls it, so behavior is identical.
      `VetPageRequestsTests` 14 tests green (9 unchanged plus 5 new);
      `VetPaginationCharacterizationTests` 9 green, all three 500s included; both proved unchanged
      since the red commit by `git diff --exit-code`; whole suite 114 tests, 0 failures, 0 errors,
      0 skipped. Failing witnesses run and reverted: with `page - 1 < 0` only
      `validatedRejectsIntegerMinValue` fails; with no bound all three `validatedRejects*` fail.
      **`@ResponseStatus` is deliberately absent** - no Iteration 3 test demands it; Iteration 4's
      HTTP contract test fails first and then drives both the selection and the status binding in
      one behavioral change (decided 2026-09-23).
      Review-record commit: `Record Iteration 3 fresh-session review`, 2026-09-23 - the post-green
      fresh-session review, verdict **appropriately scoped** (section 5.2). CLAUDE.md only; it
      widens carve-out 2 and changes no criterion, test or implementation.
- [x] **Phase 3 - Toggle.** Validating translation selected; **functional criterion AC-D1 met**.
      Commits, both 2026-09-23: `Add HTTP contract tests for the vet page lower bound` (red -
      `VetPaginationContractTests`, 8 tests, 3 failing with "expected: 400 BAD_REQUEST but was:
      500 INTERNAL_SERVER_ERROR" - `pageZeroIsBadRequest`, `negativePageIsBadRequest`,
      `integerMinValuePageIsBadRequest` - and the 5 unchanged-behavior rows passing, as expected)
      and `Select validating vet page translation (plan-step 4/4)` (green, first attempt, no
      corrective commit). `VetController.findPaginated` now calls `validated`;
      `InvalidVetPageException` gains `@ResponseStatus(HttpStatus.BAD_REQUEST)`; the
      `VetPageRequests` class Javadoc now names `validated` as selected and `unvalidated` as the
      legacy implementation, and neither method changed. `VetPaginationCharacterizationTests`
      changed under carve-out 2 only: the three lower-bound methods renamed to `IsBadRequest`,
      500 -> 400, `"An internal server error occurred."` removed, and the class Javadoc corrected
      - including its harness paragraph, which named a status-specific message no method asserts
      any longer. `VetPaginationContractTests` proved unchanged since the red commit and
      `VetPageRequestsTests` since `Add tests for the validating vet page translation`, both by
      `git diff --exit-code`. Focused: contract 8, translation 14, characterization 9,
      `VetControllerTests` 2, `OwnerNotFoundIntegrationTests` 6, `I18nPropertiesSyncTest` 2, all
      green. Formatter applied twice, no change either pass. `./gradlew build` green;
      `./gradlew test --rerun` 122 tests, 0 failures, 0 errors, 0 skipped. Failing witnesses,
      each run in a throwaway `git archive` copy of the green tree and then deleted: selecting
      `unvalidated` again, or removing `@ResponseStatus`, fails the three contract and three
      characterization lower-bound methods with 500; `page - 1 < 0` fails only the
      `Integer.MIN_VALUE` method in each of the three classes; an added `page > 2` rejection fails
      both page-999 methods with 400 and `validatedAcceptsPageFarBeyondTheLast`. The measured
      per-row result is `acceptance-2026-09.md` section 6.
      Review-record commit: `Record Iteration 4 fresh-session review`, 2026-09-23 - the post-green
      fresh-session review, verdict **appropriately scoped** (section 5.3). CLAUDE.md only; it
      changes no criterion, test or implementation.
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

**Decided 2026-09-23, Iteration 4: the MVC-layer assertion is not written.** No approved mapping
requires it - `acceptance-2026-09.md` section 3.1 leaves it OPEN and Amendment 1 maps it "if
written" - and it is not an AC-D1 row. Exception identity for `?page=0` stays pinned at unit level
by the three `validatedRejects*` tests, and row 8 stays the load-bearing witness against
over-broad exception mapping. Consequence, recorded rather than hidden: that
`ResponseStatusExceptionResolver` is what resolves `InvalidVetPageException` is **INFERRED**; the
repository proves the 400 and proves, by the removed-`@ResponseStatus` witness, that the
annotation is what produces it, but asserts no resolved-exception type. Revisit only if the two
400 mechanisms ever need telling apart.

**Newly recorded, Iteration 2 - an input no AC-D1 row covers.** `GET /vets.html?page=-2147483648`
answers **500 through the existing error page today** - the same status and page as `?page=0` -
but by a different exception. `page - 1` wraps to `Integer.MAX_VALUE`, which `PageRequest`
accepts as a legal page index (spring-data-commons 4.1.0 `AbstractPageRequest:46-58`, whose only
rejection is `pageNumber < 0`; `:71-73` computes the offset in long arithmetic). Spring Data JPA
then rejects that offset, `Integer.MAX_VALUE * 5`, because it exceeds `Integer.MAX_VALUE`
(spring-data-jpa 4.1.0 `PageableUtils:41-43`, `InvalidDataAccessApiUsageException`), and nothing
resolves it. VERIFIED 2026-09-23 by a failing `@SpringBootTest` run and a throwaway probe;
`acceptance-2026-09.md` section 7, Amendment 2 records both. The translation layer alone is
pinned by `VetPageRequestsTests.integerMinValueWrapsToTheHighestPageIndexAndIsNotRejected`, which
passes and stays correct.

*Correction, 2026-09-23.* This paragraph previously read "answers **200 with an empty table
today, not 500**", marked VERIFIED. That was a verified ledger error, corrected here under
section 3's rule: the evidence covered spring-data-commons only, and no HTTP request was made.
Iteration 3's first characterization run exposed it. Consequence for Iteration 4, as corrected:
a `page < 1` bound turns this input from 500 into 400, exactly like rows 1 and 2.

**Decided 2026-09-23, and no longer open.** The product decision is that every `int` page value
below 1, `Integer.MIN_VALUE` included, answers 400 after the Toggle. It is recorded as
`acceptance-2026-09.md` section 7, Amendment 1, which introduces criterion row 11, states the
rule the three witnesses share, adds row 11's failing witness, and maps the work across
Iterations 3 and 4. Two carve-out changes follow from it: carve-out 4 is new, and carve-out 2
widens from two expectations to three. Amendment 2 corrected row 11's baseline from 200 to 500
and renamed carve-out 4's method; row 11's 400 outcome is unchanged. Iteration 4 therefore
changes all three characterized cases from 500 to 400.

**OPEN, INFERRED follow-up - outside this migration.** The same `PageableUtils` check should make
large *positive* pages answer 500 today, for `page` at or above 429496731, where `(page - 1) * 5`
exceeds `Integer.MAX_VALUE`. INFERRED from the source and the arithmetic; no request was made. The
`page < 1` bound does not reach it, row 6's `?page=999` is far below it, and the upper end of
`page` is a separate product decision. No criterion, test or production code is added for it
here (Amendment 2).

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
separate product decision. No runtime toggle. Every `int` page below 1, `Integer.MIN_VALUE`
included, answers 400 after the Toggle - decided 2026-09-23, Amendment 1, criterion row 11, whose
baseline Amendment 2 corrected from 200 to 500. The
D2 cache finding - Caffeine is runtime-scope only in both builds and no JSR-107 provider artifact
is declared, so `CacheConfiguration.petclinicCacheConfigurationCustomizer()` is probably inert -
is INFERRED, out of scope here, and deferred to its own investigation.
