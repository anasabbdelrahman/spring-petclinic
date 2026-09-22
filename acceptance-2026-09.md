# Acceptance - Vet Pagination Contract (PAID D1) - 2026-09

Written at the documentation-only setup commit, on top of `491d568`, before any test or production
code for this migration exists. This file changes no production code, no test and no configuration;
the only other file the setup commit touches is the root `CLAUDE.md`, which gains the migration
ledger as an appended section.

Companion to `architecture-vet-2026-09.md` (architecture map and implicit-assumption table) and
`paid-grid-vet-2026-09.md` (the PAID grid that selected D1). The migration ledger lives in
`CLAUDE.md`; this file holds the criterion and, at close, the measured result.

Evidence labels used throughout:

- **VERIFIED** - re-derived from this repository, with file:line.
- **CARRIED** - recorded in a prior repository document, not re-run while writing this file.
- **INFERRED** - reasoning from verified facts; proven by nothing in the repository.
- **OPEN** - not knowable from the repository; needs a run or a human.

## 1. The criterion, as frozen on 2026-09-22

**AC-D1.** Default H2 profile, six seeded vets (`db/h2/data.sql:1-6`), page size 5
(`VetController.java:60`).

| # | Request | Status | Externally observable condition | vs. today |
|---|---|---|---|---|
| 1 | `GET /vets.html?page=0` | **400** | response rendered by `error.html` through the shared layout, not the Whitelabel error page | **CHANGED** from 500 |
| 2 | `GET /vets.html?page=-1` | **400** | as row 1 | **CHANGED** from 500 |
| 3 | `GET /vets.html` (parameter absent) | 200 | view `vets/vetList`; `currentPage` = 1, `totalPages` = 2; 5 vet rows | unchanged |
| 4 | `GET /vets.html?page=1` | 200 | identical to row 3 | unchanged |
| 5 | `GET /vets.html?page=2` | 200 | `currentPage` = 2, `totalPages` = 2; 1 vet row | unchanged |
| 6 | `GET /vets.html?page=999` | 200 | `totalPages` = 2; zero vet rows | unchanged |
| 7 | `GET /vets.html?page=abc` | **400** | unchanged | unchanged |
| 8 | `GET /owners?page=0` | 500 | `paginationErrorIsNotTreatedAsNotFound` still passes | unchanged |
| 9 | `GET /vets` (Accept: application/json) | 200 | `{"vetList":[...]}` with `$.vetList[0].id` = 1 | unchanged |
| 10 | `src/main/resources/messages/**` | - | no file modified by any commit in this migration | unchanged |

This table is not edited in place after the setup commit. Any genuine change lands in section 7 as
a dated amendment with a reason - setting or loosening a criterion later is moving the goalposts.

### 1.1 Completion, stated as two separate claims

Only the first of these may be made when the iterations finish:

1. **The functional criterion AC-D1 may be marked met after the Toggle iteration (Iteration 4)**, on
   the evidence of rows 1-10 measured and a green `./gradlew build`.
2. **The Branch-by-Abstraction migration is NOT complete**, because Phase 4 (Remove) remains open.
   Its invariant is *"Delete the old implementation only after I confirm production traffic on the
   new implementation has been clean for [period]. Tests passing alone is not sufficient."* This
   repository produces no production evidence, so the precondition cannot be satisfied.

   **The exact gap:** `VetPageRequests` retains both translations; the unvalidated one has zero
   callers but is not deleted; the seam introduced for the migration is not collapsed.

   **The exact trigger:** the application runs somewhere with an access log showing the validating
   translation clean over an agreed window, at which point Phase 4 lands as its own commit. A
   decision to delete on test evidence alone would contradict the phase invariant and must be
   recorded as such if taken.

No summary in this file, in `CLAUDE.md`, or in the final evaluation says "migration complete", or
gives a percentage that rounds Phase 4 away.

## 2. Why this criterion, and the alternatives rejected

### 2.1 Engagement option: Refactor

