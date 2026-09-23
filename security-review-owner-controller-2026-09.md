# Security Review: `OwnerController.java` - Baseline versus Post-Policy - 2026-09

Target: `src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java` (176
lines). Branch: `homework/security-defense-in-depth`. Companion audit record:
`audit-2026-09-owner-controller.md`.

**No production finding was fixed during this homework.** No production code, test,
configuration or template was changed by either review. Every finding below is open.

## 1. Scope and review method

Both reviews targeted `OwnerController.java` only. Directly related files were read solely to
verify claims about framework protections, data access, templates and configuration: `Owner`,
`Person`, `BaseEntity`, `Pet`, `PetType`, `OwnerRepository`, `OwnerNotFoundException`,
`PetController`, `PetValidator`, `db/h2/schema.sql`, `db/h2/data.sql`, `application.properties`,
`build.gradle`, `pom.xml`, `templates/owners/*.html`, `templates/error.html` and
`templates/fragments/inputField.html`.

| | Baseline review | Post-policy review |
|---|---|---|
| When | 2026-09-23, before the Security Requirements section existed | 2026-09-23, fresh session, after `e66e4cf Add project security requirements` |
| Prompt | Eight OWASP-style categories; static review; no files, installs or HTTP requests | OWASP-oriented principles; read and follow `CLAUDE.md`; read-only |
| Method | Static reading only | Static reading **plus temporary runtime probes** |
| Runtime evidence | None | One throwaway `@SpringBootTest(webEnvironment = RANDOM_PORT)` probe, default H2 profile and seed data |
| Dependency scan | Not performed (tool installation ruled out) | Not performed |

**Probe method.** The probe ran in a `git archive HEAD` copy under `/tmp`, outside the repository,
and was deleted afterwards. `git status` was clean before and after. The probe sent form POSTs and
GETs through `TestRestTemplate` and read the resulting rows back through `JdbcTemplate`. Its
results are cited below as "probed".

## 2. Important limitation - attribution

The two reviews differ in more than the presence of the Security Requirements:

- The baseline was **static-only by instruction**. The post-policy review also **ran temporary
  runtime probes**.
- The prompts differed in wording and category list.
- The post-policy session read the repository in its post-`e66e4cf` state. Both sessions used the
  same model (Claude Opus 5.5), so model choice does not explain the difference, but session
  context does differ.

Most of the strengthened findings were strengthened by runtime evidence, and a static-only
review under the new policy might not have reached them. **The improvements therefore cannot be
attributed exclusively, or even mainly, to the Security Requirements.** The policy plausibly
shaped what was checked and how remediations were phrased (section 5). It did not by itself
produce the confirmations.

## 3. Before-versus-after comparison

Baseline IDs are prefixed `B-`, post-policy IDs `P-`. The IDs were assigned independently and do
not correspond by number.

| Topic | Baseline | Post-policy | Change |
|---|---|---|---|
| Mass assignment via `pets[n].*` | B-F3, Medium, needs investigation (inferred from binder behavior) | P-F1, High, confirmed by probe | **Strengthened** |
| No authentication / object authorization | B-F1, High, confirmed missing; severity deployment-dependent | P-F2, High, confirmed missing; impact deployment-dependent | Unchanged |
| Bulk PII via empty search | B-F2, Medium, confirmed behavior | P-F3, Medium, confirmed behavior (probe returned the list) | Unchanged in substance; runtime-observed |
| Missing CSRF | B-F4, Medium, rising to High with cookie auth; `th:action` trap noted as unproven | P-F4, Medium, confirmed missing; same `th:action` trap, still unproven | Unchanged |
| `address` / `city` length bounds | Not found | P-F5, Medium, confirmed 500 by probe | **New** |
| Unvalidated `page` | B-F5, Low; `page <= 0` confirmed; large page INFERRED | P-F6, Medium; large page (`429496731`) confirmed 500 by probe | **Strengthened** (upper end now measured) |
| Exception-message exposure | Folded into B-F5 | P-F7, Low, conditional; separated and extended to persistence exceptions | **Strengthened** (separated) |
| Search binds whole `Owner`; unbounded `lastName` | Listed as a blind spot only | P-F8, Low | **New** as a finding (promoted from a blind spot) |
| No rate limit on owner creation | Rate limiting mentioned only for scraping (B-F2) | P-F9, Low, conditional | **New** |
| Owner-ID mismatch check is dead code | B-F6, Low, informational | Moved to rejected claims (not a vulnerability) | Reclassified, same conclusion |
| Actuator exposure | Outside scope, High if deployed | Outside scope, High | Unchanged |

