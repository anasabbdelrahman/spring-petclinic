# Specification: HTTP 404 for a Missing Owner - 2026-09

Status: proposed. Scope: observable behavior only; the implementation approach is left open.

## 1. Purpose

A well-formed integer owner ID with no matching record is a client error, but
the application currently reports it as a server error:

| Request | Current | Wanted |
| --- | --- | --- |
| `GET /owners/1` | 200 | 200 (unchanged) |
| `GET /owners/9999` | 500 | **404** |
| `GET /owners/9999/edit` | 500 | **404** |
| `POST /owners/9999/edit` | 500 | **404** |

A 500 for a missing record misleads users, hides genuine faults in monitoring, and leaves
this path untested.

## 2. Functional requirements

**FR-1** For each in-scope route, when `{ownerId}` parses as an integer but no owner with
that ID exists, the response status is **404**. The in-scope routes are exactly
`GET /owners/{ownerId}`, `GET /owners/{ownerId}/edit`, and `POST /owners/{ownerId}/edit`;
this list is exhaustive for this feature.

**FR-2** The 404 status applies regardless of the request's `Accept` header. Only the HTML
response body is specified: an HTML request renders the application's existing error page
with its existing 404 message.

**FR-3** For an existing owner, the successful response is unchanged: status, selected
view, model attributes, and redirect target.

**FR-4** A request that results in 404 performs no persistent write. On
`POST /owners/{ownerId}/edit` the missing-owner check precedes form validation, so a
missing owner yields 404 whether the submitted form is valid or invalid.

**FR-5** The 404 applies only to owner-not-found. The regression cases in AC-6
through AC-8 retain their existing status classifications.

**FR-6** No new template, message key, translation, database migration, dependency,
service layer, or build configuration is required.

## 3. Acceptance criteria

Owners **1** and **9999** are test fixtures, not product rules: 1 exists in the seeded
data, 9999 does not.

**AC-1** GIVEN no owner with ID 9999 exists, WHEN a client sends `GET /owners/9999` with
either `Accept: text/html` or `Accept: application/json`, THEN each response status is 404.
Only the HTML response body is specified.

**AC-2** GIVEN no owner with ID 9999 exists, WHEN a client sends `GET /owners/9999/edit`,
THEN the response status is 404.

**AC-3** GIVEN no owner with ID 9999 exists, WHEN a client sends `POST /owners/9999/edit`
once with a complete valid form and once with an invalid form containing a blank
`firstName`, THEN each response status is 404 and neither request creates, updates, or
deletes owner data.

**AC-4** GIVEN no owner with ID 9999 exists, WHEN a client sends `GET /owners/9999` with
`Accept: text/html` and `Accept-Language: en`, THEN the body contains
`Something happened...` and `The requested page was not found.`, and neither
`An internal server error occurred.` nor `Whitelabel Error Page`.

**AC-5** GIVEN owner 1 exists, its behavior is unchanged:

- WHEN a client sends `GET /owners/1`, THEN the status is 200, the view is
  `owners/ownerDetails`, and the `owner` model attribute contains the requested owner,
  including any associated pets and their visits. This clause does not require the seeded
  owner 1 to have any particular pet or visit.
- WHEN a client sends `GET /owners/1/edit`, THEN the status is 200, the view is
  `owners/createOrUpdateOwnerForm`, and the `owner` model attribute contains its current
  values.
- WHEN a client sends `POST /owners/1/edit` with a valid form, THEN the response is a 3xx
  redirect to `/owners/1` and the owner is saved.

**AC-6** GIVEN an `{ownerId}` segment that does not parse as an integer, WHEN a client
sends `GET /owners/abc`, THEN the status is 400, unchanged by this feature.

**AC-7** GIVEN a request that fails on an invalid pagination argument rather than a missing
owner, WHEN a client sends `GET /owners?page=0`, THEN the status is **not** 404 and remains
500 as it is today. This prevents accidental reclassification of pagination errors; it
does not endorse 500 as the ideal pagination behavior.

**AC-8** GIVEN a route that raises a genuine server fault, WHEN a client sends `GET /oups`,
THEN the status is 500, unchanged by this feature.

## 4. Input/output examples

`GET /owners/1` -> `200 OK`, body includes `Owner Information` and `George Franklin`.

`GET /owners/9999` -> `404 Not Found`, body is the error page with
`Something happened...` and `The requested page was not found.`

## 5. Constraints

1. Reuse the existing HTML error page and its 404 message; do not add or edit templates,
   message keys, or translations.
2. Exception messages, stack traces, and other development-only response fields are **not**
   part of the contract and must not be asserted.
3. The solution must not classify every `IllegalArgumentException` as 404: `GET
   /owners?page=0` raises one for an invalid page index and keeps its current status (AC-7).