| Option | Why rejected |
|---|---|
| **Refactor** | *Selected.* A working endpoint missing one parameter bound. Defect-repair TDR is 23.1% (`paid-grid-vet-2026-09.md:289`) - well above the 5% threshold, nowhere near replacement territory. |
| Wrap | The only wrapper that removes the 500 without deciding the contract is an `@ExceptionHandler(IllegalArgumentException.class)`, and `four-phase-evaluation-2026-09.md:46-51` records that this exact move silently reclassifies `GET /owners?page=0`, which throws the identical type. |
| Rewrite | Rebuilding the vet module is estimated at 33-60 h (`paid-grid-vet-2026-09.md:277`); it would discard five working, reviewed types to add one bound. |
| Delete | `/vets.html` is live UI surface - the nav bar links to it (`layout.html:51`) - so neither the endpoint nor its `page` parameter can go. |
| Tolerate | An ordinary URL returns 5xx, the item carries the highest proxy business value (5), and the TDR > 5% note (`paid-grid-vet-2026-09.md:306-310`) requires scheduled remediation rather than absorption into maintenance. |

### 2.2 Macro-pattern: Branch-by-Abstraction, with its fit recorded as partial

`VetController.findPaginated` (`VetController.java:59-63`) is a private method with exactly one
caller (`:46`) that inlines the whole page-to-`Pageable` translation. That is an already-isolated
seam: introduce the abstraction there and every caller goes through it in one commit with behavior
identical (Phase 1); add the validating translation behind the same seam, unselected (Phase 2); flip
the selection in one line (Phase 3). Nothing outside `findPaginated` observes the migration - not
`vetList.html`, not `VetRepository`, not the `/vets` endpoint. That property is the pattern's
premise, and this seam has it by construction.

**The fit is nevertheless partial, and that is recorded rather than argued away.** The pattern's own
non-fit clause reads "the piece is small enough that in-place refactoring with the 4-phase loop
suffices", and this piece is two lines. The honest consequence: **Iterations 2 and 3 exist because
the pattern's phase boundaries require them, not because the code demands them.** A reviewer may
fairly call that ceremony. It is accepted here because the capstone requires a macro-pattern and
this is the only one whose phases map onto independently revertible commits of an in-process change
where every commit must ship - which this repository's green-phase gate already demands.

| Pattern | Why rejected |
|---|---|
| **Expand/Contract** | *Strongest rejected.* The change **is** an API-shape change - the status for a set of `page` values - which is E/C's home territory. It fails on E/C's own clause: "Clients deploy atomically with the schema (rare in distributed systems but common in single-binary applications)." One in-repo client of `page`, `vetList.html:26-54`, ships in the same jar and never emits an out-of-range link. No dual shape to expand into, no client fleet to migrate. |
| Strangler Fig | `/vets.html` is a routable boundary, but there is no live traffic and "the traffic volume is too low to validate parity statistically". A parallel `/vets2.html` route would be absurd for a two-line bound. |
| Anti-Corruption Layer | The page-to-`Pageable` translation is a real seam, but an ACL is "a bridge, not a building" and is marked for eventual removal; this seam would be permanent, which "violates the pattern's premise". |
| Parallel Run | Ruled out by its own clause: "Volume is too low to detect rare divergences statistically (then characterization tests are the alternative)." Six seeded rows. |

### 2.3 Why 400 and not 404

400 is the correct reading for a query parameter that is syntactically valid but outside its
domain: the client sent a bad request. It is also consistent with row 7, where a malformed `page`
already answers 400 (CARRIED, `paid-grid-vet-2026-09.md:404`).

An earlier revision of this plan recommended 404, partly because `error.html:11-15` already has a
404 case and `error.404` reads "The requested page was not found." (VERIFIED,
`messages.properties:50`), which is apt wording for a page index that does not exist. That is a
template-and-i18n convenience, not an HTTP-semantics argument; choosing a status to fit the
available view is backwards, and the recommendation was corrected. The cost of the correction is
section 2.5.

### 2.4 Why `?page=999` stays 200

Whether an empty page beyond the last is "not found" is a separate product decision, not a
consequence of bounding the lower end. Pinning row 6 unchanged keeps this migration's blast radius
at the lower bound only, and the pin is what proves the change did not drift upward.

### 2.5 Why no message key is added, and the gap that leaves

`error.html:11-15` switches on `${status}` with cases for `404`, `500` and `*` only (VERIFIED), and
the base bundle holds `error.404`, `error.500` and `error.general` but no `error.400` (VERIFIED,
`messages.properties:50-52`). A 400 therefore falls to `#{error.general}` - "An unexpected error
occurred." - which is imprecise for a client input error.

