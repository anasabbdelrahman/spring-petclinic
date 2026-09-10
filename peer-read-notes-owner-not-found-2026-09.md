# Peer-Read Notes: HTTP 404 for a Missing Owner - 2026-09

## Review method

A fresh Claude session was instructed to act as an engineer who had never seen the feature
and to read exactly one file: `spec-owner-not-found-2026-09.md`. No implementation files,
tests, investigation reports, plans, `CLAUDE.md`, or other repository documentation were
inspected, and no files were created or edited during the read. The session was asked to
restate the feature in its own words rather than summarize the document section by section.
This was a model-based comprehension check of the specification, not a human peer review.

## What the peer would build

The peer described the feature as: a well-formed but non-existent numeric owner ID must
return **404** instead of the current **500**, on exactly three routes:
`GET /owners/{ownerId}`, `GET /owners/{ownerId}/edit`, and `POST /owners/{ownerId}/edit`.

It also identified, unprompted:

- Successful responses for an existing owner stay identical: status, view name, model
  attributes including pets and visits, and the post-update redirect target.
- The `POST` no-write invariant: the not-found decision must happen before any save, so no
  owner record is created, updated, or deleted, and the submitted form must not be written
  onto a different record.
- The 404 body is the application's existing error page; no new template, message key,
  translation, dependency, migration, service layer, or build change.
- The 404 must be tied to owner-not-found specifically, not to `IllegalArgumentException`
  broadly, or `GET /owners?page=0` would be reclassified.
- The required tests: a new `@SpringBootTest(webEnvironment = RANDOM_PORT)` +
  `TestRestTemplate` class covering the three 404 routes, the error-page body, the
  no-write invariant, `GET /owners/abc` staying 400, and `GET /owners?page=0` staying 500,
  plus the two existing unmodified classes covering the owner-1 happy paths and `/oups`.

## Understanding assessment

**PASS.** The interpretation matched the intended feature comfortably above the ~80%
target; estimated at approximately **95%**. The remaining gap was open questions about
under-specified edges, not misreadings of the requested behavior.

## Questions raised

1. Does the existing error page actually emit `The requested page was not found.` per
   status? If not, AC-4 and the "no template or message key changes" constraint conflict.
2. If the owner lookup is shared across controllers, would fixing it change `PetController`
   and `VisitController` behavior without editing their files?
3. On `POST`, if the form is invalid *and* the owner is missing, does the 404 win or does
   the user get the form back with field errors?
4. What status applies when the client does not accept HTML, given the JSON body shape is
   out of scope?
5. Do the existing owner controller tests really assert the model depth AC-5 claims? They
   cannot be strengthened if they must stay unmodified.
6. Does asserting `?page=0` stays 500 endorse that status, or just freeze current behavior?
7. Should a 404 stop producing an ERROR-level stack trace, given the log-noise rationale?
8. Is the three-route list exhaustive, and what about other HTTP methods on those paths?

## Resolutions

1. Verified: the existing error template and English messages already contain exactly
   `Something happened...` and `The requested page was not found.` No conflict; no template
   or message change is needed.
2. Verified: `OwnerController`, `PetController`, and `VisitController` each have their own
   owner lookup method, so no shared lookup exists to change. The specification now also
   forbids a global exception mapping.
3. A missing owner returns 404 **before** form validation, whether the submitted form is
   valid or invalid.
4. The 404 status applies regardless of the `Accept` header; only the HTML response body is
   specified.
5. Verified: the existing `OwnerControllerTests` already assert the model fields, pets,
   visits, view names, and update redirect described in AC-5. No modification required.
6. AC-7 exists to prevent accidental reclassification of pagination errors; it does not
   endorse 500 as the ideal pagination behavior.
7. Application logging, metrics, and alerting behavior are out of scope.
8. The three in-scope routes are an exhaustive list for this feature.

## Specification changes after the peer-read

- FR-1: the three routes are stated as exactly and exhaustively in scope.
- FR-2: the 404 status applies regardless of `Accept`; only the HTML body is specified.
- FR-4: the missing-owner check precedes form validation, so a missing owner yields 404 for
  a valid or an invalid form.
- AC-7: annotated as an anti-reclassification guard, not an endorsement of 500.
- Constraint 4: the change is scoped to missing owners in `OwnerController`; no global
  exception mapping or cross-cutting handler may change `PetController` or
  `VisitController` behavior.
- Out of scope: application logging, metrics, and alerting, including the log level or
  stack trace emitted for a 404.
- Sections 4 and 7 were condensed so the specification stays within its length budget;
  no requirement was removed.
- All acceptance criteria were normalized to explicit GIVEN/WHEN/THEN form, and AC-3 now
  verifies both a valid and an invalid submission for a missing owner.

## Error-case invariant assessment

The peer correctly understood that a `POST` for a missing owner must return 404 without
changing owner data, and independently proposed the strongest form of the check: owner
count unchanged, owner 9999 still absent, and a control owner's stored values untouched.

## Final result

**PASS** - the peer described the intended implementation accurately, did not invent
required functionality, and correctly identified unauthorized additions as out of scope.