### 3.1 Unchanged

- **No authentication or object-level authorization** (B-F1 / P-F2). Both reviews confirmed the
  missing control from the absence of any Spring Security dependency, and both made the severity
  deployment-dependent.
- **Bulk PII enumeration** (B-F2 / P-F3). Same conclusion. The post-policy probe observed the
  owners list for `GET /owners?lastName=`, which the baseline established by reading code.
- **Missing CSRF** (B-F4 / P-F4). Same conclusion, including the observation that
  `createOrUpdateOwnerForm.html:8` has no `th:action`. Neither review proved that Thymeleaf skips
  token injection on such a form; that detail remains unverified.
- **Actuator exposure**, outside scope in both.

### 3.2 Strengthened

- **Mass assignment** (B-F3 -> P-F1). The baseline inferred it and asked for a test. The probe
  confirmed it:
  - `POST /owners/1/edit` with `pets[0].name=Hacked&pets[0].birthDate=2999-01-01&pets[0].type=snake`
    changed pet 1 from `Leo, 2010-09-07, type 1` to `Hacked, 2999-01-01, type 4`. This bypassed
    `PetValidator` and the future-birth-date and duplicate-name checks in
    `PetController.java:112-117, 154-160`.
  - `POST /owners/new` with `pets[0].name=Smuggled&pets[0].type=cat` created a pet on the new
    owner.
  - `pets[255].name=x` and `pets[256].name=x` each returned 500. The logged causes included a
    persistence constraint violation and `InvalidPropertyException` ("Index of out of bounds in
    property path 'pets[256]'"). The exact exception for each request was not individually
    attributed.
- **Unvalidated `page`** (B-F5 -> P-F6). The large-positive-page 500 moved from INFERRED to
  observed: `GET /owners?page=429496731` returned 500 with `InvalidDataAccessApiUsageException:
  Page offset exceeds Integer.MAX_VALUE`.
- **Exception-message exposure** (part of B-F5 -> P-F7). Separated into its own finding and
  extended to the persistence exceptions P-F5 produces.

### 3.3 Newly discovered

- **P-F5, missing `address` / `city` length bounds.** `Owner.java:51-57` has `@NotBlank` only,
  while `schema.sql` caps them at `VARCHAR(255)` and `VARCHAR(80)`. An 81-character `city` and a
  256-character `address` each returned 500 (`DataIntegrityViolationException`, "Value too long
  for column").
- **P-F8, broad `Owner` binding during search.** Promoted from the baseline's blind-spot list.
  `OwnerController.java:91` binds a full `Owner` on a GET, and `lastName` has no server-side
  bound (the `maxlength=80` in `findOwners.html:14` is client-side only).
- **P-F9, no rate limit on creation.** `OwnerController.java:73-83`.

## 4. Human calibration of the post-policy findings

Calibrated by the human reviewer, Anas Abdelrahman, 2026-09-23. Where the calibration differs
from the AI rating, the difference is recorded as an override in the audit document.

| ID | Finding | Evidence | AI rating | Human calibration |
|---|---|---|---|---|
| P-F1 | Mass assignment via nested `pets[n].*` binding (`OwnerController.java:57-60`, used at `:74`, `:91`, `:141`) | Probed | High, confirmed | **Confirmed real vulnerability, High** |
| P-F2 | No authentication or object-level authorization (`:63-66`, `:68-174`) | Code and build files | High, confirmed in code; impact deployment-dependent | **Confirmed missing control; impact and severity depend on deployment** |
| P-F3 | Bulk PII enumeration through empty search (`:94-97`, `:129-132`) | Probed | Medium, confirmed | **Confirmed behavior; security impact depends on deployment and whether the data is real** |
| P-F4 | No CSRF protection on `POST /owners/new` and `POST /owners/{id}/edit` (`:73`, `:140`) | Code and build files | Medium, confirmed missing | **Confirmed missing control; conditional today and more serious with cookie authentication** |
| P-F5 | Missing `address` / `city` length bounds cause 500 (`:74`, `:141`; `Owner.java:51-57`) | Probed | Medium, confirmed | **Confirmed real robustness and error-handling defect, Medium** |
| P-F6 | Unvalidated `page`, 500 at both ends (`:91`, `:131`) | Probed; `page <= 0` also pinned by `OwnerNotFoundIntegrationTests.java:118-124` | Medium, confirmed | **Confirmed known robustness / error-hygiene defect, Medium** |
| P-F7 | Exception-message exposure through `error.html:18` (`OwnerNotFoundException.java:32`) | Code and configuration; not probed with messages enabled | Low, conditional | **Conditional, Low** |
| P-F8 | Search binds the whole `Owner`; no server-side `lastName` bound (`:91`, `:94-100`) | Code | Low, confirmed in code | **Confirmed unnecessary attack surface, Low** |
| P-F9 | No rate limit on owner creation (`:73-83`) | Code | Low, conditional | **Conditional and deployment-dependent, Low** |

Notes on the calibration:

- **P-F5 and P-F6 are defects, not vulnerabilities.** Neither yields unauthorized access or data.
  They produce 500s for ordinary out-of-range input.
- **P-F2, P-F3 and P-F4 have no fixed severity.** The controls are confirmed missing. How much
  that matters depends on whether and where the application is deployed and whether it holds real
  personal data.
- **P-F7 is unverified in its strongest form.** The post-policy review stated that a persistence
  exception's message "usually includes the SQL statement". No request was made with
  `server.error.include-message` enabled, so that is an expectation, not an observation. What is
  confirmed is that `error.html:18` renders `${message}` unconditionally and that
  `spring-boot-devtools` (`build.gradle:49`, development-only) enables message inclusion.

## 5. How the `CLAUDE.md` Security Requirements influenced the second review

The post-policy prompt instructed the reviewer to read and follow `CLAUDE.md`. The requirements
below visibly shaped the second review's checks or remediations. Section 2's limitation applies:
this describes what the review did, not proof that the policy caused it.

| Requirement | Effect on the second review |
|---|---|
| Validate every parameter and form field at the boundary; define bounds, lengths and formats | Prompted comparing Bean Validation constraints with column lengths (P-F5) and checking the upper end of `page` (P-F6) and `lastName` (P-F8) |
| Allowlist data-bound fields; reject unexpected and nested properties | Framed P-F1's remediation as an allowlist and made nested-property rejection the test's subject; promoted P-F8 |
| Bean Validation for domain constraints, controller checks for the rest | Split P-F5 (Bean Validation) from P-F6 and P-F8 (controller checks) |
| Spring Data repositories or parameterized queries only | Injection and LIKE-wildcard claims checked and rejected |
| Explicit authentication and authorization; object-level access | P-F2, P-F3 |
| Keep CSRF enabled; include valid tokens in forms | P-F4, including the `th:action` detail |
| Escaped template output | XSS claim rejected |
| Correct statuses, safe messages; never expose internals | P-F5, P-F6 and P-F7 framed as status and disclosure issues |
| Never log credentials or unnecessary PII | Logging claim rejected; `Owner.toString()` noted outside scope |
| Restrict management endpoints | Actuator outside-scope risk |
| Add focused security regression tests | Every finding carries a regression test |

Two requirements were **not** exercised: *check both Maven and Gradle dependency graphs for known
vulnerabilities* (no scan was run in either review) and *run detect-secrets before every commit*
(not applicable to a read-only review; the hook itself is recorded in the audit document).

The ledger rule "Never generalise the page fix to `OwnerController`" also shaped P-F6's
remediation: it must be a separately chartered change (PAID D5), must not use an
`@ExceptionHandler(IllegalArgumentException.class)` or `@ControllerAdvice`, and requires an
explicit decision to update `OwnerNotFoundIntegrationTests.paginationErrorIsNotTreatedAsNotFound`.

## 6. Remediations and regression tests

| ID | Remediation | Security regression test |
|---|---|---|
| P-F1 | Replace `setDisallowedFields("id", "*.id")` with `setAllowedFields("firstName", "lastName", "address", "city", "telephone")`, or bind a dedicated form DTO | `@SpringBootTest`: `POST /owners/1/edit` with `pets[0].name` and `pets[0].birthDate` leaves pet 1 as `Leo`, `2010-09-07`; `POST /owners/new` with `pets[0].*` creates an owner with zero pets; `pets[256].name` does not return 500 |
| P-F2 | Spring Security with explicit rules for `/owners/**`; an object-level check where `findOwner` resolves the owner | Unauthenticated `GET /owners/1` and `POST /owners/1/edit` return 401 or a login redirect with data unchanged; an authenticated user without rights to owner 1 gets 403 |
| P-F3 | Authentication (P-F2); optionally a minimum search length; rate limiting | Unauthenticated `GET /owners?lastName=` is rejected; if a minimum length is chosen, an empty search shows the form without a list |
| P-F4 | Spring Security default CSRF; add `th:action` to `createOrUpdateOwnerForm.html:8` | `POST /owners/new` without a token returns 403 and creates nothing; with a token it returns 302 |
| P-F5 | `@Size(max = 255)` on `address`, `@Size(max = 80)` on `city` | Per field: max+1 re-renders the form with a field error and the owner count is unchanged; max and max-1 return 302 |
| P-F6 | Dedicated `InvalidOwnerPageException` with `@ResponseStatus(BAD_REQUEST)` for `page < 1`, plus an upper bound; chartered separately as PAID D5 | `?page=0`, `?page=-1`, `?page=-2147483648` return 400; `?page=1` returns 200; above the upper bound returns 400; `?page=abc` stays 400 |
| P-F7 | Remove the message paragraph from `error.html:18`; set `server.error.include-message=never` explicitly for production | `@SpringBootTest(properties = "server.error.include-message=always")`: an over-long `city` returns a body without exception class names or SQL text |
| P-F8 | Bind search as `@RequestParam(required = false) String lastName` with a controller-level maximum of 30 | 31-character `lastName` returns 400; 30 returns 200; `GET /owners?pets[0].name=x` has no effect |
| P-F9 | Rate limit at the gateway or in the application, behind authentication | Verified in gateway configuration rather than an application test |

## 7. Rejected false positives

Rejected in the post-policy review. "Probed" means runtime evidence; otherwise the rejection rests
on reading.

| Claim | Reason for rejection | Basis |
|---|---|---|
| SQL / JPQL injection through `lastName` | Derived query with a bound parameter (`OwnerRepository.java:45`); `' OR 1=1--` returned "not found" | Probed |
| LIKE-wildcard injection | `lastName=%` and `lastName=_` both returned "not found", so wildcards are escaped | Probed (the baseline rejected it on impact grounds only) |
| Open redirect (`:82`, `:113`, `:151`, `:157`) | Targets are an `Integer` ID or a URI template variable; no user-controlled host | Reading |
| Overwriting `id` or `pets[0].id` | `id` and `*.id` are disallowed | Reading; `pets[0].id` not specifically probed |
| Renaming a `PetType` via `pets[0].type.name` | Request returned 200 but the `types` table was unchanged; `Pet.type` has no cascade | Probed |
| Stored or reflected XSS | Output is `th:text`, `th:field` or escaped `[[...]]`; no `th:utext` in any template | Reading |
| Memory exhaustion via collection auto-grow | Capped at 256; `pets[256]` raised `InvalidPropertyException`. The resulting 500 is covered by P-F1 | Probed |
| Binding visits via `pets[0].visits[..]` | `visits` is a `Set`, which is not indexable | Reading; not probed |
| Owner-ID mismatch check (`:148-152`) as a security control | Unreachable: `id` is not bindable and the owner is loaded from the path. Dead code, not a vulnerability | Reading |
| PII in logs | The controller logs nothing | Reading |

The baseline additionally rejected command and LDAP injection, password and session handling
flaws, and hallucinated packages. The post-policy review found no reason to revisit them.

## 8. Out-of-scope risks

Not part of `OwnerController.java`. Recorded so they are not lost, not assessed in depth.

- **Actuator exposure, High if deployed.** `application.properties` sets
  `management.endpoints.web.exposure.include=*` with no authentication. If `heapdump` is reachable
  it would contain owner PII. Which endpoints actually respond was not verified.
- **The same denylist binder elsewhere.** `PetController.java:91,97` and `VisitController.java:53`
  use `setDisallowedFields("id", "*.id")`. The same nested-binding class is likely; needs
  investigation.
- **Shared error page.** `error.html:18` echoes exception text for every controller.
- **PII in `Owner.toString()`** (`Owner.java:156-165`) includes address and telephone; any future
  log statement that prints an owner leaks PII.
- **H2 console.** `CLAUDE.md` states `/h2-console` is available; its exposure was not verified.
- **Committed demo credential.** `k8s/db.yml:14`, recorded in the audit document.
- **Dependency vulnerabilities.** Neither review scanned the Maven or Gradle dependency graphs.

## 9. Recommended remediation order

1. **P-F1, mass assignment.** Confirmed High, small change (one binder line or a DTO), no
   deployment decision needed. Its allowlist also covers most of P-F8.
2. **P-F5, length bounds.** Confirmed Medium, two annotations, no contract decision.
3. **P-F8, search binding.** Low, and largely closed by step 1; finish it with the `lastName`
   bound.
4. **P-F7, error-page message.** Low, but it amplifies every 500, so it is worth closing before the
   500s themselves are all gone.
5. **P-F6, pagination.** Medium, but a known item that must be chartered separately as PAID D5,
   because it changes a pinned test.
6. **P-F2 and P-F4 together, then P-F3.** Authentication, authorization and CSRF land as one
   change, because CSRF matters most once cookie authentication exists and the `th:action` fix
   belongs with it. This first needs a product decision on deployment and roles. P-F3 is largely
   resolved by P-F2.
7. **P-F9, creation rate limit.** Deployment-dependent; decide with the gateway design.

The out-of-scope actuator exposure should be scheduled independently of this order. It is likely
to matter more than several in-scope items if the application is ever deployed.

## 10. Final real-versus-false-positive assessment

**Post-policy review.** Nine findings, none calibrated as a false positive:

| Class | Findings | Count |
|---|---|---|
| Confirmed real vulnerability | P-F1 | 1 |
| Confirmed missing control, impact deployment-dependent | P-F2, P-F4 | 2 |
| Confirmed behavior, impact deployment- and data-dependent | P-F3 | 1 |
| Confirmed robustness / error-handling defect | P-F5, P-F6 | 2 |
| Confirmed unnecessary attack surface | P-F8 | 1 |
| Conditional | P-F7, P-F9 | 2 |
| False positive among reported findings | - | 0 |

Ten further candidates were investigated and rejected (section 7).

**Baseline review, for comparison.** 15 candidates: 4 confirmed and 1 plausible (the mass
assignment, later confirmed), 1 informational and 9 rejected. None of the baseline's rejections was
overturned by the post-policy review, and none of the baseline's findings was found to be false.

**Reading of the comparison.** The post-policy review reported more findings (9 against 6),
confirmed the one the baseline left open, raised it from Medium to High, and measured an input the
baseline had only inferred. Its false-positive rate among reported findings was zero in both
reviews. The additional confirmations came mainly from runtime probes, which the baseline was
instructed not to run (section 2).

**No production finding was fixed during this homework.** The reviews and this document are
records only. Remediation is follow-up work.

## 11. Tool-assisted cross-check

detect-secrets (commit `fe8ab12`, recorded in `audit-2026-09-owner-controller.md` section 3) was
compared with the two AI reviews.

- **The tool found what the AI reviews did not report.** detect-secrets flagged the real,
  pre-existing credential at `k8s/db.yml:14`. Neither controller-focused AI review reported it;
  it lies outside their target file.
- **The tool missed what a human reviewer would question.** The low-entropy demo password
  defaults in `docker-compose.yml:10,19` and in `application-mysql.properties:5` and
  `application-postgres.properties:5` (the `${...:petclinic}` fallbacks) were not flagged. A
  direct `detect-secrets scan` of those files on 2026-09-23 reported only `k8s/db.yml:14`. Secret
  scanning does not replace human review.
- **The AI reviews found what the tool cannot detect.** Authorization gaps (P-F2), nested mass
  assignment (P-F1) and missing validation (P-F5, P-F6, P-F8) are semantic issues, outside the
  scope of a secret scanner.

The tool and the AI reviews were therefore complementary. Neither covered the other's findings.