**This is accepted for this migration and recorded as a gap, not fixed inside it.** It is sound only
because the 400 page's body text is outside the acceptance contract (section 2.6). Adding
`error.400` would touch all 11 files under `src/main/resources/messages/` and engage
`I18nPropertiesSyncTest`, which kill-switch condition 4 forbids and which would take the change out
of the `vet/` package for a message string. **Trigger to close it:** a separate follow-up adding
localized `error.400` support across all 11 locale files, scoped and estimated on its own.

### 2.6 What is outside the acceptance contract

- **The body text of the 400 page** (see 2.5). Rows 1 and 2 assert the status and that the
  shared-layout error view rendered; they assert no message string.
- **Which exception produced a 400.** Two mechanisms yield 400 on this endpoint: parameter type
  mismatch for row 7, and `InvalidVetPageException` for rows 1-2. That distinction is a **design
  note**, not an externally observable clause, and a `TestRestTemplate` response cannot tell the two
  apart. If it is asserted at all it is asserted at the MVC layer - see section 3.1.
- **The exception message text.** `error.html:18` always contains the `${message}` expression, with
  no surrounding condition (VERIFIED). Whether the container populates it is
  **environment-dependent**, and that distinction was not drawn when this document was first
  written. Measured 2026-09-22 on the same 500 response: under `gradlew bootRun` the body contained
  the framework string `Page index must not be less than zero`, while under
  `@SpringBootTest(webEnvironment = RANDOM_PORT)` the same page rendered an empty exception
  paragraph. The cause is `spring-boot-devtools`, which is `developmentOnly` (`build.gradle:49`) and
  sets `server.error.include-message=always`; nothing under `src/main/resources/` configures that
  property. So the echo recorded at `four-phase-evaluation-2026-09.md:255-268` is a local-dev
  behavior, not a packaged or deployed one - which narrows, but does not remove, the concern that
  note raised. **Exact exception text stays outside this acceptance contract** either way: no row
  asserts it, and the characterization tests committed in `de7c7d0` assert the shared layout and the
  status-specific message only. The standing invariant remains a constraint on the implementation,
  not a contract clause: treat every exception message as potentially client-visible, because the
  property is one line away from being set, and keep secrets and internal details out of it.
- **Response timing, cache behavior, row ordering.** Ordering is PAID D3 and explicitly out of
  scope; an unordered paginated query means rows 3-6 assert row *counts* and model attributes, not
  which vet appears on which page.

## 3. How each row is executed

Named here, before the tests exist, so that no row can later be certified by a test that merely
mentions it. Status rows run at `@SpringBootTest(webEnvironment = RANDOM_PORT)` level with
`TestRestTemplate`, following the precedent at `OwnerNotFoundIntegrationTests.java:44-46`. That
level is selected because the rows assert an end-to-end HTTP status and the `error.html` dispatch,
which are observable there - not because a controller test could not observe pagination. A
`@WebMvcTest` could capture the `Pageable` with an argument captor, and is the right level for the
resolved-exception assertion in section 3.1.

| Row | Test class | Method (planned name) | Command |
|---|---|---|---|
| 1 | `VetPaginationContractTests` | `pageZeroIsBadRequest` | `./gradlew test --tests "org.springframework.samples.petclinic.vet.VetPaginationContractTests"` |
| 2 | `VetPaginationContractTests` | `negativePageIsBadRequest` | as above |
| 3 | `VetPaginationContractTests` | `absentPageServesFirstPage` | as above |
| 4 | `VetPaginationContractTests` | `firstPageServesFiveRows` | as above |
| 5 | `VetPaginationContractTests` | `lastPageServesRemainingRow` | as above |
| 6 | `VetPaginationContractTests` | `pageBeyondLastServesEmptyTable` | as above |
| 7 | `VetPaginationContractTests` | `nonNumericPageIsBadRequest` | as above |
| 8 | `OwnerNotFoundIntegrationTests` (existing, unmodified) | `paginationErrorIsNotTreatedAsNotFound` | `./gradlew test --tests "org.springframework.samples.petclinic.owner.OwnerNotFoundIntegrationTests"` |
| 9 | `VetControllerTests` (existing, unmodified) | `showResourcesVetList` | `./gradlew test --tests "org.springframework.samples.petclinic.vet.VetControllerTests"` |
| 10 | - | - | `git status --short` and `git diff --stat` over `src/main/resources/messages/` |

