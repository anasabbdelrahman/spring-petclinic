# Specification: One Owner Lookup per Owner-Details Request - 2026-09

Status: **implemented and verified** in commit `3f7b503`. Intensity **lightweight manual
spec**, tool **manual Markdown** (WORKFLOW.md 1, 5). Not stepped down to no spec: the
refactoring looks trivial, but its obvious implementation carries a non-obvious
request-parameter binding risk (FR-4) that must be specified and tested, not remembered.
Investigation notes, alternatives, and unsupported assumptions live outside this document.

## Approach

*Outside the contract word budget.*

Change `OwnerController.showOwner` to the zero-argument, `String`-returning shape that
returns `"owners/ownerDetails"` and lets the class-level `findOwner` supply the `owner`
attribute. In-repository precedent: `initUpdateOwnerForm`. Take no `Owner` argument, so no
request-parameter binding.

## 1. Purpose and intended outcome

`GET /owners/{ownerId}` loads the same owner twice. Measured with a temporary
`verify(owners, times(1)).findById(1)` against the pre-change code:

```
Wanted 1 time ... But was 2 times:
-> at OwnerController.findOwner(OwnerController.java:67)
-> at OwnerController.showOwner(OwnerController.java:170)
```

`findOwner`, the `@ModelAttribute("owner")` method, already resolves the owner and throws
`OwnerNotFoundException` when it is missing. `showOwner` repeats the lookup and overwrites
the attribute with a second, equal instance: one redundant repository call per page view,
and a second throw site for the same 404.

Outcome: one lookup per request, the response otherwise unchanged.

## 2. Scope, inputs, and outputs

In scope, exhaustively: the `GET /owners/{ownerId}` handler, `OwnerController.showOwner`.

- Input: that request, with or without query parameters.
- Output: status, selected view, model attributes, and the `OwnerRepository.findById`
  invocation count.

Out of scope: `findOwner`, every other handler, `PetController`, `VisitController`,
`OwnerRepository`, `Owner`, templates, message keys, SQL, build files.

## 3. Requirements and invariants

**FR-1** An owner-details request for an existing owner invokes `OwnerRepository.findById`
exactly once.

**FR-2** The success response is unchanged: status 200, view `owners/ownerDetails`, and a
model attribute `owner` holding the requested owner, with its pets reachable and each pet's
visits reachable through them.

**FR-3** A missing owner still returns 404 through the existing mechanism:
`OwnerNotFoundException` from `findOwner`, mapped by its `@ResponseStatus`. No new
exception type, handler, or global mapping.

**FR-4 (binding invariant)** Request parameters must not be data-bound onto the `owner`
model attribute. An `@ModelAttribute` handler argument would bind caller-supplied values
onto a read endpoint; `@InitBinder` disallows only `id` and `*.id`.

**FR-5** Nothing else changes: other handlers keep their routes, statuses, views, redirect
targets, model attributes, validation outcomes, and persistence effects; no new template,
message key, translation, repository method, dependency, or build change.

## 4. Acceptance criteria

Owner IDs 1 and 9999 are fixtures, not product rules.

**AC-1** GIVEN owner 1 exists, WHEN a client sends `GET /owners/1`, THEN
`findById(1)` is invoked exactly once.

**AC-2** GIVEN owner 1 exists with one pet that has one visit, from the
`OwnerControllerTests` fixture, not seeded owner 1, which has no visits, WHEN a client
sends `GET /owners/1`, THEN the status is 200, AND the view is
`owners/ownerDetails`, AND the `owner` attribute carries that owner's first and last name,
address, city, and telephone, AND its `pets` is non-empty, AND at least one pet has a
non-empty `visits`.

**AC-3** GIVEN no owner 9999 exists, WHEN a client sends `GET /owners/9999` with
`Accept: text/html`, and again with `Accept: application/json`, THEN each status is 404.

**AC-4** GIVEN no owner 9999 exists, WHEN a client sends `GET /owners/9999` with
`Accept: text/html` and `Accept-Language: en`, THEN the body contains `Something
happened...` and `The requested page was not found.`, AND contains neither `An internal
server error occurred.` nor `Whitelabel Error Page`.

**AC-5** GIVEN owner 1 exists, WHEN a client sends `GET /owners/1?firstName=Injected`, THEN
the `owner` attribute's first name is `George`.

**AC-6** GIVEN a non-integer `{ownerId}`, WHEN a client sends `GET /owners/abc`, THEN the
status is 400, unchanged.

**AC-7** GIVEN an invalid pagination argument, WHEN a client sends `GET /owners?page=0`,
THEN the status is not 404 and remains 500, unchanged; 500 is not endorsed as ideal.

## 5. Constraints, non-goals, and verification mapping

**Constraints.** Keep the existing 404 mechanism; do not touch `findOwner`,
`OwnerRepository`, templates, message keys, or SQL; the controller keeps calling
`OwnerRepository` directly. `CLAUDE.md` style and the format/checkstyle/nohttp gates apply;
both builds keep working, no build-file change. Exception text, stack traces, and instance
identity are outside the contract.

**Non-goals.** The same question in `PetController`/`VisitController`; SQL statement counts
or any latency claim; the JSON error-body shape; editing `data.sql` to give owner 1 a
visit; any change to `findOwner`.

**Verification mapping.** A floor, not a ceiling. AC-1 and AC-5 gained their witnesses in
`3f7b503`; the rest are pre-existing no-regression checks. AC-1, AC-2, AC-5 are in
`OwnerControllerTests`; the others in `OwnerNotFoundIntegrationTests`.

| AC | Test method | Witness assertion |
| --- | --- | --- |
| AC-1 | `showOwner` | `verify(owners, times(1)).findById(TEST_OWNER_ID)`; proven to fail `TooManyActualInvocations` against the pre-change code |
| AC-2 | `showOwner` | existing `status().isOk()`, `view().name(...)`, and `hasProperty(...)` matchers for the five fields, non-empty `pets`, and a pet with `visits` size > 0 |
| AC-3 | `unknownOwnerDetailsReturnsNotFound` | both statuses `NOT_FOUND` |
| AC-4 | `unknownOwnerRendersEnglishNotFoundPage` | error-page contains/does-not-contain assertions |
| AC-5 | `showOwnerIgnoresRequestParameters` | `hasProperty("firstName", is("George"))` for `?firstName=Injected` |
| AC-6 | `nonNumericOwnerIdReturnsBadRequest` | status `BAD_REQUEST` |
| AC-7 | `paginationErrorIsNotTreatedAsNotFound` | status `INTERNAL_SERVER_ERROR`, not `NOT_FOUND` |