4. The change is scoped to missing owners encountered by `OwnerController`. Do not
   introduce a global exception mapping, or any other cross-cutting handler, that changes
   `PetController` or `VisitController` behavior. Owner concerns stay in the `owner`
   package; controllers keep calling `OwnerRepository` directly.
5. Java sources use tabs at width 4, LF endings, a final newline, and the Apache 2.0
   header; spring-javaformat, checkstyle, and nohttp gate the build. New tests mirror the
   production package with the `*Tests` suffix. Both builds keep working; no build file
   changes.

## 6. Out of scope

- Missing-owner and missing-pet handling in `PetController` and `VisitController`; their
  production code and tests must not be modified by this change.
- JSON error-body shape and content; its HTTP status is governed by FR-2.
- Non-numeric owner IDs, which already return 400.
- Pagination argument validation (`?page=0`).
- Application logging, metrics, and alerting, including the log level or stack trace
  emitted for a 404.
- Removing the duplicate owner lookup in `OwnerController.showOwner(...)`.
- Turning an empty last-name search result into a 404.
- Any change to error-related application properties.

## 7. Error-case invariant

An update submission for a missing numeric owner ID leaves owner data untouched: the owner
count, the absence of owner 9999, and a control owner's stored values are unchanged.

## 8. Acceptance-criterion-to-test mapping

`OwnerNotFoundIntegrationTests` is new. It uses `@SpringBootTest(webEnvironment = RANDOM_PORT)`
with the full application context and the default H2 database, following
`PetClinicIntegrationTests`. HTTP requests are issued with `@AutoConfigureTestRestTemplate` and an
autowired `TestRestTemplate`; the persistent-state invariant is checked with an autowired
`OwnerRepository`. `CrashControllerIntegrationTests` is unmodified and is relevant here only as the
existing verifier for AC-8. `OwnerControllerTests` is **not** unmodified: it keeps every existing
method, fixture, and assertion, and Task 2 adds the two assertions named in the AC-5 row below,
because the redirect-target and persistence clauses of AC-5 otherwise have no assertion that would
fail if they regressed. This mapping is the minimum required coverage, not a ceiling: a criterion
clause lacking a failing witness is a defect in the mapping and is to be fixed here.

| AC | Test class | Test method | Request | Key assertion |
| --- | --- | --- | --- | --- |
| AC-1 | `OwnerNotFoundIntegrationTests` | `unknownOwnerDetailsReturnsNotFound` | `GET /owners/9999` with `Accept: text/html`, then with `Accept: application/json` | status is `NOT_FOUND` for both requests |
| AC-2 | `OwnerNotFoundIntegrationTests` | `unknownOwnerEditFormReturnsNotFound` | `GET /owners/9999/edit` | status is `NOT_FOUND` |
| AC-3 | `OwnerNotFoundIntegrationTests` | `unknownOwnerUpdateReturnsNotFoundAndChangesNoData` | `POST /owners/9999/edit` with a valid form body, then with an invalid form body (blank `firstName`) | status is `NOT_FOUND` for both requests; after each: owner count equal to before, `findById(9999)` still empty, owner 1's stored values unchanged |
| AC-4 | `OwnerNotFoundIntegrationTests` | `unknownOwnerRendersEnglishNotFoundPage` | `GET /owners/9999`, `Accept: text/html`, `Accept-Language: en` | body contains `Something happened...` and `The requested page was not found.`; does not contain `An internal server error occurred.` or `Whitelabel Error Page` |
| AC-5 | `OwnerControllerTests` | `showOwner`, `initUpdateOwnerForm` (existing, unchanged); `processUpdateOwnerFormSuccess` (existing, strengthened in Task 2) | `GET /owners/1`, `GET /owners/1/edit`, `POST /owners/1/edit` | `status().isOk()`, the view names, and the `owner` model attribute for the two GETs. For the POST: the existing `status().is3xxRedirection()` and `view().name("redirect:/owners/{ownerId}")`, plus two assertions added in Task 2 - `redirectedUrl("/owners/1")` for the resolved redirect target, and `verify(this.owners).save(any(Owner.class))` as the failing witness for "the owner is saved" |
| AC-6 | `OwnerNotFoundIntegrationTests` | `nonNumericOwnerIdReturnsBadRequest` | `GET /owners/abc` | status is `BAD_REQUEST` |
| AC-7 | `OwnerNotFoundIntegrationTests` | `paginationErrorIsNotTreatedAsNotFound` | `GET /owners?page=0` | status is `INTERNAL_SERVER_ERROR`, that is, not `NOT_FOUND` |
| AC-8 | `CrashControllerIntegrationTests` | `triggerExceptionHtml` (existing, unmodified) | `GET /oups` | status is `INTERNAL_SERVER_ERROR` |