Whole-suite gate for every row: `./gradlew build` green, plus `./mvnw spring-javaformat:apply`
leaving the committed tests unaltered and `git diff --check` clean.

### 3.1 The one MVC-layer assertion, and when it can exist

Distinguishing row 1's 400 from row 7's 400 is not an HTTP-level observation; it is a
`@WebMvcTest`-level assertion on the resolved exception. Its timing follows from the iteration
order, not from preference:

- **Iteration 1** may characterize today's behavior - `?page=0` raising `IllegalArgumentException`
  from `PageRequest.of` - and establish whether this MVC harness can inspect resolved exceptions
  at all. That is a capability question, answerable now.
- `InvalidVetPageException` does not exist until **Iteration 3**, and is not selected until
  **Iteration 4**.
- Therefore the assertion that `?page=0` resolves `InvalidVetPageException` while `?page=abc` fails
  during argument binding and never enters the controller method **belongs to Iteration 4**. It
  cannot be written earlier: before Iteration 4 the endpoint does not raise that type.

**OPEN:** whether the assertion is worth its cost, and its exact form. It is not an AC-D1 row at
any point.

## 4. Failing-witness proof per clause

For each row, the single assertion that fails when that clause is violated, proven once by breaking
the production line and observing the specific failure. This is the habit
`four-phase-evaluation-2026-09.md:406-412` identifies as the highest-leverage gate missing from this
repository's normal workflow, and it costs about thirty seconds per criterion.

| Row | Break this | Expect this to fail |
|---|---|---|
| 1, 2 | remove the lower-bound check from the selected translation | `pageZeroIsBadRequest`, `negativePageIsBadRequest` |
| 3, 4 | change `defaultValue = "1"` or the `page - 1` offset | `absentPageServesFirstPage`, `firstPageServesFiveRows` |
| 5 | change `pageSize` from 5 | `lastPageServesRemainingRow` (row count) and `firstPageServesFiveRows` |
| 6 | add an upper-bound rejection | `pageBeyondLastServesEmptyTable` |
| 7 | widen the new exception to catch conversion failures | `nonNumericPageIsBadRequest` remains 400, so this row's witness is weak by itself - the real witness is row 8 |
| 8 | replace the dedicated exception with a `@ControllerAdvice` or an `@ExceptionHandler(IllegalArgumentException.class)` | `paginationErrorIsNotTreatedAsNotFound` |
| 9 | rename a getter on `Vets` or `Vet` | `showResourcesVetList` |
| 10 | add `error.400` to the base bundle only | `I18nPropertiesSyncTest` |

Recorded honestly: **row 7's witness is weak on its own**, because both mechanisms produce 400 and
the status alone cannot separate them. Row 8 is the load-bearing witness against over-broad
exception mapping, and the optional MVC-layer assertion in section 3.1 is the only direct one.

## 5. Behavior deliberately left unchanged

Each with the pin that detects a change:

| Preserved | Pinned by |
|---|---|
| `?page=abc` -> 400 | row 7 |
| `?page=999` -> 200 with zero rows | row 6 |
| absent `page` -> 200, first page | row 3 |
| `/vets` JSON shape and `$.vetList[0].id` | row 9, existing test unmodified |
| `GET /owners?page=0` -> 500 | row 8, existing test unmodified |
| all 11 files under `src/main/resources/messages/` | row 10 and `I18nPropertiesSyncTest` |
| `pageSize = 5` | rows 4 and 5 |
| the 1-based `page - 1` convention | rows 3, 4, 5 |
| `vetList.html`, `error.html`, `VetRepository`, `OwnerController` | not in any iteration's allowed-file list |

## 6. Result

_Pending. Filled at the closing commit of Iteration 4's session, from measured output only._

Per-row measured status will be recorded here, each marked met or not met, with the gap and exactly
what would close it where a row does not pass. The Phase 4 (Remove) state will be recorded as open,
with the gap and trigger from section 1.1 repeated in identical wording. Numbers will be those
observed on the first attempt, with any re-run stated as a re-run.

## 7. Amendments

_None. Empty at creation._

Any change to section 1 after the setup commit is recorded here with a date, the old text, the new
text and the reason. Section 1 is never edited in place.

## 8. Evidence references

Source and configuration, VERIFIED in this repository:

- `VetController.java:44-48` - `GET /vets.html`, `page` bound with `defaultValue = "1"` and no bound
- `VetController.java:50-57` - four model attributes; `currentPage` is the raw request value;
  `totalItems` added and never read by the template
- `VetController.java:59-63` - `pageSize = 5`, `PageRequest.of(page - 1, pageSize)`, no validation,
  no `Sort`
- `VetController.java:65-72` - the `/vets` endpoint and the `Vets` wrapper
- `VetRepository.java:44-46, 54-56` - both `findAll` overloads, `@Transactional(readOnly = true)`
  and `@Cacheable("vets")`
- `OwnerController.java:120-133` - the duplicated pagination pair (PAID D5, out of scope)
- `OwnerNotFoundException.java:28` - the only `@ResponseStatus` in `src/main/java`, and the
  precedent this migration follows. No `@ControllerAdvice` and no `@ExceptionHandler` exists
  anywhere in `src/main/java`
- `vetList.html:17, 20-21, 26-54` - row iteration, specialty rendering, and the pager, which renders
  only when `totalPages > 1` and derives links from `currentPage` and `totalPages`
- `error.html:11-15` - status switch with cases `404`, `500`, `*` only
- `error.html:18` - `${message}` rendered unconditionally
- `messages.properties:50-52` - `error.404`, `error.500`, `error.general`; no `error.400`
- `layout.html:51` - the nav-bar item targeting `/vets.html`
- `db/h2/data.sql:1-6` - the six seeded vets that make `totalPages` = 2 at page size 5
- `VisitDateValidator.java`, `VisitDateValidatorTests.java` - precedent for a small package-private
  collaborator in a feature package, with its tests committed first (`2f65025`, `3d8119f`)

Tests, VERIFIED:

- `VetControllerTests.java:43-46` - `@WebMvcTest(VetController.class)`, `@DisabledInNativeImage`,
  `@DisabledInAotMode`
- `VetControllerTests.java:74-80` - `findAll(any(Pageable.class))` stubbed with a `PageImpl`, the
  `Pageable` neither captured nor asserted; this is why the existing controller tests do not
  observe pagination, and is not a limit on what a new `@WebMvcTest` could observe with an
  argument captor
- `VetControllerTests.java:82-98` - the two existing tests and their assertions
- `OwnerNotFoundIntegrationTests.java:44-46` - `@SpringBootTest(webEnvironment = RANDOM_PORT)` with
  `TestRestTemplate`, the precedent for status probes
- `OwnerNotFoundIntegrationTests.java:118-124` - `paginationErrorIsNotTreatedAsNotFound`, which pins
  `GET /owners?page=0` to 500 and asserts it is not 404

Runtime probes, CARRIED from `paid-grid-vet-2026-09.md:404-408` (2026-09-20, H2 default profile,
not re-run while writing this file): `?page=0` -> 500 `Page index must not be less than zero`;
`?page=-1` -> 500; `?page=abc` -> 400; `?page=999` -> 200 with an empty table body.

Prior artifacts: `architecture-vet-2026-09.md` (architecture map, implicit assumptions, four
manually verified claims; its teammate-review entry at `:141-145` is still pending, recorded as a
gap and not treated as a blocker), `paid-grid-vet-2026-09.md` (the grid that selected D1, and the
stakeholder delegation note at `:9-25` establishing that all business-value figures are
proxy-assigned and carry no endorsement), `four-phase-evaluation-2026-09.md` (the
`@ExceptionHandler` reclassification hazard at `:46-51`, the failing-witness habit at `:406-412`,
and the note at `:417-419` that a considered status for an invalid page index needs its own
specification - which this document supplies for the vet endpoint only; the owner-side half of
that note, where AC-7 knowingly pins `/owners?page=0` to 500, stays open and is why row 8 must
not change).

Recorded limitation: no access log, error-rate series or incident data exists for this module. Every
bug-burden input in the PAID grid is the unmeasured `1*` (`paid-grid-vet-2026-09.md:43-46, 410-411`),
so this migration proceeds on contract correctness rather than on measured harm.
